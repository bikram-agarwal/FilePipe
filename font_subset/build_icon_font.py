"""
Build the app's Material Symbols icon subset fonts.

Two stages, run in sequence by default:

1. HARVEST - walk app/src/main/java and extract every Material Symbols ligature
   name the app references, writing font_subset/ligatures.txt (+ a
   ligatures_report.json broken down by match pattern). Pure stdlib; no deps.
   Sources of truth (any one match keeps the glyph):
   - `*MaterialRoundedSymbol(name = "xxx", ...)` literal calls.
   - Any Kotlin identifier containing `icon`/`symbol`, e.g. `iconName = "xxx"`,
     `leadingIcon = "xxx"`, `materialSymbolName = "xxx"`, `symbolName = "xxx"`.
   - String literals returned from `*.materialSymbolName()`-style helpers,
     including expression-body and block-body `when` branches.
   We err on the side of extras: ligatures are cheap (~50-200 bytes each), but a
   missing one ships as an invisible icon in production.

2. SUBSET - take the Material Symbols Rounded variable TTF and produce a pair of
   small static TTFs (one filled, one outlined) containing only the harvested
   ligatures, then copy both into app/src/main/res/font/ and delete scratch
   files. For each FILL value:
   a. INSTANCE the variable font at our axis values (FILL=0/1, wght=500, GRAD=0,
      opsz=24) - collapses the per-glyph variation table (gvar, the bulk of the
      14 MB source) into baked outlines.
   b. SUBSET the static font: ligature_resolve.expand_wanted_icon_names maps each
      icon string (e.g. delete_outline) to the real LigGlyph output (e.g. delete)
      plus its component glyphs; pyftsubset runs with --no-layout-closure so we
      do not pull in every ligature that shares the Latin alphabet.
   Outputs (both ~100-300 KB, copied into app/src/main/res/font/):
     material_symbols_rounded_subset.ttf           (FILL=1, filled)
     material_symbols_rounded_outlined_subset.ttf  (FILL=0, outlined)

Usage (run from the repo root):
  python font_subset/build_icon_font.py                # harvest + subset (normal)
  python font_subset/build_icon_font.py --harvest-only # stage 1 only; no fonttools needed
  python font_subset/build_icon_font.py --skip-harvest # stage 2 only; reuse existing ligatures.txt

The subset stage requires `fonttools` (`pip install fonttools`). Place the full
variable `material_symbols_rounded.ttf` in font_subset/ (from Google Fonts /
Material Symbols) or set MATERIAL_SYMBOLS_ROUNDED_TTF to its path.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
JAVA_ROOT = REPO / "app" / "src" / "main" / "java"
APP_FONT_DIR = REPO / "app" / "src" / "main" / "res" / "font"
LIGATURES_FILE = HERE / "ligatures.txt"
LIGATURES_REPORT_FILE = HERE / "ligatures_report.json"
GLYPHS_EXPANDED_FILE = HERE / "glyphs_expanded.txt"


# ===========================================================================
# Stage 1 - harvest ligature names from Kotlin source (pure stdlib)
# ===========================================================================

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
    # Enum constructor entries like `Swipe("swipe", "swipe_left", R.string.settings_swipe_section)`.
    ("enum_entry_route_then_icon_arg", re.compile(
        r'^\s*[A-Z][A-Za-z0-9_]*\(\s*"[a-z][a-z0-9_]+"\s*,\s*"([a-z][a-z0-9_]+)"\s*,\s*R\.string\.\w+',
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


def run_harvest() -> int:
    by_pattern = harvest()
    union: set[str] = set().union(*by_pattern.values())
    union.discard("")

    LIGATURES_FILE.write_text("\n".join(sorted(union)) + "\n", encoding="utf-8")

    report = {name: sorted(values) for name, values in by_pattern.items()}
    report["_union_count"] = len(union)
    LIGATURES_REPORT_FILE.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")

    print(f"Collected {len(union)} unique Material Symbols ligatures")
    print(f"  -> {LIGATURES_FILE}")
    print(f"  -> {LIGATURES_REPORT_FILE}")
    return 0


# ===========================================================================
# Stage 2 - instance + subset the variable font (needs fonttools)
# ===========================================================================

# Two-variant output: filled keeps the original name so callers that don't opt
# into the outlined variant stay on the same path, outlined gets its own file.
VARIANTS = [
    {
        "name": "filled",
        "fill": 1,
        "instanced": HERE / "material_symbols_rounded_instanced.ttf",
        "subset": HERE / "material_symbols_rounded_subset.ttf",
        "resource": APP_FONT_DIR / "material_symbols_rounded.ttf",
    },
    {
        "name": "outlined",
        "fill": 0,
        "instanced": HERE / "material_symbols_rounded_outlined_instanced.ttf",
        "subset": HERE / "material_symbols_rounded_outlined_subset.ttf",
        "resource": APP_FONT_DIR / "material_symbols_rounded_outlined.ttf",
    },
]

# Axes held constant across both variants. FILL is per-variant and injected
# below when assembling the instancer argv.
SHARED_AXES = {
    "wght": 500,
    "GRAD": 0,
    "opsz": 24,
}


def _source_ttf_candidates() -> list[Path]:
    candidates: list[Path] = []
    env_path = os.environ.get("MATERIAL_SYMBOLS_ROUNDED_TTF")
    if env_path:
        candidates.append(Path(env_path))
    candidates.append(HERE / "material_symbols_rounded.ttf")
    return candidates


SOURCE_TTF_CANDIDATES = _source_ttf_candidates()


def find_source_ttf() -> Path:
    for candidate in SOURCE_TTF_CANDIDATES:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        "No source TTF found. Place material_symbols_rounded.ttf in font_subset/ "
        "or set MATERIAL_SYMBOLS_ROUNDED_TTF. Tried: "
        + ", ".join(str(path) for path in SOURCE_TTF_CANDIDATES)
    )


def run(args: list[str]) -> int:
    print("Running:", " ".join(args[2:5] + ["..."]))
    result = subprocess.run(args, check=False)
    if result.returncode != 0:
        print(f"Command failed (exit {result.returncode}):", " ".join(args), file=sys.stderr)
    return result.returncode


def copy_outputs_to_app() -> None:
    APP_FONT_DIR.mkdir(parents=True, exist_ok=True)
    for variant in VARIANTS:
        shutil.copy2(variant["subset"], variant["resource"])
        print(f"Copied {variant['subset'].name} -> {variant['resource'].relative_to(REPO)}")


def cleanup_generated_files() -> None:
    generated_files = [
        GLYPHS_EXPANDED_FILE,
        LIGATURES_FILE,
        LIGATURES_REPORT_FILE,
        HERE / "probe.txt",
    ]
    for variant in VARIANTS:
        generated_files.append(variant["instanced"])
        generated_files.append(variant["subset"])

    for path in generated_files:
        if path.exists():
            path.unlink()
            print(f"Deleted generated file: {path.relative_to(REPO)}")

    pycache = HERE / "__pycache__"
    if pycache.exists():
        shutil.rmtree(pycache)
        print(f"Deleted generated directory: {pycache.relative_to(REPO)}")


def run_subset() -> int:
    # Imported here (not at module top) so `--harvest-only` runs without fonttools.
    from fontTools.ttLib import TTFont

    from ligature_resolve import expand_wanted_icon_names

    if not LIGATURES_FILE.is_file():
        print(f"Missing {LIGATURES_FILE}; run harvest first (drop --skip-harvest).", file=sys.stderr)
        return 1

    source = find_source_ttf()
    cached_source = HERE / "material_symbols_rounded.ttf"
    if source != cached_source and not cached_source.is_file():
        shutil.copy2(source, cached_source)
        print(f"Cached source TTF -> {cached_source}")

    src_size = cached_source.stat().st_size
    print(f"Source TTF: {cached_source} ({src_size / 1024 / 1024:.2f} MB)")

    # Harvest the glyph seed once - it is driven by ligature names and the font's
    # substitution tables, both of which are identical between FILL=0 and FILL=1.
    # Only the outline geometry differs.
    wanted_names = {
        line.strip()
        for line in LIGATURES_FILE.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    }

    for variant in VARIANTS:
        axes = {"FILL": variant["fill"], **SHARED_AXES}
        print(f"--- Building {variant['name']} variant (FILL={variant['fill']}) ---")

        instance_args = [
            sys.executable, "-m", "fontTools.varLib.instancer",
            str(cached_source),
            *(f"{axis}={value}" for axis, value in axes.items()),
            "--output", str(variant["instanced"]),
        ]
        if run(instance_args) != 0:
            return 1
        inst_size = variant["instanced"].stat().st_size
        print(f"Instanced TTF: {variant['instanced']} ({inst_size / 1024 / 1024:.2f} MB)")

        instanced_font = TTFont(str(variant["instanced"]))
        expanded_glyphs, missing_icons = expand_wanted_icon_names(instanced_font, wanted_names)
        instanced_font.close()
        # Overwrite glyphs_expanded.txt each variant; layout closure is disabled
        # so the expanded seed is the only source of truth for what gets kept.
        GLYPHS_EXPANDED_FILE.write_text("\n".join(expanded_glyphs) + "\n", encoding="utf-8")
        if missing_icons:
            print(
                f"WARNING: {len(missing_icons)} icon names could not be mapped to a ligature in the "
                f"source font (harvester noise or unsupported). First few: {missing_icons[:15]}",
                file=sys.stderr,
            )
        print(
            f"Expanded {len(wanted_names)} icon name lines -> {len(expanded_glyphs)} glyphs "
            f"(ligature outputs + components) -> {GLYPHS_EXPANDED_FILE.name}",
        )

        subset_args = [
            sys.executable, "-m", "fontTools.subset",
            str(variant["instanced"]),
            f"--glyphs-file={GLYPHS_EXPANDED_FILE}",
            f"--output-file={variant['subset']}",
            # Keep ligature substitution + required transforms so the icon
            # ligatures (e.g. "search" -> search glyph) actually fire at runtime.
            "--layout-features+=liga,rlig,clig,calt,ccmp,locl",
            # We already expanded the glyph seed to every ligature *component* for our
            # icons. Leaving layout closure ON would pull in thousands of extra outputs
            # (any ligature whose components are all in a-z once the alphabet is present).
            "--no-layout-closure",
            # Drop heavy bits we don't need: hinting (we render scaled), CFF subroutines.
            "--no-hinting",
            "--desubroutinize",
            "--recommended-glyphs",
            "--notdef-outline",
            # Android resolves ligature strings through glyph-name based GSUB
            # component sequences in this font. Without this, fonttools rewrites
            # lowercase component glyph names (`a`) to uppercase production names
            # (`A`), so runtime lowercase ligatures like "archive" stop matching.
            "--glyph-names",
            "--ignore-missing-glyphs",
            "--ignore-missing-unicodes",
        ]
        if run(subset_args) != 0:
            return 1

        out_size = variant["subset"].stat().st_size
        print(f"Subset TTF: {variant['subset']}")
        print(f"  Source:    {src_size / 1024 / 1024:.2f} MB")
        print(f"  Instanced: {inst_size / 1024 / 1024:.2f} MB")
        print(f"  Subset:    {out_size / 1024:.2f} KB ({out_size * 100 / src_size:.3f}% of source)")

    copy_outputs_to_app()
    cleanup_generated_files()
    print("Done. App font resources are updated; generated font_subset scratch files were removed.")
    return 0


# ===========================================================================
# Entry point
# ===========================================================================

def main() -> int:
    parser = argparse.ArgumentParser(
        description="Harvest icon ligature names from source and subset the Material Symbols font.",
    )
    group = parser.add_mutually_exclusive_group()
    group.add_argument(
        "--harvest-only",
        action="store_true",
        help="Stage 1 only: scan source and write ligatures.txt, then stop (no fonttools needed).",
    )
    group.add_argument(
        "--skip-harvest",
        action="store_true",
        help="Stage 2 only: skip scanning and subset using the existing ligatures.txt.",
    )
    args = parser.parse_args()

    if not args.skip_harvest:
        rc = run_harvest()
        if rc != 0:
            return rc
    if args.harvest_only:
        return 0
    return run_subset()


if __name__ == "__main__":
    sys.exit(main())
