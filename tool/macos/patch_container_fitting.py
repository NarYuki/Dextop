"""Allow cell-less controls and filling content views to contribute fitting size."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    base = parser.parse_args().source / "src/external/cocotron/AppKit"
    path = base / "NSControl.m"
    text = path.read_text()
    old = """- (NSSize) intrinsicContentSize {
    return [[self cell] cellSize];
}"""
    new = """- (NSSize) intrinsicContentSize {
    if ([self cell] == nil) return [super intrinsicContentSize];
    return [[self cell] cellSize];
}"""
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError("Pinned control intrinsic size anchor changed")
        path.write_text(text.replace(old, new))
    path = base / "DarlingLayout.mm"
    text = path.read_text()
    text = text.replace("if (fitting) intrinsic(view, g);", "intrinsic(view, g);")
    duplicate = """                    // Filling children can determine a previously unsized container.
                    intrinsic(view, g);
                    // Filling children can determine a previously unsized container.
                    intrinsic(view, g);"""
    single = """                    // Filling children can determine a previously unsized container.
                    intrinsic(view, g);"""
    text = text.replace(duplicate, single)
    old = """                    add(g.height == p.height - (bottomMargin + topMargin));"""
    new = old + """
                    // Filling children can determine a previously unsized container.
                    intrinsic(view, g);"""
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError("Pinned filling child layout anchor changed")
        text = text.replace(old, new)
    path.write_text(text)


if __name__ == "__main__":
    main()
