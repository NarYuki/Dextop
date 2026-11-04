"""Replace the empty gradient layer with a shared rendering implementation."""
import argparse
from pathlib import Path

HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    base = parser.parse_args().source / 'src/external/cocotron/QuartzCore'
    for name, target in [('CAGradientLayer.h', base / 'include/QuartzCore/CAGradientLayer.h'), ('CAGradientLayer.m', base / 'CAGradientLayer.m')]:
        content = (HERE / 'runtime' / name).read_bytes()
        if target.read_bytes() != content: target.write_bytes(content)


if __name__ == '__main__':
    main()
