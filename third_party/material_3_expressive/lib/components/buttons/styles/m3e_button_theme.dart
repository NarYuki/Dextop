import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_button_enums.dart';
import '../models/m3e_button_measurements.dart';
import '../res/m3e_button_constants.dart';

/// Theme values for `M3EButton`.
@immutable
class M3EButtonTheme extends M3EThemeExtension<M3EButtonTheme> {
  /// M3EButtonTheme.
  const M3EButtonTheme({
    this.focusRingWidth = 2,
    this.focusRingGap = M3EButtonConstants.kFocusRingGap,
    this.minWidthFloor = 48,
    this.dividerHeight = 24,
    this.connectedInnerRadius = 8,
    this.connectedPressedInnerRadius = 4,
    this.labelSlideDistance = 10,
    this.shapeSpring = M3EMotion.spatialFast,
  });

  /// defaults.

  static const M3EButtonTheme defaults = M3EButtonTheme();

  /// focusRingWidth.

  final double focusRingWidth;

  /// focusRingGap.
  final double focusRingGap;

  /// minWidthFloor.
  final double minWidthFloor;

  /// dividerHeight.
  final double dividerHeight;

  /// connectedInnerRadius.
  final double connectedInnerRadius;

  /// connectedPressedInnerRadius.
  final double connectedPressedInnerRadius;

  /// Horizontal distance used by selection label transitions.
  final double labelSlideDistance;

  /// Spatial spring for press/release corner morph (stiffness 1400 / damping 0.9).
  final M3ESpring shapeSpring;

  static final Map<M3EButtonSize, double> _squareRadiusTable = {
    M3EButtonSize.xs: 12,
    M3EButtonSize.sm: 12,
    M3EButtonSize.md: 16,
    M3EButtonSize.lg: 28,
    M3EButtonSize.xl: 28,
  };

  static final Map<M3EButtonSize, double> _pressedRadiusTable = {
    M3EButtonSize.xs: 8,
    M3EButtonSize.sm: 8,
    M3EButtonSize.md: 12,
    M3EButtonSize.lg: 16,
    M3EButtonSize.xl: 16,
  };

  /// Legacy intermediate hover radii (used by toggle; buttons keep resting).
  static final Map<M3EButtonSize, double> _hoveredRadiusTable = {
    M3EButtonSize.xs: 10,
    M3EButtonSize.sm: 10,
    M3EButtonSize.md: 14,
    M3EButtonSize.lg: 22,
    M3EButtonSize.xl: 22,
  };

  static final Map<M3EButtonSize, double> _equalizedMinWidthTable = {
    M3EButtonSize.xs: 40,
    M3EButtonSize.sm: 56,
    M3EButtonSize.md: 72,
    M3EButtonSize.lg: 96,
    M3EButtonSize.xl: 120,
  };

  static final Map<M3EButtonSize, M3EButtonMeasurements> _measurementsTable = {
    M3EButtonSize.xs: const M3EButtonMeasurements(
      height: 32,
      hPadding: 12,
      iconSize: 20,
      iconGap: 4,
    ),
    M3EButtonSize.sm: const M3EButtonMeasurements(
      height: 40,
      hPadding: 16,
      iconSize: 20,
      iconGap: 8,
    ),
    M3EButtonSize.md: const M3EButtonMeasurements(
      height: 56,
      hPadding: 24,
      iconSize: 24,
      iconGap: 8,
    ),
    M3EButtonSize.lg: const M3EButtonMeasurements(
      height: 96,
      hPadding: 48,
      iconSize: 32,
      iconGap: 12,
      outlineWidth: 2,
    ),
    M3EButtonSize.xl: const M3EButtonMeasurements(
      height: 136,
      hPadding: 64,
      iconSize: 40,
      iconGap: 16,
      outlineWidth: 3,
    ),
  };

  /// container.

  Color container(M3EColorScheme scheme, M3EButtonStyle style) {
    switch (style) {
      case M3EButtonStyle.filled:
        return scheme.primary;
      case M3EButtonStyle.tonal:
        return scheme.secondaryContainer;
      case M3EButtonStyle.elevated:
        return scheme.surfaceContainerLow;
      case M3EButtonStyle.outlined:
      case M3EButtonStyle.text:
        return Colors.transparent;
    }
  }

  /// foreground.

  Color foreground(M3EColorScheme scheme, M3EButtonStyle style) {
    switch (style) {
      case M3EButtonStyle.filled:
        return scheme.onPrimary;
      case M3EButtonStyle.tonal:
        return scheme.onSecondaryContainer;
      case M3EButtonStyle.elevated:
      case M3EButtonStyle.text:
        return scheme.primary;
      case M3EButtonStyle.outlined:
        return scheme.onSurfaceVariant;
    }
  }

  /// outline.

  Color outline(M3EColorScheme scheme) => scheme.outlineVariant;

  /// focusRingColor.
  ///
  /// Prefer [M3EThemeData.focusRingTheme] for a package-wide override.
  Color focusRingColor(M3EColorScheme scheme) => scheme.primary;

  /// elevation.
  ///
  /// Matches Material 3 elevated / filled / tonal button elevation tokens
  /// (`material_ui` defaults): elevated stays at level 1 while pressed.
  double elevation(M3EButtonStyle style, Set<WidgetState> states) {
    final hovered = states.contains(WidgetState.hovered);
    final pressed = states.contains(WidgetState.pressed);
    final disabled = states.contains(WidgetState.disabled);
    if (disabled) {
      return 0;
    }
    switch (style) {
      case M3EButtonStyle.elevated:
        return _elevatedElevation(pressed: pressed, hovered: hovered);
      case M3EButtonStyle.filled:
      case M3EButtonStyle.tonal:
        if (pressed) {
          return 0;
        }
        if (hovered) {
          return 1;
        }
        return 0;
      case M3EButtonStyle.outlined:
      case M3EButtonStyle.text:
        return 0;
    }
  }

  double _elevatedElevation({required bool pressed, required bool hovered}) {
    if (pressed) {
      return 1;
    }
    if (hovered) {
      return 3;
    }
    return 1;
  }

  /// squareRadius.

  double squareRadius(M3EButtonSize size) =>
      _lookupByName(_squareRadiusTable, size) ?? 12;

  /// pressedRadius.

  double pressedRadius(M3EButtonSize size) =>
      _lookupByName(_pressedRadiusTable, size) ?? 12;

  /// Intermediate hover corner radius (legacy table).
  ///
  /// `M3EButton` does not morph on hover by default; set
  /// `M3EButtonDecoration.hoveredRadius` to opt in. Toggle still reads this.
  double hoveredRadius(M3EButtonSize size) =>
      _lookupByName(_hoveredRadiusTable, size) ?? 16;

  /// equalizedMinWidth.

  double equalizedMinWidth(M3EButtonSize size) =>
      _lookupByName(_equalizedMinWidthTable, size) ?? 72;

  static double? _lookupByName(
    Map<M3EButtonSize, double> table,
    M3EButtonSize size,
  ) {
    for (final entry in table.entries) {
      if (entry.key.name == size.name) {
        return entry.value;
      }
    }
    return null;
  }

  /// measurements.

  M3EButtonMeasurements measurements(
    M3EButtonSize size, {
    M3EButtonSize? override,
  }) {
    final base = _tokenMeasurements(size);
    if (override == null) {
      // Apply field overrides from [size] (custom name or copyWith height/density).
      return base.applyCustomSize(size);
    }
    final overrideBase = _tokenMeasurements(override);
    return overrideBase.applyCustomSize(override);
  }

  M3EButtonMeasurements _tokenMeasurements(M3EButtonSize size) {
    for (final variant in _measurementsTable.keys) {
      if (variant.name == size.name) {
        return _measurementsTable[variant]!;
      }
    }
    return _measurementsTable[M3EButtonSize.md]!;
  }

  @override
  M3EButtonTheme copyWith({
    double? focusRingWidth,
    double? focusRingGap,
    double? minWidthFloor,
    double? dividerHeight,
    double? connectedInnerRadius,
    double? connectedPressedInnerRadius,
    double? labelSlideDistance,
    M3ESpring? shapeSpring,
  }) {
    return M3EButtonTheme(
      focusRingWidth: focusRingWidth ?? this.focusRingWidth,
      focusRingGap: focusRingGap ?? this.focusRingGap,
      minWidthFloor: minWidthFloor ?? this.minWidthFloor,
      dividerHeight: dividerHeight ?? this.dividerHeight,
      connectedInnerRadius: connectedInnerRadius ?? this.connectedInnerRadius,
      connectedPressedInnerRadius:
          connectedPressedInnerRadius ?? this.connectedPressedInnerRadius,
      labelSlideDistance: labelSlideDistance ?? this.labelSlideDistance,
      shapeSpring: shapeSpring ?? this.shapeSpring,
    );
  }

  @override
  M3EButtonTheme lerp(M3EButtonTheme? other, double t) {
    if (other is! M3EButtonTheme) {
      return this;
    }
    return M3EButtonTheme(
      focusRingWidth: _lerpDouble(focusRingWidth, other.focusRingWidth, t)!,
      focusRingGap: _lerpDouble(focusRingGap, other.focusRingGap, t)!,
      minWidthFloor: _lerpDouble(minWidthFloor, other.minWidthFloor, t)!,
      dividerHeight: _lerpDouble(dividerHeight, other.dividerHeight, t)!,
      connectedInnerRadius: _lerpDouble(
        connectedInnerRadius,
        other.connectedInnerRadius,
        t,
      )!,
      connectedPressedInnerRadius: _lerpDouble(
        connectedPressedInnerRadius,
        other.connectedPressedInnerRadius,
        t,
      )!,
      labelSlideDistance: _lerpDouble(
        labelSlideDistance,
        other.labelSlideDistance,
        t,
      )!,
      shapeSpring: t < 0.5 ? shapeSpring : other.shapeSpring,
    );
  }

  double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
