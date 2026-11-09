#!/usr/bin/env python3
"""Check the shipped Java catalogs, including the resources Driving shares.

Compilation cannot catch a missing Java resource source set: the UI would show
keys at runtime. Compare every packaged locale with its generated catalog.
"""

import argparse
import zipfile
from pathlib import Path

import generate_inline_strings as catalogs


def check(apk, module, generated):
    prefix = 'moe/n4tsu/dextop/l10n/'
    expected = {f'{prefix}inline_{locale}.properties' for locale in catalogs.LOCALES}
    with zipfile.ZipFile(apk) as archive:
        actual = {name for name in archive.namelist() if name.startswith(prefix)}
        if actual != expected:
            raise SystemExit(f'{apk}: missing={sorted(expected - actual)}, unexpected={sorted(actual - expected)}')
        for name in expected:
            source = catalogs.ROOT / f'android/{module}/src/main/resources' / name
            if archive.read(name) != generated[source].encode('utf-8'):
                raise SystemExit(f'{apk}: packaged translation is stale: {name}')
        if module == 'app':
            guest = 'moe/n4tsu/dextop/linux/dextop-ui.json'
            source = catalogs.ROOT / 'android/app/src/main/resources' / guest
            if archive.read(guest) != generated[source].encode('utf-8'):
                raise SystemExit(f'{apk}: guest translations are missing or stale')
    print(f'{apk}: all {len(expected)} locale catalogs packaged and current')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', type=Path, nargs='*', default=[])
    parser.add_argument('--companion', type=Path, nargs='*', default=[])
    args = parser.parse_args()
    if not args.app and not args.companion:
        parser.error('specify at least one APK')
    generated = catalogs.outputs(catalogs.load())
    for apk in args.app:
        check(apk, 'app', generated)
    for apk in args.companion:
        check(apk, 'cardex', generated)


if __name__ == '__main__':
    main()
