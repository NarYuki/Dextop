import 'dart:math' as math;

import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/sliders/m3e_sliders.dart'
    show M3ESliderDotBuilder, M3ESliderThumb;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ESliderDotBuilder, M3ESliderThumb;

import '../enums/m3e_slider_enums.dart';
import '../res/m3e_slider_tokens.dart';
import '../styles/m3e_slider_theme.dart';
import '../utils/m3e_slider_dot_layout.dart';
import '../utils/m3e_slider_track_paint_metrics.dart';

/// Paints expressive track segments, stop indicators, and discrete ticks.
///
/// The thumb is painted separately by [M3ESliderThumb] so it can animate.
/// When [isWavy] is true, the active value segment is a traveling sine wave
/// (same recipe as linear wavy progress); inactive segments stay flat.
class M3ESliderTrackPainter extends CustomPainter {
  /// M3ESliderTrackPainter.
  const M3ESliderTrackPainter({
    required this.mode,
    required this.trackKind,
    required this.activeStartFraction,
    required this.activeEndFraction,
    required this.tickFractions,
    required this.colors,
    required this.trackHeight,
    required this.handleGap,
    required this.handleThickness,
    required this.insideCornerSize,
    required this.cornerRadius,
    required this.stopIndicatorSize,
    required this.tickSize,
    required this.axis,
    required this.textDirection,
    this.drawDots = true,
    this.edgeInset,
    this.isWavy = false,
    this.waveAmplitude = 0,
    this.wavelength = 40,
    this.phase = 0,
    this.amplitudeFactor = 1,
  });

  /// mode.

  final M3ESliderPaintMode mode;

  /// trackKind.
  final M3ESliderTrackKind trackKind;

  /// activeStartFraction.
  final double activeStartFraction;

  /// activeEndFraction.
  final double activeEndFraction;

  /// tickFractions.
  final List<double> tickFractions;

  /// colors.
  final M3ESliderColors colors;

  /// trackHeight.
  final double trackHeight;

  /// handleGap.
  final double handleGap;

  /// handleThickness.
  final double handleThickness;

  /// insideCornerSize.
  final double insideCornerSize;

  /// Outer corner radius for track ends (clamped to half track thickness).
  final double cornerRadius;

  /// stopIndicatorSize.
  final double stopIndicatorSize;

  /// tickSize.
  final double tickSize;

  /// axis.
  final Axis axis;

  /// textDirection.
  final TextDirection textDirection;

  /// When false, skip stop indicators and discrete ticks (custom overlay owns
  /// them via a [M3ESliderDotBuilder]).
  final bool drawDots;

  /// Inset of stop/tick markers from each track end. Defaults to
  /// [M3ESliderTokens.stopIndicatorTrailingSpace] when null.
  final double? edgeInset;

  /// isWavy.
  final bool isWavy;

  /// waveAmplitude.
  final double waveAmplitude;

  /// wavelength.
  final double wavelength;

  /// phase.
  final double phase;

  /// amplitudeFactor.
  final double amplitudeFactor;

  bool get _vertical => axis == Axis.vertical;
  bool get _rtl => !_vertical && textDirection == TextDirection.rtl;
  bool get _centered =>
      mode == M3ESliderPaintMode.single &&
      trackKind == M3ESliderTrackKind.centered;
  bool get _range => mode == M3ESliderPaintMode.range;

  @override
  void paint(Canvas canvas, Size size) {
    final double trackCross = trackHeight;
    final Rect trackBounds = _trackBounds(size, trackCross);
    final double sliderStart = _primaryMin(trackBounds);
    final double sliderEnd = _primaryMax(trackBounds);
    final double span = sliderEnd - sliderStart;
    if (span <= 0) {
      return;
    }

    final metrics = M3ESliderTrackPaintMetrics.from(
      sliderStart: sliderStart,
      sliderEnd: sliderEnd,
      span: span,
      trackCross: trackCross,
      centered: _centered,
      range: _range,
      handleThickness: handleThickness,
      handleGap: handleGap,
      activeStartFraction: activeStartFraction,
      activeEndFraction: activeEndFraction,
      cornerRadius: cornerRadius,
    );

    _paintInactiveLeading(canvas, trackBounds, metrics);
    _paintInactiveTrailing(canvas, trackBounds, metrics);
    _paintActive(canvas, trackBounds, metrics);
    if (drawDots) {
      _paintDots(canvas, size, trackBounds);
    }
  }

  void _paintInactiveLeading(
    Canvas canvas,
    Rect trackBounds,
    M3ESliderTrackPaintMetrics metrics,
  ) {
    if (!(_centered || _range) ||
        metrics.adjustedValueEnd <=
            metrics.sliderStart + metrics.startGap + metrics.corner) {
      return;
    }
    final start = metrics.sliderStart;
    final double end = metrics.adjustedValueEnd - metrics.startGap;
    if (end <= start) {
      return;
    }
    _drawSegment(
      canvas,
      trackBounds,
      start,
      end,
      colors.inactiveTrack,
      startCorner: _rtl ? insideCornerSize : metrics.corner,
      endCorner: _rtl ? metrics.corner : insideCornerSize,
    );
  }

  void _paintInactiveTrailing(
    Canvas canvas,
    Rect trackBounds,
    M3ESliderTrackPaintMetrics metrics,
  ) {
    if (metrics.adjustedValueStart >=
        metrics.sliderEnd - metrics.endGap - metrics.corner) {
      return;
    }
    final double start = metrics.adjustedValueStart + metrics.endGap;
    final end = metrics.sliderEnd;
    if (end <= start) {
      return;
    }
    _drawSegment(
      canvas,
      trackBounds,
      start,
      end,
      colors.inactiveTrack,
      startCorner: _rtl ? metrics.corner : insideCornerSize,
      endCorner: _rtl ? insideCornerSize : metrics.corner,
    );
  }

  void _paintActive(
    Canvas canvas,
    Rect trackBounds,
    M3ESliderTrackPaintMetrics metrics,
  ) {
    final double activeStart = metrics.activeStart;
    final double activeEnd = metrics.activeEnd;
    final bool filledReachesEnd =
        metrics.adjustedValueStart >=
        metrics.sliderEnd - metrics.endGap - metrics.corner;
    final bool filledReachesStart =
        metrics.adjustedValueEnd <=
        metrics.sliderStart + metrics.startGap + metrics.corner;
    var startCorner = (_rtl || _centered || _range)
        ? insideCornerSize
        : metrics.corner;
    var endCorner = (_rtl && !_centered && !_range)
        ? metrics.corner
        : insideCornerSize;
    // At a track end the filled cap uses the outer corner, same as the far edge.
    if (filledReachesEnd && endCorner == insideCornerSize) {
      endCorner = metrics.corner;
    }
    if (filledReachesStart &&
        (_centered || _range) &&
        startCorner == insideCornerSize) {
      startCorner = metrics.corner;
    }
    if (activeEnd - activeStart <= startCorner) {
      return;
    }
    if (isWavy) {
      _drawWavyActive(
        canvas,
        trackBounds,
        activeStart,
        activeEnd,
        colors.activeTrack,
      );
      return;
    }
    _drawSegment(
      canvas,
      trackBounds,
      activeStart,
      activeEnd,
      colors.activeTrack,
      startCorner: startCorner,
      endCorner: endCorner,
    );
  }

  void _paintDots(Canvas canvas, Size size, Rect trackBounds) {
    final List<M3ESliderDotPlacement> dots = M3ESliderDotLayout.resolve(
      size: size,
      mode: mode,
      trackKind: trackKind,
      activeStartFraction: activeStartFraction,
      activeEndFraction: activeEndFraction,
      tickFractions: tickFractions,
      colors: colors,
      trackHeight: trackHeight,
      handleGap: handleGap,
      handleThickness: handleThickness,
      stopIndicatorSize: stopIndicatorSize,
      tickSize: tickSize,
      edgeInset: edgeInset ?? M3ESliderTokens.stopIndicatorTrailingSpace,
      axis: axis,
      textDirection: textDirection,
    );
    for (final dot in dots) {
      _drawStop(canvas, trackBounds, dot.primary, dot.color, size: dot.size);
    }
  }

  Rect _trackBounds(Size size, double trackCross) {
    if (_vertical) {
      final double left = (size.width - trackCross) / 2;
      return Rect.fromLTWH(left, 0, trackCross, size.height);
    }
    final double top = (size.height - trackCross) / 2;
    return Rect.fromLTWH(0, top, size.width, trackCross);
  }

  double _primaryMin(Rect bounds) => _vertical ? bounds.top : bounds.left;
  double _primaryMax(Rect bounds) => _vertical ? bounds.bottom : bounds.right;

  void _drawSegment(
    Canvas canvas,
    Rect trackBounds,
    double start,
    double end,
    Color color, {
    required double startCorner,
    required double endCorner,
  }) {
    if (end <= start) {
      return;
    }
    final RRect rrect;
    if (_vertical) {
      rrect = RRect.fromRectAndCorners(
        Rect.fromLTRB(trackBounds.left, start, trackBounds.right, end),
        topLeft: Radius.circular(startCorner),
        topRight: Radius.circular(startCorner),
        bottomLeft: Radius.circular(endCorner),
        bottomRight: Radius.circular(endCorner),
      );
    } else if (_rtl) {
      rrect = RRect.fromRectAndCorners(
        Rect.fromLTRB(start, trackBounds.top, end, trackBounds.bottom),
        topLeft: Radius.circular(startCorner),
        bottomLeft: Radius.circular(startCorner),
        topRight: Radius.circular(endCorner),
        bottomRight: Radius.circular(endCorner),
      );
    } else {
      rrect = RRect.fromRectAndCorners(
        Rect.fromLTRB(start, trackBounds.top, end, trackBounds.bottom),
        topLeft: Radius.circular(startCorner),
        bottomLeft: Radius.circular(startCorner),
        topRight: Radius.circular(endCorner),
        bottomRight: Radius.circular(endCorner),
      );
    }
    canvas.drawRRect(rrect, Paint()..color = color);
  }

  /// Active value as a traveling sine wave (linear wavy progress recipe).
  ///
  /// Path endpoints are inset by half the stroke so [StrokeCap.round] tips sit
  /// flush with [start]/[end] — the same outer edges as the flat RRect, which
  /// keeps the handle ↔ track gap identical to non-wavy sliders.
  void _drawWavyActive(
    Canvas canvas,
    Rect trackBounds,
    double start,
    double end,
    Color color,
  ) {
    if (end <= start || wavelength <= 0) {
      return;
    }
    final double halfStroke = trackHeight / 2;
    final double pathStart = start + halfStroke;
    final double pathEnd = end - halfStroke;
    if (pathEnd <= pathStart) {
      return;
    }

    final double amp = waveAmplitude * amplitudeFactor.clamp(0.0, 1.0);
    final double crossCenter = _vertical
        ? trackBounds.center.dx
        : trackBounds.center.dy;
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeWidth = trackHeight
      ..isAntiAlias = true
      ..color = color;

    final path = Path();
    const step = 1.5;
    final double k = 2 * math.pi / wavelength;

    // Phase is anchored to [start] (pre-inset) so travel stays stable as the
    // thumb moves and the visible segment length changes.
    double crossAt(double primary) {
      return crossCenter + amp * math.sin(phase + (primary - start) * k);
    }

    if (_vertical) {
      var y = pathStart;
      path.moveTo(crossAt(y), y);
      for (y = pathStart + step; y <= pathEnd; y += step) {
        path.lineTo(crossAt(y), y);
      }
      path.lineTo(crossAt(pathEnd), pathEnd);
    } else {
      var x = pathStart;
      path.moveTo(x, crossAt(x));
      for (x = pathStart + step; x <= pathEnd; x += step) {
        path.lineTo(x, crossAt(x));
      }
      path.lineTo(pathEnd, crossAt(pathEnd));
    }
    canvas.drawPath(path, paint);
  }

  void _drawStop(
    Canvas canvas,
    Rect trackBounds,
    double primary,
    Color color, {
    double? size,
  }) {
    final double diameter = size ?? stopIndicatorSize;
    final center = _vertical
        ? Offset(trackBounds.center.dx, primary)
        : Offset(primary, trackBounds.center.dy);
    canvas.drawCircle(center, diameter / 2, Paint()..color = color);
  }

  @override
  bool shouldRepaint(covariant M3ESliderTrackPainter oldDelegate) {
    return oldDelegate.mode != mode ||
        oldDelegate.trackKind != trackKind ||
        oldDelegate.activeStartFraction != activeStartFraction ||
        oldDelegate.activeEndFraction != activeEndFraction ||
        oldDelegate.tickFractions != tickFractions ||
        oldDelegate.colors != colors ||
        oldDelegate.trackHeight != trackHeight ||
        oldDelegate.handleGap != handleGap ||
        oldDelegate.handleThickness != handleThickness ||
        oldDelegate.insideCornerSize != insideCornerSize ||
        oldDelegate.cornerRadius != cornerRadius ||
        oldDelegate.stopIndicatorSize != stopIndicatorSize ||
        oldDelegate.tickSize != tickSize ||
        oldDelegate.axis != axis ||
        oldDelegate.textDirection != textDirection ||
        oldDelegate.drawDots != drawDots ||
        oldDelegate.edgeInset != edgeInset ||
        oldDelegate.isWavy != isWavy ||
        oldDelegate.waveAmplitude != waveAmplitude ||
        oldDelegate.wavelength != wavelength ||
        oldDelegate.phase != phase ||
        oldDelegate.amplitudeFactor != amplitudeFactor;
  }
}
