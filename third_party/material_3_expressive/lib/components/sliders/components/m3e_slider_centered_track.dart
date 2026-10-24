import 'package:flutter/widgets.dart';

import '../enums/m3e_slider_enums.dart';
import '../styles/m3e_slider_theme.dart';
import 'm3e_slider_track.dart';

/// Centered active-track variant — active fill grows from the midpoint.
class M3ESliderCenteredTrack extends StatelessWidget {
  /// M3ESliderCenteredTrack.
  const M3ESliderCenteredTrack({
    required this.fraction,
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

  /// fraction.

  final double fraction;

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
    return M3ESliderTrack(
      fraction: fraction,
      tickFractions: tickFractions,
      colors: colors,
      theme: theme,
      axis: axis,
      textDirection: textDirection,
      handleThickness: handleThickness,
      trackKind: M3ESliderTrackKind.centered,
      trackHeight: trackHeight,
      cornerRadius: cornerRadius,
      stopIndicatorSize: stopIndicatorSize,
      tickSize: tickSize,
      edgeInset: edgeInset,
      drawDots: drawDots,
      isWavy: isWavy,
      waveAmplitude: waveAmplitude,
      wavelength: wavelength,
      phase: phase,
      amplitudeFactor: amplitudeFactor,
    );
  }
}
