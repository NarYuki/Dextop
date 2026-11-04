"""Adapt the pinned public Swift calendar enumerator to Darling's component API.

The generated implementation retains the upstream Apache 2.0 / Swift Runtime
Library Exception copyright notice. No Apple binary implementation is used.
"""
import argparse
import hashlib
from pathlib import Path
import re

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',type=Path,required=True)
args=parser.parse_args()
base=args.source/'src/external/corefoundation'
up=base/'submodules/swift-corelibs-foundation/CoreFoundation/Locale.subproj'
out=args.source/'src/external/foundation/src'
units=['Era','Year','Month','Day','Hour','Minute','Second','Weekday','WeekdayOrdinal','Quarter','WeekOfMonth','WeekOfYear','YearForWeekOfYear','Nanosecond']
header='''#import <Foundation/Foundation.h>
#import <CoreFoundation/CoreFoundation.h>
#import <limits.h>
#import <math.h>
typedef NSMutableDictionary *CFDateComponentsRef;
#define CFDateComponentUndefined NSIntegerMax
#define CF_CROSS_PLATFORM_EXPORT
#define CF_PRIVATE static
#define kCFCalendarUnitNanosecond (1UL << 15)
#define kCFCalendarUnitLeapMonth (1UL << 30)
#define kCFCalendarIdentifierChinese kCFChineseCalendar
#define kCFCalendarIdentifierGregorian kCFGregorianCalendar
#define kCFCalendarIdentifierHebrew kCFHebrewCalendar
#define kCFCalendarIdentifierIndian kCFIndianCalendar
#define kCFCalendarIdentifierPersian kCFPersianCalendar
static void DextopReleaseDeferred(const void *pointer) { CFTypeRef value=*(CFTypeRef const *)pointer; if(value) CFRelease(value); }
#define _CFReleaseDeferred __attribute__((cleanup(DextopReleaseDeferred)))
#define kCFCalendarMatchStrictly (1ULL << 1)
#define kCFCalendarSearchBackwards (1ULL << 2)
#define kCFCalendarMatchPreviousTimePreservingSmallerUnits (1ULL << 8)
#define kCFCalendarMatchNextTimePreservingSmallerUnits (1ULL << 9)
#define kCFCalendarMatchNextTime (1ULL << 10)
#define kCFCalendarMatchFirst (1ULL << 12)
#define kCFCalendarMatchLast (1ULL << 13)
static CFDateComponentsRef CFDateComponentsCreate(CFAllocatorRef a) { return [[NSMutableDictionary alloc] init]; }
static CFDateComponentsRef CFDateComponentsCreateCopy(CFAllocatorRef a, CFDateComponentsRef c) { return [c mutableCopy]; }
static CFIndex CFDateComponentsGetValue(CFDateComponentsRef c, CFCalendarUnit u) { NSNumber *v=[c objectForKey:@(u)]; return v ? [v integerValue] : CFDateComponentUndefined; }
static void CFDateComponentsSetValue(CFDateComponentsRef c, CFCalendarUnit u, CFIndex v) { if(v==CFDateComponentUndefined) [c removeObjectForKey:@(u)]; else [c setObject:@(v) forKey:@(u)]; }
static CFCalendarRef CFDateComponentsCopyCalendar(CFDateComponentsRef c) { return (CFCalendarRef)[[c objectForKey:@"calendar"] retain]; }
static void CFDateComponentsSetCalendar(CFDateComponentsRef c, CFCalendarRef v) { if(v) [c setObject:(id)v forKey:@"calendar"]; else [c removeObjectForKey:@"calendar"]; }
static CFTimeZoneRef CFDateComponentsCopyTimeZone(CFDateComponentsRef c) { return (CFTimeZoneRef)[[c objectForKey:@"timeZone"] retain]; }
static void CFDateComponentsSetTimeZone(CFDateComponentsRef c, CFTimeZoneRef v) { if(v) [c setObject:(id)v forKey:@"timeZone"]; else [c removeObjectForKey:@"timeZone"]; }
static Boolean CFDateComponentsIsLeapMonthSet(CFDateComponentsRef c) { return [c objectForKey:@"leap"] != nil; }
static Boolean CFDateComponentsIsLeapMonth(CFDateComponentsRef c) { return [[c objectForKey:@"leap"] boolValue]; }
@interface NSDateComponents (DextopEnumerationPrivate)
- (NSInteger)nanosecond;
- (void)setNanosecond:(NSInteger)value;
- (BOOL)_dextopLeapMonthIsSet;
@end
static NSDateComponents *DextopComponents(CFDateComponentsRef c) {
    NSDateComponents *result=[[[NSDateComponents alloc] init] autorelease];
'''
for unit in units:
    header+=f'    if(CFDateComponentsGetValue(c,kCFCalendarUnit{unit})!=CFDateComponentUndefined) [result set{unit}:CFDateComponentsGetValue(c,kCFCalendarUnit{unit})];\n'
header+='''    if(CFDateComponentsIsLeapMonthSet(c)) [result setLeapMonth:CFDateComponentsIsLeapMonth(c)];
    [result setCalendar:[c objectForKey:@"calendar"]];
    [result setTimeZone:[c objectForKey:@"timeZone"]];
    return result;
}
static CFDateComponentsRef DextopCopyComponents(NSDateComponents *c, CFCalendarUnit units) {
    CFDateComponentsRef result=CFDateComponentsCreate(NULL);
'''
for unit in units:
    getter=unit[0].lower()+unit[1:]
    header+=f'    if(units & kCFCalendarUnit{unit}) {{ NSInteger value=[c {getter}]; if(value!=INT_MAX && value!=NSIntegerMax) CFDateComponentsSetValue(result,kCFCalendarUnit{unit},value); }}\n'
header+='''    return result;
}
static CFDateRef CFCalendarCreateDateFromComponents(CFAllocatorRef a, CFCalendarRef c, CFDateComponentsRef p) { return (CFDateRef)[[(NSCalendar *)c dateFromComponents:DextopComponents(p)] retain]; }
static CFIndex CFCalendarGetComponentFromDate(CFCalendarRef c, CFCalendarUnit u, CFDateRef d) { return [(NSCalendar *)c component:(NSCalendarUnit)u fromDate:(NSDate *)d]; }
static CFDateComponentsRef CFCalendarCreateDateComponentsFromDate(CFAllocatorRef a, CFCalendarRef c, CFCalendarUnit u, CFDateRef d) { return DextopCopyComponents([(NSCalendar *)c components:(NSCalendarUnit)u fromDate:(NSDate *)d],u); }
static CFDateRef _CFCalendarCreateDateByAddingDateComponentsToDate(CFAllocatorRef a, CFCalendarRef c, CFDateComponentsRef p, CFDateRef d, CFOptionFlags o) { return (CFDateRef)[[(NSCalendar *)c dateByAddingComponents:DextopComponents(p) toDate:(NSDate *)d options:o] retain]; }
static CFDateRef _CFCalendarCreateDateByAddingValueOfUnitToDate(CFCalendarRef c, CFIndex v, CFCalendarUnit u, CFDateRef d) {
    CFDateComponentsRef p=CFDateComponentsCreate(NULL); CFDateComponentsSetValue(p,u,v);
    CFDateRef result=_CFCalendarCreateDateByAddingDateComponentsToDate(NULL,c,p,d,0); [p release]; return result;
}
static Boolean _CFCalendarGetTimeRangeOfUnitForDate(CFCalendarRef c, CFCalendarUnit u, CFDateRef *start, CFTimeInterval *interval, CFDateRef d) {
    NSDate *s=nil; NSTimeInterval length=0;
    BOOL ok=[(NSCalendar *)c rangeOfUnit:(NSCalendarUnit)u startDate:&s interval:&length forDate:(NSDate *)d];
    if(start) *start=(CFDateRef)[s retain]; if(interval) *interval=length; return ok;
}
'''
upstream=up/'CFCalendar_Enumerate.c'
if hashlib.sha256(upstream.read_bytes()).hexdigest() != 'd9088e32550f7ef91b95c6254d2408c0d2ac56d3b468dc2d20135340f438b042':
    raise RuntimeError('Public Swift calendar enumeration source changed; review required')
text=upstream.read_text()
text='\n'.join(line for line in text.splitlines() if not line.startswith('#include'))
wrapper='''
@implementation NSCalendar (DextopDateEnumeration)
- (NSDate *)nextDateAfterDate:(NSDate *)date matchingComponents:(NSDateComponents *)components options:(NSUInteger)options {
    CFDateComponentsRef matching=DextopCopyComponents(components,
        NSCalendarUnitEra|NSCalendarUnitYear|NSCalendarUnitMonth|NSCalendarUnitDay|
        NSCalendarUnitHour|NSCalendarUnitMinute|NSCalendarUnitSecond|NSCalendarUnitWeekday|
        NSCalendarUnitWeekdayOrdinal|NSCalendarUnitQuarter|NSCalendarUnitWeekOfMonth|
        NSCalendarUnitWeekOfYear|NSCalendarUnitYearForWeekOfYear|NSCalendarUnitNanosecond);
    CFDateComponentsSetCalendar(matching,(CFCalendarRef)[components calendar]);
    CFDateComponentsSetTimeZone(matching,(CFTimeZoneRef)[components timeZone]);
    if([components _dextopLeapMonthIsSet]) [matching setObject:@([components isLeapMonth]) forKey:@"leap"];
    __block NSDate *result=nil;
    NSCalendar *snapshot=[[NSCalendar alloc] initWithCalendarIdentifier:[self calendarIdentifier]];
    [snapshot setLocale:[self locale]];
    [snapshot setTimeZone:[self timeZone]];
    [snapshot setFirstWeekday:[self firstWeekday]];
    [snapshot setMinimumDaysInFirstWeek:[self minimumDaysInFirstWeek]];
    _CFCalendarEnumerateDates((CFCalendarRef)snapshot,(CFDateRef)date,matching,options,
        ^(CFDateRef found, Boolean exact, Boolean *stop) {
            result=[(NSDate *)found retain]; *stop=true;
        });
    [snapshot release];
    [matching release];
    return [result autorelease];
}
@end
'''
generated=out/'DarlingCalendarEnumeration.m'
content=header+'\n'+text+'\n'+wrapper
if not generated.exists() or generated.read_text()!=content: generated.write_text(content)
calendar=out/'NSCalendar.m'
text=calendar.read_text()
include='#include "DarlingCalendarEnumeration.m"'
text=re.sub(r'^#include "[^"\n]*/DarlingCalendarEnumeration\.m"\s*$',include,text,flags=re.MULTILINE)
if include not in text: text+='\n'+include+'\n'
if calendar.read_text()!=text: calendar.write_text(text)
print('Installed source-relative public calendar enumerator')

