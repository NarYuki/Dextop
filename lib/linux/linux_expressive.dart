import 'package:flutter/material.dart' as fm;
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart' as mui;

/// Hosts Material 3 Expressive widgets inside Dextop's app.
///
/// material_3_expressive builds on `material_ui`, a Material library separate
/// from the framework's `flutter/material.dart` the rest of Dextop uses. This
/// wraps the whole app (MaterialApp.builder) so the Linux pages and their
/// dialogs and sheets get a `material_ui` theme with Dextop's seed colour and
/// brightness, and an expressive theme derived from it.
class LinuxExpressive extends StatelessWidget {
  const LinuxExpressive({super.key, required this.child});

  final Widget child;

  /// The seed of Dextop's own theme (see `appTheme` in app_shell.dart).
  static const seed = Color(0xff6750a4);

  @override
  Widget build(BuildContext context) {
    final brightness = fm.Theme.of(context).brightness;
    final scheme = mui.ColorScheme.fromSeed(
      seedColor: seed,
      brightness: brightness,
      dynamicSchemeVariant: mui.DynamicSchemeVariant.tonalSpot,
    );
    final theme = mui.ThemeData(colorScheme: scheme, useMaterial3: true);
    // The status bar style every screen falls back to. Sheets and dialogs switch the
    // icons to light over their scrim; without a style below them the light icons stayed
    // on light screens after they closed.
    final overlay = brightness == Brightness.light
        ? SystemUiOverlayStyle.dark
        : SystemUiOverlayStyle.light;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: overlay.copyWith(
        statusBarColor: const Color(0x00000000),
        systemNavigationBarColor: const Color(0x00000000),
      ),
      child: mui.Theme(
        data: theme,
        child: M3ETheme(data: M3EThemeData.fromMaterial(theme), child: child),
      ),
    );
  }
}

/// Opens [page] as a route of the expressive Linux pages.
Route<T> linuxRoute<T>(Widget page) =>
    mui.MaterialPageRoute<T>(builder: (_) => page);

/// `material_ui` widgets look up their own MaterialLocalizations; Dextop's app
/// provides the framework's. This supplies `material_ui`'s defaults for every locale.
class _MaterialUiLocalizationsDelegate
    extends LocalizationsDelegate<mui.MaterialLocalizations> {
  const _MaterialUiLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) => true;

  @override
  Future<mui.MaterialLocalizations> load(Locale locale) =>
      mui.DefaultMaterialLocalizations.load(locale);

  @override
  bool shouldReload(_MaterialUiLocalizationsDelegate old) => false;
}

const LocalizationsDelegate<mui.MaterialLocalizations>
linuxMaterialUiLocalizations = _MaterialUiLocalizationsDelegate();

/// Japanese or English, like the rest of Dextop's untranslated new screens.
String linuxText(BuildContext context, String ja, String en) =>
    Localizations.localeOf(context).languageCode == 'ja' ? ja : en;
