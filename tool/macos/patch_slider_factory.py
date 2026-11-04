"""Create standard sliders through the modern value/range factory."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    base = parser.parse_args().source / "src/external/cocotron/AppKit"
    path = base / "NSSlider.m"
    text = path.read_text()
    if "- (NSSliderType)sliderType {" not in text:
        anchor = "@implementation NSSlider\n"
        forwarding = '''
- (NSSliderType)sliderType { return [[self cell] sliderType]; }
- (void)setSliderType:(NSSliderType)type {
    [[self cell] setSliderType:type];
    [self invalidateIntrinsicContentSize];
    [self setNeedsDisplay:YES];
}
'''
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned slider type forwarding anchor changed")
        text = text.replace(anchor, anchor + forwarding)
        path.write_text(text)
    if "+ (instancetype)sliderWithValue:" not in text:
        anchor = "@implementation NSSlider\n"
        method = '''
+ (instancetype)sliderWithValue:(double)value minValue:(double)minimum maxValue:(double)maximum target:(id)target action:(SEL)action {
    NSSlider *slider = [[[self alloc] initWithFrame:NSMakeRect(0, 0, 100, 20)] autorelease];
    [slider setMinValue:minimum];
    [slider setMaxValue:maximum];
    [slider setDoubleValue:value];
    [slider setTarget:target];
    [slider setAction:action];
    return slider;
}
'''
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned slider implementation changed")
        path.write_text(text.replace(anchor, anchor + method))
    path = base / "include/AppKit/NSSlider.h"
    text = path.read_text()
    if "- (NSSliderType)sliderType;" not in text:
        text = text.replace("@interface NSSlider : NSControl", "@interface NSSlider : NSControl\n- (NSSliderType)sliderType;\n- (void)setSliderType:(NSSliderType)type;")
        path.write_text(text)
    if "+ (instancetype)sliderWithValue:" not in text:
        anchor = "@interface NSSlider : NSControl"
        declaration = "\n+ (instancetype)sliderWithValue:(double)value minValue:(double)minimum maxValue:(double)maximum target:(id)target action:(SEL)action;\n"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned slider declaration changed")
        path.write_text(text.replace(anchor, anchor + declaration))


if __name__ == "__main__":
    main()
