"""Provide NSCalendar's convenience factory using its existing calendar initializer."""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    path = args.source / "src/external/foundation/src/NSCalendar.m"
    text = path.read_text()
    method = "+ (NSCalendar *)calendarWithIdentifier:(NSCalendarIdentifier)identifier\n{\n    return [[[self alloc] initWithCalendarIdentifier:identifier] autorelease];\n}\n"
    if method not in text:
        anchor = "@implementation NSCalendar (NSCalendar)"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned calendar implementation anchor changed")
        path.write_text(text.replace(anchor, anchor + "\n\n" + method))
    text = path.read_text()
    date_method = """- (NSDate *)dateWithEra:(NSInteger)era year:(NSInteger)year month:(NSInteger)month
                   day:(NSInteger)day hour:(NSInteger)hour minute:(NSInteger)minute
                second:(NSInteger)second nanosecond:(NSInteger)nanosecond
{
    NSDateComponents *components = [[NSDateComponents alloc] init];
    [components setEra:era];
    [components setYear:year];
    [components setMonth:month];
    [components setDay:day];
    [components setHour:hour];
    [components setMinute:minute];
    [components setSecond:second];
    [components setNanosecond:nanosecond];
    NSDate *date = [self dateFromComponents:components];
    [components release];
    return date;
}
"""
    if date_method not in text:
        anchor = "@implementation NSCalendar (NSCalendar)"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned calendar implementation anchor changed")
        path.write_text(text.replace(anchor, anchor + "\n\n" + date_method))
    header = args.source / "framework-include/Foundation/NSCalendar.h"
    text = header.read_text()
    declaration = "+ (NSCalendar *)calendarWithIdentifier:(NSCalendarIdentifier)identifier;"
    if declaration not in text:
        anchor = "+ (id)currentCalendar;"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned calendar header anchor changed")
        header.write_text(text.replace(anchor, declaration + "\n" + anchor))
    text = header.read_text()
    declaration = "- (NSDate *)dateWithEra:(NSInteger)era year:(NSInteger)year month:(NSInteger)month day:(NSInteger)day hour:(NSInteger)hour minute:(NSInteger)minute second:(NSInteger)second nanosecond:(NSInteger)nanosecond;"
    if declaration not in text:
        anchor = "- (NSDate *)dateFromComponents:(NSDateComponents *)comps;"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned calendar date declaration anchor changed")
        header.write_text(text.replace(anchor, anchor + "\n" + declaration))
    text = path.read_text()
    method = """- (NSDate *)startOfDayForDate:(NSDate *)date
{
    NSDate *start = nil;
    if (![self rangeOfUnit:NSCalendarUnitDay startDate:&start interval:NULL forDate:date])
        return nil;
    return start;
}
"""
    if method not in text:
        anchor = "@implementation NSCalendar (NSCalendar)"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned calendar implementation anchor changed")
        path.write_text(text.replace(anchor, anchor + "\n\n" + method))
    text = header.read_text()
    declaration = "- (NSDate *)startOfDayForDate:(NSDate *)date;"
    if declaration not in text:
        anchor = "- (NSDate *)dateFromComponents:(NSDateComponents *)comps;"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned calendar declaration anchor changed")
        header.write_text(text.replace(anchor, anchor + "\n" + declaration))


if __name__ == "__main__":
    main()
