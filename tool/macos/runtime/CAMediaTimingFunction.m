#import <Foundation/Foundation.h>
#import <QuartzCore/CAMediaTimingFunction.h>
#include <math.h>

CAMediaTimingFunctionName const kCAMediaTimingFunctionLinear = @"kCAMediaTimingFunctionLinear";
CAMediaTimingFunctionName const kCAMediaTimingFunctionEaseIn = @"kCAMediaTimingFunctionEaseIn";
CAMediaTimingFunctionName const kCAMediaTimingFunctionEaseOut = @"kCAMediaTimingFunctionEaseOut";
CAMediaTimingFunctionName const kCAMediaTimingFunctionEaseInEaseOut = @"kCAMediaTimingFunctionEaseInEaseOut";
CAMediaTimingFunctionName const kCAMediaTimingFunctionDefault = @"kCAMediaTimingFunctionDefault";

static double bezier(double s, double p1, double p2) {
    double inverse = 1 - s;
    return 3 * inverse * inverse * s * p1 + 3 * inverse * s * s * p2 + s * s * s;
}

@implementation CAMediaTimingFunction
- (instancetype)initWithControlPoints:(float)c1x :(float)c1y :(float)c2x :(float)c2y {
    if ((self = [super init])) { _c1x = c1x; _c1y = c1y; _c2x = c2x; _c2y = c2y; }
    return self;
}
+ (instancetype)functionWithControlPoints:(float)c1x :(float)c1y :(float)c2x :(float)c2y {
    return [[[self alloc] initWithControlPoints:c1x :c1y :c2x :c2y] autorelease];
}
+ (instancetype)functionWithName:(CAMediaTimingFunctionName)name {
    if ([name isEqual:kCAMediaTimingFunctionLinear]) return [self functionWithControlPoints:0 :0 :1 :1];
    // Retain the existing named curves until their exact presets are verified.
    if ([name isEqual:kCAMediaTimingFunctionEaseIn]) return [self functionWithControlPoints:0.5 :0 :1 :1];
    if ([name isEqual:kCAMediaTimingFunctionEaseOut]) return [self functionWithControlPoints:0 :0 :0.5 :1];
    if ([name isEqual:kCAMediaTimingFunctionEaseInEaseOut]) return [self functionWithControlPoints:0.5 :0 :0.5 :1];
    if ([name isEqual:kCAMediaTimingFunctionDefault]) return [self functionWithControlPoints:0.25 :0.1 :0.25 :1];
    [NSException raise:NSInvalidArgumentException format:@"Unknown timing function %@", name];
    return nil;
}
- (void)getControlPointAtIndex:(size_t)index values:(float *)values {
    if (index > 3 || !values) [NSException raise:NSInvalidArgumentException format:@"Invalid timing control point"];
    if (index == 0) { values[0] = 0; values[1] = 0; }
    if (index == 1) { values[0] = _c1x; values[1] = _c1y; }
    if (index == 2) { values[0] = _c2x; values[1] = _c2y; }
    if (index == 3) { values[0] = 1; values[1] = 1; }
}
- (double)_dextopValueForProgress:(double)progress {
    if (progress <= 0) return 0;
    if (progress >= 1) return 1;
    if (!isfinite(progress)) return progress;
    double low = 0, high = 1;
    // Solve x(s)=progress, then evaluate y(s). Direct y(progress) changes speed
    // even for the nominally linear (0,0,1,1) curve.
    for (unsigned int i = 0; i < 48; i++) {
        double middle = (low + high) * 0.5;
        if (bezier(middle, _c1x, _c2x) < progress) low = middle;
        else high = middle;
    }
    return bezier((low + high) * 0.5, _c1y, _c2y);
}
- (id)copyWithZone:(NSZone *)zone {
    return [[[self class] allocWithZone:zone] initWithControlPoints:_c1x :_c1y :_c2x :_c2y];
}
+ (BOOL)supportsSecureCoding { return YES; }
- (void)encodeWithCoder:(NSCoder *)coder {
    [coder encodeFloat:_c1x forKey:@"Dextop.c1x"]; [coder encodeFloat:_c1y forKey:@"Dextop.c1y"];
    [coder encodeFloat:_c2x forKey:@"Dextop.c2x"]; [coder encodeFloat:_c2y forKey:@"Dextop.c2y"];
}
- (id)initWithCoder:(NSCoder *)coder {
    return [self initWithControlPoints:[coder decodeFloatForKey:@"Dextop.c1x"] :[coder decodeFloatForKey:@"Dextop.c1y"] :[coder decodeFloatForKey:@"Dextop.c2x"] :[coder decodeFloatForKey:@"Dextop.c2y"]];
}
@end
