#import <QuartzCore/CALayer.h>

typedef NSString *CAGradientLayerType NS_TYPED_EXTENSIBLE_ENUM;
CA_EXPORT CAGradientLayerType const kCAGradientLayerAxial NS_SWIFT_NAME(CAGradientLayerType.axial);
CA_EXPORT CAGradientLayerType const kCAGradientLayerRadial NS_SWIFT_NAME(CAGradientLayerType.radial);
CA_EXPORT CAGradientLayerType const kCAGradientLayerConic NS_SWIFT_NAME(CAGradientLayerType.conic);

@interface CAGradientLayer : CALayer {
    NSArray *_gradientColors, *_gradientLocations;
    CGPoint _gradientStart, _gradientEnd;
    CAGradientLayerType _gradientType;
}
@property(copy, nullable) NSArray *colors;
@property(copy, nullable) NSArray<NSNumber *> *locations;
@property CGPoint startPoint;
@property CGPoint endPoint;
@property(copy, nonnull) CAGradientLayerType type;
@end
