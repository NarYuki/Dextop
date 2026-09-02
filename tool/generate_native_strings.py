#!/usr/bin/env python3
"""Generate the Kotlin overlay localization adapter from Flutter ARB files."""

import argparse
import json
from pathlib import Path
from xml.sax.saxutils import escape


ROOT = Path(__file__).resolve().parents[1]
ARB_DIR = ROOT / "lib" / "l10n"
OUTPUT = (
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
LOCALES = {
    "ja": "app_ja.arb",
    "en": "app_en.arb",
    "zh": "app_zh.arb",
    "ko": "app_ko.arb",
    "ru": "app_ru.arb",
    "pt": "app_pt.arb",
    "pt_BR": "app_pt_BR.arb",
}
ANDROID_RESOURCE_DIRS = {
    "ja": "values",
    "en": "values-en",
    "zh": "values-zh",
    "ko": "values-ko",
    "ru": "values-ru",
    "pt": "values-pt",
    "pt_BR": "values-pt-rBR",
}
ANDROID_KEYS = {
    "androidSwipeImeLabel": "dextop_swipe_ime_label",
    "androidAccessibilityServiceDescription": "accessibility_service_description",
    "androidEmbeddedPairingChannel": "embedded_pairing_channel",
    "androidEmbeddedPairingTitle": "embedded_pairing_title",
    "androidEmbeddedPairingNotificationText": "embedded_pairing_notification_text",
    "androidEmbeddedPairingCode": "embedded_pairing_code",
    "androidEmbeddedPairingEnterCode": "embedded_pairing_enter_code",
    "androidEmbeddedPairingSuccess": "embedded_pairing_success",
    "androidEmbeddedPairingSuccessMessage": "embedded_pairing_success_message",
    "androidEmbeddedPairingFailed": "embedded_pairing_failed",
    "androidEmbeddedPairingInvalidCode": "embedded_pairing_invalid_code",
    "androidEmbeddedPairingSearching": "embedded_pairing_searching",
    "androidEmbeddedPairingServiceFound": "embedded_pairing_service_found",
    "androidEmbeddedPairingServiceNotFound": "embedded_pairing_service_not_found",
    "androidEmbeddedPairingRetry": "embedded_pairing_retry",
    "androidEmbeddedPairingInProgress": "embedded_pairing_in_progress",
}


def kotlin_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False).replace("$", r"\$")


def catalogs() -> dict[str, dict[str, str]]:
    return {
        locale: json.loads((ARB_DIR / filename).read_text())
        for locale, filename in LOCALES.items()
    }


def render_native(catalog: dict[str, dict[str, str]]) -> str:
    translations: dict[str, dict[str, str]] = {}
    for locale, arb in catalog.items():
        translations[locale] = {
            key: value
            for key, value in arb.items()
            if key.startswith("native") and not key.startswith("@")
        }

    reference = set(translations["ja"])
    for locale, values in translations.items():
        if set(values) != reference:
            missing = sorted(reference - set(values))
            extra = sorted(set(values) - reference)
            raise SystemExit(f"{locale}: native ARB mismatch missing={missing} extra={extra}")

    lines = [
        "// GENERATED FILE. Source: lib/l10n/app_*.arb",
        "package moe.n4tsu.dextop",
        "",
        "import java.util.Locale",
        "",
        "internal object NativeStrings {",
        "    fun text(key: String): String {",
        "        val locale = Locale.getDefault()",
        '        val regional = if (locale.country.isBlank()) locale.language else "${locale.language}_${locale.country}"',
        '        return ((values[regional] ?: values[locale.language] ?: values.getValue("en"))[key]',
        '            ?: values.getValue("en")[key]',
        '            ?: key).replace("\\\\n", "\\n")',
        "    }",
        "",
        "    private val values = mapOf(",
    ]
    for locale, values in translations.items():
        lines.append(f'        "{locale}" to mapOf(')
        for key in sorted(values):
            lines.append(f"            {kotlin_string(key)} to {kotlin_string(values[key])},")
        lines.append("        ),")
    lines.extend(["    )", "}", ""])
    return "\n".join(lines)


def render_android(arb: dict[str, str], default: bool) -> str:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    if default:
        lines.extend([
            '    <string name="app_name" translatable="false">Dextop</string>',
            '    <string name="quick_settings_tile_label" translatable="false">Dextop</string>',
        ])
    for arb_key, resource_key in ANDROID_KEYS.items():
        lines.append(f'    <string name="{resource_key}">{escape(arb[arb_key])}</string>')
    lines.extend(["</resources>", ""])
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    catalog = catalogs()
    generated = render_native(catalog)
    android_outputs = {
        ROOT / "android" / "app" / "src" / "main" / "res" / directory / "strings.xml":
            render_android(catalog[locale], locale == "ja")
        for locale, directory in ANDROID_RESOURCE_DIRS.items()
    }
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text() != generated:
            raise SystemExit("NativeStrings.kt is not synchronized with ARB files")
        for path, contents in android_outputs.items():
            if not path.exists() or path.read_text() != contents:
                raise SystemExit(f"{path} is not synchronized with ARB files")
        return
    OUTPUT.write_text(generated)
    for path, contents in android_outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents)


if __name__ == "__main__":
    main()
