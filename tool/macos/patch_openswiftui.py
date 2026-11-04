"""Apply source compatibility fixes to the pinned OpenSwiftUI checkout."""

import argparse
from pathlib import Path
import subprocess


REVISION = "e693ac10ce079351849f0a9d4df5381e63c88c0c"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    args = parser.parse_args()
    source = args.source.resolve()
    revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if revision != REVISION:
        parser.error("OpenSwiftUI revision differs from the reviewed source pin")
    path = source / "Sources/OpenSwiftUI_SPI/Overlay/CoreGraphics/OpenSwiftUICorePlatformImage.m"
    original = path.read_text()
    old = "[kitImage renderingMode]"
    new = "[(NSObject *)kitImage renderingMode]"
    # Disambiguate the declared UIImage category selector from NSFont's
    # renderingMode enum. This preserves dynamic dispatch and the return ABI.
    if original.count(old) == 2 and original.count(new) == 0:
        path.write_text(original.replace(old, new))
    elif original.count(old) != 0 or original.count(new) != 2:
        raise RuntimeError("Pinned platform image selector anchors changed")
    color = source / "Sources/OpenSwiftUI_SPI/Overlay/CoreGraphics/OpenSwiftUICoreColor.m"
    text = color.read_text()
    marker = "@interface NSObject (DextopOpenSwiftUIColorSelectors)"
    if marker not in text:
        anchor = "Class OpenSwiftUICoreColorClass(OpenSwiftUICoreSystem system);"
        if text.count(anchor) != 1:
            raise RuntimeError("Pinned platform color selector anchor changed")
        text = text.replace(anchor, """#if OPENSWIFTUI_TARGET_OS_OSX
// Declare the dynamic UIKit selector without requiring an iOS SDK on macOS.
@interface NSObject (DextopOpenSwiftUIColorSelectors)
- (instancetype)initWithRed:(CGFloat)red green:(CGFloat)green blue:(CGFloat)blue alpha:(CGFloat)alpha;
@end
#endif

""" + anchor)
        color.write_text(text)
    # Nullability qualifies the output pointer, not the scalar float pointee.
    header = source / "Sources/OpenSwiftUI_SPI/Shims/CoreGraphics/CoreGraphics_Private.h"
    content = header.read_text()
    old = "float cg_nullable *headroom"
    new = "float * cg_nullable headroom"
    if content.count(old) == 1:
        header.write_text(content.replace(old, new))
    elif content.count(new) != 1:
        raise RuntimeError("Pinned CoreGraphics headroom pointer anchor changed")
    # Darling exposes an incomplete UIKit header even in its macOS SDK. Header
    # presence alone must not enable iOS-only shims in an AppKit build.
    count = 0
    for file in sorted((source / "Sources").rglob("*")):
        if file.suffix not in (".h", ".m", ".mm"):
            continue
        content = file.read_text()
        guard = "#if __has_include(<UIKit/UIKit.h>)"
        corrected = guard + " && !OPENSWIFTUI_TARGET_OS_OSX"
        updated = content.replace(guard + "\n", corrected + "\n")
        if updated != content:
            file.write_text(updated)
            count += 1
    print(f"Restricted UIKit header-presence guards to non-AppKit targets: {count} files")
    print("Applied OpenSwiftUI platform image selector disambiguation")


if __name__ == "__main__":
    main()
