import 'package:flutter/animation.dart';

/// Shared helpers for M3E progress indicators.
abstract final class M3EProgressIndicatorUtils {
  const M3EProgressIndicatorUtils._();

  /// Evaluates one timed segment of a linear indeterminate cycle.
  ///
  /// [t] is the cycle progress in `0..1` over [totalDurationMs].
  static double evaluateIndeterminateSegment({
    required double t,
    required double delayMs,
    required double durationMs,
    required Curve easing,
    double totalDurationMs = 1750,
  }) {
    final double start = delayMs / totalDurationMs;
    final double end = (delayMs + durationMs) / totalDurationMs;
    if (t < start) {
      return 0;
    }
    if (t > end) {
      return 1;
    }
    final double localT = (t - start) / (end - start);
    return easing.transform(localT);
  }
}
