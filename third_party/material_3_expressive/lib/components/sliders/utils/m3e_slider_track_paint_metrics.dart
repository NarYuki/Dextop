import 'dart:math' as math;

/// Intermediate geometry for slider track segment painting.
class M3ESliderTrackPaintMetrics {
  /// M3ESliderTrackPaintMetrics.
  const M3ESliderTrackPaintMetrics({
    required this.sliderStart,
    required this.sliderEnd,
    required this.corner,
    required this.startGap,
    required this.endGap,
    required this.adjustedValueEnd,
    required this.adjustedValueStart,
    required this.activeStart,
    required this.activeEnd,
  });

  /// sliderStart.
  final double sliderStart;

  /// sliderEnd.
  final double sliderEnd;

  /// corner.
  final double corner;

  /// startGap.
  final double startGap;

  /// endGap.
  final double endGap;

  /// adjustedValueEnd.
  final double adjustedValueEnd;

  /// adjustedValueStart.
  final double adjustedValueStart;

  /// activeStart.
  final double activeStart;

  /// activeEnd.
  final double activeEnd;

  /// Builds metrics from raw track fractions and gaps.
  factory M3ESliderTrackPaintMetrics.from({
    required double sliderStart,
    required double sliderEnd,
    required double span,
    required double trackCross,
    required bool centered,
    required bool range,
    required double handleThickness,
    required double handleGap,
    required double activeStartFraction,
    required double activeEndFraction,
    required double cornerRadius,
  }) {
    final double corner = math.min(cornerRadius, trackCross / 2);
    final double startGap = (centered || range)
        ? handleThickness / 2 + handleGap
        : 0;
    final double endGap = handleThickness / 2 + handleGap;

    final double valueStart =
        sliderStart + span * activeStartFraction.clamp(0.0, 1.0);
    final double valueEnd =
        sliderStart + span * activeEndFraction.clamp(0.0, 1.0);
    final double centerAxis = (sliderStart + sliderEnd) / 2;

    final double adjustedValueEnd = centered
        ? math.min(valueEnd, centerAxis)
        : valueStart;
    final double adjustedValueStart = centered
        ? math.max(valueEnd, centerAxis)
        : valueEnd;

    final double activeStart = centered
        ? adjustedValueEnd + (adjustedValueEnd < centerAxis ? startGap : 0)
        : range
        ? valueStart + startGap
        : sliderStart;
    final double activeEnd = centered
        ? adjustedValueStart - (adjustedValueStart > centerAxis ? endGap : 0)
        : valueEnd - endGap;

    return M3ESliderTrackPaintMetrics(
      sliderStart: sliderStart,
      sliderEnd: sliderEnd,
      corner: corner,
      startGap: startGap,
      endGap: endGap,
      adjustedValueEnd: adjustedValueEnd,
      adjustedValueStart: adjustedValueStart,
      activeStart: activeStart,
      activeEnd: activeEnd,
    );
  }
}
