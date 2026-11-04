"""Provide modern segmented-control factories using the existing segmented cell."""
import argparse
from pathlib import Path

METHODS = '''
+ (instancetype)segmentedControlWithImages:(NSArray *)images trackingMode:(NSSegmentSwitchTracking)mode target:(id)target action:(SEL)action {
    NSSegmentedControl *control = [[[self alloc] initWithFrame:NSZeroRect] autorelease];
    [control setSegmentCount:[images count]];
    [control setTrackingMode:mode];
    for (NSUInteger i = 0; i < [images count]; ++i)
        [control setImage:[images objectAtIndex:i] forSegment:i];
    [control setTarget:target]; [control setAction:action];
    [control sizeToFit];
    return control;
}

+ (instancetype)segmentedControlWithLabels:(NSArray *)labels trackingMode:(NSSegmentSwitchTracking)mode target:(id)target action:(SEL)action {
    NSSegmentedControl *control = [[[self alloc] initWithFrame:NSZeroRect] autorelease];
    [control setSegmentCount:[labels count]];
    [control setTrackingMode:mode];
    for (NSUInteger i = 0; i < [labels count]; ++i)
        [control setLabel:[labels objectAtIndex:i] forSegment:i];
    [control setTarget:target]; [control setAction:action];
    [control sizeToFit];
    return control;
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    base = parser.parse_args().source / "src/external/cocotron/AppKit"
    path = base / "NSSegmentedControl/NSSegmentedControl.m"
    text = path.read_text()
    if "+ (instancetype)segmentedControlWithImages:" not in text:
        anchor = "@implementation NSSegmentedControl\n"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned segmented control implementation changed")
        path.write_text(text.replace(anchor, anchor + "\n" + METHODS))
    path = base / "include/AppKit/NSSegmentedControl.h"
    text = path.read_text()
    if "+ (instancetype)segmentedControlWithImages:" not in text:
        anchor = "@interface NSSegmentedControl : NSControl"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned segmented control header changed")
        declarations = '''
+ (instancetype)segmentedControlWithImages:(NSArray *)images trackingMode:(NSSegmentSwitchTracking)mode target:(id)target action:(SEL)action;
+ (instancetype)segmentedControlWithLabels:(NSArray *)labels trackingMode:(NSSegmentSwitchTracking)mode target:(id)target action:(SEL)action;
'''
        path.write_text(text.replace(anchor, anchor + "\n" + declarations))


if __name__ == "__main__":
    main()
