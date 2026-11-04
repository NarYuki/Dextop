#import <Foundation/Foundation.h>
#import <EventKit/EKEventStore.h>
#include <stdio.h>

int main(void) {
    @autoreleasepool {
        EKEventStore *store = [[[EKEventStore alloc] init] autorelease];
        if ([EKEventStore authorizationStatusForEntityType:EKEntityTypeEvent] != EKAuthorizationStatusDenied) return 1;
        NSDate *start = [NSDate dateWithTimeIntervalSince1970:100];
        NSDate *end = [NSDate dateWithTimeIntervalSince1970:200];
        NSPredicate *predicate = [store predicateForEventsWithStartDate:start endDate:end calendars:nil];
        NSDictionary *event = @{ @"startDate": [NSDate dateWithTimeIntervalSince1970:90],
                                @"endDate": [NSDate dateWithTimeIntervalSince1970:110] };
        if (![predicate evaluateWithObject:event]) return 2;
        if ([predicate evaluateWithObject:@{ @"startDate":end, @"endDate":end }]) return 3;
        if ([[store eventsMatchingPredicate:predicate] count] != 0) return 4;
        __block BOOL called = NO;
        [store enumerateEventsMatchingPredicate:predicate usingBlock:^(id event, BOOL *stop) { called = YES; }];
        if (called) return 5;
        NSPredicate *empty = [store predicateForEventsWithStartDate:start endDate:end calendars:@[]];
        if ([empty evaluateWithObject:event]) return 6;
        BOOL invalidRange = NO, invalidPredicate = NO;
        @try { [store predicateForEventsWithStartDate:end endDate:start calendars:nil]; }
        @catch (NSException *error) { invalidRange = [[error name] isEqual:NSInvalidArgumentException]; }
        @try { [store eventsMatchingPredicate:[NSPredicate predicateWithValue:YES]]; }
        @catch (NSException *error) { invalidPredicate = [[error name] isEqual:NSInvalidArgumentException]; }
        if (!invalidRange || !invalidPredicate) return 7;
        puts("EventKit search with denied authorization PASS");
    }
    return 0;
}
