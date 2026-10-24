import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../enums/m3e_slider_enums.dart';

/// Shared track geometry used by stop/tick placement.
@immutable
class M3ESliderDotGeometry {
  /// M3ESliderDotGeometry.
  const M3ESliderDotGeometry({
    required this.sliderStart,
    required this.sliderEnd,
    required this.span,
    required this.centered,
    required this.range,
    required this.startGap,
    required this.endGap,
    required this.valueStart,
    required this.valueEnd,
    required this.centerAxis,
    required this.activeStart,
    required this.activeEnd,
  });

  /// sliderStart.
  final double sliderStart;

  /// sliderEnd.
  final double sliderEnd;

  /// span.
  final double span;

  /// centered.
  final bool centered;

  /// range.
  final bool range;

  /// startGap.
  final double startGap;

  /// endGap.
  final double endGap;

  /// valueStart.
  final double valueStart;

  /// valueEnd.
  final double valueEnd;

  /// centerAxis.
  final double centerAxis;

  /// activeStart.
  final double activeStart;

  /// activeEnd.
  final double activeEnd;

  /// Resolves geometry for the given paint mode and track kind.
  static M3ESliderDotGeometry? resolve({
    required Size size,
    required M3ESliderPaintMode mode,
    required M3ESliderTrackKind trackKind,
    required double activeStartFraction,
    required double activeEndFraction,
    required double trackHeight,
    required double handleGap,
    required double handleThickness,
    required Axis axis,
  }) {
    final vertical = axis == Axis.vertical;
    final bool centered =
        mode == M3ESliderPaintMode.single &&
        trackKind == M3ESliderTrackKind.centered;
    final range = mode == M3ESliderPaintMode.range;

    final Rect trackBounds = _trackBounds(size, trackHeight, vertical);
    final double sliderStart = vertical ? trackBounds.top : trackBounds.left;
    final double sliderEnd = vertical ? trackBounds.bottom : trackBounds.right;
    final double span = sliderEnd - sliderStart;
    if (span <= 0) {
      return null;
    }

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

    return M3ESliderDotGeometry(
      sliderStart: sliderStart,
      sliderEnd: sliderEnd,
      span: span,
      centered: centered,
      range: range,
      startGap: startGap,
      endGap: endGap,
      valueStart: valueStart,
      valueEnd: valueEnd,
      centerAxis: centerAxis,
      activeStart: activeStart,
      activeEnd: activeEnd,
    );
  }

  static Rect _trackBounds(Size size, double trackCross, bool vertical) {
    if (vertical) {
      final double left = (size.width - trackCross) / 2;
      return Rect.fromLTWH(left, 0, trackCross, size.height);
    }
    final double top = (size.height - trackCross) / 2;
    return Rect.fromLTWH(0, top, size.width, trackCross);
  }
}
