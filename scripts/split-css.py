#!/usr/bin/env python3

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JCEF = ROOT / "src" / "main" / "resources" / "jcef"
SOURCE = JCEF / "app.css"
OUT = JCEF / "css"

PARTS = [
    ("base.css", 1, 193),
    ("transcript.css", 194, 953),
    ("composer.css", 954, 1584),
    ("permissions.css", 1585, 1930),
    ("dashboard.css", 1931, 2657),
    ("boot.css", 2658, 3009),
    ("tabs.css", 3010, None),
]


def main() -> int:
    lines = SOURCE.read_text(encoding="utf-8").splitlines(keepends=True)
    parts = []
    for name, start, end in PARTS:
        stop = len(lines) if end is None else end
        parts.append((name, "".join(lines[start - 1 : stop])))

    if "".join(body for _, body in parts) != "".join(lines):
        print("refusing to write: the parts do not reproduce app.css exactly", file=sys.stderr)
        return 1

    OUT.mkdir(exist_ok=True)
    for name, body in parts:
        (OUT / name).write_text(body, encoding="utf-8")
        print(f"{name}: {body.count(chr(10))} lines")
    SOURCE.unlink()
    return 0


if __name__ == "__main__":
    sys.exit(main())
