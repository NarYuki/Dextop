#import <Accessibility/AXChartData.h>
#include <Block.h>

@implementation AXChartDescriptor
@synthesize attributedTitle = _attributedTitle, summary = _summary;
@synthesize xAxis = _xAxis, yAxis = _yAxis, additionalAxes = _additionalAxes, series = _series;
@synthesize contentFrame = _contentFrame, contentDirection = _contentDirection;
- (instancetype)initWithTitle:(NSString *)title summary:(NSString *)summary xAxisDescriptor:(id<AXDataAxisDescriptor>)x yAxisDescriptor:(AXNumericDataAxisDescriptor *)y series:(NSArray *)series {
    return [self initWithTitle:title summary:summary xAxisDescriptor:x yAxisDescriptor:y additionalAxes:@[] series:series];
}
- (instancetype)initWithAttributedTitle:(NSAttributedString *)title summary:(NSString *)summary xAxisDescriptor:(id<AXDataAxisDescriptor>)x yAxisDescriptor:(AXNumericDataAxisDescriptor *)y series:(NSArray *)series {
    return [self initWithAttributedTitle:title summary:summary xAxisDescriptor:x yAxisDescriptor:y additionalAxes:@[] series:series];
}
- (instancetype)initWithTitle:(NSString *)title summary:(NSString *)summary xAxisDescriptor:(id<AXDataAxisDescriptor>)x yAxisDescriptor:(AXNumericDataAxisDescriptor *)y additionalAxes:(NSArray *)axes series:(NSArray *)series {
    NSAttributedString *attributed = title ? [[[NSAttributedString alloc] initWithString:title] autorelease] : nil;
    return [self initWithAttributedTitle:attributed summary:summary xAxisDescriptor:x yAxisDescriptor:y additionalAxes:axes series:series];
}
- (instancetype)initWithAttributedTitle:(NSAttributedString *)title summary:(NSString *)summary xAxisDescriptor:(id<AXDataAxisDescriptor>)x yAxisDescriptor:(AXNumericDataAxisDescriptor *)y additionalAxes:(NSArray *)axes series:(NSArray *)series {
    if ((self = [super init])) {
        @try {
            if (![(id)x conformsToProtocol:@protocol(AXDataAxisDescriptor)] || (y && ![y isKindOfClass:[AXNumericDataAxisDescriptor class]]) || ![axes isKindOfClass:[NSArray class]] || ![series isKindOfClass:[NSArray class]])
                [NSException raise:NSInvalidArgumentException format:@"Invalid chart axes or series"];
            for (id axis in axes) if (![axis conformsToProtocol:@protocol(AXDataAxisDescriptor)])
                [NSException raise:NSInvalidArgumentException format:@"Invalid additional chart axis"];
            for (id data in series) if (![data isKindOfClass:[AXDataSeriesDescriptor class]])
                [NSException raise:NSInvalidArgumentException format:@"Invalid chart series"];
            self.attributedTitle = title; self.summary = summary;
            self.xAxis = x; self.yAxis = y; self.additionalAxes = axes; self.series = series;
        } @catch (id exception) { [self release]; @throw; }
    }
    return self;
}
- (NSString *)title { return [_attributedTitle string]; }
- (void)setTitle:(NSString *)title {
    self.attributedTitle = title ? [[[NSAttributedString alloc] initWithString:title] autorelease] : nil;
}
- (id)copyWithZone:(NSZone *)zone {
    id x = [[(id)_xAxis copy] autorelease];
    id y = [[_yAxis copy] autorelease];
    NSArray *axes = [[[NSArray alloc] initWithArray:_additionalAxes copyItems:YES] autorelease];
    NSArray *series = [[[NSArray alloc] initWithArray:_series copyItems:YES] autorelease];
    AXChartDescriptor *copy = [[[self class] allocWithZone:zone] initWithAttributedTitle:_attributedTitle summary:_summary xAxisDescriptor:x yAxisDescriptor:y additionalAxes:axes series:series];
    copy.contentFrame = _contentFrame; copy.contentDirection = _contentDirection;
    return copy;
}
- (void)dealloc {
    [_attributedTitle release]; [_summary release]; [(id)_xAxis release]; [_yAxis release];
    [_additionalAxes release]; [_series release]; [super dealloc];
}
@end

static void AXRequireArray(NSArray *values, Class type) {
    if (![values isKindOfClass:[NSArray class]])
        [NSException raise:NSInvalidArgumentException format:@"Chart data requires an array"];
    for (id value in values)
        if (![value isKindOfClass:type])
            [NSException raise:NSInvalidArgumentException format:@"Invalid chart element %@", value];
}

@implementation AXCategoricalDataAxisDescriptor
@synthesize attributedTitle = _attributedTitle, categoryOrder = _categoryOrder;
- (instancetype)initWithTitle:(NSString *)title categoryOrder:(NSArray *)categories {
    return [self initWithAttributedTitle:[[[NSAttributedString alloc] initWithString:title] autorelease] categoryOrder:categories];
}
- (instancetype)initWithAttributedTitle:(NSAttributedString *)title categoryOrder:(NSArray *)categories {
    if ((self = [super init])) {
        @try {
            AXRequireArray(categories, [NSString class]);
            self.attributedTitle = title; self.categoryOrder = categories;
        } @catch (id exception) { [self release]; @throw; }
    }
    return self;
}
- (NSString *)title { return [_attributedTitle string]; }
- (void)setTitle:(NSString *)title {
    self.attributedTitle = [[[NSAttributedString alloc] initWithString:title] autorelease];
}
- (id)copyWithZone:(NSZone *)zone {
    return [[[self class] allocWithZone:zone] initWithAttributedTitle:_attributedTitle categoryOrder:_categoryOrder];
}
- (void)dealloc { [_attributedTitle release]; [_categoryOrder release]; [super dealloc]; }
@end

@implementation AXNumericDataAxisDescriptor
@synthesize attributedTitle = _attributedTitle, lowerBound = _lowerBound, upperBound = _upperBound;
@synthesize gridlinePositions = _gridlinePositions;
- (instancetype)initWithTitle:(NSString *)title lowerBound:(double)lower upperBound:(double)upper gridlinePositions:(NSArray *)positions valueDescriptionProvider:(NSString *(^)(double))provider {
    return [self initWithAttributedTitle:[[[NSAttributedString alloc] initWithString:title] autorelease] lowerBound:lower upperBound:upper gridlinePositions:positions valueDescriptionProvider:provider];
}
- (instancetype)initWithAttributedTitle:(NSAttributedString *)title lowerBound:(double)lower upperBound:(double)upper gridlinePositions:(NSArray *)positions valueDescriptionProvider:(NSString *(^)(double))provider {
    if ((self = [super init])) {
        @try {
            AXRequireArray(positions, [NSNumber class]);
            self.attributedTitle = title; self.lowerBound = lower; self.upperBound = upper;
            self.gridlinePositions = positions; self.valueDescriptionProvider = provider;
        } @catch (id exception) { [self release]; @throw; }
    }
    return self;
}
- (NSString *)title { return [_attributedTitle string]; }
- (void)setTitle:(NSString *)title {
    self.attributedTitle = [[[NSAttributedString alloc] initWithString:title] autorelease];
}
- (NSString *(^)(double))valueDescriptionProvider { return _valueDescriptionProvider; }
- (void)setValueDescriptionProvider:(NSString *(^)(double))provider {
    NSString *(^copied)(double) = provider ? Block_copy(provider) : NULL;
    if (_valueDescriptionProvider) Block_release(_valueDescriptionProvider);
    _valueDescriptionProvider = copied;
}
- (id)copyWithZone:(NSZone *)zone {
    return [[[self class] allocWithZone:zone] initWithAttributedTitle:_attributedTitle lowerBound:_lowerBound upperBound:_upperBound gridlinePositions:_gridlinePositions valueDescriptionProvider:_valueDescriptionProvider];
}
- (void)dealloc {
    [_attributedTitle release]; [_gridlinePositions release];
    if (_valueDescriptionProvider) Block_release(_valueDescriptionProvider);
    [super dealloc];
}
@end

@implementation AXDataPointValue
@synthesize number = _number, category = _category;
+ (instancetype)valueWithNumber:(double)value {
    AXDataPointValue *result = [[[self alloc] init] autorelease];
    result.number = value;
    return result;
}
+ (instancetype)valueWithCategory:(NSString *)value {
    if (![value isKindOfClass:[NSString class]])
        [NSException raise:NSInvalidArgumentException format:@"A category must be a string"];
    AXDataPointValue *result = [[[self alloc] init] autorelease];
    result.category = value;
    return result;
}
- (id)copyWithZone:(NSZone *)zone {
    AXDataPointValue *result = [[[self class] allocWithZone:zone] init];
    result.number = _number;
    result.category = _category;
    return result;
}
- (void)dealloc { [_category release]; [super dealloc]; }
@end

@implementation AXDataPoint
@synthesize xValue = _xValue, yValue = _yValue;
@synthesize additionalValues = _additionalValues, attributedLabel = _attributedLabel;
- (instancetype)initWithX:(AXDataPointValue *)x y:(AXDataPointValue *)y {
    return [self initWithX:x y:y additionalValues:@[] label:nil];
}
- (instancetype)initWithX:(AXDataPointValue *)x y:(AXDataPointValue *)y additionalValues:(NSArray *)values {
    return [self initWithX:x y:y additionalValues:values label:nil];
}
- (instancetype)initWithX:(AXDataPointValue *)x y:(AXDataPointValue *)y additionalValues:(NSArray *)values label:(NSString *)label {
    if ((self = [super init])) {
        @try {
            if (![x isKindOfClass:[AXDataPointValue class]] || (y && ![y isKindOfClass:[AXDataPointValue class]]))
                [NSException raise:NSInvalidArgumentException format:@"Invalid chart coordinates"];
            AXRequireArray(values, [AXDataPointValue class]);
            self.xValue = x; self.yValue = y;
            self.additionalValues = values; self.label = label;
        } @catch (id exception) { [self release]; @throw; }
    }
    return self;
}
- (NSString *)label { return [_attributedLabel string]; }
- (void)setLabel:(NSString *)label {
    self.attributedLabel = label ? [[[NSAttributedString alloc] initWithString:label] autorelease] : nil;
}
- (id)copyWithZone:(NSZone *)zone {
    AXDataPoint *result = [[[self class] allocWithZone:zone] initWithX:_xValue y:_yValue additionalValues:_additionalValues];
    result.attributedLabel = _attributedLabel;
    return result;
}
- (void)dealloc {
    [_xValue release]; [_yValue release]; [_additionalValues release];
    [_attributedLabel release]; [super dealloc];
}
@end

@implementation AXDataSeriesDescriptor
@synthesize attributedName = _attributedName, isContinuous = _isContinuous, dataPoints = _dataPoints;
- (instancetype)initWithName:(NSString *)name isContinuous:(BOOL)continuous dataPoints:(NSArray *)points {
    NSAttributedString *attributed = name ? [[[NSAttributedString alloc] initWithString:name] autorelease] : nil;
    return [self initWithAttributedName:attributed isContinuous:continuous dataPoints:points];
}
- (instancetype)initWithAttributedName:(NSAttributedString *)name isContinuous:(BOOL)continuous dataPoints:(NSArray *)points {
    if ((self = [super init])) {
        @try {
            AXRequireArray(points, [AXDataPoint class]);
            self.attributedName = name; self.isContinuous = continuous; self.dataPoints = points;
        } @catch (id exception) { [self release]; @throw; }
    }
    return self;
}
- (NSString *)name { return [_attributedName string]; }
- (void)setName:(NSString *)name {
    self.attributedName = name ? [[[NSAttributedString alloc] initWithString:name] autorelease] : nil;
}
- (id)copyWithZone:(NSZone *)zone {
    return [[[self class] allocWithZone:zone] initWithAttributedName:_attributedName isContinuous:_isContinuous dataPoints:_dataPoints];
}
- (void)dealloc { [_attributedName release]; [_dataPoints release]; [super dealloc]; }
@end
