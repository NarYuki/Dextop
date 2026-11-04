"""Honor pending descendant layout when an unchanged ancestor is laid out."""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    args = parser.parse_args()
    path = args.source / "src/external/cocotron/AppKit/NSView.m"
    text = path.read_text()
    marker = "static BOOL DarlingSubtreeNeedsLayout(NSView *view) {"
    if marker in text:
        return
    old = """- (void) layoutSubtreeIfNeeded {
    if (!_needsLayout && !_needsUpdateConstraints) {"""
    new = """static BOOL DarlingSubtreeNeedsLayout(NSView *view) {
    if ([view needsLayout] || [view needsUpdateConstraints]) return YES;
    for (NSView *child in [view subviews])
        if (DarlingSubtreeNeedsLayout(child)) return YES;
    return NO;
}

- (void) layoutSubtreeIfNeeded {
    if (!DarlingSubtreeNeedsLayout(self)) {"""
    replacements = [(old, new),
        ("""- (void) setNeedsLayout: (BOOL) flag {
    _needsLayout = flag;
}""", """- (void) setNeedsLayout: (BOOL) flag {
    _needsLayout = flag;
    if (flag) [[self window] setViewsNeedDisplay: YES];
}"""),
        ("""- (void) invalidateIntrinsicContentSize {
    _needsLayout = YES;
}""", """- (void) invalidateIntrinsicContentSize {
    [self setNeedsLayout: YES];
}""")]
    for before, after in replacements:
        if text.count(before) != 1:
            raise RuntimeError("Pinned NSView layout implementation changed")
        text = text.replace(before, after)
    path.write_text(text)


if __name__ == "__main__":
    main()
