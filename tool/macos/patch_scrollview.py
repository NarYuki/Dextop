"""Expose scroll-indicator refresh for the current legacy AppKit scrollers.

Legacy indicators remain visible; animated transient overlay scrollers require
additional rendering support and are not implemented by this patch.
"""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    path = args.source / "src/external/cocotron/AppKit/NSScrollView.m"
    text = path.read_text()
    method = """- (void) flashScrollers {
    // The current backend uses persistent legacy indicators. Update their
    // geometry and redraw them without changing the document scroll position.
    [self reflectScrolledClipView: _clipView];
    [_verticalScroller setNeedsDisplay: YES];
    [_horizontalScroller setNeedsDisplay: YES];
}

"""
    if method not in text:
        anchor = "- (void) reflectScrolledClipView: (NSClipView *) clipView {"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned scroll view implementation anchor changed")
        path.write_text(text.replace(anchor, method + anchor))
    header = args.source / "framework-include/AppKit/NSScrollView.h"
    text = header.read_text()
    if "- (void)flashScrollers;" not in text:
        anchor = "@end"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned scroll view header boundary changed")
        header.write_text(text.replace(anchor, "- (void)flashScrollers;\n\n" + anchor))


if __name__ == "__main__":
    main()
