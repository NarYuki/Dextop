"""Expose the system accent color through the existing system color palette."""
import argparse
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    args = parser.parse_args()
    path = args.source / 'src/external/cocotron/AppKit/NSColor.subproj/NSColor.m'
    text = path.read_text()
    method = '+ (NSColor *) controlAccentColor {\n    return [self selectedControlColor];\n}\n\n'
    if method not in text:
        anchor = '+ (NSColor *) selectedControlColor {'
        if text.count(anchor) != 1: raise RuntimeError('Pinned NSColor anchor changed')
        path.write_text(text.replace(anchor, method + anchor))
    path = args.source / 'framework-include/AppKit/NSColor.h'
    text = path.read_text()
    declaration = '+ (NSColor *) controlAccentColor;'
    if declaration not in text:
        anchor = '+ (NSColor *) selectedControlColor;'
        if text.count(anchor) != 1: raise RuntimeError('Pinned NSColor header anchor changed')
        path.write_text(text.replace(anchor, declaration + '\n' + anchor))

if __name__ == '__main__': main()
