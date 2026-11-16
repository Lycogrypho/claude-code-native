#!/usr/bin/env python3

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JCEF = ROOT / "src" / "main" / "resources" / "jcef"
JCEF_HOST = ROOT / "src" / "main" / "kotlin" / "dev" / "lain" / "claudejb" / "ui" / "jcef" / "JcefHost.kt"

COMMENT = re.compile(r"/\*.*?\*/", re.S)
CLASS_IN_CSS = re.compile(r"\.([a-zA-Z][\w-]*)")
WORD = re.compile(r"[a-zA-Z][\w-]*")
CSS_PART = re.compile(r'"([\w-]+\.css)"')


def css_parts() -> list[str]:
    source = JCEF_HOST.read_text(encoding="utf-8")
    start = source.find("val CSS_PARTS = listOf(")
    if start < 0:
        raise SystemExit(f"css-usage: could not find CSS_PARTS in {JCEF_HOST}")
    block = source[start : source.index(")", start)]
    parts = CSS_PART.findall(block)
    if not parts:
        raise SystemExit(f"css-usage: CSS_PARTS in {JCEF_HOST} listed no .css files")
    return parts


def main() -> int:
    origin: dict[str, str] = {}
    for part in css_parts():
        path = JCEF / "css" / part
        if not path.exists():
            raise SystemExit(f"css-usage: {path} is in CSS_PARTS but does not exist")
        for name in CLASS_IN_CSS.findall(COMMENT.sub("", path.read_text(encoding="utf-8"))):
            origin.setdefault(name, part)

    sources = sorted(JCEF.glob("app-*.js")) + [JCEF / "shell.html"]
    words = set()
    for path in sources:
        words.update(WORD.findall(path.read_text(encoding="utf-8")))

    dead = sorted(name for name in origin if name not in words)
    print(f"{len(origin)} classes defined, {len(dead)} never named by the app:")
    for name in dead:
        print(f"  {origin[name]:16} {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
