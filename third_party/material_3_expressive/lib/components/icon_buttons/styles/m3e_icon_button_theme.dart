import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_icon_button_enums.dart';

/// Theme values for `M3EIconButton`.
@immutable
class M3EIconButtonTheme extends M3EThemeExtension<M3EIconButtonTheme> {
  /// M3EIconButtonTheme.
  const M3EIconButtonTheme({
    this.outlineWidth,
    this.morphDuration = const Duration(milliseconds: 120),
    this.morphCurve = Curves.easeOut,
    this.filledBackgroundGradient,
    this.tonalBackgroundGradient,
    this.morphSpring = M3EMotion.spatialFast,
  });

  /// defaults.

  static const M3EIconButtonTheme defaults = M3EIconButtonTheme();

  /// Disabled container opacity (spec 0.1).
  static const double disabledContainerAlpha = 0.1;

  /// Disabled icon opacity (spec 0.38).
  static const double disabledForegroundAlpha = 0.38;

  /// Optional flat outline width override for all sizes.
  ///
  /// When null, [outlineWidthFor] uses per-size tokens (1 / 1 / 1 / 2 / 3).
  final double? outlineWidth;

  /// morphDuration.
  final Duration morphDuration;

  /// morphCurve.
  final Curve morphCurve;

  /// Optional gradient for filled icon buttons.
  final Gradient? filledBackgroundGradient;

  /// Optional gradient for tonal icon buttons.
  final Gradient? tonalBackgroundGradient;

  /// Shape morph spring (stiffness 1400 / damping 0.9).
  final M3ESpring morphSpring;

  static const Map<M3EIconButtonSize, double> _icon = {
    M3EIconButtonSize.xs: 20,
    M3EIconButtonSize.sm: 24,
    M3EIconButtonSize.md: 24,
    M3EIconButtonSize.lg: 32,
    M3EIconButtonSize.xl: 40,
  };

  static const Map<M3EIconButtonSize, Map<M3EIconButtonWidth, Size>> _visual = {
    M3EIconButtonSize.xs: {
      M3EIconButtonWidth.defaultWidth: Size(32, 32),
      M3EIconButtonWidth.narrow: Size(28, 32),
      M3EIconButtonWidth.wide: Size(40, 32),
    },
    M3EIconButtonSize.sm: {
      M3EIconButtonWidth.defaultWidth: Size(40, 40),
      M3EIconButtonWidth.narrow: Size(32, 40),
      M3EIconButtonWidth.wide: Size(52, 40),
    },
    M3EIconButtonSize.md: {
      M3EIconButtonWidth.defaultWidth: Size(56, 56),
      M3EIconButtonWidth.narrow: Size(48, 56),
      M3EIconButtonWidth.wide: Size(72, 56),
    },
    M3EIconButtonSize.lg: {
      M3EIconButtonWidth.defaultWidth: Size(96, 96),
      M3EIconButtonWidth.narrow: Size(64, 96),
      M3EIconButtonWidth.wide: Size(128, 96),
    },
    M3EIconButtonSize.xl: {
      M3EIconButtonWidth.defaultWidth: Size(136, 136),
      M3EIconButtonWidth.narrow: Size(104, 136),
      M3EIconButtonWidth.wide: Size(184, 136),
    },
  };

  static const Map<M3EIconButtonSize, Map<M3EIconButtonWidth, Size>> _target = {
    M3EIconButtonSize.xs: {
      M3EIconButtonWidth.defaultWidth: Size(48, 48),
      M3EIconButtonWidth.narrow: Size(48, 48),
      M3EIconButtonWidth.wide: Size(48, 48),
    },
    M3EIconButtonSize.sm: {
      M3EIconButtonWidth.defaultWidth: Size(48, 48),
      M3EIconButtonWidth.narrow: Size(48, 48),
      M3EIconButtonWidth.wide: Size(52, 48),
    },
    M3EIconButtonSize.md: {
      M3EIconButtonWidth.defaultWidth: Size(56, 56),
      M3EIconButtonWidth.narrow: Size(48, 56),
      M3EIconButtonWidth.wide: Size(72, 56),
    },
    M3EIconButtonSize.lg: {
      M3EIconButtonWidth.defaultWidth: Size(96, 96),
      M3EIconButtonWidth.narrow: Size(64, 96),
      M3EIconButtonWidth.wide: Size(128, 96),
    },
    M3EIconButtonSize.xl: {
      M3EIconButtonWidth.defaultWidth: Size(136, 136),
      M3EIconButtonWidth.narrow: Size(104, 136),
      M3EIconButtonWidth.wide: Size(184, 136),
    },
  };

  static const Map<M3EIconButtonSize, double> _radiusRestRound = {
    M3EIconButtonSize.xs: 16,
    M3EIconButtonSize.sm: 20,
    M3EIconButtonSize.md: 28,
    M3EIconButtonSize.lg: 48,
    M3EIconButtonSize.xl: 68,
  };

  static const Map<M3EIconButtonSize, double> _radiusRestSquare = {
    M3EIconButtonSize.xs: 12,
    M3EIconButtonSize.sm: 12,
    M3EIconButtonSize.md: 16,
    M3EIconButtonSize.lg: 28,
    M3EIconButtonSize.xl: 28,
  };

  static const Map<M3EIconButtonSize, double> _radiusPressed = {
    M3EIconButtonSize.xs: 8,
    M3EIconButtonSize.sm: 8,
    M3EIconButtonSize.md: 12,
    M3EIconButtonSize.lg: 16,
    M3EIconButtonSize.xl: 16,
  };

  static const Map<M3EIconButtonSize, double> _outlineWidth = {
    M3EIconButtonSize.xs: 1,
    M3EIconButtonSize.sm: 1,
    M3EIconButtonSize.md: 1,
    M3EIconButtonSize.lg: 2,
    M3EIconButtonSize.xl: 3,
  };

  /// iconSize.

  double iconSize(M3EIconButtonSize size) => _icon[size]!;

  /// visual.

  Size visual(M3EIconButtonSize size, M3EIconButtonWidth width) =>
      _visual[size]![width]!;

  /// target.

  Size target(M3EIconButtonSize size, M3EIconButtonWidth width) =>
      _target[size]![width]!;

  /// radiusRestRound.

  double radiusRestRound(M3EIconButtonSize size) => _radiusRestRound[size]!;

  /// radiusRestSquare.

  double radiusRestSquare(M3EIconButtonSize size) => _radiusRestSquare[size]!;

  /// radiusPressed.

  double radiusPressed(M3EIconButtonSize size) => _radiusPressed[size]!;

  /// Hover keeps the resting shape; returns resting round for API compatibility.
  double radiusHovered(M3EIconButtonSize size) => radiusRestRound(size);

  /// Outline stroke width for [size] (or [outlineWidth] when set).
  double outlineWidthFor(M3EIconButtonSize size) =>
      outlineWidth ?? _outlineWidth[size]!;

  @override
  M3EIconButtonTheme copyWith({
    double? outlineWidth,
    bool clearOutlineWidth = false,
    Duration? morphDuration,
    Curve? morphCurve,
    Gradient? filledBackgroundGradient,
    Gradient? tonalBackgroundGradient,
    M3ESpring? morphSpring,
  }) {
    return M3EIconButtonTheme(
      outlineWidth: clearOutlineWidth
          ? null
          : (outlineWidth ?? this.outlineWidth),
      morphDuration: morphDuration ?? this.morphDuration,
      morphCurve: morphCurve ?? this.morphCurve,
      filledBackgroundGradient:
          filledBackgroundGradient ?? this.filledBackgroundGradient,
      tonalBackgroundGradient:
          tonalBackgroundGradient ?? this.tonalBackgroundGradient,
      morphSpring: morphSpring ?? this.morphSpring,
    );
  }

  @override
  M3EIconButtonTheme lerp(M3EIconButtonTheme? other, double t) {
    if (other is! M3EIconButtonTheme) {
      return this;
    }
    return M3EIconButtonTheme(
      outlineWidth: t < 0.5 ? outlineWidth : other.outlineWidth,
      morphDuration: Duration(
        milliseconds: _lerpDouble(
          morphDuration.inMilliseconds.toDouble(),
          other.morphDuration.inMilliseconds.toDouble(),
          t,
        )!.round(),
      ),
      morphCurve: t < 0.5 ? morphCurve : other.morphCurve,
      filledBackgroundGradient: t < 0.5
          ? filledBackgroundGradient
          : other.filledBackgroundGradient,
      tonalBackgroundGradient: t < 0.5
          ? tonalBackgroundGradient
          : other.tonalBackgroundGradient,
      morphSpring: t < 0.5 ? morphSpring : other.morphSpring,
    );
  }

  double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
