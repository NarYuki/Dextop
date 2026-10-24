import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/sliders/m3e_range_slider.dart'
    show M3ERangeSlider;
import 'package:material_3_expressive/components/sliders/m3e_sliders.dart'
    show M3ERangeSlider;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ERangeSlider;

import '../enums/m3e_slider_enums.dart';
import '../styles/m3e_slider_theme.dart';
import 'm3e_slider_track_painter.dart';

/// Default expressive track for [M3ERangeSlider].
class M3ERangeSliderTrack extends StatelessWidget {
  /// M3ERangeSliderTrack.
  const M3ERangeSliderTrack({
    required this.startFraction,
    required this.endFraction,
    required this.tickFractions,
    required this.colors,
    required this.theme,
    required this.axis,
    required this.textDirection,
    required this.handleThickness,
    this.trackHeight,
    this.cornerRadius,
    this.stopIndicatorSize,
    this.tickSize,
    this.edgeInset,
    this.drawDots = true,
    this.isWavy = false,
    this.waveAmplitude = 0,
    this.wavelength = 40,
    this.phase = 0,
    this.amplitudeFactor = 1,
    super.key,
  });

  /// startFraction.

  final double startFraction;

  /// endFraction.
  final double endFraction;

  /// tickFractions.
  final List<double> tickFractions;

  /// colors.
  final M3ESliderColors colors;

  /// theme.
  final M3ESliderTheme theme;

  /// axis.
  final Axis axis;

  /// textDirection.
  final TextDirection textDirection;

  /// handleThickness.
  final double handleThickness;

  /// trackHeight.
  final double? trackHeight;

  /// Outer corner radius. Defaults to [M3ESliderTheme.trackCornerRadius].
  final double? cornerRadius;

  /// stopIndicatorSize.
  final double? stopIndicatorSize;

  /// tickSize.
  final double? tickSize;

  /// edgeInset.
  final double? edgeInset;

  /// drawDots.
  final bool drawDots;

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

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: M3ESliderTrackPainter(
        mode: M3ESliderPaintMode.range,
        trackKind: M3ESliderTrackKind.standard,
        activeStartFraction: startFraction,
        activeEndFraction: endFraction,
        tickFractions: tickFractions,
        colors: colors,
        trackHeight: trackHeight ?? theme.trackHeight,
        handleGap: theme.handleGap,
        handleThickness: handleThickness,
        insideCornerSize: theme.trackInsideCornerSize,
        cornerRadius: cornerRadius ?? theme.trackCornerRadius,
        stopIndicatorSize: stopIndicatorSize ?? theme.stopIndicatorSize,
        tickSize: tickSize ?? theme.tickSize,
        edgeInset: edgeInset ?? theme.stopIndicatorTrailingSpace,
        axis: axis,
        textDirection: textDirection,
        drawDots: drawDots,
        isWavy: isWavy,
        waveAmplitude: waveAmplitude,
        wavelength: wavelength,
        phase: phase,
        amplitudeFactor: amplitudeFactor,
      ),
      child: const SizedBox.expand(),
    );
  }
}
