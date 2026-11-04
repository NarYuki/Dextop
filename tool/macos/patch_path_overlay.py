"""Install Swift path operations into the existing CoreGraphics overlay."""
import argparse
from pathlib import Path

HERE = Path(__file__).resolve().parent


def patch(overlays):
    target = overlays / 'CoreGraphics/CGPathOverlay.swift'
    content = (HERE / 'runtime/CGPathOverlay.swift').read_bytes()
    if not target.exists() or target.read_bytes() != content:
        target.write_bytes(content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    source = parser.parse_args().source
    header = source / 'src/external/cocotron/CoreGraphics/include/CoreGraphics/CGPath.h'
    text = header.read_text()
    old = '''typedef enum {
    kCGPathElementMoveToPoint,
    kCGPathElementAddLineToPoint,
    kCGPathElementAddQuadCurveToPoint,
    kCGPathElementAddCurveToPoint,
    kCGPathElementCloseSubpath,
} CGPathElementType;'''
    new = '''typedef CF_ENUM(int32_t, CGPathElementType) {
    kCGPathElementMoveToPoint CF_SWIFT_NAME(CGPathElementType.moveToPoint),
    kCGPathElementAddLineToPoint CF_SWIFT_NAME(CGPathElementType.addLineToPoint),
    kCGPathElementAddQuadCurveToPoint CF_SWIFT_NAME(CGPathElementType.addQuadCurveToPoint),
    kCGPathElementAddCurveToPoint CF_SWIFT_NAME(CGPathElementType.addCurveToPoint),
    kCGPathElementCloseSubpath CF_SWIFT_NAME(CGPathElementType.closeSubpath),
};'''
    if new not in text:
        if text.count(old) != 1: raise RuntimeError('Pinned path element enum changed')
        header.write_text(text.replace(old, new))
    patch(source / 'src/external/swift/overlays')


if __name__ == '__main__':
    main()
