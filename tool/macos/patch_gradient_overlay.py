"""Expose the existing gradient implementation through its public Swift API."""
import argparse
from pathlib import Path

HERE = Path(__file__).resolve().parent


def patch(overlays):
    target = overlays / 'CoreGraphics/CGGradientOverlay.swift'
    content = (HERE / 'runtime/CGGradientOverlay.swift').read_bytes()
    if not target.exists() or target.read_bytes() != content:
        target.write_bytes(content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    source = parser.parse_args().source
    header = source / 'src/external/cocotron/CoreGraphics/include/CoreGraphics/CGGradient.h'
    text = header.read_text().replace('CF_ENUM(uint32_t, CGGradientDrawingOptions)', 'CF_OPTIONS(uint32_t, CGGradientDrawingOptions)')
    marker = '// Dextop gradient Swift imports'
    if marker not in text:
        start = text.index('CGGradientRef CGGradientCreateWithColorComponents(')
        end = text.index('COREGRAPHICS_EXPORT CFTypeID', start)
        text = text[:start] + '''// Dextop gradient Swift imports
CF_IMPLICIT_BRIDGING_ENABLED
CGGradientRef _Nullable CGGradientCreateWithColorComponents(CGColorSpaceRef _Nonnull colorSpace,
    const CGFloat * _Nonnull components, const CGFloat * _Nullable locations, size_t count)
    CF_SWIFT_NAME(CGGradient.init(colorSpace:colorComponents:locations:count:));
CGGradientRef _Nullable CGGradientCreateWithColors(CGColorSpaceRef _Nullable colorSpace,
    CFArrayRef _Nonnull colors, const CGFloat * _Nullable locations)
    CF_SWIFT_NAME(CGGradient.init(colorsSpace:colors:locations:));
void CGGradientRelease(CGGradientRef _Nonnull self);
CGGradientRef _Nonnull CGGradientRetain(CGGradientRef _Nonnull self);
CF_IMPLICIT_BRIDGING_DISABLED

''' + text[end:]
    if header.read_text() != text: header.write_text(text)
    patch(source / 'src/external/swift/overlays')


if __name__ == '__main__':
    main()
