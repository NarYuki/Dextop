#import <Foundation/Foundation.h>
#include <math.h>
#include <stdio.h>

@interface NSCalendar (DextopCalendarSearchProbe)
- (NSDate *)nextDateAfterDate:(NSDate *)date matchingComponents:(NSDateComponents *)components options:(NSUInteger)options;
@end

static int checkDay(NSCalendar *calendar, NSInteger year, NSInteger month, NSInteger day,
                    NSTimeInterval expectedLength) {
    NSDate *noon = [calendar dateWithEra:1 year:year month:month day:day
                                  hour:12 minute:34 second:56 nanosecond:0];
    if (!noon) return 1;
    NSDate *start = [calendar startOfDayForDate:noon];
    NSDateComponents *parts = [calendar components:NSCalendarUnitYear | NSCalendarUnitMonth |
        NSCalendarUnitDay | NSCalendarUnitHour | NSCalendarUnitMinute | NSCalendarUnitSecond fromDate:start];
    if (!start || [parts year] != year || [parts month] != month || [parts day] != day ||
        [parts hour] || [parts minute] || [parts second]) return 2;
    NSDate *rangeStart = nil;
    NSTimeInterval length = 0;
    if (![calendar rangeOfUnit:NSCalendarUnitDay startDate:&rangeStart interval:&length forDate:noon]) return 3;
    if (fabs(length - expectedLength) > 0.001 || ![start isEqualToDate:rangeStart]) return 4;
    return 0;
}

int main(void) {
    setbuf(stdout, NULL);
    @autoreleasepool {
        NSLocale *locale = [[[NSLocale alloc] initWithLocaleIdentifier:@"ja_JP"] autorelease];
        if (![[locale countryCode] isEqualToString:@"JP"]) return 1;
        NSCalendar *calendar = [NSCalendar calendarWithIdentifier:NSCalendarIdentifierGregorian];
        if (!calendar) return 2;
        [calendar setTimeZone:[NSTimeZone timeZoneWithName:@"America/Los_Angeles"]];
        if (checkDay(calendar, 2024, 2, 29, 86400)) return 3;
        if (checkDay(calendar, 2024, 3, 10, 82800)) return 4;
        if (checkDay(calendar, 2024, 11, 3, 90000)) return 5;
        NSCalendar *automatic = [NSCalendar autoupdatingCurrentCalendar];
        NSDate *sample = [automatic dateWithEra:1 year:2024 month:2 day:29
                                          hour:12 minute:0 second:0 nanosecond:0];
        if (!sample || ![automatic startOfDayForDate:sample]) return 6;
        NSDateComponents *matching = [[[NSDateComponents alloc] init] autorelease];
        [matching setSecond:0];
        NSDate *anchor = [calendar dateWithEra:1 year:2024 month:2 day:29
                                        hour:12 minute:34 second:56 nanosecond:0];
        NSDate *next = [calendar nextDateAfterDate:anchor matchingComponents:matching options:(1UL << 10)];
        NSDate *previous = [calendar nextDateAfterDate:anchor matchingComponents:matching options:(1UL << 10)|(1UL << 2)];
        if (!next || fabs([next timeIntervalSinceDate:anchor] - 4) > 0.001) return 7;
        if (!previous || fabs([anchor timeIntervalSinceDate:previous] - 56) > 0.001) return 8;
        NSDate *automaticNext = [automatic nextDateAfterDate:anchor matchingComponents:matching options:(1UL << 10)];
        if (!automaticNext || fabs([automaticNext timeIntervalSinceDate:anchor] - 4) > 0.001) return 9;
        NSMutableIndexSet *indexes = [NSMutableIndexSet indexSet];
        [indexes addIndex:100]; [indexes addIndex:110];
        printf("indexes pair count=%lu first=%lu last=%lu\n", (unsigned long)indexes.count, (unsigned long)indexes.firstIndex, (unsigned long)indexes.lastIndex);
        if (indexes.count != 2 || indexes.firstIndex != 100 || indexes.lastIndex != 110) return 10;
        [indexes addIndex:120];
        printf("indexes triple count=%lu first=%lu last=%lu\n", (unsigned long)indexes.count, (unsigned long)indexes.firstIndex, (unsigned long)indexes.lastIndex);
        if (indexes.count != 3 || indexes.firstIndex != 100 || indexes.lastIndex != 120) return 11;
        [indexes addIndex:105];
        if (indexes.count != 4 || indexes.firstIndex != 100 || indexes.lastIndex != 120) return 12;
        if (![indexes containsIndex:105] || [indexes containsIndex:106]) return 13;
        puts("calendar: sparse date indexes OK");
        NSMutableIndexSet *many = [NSMutableIndexSet indexSet];
        for (NSUInteger i = 0; i < 100; ++i) [many addIndex:1000 + i * 3];
        if (many.count != 100 || many.lastIndex != 1297) return 14;
        if (many.count != 100) return 15;
        [many removeIndex:1150];
        if (many.count != 99 || [many containsIndex:1150]) return 16;
        [many addIndex:1150];
        if (many.count != 100 || many.firstIndex != 1000 || many.lastIndex != 1297) return 17;
        puts("calendar: large sparse index cache mutation OK");
        puts("calendar: next and previous minute search OK");
        puts("calendar: locale, leap day, DST boundaries and automatic calendar OK");
        return 0;
    }
}
