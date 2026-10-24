import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart' show clampDouble, kIsWeb;
import 'package:flutter/gestures.dart'
    show DragUpdateDetails, PointerDeviceKind;
import 'package:flutter/physics.dart';
import 'package:material_ui/material_ui.dart'
    hide CornerRounding, RoundedPolygon;
import 'package:motor/motor.dart';

import '../../foundations/foundations.dart';
import '../loading_indicator/m3e_loading_indicator.dart';
import 'controllers/m3e_refresh_indicator_controller.dart';
import 'enums/m3e_refresh_status.dart';
import 'styles/m3e_refresh_indicator_theme.dart';

export 'controllers/m3e_refresh_indicator_controller.dart';
export 'enums/m3e_refresh_status.dart';
export 'styles/m3e_refresh_indicator_theme.dart';

part 'components/m3e_refresh_indicator_scroll.dart';
part 'components/m3e_refresh_indicator_build.dart';
part 'components/m3e_refresh_indicator_web.dart';

enum _IndicatorType { material, expressive, contained, adaptive, noSpinner }

/// Web spinner cache phase (morph path must not rebuild every animation tick).
enum _WebSpinnerPhase { none, drag, refresh }

/// A Material Design 3 expressive refresh indicator.
///
/// Expressive and contained refresh kinds use a contained
/// [M3ELoadingIndicator]; elevation (when set) is applied on the refresh host
/// shell so it works on all platforms (including Flutter web).
///
/// Call [M3ERefreshIndicatorState.show] via a [GlobalKey], or pass a
/// [M3ERefreshIndicatorController] to trigger refresh programmatically.
///
/// Refresh runs only when the indicator is fully revealed (scale/opacity at 1)
/// and the pointer is released. Releasing earlier cancels with a scale/fade out.
///
/// On Flutter web, mouse drag is enabled for the child scrollable, and drag
/// updates avoid empty [State.setState] calls once the pull is at its visual
/// cap (those rebuilds freeze CanvasKit). Other platforms are unchanged.
class M3ERefreshIndicator extends StatefulWidget {
  /// const.
  const M3ERefreshIndicator({
    super.key,
    required this.child,
    this.displacement = M3ERefreshIndicatorTheme.kDefaultDisplacement,
    this.indicatorPadding = M3ERefreshIndicatorTheme.kDefaultIndicatorPadding,
    this.contentDragOffset,
    this.edgeOffset = M3ERefreshIndicatorTheme.kDefaultEdgeOffset,
    required this.onRefresh,
    this.controller,
    this.color,
    this.backgroundColor,
    this.notificationPredicate = defaultScrollNotificationPredicate,
    this.semanticsLabel,
    this.semanticsValue,
    this.triggerMode = M3ERefreshTriggerMode.onEdge,
    this.elevation = M3ERefreshIndicatorTheme.kDefaultElevation,
    this.releaseBubbleSpring,
    this.releaseBubbleFromScale,
    this.polygons,
    this.indicatorConstraints,
    this.onStatusChange,
  }) : _indicatorType = _IndicatorType.expressive,
       strokeWidth = 0.0,
       assert(elevation >= 0.0, 'assertion failed'),
       assert(indicatorPadding >= 0.0, 'assertion failed'),
       assert(
         releaseBubbleFromScale == null || releaseBubbleFromScale > 0.0,
         'assertion failed',
       ),
       assert(!(polygons != null) || polygons.length > 1, 'assertion failed');

  /// const.
  const M3ERefreshIndicator.contained({
    super.key,
    required this.child,
    this.displacement = M3ERefreshIndicatorTheme.kDefaultDisplacement,
    this.indicatorPadding = M3ERefreshIndicatorTheme.kDefaultIndicatorPadding,
    this.contentDragOffset,
    this.edgeOffset = M3ERefreshIndicatorTheme.kDefaultEdgeOffset,
    required this.onRefresh,
    this.controller,
    this.color,
    this.backgroundColor,
    this.notificationPredicate = defaultScrollNotificationPredicate,
    this.semanticsLabel,
    this.semanticsValue,
    this.triggerMode = M3ERefreshTriggerMode.onEdge,
    this.elevation = M3ERefreshIndicatorTheme.kDefaultElevation,
    this.releaseBubbleSpring,
    this.releaseBubbleFromScale,
    this.polygons,
    this.indicatorConstraints,
    this.onStatusChange,
  }) : _indicatorType = _IndicatorType.contained,
       strokeWidth = 0.0,
       assert(elevation >= 0.0, 'assertion failed'),
       assert(indicatorPadding >= 0.0, 'assertion failed'),
       assert(
         releaseBubbleFromScale == null || releaseBubbleFromScale > 0.0,
         'assertion failed',
       ),
       assert(!(polygons != null) || polygons.length > 1, 'assertion failed');

  /// const.
  const M3ERefreshIndicator.material({
    super.key,
    required this.child,
    this.displacement = M3ERefreshIndicatorTheme.kDefaultDisplacement,
    this.indicatorPadding = M3ERefreshIndicatorTheme.kDefaultIndicatorPadding,
    this.contentDragOffset,
    this.edgeOffset = M3ERefreshIndicatorTheme.kDefaultEdgeOffset,
    required this.onRefresh,
    this.controller,
    this.color,
    this.backgroundColor,
    this.notificationPredicate = defaultScrollNotificationPredicate,
    this.semanticsLabel,
    this.semanticsValue,
    this.strokeWidth = RefreshProgressIndicator.defaultStrokeWidth,
    this.triggerMode = M3ERefreshTriggerMode.onEdge,
    this.elevation = M3ERefreshIndicatorTheme.kDefaultElevation,
    this.releaseBubbleSpring,
    this.releaseBubbleFromScale,
    this.onStatusChange,
  }) : _indicatorType = _IndicatorType.material,
       polygons = null,
       indicatorConstraints = null,
       assert(elevation >= 0.0, 'assertion failed'),
       assert(indicatorPadding >= 0.0, 'assertion failed'),
       assert(
         releaseBubbleFromScale == null || releaseBubbleFromScale > 0.0,
         'assertion failed',
       );

  /// const.
  const M3ERefreshIndicator.adaptive({
    super.key,
    required this.child,
    this.displacement = M3ERefreshIndicatorTheme.kDefaultDisplacement,
    this.indicatorPadding = M3ERefreshIndicatorTheme.kDefaultIndicatorPadding,
    this.contentDragOffset,
    this.edgeOffset = M3ERefreshIndicatorTheme.kDefaultEdgeOffset,
    required this.onRefresh,
    this.controller,
    this.color,
    this.backgroundColor,
    this.notificationPredicate = defaultScrollNotificationPredicate,
    this.semanticsLabel,
    this.semanticsValue,
    this.strokeWidth = RefreshProgressIndicator.defaultStrokeWidth,
    this.triggerMode = M3ERefreshTriggerMode.onEdge,
    this.elevation = M3ERefreshIndicatorTheme.kDefaultElevation,
    this.releaseBubbleSpring,
    this.releaseBubbleFromScale,
    this.onStatusChange,
  }) : _indicatorType = _IndicatorType.adaptive,
       polygons = null,
       indicatorConstraints = null,
       assert(elevation >= 0.0, 'assertion failed'),
       assert(indicatorPadding >= 0.0, 'assertion failed'),
       assert(
         releaseBubbleFromScale == null || releaseBubbleFromScale > 0.0,
         'assertion failed',
       );

  /// const.
  const M3ERefreshIndicator.noSpinner({
    super.key,
    required this.child,
    required this.onRefresh,
    this.controller,
    this.onStatusChange,
    this.indicatorPadding = M3ERefreshIndicatorTheme.kDefaultIndicatorPadding,
    this.contentDragOffset,
    this.notificationPredicate = defaultScrollNotificationPredicate,
    this.semanticsLabel,
    this.semanticsValue,
    this.triggerMode = M3ERefreshTriggerMode.onEdge,
    this.elevation = M3ERefreshIndicatorTheme.kDefaultElevation,
  }) : _indicatorType = _IndicatorType.noSpinner,
       displacement = 0.0,
       edgeOffset = 0.0,
       color = null,
       backgroundColor = null,
       strokeWidth = 0.0,
       polygons = null,
       indicatorConstraints = null,
       releaseBubbleSpring = null,
       releaseBubbleFromScale = null,
       assert(elevation >= 0.0, 'assertion failed'),
       assert(indicatorPadding >= 0.0, 'assertion failed');

  /// final.
  final Widget child;

  /// Resting offset of the indicator below the scroll edge (top of spinner).
  ///
  /// Defaults to [M3ERefreshIndicatorTheme.kDefaultDisplacement] (8), matching
  /// the top [indicatorPadding] so the spinner sits in the list pad.
  final double displacement;

  /// Vertical gap above and below the spinner inside the list pad (default 8).
  ///
  /// Max list pad defaults to `indicatorHeight + 2 * indicatorPadding`.
  /// Scale/fade/downward reveal starts after the pad reaches
  /// `2 * indicatorPadding`.
  final double indicatorPadding;

  /// Max list top padding while dragging. Defaults to indicator height +
  /// `2 * indicatorPadding`. Does not move the indicator's resting edge.
  final double? contentDragOffset;

  /// final.
  final double edgeOffset;

  /// final.
  final M3ERefreshCallback onRefresh;

  /// Optional controller to call [M3ERefreshIndicatorController.show].
  final M3ERefreshIndicatorController? controller;

  /// final.
  final ValueChanged<M3ERefreshStatus?>? onStatusChange;

  /// final.
  final Color? color;

  /// final.
  final Color? backgroundColor;

  /// final.
  final ScrollNotificationPredicate notificationPredicate;

  /// final.
  final String? semanticsLabel;

  /// final.
  final String? semanticsValue;

  /// final.
  final double strokeWidth;

  /// final.
  final M3ERefreshTriggerMode triggerMode;

  /// final.
  final double elevation;

  /// Override for the release scale-bubble spring. Defaults to theme
  /// [M3ERefreshIndicatorTheme.releaseBubbleSpring].
  final M3ESpring? releaseBubbleSpring;

  /// Override for the bubble start scale. Defaults to theme
  /// [M3ERefreshIndicatorTheme.releaseBubbleFromScale].
  final double? releaseBubbleFromScale;

  final _IndicatorType _indicatorType;

  /// final.
  final List<RoundedPolygon>? polygons;

  /// final.
  final BoxConstraints? indicatorConstraints;

  @override
  M3ERefreshIndicatorState createState() => M3ERefreshIndicatorState();
}

/// class.
class M3ERefreshIndicatorState extends State<M3ERefreshIndicator>
    with TickerProviderStateMixin<M3ERefreshIndicator> {
  late AnimationController _positionController;
  late AnimationController _scaleController;
  late AnimationController _contentPadController;

  /// Release scale bubble at the locked rest inset (motor / M3EMotion spring).
  late SingleMotionController _bubbleController;
  late Animation<double> _scaleFactor;
  late Animation<double> _value;
  late Animation<Color?> _valueColor;

  M3ERefreshStatus? _status;
  late Future<void> _pendingRefreshFuture;
  bool? _isIndicatorAtTop;
  double? _dragOffset;

  /// Cached morph spinner on web (drag = frozen morph; refresh = auto-spin).
  Widget? _webSpinnerCache;
  _WebSpinnerPhase _webSpinnerPhase = _WebSpinnerPhase.none;
  _IndicatorType? _webSpinnerType;

  /// Dedupes pointer drag deltas when ScrollUpdate + Overscroll fire together.
  bool _pointerDeltaLocked = false;

  /// Indicator top inset locked when loading starts.
  double? _restingInset;
  late Color _effectiveValueColor;
  late Color _effectiveContainerColor;

  /// Stable show callback for [M3ERefreshIndicatorController] attach/detach.
  /// Fresh method tear-offs are never [identical], so dispose would fail to
  /// clear and leave a disposed State's callback attached.
  late final Future<void> Function({bool atTop}) _controllerShow;

  static final Animatable<double> _threeQuarterTween = Tween<double>(
    begin: 0,
    end: 0.75,
  );
  static final Animatable<double> _oneToZeroTween = Tween<double>(
    begin: 1,
    end: 0,
  );

  double _indicatorHeight(BuildContext context) {
    final double? maxHeight = widget.indicatorConstraints?.maxHeight;
    if (maxHeight != null && maxHeight.isFinite) {
      return maxHeight;
    }
    return M3ETheme.of(context).loadingIndicatorTheme.containerHeight;
  }

  /// Max list pad: enough for the spinner plus padding above and below.
  double _resolvedContentDragOffset(BuildContext context) {
    if (widget.contentDragOffset != null) {
      return widget.contentDragOffset!;
    }
    return _indicatorHeight(context) + 2 * widget.indicatorPadding;
  }

  /// Pull (px) at which scale/fade/downward motion begins.
  double get _revealDelayPx => 2 * widget.indicatorPadding;

  M3ERefreshIndicatorTheme get _refreshTheme =>
      M3ETheme.of(context).refreshIndicatorTheme;

  M3ESpring get _resolvedReleaseBubbleSpring =>
      widget.releaseBubbleSpring ?? _refreshTheme.releaseBubbleSpring;

  double get _resolvedReleaseBubbleFromScale =>
      widget.releaseBubbleFromScale ?? _refreshTheme.releaseBubbleFromScale;

  /// Motor spring from theme / widget override (user-tuned defaults: 350 / 0.1).
  SpringMotion get _releaseBubbleMotion {
    final M3ESpring spring = _resolvedReleaseBubbleSpring;
    return const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
      stiffness: spring.stiffness,
      damping: spring.damping,
    );
  }

  @override
  void initState() {
    super.initState();
    _positionController = AnimationController.unbounded(vsync: this);
    _value = _positionController.drive(_threeQuarterTween);
    _scaleController = AnimationController.unbounded(vsync: this);
    _scaleFactor = _scaleController.drive(_oneToZeroTween);
    _contentPadController = AnimationController.unbounded(vsync: this);
    // Theme may not be available yet; _playReleaseBubble applies resolved motion.
    _bubbleController = SingleMotionController(
      motion: const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness:
            M3ERefreshIndicatorTheme.kDefaultReleaseBubbleSpring.stiffness,
        damping: M3ERefreshIndicatorTheme.kDefaultReleaseBubbleSpring.damping,
      ),
      vsync: this,
      initialValue: 1,
    );
    _pendingRefreshFuture = Future<void>.value();
    Future<void> controllerShow({bool atTop = true}) => show(atTop: atTop);
    _controllerShow = controllerShow;
    widget.controller?.attach(_controllerShow);
  }

  @override
  void didChangeDependencies() {
    _setupColorTween();
    super.didChangeDependencies();
  }

  @override
  void didUpdateWidget(covariant M3ERefreshIndicator oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.detach(_controllerShow);
      widget.controller?.attach(_controllerShow);
    }
    if (oldWidget.color != widget.color) {
      _setupColorTween();
    }
  }

  @override
  void dispose() {
    widget.controller?.detach(_controllerShow);
    _positionController.dispose();
    _scaleController.dispose();
    _contentPadController.dispose();
    _bubbleController.dispose();
    super.dispose();
  }

  /// Shows the refresh indicator and runs [M3ERefreshIndicator.onRefresh].
  Future<void> show({bool atTop = true}) {
    if (!mounted) {
      return Future<void>.value();
    }
    if (_status != M3ERefreshStatus.refresh &&
        _status != M3ERefreshStatus.snap) {
      if (_status == null) {
        _start(atTop ? AxisDirection.down : AxisDirection.up);
      }
      _show();
    }
    return _pendingRefreshFuture;
  }

  /// Web and native desktop: mouse/trackpad pull needs 1:1 pointer deltas and
  /// leading underscroll clamp (same pad/reveal math as mobile).
  bool get _usePointerPullTracking {
    if (kIsWeb) {
      return true;
    }
    return switch (M3ETheme.platformOf(context)) {
      TargetPlatform.macOS ||
      TargetPlatform.windows ||
      TargetPlatform.linux => true,
      TargetPlatform.iOS ||
      TargetPlatform.android ||
      TargetPlatform.fuchsia => false,
    };
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(
      builder: (BuildContext context) {
        final Widget tree = kIsWeb
            ? _buildWebTree(context)
            : _buildHostTree(context);
        if (_usePointerPullTracking) {
          return _wrapPointerPullScrollBehavior(tree);
        }
        return tree;
      },
    );
  }

  Widget _buildHostTree(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge(<Listenable>[
        _positionController,
        _scaleController,
        _contentPadController,
        _bubbleController,
      ]),
      builder: (BuildContext context, Widget? _) {
        final double pad = _currentPad(context);
        return Stack(
          clipBehavior: Clip.none,
          children: <Widget>[
            NotificationListener<ScrollNotification>(
              onNotification: (ScrollNotification n) =>
                  _handleScrollNotification(n),
              child: NotificationListener<OverscrollIndicatorNotification>(
                onNotification: (OverscrollIndicatorNotification n) =>
                    _handleIndicatorNotification(n),
                child: Padding(
                  padding: EdgeInsets.only(
                    top: (_isIndicatorAtTop ?? false) ? pad : 0,
                    bottom: _isIndicatorAtTop == false ? pad : 0,
                  ),
                  child: widget.child,
                ),
              ),
            ),
            if (_status != null) _buildPositionedIndicator(context),
          ],
        );
      },
    );
  }
}
