import 'dart:math' as math;

import 'package:flutter/widgets.dart';

/// Paints the track and active arc of a classic circular progress indicator.
class M3ECircularProgressPainter extends CustomPainter {
  /// M3ECircularProgressPainter.
  const M3ECircularProgressPainter({
    required this.trackColor,
    required this.activeColor,
    required this.strokeWidth,
    required this.trackStrokeWidth,
    required this.startAngle,
    required this.sweepAngle,
    required this.gapSize,
    this.progress,
    this.showTrack = true,
  });

  /// trackColor.

  final Color trackColor;

  /// activeColor.
  final Color activeColor;

  /// strokeWidth.
  final double strokeWidth;

  /// trackStrokeWidth.
  final double trackStrokeWidth;

  /// startAngle.
  final double startAngle;

  /// sweepAngle.
  final double sweepAngle;

  /// gapSize.
  final double gapSize;

  /// When non-null, paints determinate dual-gap track. Null = indeterminate.
  final double? progress;

  /// When false, omits track painting.
  final bool showTrack;

  @override
  void paint(Canvas canvas, Size size) {
    final Offset center = size.center(Offset.zero);
    final double maxStroke = math.max(strokeWidth, trackStrokeWidth);
    final double radius = (size.shortestSide - maxStroke) / 2;
    final rect = Rect.fromCircle(center: center, radius: radius);

    final trackPaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = trackStrokeWidth
      ..strokeCap = StrokeCap.round
      ..color = trackColor;

    final activePaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round
      ..color = activeColor;

    final double? p = progress;
    const double tau = 2 * math.pi;
    final double adjustedGap = gapSize + (strokeWidth + trackStrokeWidth) / 2;
    final double gapAngle = adjustedGap / radius;

    if (p == null) {
      // Indeterminate: spinning active arc with the same front/back gaps.
      final double activeSweep = sweepAngle.clamp(0.0, tau);
      final double appliedGap = math.min(activeSweep, gapAngle);
      final double trackStart = startAngle + activeSweep + appliedGap;
      final double trackSweep = tau - activeSweep - appliedGap * 2;

      if (showTrack && trackSweep > 0) {
        canvas.drawArc(rect, trackStart, trackSweep, false, trackPaint);
      }
      if (activeSweep > 0) {
        canvas.drawArc(rect, startAngle, activeSweep, false, activePaint);
      }
      return;
    }

    // Determinate: active = progress * full circle.
    // Track uses two gaps (front + back). At 100% active is a complete ring.
    final double coerced = p.clamp(0.0, 1.0);
    final double activeSweep = coerced * tau;

    if (coerced >= 1.0) {
      canvas.drawArc(rect, startAngle, tau, false, activePaint);
      return;
    }

    final double appliedGap = math.min(activeSweep, gapAngle);
    final double trackStart = startAngle + activeSweep + appliedGap;
    final double trackSweep = tau - activeSweep - appliedGap * 2;

    if (showTrack && trackSweep > 0) {
      canvas.drawArc(rect, trackStart, trackSweep, false, trackPaint);
    }
    if (activeSweep > 0) {
      canvas.drawArc(rect, startAngle, activeSweep, false, activePaint);
    }
  }

  @override
  bool shouldRepaint(M3ECircularProgressPainter oldDelegate) {
    return oldDelegate.startAngle != startAngle ||
        oldDelegate.sweepAngle != sweepAngle ||
        oldDelegate.activeColor != activeColor ||
        oldDelegate.trackColor != trackColor ||
        oldDelegate.strokeWidth != strokeWidth ||
        oldDelegate.trackStrokeWidth != trackStrokeWidth ||
        oldDelegate.gapSize != gapSize ||
        oldDelegate.progress != progress ||
        oldDelegate.showTrack != showTrack;
  }

  /// A full turn in radians.
  static const double tau = 2 * math.pi;
}
