"""Align existing Foundation accessors and synchronous callbacks with Swift."""
import argparse
from pathlib import Path


def once(text, old, new):
    if text.count(old) == 1: return text.replace(old, new)
    if old not in text and new in text: return text
    raise RuntimeError('Pinned Foundation import anchor changed: ' + old)


def patch_overlays(overlays):
    # The legacy overlay sources used the old method-shaped SDK declarations.
    # Rebuilds must use the same public property imports as their clients.
    changes = {
        'Codable.swift': [('number.doubleValue()', 'number.doubleValue'), ('number.longLongValue()', 'number.longLongValue')],
        'DateComponents.swift': [('$0.integerValue()', '$0.integerValue')],
        'Data.swift': [('@objc override var bytes: UnsafeRawPointer? {', '@objc override var bytes: UnsafeRawPointer {')],
    }
    for name, replacements in changes.items():
        path = overlays / 'Foundation' / name
        text = path.read_text()
        for old, new in replacements: text = once(text, old, new)
        if path.read_text() != text: path.write_text(text)
    numeric_clients = ('NSNumber.swift', 'JSONEncoder.swift', 'JSONSerialization+Swift.swift',
                       'URL.swift', 'PlistEncoder.swift', 'URLResourceValues.swift', 'NSObject.swift', 'Locale.swift')
    for name in numeric_clients:
        path = overlays / 'Foundation' / name
        text = path.read_text()
        for getter in ('doubleValue', 'longLongValue', 'integerValue', 'boolValue', 'floatValue', 'unsignedIntegerValue',
                       'charValue', 'unsignedCharValue', 'shortValue', 'unsignedShortValue', 'intValue',
                       'unsignedIntValue', 'longValue', 'unsignedLongValue', 'unsignedLongLongValue'):
            text = text.replace('.' + getter + '()', '.' + getter)
        if name == 'NSNumber.swift':
            text = text.replace('longLongValue()', 'longLongValue').replace('doubleValue()', 'doubleValue')
        if path.read_text() != text: path.write_text(text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    source = parser.parse_args().source
    headers = source / 'src/external/foundation/include/Foundation'
    edits = {
        'NSFileManager.h': [('NSFileManager *defaultManager;', 'NSFileManager * _Nonnull defaultManager;')],
        'NSUserDefaults.h': [
            ('NSUserDefaults *standardUserDefaults;', 'NSUserDefaults * _Nonnull standardUserDefaults;'),
            ('- (void)setObject:(id)value forKey:(NSString *)key;', '- (void)setObject:(id _Nullable)value forKey:(NSString * _Nonnull)key NS_SWIFT_NAME(set(_:forKey:));')],
        'NSData.h': [('const void *bytes NS_RETURNS_INNER_POINTER;', 'const void * _Nonnull bytes NS_RETURNS_INNER_POINTER;')],
        'NSAttributedString.h': [
            ('usingBlock:(void (^)(NSDictionary<NSAttributedStringKey, id>', 'usingBlock:(void (NS_NOESCAPE ^)(NSDictionary<NSAttributedStringKey, id>'),
            ('usingBlock:(void (^)(id value, NSRange range, BOOL * _Nonnull stop))block;', 'usingBlock:(void (NS_NOESCAPE ^)(id value, NSRange range, BOOL * _Nonnull stop))block;')],
    }
    for name, replacements in edits.items():
        path = headers / name
        text = path.read_text()
        for old, new in replacements: text = once(text, old, new)
        if path.read_text() != text: path.write_text(text)
    patch_overlays(source / 'src/external/swift/overlays')


if __name__ == '__main__':
    main()
