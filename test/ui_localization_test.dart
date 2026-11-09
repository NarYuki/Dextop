import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/l10n/inline_strings.g.dart';
import 'package:free_dextop/l10n/inline_text.dart';
import 'package:free_dextop/linux/linux_expressive.dart';

void main() {
  test(
    'expressive controls use localized material labels for all app locales',
    () async {
      for (final locale in [
        const Locale('ja'),
        const Locale('en'),
        const Locale('ko'),
        const Locale('zh'),
        const Locale('ru'),
        const Locale('pt'),
        const Locale('pt', 'BR'),
      ]) {
        expect(linuxMaterialUiLocalizations.isSupported(locale), isTrue);
        final strings = await linuxMaterialUiLocalizations.load(locale);
        expect(strings.cancelButtonLabel, isNotEmpty);
        if (locale.languageCode == 'ja') {
          expect(strings.cancelButtonLabel, 'キャンセル');
        } else if (locale.languageCode != 'en') {
          expect(strings.cancelButtonLabel.toLowerCase(), isNot('cancel'));
        }
      }
    },
  );
  test(
    'all supported UI catalogs have every key and preserve placeholders',
    () {
      final english = inlineStrings['en']!;
      final placeholders = RegExp(r'\{\d+\}');
      for (final locale in inlineStrings.entries) {
        expect(
          locale.value.keys.toSet(),
          english.keys.toSet(),
          reason: locale.key,
        );
        for (final entry in locale.value.entries) {
          expect(entry.value, isNotEmpty, reason: '${locale.key}/${entry.key}');
          expect(
            placeholders.allMatches(entry.value).map((m) => m[0]).toSet(),
            placeholders
                .allMatches(english[entry.key]!)
                .map((m) => m[0])
                .toSet(),
            reason: '${locale.key}/${entry.key}',
          );
        }
      }
    },
  );

  test('regional Portuguese and unsupported languages resolve correctly', () {
    expect(
      inlineText(const Locale('pt', 'BR'), 'uiDisplayScale'),
      'Escala da tela',
    );
    expect(
      inlineText(const Locale('pt', 'PT'), 'uiDisplayScale'),
      'Escala do ecrã',
    );
    expect(inlineText(const Locale('fr'), 'uiDisplayScale'), 'Display scale');
    expect(inlineText(const Locale('ja'), 'uiDisplayScale'), '表示倍率');
  });

  test('platform names keep their identity independently of window labels', () {
    expect(inlineText(const Locale('ja'), 'uiWindowsPlatform'), 'Windows');
    expect(inlineText(const Locale('ja'), 'uiWindows'), 'ウィンドウ');
  });

  test(
    'formatting happens after lookup and never reinterprets argument text',
    () {
      final text = inlineText(const Locale('en'), 'uiDextopCouldNotBeBound0');
      expect(
        inlineFormat(text, ['name {1} \$value']),
        'Dextop could not be bound: name {1} \$value',
      );
    },
  );
}
