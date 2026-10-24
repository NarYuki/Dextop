import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart' show ColorScheme;

// Re-export so ColorScheme.harmonized / Color.harmonizeWith stay available via
// foundations without a duplicate extension (dynamic_color 2.x uses material_ui).
export 'package:dynamic_color/dynamic_color.dart'
    show ColorHarmonization, ColorSchemeHarmonization;

/// The full set of Material 3 color roles used across expressive components.
///
/// The scheme can be built from a seed color via [M3EColorScheme.fromSeed],
/// which delegates tonal palette generation to the framework, or adapted from
/// an existing [ColorScheme] with [M3EColorScheme.fromColorScheme].
@immutable
class M3EColorScheme {
  /// Creates a complete Material 3 expressive color scheme.
  const M3EColorScheme({
    required this.brightness,
    required this.primary,
    required this.onPrimary,
    required this.primaryContainer,
    required this.onPrimaryContainer,
    required this.secondary,
    required this.onSecondary,
    required this.secondaryContainer,
    required this.onSecondaryContainer,
    required this.tertiary,
    required this.onTertiary,
    required this.tertiaryContainer,
    required this.onTertiaryContainer,
    required this.error,
    required this.onError,
    required this.errorContainer,
    required this.onErrorContainer,
    required this.surface,
    required this.onSurface,
    required this.onSurfaceVariant,
    required this.surfaceContainerLowest,
    required this.surfaceContainerLow,
    required this.surfaceContainer,
    required this.surfaceContainerHigh,
    required this.surfaceContainerHighest,
    required this.surfaceDim,
    required this.surfaceBright,
    required this.inverseSurface,
    required this.onInverseSurface,
    required this.inversePrimary,
    required this.outline,
    required this.outlineVariant,
    required this.shadow,
    required this.scrim,
    required this.surfaceTint,
    required this.emphasis,
    required this.onEmphasis,
    required this.info,
    required this.success,
    required this.warning,
    required this.danger,
    required this.surfaceStrong,
    required this.onSurfaceStrong,
    required this.outlineStrong,
  });

  /// Builds a scheme from a single [seed] color.
  factory M3EColorScheme.fromSeed(
    Color seed, {
    Brightness brightness = Brightness.light,
  }) {
    return M3EColorScheme.fromColorScheme(
      ColorScheme.fromSeed(seedColor: seed, brightness: brightness),
    );
  }

  /// Adapts a framework [ColorScheme] into an [M3EColorScheme].
  ///
  /// The expressive/semantic roles (`emphasis`, `info`, `success`, `warning`,
  /// `danger`, `surfaceStrong`, `outlineStrong`) mirror the token logic from
  /// the `m3e_design` package, derived from the base scheme.
  factory M3EColorScheme.fromColorScheme(ColorScheme scheme) {
    final isDark = scheme.brightness == Brightness.dark;
    final surfaceStrong = Color.alphaBlend(
      scheme.primary.withValues(alpha: 0.06),
      scheme.surface,
    );
    return M3EColorScheme(
      brightness: scheme.brightness,
      emphasis: scheme.primary,
      onEmphasis: scheme.onPrimary,
      info: scheme.tertiary,
      success: isDark ? const Color(0xFF81C784) : const Color(0xFF2E7D32),
      warning: isDark ? const Color(0xFFFFB74D) : const Color(0xFFEF6C00),
      danger: scheme.error,
      surfaceStrong: surfaceStrong,
      onSurfaceStrong: scheme.onSurface,
      outlineStrong: scheme.outline,
      primary: scheme.primary,
      onPrimary: scheme.onPrimary,
      primaryContainer: scheme.primaryContainer,
      onPrimaryContainer: scheme.onPrimaryContainer,
      secondary: scheme.secondary,
      onSecondary: scheme.onSecondary,
      secondaryContainer: scheme.secondaryContainer,
      onSecondaryContainer: scheme.onSecondaryContainer,
      tertiary: scheme.tertiary,
      onTertiary: scheme.onTertiary,
      tertiaryContainer: scheme.tertiaryContainer,
      onTertiaryContainer: scheme.onTertiaryContainer,
      error: scheme.error,
      onError: scheme.onError,
      errorContainer: scheme.errorContainer,
      onErrorContainer: scheme.onErrorContainer,
      surface: scheme.surface,
      onSurface: scheme.onSurface,
      onSurfaceVariant: scheme.onSurfaceVariant,
      surfaceContainerLowest: scheme.surfaceContainerLowest,
      surfaceContainerLow: scheme.surfaceContainerLow,
      surfaceContainer: scheme.surfaceContainer,
      surfaceContainerHigh: scheme.surfaceContainerHigh,
      surfaceContainerHighest: scheme.surfaceContainerHighest,
      surfaceDim: scheme.surfaceDim,
      surfaceBright: scheme.surfaceBright,
      inverseSurface: scheme.inverseSurface,
      onInverseSurface: scheme.onInverseSurface,
      inversePrimary: scheme.inversePrimary,
      outline: scheme.outline,
      outlineVariant: scheme.outlineVariant,
      shadow: scheme.shadow,
      scrim: scheme.scrim,
      surfaceTint: scheme.surfaceTint,
    );
  }

  /// Harmonizes expressive custom roles with [primary].
  ///
  /// Call after `fromColorScheme` when adapting a dynamic device palette so
  /// [success] and [warning] feel cohesive with the user's colors.
  M3EColorScheme harmonized() {
    return copyWith(
      success: success.harmonizeWith(primary),
      warning: warning.harmonizeWith(primary),
    );
  }

  /// The default light expressive scheme seeded from Material baseline purple.
  factory M3EColorScheme.light() =>
      M3EColorScheme.fromSeed(const Color(0xFF6750A4));

  /// The default dark expressive scheme seeded from Material baseline purple.
  factory M3EColorScheme.dark() => M3EColorScheme.fromSeed(
    const Color(0xFF6750A4),
    brightness: Brightness.dark,
  );

  /// Overall scheme brightness.
  final Brightness brightness;

  /// Primary brand color.
  final Color primary;

  /// Content color on [primary].
  final Color onPrimary;

  /// Container tone for [primary].
  final Color primaryContainer;

  /// Content color on [primaryContainer].
  final Color onPrimaryContainer;

  /// Secondary brand color.
  final Color secondary;

  /// Content color on [secondary].
  final Color onSecondary;

  /// Container tone for [secondary].
  final Color secondaryContainer;

  /// Content color on [secondaryContainer].
  final Color onSecondaryContainer;

  /// Tertiary accent color.
  final Color tertiary;

  /// Content color on [tertiary].
  final Color onTertiary;

  /// Container tone for [tertiary].
  final Color tertiaryContainer;

  /// Content color on [tertiaryContainer].
  final Color onTertiaryContainer;

  /// Error role color.
  final Color error;

  /// Content color on [error].
  final Color onError;

  /// Container tone for [error].
  final Color errorContainer;

  /// Content color on [errorContainer].
  final Color onErrorContainer;

  /// Default surface background.
  final Color surface;

  /// Content color on [surface].
  final Color onSurface;

  /// Variant content color on surfaces.
  final Color onSurfaceVariant;

  /// Lowest surface container tone.
  final Color surfaceContainerLowest;

  /// Low surface container tone.
  final Color surfaceContainerLow;

  /// Default surface container tone.
  final Color surfaceContainer;

  /// High surface container tone.
  final Color surfaceContainerHigh;

  /// Highest surface container tone.
  final Color surfaceContainerHighest;

  /// Dimmer surface tone.
  final Color surfaceDim;

  /// Brighter surface tone.
  final Color surfaceBright;

  /// Inverse surface for contrasting overlays.
  final Color inverseSurface;

  /// Content color on [inverseSurface].
  final Color onInverseSurface;

  /// Primary color used on [inverseSurface].
  final Color inversePrimary;

  /// Outline / border color.
  final Color outline;

  /// Softer outline variant.
  final Color outlineVariant;

  /// Shadow color.
  final Color shadow;

  /// Scrim / modal backdrop color.
  final Color scrim;

  /// Tint applied for elevation overlays.
  final Color surfaceTint;

  /// High-emphasis accent (defaults to [primary]).
  final Color emphasis;

  /// Content color on top of [emphasis].
  final Color onEmphasis;

  /// Informational semantic role (defaults to [tertiary]).
  final Color info;

  /// Success semantic role.
  final Color success;

  /// Warning semantic role.
  final Color warning;

  /// Danger semantic role (defaults to [error]).
  final Color danger;

  /// A slightly emphasized surface for grouped containers.
  final Color surfaceStrong;

  /// Content color on top of [surfaceStrong].
  final Color onSurfaceStrong;

  /// A stronger outline for high-contrast separators.
  final Color outlineStrong;

  /// Returns a copy with the given fields replaced.
  M3EColorScheme copyWith({
    Brightness? brightness,
    Color? primary,
    Color? onPrimary,
    Color? primaryContainer,
    Color? onPrimaryContainer,
    Color? secondary,
    Color? onSecondary,
    Color? secondaryContainer,
    Color? onSecondaryContainer,
    Color? tertiary,
    Color? onTertiary,
    Color? tertiaryContainer,
    Color? onTertiaryContainer,
    Color? error,
    Color? onError,
    Color? errorContainer,
    Color? onErrorContainer,
    Color? surface,
    Color? onSurface,
    Color? onSurfaceVariant,
    Color? surfaceContainerLowest,
    Color? surfaceContainerLow,
    Color? surfaceContainer,
    Color? surfaceContainerHigh,
    Color? surfaceContainerHighest,
    Color? surfaceDim,
    Color? surfaceBright,
    Color? inverseSurface,
    Color? onInverseSurface,
    Color? inversePrimary,
    Color? outline,
    Color? outlineVariant,
    Color? shadow,
    Color? scrim,
    Color? surfaceTint,
    Color? emphasis,
    Color? onEmphasis,
    Color? info,
    Color? success,
    Color? warning,
    Color? danger,
    Color? surfaceStrong,
    Color? onSurfaceStrong,
    Color? outlineStrong,
  }) {
    return M3EColorScheme(
      brightness: brightness ?? this.brightness,
      primary: primary ?? this.primary,
      onPrimary: onPrimary ?? this.onPrimary,
      primaryContainer: primaryContainer ?? this.primaryContainer,
      onPrimaryContainer: onPrimaryContainer ?? this.onPrimaryContainer,
      secondary: secondary ?? this.secondary,
      onSecondary: onSecondary ?? this.onSecondary,
      secondaryContainer: secondaryContainer ?? this.secondaryContainer,
      onSecondaryContainer: onSecondaryContainer ?? this.onSecondaryContainer,
      tertiary: tertiary ?? this.tertiary,
      onTertiary: onTertiary ?? this.onTertiary,
      tertiaryContainer: tertiaryContainer ?? this.tertiaryContainer,
      onTertiaryContainer: onTertiaryContainer ?? this.onTertiaryContainer,
      error: error ?? this.error,
      onError: onError ?? this.onError,
      errorContainer: errorContainer ?? this.errorContainer,
      onErrorContainer: onErrorContainer ?? this.onErrorContainer,
      surface: surface ?? this.surface,
      onSurface: onSurface ?? this.onSurface,
      onSurfaceVariant: onSurfaceVariant ?? this.onSurfaceVariant,
      surfaceContainerLowest:
          surfaceContainerLowest ?? this.surfaceContainerLowest,
      surfaceContainerLow: surfaceContainerLow ?? this.surfaceContainerLow,
      surfaceContainer: surfaceContainer ?? this.surfaceContainer,
      surfaceContainerHigh: surfaceContainerHigh ?? this.surfaceContainerHigh,
      surfaceContainerHighest:
          surfaceContainerHighest ?? this.surfaceContainerHighest,
      surfaceDim: surfaceDim ?? this.surfaceDim,
      surfaceBright: surfaceBright ?? this.surfaceBright,
      inverseSurface: inverseSurface ?? this.inverseSurface,
      onInverseSurface: onInverseSurface ?? this.onInverseSurface,
      inversePrimary: inversePrimary ?? this.inversePrimary,
      outline: outline ?? this.outline,
      outlineVariant: outlineVariant ?? this.outlineVariant,
      shadow: shadow ?? this.shadow,
      scrim: scrim ?? this.scrim,
      surfaceTint: surfaceTint ?? this.surfaceTint,
      emphasis: emphasis ?? this.emphasis,
      onEmphasis: onEmphasis ?? this.onEmphasis,
      info: info ?? this.info,
      success: success ?? this.success,
      warning: warning ?? this.warning,
      danger: danger ?? this.danger,
      surfaceStrong: surfaceStrong ?? this.surfaceStrong,
      onSurfaceStrong: onSurfaceStrong ?? this.onSurfaceStrong,
      outlineStrong: outlineStrong ?? this.outlineStrong,
    );
  }

  /// A framework [ColorScheme] mirroring these roles.
  ///
  /// Lets components written against Material's [ColorScheme] consume the
  /// expressive palette carried by `M3EThemeData`. The result is memoised per
  /// instance, so a stable theme yields a stable identity (important for
  /// downstream `identical`-based caches).
  ColorScheme toColorScheme() {
    final ColorScheme? cached = _colorSchemeCache[this];
    if (cached != null) {
      return cached;
    }
    final scheme = ColorScheme(
      brightness: brightness,
      primary: primary,
      onPrimary: onPrimary,
      primaryContainer: primaryContainer,
      onPrimaryContainer: onPrimaryContainer,
      secondary: secondary,
      onSecondary: onSecondary,
      secondaryContainer: secondaryContainer,
      onSecondaryContainer: onSecondaryContainer,
      tertiary: tertiary,
      onTertiary: onTertiary,
      tertiaryContainer: tertiaryContainer,
      onTertiaryContainer: onTertiaryContainer,
      error: error,
      onError: onError,
      errorContainer: errorContainer,
      onErrorContainer: onErrorContainer,
      surface: surface,
      onSurface: onSurface,
      onSurfaceVariant: onSurfaceVariant,
      surfaceContainerLowest: surfaceContainerLowest,
      surfaceContainerLow: surfaceContainerLow,
      surfaceContainer: surfaceContainer,
      surfaceContainerHigh: surfaceContainerHigh,
      surfaceContainerHighest: surfaceContainerHighest,
      surfaceDim: surfaceDim,
      surfaceBright: surfaceBright,
      inverseSurface: inverseSurface,
      onInverseSurface: onInverseSurface,
      inversePrimary: inversePrimary,
      outline: outline,
      outlineVariant: outlineVariant,
      shadow: shadow,
      scrim: scrim,
      surfaceTint: surfaceTint,
    );
    _colorSchemeCache[this] = scheme;
    return scheme;
  }
}

/// Memoises [M3EColorScheme.toColorScheme] results per instance.
final Expando<ColorScheme> _colorSchemeCache = Expando<ColorScheme>();
