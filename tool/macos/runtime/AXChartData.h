// Shared chart data models. No application-specific behavior.
#import <Foundation/Foundation.h>
#import <CoreGraphics/CGGeometry.h>

@protocol AXDataAxisDescriptor <NSCopying>
@property(nonatomic, copy) NSString *title;
@property(nonatomic, copy) NSAttributedString *attributedTitle;
@end

@interface AXCategoricalDataAxisDescriptor : NSObject <AXDataAxisDescriptor> {
    NSAttributedString *_attributedTitle;
    NSArray *_categoryOrder;
}
- (instancetype)initWithTitle:(NSString *)title categoryOrder:(NSArray<NSString *> *)categories;
- (instancetype)initWithAttributedTitle:(NSAttributedString *)title categoryOrder:(NSArray<NSString *> *)categories;
@property(nonatomic, copy) NSString *title;
@property(nonatomic, copy) NSAttributedString *attributedTitle;
@property(nonatomic, copy) NSArray<NSString *> *categoryOrder;
@end

@interface AXNumericDataAxisDescriptor : NSObject <AXDataAxisDescriptor> {
    NSAttributedString *_attributedTitle;
    double _lowerBound, _upperBound;
    NSArray *_gridlinePositions;
    NSString *(^_valueDescriptionProvider)(double);
}
- (instancetype)initWithTitle:(NSString *)title lowerBound:(double)lower upperBound:(double)upper gridlinePositions:(NSArray<NSNumber *> *)positions valueDescriptionProvider:(NSString *(^)(double))provider;
- (instancetype)initWithAttributedTitle:(NSAttributedString *)title lowerBound:(double)lower upperBound:(double)upper gridlinePositions:(NSArray<NSNumber *> *)positions valueDescriptionProvider:(NSString *(^)(double))provider;
@property(nonatomic, copy) NSString *title;
@property(nonatomic, copy) NSAttributedString *attributedTitle;
@property(nonatomic) double lowerBound;
@property(nonatomic) double upperBound;
@property(nonatomic, copy) NSArray<NSNumber *> *gridlinePositions;
@property(nonatomic, copy) NSString *(^valueDescriptionProvider)(double);
@end

@interface AXDataPointValue : NSObject <NSCopying> {
    double _number;
    NSString *_category;
}
+ (instancetype)valueWithNumber:(double)number;
+ (instancetype)valueWithCategory:(NSString *)category;
@property(nonatomic) double number;
@property(nonatomic, copy) NSString *category;
@end

@interface AXDataPoint : NSObject <NSCopying> {
    AXDataPointValue *_xValue, *_yValue;
    NSArray *_additionalValues;
    NSAttributedString *_attributedLabel;
}
- (instancetype)initWithX:(AXDataPointValue *)x y:(AXDataPointValue *)y;
- (instancetype)initWithX:(AXDataPointValue *)x y:(AXDataPointValue *)y additionalValues:(NSArray<AXDataPointValue *> *)values;
- (instancetype)initWithX:(AXDataPointValue *)x y:(AXDataPointValue *)y additionalValues:(NSArray<AXDataPointValue *> *)values label:(NSString *)label;
@property(nonatomic, copy) AXDataPointValue *xValue;
@property(nonatomic, copy) AXDataPointValue *yValue;
@property(nonatomic, copy) NSArray<AXDataPointValue *> *additionalValues;
@property(nonatomic, copy) NSString *label;
@property(nonatomic, copy) NSAttributedString *attributedLabel;
@end

@interface AXDataSeriesDescriptor : NSObject <NSCopying> {
    NSAttributedString *_attributedName;
    BOOL _isContinuous;
    NSArray *_dataPoints;
}
- (instancetype)initWithName:(NSString *)name isContinuous:(BOOL)continuous dataPoints:(NSArray<AXDataPoint *> *)points;
- (instancetype)initWithAttributedName:(NSAttributedString *)name isContinuous:(BOOL)continuous dataPoints:(NSArray<AXDataPoint *> *)points;
@property(nonatomic, copy) NSString *name;
@property(nonatomic, copy) NSAttributedString *attributedName;
@property(nonatomic) BOOL isContinuous;
@property(nonatomic, copy) NSArray<AXDataPoint *> *dataPoints;
@end

@interface AXChartDescriptor : NSObject <NSCopying> {
    NSAttributedString *_attributedTitle;
    NSString *_summary;
    id<AXDataAxisDescriptor> _xAxis;
    AXNumericDataAxisDescriptor *_yAxis;
    NSArray *_additionalAxes, *_series;
    CGRect _contentFrame;
    NSInteger _contentDirection;
}
- (instancetype)initWithTitle:(NSString *)title summary:(NSString *)summary xAxisDescriptor:(id<AXDataAxisDescriptor>)x yAxisDescriptor:(AXNumericDataAxisDescriptor *)y series:(NSArray<AXDataSeriesDescriptor *> *)series;
- (instancetype)initWithAttributedTitle:(NSAttributedString *)title summary:(NSString *)summary xAxisDescriptor:(id<AXDataAxisDescriptor>)x yAxisDescriptor:(AXNumericDataAxisDescriptor *)y series:(NSArray<AXDataSeriesDescriptor *> *)series;
- (instancetype)initWithTitle:(NSString *)title summary:(NSString *)summary xAxisDescriptor:(id<AXDataAxisDescriptor>)x yAxisDescriptor:(AXNumericDataAxisDescriptor *)y additionalAxes:(NSArray<id<AXDataAxisDescriptor>> *)axes series:(NSArray<AXDataSeriesDescriptor *> *)series;
- (instancetype)initWithAttributedTitle:(NSAttributedString *)title summary:(NSString *)summary xAxisDescriptor:(id<AXDataAxisDescriptor>)x yAxisDescriptor:(AXNumericDataAxisDescriptor *)y additionalAxes:(NSArray<id<AXDataAxisDescriptor>> *)axes series:(NSArray<AXDataSeriesDescriptor *> *)series;
@property(nonatomic, copy) NSString *title;
@property(nonatomic, copy) NSAttributedString *attributedTitle;
@property(nonatomic, copy) NSString *summary;
@property(nonatomic, retain) id<AXDataAxisDescriptor> xAxis;
@property(nonatomic, retain) AXNumericDataAxisDescriptor *yAxis;
@property(nonatomic, copy) NSArray<id<AXDataAxisDescriptor>> *additionalAxes;
@property(nonatomic, copy) NSArray<AXDataSeriesDescriptor *> *series;
@property(nonatomic) CGRect contentFrame;
// Raw layout value until public enum constants have been independently verified.
@property(nonatomic) NSInteger contentDirection;
@end
