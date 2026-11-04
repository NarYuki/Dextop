#import <QuartzCore/CAGradientLayer.h>
#import <CoreGraphics/CoreGraphics.h>
#import <Onyx2D/O2Color.h>
#include <math.h>
#include <stdint.h>

CAGradientLayerType const kCAGradientLayerAxial = @"axial";
CAGradientLayerType const kCAGradientLayerRadial = @"radial";
CAGradientLayerType const kCAGradientLayerConic = @"conic";

@implementation CAGradientLayer
- (id)init {
    if ((self = [super init])) {
        _gradientStart = CGPointMake(0.5, 0);
        _gradientEnd = CGPointMake(0.5, 1);
        _gradientType = [kCAGradientLayerAxial copy];
        self.needsDisplayOnBoundsChange = YES;
    }
    return self;
}
- (id)initWithLayer:(id)layer {
    if ((self = [super initWithLayer:layer])) {
        if ([layer isKindOfClass:[CAGradientLayer class]]) {
            CAGradientLayer *other = layer;
            _gradientColors = [other.colors copy]; _gradientLocations = [other.locations copy];
            _gradientStart = other.startPoint; _gradientEnd = other.endPoint;
            _gradientType = [other.type copy];
        } else {
            _gradientStart = CGPointMake(0.5, 0); _gradientEnd = CGPointMake(0.5, 1);
            _gradientType = [kCAGradientLayerAxial copy];
        }
        self.needsDisplayOnBoundsChange = YES;
        [self setNeedsDisplay];
    }
    return self;
}
- (void)dealloc {
    [_gradientColors release]; [_gradientLocations release]; [_gradientType release];
    [super dealloc];
}
- (NSArray *)colors { return _gradientColors; }
- (void)setColors:(NSArray *)colors {
    NSArray *copy = [colors copy]; [_gradientColors release]; _gradientColors = copy;
    [self setNeedsDisplay];
}
- (NSArray *)locations { return _gradientLocations; }
- (void)setLocations:(NSArray *)locations {
    NSArray *copy = [locations copy]; [_gradientLocations release]; _gradientLocations = copy;
    [self setNeedsDisplay];
}
- (CGPoint)startPoint { return _gradientStart; }
- (void)setStartPoint:(CGPoint)value { _gradientStart = value; [self setNeedsDisplay]; }
- (CGPoint)endPoint { return _gradientEnd; }
- (void)setEndPoint:(CGPoint)value { _gradientEnd = value; [self setNeedsDisplay]; }
- (CAGradientLayerType)type { return _gradientType; }
- (void)setType:(CAGradientLayerType)value {
    CAGradientLayerType copy = [value copy]; [_gradientType release]; _gradientType = copy;
    [self setNeedsDisplay];
}
- (BOOL)_drawsOwnContent { return YES; }
- (void)drawInContext:(CGContextRef)context {
    NSUInteger count = [_gradientColors count];
    CGRect bounds = self.bounds;
    if (!count || !isfinite(bounds.size.width) || !isfinite(bounds.size.height) || bounds.size.width <= 0 || bounds.size.height <= 0) return;
    if (bounds.size.width >= (double)(SIZE_MAX / 4) || bounds.size.height >= (double)(SIZE_MAX / 4)) return;
    if (_gradientLocations && [_gradientLocations count] != count) return;
    if (count > SIZE_MAX / (5 * sizeof(double))) return;
    double *stops = calloc(count * 5, sizeof(double));
    if (!stops) return;
    double previous = 0;
    for (NSUInteger i = 0; i < count; i++) {
        id color = [_gradientColors objectAtIndex:i];
        if (![color isKindOfClass:[O2Color class]]) { free(stops); return; }
        const O2Float *components = O2ColorGetComponents((O2ColorRef)color);
        O2Float rgba[4];
        if (!O2ColorConvertComponentsToDeviceRGB(O2ColorGetColorSpace((O2ColorRef)color), components, rgba)) { free(stops); return; }
        double position = count == 1 ? 0 : (double)i / (count - 1);
        if (_gradientLocations) position = [[_gradientLocations objectAtIndex:i] doubleValue];
        if (!isfinite(position) || position < 0 || position > 1 || position < previous) { free(stops); return; }
        previous = position;
        stops[i * 5] = position;
        double alpha = fmax(0, fmin(1, rgba[3]));
        for (NSUInteger j = 0; j < 3; j++) stops[i * 5 + 1 + j] = fmax(0, fmin(1, rgba[j])) * alpha;
        stops[i * 5 + 4] = alpha;
    }
    size_t width = (size_t)ceil(bounds.size.width), height = (size_t)ceil(bounds.size.height);
    if (!width || !height || width > SIZE_MAX / 4 || height > SIZE_MAX / (width * 4)) { free(stops); return; }
    CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
    CGContextRef bitmap = CGBitmapContextCreate(NULL, width, height, 8, width * 4, space,
        kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
    CGColorSpaceRelease(space);
    if (!bitmap) { free(stops); return; }
    unsigned char *pixels = CGBitmapContextGetData(bitmap);
    double dx = _gradientEnd.x - _gradientStart.x, dy = _gradientEnd.y - _gradientStart.y;
    BOOL radial = [_gradientType isEqual:kCAGradientLayerRadial];
    BOOL conic = [_gradientType isEqual:kCAGradientLayerConic];
    for (size_t y = 0; y < height; y++) for (size_t x = 0; x < width; x++) {
        double u = (x + 0.5) / width - _gradientStart.x;
        double v = (y + 0.5) / height - _gradientStart.y;
        double t;
        if (conic) {
            t = (atan2(v, u) - atan2(dy, dx)) / (2 * M_PI);
            t -= floor(t);
        } else if (radial) {
            double rx = fabs(dx), ry = fabs(dy);
            t = rx > 0 && ry > 0 ? hypot(u / rx, v / ry) : 1;
        } else {
            double length = dx * dx + dy * dy;
            t = length > 0 ? (u * dx + v * dy) / length : 0;
        }
        NSUInteger hi = 0;
        while (hi < count && stops[hi * 5] <= t) hi++;
        NSUInteger lo = hi ? hi - 1 : 0;
        if (hi == count) hi = count - 1;
        double span = stops[hi * 5] - stops[lo * 5];
        double f = span > 0 ? fmax(0, fmin(1, (t - stops[lo * 5]) / span)) : 0;
        for (NSUInteger j = 0; j < 4; j++)
            pixels[(y * width + x) * 4 + j] = (unsigned char)lrint(255 * (stops[lo * 5 + j + 1] * (1 - f) + stops[hi * 5 + j + 1] * f));
    }
    CGImageRef image = CGBitmapContextCreateImage(bitmap);
    if (image) { CGContextDrawImage(context, bounds, image); CGImageRelease(image); }
    CGContextRelease(bitmap); free(stops);
}
@end
