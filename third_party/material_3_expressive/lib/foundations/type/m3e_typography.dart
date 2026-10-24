import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart' show TextTheme;

import 'm3e_type_style_conversion.dart';
import 'm3e_type_style_tokens.dart';
import 'm3e_variable_font_config.dart';

/// Whether token tables use static or variable-font tracking defaults.
enum M3ETypeScaleMode {
  /// Static M3 tokens with non-zero tracking on body and label roles.
  static,

  /// Variable-font tokens with zero tracking on every role.
  variable,
}

/// Brand and plain typeface names for the M3 type scale.
@immutable
class M3ETypefaceConfig {
  /// Creates brand/plain typeface names.
  const M3ETypefaceConfig({this.brand, this.plain});

  /// Typeface for display, headline, and title roles.
  final String? brand;

  /// Typeface for body and label roles.
  final String? plain;
}

/// The Material 3 type scale (15 roles).
///
/// Each role stores the font size, line height and tracking (letter spacing)
/// published in the M3 type system.
@immutable
class M3ETypeScale {
  /// Creates a complete Material 3 type scale.
  const M3ETypeScale({
    required this.displayLarge,
    required this.displayMedium,
    required this.displaySmall,
    required this.headlineLarge,
    required this.headlineMedium,
    required this.headlineSmall,
    required this.titleLarge,
    required this.titleMedium,
    required this.titleSmall,
    required this.bodyLarge,
    required this.bodyMedium,
    required this.bodySmall,
    required this.labelLarge,
    required this.labelMedium,
    required this.labelSmall,
  });

  /// The baseline Material 3 type scale using the platform default font.
  factory M3ETypeScale.baseline() {
    return M3ETypeScale.fromTokens(M3ETypeScaleTokens.baseline);
  }

  /// The emphasized Material 3 type scale (`md.sys.typescale.emphasized.*`).
  factory M3ETypeScale.emphasized() {
    return M3ETypeScale.fromTokens(M3ETypeScaleTokens.emphasized);
  }

  /// Variable-font baseline tokens (`md.sys.typescale.variable.*`).
  factory M3ETypeScale.variableBaseline() {
    return M3ETypeScale.fromTokens(M3ETypeScaleTokens.variableBaseline);
  }

  /// Variable-font emphasized tokens (`md.sys.typescale.variable.emphasized.*`).
  factory M3ETypeScale.variableEmphasized() {
    return M3ETypeScale.fromTokens(M3ETypeScaleTokens.variableEmphasized);
  }

  /// Builds a type scale from a token table.
  factory M3ETypeScale.fromTokens(
    Map<M3ETypeRole, M3ETypeStyleTokens> tokens, {
    M3ETypefaceConfig? typeface,
  }) {
    TextStyle build(M3ETypeRole role) {
      final String? family = typeface == null
          ? null
          : role.isBrandRole
          ? typeface.brand
          : typeface.plain;
      return tokens[role]!.toTextStyle(fontFamily: family);
    }

    return M3ETypeScale(
      displayLarge: build(M3ETypeRole.displayLarge),
      displayMedium: build(M3ETypeRole.displayMedium),
      displaySmall: build(M3ETypeRole.displaySmall),
      headlineLarge: build(M3ETypeRole.headlineLarge),
      headlineMedium: build(M3ETypeRole.headlineMedium),
      headlineSmall: build(M3ETypeRole.headlineSmall),
      titleLarge: build(M3ETypeRole.titleLarge),
      titleMedium: build(M3ETypeRole.titleMedium),
      titleSmall: build(M3ETypeRole.titleSmall),
      bodyLarge: build(M3ETypeRole.bodyLarge),
      bodyMedium: build(M3ETypeRole.bodyMedium),
      bodySmall: build(M3ETypeRole.bodySmall),
      labelLarge: build(M3ETypeRole.labelLarge),
      labelMedium: build(M3ETypeRole.labelMedium),
      labelSmall: build(M3ETypeRole.labelSmall),
    );
  }

  /// Largest display role.
  final TextStyle displayLarge;

  /// Medium display role.
  final TextStyle displayMedium;

  /// Smallest display role.
  final TextStyle displaySmall;

  /// Largest headline role.
  final TextStyle headlineLarge;

  /// Medium headline role.
  final TextStyle headlineMedium;

  /// Smallest headline role.
  final TextStyle headlineSmall;

  /// Largest title role.
  final TextStyle titleLarge;

  /// Medium title role.
  final TextStyle titleMedium;

  /// Smallest title role.
  final TextStyle titleSmall;

  /// Largest body role.
  final TextStyle bodyLarge;

  /// Medium body role.
  final TextStyle bodyMedium;

  /// Smallest body role.
  final TextStyle bodySmall;

  /// Largest label role.
  final TextStyle labelLarge;

  /// Medium label role.
  final TextStyle labelMedium;

  /// Smallest label role.
  final TextStyle labelSmall;

  /// Every role in declaration order.
  Iterable<TextStyle> get roles sync* {
    for (final M3ETypeRole role in M3ETypeRole.values) {
      yield roleStyle(role);
    }
  }

  /// Returns the [TextStyle] for [role].
  TextStyle roleStyle(M3ETypeRole role) {
    switch (role) {
      case M3ETypeRole.displayLarge:
        return displayLarge;
      case M3ETypeRole.displayMedium:
        return displayMedium;
      case M3ETypeRole.displaySmall:
        return displaySmall;
      case M3ETypeRole.headlineLarge:
        return headlineLarge;
      case M3ETypeRole.headlineMedium:
        return headlineMedium;
      case M3ETypeRole.headlineSmall:
        return headlineSmall;
      case M3ETypeRole.titleLarge:
        return titleLarge;
      case M3ETypeRole.titleMedium:
        return titleMedium;
      case M3ETypeRole.titleSmall:
        return titleSmall;
      case M3ETypeRole.bodyLarge:
        return bodyLarge;
      case M3ETypeRole.bodyMedium:
        return bodyMedium;
      case M3ETypeRole.bodySmall:
        return bodySmall;
      case M3ETypeRole.labelLarge:
        return labelLarge;
      case M3ETypeRole.labelMedium:
        return labelMedium;
      case M3ETypeRole.labelSmall:
        return labelSmall;
    }
  }

  /// Adapts a framework [TextTheme] into an [M3ETypeScale].
  ///
  /// Each role is taken from [textTheme] when present, falling back to the
  /// [M3ETypeScale.baseline] value otherwise.
  factory M3ETypeScale.fromTextTheme(TextTheme textTheme) {
    final base = M3ETypeScale.baseline();
    return M3ETypeScale(
      displayLarge: textTheme.displayLarge ?? base.displayLarge,
      displayMedium: textTheme.displayMedium ?? base.displayMedium,
      displaySmall: textTheme.displaySmall ?? base.displaySmall,
      headlineLarge: textTheme.headlineLarge ?? base.headlineLarge,
      headlineMedium: textTheme.headlineMedium ?? base.headlineMedium,
      headlineSmall: textTheme.headlineSmall ?? base.headlineSmall,
      titleLarge: textTheme.titleLarge ?? base.titleLarge,
      titleMedium: textTheme.titleMedium ?? base.titleMedium,
      titleSmall: textTheme.titleSmall ?? base.titleSmall,
      bodyLarge: textTheme.bodyLarge ?? base.bodyLarge,
      bodyMedium: textTheme.bodyMedium ?? base.bodyMedium,
      bodySmall: textTheme.bodySmall ?? base.bodySmall,
      labelLarge: textTheme.labelLarge ?? base.labelLarge,
      labelMedium: textTheme.labelMedium ?? base.labelMedium,
      labelSmall: textTheme.labelSmall ?? base.labelSmall,
    );
  }

  /// The per-size label font sizes used by expressive buttons.
  M3EButtonFontSize get buttonFontSize => const M3EButtonFontSize();

  /// A framework [TextTheme] mirroring these baseline roles.
  TextTheme toTextTheme() {
    final TextTheme? cached = _textThemeCache[this];
    if (cached != null) {
      return cached;
    }
    final textTheme = TextTheme(
      displayLarge: displayLarge,
      displayMedium: displayMedium,
      displaySmall: displaySmall,
      headlineLarge: headlineLarge,
      headlineMedium: headlineMedium,
      headlineSmall: headlineSmall,
      titleLarge: titleLarge,
      titleMedium: titleMedium,
      titleSmall: titleSmall,
      bodyLarge: bodyLarge,
      bodyMedium: bodyMedium,
      bodySmall: bodySmall,
      labelLarge: labelLarge,
      labelMedium: labelMedium,
      labelSmall: labelSmall,
    );
    _textThemeCache[this] = textTheme;
    return textTheme;
  }

  /// Returns a copy with [color] applied to every role.
  M3ETypeScale withColor(Color color) => apply(color: color);

  /// Applies shared typography attributes to every role.
  M3ETypeScale apply({
    String? fontFamily,
    M3ETypefaceConfig? typeface,
    List<String>? fontFamilyFallback,
    String? package,
    double fontSizeFactor = 1.0,
    double fontSizeDelta = 0.0,
    Color? color,
    TextDecoration? decoration,
    Color? decorationColor,
    TextDecorationStyle? decorationStyle,
    List<FontVariation>? fontVariations,
  }) {
    final mapped = <M3ETypeRole, TextStyle>{};
    for (final M3ETypeRole role in M3ETypeRole.values) {
      final TextStyle style = roleStyle(role);
      final String? family = typeface != null
          ? role.isBrandRole
                ? typeface.brand
                : typeface.plain
          : fontFamily;
      mapped[role] = style.copyWith(
        fontFamily: family,
        fontFamilyFallback: fontFamilyFallback,
        package: package,
        fontSize: style.fontSize == null
            ? null
            : style.fontSize! * fontSizeFactor + fontSizeDelta,
        color: color,
        decoration: decoration,
        decorationColor: decorationColor,
        decorationStyle: decorationStyle,
        fontVariations: fontVariations,
      );
    }
    return M3ETypeScale._fromMapped(mapped);
  }

  /// Applies per-role variable-font axes from [config].
  M3ETypeScale applyVariableFont(
    M3EVariableFontConfig config, {
    bool isEmphasizedScale = false,
  }) {
    final mapped = <M3ETypeRole, TextStyle>{};
    for (final M3ETypeRole role in M3ETypeRole.values) {
      final TextStyle style = roleStyle(role);
      mapped[role] = style.copyWith(
        fontVariations: config.resolveForRole(
          role,
          style,
          isEmphasizedScale: isEmphasizedScale,
        ),
      );
    }
    return M3ETypeScale._fromMapped(mapped);
  }

  /// Builds a [TextStyle] for [role] using [variant] tokens and optional overrides.
  TextStyle styleFor(
    M3ETypeRole role, {
    M3ETypeScaleVariant variant = M3ETypeScaleVariant.baseline,
    M3ETypeStyleTokens? overrides,
    M3EVariableFontConfig? variableFont,
  }) {
    M3ETypeStyleTokens tokens = M3ETypeStyleConversion.tokensFor(role, variant);
    if (overrides != null) {
      tokens = tokens.copyWith(
        fontSize: overrides.fontSize,
        lineHeight: overrides.lineHeight,
        letterSpacing: overrides.letterSpacing,
        fontWeight: overrides.fontWeight,
      );
    }

    final TextStyle base = roleStyle(role);
    TextStyle result = tokens.toTextStyle(fontFamily: base.fontFamily);
    result = result.copyWith(
      fontFamilyFallback: base.fontFamilyFallback,
      color: base.color,
      decoration: base.decoration,
      decorationColor: base.decorationColor,
      decorationStyle: base.decorationStyle,
    );

    if (variableFont != null) {
      result = result.copyWith(
        fontVariations: variableFont.resolveForRole(
          role,
          result,
          isEmphasizedScale: variant.isEmphasized,
        ),
      );
    }

    return result;
  }

  M3ETypeScale._fromMapped(Map<M3ETypeRole, TextStyle> mapped)
    : displayLarge = mapped[M3ETypeRole.displayLarge]!,
      displayMedium = mapped[M3ETypeRole.displayMedium]!,
      displaySmall = mapped[M3ETypeRole.displaySmall]!,
      headlineLarge = mapped[M3ETypeRole.headlineLarge]!,
      headlineMedium = mapped[M3ETypeRole.headlineMedium]!,
      headlineSmall = mapped[M3ETypeRole.headlineSmall]!,
      titleLarge = mapped[M3ETypeRole.titleLarge]!,
      titleMedium = mapped[M3ETypeRole.titleMedium]!,
      titleSmall = mapped[M3ETypeRole.titleSmall]!,
      bodyLarge = mapped[M3ETypeRole.bodyLarge]!,
      bodyMedium = mapped[M3ETypeRole.bodyMedium]!,
      bodySmall = mapped[M3ETypeRole.bodySmall]!,
      labelLarge = mapped[M3ETypeRole.labelLarge]!,
      labelMedium = mapped[M3ETypeRole.labelMedium]!,
      labelSmall = mapped[M3ETypeRole.labelSmall]!;
}

/// Baseline and emphasized Material 3 type scales (30 styles total).
@immutable
class M3ETypography {
  /// Creates baseline and emphasized type scales.
  const M3ETypography({required this.baseline, required this.emphasized});

  /// Default M3 typography for [mode].
  factory M3ETypography.material3({
    M3ETypeScaleMode mode = M3ETypeScaleMode.static,
  }) {
    switch (mode) {
      case M3ETypeScaleMode.static:
        return M3ETypography(
          baseline: M3ETypeScale.baseline(),
          emphasized: M3ETypeScale.emphasized(),
        );
      case M3ETypeScaleMode.variable:
        return M3ETypography(
          baseline: M3ETypeScale.variableBaseline(),
          emphasized: M3ETypeScale.variableEmphasized(),
        );
    }
  }

  /// Default UI type styles (`md.sys.typescale.*` or variable equivalent).
  final M3ETypeScale baseline;

  /// Emphasized type styles (`md.sys.typescale.emphasized.*`).
  final M3ETypeScale emphasized;

  /// Returns a copy with [color] applied to both scales.
  M3ETypography withColor(Color color) => apply(color: color);

  /// Applies shared typography attributes to both scales.
  M3ETypography apply({
    String? fontFamily,
    M3ETypefaceConfig? typeface,
    List<String>? fontFamilyFallback,
    String? package,
    double fontSizeFactor = 1.0,
    double fontSizeDelta = 0.0,
    Color? color,
    TextDecoration? decoration,
    Color? decorationColor,
    TextDecorationStyle? decorationStyle,
    List<FontVariation>? fontVariations,
  }) {
    return M3ETypography(
      baseline: baseline.apply(
        fontFamily: fontFamily,
        typeface: typeface,
        fontFamilyFallback: fontFamilyFallback,
        package: package,
        fontSizeFactor: fontSizeFactor,
        fontSizeDelta: fontSizeDelta,
        color: color,
        decoration: decoration,
        decorationColor: decorationColor,
        decorationStyle: decorationStyle,
        fontVariations: fontVariations,
      ),
      emphasized: emphasized.apply(
        fontFamily: fontFamily,
        typeface: typeface,
        fontFamilyFallback: fontFamilyFallback,
        package: package,
        fontSizeFactor: fontSizeFactor,
        fontSizeDelta: fontSizeDelta,
        color: color,
        decoration: decoration,
        decorationColor: decorationColor,
        decorationStyle: decorationStyle,
        fontVariations: fontVariations,
      ),
    );
  }

  /// Applies per-role variable-font axes to both scales.
  M3ETypography applyVariableFont(M3EVariableFontConfig config) {
    return M3ETypography(
      baseline: baseline.applyVariableFont(config),
      emphasized: emphasized.applyVariableFont(config, isEmphasizedScale: true),
    );
  }
}

/// Named Roboto Flex axis presets for Material 3 Expressive type.
///
/// Static and mono fonts ignore axes they do not define.
enum M3ETypeVariations {
  /// No extra axes; the baseline scale weights stand.
  regular,

  /// Weight and grade axis bump (`wght` + `GRAD`).
  ///
  /// Not the M3 emphasized type scale — use [M3ETypography.emphasized] for
  /// `md.sys.typescale.emphasized.*` roles.
  emphasized,

  /// Condensed width (`wdth` 75).
  condensed,

  /// Extra-condensed width (`wdth` 50).
  extraCondensed,

  /// Wide width (`wdth` 125).
  wide,

  /// Extra-wide width (`wdth` 151).
  extraWide,

  /// Roundness on (`ROND` 100).
  round;

  /// Documented alias for [emphasized] axis preset (not the emphasized scale).
  static const M3ETypeVariations graded = emphasized;

  /// Variable-font axes for this preset.
  List<FontVariation> get variations {
    switch (this) {
      case M3ETypeVariations.regular:
        return const <FontVariation>[];
      case M3ETypeVariations.emphasized:
        return const <FontVariation>[
          FontVariation('wght', 600),
          FontVariation('GRAD', 50),
        ];
      case M3ETypeVariations.condensed:
        return const <FontVariation>[FontVariation('wdth', 75)];
      case M3ETypeVariations.extraCondensed:
        return const <FontVariation>[FontVariation('wdth', 50)];
      case M3ETypeVariations.wide:
        return const <FontVariation>[FontVariation('wdth', 125)];
      case M3ETypeVariations.extraWide:
        return const <FontVariation>[FontVariation('wdth', 151)];
      case M3ETypeVariations.round:
        return const <FontVariation>[FontVariation('ROND', 100)];
    }
  }
}

/// Per-size label font sizes for expressive buttons.
@immutable
class M3EButtonFontSize {
  /// Creates button label sizes for each expressive size step.
  const M3EButtonFontSize({
    this.xs = 14,
    this.sm = 14,
    this.md = 16,
    this.lg = 20,
    this.xl = 24,
  });

  /// Extra-small button label size.
  final double xs;

  /// Small button label size.
  final double sm;

  /// Medium button label size.
  final double md;

  /// Large button label size.
  final double lg;

  /// Extra-large button label size.
  final double xl;
}

/// Memoises [M3ETypeScale.toTextTheme] results per instance.
final Expando<TextTheme> _textThemeCache = Expando<TextTheme>();
