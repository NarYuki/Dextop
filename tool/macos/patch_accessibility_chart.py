"""Install shared chart data models into Darling's existing libAccessibility."""
import argparse
from pathlib import Path

HERE = Path(__file__).resolve().parent


def update(path, data):
    if not path.exists() or path.read_bytes() != data:
        path.write_bytes(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    base = parser.parse_args().source / 'src/libaccessibility'
    for name, dest in [('AXChartData.h', 'include/Accessibility'), ('AXChartData.m', 'src')]:
        update(base / dest / name, (HERE / 'runtime' / name).read_bytes())
    header = base / 'include/Accessibility/Accessibility.h'
    text = header.read_text()
    include = '#import <Accessibility/AXChartData.h>'
    if include not in text:
        anchor = '#import <Foundation/Foundation.h>'
        if text.count(anchor) != 1:
            raise RuntimeError('Pinned Accessibility umbrella changed')
        update(header, text.replace(anchor, anchor + '\n' + include).encode())
    cmake = base / 'CMakeLists.txt'
    text = cmake.read_text()
    if 'src/AXChartData.m' not in text:
        anchor = '\tsrc/Accessibility.m'
        if text.count(anchor) != 1:
            raise RuntimeError('Pinned Accessibility source list changed')
        update(cmake, text.replace(anchor, anchor + '\n\tsrc/AXChartData.m').encode())


if __name__ == '__main__':
    main()
