"""Keep fixed-frame child geometry relative to its constrained parent."""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    path = args.source / "src/external/cocotron/AppKit/DarlingLayout.mm"
    text = path.read_text()
    marker = "// A fixed frame belongs to the parent coordinate system, not the root."
    if marker in text:
        return
    old = """                } else {
                    add(g.x == frame.origin.x); add(g.y == frame.origin.y);
                    add(g.width == frame.size.width); add(g.height == frame.size.height);
                }
            } else {"""
    new = """                } else if (p_found != geometry.end() && mask == NSViewNotSizable) {
                    // A fixed frame belongs to the parent coordinate system, not the root.
                    Geometry &p = p_found->second;
                    NSView *parentView = [view superview];
                    NSRect local = [view frame], bounds = [parentView bounds];
                    add(g.width == local.size.width); add(g.height == local.size.height);
                    add(g.x == p.x + local.origin.x - bounds.origin.x);
                    if ([parentView isFlipped] != [root isFlipped])
                        add(g.y == p.y + p.height - (local.origin.y - bounds.origin.y) - g.height);
                    else
                        add(g.y == p.y + local.origin.y - bounds.origin.y);
                } else {
                    add(g.x == frame.origin.x); add(g.y == frame.origin.y);
                    add(g.width == frame.size.width); add(g.height == frame.size.height);
                }
            } else {"""
    if text.count(old) != 1:
        raise RuntimeError("Pinned autoresizing geometry implementation changed")
    path.write_text(text.replace(old, new))


if __name__ == "__main__":
    main()
