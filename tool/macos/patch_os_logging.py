"""Add functional Logger messages to the pinned Darling Swift os overlay."""
import argparse
from pathlib import Path

HERE = Path(__file__).resolve().parent
MARKER = '// Dextop Logger message implementation\n'


def patch(path):
    text = path.read_text().split(MARKER)[0]
    if text.count('public struct Logger {') != 1:
        raise RuntimeError('Pinned Logger source anchor changed')
    updated = text.rstrip() + '\n\n' + MARKER + (HERE / 'runtime/OSLogger.swift').read_text()
    if path.read_text() != updated:
        path.write_text(updated)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    patch(parser.parse_args().source / 'src/external/swift/overlays/os/os.swift')


if __name__ == '__main__':
    main()
