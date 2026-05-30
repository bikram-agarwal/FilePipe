"""
Walk app/src/main/java and extract every Material Symbols ligature name we use.

Sources of truth (any one match is enough to keep the glyph):
- `*MaterialRoundedSymbol(name = "xxx", ...)` literal calls.
- Any Kotlin identifier containing `icon` or `symbol`, e.g. `iconName = "xxx"`,
  `leadingIcon = "xxx"`, `materialSymbolName = "xxx"`, or `symbolName = "xxx"`.
- String literals returned from `*.materialSymbolName()`-style helper functions,
  including expression-body and block-body `when` branches.

We err on the side of including extras: ligatures are cheap (~50-200 bytes
each), but a missing one ships as an invisible icon in production.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
JAVA_ROOT = REPO / "app" / "src" / "main" / "java"
OUTPUT_LIST = Path(__file__).with_name("ligatures.txt")
OUTPUT_REPORT = Path(__file__).with_name("ligatures_report.json")

LIGATURE = r'"([a-z][a-z0-9_]+)"'
ICON_SYMBOL_IDENTIFIER = r'(?=[A-Za-z_][A-Za-z0-9_]*)(?=[A-Za-z0-9_]*(?:[Ii]con|[Ss]ymbol))[A-Za-z_][A-Za-z0-9_]*'
ICON_SYMBOL_ASSIGNMENT = rf'\b{ICON_SYMBOL_IDENTIFIER}\s*=\s*{LIGATURE}'
ICON_SYMBOL_DEFAULT_PARAM = rf'\b{ICON_SYMBOL_IDENTIFIER}\s*:\s*String\??\s*=\s*{LIGATURE}'
ICON_SYMBOL_IF_ELSE_IDENTIFIER = rf'(?:name|{ICON_SYMBOL_IDENTIFIER})'
KOTLIN_OPEN_BRACE = r'\{'
KOTLIN_CLOSE_BRACE = r'\}'
ICON_SYMBOL_WHEN_BRANCH = re.compile(r'->\s*"([a-z][a-z0-9_]+)"')
ICON_SYMBOL_WHEN_BLOCKS: list[tuple[str, re.Pattern[str]]] = [
    (
        "icon_symbol_when_assignment",
        re.compile(
            rf'\b{ICON_SYMBOL_IDENTIFIER}\s*=\s*when\b[^{KOTLIN_OPEN_BRACE}]*{KOTLIN_OPEN_BRACE}(?P<body>.*?)^\s*{KOTLIN_CLOSE_BRACE}',
            re.DOTALL | re.MULTILINE,
        ),
    ),
    (
        "icon_symbol_when_function",
        re.compile(
            rf'\bfun\s+[A-Za-z0-9_.]*{ICON_SYMBOL_IDENTIFIER}\s*\([^)]*\)\s*:\s*String\s*=\s*when\b[^{KOTLIN_OPEN_BRACE}]*{KOTLIN_OPEN_BRACE}(?P<body>.*?)^\s*{KOTLIN_CLOSE_BRACE}',
            re.DOTALL | re.MULTILINE,
        ),
    ),
    (
        "icon_symbol_return_when_function",
        re.compile(
            rf'\bfun\s+[A-Za-z0-9_.]*{ICON_SYMBOL_IDENTIFIER}\s*\([^)]*\)\s*:\s*String\s*{KOTLIN_OPEN_BRACE}.*?\breturn\s+when\b[^{KOTLIN_OPEN_BRACE}]*{KOTLIN_OPEN_BRACE}(?P<body>.*?)^\s*{KOTLIN_CLOSE_BRACE}',
            re.DOTALL | re.MULTILINE,
        ),
    ),
]

PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("MaterialRoundedSymbol", re.compile(rf'\b[A-Za-z0-9_]*MaterialRoundedSymbol\s*\(\s*(?:name\s*=\s*)?{LIGATURE}', re.MULTILINE)),
    # Do not use [^)]* here: modifier args often contain `)` (e.g. Modifier.size(24.dp)).
    ("MaterialRoundedSymbol_multiline", re.compile(
        rf'\b[A-Za-z0-9_]*MaterialRoundedSymbol\s*\(\s*.*?\bname\s*=\s*{LIGATURE}',
        re.DOTALL,
    )),
    ("icon_symbol_assignment", re.compile(ICON_SYMBOL_ASSIGNMENT)),
    ("icon_symbol_default_param", re.compile(ICON_SYMBOL_DEFAULT_PARAM)),
    # `name = if (cond) "icon_a" else "icon_b"` or wrapper variants like
    # `iconName = if (...) "restore_from_trash" else "delete_forever"`.
    # The simpler `name = "x"` patterns stop at the first `)` inside the condition.
    ("symbol_name_if_else", re.compile(
        rf'\b{ICON_SYMBOL_IF_ELSE_IDENTIFIER}\s*=\s*if\s*\([^)]*\)\s*(?:{KOTLIN_OPEN_BRACE}\s*)?"([a-z][a-z0-9_]+)"\s*(?:{KOTLIN_CLOSE_BRACE}\s*)?else\s*(?:{KOTLIN_OPEN_BRACE}\s*)?"([a-z][a-z0-9_]+)"',
        re.DOTALL,
    )),
    # Enum constructor entries like `ARCHIVE("archive")`.
    ("enum_entry_first_string_arg", re.compile(
        r'^\s*[A-Z][A-Z0-9_]*\(\s*"([a-z][a-z0-9_]+)"\s*\)\s*,?',
        re.MULTILINE,
    )),
    # Enum constructor entries like `Notes(R.string.main_tab_notes, "notes")`.
    ("enum_entry_second_string_arg", re.compile(
        r'^\s*[A-Z][A-Za-z0-9_]*\(\s*R\.string\.\w+\s*,\s*"([a-z][a-z0-9_]+)"',
        re.MULTILINE,
    )),
]

ENABLED_FILES_HINT = {
    "MaterialRoundedSymbol",
    "icon",
    "Icon",
    "symbol",
    "Symbol",
}

EXCLUDE_DIRS = {"build", "generated", ".gradle"}


def file_is_relevant(text: str) -> bool:
    return any(token in text for token in ENABLED_FILES_HINT)


def strip_kotlin_comments(text: str) -> str:
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.DOTALL)
    return re.sub(r'//.*', '', text)


def collect_ligatures_from_match(
    by_pattern: dict[str, set[str]],
    pattern_name: str,
    groups: tuple[str | None, ...],
) -> None:
    for ligature in groups:
        if ligature is None:
            continue
        # Filter obvious non-ligatures.
        if ligature in {"name", "symbolName", "materialSymbolName"}:
            continue
        if ligature.startswith("ic_") or ligature.startswith("symbol:"):
            continue
        by_pattern[pattern_name].add(ligature)


def harvest() -> dict[str, set[str]]:
    by_pattern: dict[str, set[str]] = {
        name: set()
        for name, _ in PATTERNS + ICON_SYMBOL_WHEN_BLOCKS
    }
    for path in JAVA_ROOT.rglob("*.kt"):
        if any(part in EXCLUDE_DIRS for part in path.parts):
            continue
        raw_text = path.read_text(encoding="utf-8")
        if not file_is_relevant(raw_text):
            continue
        text = strip_kotlin_comments(raw_text)
        for name, pattern in PATTERNS:
            for match in pattern.finditer(text):
                collect_ligatures_from_match(by_pattern, name, match.groups())
        for name, pattern in ICON_SYMBOL_WHEN_BLOCKS:
            for match in pattern.finditer(text):
                for branch_match in ICON_SYMBOL_WHEN_BRANCH.finditer(match.group("body")):
                    collect_ligatures_from_match(by_pattern, name, branch_match.groups())
    return by_pattern


def main() -> int:
    by_pattern = harvest()
    union: set[str] = set().union(*by_pattern.values())
    union.discard("")

    OUTPUT_LIST.write_text("\n".join(sorted(union)) + "\n", encoding="utf-8")

    report = {name: sorted(values) for name, values in by_pattern.items()}
    report["_union_count"] = len(union)
    OUTPUT_REPORT.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")

    print(f"Collected {len(union)} unique Material Symbols ligatures")
    print(f"  -> {OUTPUT_LIST}")
    print(f"  -> {OUTPUT_REPORT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
