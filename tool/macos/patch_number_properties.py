"""Expose existing NSNumber/formatter accessors as their public Swift properties."""
import argparse
from pathlib import Path
import re


def replace(text, old, new):
    if new in text:
        return text
    if text.count(old) != 1:
        raise RuntimeError('Pinned number property anchor changed: ' + old)
    return text.replace(old, new)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    headers = parser.parse_args().source / 'src/external/foundation/include/Foundation'
    path = headers / 'NSValue.h'
    text = path.read_text()
    # No selectors or implementation are changed; Clang's importer needs the
    # property declaration instead of treating a zero-argument getter as a method.
    for name in ('doubleValue', 'floatValue', 'boolValue', 'integerValue', 'unsignedIntegerValue', 'charValue', 'unsignedCharValue', 'shortValue', 'unsignedShortValue', 'intValue', 'unsignedIntValue', 'longValue', 'unsignedLongValue', 'longLongValue', 'unsignedLongLongValue'):
        found = re.findall(r'^- \(([^)]+)\)' + name + r';$', text, re.M)
        if found:
            text = replace(text, '- (' + found[0] + ')' + name + ';', '@property(readonly) ' + found[0] + ' ' + name + ';')
        elif not re.search(r'@property\(readonly\) .+ ' + name + ';', text):
            raise RuntimeError('Missing NSNumber getter: ' + name)
    text = replace(text, '- (NSString *)stringValue;', '@property(readonly) NSString * _Nonnull stringValue;')
    marker = '@interface NSNumber (NSNumberCreation)'
    if text.count(marker) != 1:
        raise RuntimeError('Pinned NSNumber creation category changed')
    before, creation = text.split(marker)
    creation = creation.replace('- (id)initWith', '- (id _Nonnull)initWith')
    text = before + marker + creation
    if path.read_text() != text: path.write_text(text)
    path = headers / 'NSNumberFormatter.h'
    text = path.read_text()
    for name in ('minimumIntegerDigits', 'maximumIntegerDigits', 'minimumFractionDigits', 'maximumFractionDigits', 'minimumSignificantDigits', 'maximumSignificantDigits'):
        setter = 'set' + name[0].upper() + name[1:]
        old = '- (NSUInteger)' + name + ';\n- (void)' + setter + ':(NSUInteger)number;'
        text = text.replace('@property NSInteger ' + name + ';', '@property NSUInteger ' + name + ';')
        text = replace(text, old, '@property NSUInteger ' + name + ';')
    if path.read_text() != text: path.write_text(text)


if __name__ == '__main__':
    main()
