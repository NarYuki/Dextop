#!/usr/bin/env python3
"""Generate MaterialSymbols.kt from Material Symbols Rounded SVG files.

Download the 24px Rounded SVGs (outline and `_fill1`) from
https://github.com/google/material-design-icons/tree/master/symbols/web
into one directory, named `<name>.svg` and `<name>_fill1.svg`, then run:

    python tool/generate_material_symbols.py <svg-dir>

Material Symbols are licensed under the Apache License 2.0.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = (
    ROOT / "android" / "app" / "src" / "main" / "kotlin" / "moe" / "n4tsu" / "dextop"
    / "plasma" / "MaterialSymbols.kt"
)
PATH_DATA = re.compile(r'<path[^>]*\sd="([^"]+)"')


def path_data(svg: Path) -> str:
    text = svg.read_text(encoding="utf-8")
    if 'viewBox="0 -960 960 960"' not in text:
        raise SystemExit(f"{svg.name}: unexpected viewBox")
    paths = PATH_DATA.findall(text)
    if not paths:
        raise SystemExit(f"{svg.name}: no path data")
    return " ".join(paths)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    source = Path(sys.argv[1])
    outlines = {}
    filled = {}
    for svg in sorted(source.glob("*.svg")):
        name = svg.stem
        if name.endswith("_fill1"):
            filled[name[: -len("_fill1")]] = path_data(svg)
        else:
            outlines[name] = path_data(svg)
    lines = [
        "// GENERATED FILE. Source: Material Symbols Rounded (Apache License 2.0),",
        "// https://github.com/google/material-design-icons. Regenerate with",
        "// tool/generate_material_symbols.py.",
        "package moe.n4tsu.dextop.plasma",
        "",
        "/** SVG path data in the Material Symbols 960-unit viewport (y from -960 to 0). */",
        "internal object MaterialSymbols {",
        "    fun path(name: String, filled: Boolean = false): String? =",
        "        (if (filled) FILLED[name] else null) ?: OUTLINE[name]",
        "",
        "    private val OUTLINE = mapOf(",
    ]
    lines += [f'        "{name}" to "{data}",' for name, data in sorted(outlines.items())]
    lines += ["    )", "", "    private val FILLED = mapOf("]
    lines += [f'        "{name}" to "{data}",' for name, data in sorted(filled.items())]
    lines += ["    )", "}", ""]
    OUTPUT.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    print(f"{OUTPUT.relative_to(ROOT)}: {len(outlines)} outline, {len(filled)} filled")


if __name__ == "__main__":
    main()
