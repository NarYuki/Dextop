#import <Foundation/NSObject.h>
#import <QuartzCore/CABase.h>

typedef NSString *CAMediaTimingFunctionName NS_TYPED_ENUM;
CA_EXPORT CAMediaTimingFunctionName const kCAMediaTimingFunctionLinear;
CA_EXPORT CAMediaTimingFunctionName const kCAMediaTimingFunctionEaseIn;
CA_EXPORT CAMediaTimingFunctionName const kCAMediaTimingFunctionEaseOut;
CA_EXPORT CAMediaTimingFunctionName const kCAMediaTimingFunctionEaseInEaseOut;
CA_EXPORT CAMediaTimingFunctionName const kCAMediaTimingFunctionDefault;

@interface CAMediaTimingFunction : NSObject <NSCopying, NSSecureCoding> {
    // Keep the existing private object layout; public parameters use float.
    CGFloat _c1x, _c1y, _c2x, _c2y;
}
- (nonnull instancetype)initWithControlPoints:(float)c1x :(float)c1y :(float)c2x :(float)c2y;
+ (nonnull instancetype)functionWithControlPoints:(float)c1x :(float)c1y :(float)c2x :(float)c2y;
+ (nonnull instancetype)functionWithName:(nonnull CAMediaTimingFunctionName)name;
- (void)getControlPointAtIndex:(size_t)index values:(float * _Nonnull)values;
@end
