"""Upload ordinary AppKit view drawing to its Core Animation backing layer."""

import argparse
from pathlib import Path

IMPLEMENTATION = r'''#import <AppKit/NSViewBackingLayer.h>
#import <QuartzCore/CATransaction.h>
#import <CoreGraphics/CoreGraphics.h>
#include <math.h>

@implementation NSViewBackingLayer

- (void)dealloc {
    if (_drawingContext) CGContextRelease(_drawingContext);
    [super dealloc];
}

- (CGContextRef)drawingContextForBounds:(CGRect)bounds scale:(CGFloat)scale {
    if (!isfinite(scale) || scale <= 0) scale = 1;
    double width = ceil(bounds.size.width * scale);
    double height = ceil(bounds.size.height * scale);
    if (!isfinite(width) || !isfinite(height) || width <= 0 || height <= 0 ||
        width > 16384 || height > 16384) return NULL;
    if (_drawingContext && (CGBitmapContextGetWidth(_drawingContext) != (size_t)width ||
        CGBitmapContextGetHeight(_drawingContext) != (size_t)height)) {
        CGContextRelease(_drawingContext);
        _drawingContext = NULL;
    }
    if (!_drawingContext) {
        CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
        _drawingContext = CGBitmapContextCreate(NULL, width, height, 8, 0, space,
            kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Host);
        CGColorSpaceRelease(space);
    }
    [self setContentsScale:scale];
    return _drawingContext;
}

- (void)publishDrawing {
    if (!_drawingContext) return;
    CGImageRef image = CGBitmapContextCreateImage(_drawingContext);
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    [self setContents:(id)image];
    [CATransaction commit];
    if (image) CGImageRelease(image);
}

// Displaying the layer must not replace the bitmap published by its NSView.
- (void)display { }

@end
'''


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError("Pinned backing layer anchor changed")
    return text.replace(old, new)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    base = parser.parse_args().source / "src/external/cocotron/AppKit"
    (base / "NSViewBackingLayer.m").write_text(IMPLEMENTATION)
    (base / "NSViewBackingLayer.h").write_text('''#import <QuartzCore/CALayer.h>
#import <CoreGraphics/CoreGraphics.h>

@interface NSViewBackingLayer : CALayer {
    CGContextRef _drawingContext;
}
- (CGContextRef)drawingContextForBounds:(CGRect)bounds scale:(CGFloat)scale;
- (void)publishDrawing;
@end
''')
    path = base / "NSView.m"
    text = path.read_text()
    if "NSViewBackingLayer *drawingLayer" in text:
        text = text.replace("if (_layerContext && [_window isOpaque]) {",
                            "if (_layerContext && self == [_window contentView]) {")
        if "[superlayer addSublayer: _layer];\n            configureLayerGeometry(self);" not in text:
            text = replace_once(text, "            [superlayer addSublayer: _layer];\n",
                                "            [superlayer addSublayer: _layer];\n            configureLayerGeometry(self);\n")
        path.write_text(text)
        return
    text = replace_once(text, """            [self lockFocus];

            NSGraphicsContext *context = [NSGraphicsContext currentContext];""", """            NSViewBackingLayer *drawingLayer = nil;
            if ([_layer isKindOfClass:[NSViewBackingLayer class]] && ![self wantsUpdateLayer])
                drawingLayer = (NSViewBackingLayer *)_layer;
            CGFloat drawingScale = [_window backingScaleFactor];
            if (!isfinite(drawingScale) || drawingScale <= 0) drawingScale = 1;
            CGContextRef bitmap = [drawingLayer drawingContextForBounds:[self bounds] scale:drawingScale];
            if (drawingLayer && !bitmap) return;
            if (bitmap)
                [self _lockFocusInContext:[NSGraphicsContext graphicsContextWithGraphicsPort:bitmap flipped:[self isFlipped]]];
            else
                [self lockFocus];

            NSGraphicsContext *context = [NSGraphicsContext currentContext];""")
    text = replace_once(text, """            CGContextClipToRect(graphicsPort, rect);

            const NSRect *rects;""", """            if (bitmap) {
                CGAffineTransform transform = [self transformToLayer];
                transform = CGAffineTransformConcat(transform, CGAffineTransformMakeScale(drawingScale, drawingScale));
                CGContextSetCTM(graphicsPort, transform);
                CGContextClearRect(graphicsPort, rect);
                // The native layer surface covers the window's normal background.
                if (_layerContext && self == [_window contentView]) {
                    [[_window backgroundColor] setFill];
                    NSRectFill(rect);
                }
            }
            CGContextClipToRect(graphicsPort, rect);

            const NSRect *rects;""")
    text = replace_once(text, """            [self unlockFocus];

            NSEnumerator *viewEnumerator""", """            [self unlockFocus];
            [drawingLayer publishDrawing];

            NSEnumerator *viewEnumerator""")
    text = replace_once(text, """    else
        [layer setPosition: self->_frame.origin];

    [layer setBounds: self->_bounds];""", """    else {
        NSPoint position = self->_frame.origin;
        NSView *parent = [self superview];
        if ([[parent layer] isKindOfClass:[NSViewBackingLayer class]] && [parent isFlipped])
            position.y = NSMinY([parent bounds]) + NSMaxY([parent bounds]) - NSMaxY(self->_frame);
        [layer setPosition: position];
    }

    [layer setBounds: self->_bounds];""")
    text = replace_once(text, "            [superlayer addSublayer: _layer];\n",
                        "            [superlayer addSublayer: _layer];\n            configureLayerGeometry(self);\n")
    path.write_text(text)


if __name__ == "__main__":
    main()
