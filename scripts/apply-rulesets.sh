#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

dry_run=false
[ "${1:-}" = "--dry-run" ] && dry_run=true

command -v gh >/dev/null || { echo "gh is required: https://cli.github.com" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

repo=$(gh repo view --json nameWithOwner -q .nameWithOwner)
echo "repository: $repo"

existing=$(gh api "repos/$repo/rulesets" --paginate 2>/dev/null || echo '[]')

for file in .github/rulesets/*.json; do
  name=$(jq -r .name "$file")
  id=$(echo "$existing" | jq -r --arg n "$name" 'map(select(.name == $n)) | .[0].id // empty')

  if $dry_run; then
    if [ -n "$id" ]; then
      echo "would UPDATE ruleset '$name' (id $id) from $file"
    else
      echo "would CREATE ruleset '$name' from $file"
    fi
    continue
  fi

  body=$(jq 'walk(if type == "object"
                  then with_entries(select(.key | startswith("_comment") | not))
                  else . end)' "$file")

  if [ -n "$id" ]; then
    echo "updating '$name' (id $id)…"
    printf '%s' "$body" | gh api --method PUT "repos/$repo/rulesets/$id" --input - >/dev/null
  else
    echo "creating '$name'…"
    printf '%s' "$body" | gh api --method POST "repos/$repo/rulesets" --input - >/dev/null
  fi
done

$dry_run && exit 0

echo
echo "Active rulesets:"
for attempt in 1 2 3; do
  out=$(gh api "repos/$repo/rulesets" -q '.[] | "  \(.name)  [\(.enforcement)]  bypass_actors=\(.bypass_actors|length)"' 2>/dev/null || true)
  [ -n "$out" ] && break
  sleep 2
done
if [ -n "$out" ]; then
  printf '%s\n' "$out"
else
  echo "  (none returned — check manually: gh api repos/$repo/rulesets)"
fi

cat <<'EOF'

Two things to check that this script cannot check for you:

  1. THE APPROVAL COUNT IS 0, deliberately. GitHub does not let an author approve their own pull
     request, so on a single-maintainer repository "require 1 approval" plus no bypass actors means
     nothing can ever be merged — not by PR, not by push, not by admin. What is enforced instead is
     mechanical and cannot be argued with: a pull request, an up-to-date branch, and every status check
     green. Raise it to 1 (and re-enable code-owner review) the day a second maintainer has write access.

  2. The required status check NAMES have to resolve. A ruleset references a check by the job's DISPLAY
     name; rename a job and the gate does not fail — it silently stops applying. After the first CI run
     on a pull request, confirm every name in .github/rulesets/*.json appears in that PR's check list.
EOF
