import 'dart:math' as math;

import 'package:flutter/widgets.dart';

/// Paints a Compose-style circular wavy progress ring with front and back gaps.
class M3ECircularWavyProgressPainter extends CustomPainter {
  /// M3ECircularWavyProgressPainter.
  const M3ECircularWavyProgressPainter({
    required this.progress,
    required this.activeColor,
    required this.trackColor,
    required this.strokeWidth,
    required this.trackStrokeWidth,
    required this.gapSize,
    required this.amplitudeFactor,
    required this.maxAmplitude,
    required this.wavelength,
    required this.phase,
    this.globalRotation = 0,
    this.additionalRotation = 0,
    this.sweepFraction = 0.5,
    this.showTrack = true,
  });

  /// Null means indeterminate.
  final double? progress;

  /// activeColor.
  final Color activeColor;

  /// trackColor.
  final Color trackColor;

  /// strokeWidth.
  final double strokeWidth;

  /// trackStrokeWidth.
  final double trackStrokeWidth;

  /// gapSize.
  final double gapSize;

  /// amplitudeFactor.
  final double amplitudeFactor;

  /// maxAmplitude.
  final double maxAmplitude;

  /// wavelength.
  final double wavelength;

  /// phase.
  final double phase;

  /// Indeterminate: 0→1 maps to 0→1080°.
  final double globalRotation;

  /// Indeterminate: 0→1 maps to 0→360°.
  final double additionalRotation;

  /// Indeterminate active sweep as a fraction of the full circle.
  final double sweepFraction;

  /// When false, omits track painting.
  final bool showTrack;

  /// Minimum indeterminate sweep as a fraction of the full circle.
  static const double minSweep = 0.10;

  /// Maximum indeterminate sweep as a fraction of the full circle.
  static const double maxSweep = 0.87;

  /// Degrees of canvas rotation per full [globalRotation] cycle.
  static const double globalRotDeg = 1080;

  /// Degrees of canvas rotation per full [additionalRotation] cycle.
  static const double additionalRotDeg = 360;

  @override
  void paint(Canvas canvas, Size size) {
    final Offset center = size.center(Offset.zero);
    final double maxStroke = math.max(strokeWidth, trackStrokeWidth);
    final double amplitude = maxAmplitude * amplitudeFactor.clamp(0.0, 1.0);
    final double radius = (size.shortestSide - maxStroke) / 2 - amplitude;
    if (radius <= 0) {
      return;
    }
    const double tau = 2 * math.pi;
    final double waveK =
        math.max(1, ((tau * radius) / wavelength).round()) *
        tau /
        (tau * radius);
    final double gapAngle =
        (gapSize + (strokeWidth + trackStrokeWidth) / 2) / radius;
    const double startAngle = -math.pi / 2;
    final Paint trackPaint = _strokePaint(trackColor, trackStrokeWidth);
    final Paint activePaint = _strokePaint(activeColor, strokeWidth);

    if (progress == null) {
      _paintIndeterminate(
        canvas,
        center: center,
        radius: radius,
        startAngle: startAngle,
        tau: tau,
        gapAngle: gapAngle,
        amplitude: amplitude,
        waveK: waveK,
        trackPaint: trackPaint,
        activePaint: activePaint,
      );
      return;
    }
    _paintDeterminate(
      canvas,
      center: center,
      radius: radius,
      startAngle: startAngle,
      tau: tau,
      gapAngle: gapAngle,
      amplitude: amplitude,
      waveK: waveK,
      trackPaint: trackPaint,
      activePaint: activePaint,
    );
  }

  void _paintIndeterminate(
    Canvas canvas, {
    required Offset center,
    required double radius,
    required double startAngle,
    required double tau,
    required double gapAngle,
    required double amplitude,
    required double waveK,
    required Paint trackPaint,
    required Paint activePaint,
  }) {
    final double totalDeg =
        globalRotation * globalRotDeg + additionalRotation * additionalRotDeg;
    final double totalRad = totalDeg * math.pi / 180;

    canvas
      ..save()
      ..translate(center.dx, center.dy)
      ..rotate(totalRad)
      ..translate(-center.dx, -center.dy);

    final double progressSweep = sweepFraction.clamp(minSweep, maxSweep) * tau;
    final double appliedGap = math.min(progressSweep, gapAngle);
    final double trackSweep = tau - progressSweep - appliedGap * 2;

    if (showTrack && trackSweep > 0) {
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        startAngle + progressSweep + appliedGap,
        trackSweep,
        false,
        trackPaint,
      );
    }
    if (progressSweep > 0) {
      _drawWavy(
        canvas,
        center: center,
        radius: radius,
        startAngle: startAngle,
        sweepAngle: progressSweep,
        amplitude: amplitude,
        waveK: waveK,
        paint: activePaint,
      );
    }
    canvas.restore();
  }

  void _paintDeterminate(
    Canvas canvas, {
    required Offset center,
    required double radius,
    required double startAngle,
    required double tau,
    required double gapAngle,
    required double amplitude,
    required double waveK,
    required Paint trackPaint,
    required Paint activePaint,
  }) {
    final double p = progress!.clamp(0.0, 1.0);
    final double activeSweep = p * tau;
    if (p >= 1.0) {
      _drawWavy(
        canvas,
        center: center,
        radius: radius,
        startAngle: startAngle,
        sweepAngle: tau,
        amplitude: amplitude,
        waveK: waveK,
        paint: activePaint,
      );
      return;
    }
    final double appliedGap = math.min(activeSweep, gapAngle);
    final double trackSweep = tau - activeSweep - appliedGap * 2;
    if (showTrack && trackSweep > 0) {
      // Determinate track stays a flat arc (amplitude 0 via wavy helper).
      _drawWavy(
        canvas,
        center: center,
        radius: radius,
        startAngle: startAngle + activeSweep + appliedGap,
        sweepAngle: trackSweep,
        amplitude: 0,
        waveK: waveK,
        paint: trackPaint,
      );
    }
    if (activeSweep > 0) {
      _drawWavy(
        canvas,
        center: center,
        radius: radius,
        startAngle: startAngle,
        sweepAngle: activeSweep,
        amplitude: amplitude,
        waveK: waveK,
        paint: activePaint,
      );
    }
  }

  Paint _strokePaint(Color color, double width) {
    return Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = width
      ..strokeCap = StrokeCap.round
      ..color = color
      ..isAntiAlias = true;
  }

  void _drawWavy(
    Canvas canvas, {
    required Offset center,
    required double radius,
    required double startAngle,
    required double sweepAngle,
    required double amplitude,
    required double waveK,
    required Paint paint,
  }) {
    canvas.drawPath(
      _wavyArc(
        center: center,
        radius: radius,
        startAngle: startAngle,
        sweepAngle: sweepAngle,
        amplitude: amplitude,
        waveK: waveK,
        phase: phase,
      ),
      paint,
    );
  }

  Path _wavyArc({
    required Offset center,
    required double radius,
    required double startAngle,
    required double sweepAngle,
    required double amplitude,
    required double waveK,
    required double phase,
  }) {
    final path = Path();
    const steps = 120;
    for (var i = 0; i <= steps; i++) {
      final double t = i / steps;
      final double angle = startAngle + sweepAngle * t;
      final double arcLength = radius * (angle - startAngle).abs();
      final double r = radius + amplitude * math.sin(phase + arcLength * waveK);
      final point = Offset(
        center.dx + r * math.cos(angle),
        center.dy + r * math.sin(angle),
      );
      if (i == 0) {
        path.moveTo(point.dx, point.dy);
      } else {
        path.lineTo(point.dx, point.dy);
      }
    }
    return path;
  }

  @override
  bool shouldRepaint(M3ECircularWavyProgressPainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.activeColor != activeColor ||
        oldDelegate.trackColor != trackColor ||
        oldDelegate.strokeWidth != strokeWidth ||
        oldDelegate.trackStrokeWidth != trackStrokeWidth ||
        oldDelegate.gapSize != gapSize ||
        oldDelegate.amplitudeFactor != amplitudeFactor ||
        oldDelegate.maxAmplitude != maxAmplitude ||
        oldDelegate.wavelength != wavelength ||
        oldDelegate.phase != phase ||
        oldDelegate.globalRotation != globalRotation ||
        oldDelegate.additionalRotation != additionalRotation ||
        oldDelegate.sweepFraction != sweepFraction ||
        oldDelegate.showTrack != showTrack;
  }
}
