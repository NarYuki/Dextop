"""Compile focused SwiftUI prerequisite probes against our source-built overlays.

This diagnoses missing source APIs. Passing these checks does not establish
SwiftUI binary compatibility, rendering, input or application support.
"""

import argparse
import json
from pathlib import Path
import subprocess


PROBES = {
    "foundation-import": "import Foundation\nlet value: Date = Date(timeIntervalSince1970: 0)\n",
    "date-components-style": "import Foundation\nfunc accept(_ value: Date.ComponentsFormatStyle) {}\n",
    "duration-units-style": "import Foundation\nfunc accept(_ value: Duration.UnitsFormatStyle) {}\n",
    "adaptive-image-glyph": "import Foundation\nfunc accept(_ value: AttributedString.AdaptiveImageGlyph) {}\n",
    "nsstring-cstring": "import Foundation\nfunc convert(_ bytes: UnsafePointer<CChar>) -> NSString? { NSString(cString: bytes, encoding: String.Encoding.utf8.rawValue) }\n",
    "swiftui-import": 'import SwiftUI\nstruct DextopSwiftUIProbe: View { var body: some View { Text("Dextop") } }\n',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("swiftc", "sdk", "resource-dir", "modules", "headers", "modulemap", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--architecture", choices=("arm64", "x86_64"), required=True)
    args = parser.parse_args()
    for name in ("swiftc", "sdk", "resource_dir", "modules", "headers", "modulemap"):
        if not getattr(args, name).exists():
            parser.error(f"Missing {name}: {getattr(args, name)}")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # swiftc is a driver-mode symlink: preserve argv[0] when locating the tool.
    command = [str(args.swiftc.absolute()), "-typecheck", "-target", args.architecture + "-apple-macosx26.0",
               "-sdk", str(args.sdk.resolve()), "-resource-dir", str(args.resource_dir.resolve()),
               "-I", str(args.modules.resolve()), "-Xcc", "-I" + str(args.headers.resolve()),
               "-Xcc", "-fmodule-map-file=" + str(args.modulemap.resolve()),
               "-module-cache-path", str(output / "module-cache")]
    results = []
    for name, content in PROBES.items():
        source = output / (name + ".swift")
        source.write_text(content, encoding="utf-8")
        result = subprocess.run([*command, str(source)], capture_output=True, text=True, timeout=120)
        (output / (name + ".log")).write_text(result.stdout + result.stderr, encoding="utf-8")
        results.append({"name": name, "typecheckPassed": result.returncode == 0,
                        "returncode": result.returncode, "log": name + ".log"})
        print(f"{name}: {'PASS' if result.returncode == 0 else 'FAIL'}", flush=True)
        if name == "foundation-import" and result.returncode:
            break  # An unusable toolchain/overlay is not evidence of individual API gaps.
    version = subprocess.check_output([str(args.swiftc.absolute()), "--version"], text=True, timeout=30)
    report = {"architecture": args.architecture, "swiftVersion": version.strip(), "command": command, "checks": results,
              "scope": "source API prerequisites only", "swiftUICompatibilityVerified": False}
    (output / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if not results[0]["typecheckPassed"]:
        return 2
    return int(any(not result["typecheckPassed"] for result in results))


if __name__ == "__main__":
    raise SystemExit(main())
