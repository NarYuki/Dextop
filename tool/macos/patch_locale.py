"""Expose NSLocale countryCode through its existing ICU-backed locale values."""

import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    args = parser.parse_args()
    base = args.source / "src/external/foundation"
    path = base / "src/NSLocale.m"
    text = path.read_text()
    method = "- (NSString *)countryCode {\n    return [self objectForKey: NSLocaleCountryCode];\n}\n\n"
    if method not in text:
        anchor = "- (NSString *)languageIdentifier {"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned NSLocale implementation anchor changed")
        path.write_text(text.replace(anchor, method + anchor))
    header = args.source / "framework-include/Foundation/NSLocale.h"
    text = header.read_text()
    if "- (NSString *)countryCode;" not in text:
        anchor = "- (NSString *)languageCode;"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned NSLocale header anchor changed")
        header.write_text(text.replace(anchor, anchor + "\n- (NSString *)countryCode;"))


if __name__ == "__main__":
    main()
