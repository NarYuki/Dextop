"""Route NSWindow's initial content through subclass setContentView: overrides."""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    args = parser.parse_args()
    path = args.source / "src/external/cocotron/AppKit/NSWindow.m"
    text = path.read_text()
    marker = "    // Dispatch through the public setter so subclasses can wrap the initial view."
    if marker in text:
        return
    old = """    _contentView = [[NSView alloc] initWithFrame: contentViewFrame];
    [_contentView setAutoresizesSubviews: YES];
    [_contentView
            setAutoresizingMask: NSViewWidthSizable | NSViewHeightSizable];"""
    new = marker + """
    NSView *initialContentView = [[NSView alloc] initWithFrame: contentViewFrame];
    [initialContentView setAutoresizesSubviews: YES];
    [initialContentView
            setAutoresizingMask: NSViewWidthSizable | NSViewHeightSizable];
    [self setContentView: initialContentView];
    [initialContentView release];"""
    attach = """    [_backgroundView addSubview: _contentView];
    [_backgroundView setNeedsDisplay: YES];"""
    if text.count(old) != 1 or text.count(attach) != 1:
        raise RuntimeError("Pinned NSWindow initialization anchors changed")
    text = text.replace(old, new).replace(attach, "    [_backgroundView setNeedsDisplay: YES];")
    path.write_text(text)


if __name__ == "__main__":
    main()
