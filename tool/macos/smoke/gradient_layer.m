#import <Foundation/Foundation.h>
#import <QuartzCore/QuartzCore.h>
#import <CoreGraphics/CoreGraphics.h>
#include <stdio.h>
#include <stdlib.h>

static void gradient_check(BOOL value, const char *message) {
    if (!value) { fprintf(stderr, "gradient layer FAIL: %s\n", message); exit(1); }
}
static CGContextRef bitmap(size_t width, size_t height) {
    CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
    CGContextRef result = CGBitmapContextCreate(NULL, width, height, 8, width * 4, space,
        kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    CGColorSpaceRelease(space); return result;
}
int main(void) {
    @autoreleasepool {
        CAGradientLayer *layer = [CAGradientLayer layer];
        gradient_check(layer.colors == nil && layer.locations == nil && [layer.type isEqual:kCAGradientLayerAxial], "defaults");
        gradient_check(layer.startPoint.x == 0.5 && layer.startPoint.y == 0 && layer.endPoint.y == 1, "default unit points");
        CGColorRef red = CGColorCreateGenericRGB(1,0,0,1), blue = CGColorCreateGenericRGB(0,0,1,1);
        NSMutableArray *colors = [NSMutableArray arrayWithObjects:(id)red, (id)blue, nil];
        layer.colors = colors; [colors removeAllObjects];
        layer.bounds = CGRectMake(0,0,101,101);
        layer.startPoint = CGPointMake(0,0.5); layer.endPoint = CGPointMake(1,0.5);
        CGContextRef context = bitmap(101,101);
        [layer drawInContext:context];
        unsigned char *bytes = CGBitmapContextGetData(context);
        size_t left = (50*101)*4, middle = (50*101+50)*4, right = (50*101+100)*4;
        gradient_check(bytes[left] > 250 && bytes[left+2] < 5 && bytes[right+2] > 250, "linear pixels");
        gradient_check(abs(bytes[middle]-128)<=2 && abs(bytes[middle+2]-128)<=2, "linear midpoint");
        [layer displayIfNeeded]; gradient_check(layer.contents != nil && ![layer needsDisplay], "automatic display creates contents");
        layer.locations = @[@0.25,@0.75]; gradient_check([layer needsDisplay], "stop changes invalidate contents");
        CAGradientLayer *copy = [[[CAGradientLayer alloc] initWithLayer:layer] autorelease];
        layer.colors = nil; layer.locations = nil;
        gradient_check(copy.colors.count == 2 && copy.locations.count == 2 && copy.startPoint.x == 0, "presentation copy preserves metadata");
        layer.colors = copy.colors;
        layer.type = kCAGradientLayerRadial; layer.startPoint = CGPointMake(0.5,0.5); layer.endPoint = CGPointMake(1,1);
        CGContextClearRect(context,layer.bounds); [layer drawInContext:context];
        gradient_check(bytes[middle] > 250 && bytes[middle+2] < 5 && bytes[right+2] > 245, "radial center and edge");
        layer.type = kCAGradientLayerConic; layer.endPoint = CGPointMake(1,0.5);
        CGContextClearRect(context,layer.bounds); [layer drawInContext:context];
        gradient_check(bytes[right] > 250 && bytes[right+2] < 5 && abs(bytes[left]-128)<=2, "conic start ray and opposite ray");
        for (size_t i=0;i<101*101;i++) gradient_check(bytes[i*4+3]==255, "opaque coverage");
        layer.bounds = CGRectMake(0,0,50,60); gradient_check([layer needsDisplay], "resize invalidates gradient");
        CGColorRelease(red); CGColorRelease(blue); CGContextRelease(context);
        puts("Gradient layer: model, copy, axial/radial/conic pixels and invalidation OK");
    }
    return 0;
}
