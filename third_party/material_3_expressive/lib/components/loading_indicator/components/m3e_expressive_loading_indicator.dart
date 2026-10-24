import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/physics.dart';
import 'package:flutter/semantics.dart';
import 'package:material_3_expressive/foundations/foundations.dart';
import 'package:material_ui/material_ui.dart'
    hide CornerRounding, RoundedPolygon;

import '../styles/m3e_loading_indicator_theme.dart';

/// A Material Design loading indicator.
///
/// This version of the loading indicator morphs between its [polygons] shapes.
/// ![Loading indicator image](https://developer.android.com/images/reference/androidx/compose/material3/loading-indicator.png)
class M3EExpressiveLoadingIndicator extends ProgressIndicator {
  /// A list of [RoundedPolygon]s for the sequence of shapes this loading indicator
  /// will morph between. The loading indicator expects at least two items in that list.
  final List<RoundedPolygon>? polygons;

  /// Indicator colors cycled and interpolated during morphing.
  final List<Color>? indicatorColors;

  /// Size of the morphing indicator.
  final double? indicatorSize;

  /// Defines minimum and maximum sizes for an [M3EExpressiveLoadingIndicator].
  /// If null, then the [ProgressIndicatorThemeData.constraints] will be used. Otherwise, defaults to a minimum width and height of 48 pixels.
  final BoxConstraints? constraints;

  /// Full 360° continuous spin period. Defaults to theme.
  final Duration? globalRotationDuration;

  /// Delay between polygon morph cycles. Defaults to theme.
  final Duration? morphInterval;

  /// Extra rotation (degrees) across each morph. Defaults to theme (45).
  final double? morphRotationDegrees;

  /// Spring for morph progress. Defaults to theme.
  final M3ESpring? morphSpring;

  /// Initial morph spring velocity. Defaults to theme.
  final double? morphSpringVelocity;

  /// Scale at the start of each morph-in pulse (`→ 1`; default expands above 1).
  final double? pulseStartScale;

  /// Spring for pulse settle. Defaults to theme.
  final M3ESpring? pulseSpring;

  /// Initial pulse spring velocity. Defaults to theme.
  final double? pulseSpringVelocity;

  /// When non-null, auto spin and morph pulse are disabled; this value (in
  /// turns, where `1.0` is 360°) drives rotation instead.
  final double? rotationTurns;

  /// M3EExpressiveLoadingIndicator.

  const M3EExpressiveLoadingIndicator({
    super.key,
    super.color,
    this.polygons,
    this.indicatorColors,
    this.indicatorSize,
    this.constraints,
    this.globalRotationDuration,
    this.morphInterval,
    this.morphRotationDegrees,
    this.morphSpring,
    this.morphSpringVelocity,
    this.pulseStartScale,
    this.pulseSpring,
    this.pulseSpringVelocity,
    this.rotationTurns,
    super.semanticsLabel,
    super.semanticsValue,
  }) : assert(
         !(polygons != null) || polygons.length > 1,
         'polygons must contain more than one shape when provided',
       );

  @override
  State<M3EExpressiveLoadingIndicator> createState() =>
      _M3EExpressiveLoadingIndicatorState();
}

class _M3EExpressiveLoadingIndicatorState
    extends State<M3EExpressiveLoadingIndicator>
    with TickerProviderStateMixin {
  static final List<RoundedPolygon> _defaultPolygons = [
    M3EMaterialNewShapes.softBurst,
    M3EMaterialNewShapes.cookie9Sided,
    M3EMaterialNewShapes.pentagon,
    M3EMaterialNewShapes.pill,
    M3EMaterialNewShapes.sunny,
    M3EMaterialNewShapes.cookie4Sided,
    M3EMaterialNewShapes.oval,
  ];

  late final List<RoundedPolygon> _polygons;

  static const double _fullRotation = 360;

  late final List<Morph> _morphSequence;

  late final AnimationController _morphController;
  late final AnimationController _globalRotationController;
  late final AnimationController _pulseController;
  int _currentMorphIndex = 0;
  double _morphRotationTargetAngle = 0;

  Timer? _morphTimer;

  late BoxConstraints _constraints;
  late List<Color> _indicatorColors;
  late M3ELoadingIndicatorTheme _loadingTheme;

  Duration get _globalRotationDuration =>
      widget.globalRotationDuration ?? _loadingTheme.globalRotationDuration;

  Duration get _morphInterval =>
      widget.morphInterval ?? _loadingTheme.morphInterval;

  double get _morphRotationDegrees =>
      widget.morphRotationDegrees ?? _loadingTheme.morphRotationDegrees;

  M3ESpring get _morphSpring => widget.morphSpring ?? _loadingTheme.morphSpring;

  double get _morphSpringVelocity =>
      widget.morphSpringVelocity ?? _loadingTheme.morphSpringVelocity;

  double get _pulseStartScale =>
      widget.pulseStartScale ?? _loadingTheme.pulseStartScale;

  M3ESpring get _pulseSpring => widget.pulseSpring ?? _loadingTheme.pulseSpring;

  double get _pulseSpringVelocity =>
      widget.pulseSpringVelocity ?? _loadingTheme.pulseSpringVelocity;

  double get _activeSize =>
      widget.indicatorSize ?? _loadingTheme.activeIndicatorSize;

  bool get _manualRotation => widget.rotationTurns != null;

  void _rejectEmptyIndicatorColors() {
    assert(() {
      if (widget.indicatorColors != null && widget.indicatorColors!.isEmpty) {
        throw AssertionError('indicatorColors cannot be empty');
      }
      return true;
    }(), 'indicatorColors cannot be empty');
    if (widget.indicatorColors != null && widget.indicatorColors!.isEmpty) {
      throw ArgumentError.value(
        widget.indicatorColors,
        'indicatorColors',
        'must not be empty',
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    _rejectEmptyIndicatorColors();
    final m3eTheme = M3ETheme.of(context);
    _loadingTheme = m3eTheme.loadingIndicatorTheme;
    _indicatorColors =
        widget.indicatorColors ??
        <Color>[
          widget.color ?? _loadingTheme.activeColor(m3eTheme.colorScheme),
        ];
    _constraints =
        widget.constraints ??
        BoxConstraints.tightFor(
          width: _loadingTheme.containerWidth,
          height: _loadingTheme.containerHeight,
        );

    // Fit polygons into the active shape size only — outer container stays fixed.
    final shapesScaleFactor = _calculateScaleFactor(_polygons);

    return Semantics.fromProperties(
      properties: SemanticsProperties(
        label: widget.semanticsLabel,
        // Spec: progressbar. Flutter requires value/min/max for this role;
        // indeterminate uses 0% with optional descriptive hint.
        role: SemanticsRole.progressBar,
        minValue: '0',
        maxValue: '100',
        value: _progressBarValue(widget.semanticsValue),
        hint: _progressBarHint(widget.semanticsValue),
      ),
      child: RepaintBoundary(
        child: ConstrainedBox(
          constraints: _constraints,
          child: AspectRatio(
            aspectRatio: 1,
            child: Center(
              child: AnimatedBuilder(
                animation: Listenable.merge([
                  _morphController,
                  _globalRotationController,
                  _pulseController,
                ]),
                builder: (context, child) {
                  final morphProgress = _morphController.value.clamp(0.0, 1.0);
                  final double globalRotationDegrees = _manualRotation
                      ? (widget.rotationTurns! * _fullRotation)
                      : (_globalRotationController.value * _fullRotation);

                  final double totalRotationDegrees = _manualRotation
                      ? globalRotationDegrees
                      : morphProgress * _morphRotationDegrees +
                            _morphRotationTargetAngle +
                            globalRotationDegrees;

                  final totalRotationRadians =
                      totalRotationDegrees * (math.pi / 180.0);

                  final Color indicatorColor = _resolveIndicatorColor(
                    morphProgress,
                  );
                  final double pulseScale = _manualRotation
                      ? 1.0
                      : _pulseController.value;

                  return Transform.rotate(
                    angle: totalRotationRadians,
                    child: Transform.scale(
                      scale: pulseScale,
                      child: SizedBox(
                        width: _activeSize,
                        height: _activeSize,
                        child: CustomPaint(
                          painter: _MorphPainter(
                            morph: _morphSequence[_currentMorphIndex],
                            progress: morphProgress,
                            color: indicatorColor,
                            scaleFactor: shapesScaleFactor,
                            repaint: Listenable.merge([
                              _morphController,
                              _globalRotationController,
                              _pulseController,
                            ]),
                          ),
                          child: const SizedBox.expand(),
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _morphTimer?.cancel();
    _morphController.dispose();
    _globalRotationController.dispose();
    _pulseController.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();

    _polygons = widget.polygons ?? _defaultPolygons;

    _morphSequence = _createMorphSequence(_polygons, circularSequence: true);

    _morphController = AnimationController.unbounded(vsync: this);
    _pulseController = AnimationController.unbounded(vsync: this, value: 1);

    // continuous linear rotation — duration applied once theme is available
    _globalRotationController = AnimationController(vsync: this);

    // Theme is read in didChangeDependencies before first morph tick.
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _loadingTheme = M3ETheme.of(context).loadingIndicatorTheme;
    _ensureAnimationsStarted();
  }

  @override
  void didUpdateWidget(M3EExpressiveLoadingIndicator oldWidget) {
    super.didUpdateWidget(oldWidget);
    final wasManual = oldWidget.rotationTurns != null;
    final isManual = widget.rotationTurns != null;
    if (wasManual != isManual) {
      if (isManual) {
        _enterManualRotation();
      } else {
        _exitManualRotation(seedTurns: oldWidget.rotationTurns);
      }
    } else if (!isManual &&
        (oldWidget.globalRotationDuration != widget.globalRotationDuration ||
            oldWidget.morphInterval != widget.morphInterval)) {
      _restartPeriodicAnimations();
    }
  }

  bool _animationsStarted = false;

  void _ensureAnimationsStarted() {
    if (_animationsStarted) {
      if (!_manualRotation) {
        _syncGlobalRotationDuration();
      }
      return;
    }
    _animationsStarted = true;
    _morphRotationTargetAngle = _morphRotationDegrees;
    if (_manualRotation) {
      _pulseController.value = 1;
      return;
    }
    _syncGlobalRotationDuration();
    _startAnimations();
  }

  void _enterManualRotation() {
    _morphTimer?.cancel();
    _morphTimer = null;
    _globalRotationController.stop();
    _morphController.stop();
    _pulseController
      ..stop()
      ..value = 1;
  }

  void _exitManualRotation({double? seedTurns}) {
    if (seedTurns != null) {
      _globalRotationController.value = seedTurns % 1.0;
      _morphRotationTargetAngle = 0;
    }
    _syncGlobalRotationDuration();
    if (!_globalRotationController.isAnimating) {
      _globalRotationController.repeat();
    }
    _morphTimer?.cancel();
    _morphTimer = Timer.periodic(_morphInterval, (_) => _startMorphCycle());
    _startMorphCycle();
  }

  void _syncGlobalRotationDuration() {
    final Duration duration = _globalRotationDuration;
    if (_globalRotationController.duration != duration) {
      final double value = _globalRotationController.value;
      _globalRotationController.duration = duration;
      if (_globalRotationController.isAnimating) {
        _globalRotationController
          ..value = value
          ..repeat();
      }
    }
  }

  void _restartPeriodicAnimations() {
    if (_manualRotation) {
      return;
    }
    _morphTimer?.cancel();
    _syncGlobalRotationDuration();
    if (!_globalRotationController.isAnimating) {
      _globalRotationController.repeat();
    }
    _morphTimer = Timer.periodic(_morphInterval, (_) => _startMorphCycle());
  }

  List<Morph> _createMorphSequence(
    List<RoundedPolygon> polygons, {
    required bool circularSequence,
  }) {
    final morphs = <Morph>[];

    for (var i = 0; i < polygons.length; i++) {
      if (i + 1 < polygons.length) {
        morphs.add(Morph(polygons[i], polygons[i + 1]));
      } else if (circularSequence) {
        // morph from last shape back to first shape
        morphs.add(Morph(polygons[i], polygons[0]));
      }
    }

    return morphs;
  }

  /// Calculates a scale factor that will be used when scaling the provided `RoundedPolygon`s into a
  /// specified sized container.
  ///
  /// Since the polygons may rotate, a simple `RoundedPolygon.calculateBounds` is not enough to
  /// determine the size the polygon will occupy as it rotates. Using the simple bounds calculation may
  /// result in a clipped shape.
  ///
  /// This function calculates and returns a scale factor by utilizing the
  /// `RoundedPolygon.calculateMaxBounds` and comparing its result to the
  /// `RoundedPolygon.calculateBounds`. The scale factor can later be used when calling `processPath`.
  ///
  /// Port of Kotlin implementation.
  double _calculateScaleFactor(List<RoundedPolygon> polygons) {
    var scaleFactor = 1.0;

    for (final polygon in polygons) {
      final bounds = polygon.calculateBounds();
      final maxBounds = polygon.calculateMaxBounds();

      final boundsWidth = bounds[2] - bounds[0];
      final boundsHeight = bounds[3] - bounds[1];

      final maxBoundsWidth = maxBounds[2] - maxBounds[0];
      final maxBoundsHeight = maxBounds[3] - maxBounds[1];

      final scaleX = boundsWidth / maxBoundsWidth;
      final scaleY = boundsHeight / maxBoundsHeight;

      // We use max(scaleX, scaleY) to handle cases like a pill-shape that can throw off the
      // entire calculation.
      scaleFactor = math.min(scaleFactor, math.max(scaleX, scaleY));
    }

    return scaleFactor;
  }

  void _startAnimations() {
    if (_manualRotation) {
      return;
    }
    // infinite global rotation
    _globalRotationController.repeat();

    // periodic morph cycle
    _morphTimer = Timer.periodic(_morphInterval, (_) => _startMorphCycle());

    _startMorphCycle();
  }

  Color _resolveIndicatorColor(double progress) {
    if (_indicatorColors.length == 1) {
      return _indicatorColors.first;
    }

    final int colorIndex = _currentMorphIndex % _indicatorColors.length;
    final Color startColor = _indicatorColors[colorIndex];
    final Color endColor =
        _indicatorColors[(colorIndex + 1) % _indicatorColors.length];
    return Color.lerp(startColor, endColor, progress) ?? startColor;
  }

  /// Numeric / percent [semanticsValue], else indeterminate `0%`.
  static String _progressBarValue(String? semanticsValue) {
    if (semanticsValue == null || semanticsValue.isEmpty) {
      return '0%';
    }
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

  /// Non-numeric [semanticsValue] is exposed as a hint (progressBar value
  /// must stay numeric for Flutter debug role checks).
  static String? _progressBarHint(String? semanticsValue) {
    if (semanticsValue == null || semanticsValue.isEmpty) {
      return null;
    }
    if (_progressBarValue(semanticsValue) == semanticsValue) {
      return null;
    }
    return semanticsValue;
  }

  void _startMorphCycle() {
    if (!mounted || _manualRotation) {
      return;
    }

    // move to next morph in sequence
    _currentMorphIndex = (_currentMorphIndex + 1) % _morphSequence.length;

    // accumulate rotation target
    _morphRotationTargetAngle =
        (_morphRotationTargetAngle + _morphRotationDegrees) % _fullRotation;

    // Reset and start morph animation (slower spring + lower velocity by default)
    _morphController
      ..value = 0.0
      ..animateWith(
        SpringSimulation(
          _morphSpring.toDescription(),
          0,
          1,
          _morphSpringVelocity,
          snapToEnd: true,
        ),
      );

    // Outward pulse (scale > 1) then smooth settle down to 1.
    _pulseController
      ..value = _pulseStartScale
      ..animateWith(
        SpringSimulation(
          _pulseSpring.toDescription(),
          _pulseStartScale,
          1,
          _pulseSpringVelocity,
        ),
      );
  }
}

class _MorphPainter extends CustomPainter {
  final Morph morph;
  final double progress;
  final Color color;

  /// A scale factor that will be taken into account uniformly when the path is
  /// scaled (i.e. the scaleX would be the size width x the scale factor, and the scaleY would be
  /// the size height x the scale factor)
  final double scaleFactor;

  _MorphPainter({
    required this.morph,
    required this.progress,
    required this.color,
    this.scaleFactor = 1.0,
    super.repaint,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final path = morph.toPath(progress: progress);
    final processedPath = _processPath(path, size);
    canvas.drawPath(
      processedPath,
      Paint()
        ..style = PaintingStyle.fill
        ..color = color,
    );
  }

  @override
  bool shouldRepaint(_MorphPainter oldDelegate) {
    return oldDelegate.morph != morph ||
        oldDelegate.progress != progress ||
        oldDelegate.color != color ||
        oldDelegate.scaleFactor != scaleFactor;
  }

  /// Process a given path to scale it and center it inside the given size.
  ///
  /// [path] takes a [Path] that was generated by a _normalized_ [Morph] or [RoundedPolygon].
  /// [size] takes a [Size] that the provided [path] is going to be scaled and centered into.
  Path _processPath(Path path, Size size) {
    // a [Matrix] that would be used to apply the scaling. Note that any provided
    // matrix will be reset in this function.
    final scaleMatrix = Matrix4.diagonal3Values(
      size.width * scaleFactor,
      size.height * scaleFactor,
      1,
    );
    final Path scaledPath = path.transform(scaleMatrix.storage);

    // Translate the path so that its center aligns with the center of the container.
    final Rect bounds = scaledPath.getBounds();
    final Offset translation =
        Offset(size.width / 2, size.height / 2) - bounds.center;
    final Path finalPath = scaledPath.shift(translation);

    return finalPath;
  }
}
