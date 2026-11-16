#!/usr/bin/env bash

set -euo pipefail

BINARY="${1:-$HOME/.local/bin/claude}"
FIXTURE="${2:-}"

if [[ ! -x "$BINARY" ]]; then
    echo "ERROR: claude binary not found or not executable at: $BINARY" >&2
    exit 1
fi

TS="$(date +%s)"
OUT="/tmp/probe-${TS}.jsonl"

EXPECTED_TYPES=(system assistant user result stream_event control_request control_response rate_limit_event)

is_expected() {
    local t="$1"
    local e
    for e in "${EXPECTED_TYPES[@]}"; do
        [[ "$t" == "$e" ]] && return 0
    done
    return 1
}

build_stdin() {
    if [[ -n "$FIXTURE" ]]; then
        if [[ ! -r "$FIXTURE" ]]; then
            echo "ERROR: fixture not readable: $FIXTURE" >&2
            exit 1
        fi
        cat "$FIXTURE"
    else
        printf '%s\n' '{"type":"user","message":{"role":"user","content":"Say hi and exit."},"parent_tool_use_id":null}'
    fi
}

echo "probe-binary: binary=$BINARY"
echo "probe-binary: capturing stdout -> $OUT"

set +e
build_stdin | timeout 60 "$BINARY" \
    --print \
    --output-format stream-json \
    --input-format stream-json \
    --verbose \
    --permission-prompt-tool stdio \
    >"$OUT" 2>/tmp/probe-${TS}.stderr
RC=$?
set -e

if [[ ! -s "$OUT" ]]; then
    echo "ERROR: binary produced no stdout (rc=$RC). stderr follows:" >&2
    cat "/tmp/probe-${TS}.stderr" >&2 || true
    exit 1
fi

mapfile -t SEEN_TYPES < <(python3 - "$OUT" <<'PY'
import json, sys, collections
counts = collections.Counter()
with open(sys.argv[1], "r", encoding="utf-8", errors="replace") as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            counts["<malformed>"] += 1
            continue
        t = obj.get("type", "<missing>")
        counts[t] += 1
for t, n in sorted(counts.items()):
    print(f"{t}\t{n}")
PY
)

unknown=0
echo
echo "Event types observed:"
printf '  %-24s %s\n' "TYPE" "COUNT"
printf '  %-24s %s\n' "----" "-----"
for row in "${SEEN_TYPES[@]}"; do
    t="${row%%	*}"
    n="${row##*	}"
    if is_expected "$t"; then
        marker="ok"
    else
        marker="UNKNOWN"
        unknown=1
        echo "UNKNOWN_EVENT_TYPE: $t" >&2
    fi
    printf '  %-24s %-6s %s\n' "$t" "$n" "$marker"
done

echo
if [[ $unknown -ne 0 ]]; then
    echo "probe-binary: drift detected — at least one unexpected event type was emitted." >&2
    echo "probe-binary: full capture preserved at $OUT" >&2
    exit 2
fi

echo "probe-binary: ok — all observed event types are known to the plugin."
echo "probe-binary: capture: $OUT"
exit 0
