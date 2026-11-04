"""Install the shared AppKit grid implementation and public declarations."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    base = parser.parse_args().source / "src/external/cocotron/AppKit"
    own = Path(__file__).resolve().parent / "runtime"
    for name in ("NSGridView.m", "NSGridModel.inc"):
        (base / name).write_bytes((own / name).read_bytes())
    (base / "include/AppKit/NSGridView.h").write_bytes((own / "NSGridView.h").read_bytes())
    path = base / "include/AppKit/AppKit.h"
    text = path.read_text()
    if "#import <AppKit/NSGridView.h>" not in text:
        path.write_text(text + "\n#import <AppKit/NSGridView.h>\n")


if __name__ == "__main__":
    main()
