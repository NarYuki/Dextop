"""Measure sliders using their actual graphics-style knob and tick geometry."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    base = parser.parse_args().source / "src/external/cocotron/AppKit"
    path = base / "NSSlider.m"
    text = path.read_text()
    if "// The slider's travel axis has no intrinsic length." in text:
        text = text.replace("([self numberOfTickMarks] > 0 ? 5 : 0)",
                            "([self numberOfTickMarks] > 0 ? 8 : 0)")
        path.write_text(text)
    if "// The slider's travel axis has no intrinsic length." not in text:
        text = '#import <AppKit/NSGraphicsStyle.h>\n#import <AppKit/NSView.h>\n' + text
        anchor = "@implementation NSSlider\n"
        method = '''
- (NSSize)intrinsicContentSize {
    NSSize knob = [[self graphicsStyle] sliderKnobSizeForControlSize:[[self cell] controlSize]];
    CGFloat thickness = knob.height + ([self numberOfTickMarks] > 0 ? 8 : 0);
    if ([self sliderType] == NSCircularSlider)
        return NSMakeSize(knob.height, knob.height);
    // The slider's travel axis has no intrinsic length.
    BOOL vertical = [self frame].size.height > [self frame].size.width;
    return vertical ? NSMakeSize(thickness, NSViewNoIntrinsicMetric)
                    : NSMakeSize(NSViewNoIntrinsicMetric, thickness);
}
'''
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned slider intrinsic anchor changed")
        path.write_text(text.replace(anchor, anchor + method))


if __name__ == "__main__":
    main()
