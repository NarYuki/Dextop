"""Check host ELF dependencies of our built wrap_elf bridges on the target Linux CPU.

Only reads bridge artifacts built from the pinned open-source runtime. This is
not an inspector for third-party applications or SDK binaries. Each dlopen runs
in a separate process, so an earlier load cannot hide a missing dependency.
"""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


def check(runtime):
    bridges = runtime / "libexec/darling/usr/lib/native"
    if not bridges.is_dir():
        raise RuntimeError(f"Native bridge directory missing: {bridges}")
    records = []
    for bridge in sorted(bridges.glob("lib*.dylib")):
        if bridge.is_symlink():
            continue
        names = sorted(set(re.findall(rb"lib[A-Za-z0-9_+.-]+\.so(?:\.[0-9]+)*", bridge.read_bytes())))
        for encoded in names:
            name = encoded.decode("ascii")
            private = runtime / "lib/dextop-native" / name
            selected = str(private) if private.is_file() else name
            result = subprocess.run(
                [sys.executable, "-c", "import ctypes, os, sys; ctypes.CDLL(sys.argv[1], mode=os.RTLD_NOW)", selected],
                capture_output=True, text=True, timeout=30,
            )
            records.append({"bridge": bridge.name, "library": name, "selected": selected,
                            "returncode": result.returncode, "error": result.stderr.strip()})
    if not records:
        raise RuntimeError("No host library names found in the built native bridges")
    return records


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if sys.platform != "linux":
        parser.error("Run on the Linux target CPU, inside the same environment as Darling")
    records = check(args.runtime.resolve())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")
    failures = [record for record in records if record["returncode"]]
    for record in failures:
        print(f"{record['bridge']}: {record['library']}: {record['error']}", file=sys.stderr)
    print(f"Native host dependencies: {len(records) - len(failures)}/{len(records)} load successfully.")
    return int(bool(failures))


if __name__ == "__main__":
    sys.exit(main())
