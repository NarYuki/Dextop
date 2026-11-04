"""Resolve first/last baseline constraints through NSView's overridable metrics."""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    base = parser.parse_args().source / "src/external/cocotron/AppKit"
    path = base / "DarlingLayout.mm"
    text = path.read_text()
    anchor = "        case NSLayoutAttributeWidth: return kiwi::Term(g.width);"
    if "case NSLayoutAttributeFirstBaseline:" not in text:
        assert text.count(anchor) == 1
        text = text.replace(anchor, """        case NSLayoutAttributeFirstBaseline:
            return (flipped ? kiwi::Expression(kiwi::Term(g.y)) : top - (g.y + g.height))
                + [view alignmentRectInsets].top + [view firstBaselineOffsetFromTop];
        case NSLayoutAttributeLastBaseline:
            return (flipped ? g.y + g.height : top - g.y)
                - [view alignmentRectInsets].bottom - [view lastBaselineOffsetFromBottom];
""" + anchor)
        path.write_text(text)
    path = base / "NSView.m"
    text = path.read_text()
    anchor = "- (NSSize) intrinsicContentSize {"
    if "- (CGFloat) firstBaselineOffsetFromTop {" not in text:
        assert text.count(anchor) == 1
        text = text.replace(anchor, """// A plain view has no text baseline; subclasses provide their own metrics.
- (CGFloat) firstBaselineOffsetFromTop { return 0; }
- (CGFloat) baselineOffsetFromBottom { return 0; }
- (CGFloat) lastBaselineOffsetFromBottom { return [self baselineOffsetFromBottom]; }

""" + anchor)
        path.write_text(text)
    path = base / "include/AppKit/NSView.h"
    text = path.read_text()
    anchor = "@property(readonly) NSEdgeInsets alignmentRectInsets;"
    if "@property(readonly) CGFloat firstBaselineOffsetFromTop;" not in text:
        assert text.count(anchor) == 1
        text = text.replace(anchor, anchor + """
@property(readonly) CGFloat firstBaselineOffsetFromTop;
@property(readonly) CGFloat lastBaselineOffsetFromBottom;
@property(readonly) CGFloat baselineOffsetFromBottom;""")
        path.write_text(text)


if __name__ == "__main__":
    main()
