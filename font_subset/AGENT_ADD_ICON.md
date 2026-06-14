# Adding or changing Material Symbols icons (for coding agents)

FilePipe renders icons with `FilePipeMaterialRoundedSymbol(name = "ligature_name", …)` using **two** subset TTFs in `app/src/main/res/font/`:

- `material_symbols_rounded.ttf` - filled variant (FILL=1, default)
- `material_symbols_rounded_outlined.ttf` - outlined variant (FILL=0)

Pass `filled = false` on `FilePipeMaterialRoundedSymbol` to render from the outlined TTF. Inside the app, prefer `filled = <state>` over the `if (state) "icon" else "icon_border"` pattern - the instanced subsets bake FILL into geometry, so a `_border` alt name alone will look identical to the filled glyph. Switching the font family is the only way to get a visually distinct outline.
@
If a ligature is missing from either file, the UI shows the raw string or a blank.

## When you change Kotlin only (name already in the subset)

- Add or change the string in `FilePipeMaterialRoundedSymbol`, `symbolName = "…"`, `SwipeAction.materialSymbolName()`, `RuleIcon.materialSymbolName()`, or `name = if (…) "a" else "b"` rows.
- If the ligature was **already** harvested in a prior run, **nothing else** is required.

## When you introduce a **new** ligature name

1. **Confirm** the name is a valid [Material Symbols](https://fonts.google.com/icons) Rounded ligature (underscores, lowercase).

2. **Build the icon fonts** (repo root = `FilePipe/`):

   ```text
   python font_subset/build_icon_font.py
   ```

   This rescans `app/src/main/java`, subsets the variable font down to the icons it finds, copies both generated subset fonts into `app/src/main/res/font/`, and deletes the scratch/report files it created (`*_instanced.ttf`, `*_subset.ttf`, `glyphs_expanded.txt`, `ligatures.txt`, `ligatures_report.json`, `probe.txt`, `__pycache__/`).

   Requires `fonttools` (`pip install fonttools`). Place the **full** variable `material_symbols_rounded.ttf` in `font_subset/` (from [Google Fonts](https://fonts.google.com/icons) / Material Symbols download), or set `MATERIAL_SYMBOLS_ROUNDED_TTF` to its path (see `build_icon_font.py` `SOURCE_TTF_CANDIDATES`).

   Stage flags if you need them: `--harvest-only` (scan + write `ligatures.txt` only; no `fonttools` needed) or `--skip-harvest` (subset an existing `ligatures.txt`). If your new name is built only from variables (no string literal the regex can see), either add a literal in Kotlin (a comment line is NOT enough) or add a small explicit pattern in `build_icon_font.py` (see existing patterns: ternary `name = if`, enum `FOO -> "…"`, etc.).

3. **Build** the app (e.g. `:app:compileGithubDebugKotlin` or your flavor) and verify the icon on device/emulator.

## Do not

- Assume a full Material font is bundled; only **subset** glyphs exist.
- Rely on `favorite_border`-style names for a different look at FILL=1; instancing collapses many outline names to filled shapes.
