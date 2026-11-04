#import <Foundation/Foundation.h>
#import <QuartzCore/QuartzCore.h>
#include <math.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>

@interface CAMediaTimingFunction (DextopProbe)
- (double)_dextopValueForProgress:(double)progress;
@end
static void timing_check(BOOL condition, const char *message) {
    if (!condition) { fprintf(stderr,"Timing function FAIL: %s\n",message); exit(1); }
}
int main(void) {
    @autoreleasepool {
        CAMediaTimingFunction *custom = [[[CAMediaTimingFunction alloc] initWithControlPoints:0.1f :0 :0.1f :1] autorelease];
        struct { uint32_t before; float values[2]; uint32_t after; } guarded = {0x12345678,{0,0},0xaabbccdd};
        [custom getControlPointAtIndex:1 values:guarded.values];
        timing_check(guarded.before==0x12345678 && guarded.after==0xaabbccdd,"float output buffer bounds");
        timing_check(fabs(guarded.values[0]-.1)<1e-6 && guarded.values[1]==0,"float argument and output ABI");
        [custom getControlPointAtIndex:2 values:guarded.values];
        timing_check(fabs(guarded.values[0]-.1)<1e-6 && guarded.values[1]==1,"second control point");
        timing_check(fabs([custom _dextopValueForProgress:.2]-.5)<1e-6,"invert cubic X before evaluating Y");
        CAMediaTimingFunction *linear = [CAMediaTimingFunction functionWithName:kCAMediaTimingFunctionLinear];
        double samples[]={0,.1,.25,.5,.9,1};
        for (size_t i=0;i<sizeof(samples)/sizeof(samples[0]);i++)
            timing_check(fabs([linear _dextopValueForProgress:samples[i]]-samples[i])<1e-10,"linear speed is constant");
        CAMediaTimingFunction *overshoot = [CAMediaTimingFunction functionWithControlPoints:0 :2 :1 :2];
        timing_check(fabs([overshoot _dextopValueForProgress:.5]-1.625)<1e-8,"Y overshoot is preserved");
        CAMediaTimingFunction *copy = [[custom copy] autorelease];
        timing_check(fabs([copy _dextopValueForProgress:.2]-.5)<1e-6,"copy preserves curve");
        NSData *archive = [NSKeyedArchiver archivedDataWithRootObject:custom];
        CAMediaTimingFunction *restored = [NSKeyedUnarchiver unarchiveObjectWithData:archive];
        timing_check(fabs([restored _dextopValueForProgress:.2]-.5)<1e-6,"archive preserves curve");
        BOOL invalid=NO;
        @try { [custom getControlPointAtIndex:4 values:guarded.values]; }
        @catch (NSException *exception) { invalid=YES; }
        timing_check(invalid,"invalid index rejected");
        puts("Timing function: float ABI, guarded output, inverse curve, copy and archive OK");
    }
    return 0;
}
