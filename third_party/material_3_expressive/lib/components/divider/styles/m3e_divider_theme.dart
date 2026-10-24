import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_divider_inset.dart';

/// Theme values for M3EDivider.
@immutable
class M3EDividerTheme extends M3EThemeExtension<M3EDividerTheme> {
  /// Creates divider theme values.
  const M3EDividerTheme({
    this.thickness = 1,
    this.insetStart = 16,
    this.insetEnd = 0,
    this.middleInsetStart = 16,
    this.middleInsetEnd = 16,
    this.textGap = 4,
    this.endMargin = 8,
    this.bottomMargin = 8,
  });

  /// Spec defaults.
  static const M3EDividerTheme defaults = M3EDividerTheme();

  /// Line thickness.
  final double thickness;

  /// Leading inset for [M3EDividerInset.inset].
  final double insetStart;

  /// Trailing inset for [M3EDividerInset.inset].
  final double insetEnd;

  /// Leading inset for [M3EDividerInset.middle].
  final double middleInsetStart;

  /// Trailing inset for [M3EDividerInset.middle].
  final double middleInsetEnd;

  /// Gap between a divider and supporting text. The line does not apply this.
  final double textGap;

  /// Trailing margin used when outer margin is enabled.
  final double endMargin;

  /// Bottom margin used when outer margin is enabled.
  final double bottomMargin;

  /// Line color. Outline variant in both light and dark schemes.
  Color color(M3EColorScheme scheme) => scheme.outlineVariant;

  /// Leading inset for [inset] before a widget override.
  double startFor(M3EDividerInset inset) {
    return switch (inset) {
      M3EDividerInset.full => 0,
      M3EDividerInset.inset => insetStart,
      M3EDividerInset.middle => middleInsetStart,
    };
  }

  /// Trailing inset for [inset] before a widget override.
  double endFor(M3EDividerInset inset) {
    return switch (inset) {
      M3EDividerInset.full => 0,
      M3EDividerInset.inset => insetEnd,
      M3EDividerInset.middle => middleInsetEnd,
    };
  }

  @override
  M3EDividerTheme copyWith({
    double? thickness,
    double? insetStart,
    double? insetEnd,
    double? middleInsetStart,
    double? middleInsetEnd,
    double? textGap,
    double? endMargin,
    double? bottomMargin,
  }) {
    return M3EDividerTheme(
      thickness: thickness ?? this.thickness,
      insetStart: insetStart ?? this.insetStart,
      insetEnd: insetEnd ?? this.insetEnd,
      middleInsetStart: middleInsetStart ?? this.middleInsetStart,
      middleInsetEnd: middleInsetEnd ?? this.middleInsetEnd,
      textGap: textGap ?? this.textGap,
      endMargin: endMargin ?? this.endMargin,
      bottomMargin: bottomMargin ?? this.bottomMargin,
    );
  }

  @override
  M3EDividerTheme lerp(M3EDividerTheme? other, double t) {
    if (other is! M3EDividerTheme) {
      return this;
    }
    return M3EDividerTheme(
      thickness: _lerpDouble(thickness, other.thickness, t),
      insetStart: _lerpDouble(insetStart, other.insetStart, t),
      insetEnd: _lerpDouble(insetEnd, other.insetEnd, t),
      middleInsetStart: _lerpDouble(
        middleInsetStart,
        other.middleInsetStart,
        t,
      ),
      middleInsetEnd: _lerpDouble(middleInsetEnd, other.middleInsetEnd, t),
      textGap: _lerpDouble(textGap, other.textGap, t),
      endMargin: _lerpDouble(endMargin, other.endMargin, t),
      bottomMargin: _lerpDouble(bottomMargin, other.bottomMargin, t),
    );
  }

  double _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
