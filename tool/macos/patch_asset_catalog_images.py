"""Allow NSImage named lookup to use an external decoded catalog index."""

import argparse
from pathlib import Path


HELPER = r'''
+ (NSImage *) _dextopCatalogImageNamed: (NSString *) name bundle: (NSBundle *) bundle {
    const char *indexPath = getenv("DEXTOP_ASSET_CATALOG_INDEX");
    if (!indexPath || !*indexPath) return nil;
    static NSDictionary *index = nil;
    if (!index) index = [[NSDictionary dictionaryWithContentsOfFile:
            [NSString stringWithUTF8String:indexPath]] retain];
    if ([[index objectForKey:@"Version"] intValue] != 1) return nil;
    NSDictionary *images = [[index objectForKey:@"Bundles"] objectForKey:[bundle bundlePath]];
    NSArray *variants = [images objectForKey:name];
    NSImage *image = nil;
    for (NSDictionary *variant in variants) {
        NSSize size = NSMakeSize([[variant objectForKey:@"Width"] doubleValue],
                                 [[variant objectForKey:@"Height"] doubleValue]);
        if (size.width <= 0 || size.height <= 0) continue;
        NSBitmapImageRep *rep = [NSBitmapImageRep imageRepWithContentsOfFile:[variant objectForKey:@"Path"]];
        if (!rep) continue;
        if (!image) image = [[[NSImage alloc] initWithSize:size] autorelease];
        if (!NSEqualSizes(size, [image size])) continue;
        [rep setSize:size];
        [image addRepresentation:rep];
    }
    return image;
}

'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    path = args.source / "src/external/cocotron/AppKit/NSImage.m"
    text = path.read_text()
    if HELPER in text:
        return
    anchor = "+ imageNamed: (NSString *) name {"
    old = """            NSString *path = [bundle pathForImageResource: name];

            if (path != nil) {"""
    new = """            image = [self _dextopCatalogImageNamed:name bundle:bundle];
            if (image) {
                [image setName:name];
                break;
            }
            NSString *path = [bundle pathForImageResource: name];

            if (path != nil) {"""
    if text.count(anchor) != 1 or text.count(old) != 1:
        raise RuntimeError("Pinned NSImage lookup changed")
    path.write_text(text.replace(anchor, HELPER + anchor).replace(old, new))


if __name__ == "__main__":
    main()
