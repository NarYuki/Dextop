/* Own-source Metal/MSL compute check; no precompiled Apple shader is used. */
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <stdio.h>
#include <string.h>

static unsigned differences(const float *input, const float *output)
{
    unsigned count = 0;
    for (unsigned i = 0; i < 4096; ++i) {
        float expected = input[i] * 2.0f + 1.0f;
        if (memcmp(&expected, &output[i], sizeof(float))) ++count;
    }
    return count;
}

int main(void)
{
    setvbuf(stdout, NULL, _IOLBF, 0);
    @autoreleasepool {
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (!device) { puts("metal: no device"); return 1; }
        printf("metal: device=%s\n", [[device name] UTF8String]);
        NSError *error = nil;
        NSString *source = @"kernel void dextop(device const float* input [[buffer(0)]], "
            "device float* output [[buffer(1)]], uint i [[thread_position_in_grid]]) "
            "{ output[i] = input[i] * 2.0f + 1.0f; }";
        id<MTLLibrary> library = [device newLibraryWithSource:source options:nil error:&error];
        if (!library) {
            printf("metal: source compilation failed: %s\n", [[error description] UTF8String]);
            return 2;
        }
        id<MTLFunction> function = [library newFunctionWithName:@"dextop"];
        id<MTLComputePipelineState> pipeline = [device newComputePipelineStateWithFunction:function error:&error];
        if (!function || !pipeline) return 3;
        id<MTLBuffer> input = [device newBufferWithLength:4096 * sizeof(float) options:MTLResourceStorageModeShared];
        id<MTLBuffer> output = [device newBufferWithLength:4096 * sizeof(float) options:MTLResourceStorageModeShared];
        if (!input || !output || ![input contents] || ![output contents]) return 4;
        float *a = [input contents], *b = [output contents];
        for (unsigned i = 0; i < 4096; ++i) { a[i] = (i % 101) * 0.25f; b[i] = -12345.0f; }
        id<MTLCommandQueue> queue = [device newCommandQueue];
        id<MTLCommandBuffer> command = [queue commandBuffer];
        id<MTLComputeCommandEncoder> encoder = [command computeCommandEncoder];
        if (!queue || !command || !encoder) return 5;
        [encoder setComputePipelineState:pipeline];
        [encoder setBuffer:input offset:0 atIndex:0];
        [encoder setBuffer:output offset:0 atIndex:1];
        NSUInteger group = [pipeline maxTotalThreadsPerThreadgroup];
        if (!group) return 6;
        if (group > 64) group = 64;
        [encoder dispatchThreads:MTLSizeMake(4096, 1, 1) threadsPerThreadgroup:MTLSizeMake(group, 1, 1)];
        [encoder endEncoding];
        [command commit];
        [command waitUntilCompleted];
        unsigned wrong = differences(a, b);
        printf("metal: mismatches=%u\n", wrong);
        if (wrong) return 7;
        b[7] += 1.0f;
        if (differences(a, b) != 1) return 8;
        error = nil;
        id<MTLLibrary> invalid = [device newLibraryWithSource:
            @"kernel void bad(device float* out [[buffer(0)]]) { out[0] = missing_value; }"
            options:nil error:&error];
        if (invalid || !error) return 9;
        puts("metal: source compute, readback and negative controls OK");
        return 0;
    }
}
