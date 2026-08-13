#!/usr/bin/env python3
"""Fail when a locale or a translated call site is missing a key."""

from collections import Counter
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
LOCALES = ("ja", "en", "zh", "ko", "ru")


def fail(message: str) -> None:
    print(f"localization check failed: {message}", file=sys.stderr)
    raise SystemExit(1)


arb_keys: dict[str, set[str]] = {}
for locale in LOCALES:
    path = ROOT / "lib" / "l10n" / f"app_{locale}.arb"
    arb_keys[locale] = {
        key for key in json.loads(path.read_text()).keys() if not key.startswith("@")
    }

reference = arb_keys["ja"]
for locale, keys in arb_keys.items():
    if keys != reference:
        fail(
            f"app_{locale}.arb: missing={sorted(reference - keys)}, "
            f"extra={sorted(keys - reference)}"
        )

dart_source = "\n".join(
    path.read_text()
    for path in (ROOT / "lib").glob("*.dart")
    if path.name != "app_strings.dart"
)
dart_calls = set(re.findall(r"AppStrings\.tr\(['\"]([^'\"]+)['\"]\)", dart_source))
if missing := dart_calls - reference:
    fail(f"Dart references unknown keys: {sorted(missing)}")

dart_display_literals = re.findall(
    r"(?:Text|SelectableText)\(\s*(?:const\s+)?['\"]([^'\"]+)['\"]"
    r"|(?:label|tooltip|hintText|semanticLabel)\s*:\s*['\"]([^'\"]+)['\"]",
    dart_source,
)
if literals := [
    next(value for value in match if value)
    for match in dart_display_literals
    if "$" not in next(value for value in match if value)
]:
    fail(f"Dart contains fixed display strings: {sorted(set(literals))}")

native_path = (
    ROOT
    / "android"
    / "app"
    / "src"
    / "main"
    / "kotlin"
    / "moe"
    / "n4tsu"
    / "dextop"
    / "NativeStrings.kt"
)
native_source = native_path.read_text()
native_key_counts = Counter(re.findall(r'"(native[A-Za-z0-9]+)"\s+to\s+', native_source))
bad_counts = {key: count for key, count in native_key_counts.items() if count != len(LOCALES)}
if bad_counts:
    fail(f"native locale key counts are not {len(LOCALES)}: {bad_counts}")

kotlin_dir = native_path.parent
kotlin_source = "\n".join(
    path.read_text() for path in kotlin_dir.glob("*.kt") if path != native_path
)
native_calls = set(re.findall(r'NativeStrings\.text\("([^"]+)"\)', kotlin_source))
if missing := native_calls - native_key_counts.keys():
    fail(f"Kotlin references unknown keys: {sorted(missing)}")

native_display_literals = re.findall(
    r'(?:\.text\s*=|setText\(|contentDescription\s*=|setTitle\(|(?<![.A-Za-z])error\()\s*"([^"]+)"',
    kotlin_source,
)
if native_display_literals:
    fail(f"Kotlin contains fixed display/error strings: {sorted(set(native_display_literals))}")

android_namespace = "{http://schemas.android.com/apk/res/android}"
for path in (ROOT / "android" / "app" / "src" / "main").rglob("*.xml"):
    tree = ET.parse(path)
    for element in tree.iter():
        for attribute in ("label", "text", "hint", "contentDescription", "title", "summary", "description"):
            value = element.attrib.get(android_namespace + attribute)
            if value and not value.startswith(("@", "?")):
                fail(f"Android XML fixed display string in {path}: {attribute}={value!r}")

resource_keys: dict[str, set[str]] = {}
for locale, directory in {
    "ja": "values",
    "en": "values-en",
    "zh": "values-zh",
    "ko": "values-ko",
    "ru": "values-ru",
}.items():
    path = ROOT / "android" / "app" / "src" / "main" / "res" / directory / "strings.xml"
    resource_keys[locale] = {
        item.attrib["name"]
        for item in ET.parse(path).getroot().findall("string")
        if item.attrib.get("translatable") != "false"
    }
resource_reference = resource_keys["ja"]
for locale, keys in resource_keys.items():
    if keys != resource_reference:
        fail(
            f"Android resources {locale}: missing={sorted(resource_reference - keys)}, "
            f"extra={sorted(keys - resource_reference)}"
        )

print(
    f"localization check passed: {len(reference)} Flutter keys, "
    f"{len(native_key_counts)} native keys, {len(resource_reference)} Android resource keys, "
    f"{len(LOCALES)} locales"
)
