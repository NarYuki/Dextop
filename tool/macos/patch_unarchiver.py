"""Implement Foundation's top-level keyed archive decoding/error entry point."""

import argparse
from pathlib import Path


IMPLEMENTATION = '''+ (id)unarchiveTopLevelObjectWithData:(NSData *)data error:(NSError **)error
{
    if (error) *error = nil;
    NSKeyedUnarchiver *decoder = nil;
    id result = nil;
    @try {
        decoder = [[self alloc] initForReadingWithData:data];
        if (!decoder) {
            if (error) *error = [NSError errorWithDomain:NSCocoaErrorDomain code:4864
                userInfo:@{NSLocalizedDescriptionKey: @"The keyed archive could not be read."}];
        } else {
            // This legacy API accepts NSCoding; secure class allowlists belong
            // to unarchivedObjectOfClasses:fromData:error: instead.
            result = [[decoder decodeObjectForKey:NSKeyedArchiveRootObjectKey] retain];
            [decoder finishDecoding];
        }
    } @catch (NSException *exception) {
        [result release];
        result = nil;
        if (error) *error = [NSError errorWithDomain:NSCocoaErrorDomain code:4864
            userInfo:@{NSLocalizedDescriptionKey: [exception reason] ?: @"The keyed archive could not be decoded."}];
    } @finally {
        [decoder release];
    }
    return [result autorelease];
}

'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    source = parser.parse_args().source
    header = source / "src/external/foundation/include/Foundation/NSKeyedArchiver.h"
    text = header.read_text()
    legacy = "+ (id)unarchiveTopLevelObjectWithData:(NSData *)data error:(NSError **)error;"
    declaration = '''+ (id _Nullable)unarchiveTopLevelObjectWithData:(NSData * _Nonnull)data
    error:(NSError * _Nullable * _Nullable)error
    NS_SWIFT_NAME(__unarchiveTopLevelObjectWithData(_:))
    __attribute__((swift_error(nonnull_error)));'''
    if legacy in text:
        text = text.replace(legacy, declaration)
        header.write_text(text)
    if declaration not in text:
        anchor = "+ (id)unarchiveObjectWithData:(NSData *)data;"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned unarchiver declaration changed")
        header.write_text(text.replace(anchor, declaration + "\n" + anchor))
    implementation = source / "src/external/foundation/src/NSKeyedUnarchiver.m"
    text = implementation.read_text()
    # XML property lists can be valid plists without being valid keyed archives.
    # Guard borrowed values before CFDictionary access or CFRetain(NULL).
    marker = "            // Validate keyed-archive XML container types before retaining them."
    if marker not in text:
        anchor = '''            CFNumberRef version = CFDictionaryGetValue((CFDictionaryRef)plist, @"$version");'''
        replacement = '''            if (CFGetTypeID(plist) != CFDictionaryGetTypeID()) {
                CFRelease(plist);
                [self release];
                return nil;
            }
''' + anchor
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned XML archive version anchor changed")
        text = text.replace(anchor,replacement)
        anchor = '''            _objects = CFDictionaryGetValue((CFDictionaryRef)plist, @"$objects");
            CFRetain(_objects);

            if (top == NULL || _objects == NULL)
            {
                [self release];
                @throw [NSException exceptionWithName:NSInvalidUnarchiveOperationException reason:@"Improperly formatted archive" userInfo:nil];
                return nil;
            }'''
        replacement = marker + '''
            CFTypeRef objects = CFDictionaryGetValue((CFDictionaryRef)plist, @"$objects");
            if (top == NULL || CFGetTypeID((CFTypeRef)top) != CFDictionaryGetTypeID() ||
                objects == NULL || CFGetTypeID(objects) != CFArrayGetTypeID()) {
                CFRelease(plist);
                [self release];
                @throw [NSException exceptionWithName:NSInvalidUnarchiveOperationException reason:@"Improperly formatted archive" userInfo:nil];
                return nil;
            }
            _objects = CFRetain(objects);'''
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned XML archive containers anchor changed")
        text = text.replace(anchor,replacement)
    if IMPLEMENTATION not in text:
        anchor = "+ (id)unarchiveObjectWithData:(NSData *)data\n"
        if text.count(anchor) != 1 or "+ (id)unarchiveTopLevelObjectWithData:" in text:
            raise RuntimeError("Pinned unarchiver implementation changed")
        text = text.replace(anchor, IMPLEMENTATION + anchor)
    implementation.write_text(text)
    plist = source / "src/external/corefoundation/CFPropertyList.c"
    text = plist.read_text()
    marker = "        // Keyed archive UIDs have a dictionary representation in XML."
    if marker not in text:
        anchor = "    } else if (typeID == booltype) {\n        if (CFBooleanGetValue((CFBooleanRef)object)) {"
        replacement = '''    } else if (typeID == _CFKeyedArchiverUIDGetTypeID()) {
''' + marker + '''
        _plistAppendUTF8CString(xmlString, "<dict>\\n");
        _appendIndents(indentation + 1, xmlString);
        _plistAppendUTF8CString(xmlString, "<key>CF$UID</key>\\n");
        _appendIndents(indentation + 1, xmlString);
        _plistAppendFormat(xmlString, CFSTR("<integer>%u</integer>\\n"),
            (unsigned int)_CFKeyedArchiverUIDGetValue((CFKeyedArchiverUIDRef)object));
        _appendIndents(indentation, xmlString);
        _plistAppendUTF8CString(xmlString, "</dict>\\n");
''' + anchor
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned XML property list writer anchor changed")
        plist.write_text(text.replace(anchor,replacement))


if __name__ == "__main__":
    main()
