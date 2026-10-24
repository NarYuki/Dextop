import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// Theme values for M3E time pickers.
@immutable
class M3ETimePickerTheme extends M3EThemeExtension<M3ETimePickerTheme> {
  /// M3ETimePickerTheme.
  const M3ETimePickerTheme({
    this.padding = const EdgeInsets.all(24),
    this.dialSize = 256,
    this.dialKnobRadius = 20,
    this.dialCenterRadius = 4,
    this.dialRingInset = 4,
    this.dialHandWidth = 2,
    this.dialLabelFontSize = 16,
    this.fieldSize = const Size(96, 80),
    this.fieldMargin = const EdgeInsets.symmetric(horizontal: 4),
    this.periodOptionSize = const Size(52, 40),
    this.periodPortraitSize = const Size(52, 80),
    this.periodLandscapeSize = const Size(216, 38),
    this.periodInputSize = const Size(52, 72),
    this.fieldPeriodGap = 12,
    this.headerDialGap = 24,
    this.elevation = 6,
    this.headerLandscapeWidth = 216,
  });

  /// defaults.

  static const M3ETimePickerTheme defaults = M3ETimePickerTheme();

  /// padding.

  final EdgeInsets padding;

  /// dialSize.
  final double dialSize;

  /// dialKnobRadius.
  final double dialKnobRadius;

  /// dialCenterRadius.
  final double dialCenterRadius;

  /// dialRingInset.
  final double dialRingInset;

  /// dialHandWidth.
  final double dialHandWidth;

  /// dialLabelFontSize.
  final double dialLabelFontSize;

  /// fieldSize.
  final Size fieldSize;

  /// fieldMargin.
  final EdgeInsets fieldMargin;

  /// Half of [periodPortraitSize] (legacy per-option size).
  final Size periodOptionSize;

  /// Full AM/PM control size in portrait dial mode (Material dayPeriodPortraitSize).
  final Size periodPortraitSize;

  /// Full AM/PM control size in landscape dial mode.
  final Size periodLandscapeSize;

  /// Full AM/PM control size in input mode (slightly shorter than portrait).
  final Size periodInputSize;

  /// fieldPeriodGap.
  final double fieldPeriodGap;

  /// headerDialGap.
  final double headerDialGap;

  /// elevation.
  final double elevation;

  /// headerLandscapeWidth.
  final double headerLandscapeWidth;

  /// The borderRadius.

  BorderRadius get borderRadius => M3EShapes.radiusExtraLarge;

  /// The dialogShape.
  BorderRadius get dialogShape => M3EShapes.radiusExtraLarge;

  /// backgroundColor.

  Color backgroundColor(M3EColorScheme scheme) => scheme.surfaceContainerHigh;

  /// containerColor.

  Color containerColor(M3EColorScheme scheme) => scheme.surfaceContainerHigh;

  /// headerBackgroundColor.

  Color headerBackgroundColor(M3EColorScheme scheme) =>
      scheme.surfaceContainerHigh;

  /// headerForegroundColor.

  Color headerForegroundColor(M3EColorScheme scheme) => scheme.onSurfaceVariant;

  /// dividerColor.

  Color dividerColor(M3EColorScheme scheme) =>
      M3EColorUtils.withOpacity(scheme.onSurface, 0.12);

  /// periodOptionBackgroundColor.

  Color periodOptionBackgroundColor(
    M3EColorScheme scheme, {
    required bool selected,
  }) {
    return selected ? scheme.tertiaryContainer : const Color(0x00000000);
  }

  /// periodOptionForegroundColor.

  Color periodOptionForegroundColor(
    M3EColorScheme scheme, {
    required bool selected,
  }) {
    return selected ? scheme.onTertiaryContainer : scheme.onSurfaceVariant;
  }

  /// fieldBackgroundColor.

  Color fieldBackgroundColor(M3EColorScheme scheme, {required bool active}) {
    return active ? scheme.primaryContainer : scheme.surfaceContainerHighest;
  }

  /// fieldForegroundColor.

  Color fieldForegroundColor(M3EColorScheme scheme, {required bool active}) {
    return active ? scheme.onPrimaryContainer : scheme.onSurface;
  }

  /// headerHelpStyle.

  TextStyle headerHelpStyle(M3ETypeScale typeScale, M3EColorScheme scheme) {
    return typeScale.labelLarge.copyWith(color: headerForegroundColor(scheme));
  }

  /// headerHeadlineStyle.

  TextStyle headerHeadlineStyle(M3ETypeScale typeScale, M3EColorScheme scheme) {
    return typeScale.headlineLarge.copyWith(color: scheme.onSurface);
  }

  /// headerHeadlineShortStyle.

  TextStyle headerHeadlineShortStyle(
    M3ETypeScale typeScale,
    M3EColorScheme scheme,
  ) {
    return typeScale.headlineSmall.copyWith(color: scheme.onSurface);
  }

  @override
  M3ETimePickerTheme copyWith({
    EdgeInsets? padding,
    double? dialSize,
    double? dialKnobRadius,
    double? dialCenterRadius,
    double? dialRingInset,
    double? dialHandWidth,
    double? dialLabelFontSize,
    Size? fieldSize,
    EdgeInsets? fieldMargin,
    Size? periodOptionSize,
    Size? periodPortraitSize,
    Size? periodLandscapeSize,
    Size? periodInputSize,
    double? fieldPeriodGap,
    double? headerDialGap,
    double? elevation,
    double? headerLandscapeWidth,
  }) {
    return M3ETimePickerTheme(
      padding: padding ?? this.padding,
      dialSize: dialSize ?? this.dialSize,
      dialKnobRadius: dialKnobRadius ?? this.dialKnobRadius,
      dialCenterRadius: dialCenterRadius ?? this.dialCenterRadius,
      dialRingInset: dialRingInset ?? this.dialRingInset,
      dialHandWidth: dialHandWidth ?? this.dialHandWidth,
      dialLabelFontSize: dialLabelFontSize ?? this.dialLabelFontSize,
      fieldSize: fieldSize ?? this.fieldSize,
      fieldMargin: fieldMargin ?? this.fieldMargin,
      periodOptionSize: periodOptionSize ?? this.periodOptionSize,
      periodPortraitSize: periodPortraitSize ?? this.periodPortraitSize,
      periodLandscapeSize: periodLandscapeSize ?? this.periodLandscapeSize,
      periodInputSize: periodInputSize ?? this.periodInputSize,
      fieldPeriodGap: fieldPeriodGap ?? this.fieldPeriodGap,
      headerDialGap: headerDialGap ?? this.headerDialGap,
      elevation: elevation ?? this.elevation,
      headerLandscapeWidth: headerLandscapeWidth ?? this.headerLandscapeWidth,
    );
  }

  @override
  M3ETimePickerTheme lerp(M3ETimePickerTheme? other, double t) {
    if (other is! M3ETimePickerTheme) {
      return this;
    }
    return M3ETimePickerTheme(
      padding: EdgeInsets.lerp(padding, other.padding, t)!,
      dialSize: _lerpDouble(dialSize, other.dialSize, t)!,
      dialKnobRadius: _lerpDouble(dialKnobRadius, other.dialKnobRadius, t)!,
      dialCenterRadius: _lerpDouble(
        dialCenterRadius,
        other.dialCenterRadius,
        t,
      )!,
      dialRingInset: _lerpDouble(dialRingInset, other.dialRingInset, t)!,
      dialHandWidth: _lerpDouble(dialHandWidth, other.dialHandWidth, t)!,
      dialLabelFontSize: _lerpDouble(
        dialLabelFontSize,
        other.dialLabelFontSize,
        t,
      )!,
      fieldSize: Size.lerp(fieldSize, other.fieldSize, t)!,
      fieldMargin: EdgeInsets.lerp(fieldMargin, other.fieldMargin, t)!,
      periodOptionSize: Size.lerp(periodOptionSize, other.periodOptionSize, t)!,
      periodPortraitSize: Size.lerp(
        periodPortraitSize,
        other.periodPortraitSize,
        t,
      )!,
      periodLandscapeSize: Size.lerp(
        periodLandscapeSize,
        other.periodLandscapeSize,
        t,
      )!,
      periodInputSize: Size.lerp(periodInputSize, other.periodInputSize, t)!,
      fieldPeriodGap: _lerpDouble(fieldPeriodGap, other.fieldPeriodGap, t)!,
      headerDialGap: _lerpDouble(headerDialGap, other.headerDialGap, t)!,
      elevation: _lerpDouble(elevation, other.elevation, t)!,
      headerLandscapeWidth: _lerpDouble(
        headerLandscapeWidth,
        other.headerLandscapeWidth,
        t,
      )!,
    );
  }

  double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
