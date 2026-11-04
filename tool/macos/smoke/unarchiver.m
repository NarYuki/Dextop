/* Own-source archive API regression: NSCoding graphs, nil roots and failures. */
#import <Foundation/Foundation.h>
#include <stdio.h>

static BOOL failDecoding;
@interface ArchiveProbeValue : NSObject <NSCoding> {
@public
    NSString *value;
}
@end
@implementation ArchiveProbeValue
- (void)encodeWithCoder:(NSCoder *)coder { [coder encodeObject:value forKey:@"value"]; }
- (id)initWithCoder:(NSCoder *)coder {
    self = [super init];
    if (failDecoding) {
        [self release];
        [NSException raise:NSInvalidUnarchiveOperationException format:@"Own probe decode failure"];
        return nil;
    }
    value = [[coder decodeObjectForKey:@"value"] retain];
    return self;
}
- (void)dealloc { [value release]; [super dealloc]; }
@end

int main(void) {
    @autoreleasepool {
        NSDictionary *expected = @{@"text":@"Dextop", @"values":@[@1,@2,@3]};
        NSData *data = [NSKeyedArchiver archivedDataWithRootObject:expected];
        NSError *error = nil;
        id decoded = [NSKeyedUnarchiver unarchiveTopLevelObjectWithData:data error:&error];
        if (![expected isEqual:decoded] || error) return 1;
        NSMutableData *xml = [NSMutableData data];
        NSKeyedArchiver *writer = [[NSKeyedArchiver alloc] initForWritingWithMutableData:xml];
        [writer setOutputFormat:NSPropertyListXMLFormat_v1_0];
        [writer encodeObject:expected forKey:NSKeyedArchiveRootObjectKey];
        [writer finishEncoding]; [writer release];
        decoded = [NSKeyedUnarchiver unarchiveTopLevelObjectWithData:xml error:&error];
        if (![expected isEqual:decoded] || error) {
            NSLog(@"XML archive result %@ error %@ source %@",decoded,error,
                [[[NSString alloc] initWithData:xml encoding:NSUTF8StringEncoding] autorelease]);
            return 7;
        }
        ArchiveProbeValue *custom = [[[ArchiveProbeValue alloc] init] autorelease];
        custom->value = [@"NSCoding without NSSecureCoding" retain];
        data = [NSKeyedArchiver archivedDataWithRootObject:custom];
        decoded = [NSKeyedUnarchiver unarchiveTopLevelObjectWithData:data error:&error];
        if (![decoded isKindOfClass:[ArchiveProbeValue class]] || error ||
            ![custom->value isEqual:((ArchiveProbeValue *)decoded)->value]) return 2;
        failDecoding = YES;
        decoded = [NSKeyedUnarchiver unarchiveTopLevelObjectWithData:data error:&error];
        if (decoded || ![[error domain] isEqual:NSCocoaErrorDomain] ||
            [error code] != 4864 || ![[error localizedDescription] isEqual:@"Own probe decode failure"]) return 3;
        if ([NSKeyedUnarchiver unarchiveTopLevelObjectWithData:data error:NULL]) return 4;
        failDecoding = NO;
        NSMutableArray *invalid = [NSMutableArray arrayWithObjects:[NSData data],
            [@"broken archive" dataUsingEncoding:NSUTF8StringEncoding],nil];
        for (id plist in @[@[@"not a keyed archive"],
                @{@"$version":@100000,@"$top":@{}},
                @{@"$version":@100000,@"$objects":@[],@"$top":@"wrong type"},
                @{@"$version":@100000,@"$objects":@"wrong type",@"$top":@{}}]) {
            [invalid addObject:[NSPropertyListSerialization dataWithPropertyList:plist
                format:NSPropertyListXMLFormat_v1_0 options:0 error:NULL]];
        }
        for (NSData *bytes in invalid) {
            error = nil;
            if ([NSKeyedUnarchiver unarchiveTopLevelObjectWithData:bytes error:&error] ||
                ![[error domain] isEqual:NSCocoaErrorDomain] || [error code] != 4864) return 5;
        }
        data = [NSKeyedArchiver archivedDataWithRootObject:nil];
        error = nil;
        if ([NSKeyedUnarchiver unarchiveTopLevelObjectWithData:data error:&error] || error) return 6;
        puts("unarchiver: object graphs, NSCoding, nil root and error propagation OK");
    }
    return 0;
}
