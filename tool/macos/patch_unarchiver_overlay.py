"""Restore the public Swift spelling of keyed top-level unarchiving."""
import argparse
from pathlib import Path

HERE = Path(__file__).resolve().parent


def patch(overlays):
    target = overlays / 'Foundation/KeyedUnarchiverOverlay.swift'
    content = (HERE / 'runtime/KeyedUnarchiverOverlay.swift').read_bytes()
    if not target.exists() or target.read_bytes() != content:
        target.write_bytes(content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    patch(parser.parse_args().source / 'src/external/swift/overlays')


if __name__ == '__main__':
    main()
