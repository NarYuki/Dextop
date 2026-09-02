import 'dart:ui';

import 'app_localizations.dart';

/// Resolves the generated ARB localization outside a widget build context.
///
/// UI code should prefer [AppLocalizations.of]. This accessor exists for
/// report generation and platform callbacks which do not own a BuildContext.
AppLocalizations currentLocalizations() {
  final locale = PlatformDispatcher.instance.locale;
  final supported = AppLocalizations.supportedLocales.firstWhere(
    (candidate) =>
        candidate.languageCode == locale.languageCode &&
        candidate.countryCode == locale.countryCode,
    orElse: () => AppLocalizations.supportedLocales.firstWhere(
      (candidate) => candidate.languageCode == locale.languageCode,
      orElse: () => const Locale('en'),
    ),
  );
  return lookupAppLocalizations(supported);
}
