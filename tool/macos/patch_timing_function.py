"""Repair the public timing-function float ABI and renderer's Bezier inversion."""
import argparse
from pathlib import Path

HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    base = parser.parse_args().source / 'src/external/cocotron/QuartzCore'
    for name, target in [('CAMediaTimingFunction.h', base / 'include/QuartzCore/CAMediaTimingFunction.h'), ('CAMediaTimingFunction.m', base / 'CAMediaTimingFunction.m')]:
        content = (HERE / 'runtime' / name).read_bytes()
        if target.read_bytes() != content: target.write_bytes(content)
    renderer = base / 'CARenderer.m'
    text = renderer.read_text()
    signature = 'static CGFloat applyMediaTimingFunction(CAMediaTimingFunction *function,'
    end = '\nstatic CGFloat mediaTimingScale('
    start = text.index(signature)
    stop = text.index(end, start)
    replacement = '''static CGFloat applyMediaTimingFunction(CAMediaTimingFunction *function,
                                        CGFloat t)
{
    return function ? [function _dextopValueForProgress:t] : t;
}
'''
    declaration = '''@interface CAMediaTimingFunction (DextopTimingEvaluation)
- (double)_dextopValueForProgress:(double)progress;
@end

'''
    text = text[:start] + replacement + text[stop:]
    text = text.replace(declaration, '')
    anchor = '@implementation CARenderer'
    if text.count(anchor) != 1: raise RuntimeError('Pinned CARenderer implementation changed')
    text = text.replace(anchor, declaration + anchor)
    if renderer.read_text() != text: renderer.write_text(text)


if __name__ == '__main__':
    main()
