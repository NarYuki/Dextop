import 'dart:math' as math;
import 'dart:ui' show lerpDouble;

import 'package:flutter/semantics.dart';
import 'package:flutter/widgets.dart';

import '../../foundations/foundations.dart';
import 'components/m3e_circular_progress_painter.dart';
import 'components/m3e_circular_wavy_progress_painter.dart';
import 'components/m3e_linear_progress_painter.dart';
import 'enums/m3e_progress_enums.dart';
import 'styles/m3e_progress_indicator_theme.dart';

export 'enums/m3e_progress_enums.dart';
export 'styles/m3e_progress_indicator_theme.dart';
export 'utils/m3e_progress_indicator_utils.dart';

/// Which progress indicator layout an [M3EProgressIndicator] renders.
enum _M3EProgressKind { circular, circularWavy, linear, linearWavy }

/// Material 3 Expressive progress indicators.
///
/// Mirrors Compose Material 3:
/// - [M3EProgressIndicator.circular] → `CircularProgressIndicator`
/// - [M3EProgressIndicator.circularWavy] → `CircularWavyProgressIndicator`
/// - [M3EProgressIndicator.linear] → `LinearProgressIndicator`
/// - [M3EProgressIndicator.linearWavy] → `LinearWavyProgressIndicator`
///
/// Pass [value] in `0..1` for determinate progress, or leave it null for
/// indeterminate animation.
class M3EProgressIndicator extends StatefulWidget {
  /// Classic circular progress indicator.
  const M3EProgressIndicator.circular({
    super.key,
    this.value,
    this.size,
    this.strokeWidth,
    this.trackStrokeWidth,
    this.color,
    this.trackColor,
    this.showTrack = true,
    this.semanticsLabel,
    this.semanticsValue,
  }) : _kind = _M3EProgressKind.circular,
       linearSize = M3EProgressIndicatorSize.m,
       gapSize = null,
       stopSize = null,
       amplitude = null,
       amplitudeForProgress = null,
       wavelength = null,
       waveSpeed = null;

  /// Expressive circular wavy progress indicator.
  const M3EProgressIndicator.circularWavy({
    super.key,
    this.value,
    this.size,
    this.color,
    this.trackColor,
    this.strokeWidth,
    this.trackStrokeWidth,
    this.gapSize,
    this.amplitude,
    this.amplitudeForProgress,
    this.wavelength,
    this.waveSpeed,
    this.showTrack = true,
    this.semanticsLabel,
    this.semanticsValue,
  }) : _kind = _M3EProgressKind.circularWavy,
       linearSize = M3EProgressIndicatorSize.m,
       stopSize = null;

  /// Classic flat linear progress indicator.
  const M3EProgressIndicator.linear({
    super.key,
    this.value,
    this.linearSize = M3EProgressIndicatorSize.m,
    this.strokeWidth,
    this.trackStrokeWidth,
    this.color,
    this.trackColor,
    this.showTrack = true,
    this.semanticsLabel,
    this.semanticsValue,
  }) : _kind = _M3EProgressKind.linear,
       size = null,
       gapSize = null,
       stopSize = null,
       amplitude = null,
       amplitudeForProgress = null,
       wavelength = null,
       waveSpeed = null;

  /// Expressive linear wavy progress indicator.
  const M3EProgressIndicator.linearWavy({
    super.key,
    this.value,
    this.linearSize = M3EProgressIndicatorSize.m,
    this.color,
    this.trackColor,
    this.strokeWidth,
    this.trackStrokeWidth,
    this.gapSize,
    this.stopSize,
    this.amplitude,
    this.amplitudeForProgress,
    this.wavelength,
    this.waveSpeed,
    this.showTrack = true,
    this.semanticsLabel,
    this.semanticsValue,
  }) : _kind = _M3EProgressKind.linearWavy,
       size = null;

  final _M3EProgressKind _kind;

  /// Determinate progress in `0..1`, or null for indeterminate.
  final double? value;

  /// Diameter for circular variants.
  final double? size;

  /// Track thickness for linear classic size matrix.
  final M3EProgressIndicatorSize linearSize;

  /// Active indicator color.
  final Color? color;

  /// Track color.
  final Color? trackColor;

  /// Active stroke width.
  final double? strokeWidth;

  /// Track stroke width.
  final double? trackStrokeWidth;

  /// Gap between active indicator and track (wavy variants).
  final double? gapSize;

  /// End stop diameter for linear wavy.
  final double? stopSize;

  /// Fixed amplitude factor `0..1` (typically indeterminate).
  final double? amplitude;

  /// Amplitude factor as a function of progress (determinate wavy).
  final double Function(double progress)? amplitudeForProgress;

  /// Wave length in logical pixels.
  final double? wavelength;

  /// Wave travel speed in logical pixels per second.
  final double? waveSpeed;

  /// When false, omits the track (e.g. in-button use). Spec default: true.
  final bool showTrack;

  /// Accessibility label for the progress bar.
  final String? semanticsLabel;

  /// Accessibility value. Numeric / percent strings are exposed as the
  /// progressbar value; other strings become a hint. When null, determinate
  /// progress uses a percent derived from [value].
  final String? semanticsValue;

  @override
  State<M3EProgressIndicator> createState() => _M3EProgressIndicatorState();
}

class _M3EProgressIndicatorState extends State<M3EProgressIndicator>
    with TickerProviderStateMixin {
  /// Classic circular spin / shared wave phase for wavy determinate scroll.
  late final AnimationController _spinController;

  /// Linear (flat + wavy) indeterminate cycle (1750ms).
  late final AnimationController _linearIndetController;

  /// Circular wavy indeterminate rotations / sweep.
  late final AnimationController _globalRotController;
  late final AnimationController _additionalRotController;
  late final AnimationController _sweepController;
  bool _sweepExpanding = true;

  bool get _isWavy =>
      widget._kind == _M3EProgressKind.circularWavy ||
      widget._kind == _M3EProgressKind.linearWavy;

  bool get _isLinearIndet =>
      (widget._kind == _M3EProgressKind.linear ||
          widget._kind == _M3EProgressKind.linearWavy) &&
      widget.value == null;

  bool get _isCircularWavyIndet =>
      widget._kind == _M3EProgressKind.circularWavy && widget.value == null;

  bool get _isClassicCircularIndet =>
      widget._kind == _M3EProgressKind.circular && widget.value == null;

  /// Classic or wavy circular with null value — shared rot/sweep drivers.
  bool get _isCircularIndet => _isClassicCircularIndet || _isCircularWavyIndet;

  bool get _needsWavePhase => _isWavy;

  @override
  void initState() {
    super.initState();
    _spinController = AnimationController(
      vsync: this,
      duration: M3EMotion.extraLong2,
    );
    _linearIndetController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1750),
    );
    _globalRotController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 6000),
    );
    _additionalRotController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 4500),
    );
    _sweepController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1300),
    );
    _syncControllers();
  }

  @override
  void didUpdateWidget(M3EProgressIndicator oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncControllers();
  }

  void _syncControllers() {
    _syncSpinController();
    _syncLinearIndetController();
    _syncCircularIndetControllers();
  }

  void _syncSpinController() {
    // Wave phase only — classic circular indet uses rot/sweep controllers.
    if (_isWavy) {
      if (!_spinController.isAnimating) {
        _spinController.repeat();
      }
    } else if (_spinController.isAnimating) {
      _spinController.stop();
    }
  }

  void _syncLinearIndetController() {
    if (_isLinearIndet) {
      if (!_linearIndetController.isAnimating) {
        _linearIndetController.repeat();
      }
    } else if (_linearIndetController.isAnimating) {
      _linearIndetController.stop();
    }
  }

  void _syncCircularIndetControllers() {
    if (_isCircularIndet) {
      _startCircularIndeterminate();
    } else {
      _stopCircularIndeterminate();
    }
  }

  void _startCircularIndeterminate() {
    if (!_globalRotController.isAnimating) {
      _globalRotController.repeat();
    }
    if (!_additionalRotController.isAnimating) {
      _additionalRotController.repeat();
    }
    if (!_sweepController.isAnimating) {
      _sweepExpanding = true;
      _sweepController.forward(from: 0).then((_) => _pingPongSweep());
    }
  }

  void _pingPongSweep() {
    if (!mounted || !_isCircularIndet) {
      return;
    }
    _sweepExpanding = !_sweepExpanding;
    (_sweepExpanding ? _sweepController.forward() : _sweepController.reverse())
        .then((_) => _pingPongSweep());
  }

  void _stopCircularIndeterminate() {
    _globalRotController.stop();
    _additionalRotController.stop();
    _sweepController.stop();
  }

  @override
  void dispose() {
    _spinController.dispose();
    _linearIndetController.dispose();
    _globalRotController.dispose();
    _additionalRotController.dispose();
    _sweepController.dispose();
    super.dispose();
  }

  double _phase(double wavelength, double waveSpeed) {
    final Duration elapsed =
        _spinController.lastElapsedDuration ?? Duration.zero;
    final double seconds = elapsed.inMicroseconds / 1e6;
    if (wavelength <= 0) {
      return 0;
    }
    return seconds * waveSpeed / wavelength * 2 * math.pi;
  }

  double _amplitudeFactor({
    required double Function(double progress) themeAmplitude,
  }) {
    final double? value = widget.value;
    if (value == null) {
      return widget.amplitude ?? 1;
    }
    if (widget.amplitudeForProgress != null) {
      return widget.amplitudeForProgress!(value).clamp(0.0, 1.0);
    }
    if (widget.amplitude != null) {
      return widget.amplitude!.clamp(0.0, 1.0);
    }
    return themeAmplitude(value).clamp(0.0, 1.0);
  }

  /// Numeric / percent [semanticsValue], else determinate % or indeterminate
  /// `0%`.
  String _progressBarValue(String? semanticsValue) {
    if (semanticsValue != null && semanticsValue.isNotEmpty) {
      if (double.tryParse(semanticsValue) != null) {
        return semanticsValue;
      }
      if (semanticsValue.endsWith('%') &&
          double.tryParse(
                semanticsValue.substring(0, semanticsValue.length - 1),
              ) !=
              null) {
        return semanticsValue;
      }
      return '0%';
    }
    final double? progress = widget.value;
    if (progress != null) {
      return '${(progress.clamp(0.0, 1.0) * 100).round()}%';
    }
    return '0%';
  }

  /// Non-numeric [semanticsValue] is exposed as a hint.
  String? _progressBarHint(String? semanticsValue) {
    if (semanticsValue == null || semanticsValue.isEmpty) {
      return null;
    }
    if (_progressBarValue(semanticsValue) == semanticsValue) {
      return null;
    }
    return semanticsValue;
  }

  Widget _withSemantics(Widget child) {
    return Semantics.fromProperties(
      properties: SemanticsProperties(
        label: widget.semanticsLabel,
        role: SemanticsRole.progressBar,
        minValue: '0',
        maxValue: '100',
        value: _progressBarValue(widget.semanticsValue),
        hint: _progressBarHint(widget.semanticsValue),
      ),
      child: child,
    );
  }

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    return M3EComponentTheme(
      builder: (BuildContext context) {
        return _withSemantics(switch (widget._kind) {
          _M3EProgressKind.circular => _buildCircular(theme),
          _M3EProgressKind.circularWavy => _buildCircularWavy(theme),
          _M3EProgressKind.linear => _buildLinear(theme, wavy: false),
          _M3EProgressKind.linearWavy => _buildLinear(theme, wavy: true),
        });
      },
    );
  }

  Widget _buildCircular(M3EThemeData theme) {
    final M3ECircularProgressTheme circular =
        theme.progressIndicatorTheme.circular;
    final M3EColorScheme scheme = theme.colorScheme;
    final double resolvedSize = widget.size ?? circular.defaultSize;
    final double resolvedStroke =
        widget.strokeWidth ?? circular.defaultStrokeWidth;
    final double trackStroke =
        widget.trackStrokeWidth ?? circular.trackStrokeWidth;
    final double gap = widget.gapSize ?? circular.gapSize;
    final Color active = widget.color ?? circular.activeColor(scheme);
    final Color track = widget.trackColor ?? circular.trackColor(scheme);

    return RepaintBoundary(
      child: SizedBox(
        width: resolvedSize,
        height: resolvedSize,
        child: AnimatedBuilder(
          animation: _isClassicCircularIndet
              ? Listenable.merge(<Listenable>[
                  _globalRotController,
                  _additionalRotController,
                  _sweepController,
                ])
              : const AlwaysStoppedAnimation<double>(0),
          builder: (BuildContext context, Widget? child) {
            final _Arc arc = _resolveClassicArc();
            return CustomPaint(
              painter: M3ECircularProgressPainter(
                trackColor: track,
                activeColor: active,
                strokeWidth: resolvedStroke,
                trackStrokeWidth: trackStroke,
                startAngle: arc.start,
                sweepAngle: arc.sweep,
                gapSize: gap,
                progress: widget.value,
                showTrack: widget.showTrack,
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildCircularWavy(M3EThemeData theme) {
    final M3ECircularProgressTheme circular =
        theme.progressIndicatorTheme.circular;
    final M3EColorScheme scheme = theme.colorScheme;
    final double resolvedSize = widget.size ?? circular.wavySize;
    final double stroke = widget.strokeWidth ?? circular.defaultStrokeWidth;
    final double trackStroke =
        widget.trackStrokeWidth ?? circular.trackStrokeWidth;
    final double gap = widget.gapSize ?? circular.gapSize;
    final double wavelength = widget.wavelength ?? circular.wavelength;
    final double waveSpeed = widget.waveSpeed ?? wavelength;
    final Color active = widget.color ?? circular.activeColor(scheme);
    final Color track = widget.trackColor ?? circular.trackColor(scheme);
    final double amplitudeFactor = _amplitudeFactor(
      themeAmplitude: circular.amplitudeForProgress,
    );

    final Listenable listenable = _isCircularWavyIndet
        ? Listenable.merge(<Listenable>[
            _spinController,
            _globalRotController,
            _additionalRotController,
            _sweepController,
          ])
        : _spinController;

    return RepaintBoundary(
      child: SizedBox(
        width: resolvedSize,
        height: resolvedSize,
        child: AnimatedBuilder(
          animation: listenable,
          builder: (BuildContext context, Widget? child) {
            final double sweepFraction = lerpDouble(
              M3ECircularWavyProgressPainter.minSweep,
              M3ECircularWavyProgressPainter.maxSweep,
              _sweepController.value,
            )!;
            return CustomPaint(
              painter: M3ECircularWavyProgressPainter(
                progress: widget.value,
                activeColor: active,
                trackColor: track,
                strokeWidth: stroke,
                trackStrokeWidth: trackStroke,
                gapSize: gap,
                amplitudeFactor: amplitudeFactor,
                maxAmplitude: circular.waveAmplitude,
                wavelength: wavelength,
                phase: _needsWavePhase ? _phase(wavelength, waveSpeed) : 0,
                globalRotation: _globalRotController.value,
                additionalRotation: _additionalRotController.value,
                sweepFraction: sweepFraction,
                showTrack: widget.showTrack,
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildLinear(M3EThemeData theme, {required bool wavy}) {
    final M3ELinearProgressTheme linear = theme.progressIndicatorTheme.linear;
    final M3EColorScheme scheme = theme.colorScheme;
    final Color active = widget.color ?? linear.activeColor(scheme);
    final Color track = widget.trackColor ?? linear.trackColor(scheme);
    if (!wavy) {
      return _buildLinearFlat(linear: linear, active: active, track: track);
    }
    return _buildLinearWavy(linear: linear, active: active, track: track);
  }

  Widget _buildLinearFlat({
    required M3ELinearProgressTheme linear,
    required Color active,
    required Color track,
  }) {
    final M3ELinearProgressLayout layout = linear.resolveFlat(
      widget.linearSize,
    );
    final double stroke = widget.strokeWidth ?? layout.trackHeight;
    final double trackStroke = widget.trackStrokeWidth ?? layout.trackHeight;
    final indet = widget.value == null;
    final double height = math.max(stroke, trackStroke);
    final TextDirection textDirection = Directionality.of(context);
    return RepaintBoundary(
      child: SizedBox(
        height: height,
        width: double.infinity,
        child: AnimatedBuilder(
          animation: _linearIndetController,
          builder: (BuildContext context, Widget? child) {
            return CustomPaint(
              painter: M3ELinearProgressPainter(
                value: widget.value,
                animationValue: indet ? _linearIndetController.value : 0,
                active: active,
                track: track,
                strokeWidth: stroke,
                trackStrokeWidth: trackStroke,
                gap: layout.gap,
                stopSize: layout.dotDiameter,
                isWavy: false,
                waveAmplitude: 0,
                wavelength: 40,
                phase: 0,
                amplitudeFactor: 0,
                flatLayout: layout,
                showTrack: widget.showTrack,
                textDirection: textDirection,
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildLinearWavy({
    required M3ELinearProgressTheme linear,
    required Color active,
    required Color track,
  }) {
    final M3ELinearProgressLayout layout = linear.resolveFlat(
      widget.linearSize,
    );
    // Explicit strokes > linearSize matrix > theme defaults.
    final double stroke = widget.strokeWidth ?? layout.trackHeight;
    final double trackStroke = widget.trackStrokeWidth ?? layout.trackHeight;
    final double gap = widget.gapSize ?? layout.gap;
    final double stop = widget.stopSize ?? layout.dotDiameter;
    final indeterminate = widget.value == null;
    final double wavelength =
        widget.wavelength ??
        (indeterminate
            ? linear.indeterminateWavelength
            : linear.determinateWavelength);
    final double waveSpeed = widget.waveSpeed ?? wavelength;
    final double amplitudeFactor = _amplitudeFactor(
      themeAmplitude: linear.amplitudeForProgress,
    );
    final double height = math.max(
      math.max(
        trackStroke,
        stroke + 2 * linear.waveAmplitude * amplitudeFactor,
      ),
      linear.wavyContainerHeight,
    );
    final TextDirection textDirection = Directionality.of(context);

    final Listenable listenable = indeterminate
        ? Listenable.merge(<Listenable>[
            _spinController,
            _linearIndetController,
          ])
        : _spinController;

    return RepaintBoundary(
      child: SizedBox(
        height: height,
        width: double.infinity,
        child: AnimatedBuilder(
          animation: listenable,
          builder: (BuildContext context, Widget? child) {
            return CustomPaint(
              painter: M3ELinearProgressPainter(
                value: widget.value,
                animationValue: indeterminate
                    ? _linearIndetController.value
                    : 0,
                active: active,
                track: track,
                strokeWidth: stroke,
                trackStrokeWidth: trackStroke,
                gap: gap,
                stopSize: stop,
                isWavy: true,
                waveAmplitude: linear.waveAmplitude,
                wavelength: wavelength,
                phase: _needsWavePhase ? _phase(wavelength, waveSpeed) : 0,
                amplitudeFactor: amplitudeFactor,
                trailingMargin: layout.trailingMargin,
                showTrack: widget.showTrack,
                textDirection: textDirection,
              ),
            );
          },
        ),
      ),
    );
  }

  _Arc _resolveClassicArc() {
    const double tau = 2 * math.pi;
    final double? value = widget.value;
    if (value != null) {
      return _Arc(-math.pi / 2, value.clamp(0, 1).toDouble() * tau);
    }
    // Match wavy circular indeterminate timing (flat arcs, not wavy path).
    final double totalDeg =
        _globalRotController.value *
            M3ECircularWavyProgressPainter.globalRotDeg +
        _additionalRotController.value *
            M3ECircularWavyProgressPainter.additionalRotDeg;
    final double totalRad = totalDeg * math.pi / 180;
    final double sweepFraction = lerpDouble(
      M3ECircularWavyProgressPainter.minSweep,
      M3ECircularWavyProgressPainter.maxSweep,
      _sweepController.value,
    )!;
    return _Arc(-math.pi / 2 + totalRad, sweepFraction * tau);
  }
}

@immutable
class _Arc {
  const _Arc(this.start, this.sweep);

  final double start;
  final double sweep;
}
