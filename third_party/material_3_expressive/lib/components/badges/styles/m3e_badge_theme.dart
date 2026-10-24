import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// Theme values for `M3EBadge`.
@immutable
class M3EBadgeTheme extends M3EThemeExtension<M3EBadgeTheme> {
  /// M3EBadgeTheme.
  const M3EBadgeTheme({
    this.dotSize = 6,
    this.dotCornerRadius = 3,
    this.smallOffset = const Offset(6, 6),
    this.largeOffset = const Offset(12, 14),
    this.labelHorizontalPadding = 4,
    this.labelVerticalPadding = 0,
    this.labelMinSize = 16,
    this.labelCornerRadius = 8,
    this.labelFontSize = 11,
    this.labelFontWeight = FontWeight.w500,
    this.labelLineHeight = 16,
    this.labelLetterSpacing = 0.5,
  });

  /// defaults.

  static const M3EBadgeTheme defaults = M3EBadgeTheme();

  /// Small (dot) badge diameter. Spec: 6dp.
  final double dotSize;

  /// Small badge corner radius. Spec: 3dp (fully rounded circle).
  final double dotCornerRadius;

  /// Distance from icon top-trailing corner to badge bottom-leading (W×H).
  /// Spec: 6×6dp for small badges.
  final Offset smallOffset;

  /// Distance from icon top-trailing corner to badge bottom-leading (W×H).
  /// Spec: 12×14dp for large badges.
  final Offset largeOffset;

  /// Horizontal padding inside the large badge. Spec: 4dp.
  final double labelHorizontalPadding;

  /// Vertical padding inside the large badge. Spec: 0.
  final double labelVerticalPadding;

  /// Minimum width and height for the large badge. Spec: 16dp.
  final double labelMinSize;

  /// Large badge corner radius. Spec: 8dp.
  final double labelCornerRadius;

  /// Large badge label font size. Spec: 11pt.
  final double labelFontSize;

  /// Large badge label weight. Spec: 500.
  final FontWeight labelFontWeight;

  /// Large badge label line height. Spec: 16pt.
  final double labelLineHeight;

  /// Large badge label tracking. Spec: 0.5pt.
  final double labelLetterSpacing;

  /// The labelBorderRadius.
  BorderRadius get labelBorderRadius =>
      BorderRadius.circular(labelCornerRadius);

  /// Dot shape border radius.
  BorderRadius get dotBorderRadius => BorderRadius.circular(dotCornerRadius);

  /// Container color. Spec: Error.
  Color containerColor(M3EColorScheme scheme) => scheme.error;

  /// Label color. Spec: On error.
  Color labelColor(M3EColorScheme scheme) => scheme.onError;

  /// Label text style based on type-scale labelSmall with spec metrics.
  TextStyle labelStyle(M3ETypeScale type, M3EColorScheme scheme) =>
      type.labelSmall.copyWith(
        fontSize: labelFontSize,
        fontWeight: labelFontWeight,
        height: labelLineHeight / labelFontSize,
        letterSpacing: labelLetterSpacing,
        color: labelColor(scheme),
      );

  @override
  M3EBadgeTheme copyWith({
    double? dotSize,
    double? dotCornerRadius,
    Offset? smallOffset,
    Offset? largeOffset,
    double? labelHorizontalPadding,
    double? labelVerticalPadding,
    double? labelMinSize,
    double? labelCornerRadius,
    double? labelFontSize,
    FontWeight? labelFontWeight,
    double? labelLineHeight,
    double? labelLetterSpacing,
  }) {
    return M3EBadgeTheme(
      dotSize: dotSize ?? this.dotSize,
      dotCornerRadius: dotCornerRadius ?? this.dotCornerRadius,
      smallOffset: smallOffset ?? this.smallOffset,
      largeOffset: largeOffset ?? this.largeOffset,
      labelHorizontalPadding:
          labelHorizontalPadding ?? this.labelHorizontalPadding,
      labelVerticalPadding: labelVerticalPadding ?? this.labelVerticalPadding,
      labelMinSize: labelMinSize ?? this.labelMinSize,
      labelCornerRadius: labelCornerRadius ?? this.labelCornerRadius,
      labelFontSize: labelFontSize ?? this.labelFontSize,
      labelFontWeight: labelFontWeight ?? this.labelFontWeight,
      labelLineHeight: labelLineHeight ?? this.labelLineHeight,
      labelLetterSpacing: labelLetterSpacing ?? this.labelLetterSpacing,
    );
  }

  @override
  M3EBadgeTheme lerp(M3EBadgeTheme? other, double t) {
    if (other is! M3EBadgeTheme) {
      return this;
    }
    return M3EBadgeTheme(
      dotSize: _lerpDouble(dotSize, other.dotSize, t)!,
      dotCornerRadius: _lerpDouble(dotCornerRadius, other.dotCornerRadius, t)!,
      smallOffset: Offset.lerp(smallOffset, other.smallOffset, t)!,
      largeOffset: Offset.lerp(largeOffset, other.largeOffset, t)!,
      labelHorizontalPadding: _lerpDouble(
        labelHorizontalPadding,
        other.labelHorizontalPadding,
        t,
      )!,
      labelVerticalPadding: _lerpDouble(
        labelVerticalPadding,
        other.labelVerticalPadding,
        t,
      )!,
      labelMinSize: _lerpDouble(labelMinSize, other.labelMinSize, t)!,
      labelCornerRadius: _lerpDouble(
        labelCornerRadius,
        other.labelCornerRadius,
        t,
      )!,
      labelFontSize: _lerpDouble(labelFontSize, other.labelFontSize, t)!,
      labelFontWeight: t < 0.5 ? labelFontWeight : other.labelFontWeight,
      labelLineHeight: _lerpDouble(labelLineHeight, other.labelLineHeight, t)!,
      labelLetterSpacing: _lerpDouble(
        labelLetterSpacing,
        other.labelLetterSpacing,
        t,
      )!,
    );
  }

  double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
