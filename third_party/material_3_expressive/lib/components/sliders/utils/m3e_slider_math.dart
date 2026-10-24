import 'dart:math' as math;

import 'package:flutter/services.dart' show LogicalKeyboardKey;
import 'package:material_3_expressive/components/sliders/m3e_sliders.dart'
    show M3ERangeSlider, M3ESlider;

import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ERangeSlider, M3ESlider;

/// Shared fraction / snap helpers for [M3ESlider] and [M3ERangeSlider].
abstract final class M3ESliderMath {
  const M3ESliderMath._();

  /// `0..1` fraction of [value] within [min]..[max].
  static double fraction(double value, double min, double max) {
    if (max <= min) {
      return 0;
    }
    return ((value - min) / (max - min)).clamp(0.0, 1.0);
  }

  /// Maps a `0..1` [fraction] back into [min]..[max].
  static double valueFromFraction(double fraction, double min, double max) {
    return min + fraction.clamp(0.0, 1.0) * (max - min);
  }

  /// Tick fractions when [divisions] > 0 (Compose `steps` → `steps + 2` marks).
  ///
  /// Compose `steps` is the count of intervals between endpoints minus one for
  /// stops; Flutter [divisions] matches Material's discrete stop count between
  /// min and max, so tick count is `divisions + 1` endpoints inclusive.
  static List<double> tickFractions(int? divisions) {
    if (divisions == null || divisions <= 0) {
      return const <double>[];
    }
    return List<double>.generate(divisions + 1, (int i) => i / divisions);
  }

  /// Snaps [value] to the nearest division step.
  static double snap(double value, double min, double max, int? divisions) {
    final double clamped = value.clamp(min, max);
    if (divisions == null || divisions <= 0) {
      return clamped;
    }
    final double step = (max - min) / divisions;
    return min + ((clamped - min) / step).round() * step;
  }

  /// Local pointer position → value along the track axis.
  static double valueFromOffset({
    required double localPrimary,
    required double extent,
    required double min,
    required double max,
    required int? divisions,
    required bool reverse,
  }) {
    if (extent <= 0) {
      return min;
    }
    double fraction = (localPrimary / extent).clamp(0.0, 1.0);
    if (reverse) {
      fraction = 1.0 - fraction;
    }
    return snap(valueFromFraction(fraction, min, max), min, max, divisions);
  }

  /// Closest tick fraction to [fraction], or [fraction] when there are none.
  static double snapFraction(double fraction, List<double> ticks) {
    if (ticks.isEmpty) {
      return fraction.clamp(0.0, 1.0);
    }
    double best = ticks.first;
    double bestDist = (best - fraction).abs();
    for (final double tick in ticks.skip(1)) {
      final double dist = (tick - fraction).abs();
      if (dist < bestDist) {
        best = tick;
        bestDist = dist;
      }
    }
    return best;
  }

  /// lerp.

  static double lerp(double a, double b, double t) => a + (b - a) * t;

  /// clampRangeStart.

  static double clampRangeStart(double start, double end, double min) =>
      math.max(min, math.min(start, end));

  /// clampRangeEnd.

  static double clampRangeEnd(double start, double end, double max) =>
      math.min(max, math.max(end, start));

  /// Whether [key] drives keyboard value changes (arrows, page, home/end).
  static bool isNavigationKey(LogicalKeyboardKey key) {
    return key == LogicalKeyboardKey.arrowRight ||
        key == LogicalKeyboardKey.arrowLeft ||
        key == LogicalKeyboardKey.arrowUp ||
        key == LogicalKeyboardKey.arrowDown ||
        key == LogicalKeyboardKey.pageUp ||
        key == LogicalKeyboardKey.pageDown ||
        key == LogicalKeyboardKey.home ||
        key == LogicalKeyboardKey.end;
  }

  /// Single keyboard step: one division, or 1% of the range when continuous.
  static double stepSize(double min, double max, int? divisions) {
    return keyboardStep(min, max, divisions, coarse: false);
  }

  /// Larger keyboard step: one division, or 10% of the range when continuous.
  static double pageStep(double step, int? divisions) {
    if (divisions != null && divisions > 0) {
      return step;
    }
    return step * 10;
  }

  /// Arrow step, or the Space+arrow step when [coarse] is true.
  static double keyboardStep(
    double min,
    double max,
    int? divisions, {
    required bool coarse,
  }) {
    if (divisions != null && divisions > 0) {
      return (max - min) / divisions;
    }
    final span = max - min;
    return coarse ? span / 10 : span / 100;
  }
}
