"""Implement EventKit's unavailable-store behavior without granting calendar access.

This exposes notification/authorization APIs and reports an unavailable backend
honestly. It does not implement calendar storage or Android calendar integration.
"""

import argparse
import json
from pathlib import Path
import subprocess


DECLARATIONS = """
typedef NS_ENUM(NSInteger, EKEntityType) { EKEntityTypeEvent = 0, EKEntityTypeReminder = 1 };
typedef NS_ENUM(NSInteger, EKAuthorizationStatus) {
    EKAuthorizationStatusNotDetermined = 0, EKAuthorizationStatusRestricted = 1,
    EKAuthorizationStatusDenied = 2, EKAuthorizationStatusAuthorized = 3,
    EKAuthorizationStatusFullAccess = 3, EKAuthorizationStatusWriteOnly = 4
};
typedef void (^EKEventStoreRequestAccessCompletionHandler)(BOOL granted, NSError *error);
FOUNDATION_EXPORT NSString *const EKEventStoreChangedNotification;

@interface EKEventStore : NSObject
+ (EKAuthorizationStatus)authorizationStatusForEntityType:(EKEntityType)entityType;
- (void)requestAccessToEntityType:(EKEntityType)entityType completion:(EKEventStoreRequestAccessCompletionHandler)completion;
- (void)requestFullAccessToEventsWithCompletion:(EKEventStoreRequestAccessCompletionHandler)completion;
- (void)requestWriteOnlyAccessToEventsWithCompletion:(EKEventStoreRequestAccessCompletionHandler)completion;
- (NSArray *)sources;
- (NSArray *)calendarsForEntityType:(EKEntityType)entityType;
- (NSPredicate *)predicateForEventsWithStartDate:(NSDate *)startDate endDate:(NSDate *)endDate calendars:(NSArray *)calendars;
- (NSArray *)eventsMatchingPredicate:(NSPredicate *)predicate;
- (void)enumerateEventsMatchingPredicate:(NSPredicate *)predicate usingBlock:(void (^)(id event, BOOL *stop))block;
- (void)reset;
@end
"""

IMPLEMENTATION = """
#import <dispatch/dispatch.h>

NSString *const EKEventStoreChangedNotification = @"EKEventStoreChangedNotification";

@interface DextopEventRangePredicate : NSPredicate {
@public
    EKEventStore *owner;
    NSDate *start, *end;
    NSArray *calendars;
}
@end

@implementation DextopEventRangePredicate
- (void)dealloc {
    [owner release]; [start release]; [end release]; [calendars release];
    [super dealloc];
}
- (id)copyWithZone:(NSZone *)zone { return [self retain]; }
- (BOOL)evaluateWithObject:(id)object {
    NSDate *eventStart = [object valueForKey:@"startDate"];
    NSDate *eventEnd = [object valueForKey:@"endDate"];
    if (![eventStart isKindOfClass:[NSDate class]] || ![eventEnd isKindOfClass:[NSDate class]]) return NO;
    if ([eventStart compare:end] != NSOrderedAscending || [eventEnd compare:start] == NSOrderedAscending) return NO;
    return calendars == nil || [calendars containsObject:[object valueForKey:@"calendar"]];
}
- (BOOL)evaluateWithObject:(id)object substitutionVariables:(NSDictionary *)variables {
    return [self evaluateWithObject:object];
}
@end

@implementation EKEventStore
+ (EKAuthorizationStatus)authorizationStatusForEntityType:(EKEntityType)entityType {
    return EKAuthorizationStatusDenied;
}
- (void)requestAccessToEntityType:(EKEntityType)entityType completion:(EKEventStoreRequestAccessCompletionHandler)completion {
    if (!completion) return;
    dispatch_async(dispatch_get_global_queue(0, 0), ^{
        NSError *error = [NSError errorWithDomain:@"DextopMacOSCompatibilityErrorDomain" code:1
            userInfo:@{NSLocalizedDescriptionKey: @"No calendar access backend is configured."}];
        completion(NO, error);
    });
}
- (void)requestFullAccessToEventsWithCompletion:(EKEventStoreRequestAccessCompletionHandler)completion {
    [self requestAccessToEntityType:EKEntityTypeEvent completion:completion];
}
- (void)requestWriteOnlyAccessToEventsWithCompletion:(EKEventStoreRequestAccessCompletionHandler)completion {
    [self requestAccessToEntityType:EKEntityTypeEvent completion:completion];
}
- (NSArray *)sources { return [NSArray array]; }
- (NSArray *)calendarsForEntityType:(EKEntityType)entityType { return [NSArray array]; }
- (NSPredicate *)predicateForEventsWithStartDate:(NSDate *)startDate endDate:(NSDate *)endDate calendars:(NSArray *)selected {
    if (getenv("DEXTOP_DARLING_EVENTKIT_TRACE"))
        NSLog(@"Dextop event search: start=%@ end=%@ calendarsClass=%@", startDate, endDate, NSStringFromClass([selected class]));
    if (![startDate isKindOfClass:[NSDate class]] || ![endDate isKindOfClass:[NSDate class]] ||
        [startDate compare:endDate] != NSOrderedAscending ||
        (selected && ![selected isKindOfClass:[NSArray class]]))
        [NSException raise:NSInvalidArgumentException format:@"An event search requires a valid date range and calendars array."];
    DextopEventRangePredicate *predicate = [[[DextopEventRangePredicate alloc] init] autorelease];
    predicate->owner = [self retain];
    predicate->start = [startDate copy]; predicate->end = [endDate copy];
    predicate->calendars = [selected copy];
    return predicate;
}
- (NSArray *)eventsMatchingPredicate:(NSPredicate *)predicate {
    if (![predicate isKindOfClass:[DextopEventRangePredicate class]] ||
        ((DextopEventRangePredicate *)predicate)->owner != self)
        [NSException raise:NSInvalidArgumentException format:@"Use a search predicate created by this event store."];
    // No storage backend is configured and authorization remains denied.
    return [NSArray array];
}
- (void)enumerateEventsMatchingPredicate:(NSPredicate *)predicate usingBlock:(void (^)(id event, BOOL *stop))block {
    BOOL stop = NO;
    for (id event in [self eventsMatchingPredicate:predicate]) {
        if (block) block(event, &stop);
        if (stop) break;
    }
}
- (void)reset {}
@end
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    source = args.source.resolve()
    lock = json.loads((Path(__file__).resolve().parent / "darling.lock.json").read_text())
    revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if revision != lock["commit"]:
        parser.error("Source HEAD must match darling.lock.json")
    base = source / "src/frameworks/EventKit"
    for path, anchor, replacement in (
        (base / "include/EventKit/EKEventStore.h", "#include <Foundation/Foundation.h>", DECLARATIONS),
        (base / "src/EKEventStore.m", "#import <EventKit/EKEventStore.h>", IMPLEMENTATION),
    ):
        text = path.read_text()
        if text.count(anchor) != 1:
            raise RuntimeError(f"Pinned EventKit source anchor changed: {path}")
        updated = text[:text.index(anchor) + len(anchor)] + "\n\n" + replacement
        if text != updated:
            path.write_text(updated)
    print("EventKit unavailable-store authorization and notification APIs applied")


if __name__ == "__main__":
    main()
