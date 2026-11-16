#!/usr/bin/env bash
set -Eeuo pipefail

on_err() {
  local status=$1 line=$2 cmd=$3
  printf '\n\033[31merror: line %s exited %s\033[0m\n' "$line" "$status" >&2
  printf '  while running: %s\n' "$cmd" >&2
  printf '  nothing further was changed; re-run when the cause is fixed.\n' >&2
}
trap 'on_err "$?" "$LINENO" "$BASH_COMMAND"' ERR

cd "$(dirname "$0")/.."

for bin in gh jq gpg openssl ykman; do
  command -v "$bin" >/dev/null || { echo "error: $bin is required" >&2; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "error: run 'gh auth login' first" >&2; exit 1; }

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
ENVIRONMENT=marketplace

tmp=$(mktemp -d)
cleanup() {
  find "$tmp" -type f -exec sh -c 'command -v shred >/dev/null && shred -u "$1" 2>/dev/null || { : > "$1"; rm -f "$1"; }' _ {} \; 2>/dev/null || true
  rm -rf "$tmp"
}
trap cleanup EXIT INT TERM
chmod 700 "$tmp"

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
info() { printf '   %s\n' "$*"; }
warn() { printf '   \033[33m! %s\033[0m\n' "$*"; }
ask()  { local a; read -r -p "   $1 [y/N] " a; [ "$a" = y ] || [ "$a" = Y ]; }

read_secret() {
  local var=$1 prompt=$2 value
  printf '   %s: ' "$prompt" >&2
  read -rs value
  printf '\n' >&2
  [ -n "$value" ] || { echo "error: empty value" >&2; exit 1; }
  printf -v "$var" '%s' "$value"
}

existing_secrets=$(gh secret list --env "$ENVIRONMENT" --repo "$REPO" --json name -q '.[].name' 2>/dev/null || true)
has_secret() { grep -qx "$1" <<<"$existing_secrets"; }

set_secret_from_file() {
  local name=$1 file=$2
  if has_secret "$name"; then
    if ! ask "$name is already set. Replace it?"; then info "keeping the existing $name"; return; fi
  fi
  gh secret set "$name" --env "$ENVIRONMENT" --repo "$REPO" < "$file"
  info "set $name"
}

echo "repository:  $REPO"
echo "environment: $ENVIRONMENT"

say "1/7  Deployment environment"

jq -n '{
  wait_timer: 0,
  prevent_self_review: false,
  reviewers: [],
  deployment_branch_policy: { protected_branches: false, custom_branch_policies: true }
}' | gh api --method PUT "repos/$REPO/environments/$ENVIRONMENT" --input - >/dev/null
info "environment created/updated — publish runs without a manual approval"

for policy in "main:branch" "v*.*.*:tag"; do
  name=${policy%:*}; type=${policy##*:}
  if gh api "repos/$REPO/environments/$ENVIRONMENT/deployment-branch-policies" \
        -q '.branch_policies[].name' 2>/dev/null | grep -qxF "$name"; then
    info "deployment policy '$name' already present"
  else
    gh api --method POST "repos/$REPO/environments/$ENVIRONMENT/deployment-branch-policies" \
      -f "name=$name" -f "type=$type" >/dev/null
    info "allowed deployments from '$name' ($type)"
  fi
done

say "2/7  JetBrains Marketplace token"

if has_secret PUBLISH_TOKEN && ! ask "PUBLISH_TOKEN is already set. Replace it?"; then
  info "keeping the existing PUBLISH_TOKEN"
else
  info "Create a permanent token at https://plugins.jetbrains.com/author/me/tokens"
  info "(it is shown once — copy it before closing the page)"
  read_secret token "Paste the token"
  printf '%s' "$token" > "$tmp/token"
  unset token
  gh secret set PUBLISH_TOKEN --env "$ENVIRONMENT" --repo "$REPO" < "$tmp/token"
  info "set PUBLISH_TOKEN"
fi

say "3/7  JetBrains plugin signing key (X.509 — not GPG)"

if has_secret PRIVATE_KEY && has_secret CERTIFICATE_CHAIN && has_secret PRIVATE_KEY_PASSWORD \
   && ! ask "The signing key is already configured. Replace it?"; then
  info "keeping the existing signing key"
else
  info "reading the CA certificates from PIV slot F9"
  pki_root=""; pki_int=""; int_card=""
  while read -r serial; do
    [ -n "$serial" ] || continue
    f9="$tmp/f9-$serial.pem"
    ykman --device "$serial" piv certificates export f9 - > "$f9" 2>/dev/null || continue
    grep -q 'BEGIN CERTIFICATE' "$f9" || continue
    subj=$(openssl x509 -in "$f9" -noout -subject -nameopt RFC2253); subj=${subj#subject=}; subj=${subj# }
    iss=$(openssl x509 -in "$f9" -noout -issuer  -nameopt RFC2253);  iss=${iss#issuer=};    iss=${iss# }
    if [ "$subj" = "$iss" ]; then
      pki_root=$f9; info "  root         $subj  (card $serial)"
    else
      pki_int=$f9; int_card=$serial; info "  intermediate $subj  (card $serial)"
    fi
  done < <(ykman list --serials 2>/dev/null)

  if [ -z "$pki_root" ] || [ -z "$pki_int" ]; then
    echo "error: PIV slot F9 gave root=${pki_root:+found}${pki_root:-MISSING} intermediate=${pki_int:+found}${pki_int:-MISSING}" >&2
    echo "       both CA YubiKeys must be plugged in. Nothing was changed." >&2
    exit 1
  fi
  cp "$pki_root" "$tmp/root.crt"; cp "$pki_int" "$tmp/int.crt"

  int_cn=$(openssl x509 -in "$tmp/int.crt" -noout -subject -nameopt multiline \
           | awk -F'= ' '/commonName/{ if (v == "") v = $2 } END { print v }')
  info "issuing under: $int_cn"

  ca_key=${CA_KEY:-}
  want=$(openssl x509 -in "$tmp/int.crt" -noout -pubkey \
         | openssl pkey -pubin -outform DER | sha256sum | cut -d' ' -f1)
  if [ -n "$ca_key" ]; then
    have=$(openssl pkey -in "$ca_key" -pubout -outform DER 2>/dev/null | sha256sum | cut -d' ' -f1) \
      || have=""
    [ "$have" = "$want" ] || { echo "error: CA_KEY is not the CA on card $int_card" >&2; exit 1; }
  else
    tried=0
    while read -r cand; do
      [ -n "$cand" ] || continue
      tried=$((tried + 1))
      have=$(openssl pkey -in "$cand" -pubout -outform DER -passin pass: 2>/dev/null \
             | sha256sum | cut -d' ' -f1) || have=""
      if [ "$have" = "$want" ]; then ca_key=$cand; break; fi
    done < <(find "${PKI_DIR:-$HOME/pki}" -type f \( -name '*.key' -o -name '*.pem' \) 2>/dev/null)
    info "examined $tried candidate key files"
  fi
  if [ -z "$ca_key" ]; then
    echo "error: no reachable private key matches the CA certificate on card $int_card." >&2
    echo "       F9 cannot sign (attestation slot), so the CA's key has to be readable here." >&2
    echo "       Set CA_KEY to its path, or PKI_DIR to the tree holding it. Nothing was changed." >&2
    exit 1
  fi
  info "signing with the CA key (public half verified against the card)"

  gpg --gen-random --armor 2 32 | tr -d '\n' > "$tmp/keypass"
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:secp384r1 \
    -aes-256-cbc -pass "file:$tmp/keypass" -out "$tmp/leaf.key" 2>/dev/null
  openssl req -new -key "$tmp/leaf.key" -passin "file:$tmp/keypass" -sha384 \
    -subj "/CN=Claude Code Native plugin upload key" \
    -out "$tmp/leaf.csr" 2>/dev/null

  cat > "$tmp/leaf.ext" <<'EXT'
basicConstraints       = critical,CA:FALSE
keyUsage               = critical,digitalSignature
extendedKeyUsage       = codeSigning
subjectKeyIdentifier   = hash
authorityKeyIdentifier = keyid,issuer
EXT
  openssl x509 -req -in "$tmp/leaf.csr" -sha384 \
    -CA "$tmp/int.crt" -CAkey "$ca_key" \
    -set_serial "0x$(openssl rand -hex 16)" -days 3650 \
    -extfile "$tmp/leaf.ext" -out "$tmp/leaf.crt" \
    || { echo "error: signing failed — $ca_key did not open, or it is not the CA's key" >&2; exit 1; }

  cat "$tmp/leaf.crt" "$tmp/int.crt" > "$tmp/fullchain.crt"
  openssl verify -CAfile "$tmp/root.crt" -untrusted "$tmp/int.crt" "$tmp/leaf.crt" >/dev/null \
    || { echo "error: the issued certificate does not verify against the root — nothing was set" >&2; exit 1; }

  gh secret set PRIVATE_KEY          --env "$ENVIRONMENT" --repo "$REPO" < "$tmp/leaf.key"
  gh secret set CERTIFICATE_CHAIN    --env "$ENVIRONMENT" --repo "$REPO" < "$tmp/fullchain.crt"
  gh secret set PRIVATE_KEY_PASSWORD --env "$ENVIRONMENT" --repo "$REPO" < "$tmp/keypass"
  info "set PRIVATE_KEY, CERTIFICATE_CHAIN, PRIVATE_KEY_PASSWORD — nothing written outside $tmp"
  info "$(openssl x509 -in "$tmp/leaf.crt" -noout -subject -issuer -enddate | tr '\n' ' ')"

  printf '%s\n%s\n' "$(cat "$tmp/keypass")" "$(cat "$tmp/keypass")" > "$tmp/keypass2"

  if openssl pkcs12 -export -inkey "$tmp/leaf.key" -in "$tmp/leaf.crt" -certfile "$tmp/int.crt" \
       -name "$(openssl x509 -in "$tmp/leaf.crt" -noout -subject -nameopt RFC2253)" \
       -passin "file:$tmp/keypass2" -passout "file:$tmp/keypass2" -out "$tmp/leaf.p12" 2>"$tmp/p12.err" \
     && gpgsm --batch --pinentry-mode loopback --passphrase-fd 3 \
          --import "$tmp/leaf.p12" 3< "$tmp/keypass" 2>>"$tmp/p12.err"; then
    info "kept a copy of the issued certificate and key in the local gpgsm store"
  else
    warn "could not keep a local copy of the issued key — CI has it, you do not."
    warn "  $(tr '\n' ' ' < "$tmp/p12.err" 2>/dev/null || true)"
    warn "the certificate is reproducible by re-running this step; nothing is lost but the audit trail."
  fi

  for leftover in "$tmp"/f9-*.pem "$tmp/root.crt" "$tmp/int.crt" "$tmp/leaf.p12" "$tmp/p12.err" \
                  "$tmp/leaf.csr" "$tmp/leaf.ext" "$tmp/leaf.key" "$tmp/leaf.crt" \
                  "$tmp/fullchain.crt" "$tmp/keypass" "$tmp/keypass2"; do
    [ -e "$leftover" ] || continue
    shred -u "$leftover" 2>/dev/null || { : > "$leftover"; rm -f "$leftover"; }
  done
  unset ca_key
  info "card certificates and issued key wiped from $tmp"
fi

say "4/7  CI artifact signing key (GPG)"

if has_secret GPG_SIGNING_KEY && has_secret GPG_SIGNING_PASSPHRASE \
   && ! ask "The CI signing key is already configured. Rotate it?"; then
  info "keeping the existing CI signing key"
else
  info "Generating an Ed25519 key that expires in 1 year, in a throwaway keyring."
  info "It is NOT the maintainer key — see SECURITY.md for what each signature claims."
  ./scripts/gen-ci-signing-key.sh --outdir "$tmp/gpg" >/dev/null 2>&1
  gh secret set GPG_SIGNING_KEY        --env "$ENVIRONMENT" --repo "$REPO" < "$tmp/gpg/private.asc"
  gh secret set GPG_SIGNING_PASSPHRASE --env "$ENVIRONMENT" --repo "$REPO" < "$tmp/gpg/passphrase"
  info "set GPG_SIGNING_KEY and GPG_SIGNING_PASSPHRASE (private half never printed)"

  ci_fpr=$(cat "$tmp/gpg/fingerprint")

  if gpg --batch --import "$tmp/gpg/private.asc" 2>/dev/null; then
    info "imported the CI key $ci_fpr into your keyring (private half, still passphrase-protected)"
  else
    warn "could not import the CI key into your keyring — it will exist ONLY as a GitHub secret,"
    warn "  which is write-only: it can never be read back, audited or reused."
  fi

  mapfile -t certifiers < <(gpg --list-secret-keys --with-colons \
    | awk -F: '$1=="sec" && $15!="" && $15!="+" { getline; if ($1=="fpr") print $10 }')

  pass_stash="$HOME/.gnupg/claude-code-native-ci-passphrase.asc"
  if [ ${#certifiers[@]} -gt 0 ]; then
    recipients=(); for fpr in "${certifiers[@]}"; do recipients+=(-r "$fpr"); done
    if gpg --batch --yes --armor --trust-model always "${recipients[@]}" \
           --output "$pass_stash" --encrypt "$tmp/gpg/passphrase" 2>/dev/null; then
      chmod 600 "$pass_stash"
      info "stashed the CI passphrase at $pass_stash (opens with any CA YubiKey)"
    else
      warn "could not encrypt the CI passphrase to a CA — none of them has an encryption subkey."
      warn "  the imported CI key stays locked: its passphrase now lives ONLY in the GitHub secret."
    fi
  fi

  if [ ${#certifiers[@]} -eq 0 ]; then
    warn "no hardware-held key in your keyring — the CI key cannot be certified by a CA."
    warn "the exported key will carry no endorsement; users can only take its fingerprint on faith."
    warn "the CI passphrase could not be stashed either — there is no key here to encrypt it to."
    cp "$tmp/gpg/public.asc" docs/trust-chain.asc
  else
    gpg --batch --import "$tmp/gpg/public.asc" 2>/dev/null
    warn "TOUCH YOUR YUBIKEY once per certifier — ${#certifiers[@]} of them."

    for a in "${certifiers[@]}"; do
      for b in "${certifiers[@]}"; do
        [ "$a" = "$b" ] && continue
        gpg --batch --yes --local-user "$a" --quick-sign-key "$b" >/dev/null 2>&1 \
          || warn "$a did not certify $b (already signed, cancelled, or that card was absent)"
      done
    done

    for fpr in "${certifiers[@]}"; do
      uid=$(gpg --list-keys --with-colons "$fpr" \
            | awk -F: '$1=="uid" { if (v == "") v = $10 } END { print v }') || uid="?"
      info "certifying $ci_fpr with $fpr ($uid)"
      gpg --batch --yes --local-user "$fpr" --quick-sign-key "$ci_fpr" \
        || warn "certification with $fpr failed (cancelled, or that YubiKey was not present)"
    done
    { for fpr in "${certifiers[@]}"; do gpg --armor --export "$fpr"; done
      gpg --armor --export "$ci_fpr"; } > docs/trust-chain.asc

    chain_home="$tmp/chain-verify"; mkdir -p "$chain_home"; chmod 700 "$chain_home"
    GNUPGHOME="$chain_home" gpg --batch --quiet --import docs/trust-chain.asc 2>/dev/null

    survived() {
      GNUPGHOME="$chain_home" gpg --check-sigs --with-colons "$2" 2>/dev/null \
        | awk -F: -v m="${1: -16}" '$1=="sig" && $5==m { v = "y" } END { print v }' || true
    }
    for a in "${certifiers[@]}"; do
      for b in "${certifiers[@]}"; do
        [ "$a" = "$b" ] && continue
        if [ "$(survived "$a" "$b")" = y ]; then
          info "docs/trust-chain.asc: $a certifies $b"
        else
          warn "no certification by $a of $b survived the export — an lsign is the usual cause."
          warn "  gpg --edit-key $b   → 'delsig' the local one, then:"
          warn "  gpg --local-user $a --quick-sign-key $b"
        fi
      done
    done

    for fpr in "${certifiers[@]}"; do
      have_key=$(GNUPGHOME="$chain_home" gpg --list-keys --with-colons 2>/dev/null \
        | awk -F: -v f="$fpr" '$1=="fpr" && $10==f { v = "y" } END { print v }') || have_key=""
      have_sig=$(GNUPGHOME="$chain_home" gpg --check-sigs --with-colons "$ci_fpr" 2>/dev/null \
        | awk -F: -v m="${fpr: -16}" '$1=="sig" && $5==m { v = "y" } END { print v }') || have_sig=""
      if [ "$have_key" = y ] && [ "$have_sig" = y ]; then
        info "docs/trust-chain.asc: $fpr is present, and its certification of the CI key survived"
      else
        [ "$have_key" = y ] || warn "docs/trust-chain.asc does not carry the CA $fpr at all."
        [ "$have_sig" = y ] || warn "no certification by $fpr survived the export — was it an lsign?"
        warn "Re-run with that YubiKey present, then re-export:"
        warn "  gpg --local-user $fpr --quick-sign-key $ci_fpr"
      fi
    done
    for fpr in "${certifiers[@]}" "$ci_fpr"; do
      if gpg --batch --keyserver hkps://keys.openpgp.org --send-keys "$fpr" >/dev/null 2>&1; then
        info "published $fpr to keys.openpgp.org"
      else
        warn "could not publish $fpr to keys.openpgp.org — no independent anchor for it yet."
      fi
    done
  fi

  info "wrote docs/trust-chain.asc  (CI signing key $ci_fpr)"
  warn "COMMIT docs/trust-chain.asc — without it nobody can verify a release."

  if gh gpg-key list >/dev/null 2>&1; then
    if gh gpg-key add "$tmp/gpg/public.asc" >/dev/null 2>&1; then
      info "registered the CI public key on the GitHub account"
    else
      info "GitHub already knows this key (or rejected it) — verifying below"
    fi
    short=${ci_fpr: -16}
    if gh api user/gpg_keys -q ".[] | select(.key_id==\"$short\") | .emails[]?.email" 2>/dev/null | grep -q .; then
      info "the registered key carries an email — tags signed with it can be verified"
    else
      warn "the registered key lists NO email address. Tags signed with it will show as UNVERIFIED."
      warn "Regenerate it with gen-ci-signing-key.sh (which now sets Name-Email) and re-run this step."
    fi
  else
    warn "gh lacks the GPG scope, so the key was NOT registered on your account."
    warn "Without this the release tag will show as unverified. Run:"
    warn "  gh auth refresh -s write:gpg_key && gh gpg-key add <(gpg --armor --export $ci_fpr)"
  fi
fi

say "5/7  Repository deploy key"

if has_secret DEPLOY_KEY && ! ask "DEPLOY_KEY is already set. Rotate it?"; then
  info "keeping the existing deploy key"
else
  title="ci-release-$(date -u +%Y-%m-%d)"
  ssh-keygen -q -t ed25519 -N '' -C "$title" -f "$tmp/deploy_key"
  for id in $(gh api "repos/$REPO/keys" --jq '.[] | select(.title | startswith("ci-release-")) | .id' 2>/dev/null); do
    gh api --method DELETE "repos/$REPO/keys/$id" >/dev/null && info "removed the previous deploy key ($id)"
  done
  gh repo deploy-key add "$tmp/deploy_key.pub" --repo "$REPO" --title "$title" --allow-write >/dev/null
  gh secret set DEPLOY_KEY --env "$ENVIRONMENT" --repo "$REPO" < "$tmp/deploy_key"
  info "registered '$title' with write access; private half stored as DEPLOY_KEY and shredded locally"
  info "fingerprint: $(ssh-keygen -lf "$tmp/deploy_key.pub" | cut -d' ' -f2)"
  warn "NOTHING CONSUMES DEPLOY_KEY YET. release.yml pushes the tag with GITHUB_TOKEN, and switching that"
  warn "push to this key also makes it TRIGGER workflows — which fires release.yml's own tag trigger and"
  warn "starts a second run of the release it just finished. Guard that trigger before wiring it up."
fi

say "6/7  Verification"

actual=$(gh secret list --env "$ENVIRONMENT" --repo "$REPO" --json name -q '.[].name' | sort)
expected=$(printf '%s\n' CERTIFICATE_CHAIN DEPLOY_KEY GPG_SIGNING_KEY GPG_SIGNING_PASSPHRASE PRIVATE_KEY PRIVATE_KEY_PASSWORD PUBLISH_TOKEN | sort)
if [ "$actual" = "$expected" ]; then
  info "all seven environment secrets present"
else
  warn "missing: $(comm -23 <(echo "$expected") <(echo "$actual") | tr '\n' ' ')"
  warn "unexpected: $(comm -13 <(echo "$expected") <(echo "$actual") | tr '\n' ' ')"
fi

repo_secrets=$(gh secret list --repo "$REPO" --json name -q '.[].name' 2>/dev/null || true)
if [ -n "$repo_secrets" ]; then
  warn "repository-level secrets found — these are visible to EVERY workflow job:"
  printf '     %s\n' $repo_secrets
  warn "move them into the environment and delete them: gh secret delete <name> --repo $REPO"
else
  info "no repository-level secrets (correct)"
fi

reviewers=$(gh api "repos/$REPO/environments/$ENVIRONMENT" \
  -q '[.protection_rules[]? | select(.type=="required_reviewers") | .reviewers[].reviewer.login] | join(", ")')
if [ -z "$reviewers" ]; then
  info "no required reviewer — a merge to main publishes without a second confirmation"
else
  warn "required reviewer(s) present: $reviewers — publish will WAIT for approval"
fi
info "deployments allowed from: $(gh api "repos/$REPO/environments/$ENVIRONMENT/deployment-branch-policies" \
  -q '[.branch_policies[] | "\(.type):\(.name)"] | join(", ")')"

say "7/7  Branch protection"

./scripts/apply-rulesets.sh --dry-run
echo
warn "Applying this stops direct pushes to main and develop — INCLUDING YOURS. No bypass, by design."
if ask "Apply the rulesets now?"; then
  ./scripts/apply-rulesets.sh
else
  info "skipped — run ./scripts/apply-rulesets.sh when ready"
fi

say "Done"
cat <<EOF
   Remaining, and neither can be done for you:

   1. Commit the trust chain, or releases cannot be verified:
        git add docs/trust-chain.asc && git commit -m "chore(release): publish the release trust chain"

   2. Smoke-test the pipeline before a real release depends on it:
        git checkout -b test/ci-smoke
        git commit --allow-empty -m "test(ci): verify the pipeline runs end to end"
        git push -u origin test/ci-smoke && gh run watch

      Then open a PR into develop and confirm the checks BLOCK the merge rather than
      merely appearing. A check that is present but not required is not a gate.

   Troubleshooting: docs/CI_SETUP.md
EOF
