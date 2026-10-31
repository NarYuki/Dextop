import 'package:flutter/widgets.dart';

import 'inline_strings.g.dart';

/// Text written in place as a Japanese/English pair; other languages look the English up in
/// `lib/l10n/inline/<locale>.json` (tool/generate_inline_strings.py), and fall back to it.
String localText(BuildContext context, String ja, String en) =>
    inlineText(Localizations.localeOf(context), ja, en);

String inlineText(Locale locale, String ja, String en) {
  if (locale.languageCode == 'ja') return ja;
  final table = inlineStrings[locale.languageCode == 'pt' &&
          locale.countryCode == 'BR'
      ? 'pt_BR'
      : locale.languageCode];
  return table?[en] ?? en;
}

/// Fills {0}, {1}… of a text chosen by [localText] (interpolated text is looked up as a template).
String inlineFormat(String template, List<Object?> args) => template.replaceAllMapped(
  RegExp(r'\{(\d+)\}'),
  (m) {
    final i = int.parse(m[1]!);
    return i < args.length ? '${args[i]}' : m[0]!;
  },
);
