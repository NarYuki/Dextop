import 'package:flutter/widgets.dart';

import '../color/m3e_color_scheme.dart';
import '../theme/m3e_theme_extension.dart';

/// Shared tokens for the Material 3 Expressive keyboard focus ring.
@immutable
class M3EFocusRingTheme extends M3EThemeExtension<M3EFocusRingTheme> {
  /// Creates focus ring theme tokens.
  const M3EFocusRingTheme({this.color, this.width = 2, this.gap = 2});

  /// Package defaults.
  static const M3EFocusRingTheme defaults = M3EFocusRingTheme();

  /// Optional override; when null, resolves to [M3EColorScheme.primary].
  final Color? color;

  /// Stroke width of the focus ring outline (dp).
  final double width;

  /// Gap between the component edge and the focus ring outline (dp).
  final double gap;

  /// Total outset past the component edge (`gap + width`).
  double get outset => gap + width;

  /// Resolved ring color for [scheme].
  Color resolveColor(M3EColorScheme scheme) => color ?? scheme.primary;

  @override
  M3EFocusRingTheme copyWith({
    Color? color,
    bool clearColor = false,
    double? width,
    double? gap,
  }) {
    return M3EFocusRingTheme(
      color: clearColor ? null : (color ?? this.color),
      width: width ?? this.width,
      gap: gap ?? this.gap,
    );
  }

  @override
  M3EFocusRingTheme lerp(M3EFocusRingTheme? other, double t) {
    if (other is! M3EFocusRingTheme) {
      return this;
    }
    return M3EFocusRingTheme(
      color: Color.lerp(color, other.color, t) ?? color ?? other.color,
      width: _lerpDouble(width, other.width, t),
      gap: _lerpDouble(gap, other.gap, t),
    );
  }

  static double _lerpDouble(double a, double b, double t) => a + (b - a) * t;

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) {
      return true;
    }
    return other is M3EFocusRingTheme &&
        other.color == color &&
        other.width == width &&
        other.gap == gap;
  }

  @override
  int get hashCode => Object.hash(color, width, gap);
}
