"""Resize NSWindow when installing a content controller, preserving view sizing."""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    args = parser.parse_args()
    path = args.source / "src/external/cocotron/AppKit/NSWindow.m"
    text = path.read_text()
    old = """    if (controller != nil)
        [self setContentView: [controller view]];
}"""
    new = """    if (controller != nil) {
        NSView *view = [controller view];
        // Capture the requested size before setContentView: resizes the view.
        NSSize requested = [view frame].size;
        BOOL constrained = ![view translatesAutoresizingMaskIntoConstraints];
        [self setContentView: view];
        if (constrained || requested.width <= 0 || requested.height <= 0) {
            NSSize fitting = [_contentView fittingSize];
            if (constrained || requested.width <= 0) requested.width = fitting.width;
            if (constrained || requested.height <= 0) requested.height = fitting.height;
        }
        if (requested.width > 0 && requested.height > 0)
            [self setContentSize: requested];
    }
}"""
    if "// Capture the requested size before setContentView: resizes the view." in text:
        return
    if text.count(old) != 1:
        raise RuntimeError("Pinned content controller setter changed")
    path.write_text(text.replace(old, new))


if __name__ == "__main__":
    main()
