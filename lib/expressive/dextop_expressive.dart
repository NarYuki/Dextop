import 'package:flutter/material.dart' as fm;
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart' as mui;

import '../l10n/inline_text.dart';

/// Soft Material 3 Expressive theme host for the entire Dextop application.
///
/// Uses the Android OS Material You dynamic colour scheme when available
/// (falling back to `DynamicSchemeVariant.tonalSpot`), generous 24–28dp rounded
/// surfaces, `surfaceContainerLow` resting cards/lists, and expressive motion.
class DextopExpressive extends StatelessWidget {
  const DextopExpressive({super.key, required this.child});

  final Widget child;

  /// Seed colour for Dextop's theme: system Material You accent when known,
  /// soft Iris purple fallback otherwise.
  static Color seed = const Color(0xff6750a4);

  /// Full OS Material You dynamic schemes reported by Android 12+.
  static Map<String, int>? systemLightPalette;
  static Map<String, int>? systemDarkPalette;

  static void applyLaunchContext(Map<String, dynamic> context) {
    final accent = context['accentColor'];
    if (accent is int) seed = Color(accent & 0xFFFFFFFF);
    systemLightPalette = _readPalette(context['dynamicLight']);
    systemDarkPalette = _readPalette(context['dynamicDark']);
  }

  static Map<String, int>? _readPalette(Object? raw) {
    if (raw is! Map) return null;
    final parsed = <String, int>{};
    raw.forEach((key, value) {
      if (key is String && value is int) {
        parsed[key] = value & 0xFFFFFFFF;
      }
    });
    return parsed.isEmpty ? null : parsed;
  }

  static mui.ColorScheme buildMuiScheme(Brightness brightness) {
    final base = mui.ColorScheme.fromSeed(
      seedColor: seed,
      brightness: brightness,
      dynamicSchemeVariant: mui.DynamicSchemeVariant.tonalSpot,
    );
    final raw = brightness == Brightness.dark
        ? systemDarkPalette
        : systemLightPalette;
    if (raw == null) return base;
    Color pick(String key, Color fallback) =>
        raw.containsKey(key) ? Color(raw[key]!) : fallback;
    return base.copyWith(
      primary: pick('primary', base.primary),
      onPrimary: pick('onPrimary', base.onPrimary),
      primaryContainer: pick('primaryContainer', base.primaryContainer),
      onPrimaryContainer: pick('onPrimaryContainer', base.onPrimaryContainer),
      primaryFixed: pick('primaryFixed', base.primaryFixed),
      primaryFixedDim: pick('primaryFixedDim', base.primaryFixedDim),
      onPrimaryFixed: pick('onPrimaryFixed', base.onPrimaryFixed),
      onPrimaryFixedVariant: pick(
        'onPrimaryFixedVariant',
        base.onPrimaryFixedVariant,
      ),
      secondary: pick('secondary', base.secondary),
      onSecondary: pick('onSecondary', base.onSecondary),
      secondaryContainer: pick('secondaryContainer', base.secondaryContainer),
      onSecondaryContainer: pick(
        'onSecondaryContainer',
        base.onSecondaryContainer,
      ),
      secondaryFixed: pick('secondaryFixed', base.secondaryFixed),
      secondaryFixedDim: pick('secondaryFixedDim', base.secondaryFixedDim),
      onSecondaryFixed: pick('onSecondaryFixed', base.onSecondaryFixed),
      onSecondaryFixedVariant: pick(
        'onSecondaryFixedVariant',
        base.onSecondaryFixedVariant,
      ),
      tertiary: pick('tertiary', base.tertiary),
      onTertiary: pick('onTertiary', base.onTertiary),
      tertiaryContainer: pick('tertiaryContainer', base.tertiaryContainer),
      onTertiaryContainer: pick(
        'onTertiaryContainer',
        base.onTertiaryContainer,
      ),
      tertiaryFixed: pick('tertiaryFixed', base.tertiaryFixed),
      tertiaryFixedDim: pick('tertiaryFixedDim', base.tertiaryFixedDim),
      onTertiaryFixed: pick('onTertiaryFixed', base.onTertiaryFixed),
      onTertiaryFixedVariant: pick(
        'onTertiaryFixedVariant',
        base.onTertiaryFixedVariant,
      ),
      error: pick('error', base.error),
      onError: pick('onError', base.onError),
      errorContainer: pick('errorContainer', base.errorContainer),
      onErrorContainer: pick('onErrorContainer', base.onErrorContainer),
      surface: pick('surface', base.surface),
      onSurface: pick('onSurface', base.onSurface),
      onSurfaceVariant: pick('onSurfaceVariant', base.onSurfaceVariant),
      surfaceDim: pick('surfaceDim', base.surfaceDim),
      surfaceBright: pick('surfaceBright', base.surfaceBright),
      surfaceContainerLowest: pick(
        'surfaceContainerLowest',
        base.surfaceContainerLowest,
      ),
      surfaceContainerLow: pick(
        'surfaceContainerLow',
        base.surfaceContainerLow,
      ),
      surfaceContainer: pick('surfaceContainer', base.surfaceContainer),
      surfaceContainerHigh: pick(
        'surfaceContainerHigh',
        base.surfaceContainerHigh,
      ),
      surfaceContainerHighest: pick(
        'surfaceContainerHighest',
        base.surfaceContainerHighest,
      ),
      inverseSurface: pick('inverseSurface', base.inverseSurface),
      onInverseSurface: pick('onInverseSurface', base.onInverseSurface),
      inversePrimary: pick('inversePrimary', base.inversePrimary),
      surfaceTint: pick('surfaceTint', base.surfaceTint),
      outline: pick('outline', base.outline),
      outlineVariant: pick('outlineVariant', base.outlineVariant),
      scrim: pick('scrim', base.scrim),
    );
  }

  static mui.ThemeData buildMuiTheme(Brightness brightness) {
    final scheme = buildMuiScheme(brightness);
    return mui.ThemeData(
      colorScheme: scheme,
      useMaterial3: true,
      scaffoldBackgroundColor: scheme.surface,
      textTheme: const mui.TextTheme(
        displaySmall: TextStyle(
          fontSize: 34,
          height: 1.15,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.5,
        ),
        headlineMedium: TextStyle(
          fontSize: 27,
          height: 1.22,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.25,
        ),
        headlineSmall: TextStyle(
          fontSize: 22,
          height: 1.26,
          fontWeight: FontWeight.w600,
        ),
        titleLarge: TextStyle(
          fontSize: 20,
          height: 1.28,
          fontWeight: FontWeight.w600,
        ),
        titleMedium: TextStyle(
          fontSize: 16,
          height: 1.35,
          fontWeight: FontWeight.w600,
        ),
        bodyLarge: TextStyle(fontSize: 15.5, height: 1.48),
        bodyMedium: TextStyle(fontSize: 14, height: 1.45),
        labelLarge: TextStyle(
          fontSize: 13.5,
          height: 1.38,
          fontWeight: FontWeight.w600,
        ),
      ),
      cardTheme: mui.CardThemeData(
        elevation: 0,
        color: scheme.surfaceContainerLow,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(26)),
      ),
    );
  }

  static M3EThemeData buildM3ETheme(mui.ThemeData theme) {
    final base = M3EThemeData.fromMaterial(theme);
    final scheme = base.colorScheme;
    return base.copyWith(
      cardTheme: base.cardTheme.copyWith(
        radius: 26,
        contentPadding: const EdgeInsets.all(20),
        elevatedContainerColor: scheme.surfaceContainerLow,
        filledContainerColor: scheme.surfaceContainerLow,
        outlinedContainerColor: scheme.surface,
        elevatedElevation: M3EElevation.level1,
        filledElevation: M3EElevation.level0,
      ),
      listTheme: base.listTheme.copyWith(
        cardList: const M3EListCardListTheme(
          outerRadius: 24,
          innerRadius: 8,
          gap: 3,
          variant: M3ECardVariant.filled,
        ),
        item: base.listTheme.item.copyWith(
          selectedRadius: 20,
          horizontalPadding: 18,
          verticalPadding: 12,
          twoLineHeight: 74,
          iconSize: 22,
          containerColor: scheme.surfaceContainerLow,
          selectedContainerColor: scheme.secondaryContainer,
          disabledStateOpacity: 0,
        ),
      ),
      navigationBarTheme: base.navigationBarTheme.copyWith(
        heightSmall: 68,
        heightMedium: 76,
      ),
      navigationRailTheme: base.navigationRailTheme.copyWith(
        containerColor: scheme.surfaceContainerLow,
        scrolledContainerColor: scheme.surfaceContainer,
        expandedIndicatorRadius: 28,
      ),
      dialogTheme: base.dialogTheme.copyWith(
        cornerRadius: 28,
      ),
      bottomSheetTheme: base.bottomSheetTheme.copyWith(
        topCornerRadius: 28,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final brightness = mui.Theme.of(context).brightness;
    final theme = buildMuiTheme(brightness);
    final m3eTheme = buildM3ETheme(theme);
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
        child: M3ETheme(data: m3eTheme, child: child),
      ),
    );
  }
}

/// Ensures an [M3ETheme] and `material_ui` [mui.Theme] are available even when
/// a page or widget is pumped directly in a standalone test `MaterialApp`.
class EnsureDextopExpressive extends StatelessWidget {
  const EnsureDextopExpressive({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (M3ETheme.maybeOf(context) != null) return child;
    final brightness = fm.Theme.of(context).brightness;
    final theme = DextopExpressive.buildMuiTheme(brightness);
    return mui.Theme(
      data: theme,
      child: M3ETheme(
        data: DextopExpressive.buildM3ETheme(theme),
        child: child,
      ),
    );
  }
}

/// Whether the window is tablet-sized (DeX, a tablet, an unfolded phone).
bool dextopWide(BuildContext context) => MediaQuery.sizeOf(context).width >= 840;

/// Opens [page] as an expressive route.
Route<T> dextopRoute<T>(Widget page) =>
    mui.MaterialPageRoute<T>(builder: (_) => page);

/// The expressive widgets use a separate localization type from Flutter.
const LocalizationsDelegate<mui.MaterialLocalizations>
dextopMaterialUiLocalizations = mui.GlobalMaterialLocalizations.delegate;

/// Resolves an interface string from the shared locale catalog.
String dextopText(BuildContext context, String key) => localText(context, key);
