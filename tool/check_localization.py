#!/usr/bin/env python3
"""Check every shipped UI catalog, source call site and Android application.

Protocol identifiers, keyboard characters, third-party application metadata and
raw diagnostic logs are data; they must not be translated as interface text.
"""

import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import generate_inline_strings
import inline_strings
import localization_literals

ROOT = Path(__file__).resolve().parents[1]
LOCALES = ('ja', 'en', 'zh', 'ko', 'ru', 'pt', 'pt_BR')


def fail(message):
    raise SystemExit(f'localization check failed: {message}')


def catalog(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                fail(f'{path.relative_to(ROOT)}: duplicate key {key}')
            result[key] = value
        return result
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)


arb = {locale: catalog(ROOT / f'lib/l10n/app_{locale}.arb') for locale in LOCALES}
keys = {key for key in arb['ja'] if not key.startswith('@')}
for locale, table in arb.items():
    if {key for key in table if not key.startswith('@')} != keys:
        fail(f'{locale}: ARB keys do not match Japanese')
    if any(not isinstance(table[key], str) or not table[key] for key in keys):
        fail(f'{locale}: empty ARB translation')
generated = (ROOT / 'lib/l10n/app_localizations.dart').read_text(encoding='utf-8')
members = set(re.findall(r'String (?:get )?([A-Za-z0-9_]+)(?:;|\()', generated))
if missing := keys - members:
    fail(f'generated AppLocalizations is stale: {sorted(missing)}')
subprocess.run([sys.executable, str(ROOT / 'tool/generate_native_strings.py'), '--check'], check=True)
if not generate_inline_strings.check():
    fail('shared catalog keys, placeholders or generated outputs are inconsistent')
ui = {locale: catalog(ROOT / f'lib/l10n/inline/{locale}.json') for locale in LOCALES}
ui_keys = set(ui['en'])

native_keys = {key for key in keys if key.startswith('native')}
paths = [p for p in (ROOT / 'lib').rglob('*.dart') if 'l10n' not in p.parts and p.name != 'firebase_options.dart']
for module in ('app', 'cardex', 'cardexdriving', 'linuxshortcut'):
    paths += list((ROOT / f'android/{module}/src/main').rglob('*.kt'))

# These patterns check display sinks, including native bare `text =`, Compose,
# accessibility descriptions, notifications and ordinary dialog buttons.
kotlin_sink = re.compile(
    r'(?:\bText\(\s*|\bcontentDescription\s*=\s*|\.text\s*=\s*|'
    r'^\s*(?:this\.)?text\s*=\s*|'
    r'\.(?:setText|setTitle|setMessage|setContentTitle|setContentText|setPositiveButton|setNegativeButton|setNeutralButton)\(\s*|'
    r'\b(?:plasmaLabel|appletHeader|makeText)\(\s*[\w.]+\s*,\s*)\Z', re.M)
dart_sink = re.compile(r'(?:\b(?:Text|SelectableText)\(\s*|\b(?:label|tooltip|hintText|semanticLabel|title|subtitle|headline|supportingText|helperText|errorText|dialogTitle|barrierLabel)\s*:\s*)\Z')
fixed = []
for path in paths:
    if path.name == 'NativeStrings.kt':
        continue
    src = path.read_text(encoding='utf-8')
    kotlin = path.suffix == '.kt'
    helper = (r'(?<![A-Za-z0-9_.])(?:trf?|t|ct|localized|message|phase|localText)\(\s*"([^"\n]+)"'
              if kotlin else
              r'(?<![A-Za-z0-9_.])(?:tr|t|currentUiText)\(\s*[\'\"]([^\'\"\n]+)[\'\"]|'
              r'\b(?:localText|linuxText|LinuxBackupsUi\.text)\(\s*[A-Za-z_][A-Za-z0-9_.]*\s*,\s*[\'\"]([^\'\"\n]+)[\'\"]')
    for match in re.finditer(helper, src):
        key = next(value for value in match.groups() if value is not None)
        if key not in ui_keys and key not in native_keys:
            fail(f'{path.relative_to(ROOT)}: text helper requires a known catalog key: {key}')
    for key in re.findall(r'[\'"](ui[A-Z][A-Za-z0-9]*)[\'"]', src):
        if key == 'uiModeType':  # Android diagnostic field, not a translation key.
            continue
        if key not in ui_keys:
            fail(f'{path.relative_to(ROOT)}: unknown UI key {key}')
    for key in re.findall(r'NativeStrings\.text\("([^"]+)"\)', src):
        if key not in native_keys:
            fail(f'{path.relative_to(ROOT)}: unknown native key {key}')
    if list(inline_strings.calls(src, kotlin)):
        fail(f'{path.relative_to(ROOT)}: embedded bilingual text; use a catalog key')
    for start, _, value, _ in localization_literals.failures(src, kotlin):
        if value in ui_keys or value in native_keys or not re.search(r'[A-Za-z\u3040-\u9fff]', value):
            continue
        fail(f'{path.relative_to(ROOT)}:{src.count(chr(10), 0, start) + 1}: fixed failure text: {value!r}')
    sink = kotlin_sink if kotlin else dart_sink
    # Parse literals rather than discarding every string containing `$`: a
    # template such as "Starting $name" still contains unlocalized prose.
    for match in re.finditer(r'"' if kotlin else r'[\'\"]', src):
        start = match.start()
        before = src[max(0, start - 250):start]
        if not sink.search(before):
            continue
        parsed = (inline_strings.kotlin_literal if kotlin else inline_strings.dart_literal)(src, start)
        if parsed is None:
            continue
        _, parts = parsed
        literal = inline_strings.unescape(''.join(value for kind, value in parts if kind == 't'), kotlin)
        # Unit symbols, numbers and punctuation are language independent.
        prose = re.sub(r'\b(?:dpi|ID|FPS|Hz|MiB|GiB|KiB|MB|GB|ms)\b', '', literal)
        if re.search(r'[A-Za-z\u3040-\u9fff]', prose):
            fixed.append(f'{path.relative_to(ROOT)}:{src.count(chr(10), 0, start) + 1}: {literal!r}')
if fixed:
    fail('fixed display strings:\n' + '\n'.join(fixed))

namespace = '{http://schemas.android.com/apk/res/android}'
attributes = ('label', 'text', 'hint', 'contentDescription', 'title', 'summary', 'description')
resource_count = 0
for module in ('app', 'cardex', 'cardexdriving', 'linuxshortcut'):
    source = ROOT / f'android/{module}/src/main'
    for path in source.rglob('*.xml'):
        for element in ET.parse(path).iter():
            for attribute in attributes:
                value = element.attrib.get(namespace + attribute)
                if module == 'linuxshortcut' and path.name == 'AndroidManifest.xml' and attribute == 'label' and value == 'LXSTUB_LABEL_PLACEHOLDER_0000000000':
                    continue  # Binary manifest slot replaced with the Linux application's own name.
                if value and not value.startswith(('@', '?')):
                    fail(f'{path.relative_to(ROOT)}: fixed {attribute}: {value!r}')
    default = source / 'res/values/strings.xml'
    if not default.exists():
        continue  # Driving shares Parking's resource source set.
    reference = {item.attrib['name'] for item in ET.parse(default).getroot().findall('string')
                 if item.attrib.get('translatable') != 'false'}
    resource_count += len(reference)
    directories = {'ja': 'values' if module == 'app' else 'values-ja',
                   'en': 'values-en' if module == 'app' else 'values',
                   'zh': 'values-zh', 'ko': 'values-ko', 'ru': 'values-ru',
                   'pt': 'values-pt', 'pt_BR': 'values-pt-rBR'}
    for locale, directory in directories.items():
        path = source / f'res/{directory}/strings.xml'
        if not path.exists():
            fail(f'{module}: missing {locale} Android resource catalog')
        names = {item.attrib['name'] for item in ET.parse(path).getroot().findall('string')
                 if item.attrib.get('translatable') != 'false'}
        if names != reference:
            fail(f'{module}/{locale}: resource keys differ from default')

terminal = (ROOT / 'android/app/src/main/assets/terminal/index.html').read_text(encoding='utf-8')
for key in re.findall(r'data-i18n="([^"]+)"|Dextop\.translate\(\'([^\']+)\'\)', terminal):
    if (key[0] or key[1]) not in ui_keys:
        fail(f'terminal: unknown UI key {key}')
if re.search(r'<button\b[^>]*>[^<]*[A-Za-z][^<]*</button>', terminal):
    fail('terminal: fixed button label')
print(f'localization check passed: {len(keys)} ARB keys, {len(ui_keys)} shared UI keys, '
      f'{resource_count} Android resource keys, {len(LOCALES)} locales')
