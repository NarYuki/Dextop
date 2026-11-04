"""Measure constrained NSView fitting sizes without changing installed frames."""

import argparse
from pathlib import Path


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError("Pinned layout implementation anchor changed")
    return text.replace(old, new)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    args = parser.parse_args()
    base = args.source / "src/external/cocotron/AppKit"
    path = base / "DarlingLayout.mm"
    text = path.read_text()
    if "NSSize DarlingFittingSize" in text:
        return
    text = replace_once(text, "    void run() {", "    NSSize run(bool fitting = false) {")
    text = replace_once(text, "        if ([constraints count] == 0) return;", """        if ([constraints count] == 0 && !fitting) return [root bounds].size;""")
    text = replace_once(text,
        "                add(g.x == frame.origin.x); add(g.y == frame.origin.y);\n                add(g.width == frame.size.width); add(g.height == frame.size.height);\n            } else if",
        """                add(g.x == frame.origin.x); add(g.y == frame.origin.y);
                if (fitting) {
                    add(g.width >= 0.0); add(g.height >= 0.0);
                    intrinsic(view, g);
                    // Fitting size minimizes unconstrained axes below all CHCR priorities.
                    add((g.width == 0.0) | 0.001);
                    add((g.height == 0.0) | 0.001);
                } else {
                    add(g.width == frame.size.width); add(g.height == frame.size.height);
                }
            } else if""")
    text = replace_once(text, "        // Convert every result before changing any ancestor frame.", """        if (fitting) {
            Geometry &g = geometry.find(root)->second;
            return NSMakeSize(g.width.value(), g.height.value());
        }
        // Convert every result before changing any ancestor frame.""")
    text = replace_once(text,
        "            [[views objectAtIndex:i] setFrame:[[frames objectAtIndex:i] rectValue]];",
        "            [[views objectAtIndex:i] setFrame:[[frames objectAtIndex:i] rectValue]];\n        return [root bounds].size;")
    text += """
NSSize DarlingFittingSize(NSView *root) {
    try { return Layout(root).run(true); }
    catch (const std::exception &error) {
        NSLog(@"Unable to measure constrained fitting size: %s", error.what());
        return [root bounds].size;
    }
}
"""
    header = base / "DarlingLayout.h"
    h = header.read_text()
    h = replace_once(h, "void DarlingLayoutSubtree(NSView *root);",
                     "void DarlingLayoutSubtree(NSView *root);\nNSSize DarlingFittingSize(NSView *root);")
    view = base / "NSView.m"
    v = view.read_text()
    start = v.index("- (NSSize) fittingSize {")
    end = v.index("\n- (void) invalidateIntrinsicContentSize", start)
    v = v[:start] + """- (NSSize) fittingSize {
    [self updateConstraintsForSubtreeIfNeeded];
    return DarlingFittingSize(self);
}
""" + v[end:]
    path.write_text(text)
    header.write_text(h)
    view.write_text(v)


if __name__ == "__main__":
    main()
