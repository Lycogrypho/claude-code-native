# Script notes

The reasoning that used to stand in comments beside the scripts and the fake `claude`. It was moved here when the
no-comments rule was extended to every file (D_0041): the files now carry code only, and a reason
belongs here, in a test or in the commit message. Each note is headed by the line it stood above.

## scripts/apply-rulesets.sh

### `set -euo pipefail`

Apply the branch protection rulesets in .github/rulesets/ to the GitHub repository.

Branch protection is the one part of the CI/CD setup that does NOT live in the repository by default —
it lives in a settings page, where it is invisible in review, undiffable, and silently editable. That
is a bad place for a control whose entire job is to be non-bypassable, so the rulesets are versioned
here and this script pushes them.

Idempotent: a ruleset with a matching name is UPDATED, not duplicated. Safe to re-run.

  ./scripts/apply-rulesets.sh            # apply
  ./scripts/apply-rulesets.sh --dry-run  # show what would change and exit

Requires: gh (authenticated with admin rights on the repo) and jq.

### `body=$(jq 'walk(if type == "object"`

The JSON files carry `_comment` keys explaining the non-obvious choices — chiefly why the required
approval count is 0 on a single-maintainer repo. Those keys are documentation, not API fields, so
they are stripped here rather than risking a 422 on an unrecognised property.

Every key with the `_comment` PREFIX, not just the exact name. Two comments cannot share one object
under the exact-match version, so the moment a second annotation is needed in the same block the
obvious move is to call it `_comment_<something>` — which then sails through this filter and gets
rejected by the API as an unrecognised property. The 422 does not name the offending key, so the
failure reads as "the ruleset is wrong" rather than "the comment leaked". Observed, not hypothetical.

### `echo`

Read back what is ACTUALLY in place rather than trusting that the writes above worked. The listing is
retried because the API is eventually consistent: read immediately after creating and it can come back
empty, which looks exactly like a failure and is not one.

## scripts/bootstrap-ci.sh

### `set -Eeuo pipefail`

One-shot CI/CD bootstrap: creates the deployment environment, sets its seven secrets, and (optionally)
applies the branch protections. Automates everything that can be automated and asks you only for what
you actually hold: the Marketplace token, and the JetBrains signing key if you still have it.

Idempotent — safe to re-run. Existing secrets are reported and skipped unless you ask to replace them.

  ./scripts/bootstrap-ci.sh

Handling rules this script follows, because it moves secret material around:
  * nothing secret is ever echoed, logged, or passed as a command-line argument (argv is world-readable
    in /proc on Linux — `gh secret set --body "$TOKEN"` would leak it to any local user);
  * everything goes to `gh secret set` over stdin;
  * temp files live in a 0700 directory under $TMPDIR and are shredded on exit, including on Ctrl-C;
  * the generated GPG private key is piped straight from the generator into `gh` and never reaches your
    terminal, scrollback, or shell history.

See docs/CI_SETUP.md for what each step means and why.

### `on_err() {`

`set -e` kills this script SILENTLY — no line, no command, no status — and it has done so three times
in a row on a failure that looked like the step simply doing nothing. Every one of them was the same
shape: an assignment whose command substitution failed, usually because `pipefail` promoted the failure
of something in the middle of a pipe. That is invisible by construction, so the trap below is not a
nicety: it is the difference between a bug report that says "it configures nothing" and one that names
the line. `-E` is what makes it fire inside functions, subshells and command substitutions too.

### `for bin in gh jq gpg openssl ykman; do`

--- preconditions -------------------------------------------------------------------------------------

### `find "$tmp" -type f -exec sh -c 'command -v shred >/dev/null && shred -u "$1" 2>/dev/null || { : > "$1"; rm -f`

`shred` where available; the fallback still beats plain rm because it overwrites first.

### `read_secret() {`

Reads a secret without echoing it and without it becoming an argument.

### `set_secret_from_file() {`

Sets a secret from a file, unless it exists and you decline to replace it.

### `say "1/7  Deployment environment"`

--- 1. environment ------------------------------------------------------------------------------------

### `jq -n '{`

NO required reviewer, deliberately, and this reverses an earlier decision rather than overlooking one.

The approval existed as the human gate on an irreversible publish. On a single-maintainer repository it
was not buying that: this account is the ONLY collaborator, `main` is protected and accepts nothing but
pull requests, and the merge of that pull request is already a deliberate human act. The approval added
a second click by the same person, moments later, over the same decision.

What is genuinely lost is named rather than glossed: an automated publish now follows a merge without
anyone confirming which VERSION is about to go out. The remaining guards are the reviewed pull request
into main, the lineage assertion in release.yml's `guard` job, and the fact that `guard` refuses to
publish a version whose tag already exists.

The body is built with jq and piped in, rather than assembled from -f/-F flags. Two reasons, both
learned the hard way: gh's `-f` sends STRINGS (so `-f wait_timer=0` is rejected as `"0"` is not an
integer) while `-F` guesses the type, and the bracket syntax for an array of objects is ambiguous
enough that it is not worth relying on. A JSON document has exactly one meaning.

### `for policy in "main:branch" "v*.*.*:tag"; do`

Which refs may deploy. BOTH are needed and they are not interchangeable:

  main      the primary path. release.yml triggers on a push to main and derives the tag from
            build.gradle.kts, so at deployment time github.ref is refs/heads/main. Without this entry
            the job is rejected outright with "Branch 'main' is not allowed to deploy to marketplace",
            before it even reaches the workflow — which is exactly how the first attempt failed.
  v*.*.*    the manual escape hatch: re-cutting a release by pushing an explicit tag.

### `say "2/7  JetBrains Marketplace token"`

--- 2. Marketplace token ------------------------------------------------------------------------------

### `say "3/7  JetBrains plugin signing key (X.509 — not GPG)"`

--- 3. JetBrains plugin signing key -------------------------------------------------------------------

### `info "reading the CA certificates from PIV slot F9"`

What this key actually is, because the naming misleads: the Marketplace RE-SIGNS every plugin with
JetBrains' own key (AWS KMS) before serving it. Yours is effectively an UPLOAD KEY — the Google Play
analogy — and the signature an end user's IDE verifies is JetBrains', not this one. So rotating it is
not user-visible, and there is no reason to keep a copy on disk: it is generated here, pushed straight
to GitHub, and forgotten. If it is ever lost, generate another.

It cannot be dropped, though, and that is the part the paragraph above invites you to get wrong. A
plugin is signed TWICE by design — author first, Marketplace second — and the author's half is
optional only in the sense that an unsigned upload is accepted: JetBrains then shows a WARNING DIALOG
in the IDE to every user who installs it. So the trade is one software key in an environment secret
against a dialog in front of everyone, which is why this step exists and why `signPlugin` is not
something to remove for the sake of holding no key material.

ISSUED here, every time, with no question asked and no file to hand over. That is the requirement and
not a nicety: this script exists to leave the pipeline configured end to end, so a prompt asking for
the path to a PEM would be a hole in precisely the thing it delivers.

Nothing about the maintainer's PKI is written down here — not a serial, not a DN, not a fingerprint,
not a path. Which card carries which CA is DERIVED: the card whose F9 certificate is self-issued is
the root, the other is the intermediate. A reissued PKI is therefore picked up rather than
contradicted, and a repository that anyone can read describes no one's key material.

The PKI is read, never written: this asks it for one leaf and creates, resets and reissues nothing.

### `ykman --device "$serial" piv certificates export f9 - > "$f9" 2>/dev/null || continue`

Exporting a certificate is a public read: no PIN, no touch, nothing to approve.

### `int_cn=$(openssl x509 -in "$tmp/int.crt" -noout -subject -nameopt multiline \`

`awk … {print; exit}` would be the obvious spelling and is the one to avoid throughout this script:
awk leaving early closes the pipe, the writer on the left dies of SIGPIPE, and `pipefail` + `set -e`
then abort the whole run with no message at all. Reading the input to the end costs nothing here.

### `ca_key=${CA_KEY:-}`

The CERTIFICATES come off the cards; the SIGNATURE cannot. Slot F9 is the attestation slot, and Yubico's
firmware restricts it to attesting keys generated on the device — "It is only used for attestation of
other keys generated on device with instruction f9". So it holds the CA and refuses to sign with it:
the PIN is accepted, the login succeeds, and GENERAL AUTHENTICATE comes back a device error. No PKCS#11
module, mechanism or digest changes that, and an attempt looks exactly like a wrong PIN, which is why
this paragraph exists rather than a retry.

The signing key is therefore the CA's own private key on disk — MATCHED to the card, never assumed. The
card's certificate is the reference and the key is found by comparing public halves, so a key file that
merely has the right name cannot be used: a PKI reissued on disk and not on the cards would otherwise
produce a chain that fails `openssl verify` at the end of this step, after the secrets were set.

### `if [ -n "$ca_key" ]; then`

`|| have=""` on every one of these, and it is load-bearing rather than defensive: MOST of the files
looked at here are expected to fail to open — a key for another purpose, an encrypted one, a PEM that
is a certificate. Without it the first such file ends the run, which is exactly what happened: the
search died on an encrypted key several files before reaching the one it was looking for.

### `have=$(openssl pkey -in "$cand" -pubout -outform DER -passin pass: 2>/dev/null \`

`-passin pass:` so an ENCRYPTED key fails immediately instead of stopping the run on a prompt:
a key we cannot open unattended is one this script cannot use, whichever key it turns out to be.

### `gpg --gen-random --armor 2 32 | tr -d '\n' > "$tmp/keypass"`

Never copied into $tmp: openssl reads it in place, so this step creates no second copy of a CA key and
has none to shred. The path is not printed either — a log, a screenshot or a pasted run should not be
the thing that tells a reader where the CA of a trust chain is kept.
A random passphrase, never displayed: nobody types this key in by hand. Its only consumer is the CI
job, which reads it from the secret — a memorable passphrase would be a weakness with no upside.

### `openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:secp384r1 \`

EC P-384 + SHA-384 — the shape this PKI issues in, and one marketplace-zip-signer supports natively
(SignatureAlgorithm.ECDSA_WITH_SHA384). No RSA detour to keep a tool happy that does not need one.
Passphrase read from a FILE, never from argv: process arguments are world-readable in /proc.

### `cat > "$tmp/leaf.ext" <<'EXT'`

codeSigning, and only that. The PKI's own leaf profile is clientAuth/serverAuth — a TLS certificate —
which is the wrong claim for one that signs an artifact, so the profile is written here rather than
borrowed. keyUsage is digitalSignature alone for the same reason.

### `openssl x509 -req -in "$tmp/leaf.csr" -sha384 \`

10 years, not the PKI's 1095 days. An expiring upload key would break publishing on a date nobody has
in a calendar, and expiry buys nothing here: this certificate is not a trust anchor for any user.
Random serial rather than -CAcreateserial, which would drop a .srl file next to the CA it read.

### `cat "$tmp/leaf.crt" "$tmp/int.crt" > "$tmp/fullchain.crt"`

leaf first, issuer after — the order signPlugin reads the chain in, and the order the PKI's own
fullchain.crt uses. The root is deliberately NOT appended: a self-signed anchor inside the chain adds
nothing a verifier can use, since it either already trusts that root or must not be told to.

### `printf '%s\n%s\n' "$(cat "$tmp/keypass")" "$(cat "$tmp/keypass")" > "$tmp/keypass2"`

A copy of what was issued goes into the LOCAL X.509 store (gpgsm, software-held — no YubiKey), and
that is a deliberate exception to "no copy is kept" rather than a contradiction of it. The reason is
the one thing a GitHub secret cannot do: it is write-only. Once these three are set nobody, including
you, can read back what was uploaded — so without a local copy the certificate that signs the plugin
exists in exactly one place you cannot inspect, and questions like "what is in CI right now" and "did
this artifact come from that certificate" stop being answerable at all.

The store is the right place for it and a file in a directory is not: the private half lands under
gpg-agent's protection instead of sitting in plaintext, and it is a KEY, not a CA — losing it costs
one re-run of this step, while the CAs that issued it never left their cards.

Carried as PKCS#12 because that is the one container holding key, leaf and issuer together, and with
the same random passphrase used above: pinentry asks once to unlock it and once to protect it, which
is gpg-agent doing its job, not a manual step bolted on.
The SAME passphrase twice, one per line, and that is not redundancy: when `-passin` and `-passout`
name the same file, OpenSSL reads line 1 for the first and line 2 for the SECOND. A one-line file
therefore fails at the passout with "Error reading password from BIO" — an I/O error message for what
is really an empty password, which is why it does not read like the file-format problem it is.
Each line is TERMINATED, which is the whole trick: `$tmp/keypass` is written with no trailing newline,
so concatenating it twice yields one line holding the passphrase twice — the same failure, reached by
a different route, and one that a test using a newline-terminated file cannot see.

### `if openssl pkcs12 -export -inkey "$tmp/leaf.key" -in "$tmp/leaf.crt" -certfile "$tmp/int.crt" \`

Nothing from here to the end of the local copy may abort the run. It is an audit convenience, the
deliverable (three secrets) is already set, and step 4 — the GPG key the releases are signed with — is
still to come: failing here used to take the whole bootstrap down AFTER the part that mattered
succeeded, which reads as "the script does nothing" for a reason that costs nothing.
The import is LOOPBACK, with the passphrase on a file descriptor. Left to pinentry, gpgsm asks for the
passphrase protecting the PKCS#12 — which is the random one generated above and deliberately never
displayed, so there is nobody who could answer it. A prompt no human can satisfy is worse than none:
it stops the run dead on a step whose whole purpose is to leave nothing manual behind.

### `for leftover in "$tmp"/f9-*.pem "$tmp/root.crt" "$tmp/int.crt" "$tmp/leaf.p12" "$tmp/p12.err" \`

The card material goes NOW, not at the exit trap: everything above is done with it, and a file that is
still needed cannot be deleted early — which makes "delete it here" a claim the rest of the step has to
keep true. The trap remains as the backstop for the paths that exit before this line. The CA key is not
in this list because it was never copied here: it was read in place, from the PKI that owns it.

### `fi`

No pinning to warn about: JetBrains RE-SIGNS every plugin with its own CA, which is what the user's
IDE verifies, and the vendor-uploads-a-public-key half of that design is still listed as "not
available yet" in the plugin-signing docs. So this key is only ever an upload credential and rotating
it is invisible — to users and to the Marketplace alike. Nothing to upload anywhere.

### `say "4/7  CI artifact signing key (GPG)"`

--- 4. CI artifact signing key (GPG) ------------------------------------------------------------------

### `if gpg --batch --import "$tmp/gpg/private.asc" 2>/dev/null; then`

The PRIVATE half is imported into your keyring, and that is custody rather than convenience: a GitHub
environment secret is WRITE-ONLY. Nothing — not the API, not the UI, not a workflow — can read back
what was uploaded, so a key that exists only there cannot be inspected, cannot be revoked with its own
revocation certificate, and cannot re-sign anything the day the workflow is not the thing signing. It
stays encrypted with the passphrase it was generated under, so what lands on disk is ciphertext.

### `mapfile -t certifiers < <(gpg --list-secret-keys --with-colons \`

--- certify the CI key with the maintainer's hardware key -------------------------------------------
This is what turns the CI key from "a key that happens to sign the artifacts" into "a key the
maintainer vouches for". Two concrete consequences, and the second is the important one:

  * a user who trusts the YubiKey key gets the CI key transitively — they do not have to take a bare
    fingerprint on faith from a file in the same repository an attacker would have edited;
  * if the CI key ever leaks, the certification can be REVOKED from hardware, which withdraws the
    maintainer's endorsement without needing anyone to notice a new file. A bare key has no such lever.

BOTH certification authorities sign it, not just one. The certifiers are derived rather than written
down: every primary key whose secret half lives on a SMARTCARD (field 15 of the `sec` colon record is
the token serial), which on this repository is the root key and the intermediate CA. A fingerprint
pasted into this script goes stale the first time a key is rotated and says nothing when it does;
a derived list cannot, and every certification is verified individually below.

`$15 != "+"` is what makes that test mean what it says, and without it the filter matched EVERY key.
GnuPG writes the token's serial into field 15 for a card-held key and a literal `+` for one whose
secret half is an ordinary file on disk — so `$15 != ""` is true for all of them. On this keyring
that silently widened two certifiers to five, three of them personal identities that must never
certify anything for a public repository. The question is "is there a serial", not "is the field
populated", and the two only look alike until a second key exists.

### `pass_stash="$HOME/.gnupg/claude-code-native-ci-passphrase.asc"`

The imported private half is useless without its passphrase, and the passphrase's only other home is a
write-only GitHub secret — so the local copy would be a key nobody, including you, can ever open. It is
stashed beside the keyring ENCRYPTED TO THE CAs, never in the clear: a plaintext passphrase file next
to the key it unlocks is the same thing as an unprotected key, spelled in two files instead of one.

### `gpg --batch --import "$tmp/gpg/public.asc" 2>/dev/null`

The public half again, deliberately: the private import above may have failed, and certification
needs the key present. A second import of a key already held is a no-op, not a duplicate.

### `for a in "${certifiers[@]}"; do`

The CAs certify EACH OTHER first, and that is structure rather than decoration: a bundle whose
blocks carry no signature between them is just three keys in a file, and a reader who trusts one CA
has no path to the other. Both directions, deliberately — which of the two a reader already holds
is not ours to decide, and GPG has no notion of issuer to derive an order from.

A LOCAL signature is what would silently defeat this: gpg will not add a second certification from a
key that has already signed, and an lsign is stripped on export — so the endorsement sits in the
keyring and reaches nobody. That is exactly why the verification below reads the exported FILE and
not the keyring, and why it prints the recovery when a link is missing.

### `gpg --batch --yes --local-user "$fpr" --quick-sign-key "$ci_fpr" \`

--quick-sign-key, never --quick-lsign-key: a LOCAL signature stays in your keyring and is stripped
on export, so the endorsement would be invisible to every user it exists for.

### `{ for fpr in "${certifiers[@]}"; do gpg --armor --export "$fpr"; done`

ONE file holding the CAs and the leaf they certify, and that is assurance rather than packaging.
A certification is only followable by someone who already holds the CERTIFIER's public key, so
publishing the CI key alone ships an endorsement nobody can check — which reads, to anybody
verifying, exactly like no endorsement at all. Split across three files it becomes three imports,
and the one people skip is the one carrying the assurance.

Exported AFTER certifying, so the leaf's block carries the signatures on its uid. Certifiers
first and leaf last, one `--export` each: a single call listing several keys emits them in
KEYRING order, which is not argument order, and a chain that has to be read bottom-up is a chain
nobody reads.

### `chain_home="$tmp/chain-verify"; mkdir -p "$chain_home"; chmod 700 "$chain_home"`

Verified as a FILE, never as a keyring query, and the distinction is the entire point of the
check. An export is precisely where a certification vanishes: a signature made with
`--quick-lsign-key` is LOCAL — it lives in the keyring and is stripped on export — so asking gpg
about the keyring reports a chain the user will never receive. Reading the bytes back through a
throwaway GNUPGHOME asks the only question that matters: does what is about to be committed
actually chain, for somebody who has nothing but this file?

### `survived() {`

$1 = signer fingerprint, $2 = signed fingerprint

### `survived() {`

Asks the file whether one CA vouches for another. Same question as below, different subject: this
one is what makes the bundle a CHAIN rather than a list.

### `have_key=$(GNUPGHOME="$chain_home" gpg --list-keys --with-colons 2>/dev/null \`

A missing key makes gpg exit non-zero, which is the ANSWER here, not an error: the check exists to
report it. Left bare it would abort the run instead, at the one point whose whole job is to say
what did not survive the export.

### `for fpr in "${certifiers[@]}" "$ci_fpr"; do`

--- publish the CAs where GitHub is not the publisher ---------------------------------------------
The step that makes any of the above mean something to a stranger, and it is the one that was
missing. Everything a verifier receives — the artifact, its signature, the bundle carrying the CAs
— arrives from this repository, so the chain proves internal CONSISTENCY and says nothing about
provenance: whoever can publish a release can publish a bundle that agrees with it. The endorsement
only becomes evidence for someone holding the CA fingerprint from a channel this repository does
not control.

keys.openpgp.org is that channel, cheaply: it serves any key BY FINGERPRINT with no identity
checks, so `gpg --recv-keys <CA fingerprint>` reaches an operator unrelated to GitHub, and the two
copies of the CA either agree or the discrepancy is the story. It strips uids that are not
email-verified, which costs nothing here — the fingerprint is the anchor, not the name.

Uploading a public key is irreversible in practice, and deliberately unconditional anyway: a
certification nobody can anchor is the failure this whole step exists to avoid, and re-uploading an
unchanged key is a no-op.

### `if gh gpg-key list >/dev/null 2>&1; then`

--- register the CI key on the GitHub ACCOUNT --------------------------------------------------------
This step did not exist, and its absence is the whole reason v5.0.0 shipped with an unverified tag.

Certifying the key with the YubiKey (above) makes it trustworthy to a human running `gpg --verify`.
It does nothing for the "Verified" badge, which is a different mechanism entirely: GitHub marks a
signature verified only when the tagger email, an email in a uid of a key REGISTERED ON THE ACCOUNT,
and a verified account email all agree. Certification is not registration, and the two were conflated.

Registered from the SINGLE-key export, never from docs/trust-chain.asc: `gh gpg-key add` posts one
armored key, so handing it a bundle either registers the first block or is rejected outright —
and the first block is a CA, which is emphatically not the key that signs the tag. The endorsement
is irrelevant here anyway; GitHub reads the uid's email and nothing else.

### `short=${ci_fpr: -16}`

The check that matters. A key registered with NO email can never verify a tag, which is precisely
the state the previous key was in: `emails=` came back empty and nothing said so.

### `say "5/7  Repository deploy key"`

--- 5. repository deploy key --------------------------------------------------------------------------

### `if has_secret DEPLOY_KEY && ! ask "DEPLOY_KEY is already set. Rotate it?"; then`

An SSH key scoped to THIS repository, for the only write CI performs: pushing the release tag. It is
generated here, the public half is registered on the repository and the private half goes straight into
the environment secret. Nothing is left on this machine — the pair lives in the 0700 temp dir and the
EXIT trap shreds it, so it exists in exactly two places: that secret, and the repository's key list.

Two properties, both deliberate:
  * no passphrase — a CI job has nobody to type one, and what protects it is the environment scoping of
    the secret, not a passphrase stored in the same environment as the key;
  * repository scope. An account SSH key would carry write access to everything you can push to; a
    deploy key carries this repository and nothing else.

NB gh binds a deploy key to the TOKEN that created it: de-authorizing the GitHub CLI, or letting that
token expire, REMOVES the key. If a release ever fails on the tag push, check it is still there:
  gh api "repos/$REPO/keys"

### `for id in $(gh api "repos/$REPO/keys" --jq '.[] | select(.title | startswith("ci-release-")) | .id' 2>/dev/nul`

Retire the ones this script registered before. A rotation that leaves the previous key authorized has
rotated nothing: the old private half is still a write credential wherever it ended up.

### `info "fingerprint: $(ssh-keygen -lf "$tmp/deploy_key.pub" | cut -d' ' -f2)"`

The fingerprint, not the key material: it is what `gh api "repos/$REPO/keys"` reports, so this line is
the one thing that lets you confirm later that the registered key is the one this run generated.

### `say "6/7  Verification"`

--- 6. verify -----------------------------------------------------------------------------------------

### `repo_secrets=$(gh secret list --repo "$REPO" --json name -q '.[].name' 2>/dev/null || true)`

A repository-level secret is readable by EVERY job, including one introduced in a pull request. That is
precisely the exposure the environment scoping exists to avoid, so flag any that exist.

### `say "7/7  Branch protection"`

--- 7. branch protection ------------------------------------------------------------------------------

## scripts/drift-baseline.properties

### `sdk=0.3.270`

Protocol baseline — the versions the plugin's protocol layer was last verified against.
`./gradlew checkDrift` compares these (and the vendored sdk.d.ts) against the latest published
SDK and the locally-installed (auto-updated) `claude` binary, then reports the surface drift.

These are the last *reconciled* versions: the detector updates the live tools to latest (npm update +
claude --update), then reports any protocol surface the plugin doesn't model yet. After reconciling a
report (and `npm update` having bumped the vendored SDK), bump these to match.

## scripts/gen-ci-signing-key.sh

### `set -euo pipefail`

Generate the CI-only GPG key that signs release artifacts in GitHub Actions.

WHY THIS KEY IS SEPARATE FROM THE MAINTAINER KEY, AND MUST STAY SEPARATE
The maintainer key lives on a YubiKey. It cannot be exported — that is the point of it — so
it cannot sign inside a runner. Automating artifact signatures therefore requires a SOFTWARE key whose
private material sits in a GitHub secret, readable by the job that references it and by anything that
compromises the runner.

That is a real, accepted trade-off, and the mitigation is that the two keys must never be confusable:

  * the MAINTAINER key signs COMMITS and TAGS. That signature means "a person chose to release this."
  * this CI key signs RELEASE ARTIFACTS. That signature means "this workflow produced these bytes."

If both signatures looked alike, a leaked CI key would impersonate the maintainer. So this key carries a
uid that says what it is out loud, and it EXPIRES — a leaked key that expires stops being useful on its
own, without anyone having to notice the leak first.

This script never touches your real keyring: it works in a throwaway GNUPGHOME under a temp directory,
emits what you need, and deletes it.

  ./scripts/gen-ci-signing-key.sh                 # print everything (manual setup / rotation)
  ./scripts/gen-ci-signing-key.sh --outdir DIR    # write private.asc, public.asc, passphrase into DIR

--outdir exists so scripts/bootstrap-ci.sh can pipe the private key straight into `gh secret set`
without it ever crossing a terminal, a scrollback buffer, or a shell history.

### `maintainer_fpr="${CI_KEY_FROM:-$(git config --get user.signingkey || true)}"`

The key MUST carry an email, and it must be the maintainer's verified GitHub address.

The first version of this script set only Name-Real, so the key was generated with no email in its uid
at all. Everything still worked — artifacts were signed, `gpg --verify` passed — except the one thing
that is visible to everyone: GitHub never showed the release tag as Verified, and could not, because
marking a signature Verified requires THREE things to agree (docs: "Associating an email with your GPG
key"): the tagger's email, an email in a uid of the registered key, and a verified email on the account.
A key with no email fails the second forever. GitHub's own API reported the key as `emails=` — empty.

Using the maintainer's address here does blunt one edge of the separation this file argues for, so state
what actually keeps the two keys distinguishable now: the uid NAME says out loud that this is a CI key,
the key EXPIRES after a year, and it is certified by the hardware key rather than merely asserted. What
it no longer provides is separation by address — and it never could, because GitHub offers no way to
verify a tag signed by a key bearing an address that is not yours.

READ from the maintainer key, never written here. The project publishes no contact address anywhere (see
CHANGELOG 5.0.0), and a committed script is published: hardcoding it would put the address into the
repository, into every clone, and into the search index — undoing that decision to save one lookup.
It also cannot drift, since the address that must match is by definition the one on the key.

## scripts/probe-binary.sh

### `set -euo pipefail`

probe-binary.sh — sanity-check the local `claude` binary against the set of
stream-json event `type`s that the plugin's ProtocolParser knows how to handle.

Usage:
  bash scripts/probe-binary.sh [path-to-claude] [fixture.jsonl]

Defaults:
  path-to-claude = $HOME/.local/bin/claude
  fixture.jsonl  = a single canned user message ("Say hi and exit.")

Exit codes:
  0  -> only expected `type` values seen
  1  -> usage / environment error (binary missing, timeout with no output, ...)
  2  -> at least one UNKNOWN_EVENT_TYPE was observed (drift signal for CI)

This is meant to be cheap and offline-friendly enough for CI: it does NOT
require an API key to be valid (we only care about what `type`s the binary
emits; even an auth error surfaces as a `result`/`system` event we recognise).

### `EXPECTED_TYPES=(system assistant user result stream_event control_request control_response rate_limit_event)`

Hardcoded inline expected types — keep in sync with
src/main/kotlin/dev/lain/claudejb/model/protocol/parse/ProtocolParser.kt

### `build_stdin() {`

Build the stdin payload: either user-supplied fixture or a canned one-shot.

### `set +e`

Run the binary with the same flags the plugin uses to talk to it.
`timeout 60` caps the whole exchange; we don't care about its exit code,
only about what landed on stdout.

### `mapfile -t SEEN_TYPES < <(python3 - "$OUT" <<'PY'`

Extract unique `type` values from the NDJSON stream.
We use a small Python pass to stay robust against malformed lines (the lenient
codec in production swallows those silently — that's exactly the thing we want
to surface here).

## scripts/css-usage.py

### `(module)`

Which CSS classes in the stylesheet are never named by the web app.

A reporting aid for the periodic CSS sweep, not a gate: `src/test/frontend/css-contract.test.js` is the
gate, and it checks the opposite direction (a class the JS emits with no rule). This one answers "is this
rule still reachable?", which is what lets dead CSS be deleted instead of accumulating.

The stylesheet is not one file: it is the parts of `jcef/css/`, concatenated in CASCADE ORDER by
`JcefHost.CSS_PARTS`. That list is READ OUT OF THE KOTLIN, never copied here — this script used to open a
single `app.css` that the split deleted, and it had been dead ever since. `readdirSync`-style discovery
would be the same mistake in a slower form: a part added to the host but not seen here would go unreported.

It is deliberately CONSERVATIVE about "used": any occurrence of the bare name anywhere in the JS or the
shell counts, because classes are also built by concatenation (`'pill-dot ' + status`). A name reported
here is a CANDIDATE to delete — read the rule before removing it.

Known-good residue, so nobody re-derives it: `hljs-*` plus `class_` and `function_` are the syntax theme,
emitted by the vendored highlight.js and not by our code; `stopped` is one of the status words the host
sends and the page concatenates.

### `def css_parts() -> list[str]:`

The stylesheet's parts, in cascade order, as `JcefHost` concatenates them.

    The DECLARATION, not the first mention: `CSS_PARTS` is also read higher up, where the page is built.

### `origin: dict[str, str] = {}`

Which part defines it, so a candidate can be opened rather than hunted for. First definition wins,
which is the one the cascade starts from.

## scripts/split-css.py

### `(module)`

One-shot: split `app.css` into per-component files at its own section boundaries.

Mechanical by construction and verified as such: the parts are contiguous line ranges of the original, in
the original order, and the script refuses to write anything unless concatenating them reproduces the input
byte for byte. CSS order is semantics (later rules win), so a split that reorders is a rewrite, not a split.

Run once, from the repository root. Kept in the tree as the record of how the parts were cut.

### `PARTS = [`

(file, first line, last line) — 1-based and inclusive, cut at the file's own section banners.

## bin/fake-claude

### `(module)`

fake-claude — deterministic stand-in for the `claude` binary used by the test pyramid.

The plugin spawns the real `claude` binary in stream-json mode (`--print --output-format stream-json
--input-format stream-json --verbose --permission-prompt-tool stdio …`). For tests we replace that
binary with this script: it replays a JSONL fixture line-by-line to stdout while honouring a tiny
protocol-aware DSL so scenarios like `can_use_tool` (which require waiting on the host's
`control_response`) work end-to-end without a real model in the loop.

Selection: the fixture path comes from the env var `FAKE_FIXTURE`. If unset, the script emits a
minimal `system/init` and a tiny `result` so a smoke launch doesn't hang. CLI args are accepted and
ignored (we match the real binary's surface so the plugin's `buildArgs` keeps working unchanged).

ONE exception to "args are ignored": `claude auth …`. The plugin does not only stream — before it
spawns anything it asks WHO it would run as (`AuthGate.hasCredential` → `AuthCli.status`), and
`ClaudeSession.start()` refuses to launch without an answer. A stand-in that replays stream-json at
that question parses as "logged out", so on a machine holding no real credential the UI suite never
gets a session at all: the tab sits on the sign-in card. See `_run_auth` below. The branch is keyed
on the FIRST argument being exactly `auth` — the streaming invocation always begins with `--print`
(`SessionLauncher.buildArgs`), so the stream-json path cannot be diverted into it.

DSL on top of plain JSONL lines:
  - A line with key `_wait_stdin` is consumed: the script reads one line from stdin (typically the
    host's `control_response`) before continuing. Useful right after emitting a `control_request`.
  - A line with key `_sleep_ms` sleeps that many milliseconds (good for racing tests).
  - Anything else: emitted verbatim to stdout as a single line, followed by `
` and flushed.

Exit codes:
  0  fixture consumed, EOF on stdin (or end of script); or `auth status` answered.
  1  fixture file unreadable (only when FAKE_FIXTURE was set); or an `auth` subcommand this
     stand-in deliberately does not implement (`auth login` — it mints no credentials).
  2  stdin closed unexpectedly while waiting for a control_response.

This script never makes network calls and prints nothing to stderr by default. Set FAKE_DEBUG=1 to
trace what it's doing (useful when a fixture stalls).

### `FAKE_ACCOUNT = {`

The identity `auth status` reports. Shaped exactly like the real binary's reply (verified against
`claude` 2.1.223 and modelled by `AuthCli.AuthState`: loggedIn, authMethod, apiProvider, email,
orgId, orgName, subscriptionType — lowerCamelCase, one JSON object, exit 0), and OBVIOUSLY not a
person: `.invalid` is the RFC 2606 reserved TLD, so the address cannot resolve or belong to anyone,
and the org says out loud what it is. Anyone who sees this in a dashboard is looking at the test
double, not at their account — which matters, because a successful reply that names an account is
filed in the IDE password safe (`AuthCli.status` → `SecretStore.AUTH_STATUS`), and a sandbox IDE
shares the OS keyring with the developer's real one.

NOTHING SECRET-SHAPED IN HERE, and that is a rule rather than an accident: no token, no key, no
`sk-ant-…`-looking string, nothing an automated scanner should ever flag. `auth status` does not
emit credentials in the real binary either — it describes an identity, it does not hand one over.

### `def _run_auth(rest: list[str]) -> int:`

`claude auth …` — the non-streaming surface the plugin probes before it launches anything.

    `status` is the one that unblocks the UI suite: `AuthGate.hasCredential` falls through to it when
    the IDE safe holds nothing, and a machine with no answer here never starts a session.

    `login` is answered with an honest FAILURE, not a fake success. It exists only so the path is
    deterministic and fast: `CredentialsVault.renewOnDisk` shells out to it (`AuthCli.refreshUsingOwnFiles`)
    when a vaulted credential has expired, and without this branch the stand-in would replay a whole
    stream-json fixture as the answer to a login and could sit there until the 20s refresh timeout, in
    the middle of `launch()`. Exiting non-zero says what is true — a stand-in mints no credentials — and
    the plugin then re-asks `hasCredential`, which `status` answers.

### `def _run_default_smoke() -> int:`

No fixture → emit just enough that the plugin's init handshake finishes cleanly.

### `_emit({`

The initialize control request will arrive on stdin from the plugin; reply success then quit.

### `try:`

Wait for the initialize control request and reply success, then end the turn.

### `_emit({"type": "result", "subtype": "success", "result": "ok", "session_id": session_id})`

A user message before init: bounce out a tiny result.

### `_emit_line(raw)`

Not JSON — emit verbatim and continue.

### `try:`

Drain any pending stdin so the plugin's writer doesn't block on a broken pipe.

### `if len(argv) > 1 and argv[1] == "auth":`

`claude auth …`, matched on the FIRST argument only. The streaming invocation always starts with
`--print` (SessionLauncher.buildArgs), so this cannot swallow it — and everything below stays
byte-identical to what the integration fixtures have always seen.

### `fixture = os.environ.get("FAKE_FIXTURE", "").strip()`

The real binary accepts many flags; we ignore the rest of them (the protocol is what matters).
