"""Fill source SDK declaration gaps needed by the OpenSwiftUI AppKit build.

Declarations alone do not implement the corresponding accessibility behavior.
"""

import argparse
import json
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    args = parser.parse_args()
    source = args.source.resolve()
    lock = json.loads((Path(__file__).resolve().parent / "darling.lock.json").read_text())
    revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if revision != lock["commit"]:
        parser.error("Source HEAD must match darling.lock.json")
    path = source / "framework-include/AppKit/NSAccessibilityConstants.h"
    text = path.read_text()
    declaration = "typedef NSString *NSAccessibilityAttributeName;"
    if declaration not in text:
        anchor = "typedef NSString *NSAccessibilityActionName;"
        if text.count(anchor) != 1 or "NSAccessibilityAttributeName" in text:
            raise RuntimeError("Pinned AppKit accessibility type anchor changed")
        path.write_text(text.replace(anchor, anchor + "\n" + declaration))
    print("AppKit accessibility attribute-name declaration available")
    path = source / "src/external/foundation/include/Foundation/NSProcessInfo.h"
    text = path.read_text()
    old = "@property (readonly, copy) NSDictionary<NSString *, NSString *> *environment;"
    new = "@property (readonly, copy, nonnull) NSDictionary<NSString *, NSString *> *environment;"
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError("Pinned process environment declaration changed")
        path.write_text(text.replace(old,new))
    path = source / "src/external/cocotron/QuartzCore/include/QuartzCore/QuartzCore.h"
    text = path.read_text()
    old = """#import <CoreGraphics/CoreGraphics.h>
#import <QuartzCore/CoreAnimation.h>
#import <QuartzCore/CoreImage.h>
#import <QuartzCore/CoreVideo.h>
"""
    new = """#import <CoreGraphics/CoreGraphics.h>
#import <QuartzCore/CABase.h>
#import <QuartzCore/CoreVideo.h>
#ifdef __OBJC__
#import <QuartzCore/CoreAnimation.h>
#import <QuartzCore/CoreImage.h>
#endif
"""
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError("Pinned QuartzCore umbrella header changed")
        path.write_text(text.replace(old,new))


if __name__ == "__main__":
    main()
