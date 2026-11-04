"""Create standard single-line editable text fields through the modern factory."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    base = parser.parse_args().source / "src/external/cocotron/AppKit"
    path = base / "NSTextField.m"
    text = path.read_text()
    method = '''
+ (instancetype)textFieldWithString:(NSString *)stringValue {
    NSTextField *field = [[[self alloc] initWithFrame:NSZeroRect] autorelease];
    [field setStringValue:stringValue];
    [field setEditable:YES];
    [field setSelectable:YES];
    [field setBezeled:YES];
    [field setDrawsBackground:YES];
    [[field cell] setWraps:NO];
    [[field cell] setScrollable:YES];
    [field sizeToFit];
    return field;
}
'''
    if "+ (instancetype)textFieldWithString:" not in text:
        anchor = "@implementation NSTextField\n"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned text field implementation changed")
        path.write_text(text.replace(anchor, anchor + method))
    path = base / "include/AppKit/NSTextField.h"
    text = path.read_text()
    if "+ (instancetype)textFieldWithString:" not in text:
        anchor = "+ (instancetype)labelWithString:"
        position = text.index(anchor)
        text = text[:position] + "+ (instancetype)textFieldWithString:(NSString *)stringValue;\n" + text[position:]
        path.write_text(text)


if __name__ == "__main__":
    main()
