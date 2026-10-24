import 'dart:math' as math;
import 'dart:ui' show lerpDouble;

import 'package:flutter/foundation.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../controllers/m3e_bottom_sheet_controller.dart';
import '../controllers/m3e_bottom_sheet_scroll_controller.dart';
import '../enums/m3e_bottom_sheet_enums.dart';
import '../models/m3e_bottom_sheet_labels.dart';
import '../styles/m3e_bottom_sheet_theme.dart';
import '../utils/m3e_bottom_sheet_detents.dart';
import '../utils/m3e_bottom_sheet_size_reporter.dart';
import '../utils/m3e_bottom_sheet_spring.dart';
import 'm3e_bottom_sheet_drag_handle.dart';
import 'm3e_bottom_sheet_drag_region.dart';
import 'm3e_bottom_sheet_header.dart';
import 'm3e_bottom_sheet_surface.dart';

part 'm3e_bottom_sheet_frame_build.dart';

/// Shared core of standard and modal bottom sheets.
///
/// Fills its parent and anchors the sheet to the bottom edge. Owns the
/// preset heights, dragging, spring snapping, responsive width and the
/// controller link.
class M3EBottomSheetFrame extends StatefulWidget {
  /// M3EBottomSheetFrame.
  const M3EBottomSheetFrame({
    required this.variant,
    required this.theme,
    required this.labels,
    required this.child,
    this.showDragHandle = true,
    this.enableDrag = true,
    this.isDismissible = true,
    this.initialValue = M3EBottomSheetValue.collapsed,
    this.previewHeight,
    this.expandToFullScreen = false,
    this.fullScreenTitle,
    this.controller,
    this.onValueChanged,
    this.onDismiss,
    this.onClose,
    this.entrance,
    this.backProgress,
    this.handleFocusNode,
    this.overlayStyle,
    super.key,
  });

  /// Standard or modal.
  final M3EBottomSheetVariant variant;

  /// Resolved sheet theme.
  final M3EBottomSheetTheme theme;

  /// Accessibility strings.
  final M3EBottomSheetLabels labels;

  /// Sheet content.
  final Widget child;

  /// Whether to show the drag handle.
  final bool showDragHandle;

  /// Whether dragging resizes the sheet.
  final bool enableDrag;

  /// Standard only: whether dragging down may hide the sheet.
  final bool isDismissible;

  /// Height to rest at first.
  final M3EBottomSheetValue initialValue;

  /// Standard only: optional peek height.
  final double? previewHeight;

  /// Adds a full-screen height with a header; the sheet spans the width.
  final bool expandToFullScreen;

  /// Title in the full-screen header.
  final String? fullScreenTitle;

  /// Optional controller.
  final M3EBottomSheetController? controller;

  /// Called when the sheet rests at a new height.
  final ValueChanged<M3EBottomSheetValue>? onValueChanged;

  /// Modal: guarded dismissal. Resolves false to stay open.
  final Future<bool> Function()? onDismiss;

  /// Modal: closes without guards and pops [Object] as the result.
  final ValueChanged<Object?>? onClose;

  /// Modal: open progress (0 hidden, 1 shown).
  final ValueListenable<double>? entrance;

  /// Modal: predictive-back progress (0–1).
  final ValueListenable<double>? backProgress;

  /// Focus node for the drag handle.
  final FocusNode? handleFocusNode;

  /// System bar style where the sheet sits under a system bar. Null leaves
  /// the bars to the page below.
  final SystemUiOverlayStyle? overlayStyle;

  @override
  State<M3EBottomSheetFrame> createState() => _M3EBottomSheetFrameState();
}

class _M3EBottomSheetFrameState extends State<M3EBottomSheetFrame>
    with TickerProviderStateMixin
    implements M3EBottomSheetControllerClient, M3EBottomSheetDragTarget {
  late final AnimationController _extent = AnimationController.unbounded(
    vsync: this,
  );
  late final AnimationController _layout = AnimationController.unbounded(
    vsync: this,
  );
  late final M3EBottomSheetScrollController _scroll =
      M3EBottomSheetScrollController(sheet: this);

  M3EBottomSheetDetents? _detents;
  M3EBottomSheetValue _value = M3EBottomSheetValue.hidden;
  Size _area = Size.zero;
  double _sheetHeight = 0;
  double _topPadding = 0;
  bool? _wide;
  bool _dragging = false;
  int _runId = 0;

  M3EBottomSheetTheme get _theme => widget.theme;

  bool get _modal => widget.variant == M3EBottomSheetVariant.modal;

  bool get _canHide => _modal || widget.isDismissible;

  bool get _reduceMotion =>
      MediaQuery.maybeDisableAnimationsOf(context) ?? false;

  double get _topMargin {
    final double margin = (_wide ?? false)
        ? _theme.wideTopMargin
        : _theme.compactTopMargin;
    return math.max(margin, _topPadding);
  }

  @override
  void initState() {
    super.initState();
    widget.controller?.attach(this);
  }

  @override
  void didUpdateWidget(covariant M3EBottomSheetFrame oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.detach(this);
      widget.controller?.attach(this);
    }
    if (oldWidget.expandToFullScreen != widget.expandToFullScreen ||
        oldWidget.previewHeight != widget.previewHeight) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _syncDetents());
    }
  }

  @override
  void dispose() {
    widget.controller?.detach(this);
    _extent.dispose();
    _layout.dispose();
    _scroll.dispose();
    super.dispose();
  }

  // ── Controller client ──────────────────────────────────────────────────

  @override
  M3EBottomSheetValue get value => _value;

  @override
  List<M3EBottomSheetValue> get availableValues =>
      _detents?.values ?? const <M3EBottomSheetValue>[];

  @override
  double get extent => _extent.value;

  @override
  Future<void> animateTo(M3EBottomSheetValue value) async {
    final M3EBottomSheetDetents? detents = _detents;
    if (detents == null) {
      return;
    }
    await _settleTo(detents.resolve(value));
  }

  @override
  Future<void> cycle() async {
    final M3EBottomSheetDetents? detents = _detents;
    if (detents == null) {
      return;
    }
    final List<M3EBottomSheetValue> values = detents.values;
    final M3EBottomSheetValue current = values.contains(_value)
        ? _value
        : detents.nearest(_extent.value);
    final int index = values.indexOf(current);
    if (index < values.length - 1) {
      return _settleTo(values[index + 1]);
    }
    if (_theme.handleCyclesToClose && _canHide) {
      return _settleTo(M3EBottomSheetValue.hidden);
    }
    return _settleTo(values.first);
  }

  @override
  void close([Object? result]) {
    final ValueChanged<Object?>? onClose = widget.onClose;
    if (onClose != null) {
      onClose(result);
      return;
    }
    _settleTo(M3EBottomSheetValue.hidden);
  }

  // ── Drag target ────────────────────────────────────────────────────────

  @override
  bool get isAtMax {
    final M3EBottomSheetDetents? detents = _detents;
    return detents == null || _extent.value >= detents.max - 0.5;
  }

  @override
  bool get canDrag => widget.enableDrag && _detents != null;

  @override
  void dragBy(double delta) {
    final M3EBottomSheetDetents? detents = _detents;
    if (detents == null) {
      return;
    }
    _runId++;
    _extent
      ..stop()
      ..value = clampDouble(_extent.value - delta, 0, detents.max);
  }

  @override
  void settle(double velocity) {
    final M3EBottomSheetDetents? detents = _detents;
    if (detents == null) {
      return;
    }
    var target = _targetFor(detents, _extent.value, velocity);
    if (target == M3EBottomSheetValue.hidden && !_canHide) {
      target = detents.values.first;
    }
    _settleTo(target, velocity: velocity);
  }

  M3EBottomSheetValue _targetFor(
    M3EBottomSheetDetents detents,
    double x,
    double velocity,
  ) {
    if (velocity.abs() >= _theme.dismissVelocity) {
      return velocity > 0
          ? detents.above(x) ?? detents.values.last
          : detents.below(x) ?? M3EBottomSheetValue.hidden;
    }
    if (x < detents.min * _theme.dismissThresholdFraction) {
      return M3EBottomSheetValue.hidden;
    }
    return detents.nearest(x);
  }

  // ── Motion ─────────────────────────────────────────────────────────────

  Future<void> _settleTo(
    M3EBottomSheetValue target, {
    double velocity = 0,
    M3ESpring? spring,
  }) async {
    final M3EBottomSheetDetents? detents = _detents;
    if (detents == null || !mounted) {
      return;
    }
    if (target == M3EBottomSheetValue.hidden && _modal) {
      return _dismissModal(detents);
    }
    final bool done = await _run(
      detents.heightOf(target),
      velocity,
      spring ?? _theme.settleSpring,
    );
    if (done && mounted) {
      _setValue(target);
    }
  }

  Future<void> _dismissModal(M3EBottomSheetDetents detents) async {
    final Future<bool> Function()? onDismiss = widget.onDismiss;
    final bool closed = onDismiss == null || await onDismiss();
    if (!closed && mounted) {
      await _settleTo(detents.values.first);
    }
  }

  /// Springs the extent to [to]. True when it got there uninterrupted.
  Future<bool> _run(double to, double velocity, M3ESpring spring) async {
    final int id = ++_runId;
    if (_reduceMotion) {
      _extent.value = to;
      return true;
    }
    try {
      await _extent
          .animateWith(
            m3eBottomSheetSpring(
              spring,
              from: _extent.value,
              to: to,
              velocity: velocity,
            ),
          )
          .orCancel;
    } on TickerCanceled {
      return false;
    }
    return id == _runId;
  }

  void _setValue(M3EBottomSheetValue value) {
    if (_value == value) {
      return;
    }
    setState(() => _value = value);
    widget.controller?.updateValue(value);
    widget.onValueChanged?.call(value);
  }

  // ── Layout ─────────────────────────────────────────────────────────────

  void _noteArea(Size area, double topPadding) {
    final bool wide = area.width > _theme.wideBreakpoint;
    if (_wide == null) {
      _wide = wide;
      _layout.value = wide ? 1 : 0;
    } else if (_wide != wide) {
      _wide = wide;
      WidgetsBinding.instance.addPostFrameCallback((_) => _springLayout());
    }
    if (area != _area || topPadding != _topPadding) {
      _area = area;
      _topPadding = topPadding;
      WidgetsBinding.instance.addPostFrameCallback((_) => _syncDetents());
    }
  }

  void _springLayout() {
    if (!mounted) {
      return;
    }
    final double target = (_wide ?? false) ? 1 : 0;
    if (_reduceMotion) {
      _layout.value = target;
      return;
    }
    _layout.animateWith(
      m3eBottomSheetSpring(
        _theme.layoutSpring,
        from: _layout.value,
        to: target,
        velocity: _layout.velocity,
      ),
    );
  }

  void _onSize(Size size) {
    if (!mounted || size.height == _sheetHeight) {
      return;
    }
    _sheetHeight = size.height;
    _syncDetents();
  }

  void _syncDetents() {
    if (!mounted || _sheetHeight <= 0 || _area.isEmpty) {
      return;
    }
    final next = M3EBottomSheetDetents.resolve(
      contentHeight: _sheetHeight,
      screenHeight: _area.height,
      topMargin: _topMargin,
      initialFraction: _theme.initialHeightFraction,
      fullScreen: widget.expandToFullScreen,
      previewHeight: _modal ? null : widget.previewHeight,
    );
    final M3EBottomSheetDetents? previous = _detents;
    if (next == previous) {
      return;
    }
    setState(() => _detents = next);
    if (previous == null) {
      final M3EBottomSheetValue first = next.resolve(widget.initialValue);
      _extent.value = next.heightOf(first);
      _setValue(first);
      return;
    }
    if (!_dragging && _value != M3EBottomSheetValue.hidden) {
      // While the route is exiting, the sheet travels down. Content or system
      // bar changes can still re-resolve the detents, and settling the new
      // height here would pull the sheet back up mid-exit (a visible bounce).
      final AnimationStatus? routeStatus = ModalRoute.of(context)
          ?.animation
          ?.status;
      final bool exiting =
          routeStatus == AnimationStatus.reverse ||
          routeStatus == AnimationStatus.dismissed;
      if (!exiting) {
        _settleTo(next.resolve(_value), spring: _theme.layoutSpring);
      }
    }
  }

  void _setDragging(bool value) {
    if (_dragging != value) {
      setState(() => _dragging = value);
    }
  }

  @override
  Widget build(BuildContext context) => _buildFrame(context);
}
