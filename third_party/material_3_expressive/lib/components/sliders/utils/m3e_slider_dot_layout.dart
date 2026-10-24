import 'package:flutter/widgets.dart';

import '../enums/m3e_slider_enums.dart';
import '../styles/m3e_slider_theme.dart';
import '../utils/m3e_slider_math.dart';
import 'm3e_slider_dot_geometry.dart';

/// One stop/tick marker along the slider track.
@immutable
class M3ESliderDotPlacement {
  /// M3ESliderDotPlacement.
  const M3ESliderDotPlacement({
    required this.primary,
    required this.color,
    required this.size,
    required this.active,
  });

  /// Position along the primary track axis (x for horizontal, y for vertical).
  final double primary;

  /// color.
  final Color color;

  /// size.
  final double size;

  /// active.
  final bool active;
}

/// Shared stop/tick placement for canvas paint and custom `dotBuilder` overlays.
abstract final class M3ESliderDotLayout {
  const M3ESliderDotLayout._();

  /// resolve.
  static List<M3ESliderDotPlacement> resolve({
    required Size size,
    required M3ESliderPaintMode mode,
    required M3ESliderTrackKind trackKind,
    required double activeStartFraction,
    required double activeEndFraction,
    required List<double> tickFractions,
    required M3ESliderColors colors,
    required double trackHeight,
    required double handleGap,
    required double handleThickness,
    required double stopIndicatorSize,
    required double tickSize,
    required double edgeInset,
    required Axis axis,
    required TextDirection textDirection,
  }) {
    final M3ESliderDotGeometry? geometry = M3ESliderDotGeometry.resolve(
      size: size,
      mode: mode,
      trackKind: trackKind,
      activeStartFraction: activeStartFraction,
      activeEndFraction: activeEndFraction,
      trackHeight: trackHeight,
      handleGap: handleGap,
      handleThickness: handleThickness,
      axis: axis,
    );
    if (geometry == null) {
      return const <M3ESliderDotPlacement>[];
    }

    final out = <M3ESliderDotPlacement>[];
    _appendEndStops(
      out,
      geometry: geometry,
      colors: colors,
      stopIndicatorSize: stopIndicatorSize,
      edgeInset: edgeInset,
    );
    _appendTicks(
      out,
      geometry: geometry,
      tickFractions: tickFractions,
      colors: colors,
      tickSize: tickSize,
      stopIndicatorSize: stopIndicatorSize,
      edgeInset: edgeInset,
    );
    return out;
  }

  static void _appendEndStops(
    List<M3ESliderDotPlacement> out, {
    required M3ESliderDotGeometry geometry,
    required M3ESliderColors colors,
    required double stopIndicatorSize,
    required double edgeInset,
  }) {
    // [edgeInset] is clear space between the track edge and the marker's outer
    // edge; centers are therefore inset by spacing + half the marker size.
    final double stopRadius = stopIndicatorSize / 2;
    final double stopStart = geometry.sliderStart + edgeInset + stopRadius;
    final double stopEnd = geometry.sliderEnd - edgeInset - stopRadius;
    if (stopEnd <= stopStart) {
      return;
    }
    if (!_onActiveOrGap(stopStart, geometry)) {
      out.add(
        M3ESliderDotPlacement(
          primary: stopStart,
          color: colors.stopIndicator,
          size: stopIndicatorSize,
          active: false,
        ),
      );
    }
    if (!_onActiveOrGap(stopEnd, geometry)) {
      out.add(
        M3ESliderDotPlacement(
          primary: stopEnd,
          color: colors.stopIndicator,
          size: stopIndicatorSize,
          active: false,
        ),
      );
    }
  }

  static void _appendTicks(
    List<M3ESliderDotPlacement> out, {
    required M3ESliderDotGeometry geometry,
    required List<double> tickFractions,
    required M3ESliderColors colors,
    required double tickSize,
    required double stopIndicatorSize,
    required double edgeInset,
  }) {
    if (tickFractions.isEmpty) {
      return;
    }

    // Discrete ticks share the same padded span as the end stops.
    final double stopRadius = stopIndicatorSize / 2;
    final double tickStart = geometry.sliderStart + edgeInset + stopRadius;
    final double tickEnd = geometry.sliderEnd - edgeInset - stopRadius;
    final double tickCenterGapStart = geometry.centered
        ? geometry.centerAxis - geometry.endGap
        : 0;
    final double tickCenterGapEnd = geometry.centered
        ? geometry.centerAxis + geometry.endGap
        : 0;
    final double tickStartGapLo = geometry.valueStart - geometry.startGap;
    final double tickStartGapHi = geometry.valueStart + geometry.startGap;
    final double tickEndGapLo = geometry.valueEnd - geometry.endGap;
    final double tickEndGapHi = geometry.valueEnd + geometry.endGap;

    for (var i = 0; i < tickFractions.length; i++) {
      // Ends are owned by stop indicators.
      if (i == 0 || i == tickFractions.length - 1) {
        continue;
      }
      final double centerTick = M3ESliderMath.lerp(
        tickStart,
        tickEnd,
        tickFractions[i],
      );
      if (_skipTick(
        centerTick,
        geometry: geometry,
        tickCenterGapStart: tickCenterGapStart,
        tickCenterGapEnd: tickCenterGapEnd,
        tickStartGapLo: tickStartGapLo,
        tickStartGapHi: tickStartGapHi,
        tickEndGapLo: tickEndGapLo,
        tickEndGapHi: tickEndGapHi,
      )) {
        continue;
      }
      final bool inActive =
          centerTick >= geometry.activeStart &&
          centerTick <= geometry.activeEnd;
      out.add(
        M3ESliderDotPlacement(
          primary: centerTick,
          color: inActive ? colors.activeTick : colors.inactiveTick,
          size: tickSize,
          active: inActive,
        ),
      );
    }
  }

  static bool _skipTick(
    double centerTick, {
    required M3ESliderDotGeometry geometry,
    required double tickCenterGapStart,
    required double tickCenterGapEnd,
    required double tickStartGapLo,
    required double tickStartGapHi,
    required double tickEndGapLo,
    required double tickEndGapHi,
  }) {
    if (geometry.centered &&
        centerTick >= tickCenterGapStart &&
        centerTick <= tickCenterGapEnd) {
      return true;
    }
    if (geometry.range &&
        centerTick >= tickStartGapLo &&
        centerTick <= tickStartGapHi) {
      return true;
    }
    return centerTick >= tickEndGapLo && centerTick <= tickEndGapHi;
  }

  static bool _onActiveOrGap(double primary, M3ESliderDotGeometry geometry) {
    if (primary >= geometry.activeStart && primary <= geometry.activeEnd) {
      return true;
    }
    if (geometry.range &&
        primary >= geometry.valueStart - geometry.startGap &&
        primary <= geometry.valueStart + geometry.startGap) {
      return true;
    }
    if (primary >= geometry.valueEnd - geometry.endGap &&
        primary <= geometry.valueEnd + geometry.endGap) {
      return true;
    }
    if (geometry.centered &&
        geometry.startGap > 0 &&
        primary >= geometry.valueEnd - geometry.startGap &&
        primary <= geometry.valueEnd + geometry.startGap) {
      return true;
    }
    return false;
  }
}
