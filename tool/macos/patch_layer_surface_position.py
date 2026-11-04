"""Refresh native layer positions when the enclosing window changes height."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    path = parser.parse_args().source / "src/external/cocotron/AppKit/NSView.m"
    text = path.read_text()
    old = """    if (NSEqualRects(_frame, frame))
        return;"""
    new = """    if (NSEqualRects(_frame, frame)) {
        // Native child coordinates also depend on the window's current height.
        NSRect layerFrame = _frame;
        if (_superview != nil)
            layerFrame = [_superview convertRect:layerFrame toView:nil];
        [_layerContext setFrame:layerFrame];
        return;
    }"""
    if new in text:
        pass
    else:
        if text.count(old) != 1:
            raise RuntimeError("Pinned view frame equality anchor changed")
        text = text.replace(old, new)
    anchor = "        [_layerContext render];"
    replacement = """        // Layout can retain an identical content frame while its native parent grows.
        if (_layerContext) {
            NSRect surfaceFrame = _frame;
            if (_superview) surfaceFrame = [_superview convertRect:surfaceFrame toView:nil];
            [_layerContext setFrame:surfaceFrame];
        }
        [_layerContext render];"""
    if replacement not in text:
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned layer render anchor changed")
        text = text.replace(anchor, replacement)
    path.write_text(text)


if __name__ == "__main__":
    main()
