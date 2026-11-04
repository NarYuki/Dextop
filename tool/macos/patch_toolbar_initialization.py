"""Request delegate default toolbar items when the toolbar is attached."""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    path = parser.parse_args().source / "src/external/cocotron/AppKit/NSToolbar.subproj/NSToolbar.m"
    text = path.read_text()
    old = """    if (isNew) { // allow the new delegate to reset the layout
        _loadDefaultItems = YES;
        [self loadDefaultItemsIfNeeded];
        [_window _toolbarSizeDidChangeFromOldHeight: [self visibleHeight]];
    }"""
    new = """    if (isNew) { // allow the new delegate to reset the layout
        _loadDefaultItems = YES;
        // An unattached delegate may still be constructing its item identifiers.
        if (_window != nil) {
            [self loadDefaultItemsIfNeeded];
            [_window _toolbarSizeDidChangeFromOldHeight: [self visibleHeight]];
        }
    }"""
    if new in text:
        return
    if text.count(old) != 1:
        raise RuntimeError("Pinned toolbar delegate initialization anchor changed")
    path.write_text(text.replace(old, new))


if __name__ == "__main__":
    main()
