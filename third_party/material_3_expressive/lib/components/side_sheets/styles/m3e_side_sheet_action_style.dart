import 'package:flutter/widgets.dart';

/// Interaction tokens for the side sheet back and close icon buttons.
///
/// Hovered, focused and pressed icons and state layers use [activeColor]
/// (null: `primary`). The keyboard focus ring uses [focusIndicatorColor]
/// (null: `secondary`).
@immutable
class M3ESideSheetActionStyle {
  /// M3ESideSheetActionStyle.
  const M3ESideSheetActionStyle({
    this.activeColor,
    this.stateLayerColor,
    this.hoverOpacity = 0.08,
    this.focusOpacity = 0.1,
    this.pressedOpacity = 0.1,
    this.focusIndicatorColor,
    this.focusIndicatorThickness = 3,
    this.focusIndicatorOffset = 2,
  });

  /// Icon color while hovered, focused or pressed. Null uses `primary`.
  final Color? activeColor;

  /// State layer color. Null uses `primary`.
  final Color? stateLayerColor;

  /// Hover state layer opacity (0.08).
  final double hoverOpacity;

  /// Focus state layer opacity (0.1).
  final double focusOpacity;

  /// Pressed state layer opacity (0.1).
  final double pressedOpacity;

  /// Focus ring color. Null uses `secondary`.
  final Color? focusIndicatorColor;

  /// Focus ring thickness (3).
  final double focusIndicatorThickness;

  /// Gap between the button and the focus ring (2).
  final double focusIndicatorOffset;

  /// Returns a copy with the given fields replaced.
  M3ESideSheetActionStyle copyWith({
    Color? activeColor,
    Color? stateLayerColor,
    double? hoverOpacity,
    double? focusOpacity,
    double? pressedOpacity,
    Color? focusIndicatorColor,
    double? focusIndicatorThickness,
    double? focusIndicatorOffset,
  }) {
    return M3ESideSheetActionStyle(
      activeColor: activeColor ?? this.activeColor,
      stateLayerColor: stateLayerColor ?? this.stateLayerColor,
      hoverOpacity: hoverOpacity ?? this.hoverOpacity,
      focusOpacity: focusOpacity ?? this.focusOpacity,
      pressedOpacity: pressedOpacity ?? this.pressedOpacity,
      focusIndicatorColor: focusIndicatorColor ?? this.focusIndicatorColor,
      focusIndicatorThickness:
          focusIndicatorThickness ?? this.focusIndicatorThickness,
      focusIndicatorOffset: focusIndicatorOffset ?? this.focusIndicatorOffset,
    );
  }

  /// Interpolates between this and [other].
  M3ESideSheetActionStyle lerp(M3ESideSheetActionStyle other, double t) {
    return M3ESideSheetActionStyle(
      activeColor: Color.lerp(activeColor, other.activeColor, t),
      stateLayerColor: Color.lerp(stateLayerColor, other.stateLayerColor, t),
      hoverOpacity: _lerp(hoverOpacity, other.hoverOpacity, t),
      focusOpacity: _lerp(focusOpacity, other.focusOpacity, t),
      pressedOpacity: _lerp(pressedOpacity, other.pressedOpacity, t),
      focusIndicatorColor: Color.lerp(
        focusIndicatorColor,
        other.focusIndicatorColor,
        t,
      ),
      focusIndicatorThickness: _lerp(
        focusIndicatorThickness,
        other.focusIndicatorThickness,
        t,
      ),
      focusIndicatorOffset: _lerp(
        focusIndicatorOffset,
        other.focusIndicatorOffset,
        t,
      ),
    );
  }

  static double _lerp(double a, double b, double t) => a + (b - a) * t;
}
