import 'package:flutter/widgets.dart';

import 'm3e_type_style_tokens.dart';
import 'm3e_variable_font_config.dart';

/// Which M3 token table to use when resolving a type style.
enum M3ETypeScaleVariant {
  /// `md.sys.typescale.*`
  baseline,

  /// `md.sys.typescale.emphasized.*`
  emphasized,

  /// `md.sys.typescale.variable.*`
  variableBaseline,

  /// `md.sys.typescale.variable.emphasized.*`
  variableEmphasized,
}

/// Whether the variant is an emphasized token set.
extension M3ETypeScaleVariantX on M3ETypeScaleVariant {
  /// True for emphasized and variable emphasized variants.
  bool get isEmphasized {
    return this == M3ETypeScaleVariant.emphasized ||
        this == M3ETypeScaleVariant.variableEmphasized;
  }
}

/// Converts arbitrary text styles to M3 type scale variants.
final class M3ETypeStyleConversion {
  /// Creates a conversion helper instance.
  const M3ETypeStyleConversion();

  /// Tie-break priority when font size matches multiple roles.
  ///
  /// Higher value wins on equal distance (prefers label/body over display).
  static const Map<M3ETypeRole, int> _tiePriority = <M3ETypeRole, int>{
    M3ETypeRole.displayLarge: 1,
    M3ETypeRole.displayMedium: 2,
    M3ETypeRole.displaySmall: 3,
    M3ETypeRole.headlineLarge: 4,
    M3ETypeRole.headlineMedium: 5,
    M3ETypeRole.headlineSmall: 6,
    M3ETypeRole.titleLarge: 7,
    M3ETypeRole.titleMedium: 8,
    M3ETypeRole.titleSmall: 9,
    M3ETypeRole.bodyLarge: 10,
    M3ETypeRole.bodyMedium: 11,
    M3ETypeRole.bodySmall: 12,
    M3ETypeRole.labelLarge: 13,
    M3ETypeRole.labelMedium: 14,
    M3ETypeRole.labelSmall: 15,
  };

  /// Nearest type role by font size; ties prefer smaller roles.
  M3ETypeRole nearestRoleFor(TextStyle style) {
    final size = style.fontSize ?? 14;
    M3ETypeRole best = M3ETypeRole.bodyMedium;
    double bestDistance = double.infinity;
    var bestPriority = -1;

    for (final M3ETypeRole role in M3ETypeRole.values) {
      final M3ETypeStyleTokens tokens = M3ETypeScaleTokens.baseline[role]!;
      final double distance = (tokens.fontSize - size).abs();
      final int priority = _tiePriority[role]!;
      if (distance < bestDistance ||
          (distance == bestDistance && priority > bestPriority)) {
        bestDistance = distance;
        bestPriority = priority;
        best = role;
      }
    }
    return best;
  }

  /// Spec tokens for [role] and [variant].
  M3ETypeStyleTokens resolveTokens(
    M3ETypeRole role,
    M3ETypeScaleVariant variant,
  ) {
    final Map<M3ETypeRole, M3ETypeStyleTokens> table = switch (variant) {
      M3ETypeScaleVariant.baseline => M3ETypeScaleTokens.baseline,
      M3ETypeScaleVariant.emphasized => M3ETypeScaleTokens.emphasized,
      M3ETypeScaleVariant.variableBaseline =>
        M3ETypeScaleTokens.variableBaseline,
      M3ETypeScaleVariant.variableEmphasized =>
        M3ETypeScaleTokens.variableEmphasized,
    };
    return table[role]!;
  }

  /// Converts [source] to [variant], preserving family, color, and decoration.
  TextStyle toVariantFor(
    TextStyle source, {
    required M3ETypeScaleVariant variant,
    M3ETypeRole? role,
    M3ETypeStyleTokens? tokenOverrides,
    M3EVariableFontConfig? variableFont,
  }) {
    final M3ETypeRole resolvedRole = role ?? nearestRoleFor(source);
    M3ETypeStyleTokens tokens = resolveTokens(resolvedRole, variant);
    if (tokenOverrides != null) {
      tokens = tokens.copyWith(
        fontSize: tokenOverrides.fontSize,
        lineHeight: tokenOverrides.lineHeight,
        letterSpacing: tokenOverrides.letterSpacing,
        fontWeight: tokenOverrides.fontWeight,
      );
    }

    TextStyle result = tokens.toTextStyle(fontFamily: source.fontFamily);
    result = result.copyWith(
      fontFamilyFallback: source.fontFamilyFallback,
      color: source.color,
      decoration: source.decoration,
      decorationColor: source.decorationColor,
      decorationStyle: source.decorationStyle,
      backgroundColor: source.backgroundColor,
    );

    if (variableFont != null) {
      result = result.copyWith(
        fontVariations: variableFont.resolveForRole(
          resolvedRole,
          result,
          isEmphasizedScale: variant.isEmphasized,
        ),
      );
    } else if (source.fontVariations != null) {
      result = result.copyWith(fontVariations: source.fontVariations);
    }

    return result;
  }

  /// Nearest type role by font size; ties prefer smaller roles.
  static M3ETypeRole nearestRole(TextStyle style) {
    return const M3ETypeStyleConversion().nearestRoleFor(style);
  }

  /// Spec tokens for [role] and [variant].
  static M3ETypeStyleTokens tokensFor(
    M3ETypeRole role,
    M3ETypeScaleVariant variant,
  ) {
    return const M3ETypeStyleConversion().resolveTokens(role, variant);
  }

  /// Converts [source] to [variant], preserving family, color, and decoration.
  static TextStyle toVariant(
    TextStyle source, {
    required M3ETypeScaleVariant variant,
    M3ETypeRole? role,
    M3ETypeStyleTokens? tokenOverrides,
    M3EVariableFontConfig? variableFont,
  }) {
    return const M3ETypeStyleConversion().toVariantFor(
      source,
      variant: variant,
      role: role,
      tokenOverrides: tokenOverrides,
      variableFont: variableFont,
    );
  }
}
