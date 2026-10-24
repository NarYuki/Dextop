import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// Theme values for `M3ERadio`.
@immutable
class M3ERadioTheme extends M3EThemeExtension<M3ERadioTheme> {
  /// M3ERadioTheme.
  const M3ERadioTheme({
    this.ringSize = 20,
    this.hitSize = 40,
    this.targetSize = 48,
    this.dotSize = 10,
    this.borderWidth = 2,
    this.labelGap = 8,
    this.disabledOpacity = 0.38,
    this.hoverStateLayerOpacity = 0.08,
    this.focusStateLayerOpacity = 0.1,
    this.pressedStateLayerOpacity = 0.1,
  });

  /// defaults.

  static const M3ERadioTheme defaults = M3ERadioTheme();

  /// Icon diameter.
  final double ringSize;

  /// Circular state-layer size.
  final double hitSize;

  /// Touch target. The state layer stays centered inside this slot.
  final double targetSize;

  /// Selected inner-dot diameter.
  final double dotSize;

  /// Unselected and selected ring stroke width.
  final double borderWidth;

  /// Space between the control and an optional label.
  final double labelGap;

  /// disabledOpacity.
  final double disabledOpacity;

  /// Hover state-layer opacity.
  final double hoverStateLayerOpacity;

  /// Focus state-layer opacity.
  final double focusStateLayerOpacity;

  /// Pressed state-layer opacity.
  final double pressedStateLayerOpacity;

  /// Opacity for the active hover, focus, or pressed state layer.
  double stateLayerOpacity(M3EInteractionState state) {
    if (state.pressed) {
      return pressedStateLayerOpacity;
    }
    if (state.focused) {
      return focusStateLayerOpacity;
    }
    if (state.hovered) {
      return hoverStateLayerOpacity;
    }
    return 0;
  }

  /// stateLayerColor.
  ///
  /// Pressed selected uses onSurface; pressed unselected uses primary.
  /// Hover and focus use primary when selected and onSurface when not.
  Color stateLayerColor(
    M3EColorScheme scheme, {
    required bool selected,
    bool pressed = false,
  }) {
    if (pressed) {
      return selected ? scheme.onSurface : scheme.primary;
    }
    return selected ? scheme.primary : scheme.onSurface;
  }

  /// color.

  Color color(
    M3EColorScheme scheme, {
    required bool enabled,
    required bool error,
    required bool selected,
    bool hovered = false,
    bool focused = false,
    bool pressed = false,
  }) {
    if (!enabled) {
      return M3EColorUtils.withOpacity(scheme.onSurface, disabledOpacity);
    }
    if (error) {
      return scheme.error;
    }
    if (selected) {
      return scheme.primary;
    }
    if (hovered || focused || pressed) {
      return scheme.onSurface;
    }
    return scheme.onSurfaceVariant;
  }

  @override
  M3ERadioTheme copyWith({
    double? ringSize,
    double? hitSize,
    double? targetSize,
    double? dotSize,
    double? borderWidth,
    double? labelGap,
    double? disabledOpacity,
    double? hoverStateLayerOpacity,
    double? focusStateLayerOpacity,
    double? pressedStateLayerOpacity,
  }) {
    return M3ERadioTheme(
      ringSize: ringSize ?? this.ringSize,
      hitSize: hitSize ?? this.hitSize,
      targetSize: targetSize ?? this.targetSize,
      dotSize: dotSize ?? this.dotSize,
      borderWidth: borderWidth ?? this.borderWidth,
      labelGap: labelGap ?? this.labelGap,
      disabledOpacity: disabledOpacity ?? this.disabledOpacity,
      hoverStateLayerOpacity:
          hoverStateLayerOpacity ?? this.hoverStateLayerOpacity,
      focusStateLayerOpacity:
          focusStateLayerOpacity ?? this.focusStateLayerOpacity,
      pressedStateLayerOpacity:
          pressedStateLayerOpacity ?? this.pressedStateLayerOpacity,
    );
  }

  @override
  M3ERadioTheme lerp(M3ERadioTheme? other, double t) {
    if (other is! M3ERadioTheme) {
      return this;
    }
    return M3ERadioTheme(
      ringSize: _lerpDouble(ringSize, other.ringSize, t)!,
      hitSize: _lerpDouble(hitSize, other.hitSize, t)!,
      targetSize: _lerpDouble(targetSize, other.targetSize, t)!,
      dotSize: _lerpDouble(dotSize, other.dotSize, t)!,
      borderWidth: _lerpDouble(borderWidth, other.borderWidth, t)!,
      labelGap: _lerpDouble(labelGap, other.labelGap, t)!,
      disabledOpacity: _lerpDouble(disabledOpacity, other.disabledOpacity, t)!,
      hoverStateLayerOpacity: _lerpDouble(
        hoverStateLayerOpacity,
        other.hoverStateLayerOpacity,
        t,
      )!,
      focusStateLayerOpacity: _lerpDouble(
        focusStateLayerOpacity,
        other.focusStateLayerOpacity,
        t,
      )!,
      pressedStateLayerOpacity: _lerpDouble(
        pressedStateLayerOpacity,
        other.pressedStateLayerOpacity,
        t,
      )!,
    );
  }

  double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
