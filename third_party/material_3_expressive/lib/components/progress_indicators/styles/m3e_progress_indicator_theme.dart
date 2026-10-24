import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_progress_enums.dart';

/// Measured layout values for a flat linear progress indicator.
@immutable
class M3ELinearProgressLayout {
  /// M3ELinearProgressLayout.
  const M3ELinearProgressLayout({
    required this.trackHeight,
    required this.gap,
    required this.dotDiameter,
    required this.dotOffset,
    required this.trailingMargin,
  });

  /// trackHeight.

  final double trackHeight;

  /// gap.
  final double gap;

  /// dotDiameter.
  final double dotDiameter;

  /// dotOffset.
  final double dotOffset;

  /// Host edge inset after the track/stop. Spec: ≥4dp.
  final double trailingMargin;
}

/// Theme values for linear progress indicators.
@immutable
class M3ELinearProgressTheme extends M3EThemeExtension<M3ELinearProgressTheme> {
  /// M3ELinearProgressTheme.
  const M3ELinearProgressTheme({
    this.strokeWidth = 4,
    this.trackStrokeWidth = 4,
    this.gapSize = 4,
    this.stopSize = 4,
    this.waveAmplitude = 3,
    this.determinateWavelength = 40,
    this.indeterminateWavelength = 20,
    this.wavyContainerHeight = 10,
  });

  /// defaults.

  static const M3ELinearProgressTheme defaults = M3ELinearProgressTheme();

  /// strokeWidth.

  final double strokeWidth;

  /// trackStrokeWidth.
  final double trackStrokeWidth;

  /// Track–active gap. Spec: 4dp.
  final double gapSize;

  /// Stop diameter. Spec: 4dp.
  final double stopSize;

  /// waveAmplitude.
  final double waveAmplitude;

  /// determinateWavelength.
  final double determinateWavelength;

  /// indeterminateWavelength.
  final double indeterminateWavelength;

  /// wavyContainerHeight.
  final double wavyContainerHeight;

  /// resolveFlat.

  M3ELinearProgressLayout resolveFlat(M3EProgressIndicatorSize size) =>
      switch (size) {
        M3EProgressIndicatorSize.s => const M3ELinearProgressLayout(
          trackHeight: 4,
          gap: 4,
          dotDiameter: 4,
          dotOffset: 4,
          trailingMargin: 4,
        ),
        M3EProgressIndicatorSize.m => const M3ELinearProgressLayout(
          trackHeight: 8,
          gap: 4,
          dotDiameter: 4,
          dotOffset: 2,
          trailingMargin: 4,
        ),
      };

  /// Track color. Spec: Secondary container.
  Color trackColor(M3EColorScheme scheme) => scheme.secondaryContainer;

  /// Active / stop color. Spec: Primary.
  Color activeColor(M3EColorScheme scheme) => scheme.primary;

  /// Compose wavy determinate amplitude: full mid-progress, zero near ends.
  double amplitudeForProgress(double progress) {
    if (progress <= 0.1 || progress >= 0.95) {
      return 0;
    }
    return 1;
  }

  @override
  M3ELinearProgressTheme copyWith({
    double? strokeWidth,
    double? trackStrokeWidth,
    double? gapSize,
    double? stopSize,
    double? waveAmplitude,
    double? determinateWavelength,
    double? indeterminateWavelength,
    double? wavyContainerHeight,
  }) {
    return M3ELinearProgressTheme(
      strokeWidth: strokeWidth ?? this.strokeWidth,
      trackStrokeWidth: trackStrokeWidth ?? this.trackStrokeWidth,
      gapSize: gapSize ?? this.gapSize,
      stopSize: stopSize ?? this.stopSize,
      waveAmplitude: waveAmplitude ?? this.waveAmplitude,
      determinateWavelength:
          determinateWavelength ?? this.determinateWavelength,
      indeterminateWavelength:
          indeterminateWavelength ?? this.indeterminateWavelength,
      wavyContainerHeight: wavyContainerHeight ?? this.wavyContainerHeight,
    );
  }

  @override
  M3ELinearProgressTheme lerp(M3ELinearProgressTheme? other, double t) {
    if (other is! M3ELinearProgressTheme) {
      return this;
    }
    return M3ELinearProgressTheme(
      strokeWidth: _lerp(strokeWidth, other.strokeWidth, t),
      trackStrokeWidth: _lerp(trackStrokeWidth, other.trackStrokeWidth, t),
      gapSize: _lerp(gapSize, other.gapSize, t),
      stopSize: _lerp(stopSize, other.stopSize, t),
      waveAmplitude: _lerp(waveAmplitude, other.waveAmplitude, t),
      determinateWavelength: _lerp(
        determinateWavelength,
        other.determinateWavelength,
        t,
      ),
      indeterminateWavelength: _lerp(
        indeterminateWavelength,
        other.indeterminateWavelength,
        t,
      ),
      wavyContainerHeight: _lerp(
        wavyContainerHeight,
        other.wavyContainerHeight,
        t,
      ),
    );
  }

  static double _lerp(double a, double b, double t) => a + (b - a) * t;
}

/// Theme values for circular progress indicators.
@immutable
class M3ECircularProgressTheme
    extends M3EThemeExtension<M3ECircularProgressTheme> {
  /// M3ECircularProgressTheme.
  const M3ECircularProgressTheme({
    this.defaultSize = 40,
    this.wavySize = 48,
    this.defaultStrokeWidth = 4,
    this.trackStrokeWidth = 4,
    this.gapSize = 4,
    this.waveAmplitude = 1.6,
    this.wavelength = 15,
  });

  /// defaults.

  static const M3ECircularProgressTheme defaults = M3ECircularProgressTheme();

  /// defaultSize.

  final double defaultSize;

  /// wavySize.
  final double wavySize;

  /// defaultStrokeWidth.
  final double defaultStrokeWidth;

  /// trackStrokeWidth.
  final double trackStrokeWidth;

  /// Track–active gap. Spec: 4dp.
  final double gapSize;

  /// waveAmplitude.
  final double waveAmplitude;

  /// wavelength.
  final double wavelength;

  /// trackColor.

  Color trackColor(M3EColorScheme scheme) => scheme.secondaryContainer;

  /// activeColor.

  Color activeColor(M3EColorScheme scheme) => scheme.primary;

  /// Compose wavy determinate amplitude: full mid-progress, zero near ends.
  double amplitudeForProgress(double progress) {
    if (progress <= 0.1 || progress >= 0.95) {
      return 0;
    }
    return 1;
  }

  @override
  M3ECircularProgressTheme copyWith({
    double? defaultSize,
    double? wavySize,
    double? defaultStrokeWidth,
    double? trackStrokeWidth,
    double? gapSize,
    double? waveAmplitude,
    double? wavelength,
  }) {
    return M3ECircularProgressTheme(
      defaultSize: defaultSize ?? this.defaultSize,
      wavySize: wavySize ?? this.wavySize,
      defaultStrokeWidth: defaultStrokeWidth ?? this.defaultStrokeWidth,
      trackStrokeWidth: trackStrokeWidth ?? this.trackStrokeWidth,
      gapSize: gapSize ?? this.gapSize,
      waveAmplitude: waveAmplitude ?? this.waveAmplitude,
      wavelength: wavelength ?? this.wavelength,
    );
  }

  @override
  M3ECircularProgressTheme lerp(M3ECircularProgressTheme? other, double t) {
    if (other is! M3ECircularProgressTheme) {
      return this;
    }
    return M3ECircularProgressTheme(
      defaultSize: _lerp(defaultSize, other.defaultSize, t),
      wavySize: _lerp(wavySize, other.wavySize, t),
      defaultStrokeWidth: _lerp(
        defaultStrokeWidth,
        other.defaultStrokeWidth,
        t,
      ),
      trackStrokeWidth: _lerp(trackStrokeWidth, other.trackStrokeWidth, t),
      gapSize: _lerp(gapSize, other.gapSize, t),
      waveAmplitude: _lerp(waveAmplitude, other.waveAmplitude, t),
      wavelength: _lerp(wavelength, other.wavelength, t),
    );
  }

  static double _lerp(double a, double b, double t) => a + (b - a) * t;
}

/// Theme values for progress indicator widgets.
@immutable
class M3EProgressIndicatorTheme
    extends M3EThemeExtension<M3EProgressIndicatorTheme> {
  /// M3EProgressIndicatorTheme.
  const M3EProgressIndicatorTheme({
    this.linear = M3ELinearProgressTheme.defaults,
    this.circular = M3ECircularProgressTheme.defaults,
  });

  /// defaults.

  static const M3EProgressIndicatorTheme defaults = M3EProgressIndicatorTheme();

  /// linear.

  final M3ELinearProgressTheme linear;

  /// circular.
  final M3ECircularProgressTheme circular;

  @override
  M3EProgressIndicatorTheme copyWith({
    M3ELinearProgressTheme? linear,
    M3ECircularProgressTheme? circular,
  }) {
    return M3EProgressIndicatorTheme(
      linear: linear ?? this.linear,
      circular: circular ?? this.circular,
    );
  }

  @override
  M3EProgressIndicatorTheme lerp(M3EProgressIndicatorTheme? other, double t) {
    if (other is! M3EProgressIndicatorTheme) {
      return this;
    }
    return M3EProgressIndicatorTheme(
      linear: linear.lerp(other.linear, t),
      circular: circular.lerp(other.circular, t),
    );
  }
}
