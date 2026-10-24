import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/sliders/m3e_sliders.dart'
    show M3ERangeSlider, M3ESlider;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ERangeSlider, M3ESlider;

import '../../../foundations/foundations.dart';
import '../enums/m3e_slider_enums.dart';
import '../res/m3e_slider_tokens.dart';

/// Resolved colors for an [M3ESlider] / [M3ERangeSlider] paint pass.
@immutable
class M3ESliderColors {
  /// M3ESliderColors.
  const M3ESliderColors({
    required this.thumb,
    required this.activeTrack,
    required this.inactiveTrack,
    required this.activeTick,
    required this.inactiveTick,
    required this.stopIndicator,
    required this.valueIndicator,
    required this.valueIndicatorLabel,
  });

  /// thumb.

  final Color thumb;

  /// activeTrack.
  final Color activeTrack;

  /// inactiveTrack.
  final Color inactiveTrack;

  /// activeTick.
  final Color activeTick;

  /// inactiveTick.
  final Color inactiveTick;

  /// stopIndicator.
  final Color stopIndicator;

  /// valueIndicator.
  final Color valueIndicator;

  /// valueIndicatorLabel.
  final Color valueIndicatorLabel;
}

/// Theme values for [M3ESlider] and [M3ERangeSlider].
@immutable
class M3ESliderTheme extends M3EThemeExtension<M3ESliderTheme> {
  /// M3ESliderTheme.
  const M3ESliderTheme({
    this.height = M3ESliderTokens.handleHeight,
    this.trackHeight = M3ESliderTokens.activeTrackHeight,
    this.handleGap = M3ESliderTokens.thumbTrackGapSize,
    this.handleWidth = M3ESliderTokens.handleWidth,
    this.handleHeight = M3ESliderTokens.handleHeight,
    this.pressedHandleWidth = M3ESliderTokens.pressedHandleWidth,
    this.focusHandleWidth = M3ESliderTokens.focusHandleWidth,
    this.hoverHandleWidth = M3ESliderTokens.hoverHandleWidth,
    this.trackInsideCornerSize = M3ESliderTokens.trackInsideCornerSize,
    this.trackCornerRadius = M3ESliderTokens.trackCornerRadius,
    this.stopIndicatorSize = M3ESliderTokens.stopIndicatorSize,
    this.tickSize = M3ESliderTokens.tickSize,
    this.stopIndicatorTrailingSpace =
        M3ESliderTokens.stopIndicatorTrailingSpace,
    this.iconEdgeInset = M3ESliderTokens.iconEdgeInset,
    this.iconSize = M3ESliderTokens.iconSize,
    this.valueIndicatorBottomSpace =
        M3ESliderTokens.valueIndicatorActiveBottomSpace,
    this.valueIndicatorWidth = M3ESliderTokens.valueIndicatorWidth,
    this.valueIndicatorHeight = M3ESliderTokens.valueIndicatorHeight,
    this.valueIndicatorRadius = M3ESliderTokens.valueIndicatorRadius,
    this.valueIndicatorFontSize = M3ESliderTokens.valueIndicatorFontSize,
    this.valueIndicatorLineHeight = M3ESliderTokens.valueIndicatorLineHeight,
    this.valueIndicatorLetterSpacing =
        M3ESliderTokens.valueIndicatorLetterSpacing,
    this.valueIndicatorFontWeight = FontWeight.w400,
    this.stateLayerSize = M3ESliderTokens.stateLayerSize,
    this.hoverStateOpacity = M3ESliderTokens.hoverStateOpacity,
    this.focusStateOpacity = M3ESliderTokens.focusStateOpacity,
    this.pressedStateOpacity = M3ESliderTokens.pressedStateOpacity,
    this.tickOpacity = M3ESliderTokens.tickOpacity,
    this.overlapOutlineWidth = M3ESliderTokens.overlapOutlineWidth,
    this.disabledActiveOpacity = M3ESliderTokens.disabledActiveTrackOpacity,
    this.disabledInactiveOpacity = M3ESliderTokens.disabledInactiveTrackOpacity,
    this.waveAmplitude = M3ESliderTokens.waveAmplitude,
    this.wavelength = M3ESliderTokens.wavelength,
    this.dockSpring = M3EMotion.expressiveSpatialFast,
  });

  /// defaults.

  static const M3ESliderTheme defaults = M3ESliderTheme();

  /// Geometry preset for [size]. Motion and color fields stay at defaults.
  factory M3ESliderTheme.forSize(M3ESliderSize size) {
    return M3ESliderTheme(
      height: size.handleHeight,
      trackHeight: size.trackHeight,
      handleHeight: size.handleHeight,
      trackCornerRadius: size.cornerRadius,
      iconEdgeInset: size.iconPadding,
      iconSize: size.iconSize,
    );
  }

  /// Uses [size] geometry when it is not [M3ESliderSize.xs].
  ///
  /// XS keeps this theme so an app-wide slider theme still applies.
  M3ESliderTheme applyingSize(M3ESliderSize size) {
    if (size == M3ESliderSize.xs) {
      return this;
    }
    final preset = M3ESliderTheme.forSize(size);
    return copyWith(
      height: preset.height,
      trackHeight: preset.trackHeight,
      handleHeight: preset.handleHeight,
      trackCornerRadius: preset.trackCornerRadius,
      iconEdgeInset: preset.iconEdgeInset,
      iconSize: preset.iconSize,
    );
  }

  /// Cross-axis extent of the interactive slider layout.
  final double height;

  /// trackHeight.

  final double trackHeight;

  /// handleGap.
  final double handleGap;

  /// handleWidth.
  final double handleWidth;

  /// handleHeight.
  final double handleHeight;

  /// pressedHandleWidth.
  final double pressedHandleWidth;

  /// Short-axis handle width while keyboard focus is showing.
  final double focusHandleWidth;

  /// Short-axis handle width while hovered.
  final double hoverHandleWidth;

  /// trackInsideCornerSize.
  final double trackInsideCornerSize;

  /// Outer corner radius for active and inactive track ends.
  ///
  /// Fixed default; not derived from [trackHeight].
  final double trackCornerRadius;

  /// stopIndicatorSize.
  final double stopIndicatorSize;

  /// tickSize.
  final double tickSize;

  /// Clear space between each track end and the outer edge of end markers.
  final double stopIndicatorTrailingSpace;

  /// Clear space between the track edge and the relocating [M3ESlider.icon].
  final double iconEdgeInset;

  /// Extent of the inset icon.
  final double iconSize;

  /// valueIndicatorBottomSpace.
  final double valueIndicatorBottomSpace;

  /// valueIndicatorWidth.
  final double valueIndicatorWidth;

  /// valueIndicatorHeight.
  final double valueIndicatorHeight;

  /// valueIndicatorRadius.
  final double valueIndicatorRadius;

  /// valueIndicatorFontSize.
  final double valueIndicatorFontSize;

  /// valueIndicatorLineHeight.
  final double valueIndicatorLineHeight;

  /// valueIndicatorLetterSpacing.
  final double valueIndicatorLetterSpacing;

  /// valueIndicatorFontWeight.
  final FontWeight valueIndicatorFontWeight;

  /// Diameter of the hover, focus, and press state layer.
  final double stateLayerSize;

  /// hoverStateOpacity.
  final double hoverStateOpacity;

  /// focusStateOpacity.
  final double focusStateOpacity;

  /// pressedStateOpacity.
  final double pressedStateOpacity;

  /// Opacity of discrete tick marks.
  final double tickOpacity;

  /// Outline width when range handles overlap.
  final double overlapOutlineWidth;

  /// disabledActiveOpacity.
  final double disabledActiveOpacity;

  /// disabledInactiveOpacity.
  final double disabledInactiveOpacity;

  /// Peak offset of the wavy active track from the centerline.
  final double waveAmplitude;

  /// Length of one full sine cycle on a wavy active track.
  final double wavelength;

  /// Dock / undock spring for the handle.
  final M3ESpring dockSpring;

  /// Compose wavy determinate amplitude: full mid-progress, zero near ends.
  double amplitudeForProgress(double progress) {
    if (progress <= 0.1 || progress >= 0.95) {
      return 0;
    }
    return 1;
  }

  /// Resolves Compose-accurate slider colors for the current [scheme].
  M3ESliderColors colors(M3EColorScheme scheme, {required bool enabled}) {
    Color active(Color c) => enabled
        ? c
        : M3EColorUtils.withOpacity(scheme.onSurface, disabledActiveOpacity);
    Color inactive(Color c) => enabled
        ? c
        : M3EColorUtils.withOpacity(scheme.onSurface, disabledInactiveOpacity);

    final Color activeTrack = active(scheme.primary);
    final Color inactiveTrack = inactive(scheme.secondaryContainer);
    final Color activeTick = enabled
        ? M3EColorUtils.withOpacity(scheme.onPrimary, tickOpacity)
        : M3EColorUtils.withOpacity(scheme.onInverseSurface, tickOpacity);
    final Color inactiveTick = enabled
        ? M3EColorUtils.withOpacity(scheme.onSecondaryContainer, tickOpacity)
        : M3EColorUtils.withOpacity(scheme.onSurface, tickOpacity);
    return M3ESliderColors(
      thumb: active(scheme.primary),
      activeTrack: activeTrack,
      inactiveTrack: inactiveTrack,
      activeTick: activeTick,
      inactiveTick: inactiveTick,
      stopIndicator: enabled ? scheme.onSecondaryContainer : scheme.onSurface,
      valueIndicator: scheme.inverseSurface,
      valueIndicatorLabel: scheme.onInverseSurface,
    );
  }

  /// Hover, focus, or press disc behind the handle. Null when idle.
  Color? stateLayerColor(
    M3EColorScheme scheme, {
    required bool hovered,
    required bool focused,
    required bool pressed,
  }) {
    if (pressed) {
      return M3EColorUtils.withOpacity(scheme.primary, pressedStateOpacity);
    }
    if (focused) {
      return M3EColorUtils.withOpacity(scheme.primary, focusStateOpacity);
    }
    if (hovered) {
      return M3EColorUtils.withOpacity(scheme.primary, hoverStateOpacity);
    }
    return null;
  }

  /// Legacy helper retained for call sites that only need one role color.
  Color color(
    M3EColorScheme scheme, {
    required Color enabledColor,
    required bool enabled,
  }) {
    if (!enabled) {
      return M3EColorUtils.withOpacity(scheme.onSurface, disabledActiveOpacity);
    }
    return enabledColor;
  }

  @override
  M3ESliderTheme copyWith({
    double? height,
    double? trackHeight,
    double? handleGap,
    double? handleWidth,
    double? handleHeight,
    double? pressedHandleWidth,
    double? focusHandleWidth,
    double? hoverHandleWidth,
    double? trackInsideCornerSize,
    double? trackCornerRadius,
    double? stopIndicatorSize,
    double? tickSize,
    double? stopIndicatorTrailingSpace,
    double? iconEdgeInset,
    double? iconSize,
    double? valueIndicatorBottomSpace,
    double? valueIndicatorWidth,
    double? valueIndicatorHeight,
    double? valueIndicatorRadius,
    double? valueIndicatorFontSize,
    double? valueIndicatorLineHeight,
    double? valueIndicatorLetterSpacing,
    FontWeight? valueIndicatorFontWeight,
    double? stateLayerSize,
    double? hoverStateOpacity,
    double? focusStateOpacity,
    double? pressedStateOpacity,
    double? tickOpacity,
    double? overlapOutlineWidth,
    double? disabledActiveOpacity,
    double? disabledInactiveOpacity,
    double? waveAmplitude,
    double? wavelength,
    M3ESpring? dockSpring,
  }) {
    return M3ESliderTheme(
      height: height ?? this.height,
      trackHeight: trackHeight ?? this.trackHeight,
      handleGap: handleGap ?? this.handleGap,
      handleWidth: handleWidth ?? this.handleWidth,
      handleHeight: handleHeight ?? this.handleHeight,
      pressedHandleWidth: pressedHandleWidth ?? this.pressedHandleWidth,
      focusHandleWidth: focusHandleWidth ?? this.focusHandleWidth,
      hoverHandleWidth: hoverHandleWidth ?? this.hoverHandleWidth,
      trackInsideCornerSize:
          trackInsideCornerSize ?? this.trackInsideCornerSize,
      trackCornerRadius: trackCornerRadius ?? this.trackCornerRadius,
      stopIndicatorSize: stopIndicatorSize ?? this.stopIndicatorSize,
      tickSize: tickSize ?? this.tickSize,
      stopIndicatorTrailingSpace:
          stopIndicatorTrailingSpace ?? this.stopIndicatorTrailingSpace,
      iconEdgeInset: iconEdgeInset ?? this.iconEdgeInset,
      iconSize: iconSize ?? this.iconSize,
      valueIndicatorBottomSpace:
          valueIndicatorBottomSpace ?? this.valueIndicatorBottomSpace,
      valueIndicatorWidth: valueIndicatorWidth ?? this.valueIndicatorWidth,
      valueIndicatorHeight: valueIndicatorHeight ?? this.valueIndicatorHeight,
      valueIndicatorRadius: valueIndicatorRadius ?? this.valueIndicatorRadius,
      valueIndicatorFontSize:
          valueIndicatorFontSize ?? this.valueIndicatorFontSize,
      valueIndicatorLineHeight:
          valueIndicatorLineHeight ?? this.valueIndicatorLineHeight,
      valueIndicatorLetterSpacing:
          valueIndicatorLetterSpacing ?? this.valueIndicatorLetterSpacing,
      valueIndicatorFontWeight:
          valueIndicatorFontWeight ?? this.valueIndicatorFontWeight,
      stateLayerSize: stateLayerSize ?? this.stateLayerSize,
      hoverStateOpacity: hoverStateOpacity ?? this.hoverStateOpacity,
      focusStateOpacity: focusStateOpacity ?? this.focusStateOpacity,
      pressedStateOpacity: pressedStateOpacity ?? this.pressedStateOpacity,
      tickOpacity: tickOpacity ?? this.tickOpacity,
      overlapOutlineWidth: overlapOutlineWidth ?? this.overlapOutlineWidth,
      disabledActiveOpacity:
          disabledActiveOpacity ?? this.disabledActiveOpacity,
      disabledInactiveOpacity:
          disabledInactiveOpacity ?? this.disabledInactiveOpacity,
      waveAmplitude: waveAmplitude ?? this.waveAmplitude,
      wavelength: wavelength ?? this.wavelength,
      dockSpring: dockSpring ?? this.dockSpring,
    );
  }

  @override
  M3ESliderTheme lerp(M3ESliderTheme? other, double t) {
    if (other is! M3ESliderTheme) {
      return this;
    }
    final _M3ESliderGeometryLerp geometry = _lerpGeometry(other, t);
    final _M3ESliderIndicatorLerp indicator = _lerpValueIndicator(other, t);
    final _M3ESliderStateLerp state = _lerpState(other, t);
    return M3ESliderTheme(
      height: geometry.height,
      trackHeight: geometry.trackHeight,
      handleGap: geometry.handleGap,
      handleWidth: geometry.handleWidth,
      handleHeight: geometry.handleHeight,
      pressedHandleWidth: geometry.pressedHandleWidth,
      focusHandleWidth: geometry.focusHandleWidth,
      hoverHandleWidth: geometry.hoverHandleWidth,
      trackInsideCornerSize: geometry.trackInsideCornerSize,
      trackCornerRadius: geometry.trackCornerRadius,
      stopIndicatorSize: geometry.stopIndicatorSize,
      tickSize: geometry.tickSize,
      stopIndicatorTrailingSpace: geometry.stopIndicatorTrailingSpace,
      iconEdgeInset: geometry.iconEdgeInset,
      iconSize: geometry.iconSize,
      valueIndicatorBottomSpace: indicator.bottomSpace,
      valueIndicatorWidth: indicator.width,
      valueIndicatorHeight: indicator.height,
      valueIndicatorRadius: indicator.radius,
      valueIndicatorFontSize: indicator.fontSize,
      valueIndicatorLineHeight: indicator.lineHeight,
      valueIndicatorLetterSpacing: indicator.letterSpacing,
      valueIndicatorFontWeight: indicator.fontWeight,
      stateLayerSize: state.stateLayerSize,
      hoverStateOpacity: state.hoverStateOpacity,
      focusStateOpacity: state.focusStateOpacity,
      pressedStateOpacity: state.pressedStateOpacity,
      tickOpacity: state.tickOpacity,
      overlapOutlineWidth: state.overlapOutlineWidth,
      disabledActiveOpacity: state.disabledActiveOpacity,
      disabledInactiveOpacity: state.disabledInactiveOpacity,
      waveAmplitude: state.waveAmplitude,
      wavelength: state.wavelength,
      dockSpring: state.dockSpring,
    );
  }

  /// Sizing / geometry fields lerped for [lerp].
  _M3ESliderGeometryLerp _lerpGeometry(M3ESliderTheme other, double t) {
    return (
      height: _lerp(height, other.height, t),
      trackHeight: _lerp(trackHeight, other.trackHeight, t),
      handleGap: _lerp(handleGap, other.handleGap, t),
      handleWidth: _lerp(handleWidth, other.handleWidth, t),
      handleHeight: _lerp(handleHeight, other.handleHeight, t),
      pressedHandleWidth: _lerp(
        pressedHandleWidth,
        other.pressedHandleWidth,
        t,
      ),
      focusHandleWidth: _lerp(focusHandleWidth, other.focusHandleWidth, t),
      hoverHandleWidth: _lerp(hoverHandleWidth, other.hoverHandleWidth, t),
      trackInsideCornerSize: _lerp(
        trackInsideCornerSize,
        other.trackInsideCornerSize,
        t,
      ),
      trackCornerRadius: _lerp(trackCornerRadius, other.trackCornerRadius, t),
      stopIndicatorSize: _lerp(stopIndicatorSize, other.stopIndicatorSize, t),
      tickSize: _lerp(tickSize, other.tickSize, t),
      stopIndicatorTrailingSpace: _lerp(
        stopIndicatorTrailingSpace,
        other.stopIndicatorTrailingSpace,
        t,
      ),
      iconEdgeInset: _lerp(iconEdgeInset, other.iconEdgeInset, t),
      iconSize: _lerp(iconSize, other.iconSize, t),
    );
  }

  /// Value-indicator fields lerped for [lerp].
  _M3ESliderIndicatorLerp _lerpValueIndicator(M3ESliderTheme other, double t) {
    return (
      bottomSpace: _lerp(
        valueIndicatorBottomSpace,
        other.valueIndicatorBottomSpace,
        t,
      ),
      width: _lerp(valueIndicatorWidth, other.valueIndicatorWidth, t),
      height: _lerp(valueIndicatorHeight, other.valueIndicatorHeight, t),
      radius: _lerp(valueIndicatorRadius, other.valueIndicatorRadius, t),
      fontSize: _lerp(valueIndicatorFontSize, other.valueIndicatorFontSize, t),
      lineHeight: _lerp(
        valueIndicatorLineHeight,
        other.valueIndicatorLineHeight,
        t,
      ),
      letterSpacing: _lerp(
        valueIndicatorLetterSpacing,
        other.valueIndicatorLetterSpacing,
        t,
      ),
      fontWeight: t < 0.5
          ? valueIndicatorFontWeight
          : other.valueIndicatorFontWeight,
    );
  }

  /// State-layer / opacity / motion fields lerped for [lerp].
  _M3ESliderStateLerp _lerpState(M3ESliderTheme other, double t) {
    return (
      stateLayerSize: _lerp(stateLayerSize, other.stateLayerSize, t),
      hoverStateOpacity: _lerp(hoverStateOpacity, other.hoverStateOpacity, t),
      focusStateOpacity: _lerp(focusStateOpacity, other.focusStateOpacity, t),
      pressedStateOpacity: _lerp(
        pressedStateOpacity,
        other.pressedStateOpacity,
        t,
      ),
      tickOpacity: _lerp(tickOpacity, other.tickOpacity, t),
      overlapOutlineWidth: _lerp(
        overlapOutlineWidth,
        other.overlapOutlineWidth,
        t,
      ),
      disabledActiveOpacity: _lerp(
        disabledActiveOpacity,
        other.disabledActiveOpacity,
        t,
      ),
      disabledInactiveOpacity: _lerp(
        disabledInactiveOpacity,
        other.disabledInactiveOpacity,
        t,
      ),
      waveAmplitude: _lerp(waveAmplitude, other.waveAmplitude, t),
      wavelength: _lerp(wavelength, other.wavelength, t),
      dockSpring: t < 0.5 ? dockSpring : other.dockSpring,
    );
  }

  double _lerp(double a, double b, double t) => a + (b - a) * t;
}

/// Geometry fields grouped for [M3ESliderTheme._lerpGeometry].
typedef _M3ESliderGeometryLerp = ({
  double height,
  double trackHeight,
  double handleGap,
  double handleWidth,
  double handleHeight,
  double pressedHandleWidth,
  double focusHandleWidth,
  double hoverHandleWidth,
  double trackInsideCornerSize,
  double trackCornerRadius,
  double stopIndicatorSize,
  double tickSize,
  double stopIndicatorTrailingSpace,
  double iconEdgeInset,
  double iconSize,
});

/// Value-indicator fields grouped for [M3ESliderTheme._lerpValueIndicator].
typedef _M3ESliderIndicatorLerp = ({
  double bottomSpace,
  double width,
  double height,
  double radius,
  double fontSize,
  double lineHeight,
  double letterSpacing,
  FontWeight fontWeight,
});

/// State-layer / opacity / motion fields grouped for
/// [M3ESliderTheme._lerpState].
typedef _M3ESliderStateLerp = ({
  double stateLayerSize,
  double hoverStateOpacity,
  double focusStateOpacity,
  double pressedStateOpacity,
  double tickOpacity,
  double overlapOutlineWidth,
  double disabledActiveOpacity,
  double disabledInactiveOpacity,
  double waveAmplitude,
  double wavelength,
  M3ESpring dockSpring,
});
