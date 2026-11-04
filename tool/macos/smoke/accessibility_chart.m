#import <Foundation/Foundation.h>
#import <Accessibility/Accessibility.h>
#include <stdio.h>
#include <stdlib.h>

static void chart_check(BOOL condition, const char *message) {
    if (!condition) { fprintf(stderr, "FAIL: %s\n", message); exit(1); }
}
static AXNumericDataAxisDescriptor *makeAxis(void) {
    NSString *unit = [NSString stringWithFormat:@"%@", @"kg"];
    return [[[AXNumericDataAxisDescriptor alloc] initWithTitle:@"Weight" lowerBound:-2 upperBound:8 gridlinePositions:@[@0, @4, @8] valueDescriptionProvider:^NSString *(double value) {
        return [NSString stringWithFormat:@"%.1f %@", value, unit];
    }] autorelease];
}
int main(void) {
    NSAutoreleasePool *pool = [NSAutoreleasePool new];
    NSMutableString *category = [NSMutableString stringWithString:@"Monday"];
    AXDataPointValue *x = [AXDataPointValue valueWithCategory:category];
    [category appendString:@" changed"];
    chart_check([x.category isEqual:@"Monday"], "category owns an immutable copy");
    AXDataPointValue *y = [AXDataPointValue valueWithNumber:42.25];
    NSMutableArray *values = [NSMutableArray arrayWithObject:[AXDataPointValue valueWithNumber:3]];
    AXDataPoint *point = [[[AXDataPoint alloc] initWithX:x y:y additionalValues:values label:@"sample"] autorelease];
    [values removeAllObjects];
    chart_check(point.additionalValues.count == 1, "point copies input collection");
    y.number = 99;
    chart_check(point.yValue.number == 42.25, "point coordinate snapshot");
    AXDataPoint *copy = [[point copy] autorelease];
    point.label = @"updated";
    point.xValue.number = 17;
    chart_check([copy.label isEqual:@"sample"] && copy.xValue.number == 0, "copied point retains independent coordinates and label");
    NSAttributedString *rich = [[[NSAttributedString alloc] initWithString:@"rich" attributes:@{@"ChartTest": @7}] autorelease];
    copy.attributedLabel = rich;
    chart_check([copy.label isEqual:@"rich"] && [[copy.attributedLabel attribute:@"ChartTest" atIndex:0 effectiveRange:NULL] isEqual:@7], "attributed label preserved");
    NSMutableArray *points = [NSMutableArray arrayWithObject:copy];
    AXDataSeriesDescriptor *series = [[[AXDataSeriesDescriptor alloc] initWithAttributedName:rich isContinuous:YES dataPoints:points] autorelease];
    [points removeAllObjects];
    AXDataSeriesDescriptor *other = [[series copy] autorelease];
    series.name = @"renamed"; series.isContinuous = NO; series.dataPoints = @[];
    chart_check(other.isContinuous && other.dataPoints.count == 1 && [other.name isEqual:@"rich"], "series copy survives independent updates");
    chart_check([[other.attributedName attribute:@"ChartTest" atIndex:0 effectiveRange:NULL] isEqual:@7], "series copy preserves attributes");
    AXDataPoint *single = [[[AXDataPoint alloc] initWithX:x y:nil] autorelease];
    chart_check(single.yValue == nil && single.additionalValues.count == 0 && single.label == nil, "optional Y and label");
    BOOL rejected = NO;
    @try { [[[AXDataSeriesDescriptor alloc] initWithName:nil isContinuous:NO dataPoints:(id)@[@1]] autorelease]; }
    @catch (NSException *exception) { rejected = YES; }
    chart_check(rejected, "invalid initial data element rejected");
    AXNumericDataAxisDescriptor *axis = makeAxis();
    chart_check([axis conformsToProtocol:@protocol(AXDataAxisDescriptor)], "numeric axis protocol");
    chart_check([axis.valueDescriptionProvider(3.25) isEqual:@"3.2 kg"], "copied callback survives creator stack");
    AXNumericDataAxisDescriptor *axisCopy = [[axis copy] autorelease];
    axis.lowerBound = 10; axis.upperBound = 20; axis.title = @"new"; axis.gridlinePositions = @[];
    axis.valueDescriptionProvider = ^NSString *(double value) { return @"replacement"; };
    chart_check(axisCopy.lowerBound == -2 && axisCopy.upperBound == 8 && [axisCopy.title isEqual:@"Weight"] && axisCopy.gridlinePositions.count == 3, "numeric axis copy independent metadata");
    chart_check([axisCopy.valueDescriptionProvider(2) isEqual:@"2.0 kg"], "copied axis retains original callback");
    axisCopy.valueDescriptionProvider = nil;
    chart_check(axisCopy.valueDescriptionProvider == nil, "callback may be cleared without dangling pointer");
    NSMutableArray *categories = [NSMutableArray arrayWithObjects:@"C", @"A", @"B", nil];
    AXCategoricalDataAxisDescriptor *categorical = [[[AXCategoricalDataAxisDescriptor alloc] initWithAttributedTitle:rich categoryOrder:categories] autorelease];
    [categories removeAllObjects];
    AXCategoricalDataAxisDescriptor *categoricalCopy = [[categorical copy] autorelease];
    categorical.title = @"updated"; categorical.categoryOrder = @[];
    chart_check([categoricalCopy conformsToProtocol:@protocol(AXDataAxisDescriptor)] && [categoricalCopy.categoryOrder isEqual:(@[@"C", @"A", @"B"])], "categorical axis preserves supplied order and copy");
    chart_check([[categoricalCopy.attributedTitle attribute:@"ChartTest" atIndex:0 effectiveRange:NULL] isEqual:@7], "axis attributed title survives copying");
    AXChartDescriptor *chart = [[[AXChartDescriptor alloc] initWithAttributedTitle:rich summary:@"summary" xAxisDescriptor:categoricalCopy yAxisDescriptor:axisCopy additionalAxes:@[categorical] series:@[other]] autorelease];
    chart.contentFrame = CGRectMake(12, 34, 200, 100);
    chart.contentDirection = 4;
    chart_check(chart.xAxis == categoricalCopy && chart.yAxis == axisCopy, "chart axes retain reference identity");
    AXChartDescriptor *chartCopy = [[chart copy] autorelease];
    categoricalCopy.title = @"mutated source"; axisCopy.lowerBound = 500;
    chart.title = @"new title"; chart.summary = nil; chart.series = @[];
    chart.contentFrame = CGRectMake(0, 0, 1, 1); chart.contentDirection = 0;
    chart_check([chartCopy.title isEqual:@"rich"] && [chartCopy.summary isEqual:@"summary"] && chartCopy.series.count == 1, "chart metadata and collections copy");
    chart_check([chartCopy.xAxis.title isEqual:@"rich"] && chartCopy.yAxis.lowerBound == -2, "chart copied axes independent");
    chart_check(chartCopy.contentFrame.origin.x == 12 && chartCopy.contentFrame.size.height == 100 && chartCopy.contentDirection == 4, "chart layout metadata preserved");
    chart_check([[chartCopy.attributedTitle attribute:@"ChartTest" atIndex:0 effectiveRange:NULL] isEqual:@7], "chart attributed title preserved");
    AXChartDescriptor *minimal = [[[AXChartDescriptor alloc] initWithTitle:nil summary:nil xAxisDescriptor:categorical yAxisDescriptor:nil series:@[]] autorelease];
    chart_check(minimal.title == nil && minimal.yAxis == nil && minimal.additionalAxes.count == 0, "minimal chart constructor");
    puts("Accessibility chart data OK");
    [pool drain]; return 0;
}
