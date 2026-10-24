import 'package:flutter/widgets.dart';

/// A role in the Material 3 type scale.
enum M3ETypeRole {
  /// Largest display role.
  displayLarge,

  /// Medium display role.
  displayMedium,

  /// Smallest display role.
  displaySmall,

  /// Largest headline role.
  headlineLarge,

  /// Medium headline role.
  headlineMedium,

  /// Smallest headline role.
  headlineSmall,

  /// Largest title role.
  titleLarge,

  /// Medium title role.
  titleMedium,

  /// Smallest title role.
  titleSmall,

  /// Largest body role.
  bodyLarge,

  /// Medium body role.
  bodyMedium,

  /// Smallest body role.
  bodySmall,

  /// Largest label role.
  labelLarge,

  /// Medium label role.
  labelMedium,

  /// Smallest label role.
  labelSmall,
}

/// Per-property tokens for one M3 type scale role.
@immutable
class M3ETypeStyleTokens {
  /// Creates tokens for one type scale role.
  const M3ETypeStyleTokens({
    required this.fontSize,
    required this.lineHeight,
    required this.letterSpacing,
    required this.fontWeight,
  });

  /// Type size in logical pixels.
  final double fontSize;

  /// Absolute line height in logical pixels.
  final double lineHeight;

  /// Letter spacing (tracking) in logical pixels.
  final double letterSpacing;

  /// Font weight for this role.
  final FontWeight fontWeight;

  /// Reads geometry tokens from a [TextStyle].
  factory M3ETypeStyleTokens.fromTextStyle(TextStyle style) {
    final double size = style.fontSize ?? 14;
    final double lineHeight = style.height == null
        ? size * 1.2
        : style.height! * size;
    return M3ETypeStyleTokens(
      fontSize: size,
      lineHeight: lineHeight,
      letterSpacing: style.letterSpacing ?? 0,
      fontWeight: style.fontWeight ?? FontWeight.w400,
    );
  }

  /// Returns a copy with the given fields replaced.
  M3ETypeStyleTokens copyWith({
    double? fontSize,
    double? lineHeight,
    double? letterSpacing,
    FontWeight? fontWeight,
  }) {
    return M3ETypeStyleTokens(
      fontSize: fontSize ?? this.fontSize,
      lineHeight: lineHeight ?? this.lineHeight,
      letterSpacing: letterSpacing ?? this.letterSpacing,
      fontWeight: fontWeight ?? this.fontWeight,
    );
  }

  /// Applies M3 emphasized weight bump (+100 or +200 per role group rules).
  M3ETypeStyleTokens toEmphasized() {
    final FontWeight next = switch (fontWeight) {
      FontWeight.w400 => FontWeight.w500,
      FontWeight.w500 => FontWeight.w700,
      _ => fontWeight,
    };
    return copyWith(fontWeight: next);
  }

  /// Builds a [TextStyle] from these tokens.
  TextStyle toTextStyle({String? fontFamily}) {
    return TextStyle(
      fontFamily: fontFamily,
      fontSize: fontSize,
      height: lineHeight / fontSize,
      letterSpacing: letterSpacing,
      fontWeight: fontWeight,
      textBaseline: TextBaseline.alphabetic,
      leadingDistribution: TextLeadingDistribution.even,
    );
  }
}

/// Canonical M3 type scale token tables (material_ui gen_defaults v38.2.31).
abstract final class M3ETypeScaleTokens {
  /// `md.sys.typescale.*` baseline tokens.
  static const Map<M3ETypeRole, M3ETypeStyleTokens> baseline =
      <M3ETypeRole, M3ETypeStyleTokens>{
        M3ETypeRole.displayLarge: M3ETypeStyleTokens(
          fontSize: 57,
          lineHeight: 64,
          letterSpacing: -0.25,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.displayMedium: M3ETypeStyleTokens(
          fontSize: 45,
          lineHeight: 52,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.displaySmall: M3ETypeStyleTokens(
          fontSize: 36,
          lineHeight: 44,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.headlineLarge: M3ETypeStyleTokens(
          fontSize: 32,
          lineHeight: 40,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.headlineMedium: M3ETypeStyleTokens(
          fontSize: 28,
          lineHeight: 36,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.headlineSmall: M3ETypeStyleTokens(
          fontSize: 24,
          lineHeight: 32,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.titleLarge: M3ETypeStyleTokens(
          fontSize: 22,
          lineHeight: 28,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.titleMedium: M3ETypeStyleTokens(
          fontSize: 16,
          lineHeight: 24,
          letterSpacing: 0.15,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.titleSmall: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0.1,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.bodyLarge: M3ETypeStyleTokens(
          fontSize: 16,
          lineHeight: 24,
          letterSpacing: 0.5,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.bodyMedium: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0.25,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.bodySmall: M3ETypeStyleTokens(
          fontSize: 12,
          lineHeight: 16,
          letterSpacing: 0.4,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.labelLarge: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0.1,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.labelMedium: M3ETypeStyleTokens(
          fontSize: 12,
          lineHeight: 16,
          letterSpacing: 0.5,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.labelSmall: M3ETypeStyleTokens(
          fontSize: 11,
          lineHeight: 16,
          letterSpacing: 0.5,
          fontWeight: FontWeight.w500,
        ),
      };

  /// `md.sys.typescale.emphasized.*` tokens.
  static const Map<M3ETypeRole, M3ETypeStyleTokens> emphasized =
      <M3ETypeRole, M3ETypeStyleTokens>{
        M3ETypeRole.displayLarge: M3ETypeStyleTokens(
          fontSize: 57,
          lineHeight: 64,
          letterSpacing: -0.25,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.displayMedium: M3ETypeStyleTokens(
          fontSize: 45,
          lineHeight: 52,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.displaySmall: M3ETypeStyleTokens(
          fontSize: 36,
          lineHeight: 44,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.headlineLarge: M3ETypeStyleTokens(
          fontSize: 32,
          lineHeight: 40,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.headlineMedium: M3ETypeStyleTokens(
          fontSize: 28,
          lineHeight: 36,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.headlineSmall: M3ETypeStyleTokens(
          fontSize: 24,
          lineHeight: 32,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.titleLarge: M3ETypeStyleTokens(
          fontSize: 22,
          lineHeight: 28,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.titleMedium: M3ETypeStyleTokens(
          fontSize: 16,
          lineHeight: 24,
          letterSpacing: 0.15,
          fontWeight: FontWeight.w700,
        ),
        M3ETypeRole.titleSmall: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0.1,
          fontWeight: FontWeight.w700,
        ),
        M3ETypeRole.bodyLarge: M3ETypeStyleTokens(
          fontSize: 16,
          lineHeight: 24,
          letterSpacing: 0.5,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.bodyMedium: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0.25,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.bodySmall: M3ETypeStyleTokens(
          fontSize: 12,
          lineHeight: 16,
          letterSpacing: 0.4,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.labelLarge: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0.1,
          fontWeight: FontWeight.w700,
        ),
        M3ETypeRole.labelMedium: M3ETypeStyleTokens(
          fontSize: 12,
          lineHeight: 16,
          letterSpacing: 0.5,
          fontWeight: FontWeight.w700,
        ),
        M3ETypeRole.labelSmall: M3ETypeStyleTokens(
          fontSize: 11,
          lineHeight: 16,
          letterSpacing: 0.5,
          fontWeight: FontWeight.w700,
        ),
      };

  /// `md.sys.typescale.variable.*` tokens (tracking zero).
  static const Map<M3ETypeRole, M3ETypeStyleTokens> variableBaseline =
      <M3ETypeRole, M3ETypeStyleTokens>{
        M3ETypeRole.displayLarge: M3ETypeStyleTokens(
          fontSize: 57,
          lineHeight: 64,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.displayMedium: M3ETypeStyleTokens(
          fontSize: 45,
          lineHeight: 52,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.displaySmall: M3ETypeStyleTokens(
          fontSize: 36,
          lineHeight: 44,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.headlineLarge: M3ETypeStyleTokens(
          fontSize: 32,
          lineHeight: 40,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.headlineMedium: M3ETypeStyleTokens(
          fontSize: 28,
          lineHeight: 36,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.headlineSmall: M3ETypeStyleTokens(
          fontSize: 24,
          lineHeight: 32,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.titleLarge: M3ETypeStyleTokens(
          fontSize: 22,
          lineHeight: 28,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.titleMedium: M3ETypeStyleTokens(
          fontSize: 16,
          lineHeight: 24,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.titleSmall: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.bodyLarge: M3ETypeStyleTokens(
          fontSize: 16,
          lineHeight: 24,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.bodyMedium: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.bodySmall: M3ETypeStyleTokens(
          fontSize: 12,
          lineHeight: 16,
          letterSpacing: 0,
          fontWeight: FontWeight.w400,
        ),
        M3ETypeRole.labelLarge: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.labelMedium: M3ETypeStyleTokens(
          fontSize: 12,
          lineHeight: 16,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.labelSmall: M3ETypeStyleTokens(
          fontSize: 11,
          lineHeight: 16,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
      };

  /// `md.sys.typescale.variable.emphasized.*` tokens (tracking zero).
  static const Map<M3ETypeRole, M3ETypeStyleTokens> variableEmphasized =
      <M3ETypeRole, M3ETypeStyleTokens>{
        M3ETypeRole.displayLarge: M3ETypeStyleTokens(
          fontSize: 57,
          lineHeight: 64,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.displayMedium: M3ETypeStyleTokens(
          fontSize: 45,
          lineHeight: 52,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.displaySmall: M3ETypeStyleTokens(
          fontSize: 36,
          lineHeight: 44,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.headlineLarge: M3ETypeStyleTokens(
          fontSize: 32,
          lineHeight: 40,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.headlineMedium: M3ETypeStyleTokens(
          fontSize: 28,
          lineHeight: 36,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.headlineSmall: M3ETypeStyleTokens(
          fontSize: 24,
          lineHeight: 32,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.titleLarge: M3ETypeStyleTokens(
          fontSize: 22,
          lineHeight: 28,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.titleMedium: M3ETypeStyleTokens(
          fontSize: 16,
          lineHeight: 24,
          letterSpacing: 0,
          fontWeight: FontWeight.w700,
        ),
        M3ETypeRole.titleSmall: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0,
          fontWeight: FontWeight.w700,
        ),
        M3ETypeRole.bodyLarge: M3ETypeStyleTokens(
          fontSize: 16,
          lineHeight: 24,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.bodyMedium: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.bodySmall: M3ETypeStyleTokens(
          fontSize: 12,
          lineHeight: 16,
          letterSpacing: 0,
          fontWeight: FontWeight.w500,
        ),
        M3ETypeRole.labelLarge: M3ETypeStyleTokens(
          fontSize: 14,
          lineHeight: 20,
          letterSpacing: 0,
          fontWeight: FontWeight.w700,
        ),
        M3ETypeRole.labelMedium: M3ETypeStyleTokens(
          fontSize: 12,
          lineHeight: 16,
          letterSpacing: 0,
          fontWeight: FontWeight.w700,
        ),
        M3ETypeRole.labelSmall: M3ETypeStyleTokens(
          fontSize: 11,
          lineHeight: 16,
          letterSpacing: 0,
          fontWeight: FontWeight.w700,
        ),
      };
}

/// Whether the role belongs to the brand typeface group (display/headline/title).
extension M3ETypeRoleX on M3ETypeRole {
  /// True for display, headline, and title roles.
  bool get isBrandRole {
    switch (this) {
      case M3ETypeRole.displayLarge:
      case M3ETypeRole.displayMedium:
      case M3ETypeRole.displaySmall:
      case M3ETypeRole.headlineLarge:
      case M3ETypeRole.headlineMedium:
      case M3ETypeRole.headlineSmall:
      case M3ETypeRole.titleLarge:
      case M3ETypeRole.titleMedium:
      case M3ETypeRole.titleSmall:
        return true;
      case M3ETypeRole.bodyLarge:
      case M3ETypeRole.bodyMedium:
      case M3ETypeRole.bodySmall:
      case M3ETypeRole.labelLarge:
      case M3ETypeRole.labelMedium:
      case M3ETypeRole.labelSmall:
        return false;
    }
  }
}
