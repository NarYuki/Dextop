import 'dart:ui' show PlatformDispatcher;

import 'package:flutter/widgets.dart';

import 'inline_strings.g.dart';

/// Resolves a catalog key using the widget's locale, including regional variants.
String localText(BuildContext context, String key) =>
    inlineText(Localizations.localeOf(context), key);

/// For platform callbacks and data loading that have no widget context.
String currentUiText(String key) =>
    inlineText(PlatformDispatcher.instance.locale, key);

String inlineText(Locale locale, String key) {
  final table =
      inlineStrings[locale.languageCode == 'pt' && locale.countryCode == 'BR'
          ? 'pt_BR'
          : locale.languageCode];
  return table?[key] ?? inlineStrings['en']![key] ?? key;
}

/// Fills {0}, {1}… of a text chosen by [localText] (interpolated text is looked up as a template).
String inlineFormat(String template, List<Object?> args) =>
    template.replaceAllMapped(RegExp(r'\{(\d+)\}'), (m) {
      final i = int.parse(m[1]!);
      return i < args.length ? '${args[i]}' : m[0]!;
    });
