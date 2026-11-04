"""Retain explicit leap-month state in NSDateComponents and its copies."""
import argparse
from pathlib import Path
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',type=Path,required=True)
args=parser.parse_args()
p=args.source/'src/external/corefoundation/NSDateComponents.m'
s=p.read_text()
if 'BOOL dextopLeapMonthSet;' not in s:
    a='    NSTimeZone *timeZone;'
    assert s.count(a)==1
    s=s.replace(a,a+'\n    BOOL dextopLeapMonth;\n    BOOL dextopLeapMonthSet;')
    a='    copy.timeZone = self.timeZone;'
    s=s.replace(a,a+'\n    if ([self _dextopLeapMonthIsSet]) [copy setLeapMonth:[self isLeapMonth]];')
    a='@end'
    assert s.count(a)==1
    s=s.replace(a,'''- (BOOL)isLeapMonth {
    NSDateComponentParts *parts = (NSDateComponentParts *)object_getIndexedIvars(self);
    return parts->dextopLeapMonth;
}
- (void)setLeapMonth:(BOOL)value {
    NSDateComponentParts *parts = (NSDateComponentParts *)object_getIndexedIvars(self);
    parts->dextopLeapMonth = value;
    parts->dextopLeapMonthSet = YES;
}
- (BOOL)_dextopLeapMonthIsSet {
    NSDateComponentParts *parts = (NSDateComponentParts *)object_getIndexedIvars(self);
    return parts->dextopLeapMonthSet;
}
@end''')
    p.write_text(s)
