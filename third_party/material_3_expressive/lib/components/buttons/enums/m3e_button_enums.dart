import 'package:flutter/foundation.dart';
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3EButton, M3EButtonGroup;

export 'package:material_3_expressive/foundations/foundations.dart'
    show M3EHapticFeedback;

/// Visual styles for [M3EButton].
///
/// See also:
/// - [M3EButton] for the standard button widget
enum M3EButtonStyle {
  /// Solid background, high prominence.
  filled,

  /// Transparent background with a visible border.
  outlined,

  /// No background or border, lowest prominence.
  text,

  /// Surface-colored background with a shadow.
  elevated,

  /// Subdued background color, medium prominence.
  tonal,
}

/// Overall corner-radius strategy for buttons.
///
/// Used by [M3EButton] and [M3EButtonGroup] to control the button's
/// corner radius. [round] produces a pill shape, while [square] uses the
/// token-defined radius for the current size.
///
/// See also:
/// - [M3EButton] for the standard button widget
/// - [M3EButtonGroup] for the connected toggle button group
enum M3EButtonShape {
  /// Fully round pill shape (`height / 2`).
  round,

  /// Square corners using the token radius for the current size.
  square,
}

/// Standard size variants that map to Material 3 Expressive tokens.
///
/// Heights: xs=32dp, sm=40dp, md=56dp, lg=96dp, xl=136dp.
///
/// Used by [M3EButton] and [M3EButtonGroup].
///
/// See also:
/// - [M3EButton] for the standard button widget
/// - [M3EButtonGroup] for the connected toggle button group
@immutable
class M3EButtonSize {
  const M3EButtonSize._(
    this.name, {
    this.height,
    this.hPadding,
    this.iconSize,
    this.iconGap,
    this.outlineWidth,
    this.width,
  });

  /// The name of the size variant.
  final String name;

  /// Custom height override.
  final double? height;

  /// Custom horizontal padding override.
  final double? hPadding;

  /// Custom icon size override.
  final double? iconSize;

  /// Custom icon gap override.
  final double? iconGap;

  /// Custom outlined stroke width override.
  final double? outlineWidth;

  /// Custom width override.
  final double? width;

  /// 32 dp height.
  static const M3EButtonSize xs = M3EButtonSize._('xs');

  /// 40 dp height.
  static const M3EButtonSize sm = M3EButtonSize._('sm');

  /// 56 dp height.
  static const M3EButtonSize md = M3EButtonSize._('md');

  /// 96 dp height.
  static const M3EButtonSize lg = M3EButtonSize._('lg');

  /// 136 dp height.
  static const M3EButtonSize xl = M3EButtonSize._('xl');

  /// Creates a custom size variant with the specified overrides.
  factory M3EButtonSize.custom({
    double? height,
    double? hPadding,
    double? iconSize,
    double? iconGap,
    double? outlineWidth,
    double? width,
  }) {
    return M3EButtonSize._(
      'custom',
      height: height,
      hPadding: hPadding,
      iconSize: iconSize,
      iconGap: iconGap,
      outlineWidth: outlineWidth,
      width: width,
    );
  }

  /// Returns this size with selective measurement overrides.
  ///
  /// Keeps [name] so theme token tables (spacing, radii) still resolve.
  M3EButtonSize copyWith({
    double? height,
    double? hPadding,
    double? iconSize,
    double? iconGap,
    double? outlineWidth,
    double? width,
  }) {
    return M3EButtonSize._(
      name,
      height: height ?? this.height,
      hPadding: hPadding ?? this.hPadding,
      iconSize: iconSize ?? this.iconSize,
      iconGap: iconGap ?? this.iconGap,
      outlineWidth: outlineWidth ?? this.outlineWidth,
      width: width ?? this.width,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is M3EButtonSize &&
          name == other.name &&
          height == other.height &&
          hPadding == other.hPadding &&
          iconSize == other.iconSize &&
          iconGap == other.iconGap &&
          outlineWidth == other.outlineWidth &&
          width == other.width);

  @override
  int get hashCode => Object.hash(
    name,
    height,
    hPadding,
    iconSize,
    iconGap,
    outlineWidth,
    width,
  );

  @override
  String toString() => 'M3EButtonSize.$name';
}
