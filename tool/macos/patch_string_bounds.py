"""Measure attributed strings using AppKit's existing text layout engine.

Drawing-context font scaling remains unsupported by the underlying renderer.
"""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    args = parser.parse_args()
    path = args.source / "src/external/cocotron/AppKit/NSAttributedString.m"
    text = path.read_text()
    old = """- (NSRect) boundingRectWithSize: (NSSize) size
                        options: (NSStringDrawingOptions) options
{
    NSUnimplementedMethod();
    return NSMakeRect(0, 0, 0, 0);
}"""
    new = """- (NSRect) boundingRectWithSize: (NSSize) size
                        options: (NSStringDrawingOptions) options
{
    NSSize measured = [[NSStringDrawer sharedStringDrawer]
            sizeOfAttributedString: self inSize: size];
    return NSMakeRect(0, 0, measured.width, measured.height);
}

- (NSRect) boundingRectWithSize: (NSSize) size
                        options: (NSStringDrawingOptions) options
                        context: (NSStringDrawingContext *) context
{
    return [self boundingRectWithSize: size options: options];
}"""
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError("Pinned attributed string measurement anchor changed")
        path.write_text(text.replace(old, new))


if __name__ == "__main__":
    main()
