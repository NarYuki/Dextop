"""Avoid zero-over-zero roots in the shared radial-gradient paint solver."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    path = parser.parse_args().source / 'src/external/cocotron/Onyx2D/O2Paint_radialGradient.m'
    text = path.read_text()
    old = '''    O2Float qD = hb * hb - a * c;
    O2Float d = (O2Float) sqrt(qD);
    *x1 = c / (-hb + d);
    *x2 = c / (-hb - d);'''
    new = '''    // Dextop: preserve the minus/plus root order without 0/0 at a cone tip.
    if (a == 0) {
        *x1 = *x2 = hb != 0 ? -c / (2 * hb) : (c == 0 ? 0 : NAN);
        return;
    }
    O2Float qD = hb * hb - a * c;
    O2Float d = (O2Float) sqrt(qD);
    O2Float q = -hb - (O2Float)copysign(d, hb);
    if (q == 0) {
        *x1 = *x2 = 0;
    } else if (hb >= 0) {
        *x1 = q / a;
        *x2 = c / q;
    } else {
        *x1 = c / q;
        *x2 = q / a;
    }'''
    if new not in text:
        if text.count(old) != 1: raise RuntimeError('Pinned radial root solver changed')
        text = text.replace(old, new)
    anchor = '''    result = O2PaintColorRamp(self, g, rho, skip);'''
    replacement = '''    // At a zero-radius cone tip the filter cannot sample negative radii.
    // Shrink its footprint to the valid domain, including a point sample at g=0.
    if (self->_startRadius == 0 && g >= 0 && g < rho * 0.5f)
        rho = 2 * g;
    result = O2PaintColorRamp(self, g, rho, skip);'''
    if replacement not in text:
        if text.count(anchor) != 1: raise RuntimeError('Pinned radial ramp call changed')
        text = text.replace(anchor, replacement)
    if path.read_text() != text: path.write_text(text)


if __name__ == '__main__':
    main()
