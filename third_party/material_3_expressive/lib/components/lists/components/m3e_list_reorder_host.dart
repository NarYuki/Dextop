import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_list_reorder_state.dart';
import 'm3e_list_drag_proxy_scope.dart';
import 'm3e_list_reorder_exclude.dart';
import 'm3e_list_reorder_placeholder.dart';
import 'm3e_list_reorder_session_scope.dart';

/// Spring-driven reorderable list.
///
/// Layout slots stay fixed while dragging: the dragged row becomes an invisible
/// spacer, a floating proxy follows the pointer, and neighbors spring-shift to
/// open the destination gap. A placeholder fill marks that gap and
/// spring-follows it between slots. On drop the proxy eases into that gap, then the
/// order changes.
class M3EListReorderHost extends StatefulWidget {
  /// Creates a reorder host.
  const M3EListReorderHost({
    required this.itemCount,
    required this.itemBuilder,
    required this.onReorder,
    required this.reorderState,
    this.gap = 4,
    this.scrollable = false,
    this.controller,
    this.physics,
    this.shrinkWrap = false,
    this.padding,
    this.prepareDrag,
    this.onDragSettled,
    this.canStartDrag,
    super.key,
  });

  /// Number of items.
  final int itemCount;

  /// Builds each item at the given index.
  final IndexedWidgetBuilder itemBuilder;

  /// Called after a successful drop.
  final ReorderCallback onReorder;

  /// Reorder visuals / motion.
  final M3EListReorderState reorderState;

  /// Gap between items.
  final double gap;

  /// Whether to wrap in a [ListView].
  final bool scrollable;

  /// Scroll controller when [scrollable].
  final ScrollController? controller;

  /// Scroll physics when [scrollable].
  final ScrollPhysics? physics;

  /// Shrink-wrap when [scrollable].
  final bool shrinkWrap;

  /// List padding when [scrollable].
  final EdgeInsetsGeometry? padding;

  /// Called when a long-press drag is about to begin (before measuring).
  ///
  /// Awaited so callers can collapse expanded content before the drag extent
  /// is cached.
  final Future<void> Function(int index)? prepareDrag;

  /// Called after drag ends with the from/to indices (to == from if no move).
  ///
  /// Invoked after [onReorder] when the index changed.
  final void Function(int from, int to)? onDragSettled;

  /// When set, long-press only starts a drag if this returns true for the
  /// given index.
  final bool Function(int index)? canStartDrag;

  @override
  State<M3EListReorderHost> createState() => _M3EListReorderHostState();
}

class _M3EListReorderHostState extends State<M3EListReorderHost>
    with TickerProviderStateMixin {
  int? _dragIndex;
  int? _targetIndex;
  int? _dropFrom;
  int? _dropTo;
  int? _activePointer;
  Offset? _pointerDownGlobal;
  Timer? _longPressTimer;
  bool _settling = false;
  PointerDeviceKind _deviceKind = PointerDeviceKind.touch;
  VelocityTracker _velocity = VelocityTracker.withKind(PointerDeviceKind.touch);
  final ValueNotifier<bool> _sessionActive = ValueNotifier<bool>(false);
  late final SingleMotionController _lift;
  late final SingleMotionController _settle;
  late final SingleMotionController _placeholder;
  final GlobalKey _stackKey = GlobalKey();
  final Map<int, GlobalKey> _keys = <int, GlobalKey>{};
  final Map<int, SingleMotionController> _offsets =
      <int, SingleMotionController>{};
  final Map<int, double> _offsetGoals = <int, double>{};
  final List<Offset> _slotOrigins = <Offset>[];
  Offset _pointer = Offset.zero;
  Offset _grab = Offset.zero;
  Offset _dragOrigin = Offset.zero;
  Offset _settleFrom = Offset.zero;
  Offset _settleTo = Offset.zero;
  Size _dragSize = Size.zero;

  GlobalKey _keyFor(int index) => _keys.putIfAbsent(index, GlobalKey.new);

  SingleMotionController _offsetCtrl(int index) {
    return _offsets.putIfAbsent(
      index,
      () => SingleMotionController(
        motion: _motion(M3EMotion.expressiveSpatialDefault),
        vsync: this,
      ),
    );
  }

  SpringMotion _motion(M3ESpring spring) {
    return const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
      stiffness: spring.stiffness,
      damping: spring.damping,
    );
  }

  @override
  void initState() {
    super.initState();
    _lift = SingleMotionController(
      motion: _motion(M3EMotion.effectsFast),
      vsync: this,
    )..addListener(_onMotion);
    _settle = SingleMotionController(
      motion: _motion(M3EMotion.expressiveSpatialDefault),
      vsync: this,
    )..addListener(_onMotion);
    _placeholder = SingleMotionController(
      motion: _motion(M3EMotion.expressiveSpatialDefault),
      vsync: this,
    );
  }

  void _onMotion() {
    if (!mounted) {
      return;
    }
    final int? from = _dropFrom;
    final int? to = _dropTo;
    if (_settling && from != null && to != null && _settle.value >= 1) {
      _finishDrag(from, to);
      return;
    }
    setState(() {});
  }

  @override
  void dispose() {
    _longPressTimer?.cancel();
    _sessionActive.dispose();
    _lift.dispose();
    _settle.dispose();
    _placeholder.dispose();
    for (final SingleMotionController c in _offsets.values) {
      c.dispose();
    }
    super.dispose();
  }

  void _captureSlots(RenderBox stack) {
    _slotOrigins.clear();
    for (var i = 0; i < widget.itemCount; i++) {
      final box = _keyFor(i).currentContext?.findRenderObject() as RenderBox?;
      _slotOrigins.add(
        box != null && box.hasSize
            ? stack.globalToLocal(box.localToGlobal(Offset.zero))
            : Offset.zero,
      );
    }
  }

  void _cancelPendingLongPress() {
    _longPressTimer?.cancel();
    _longPressTimer = null;
  }

  void _onPointerDown(int index, PointerDownEvent event) {
    if (!_canArmLongPress(index, event.position)) {
      return;
    }
    _activePointer = event.pointer;
    _pointerDownGlobal = event.position;
    _deviceKind = event.kind;
    _cancelPendingLongPress();
    _longPressTimer = Timer(
      kLongPressTimeout,
      () => _onLongPressArmed(index, event),
    );
  }

  bool _canArmLongPress(int index, Offset globalPosition) {
    if (_dragIndex != null) {
      return false;
    }
    if (widget.canStartDrag != null && !widget.canStartDrag!(index)) {
      return false;
    }
    return !_isOverReorderExclude(index, globalPosition);
  }

  void _onLongPressArmed(int index, PointerDownEvent event) {
    if (!mounted || _activePointer != event.pointer) {
      return;
    }
    if (widget.canStartDrag != null && !widget.canStartDrag!(index)) {
      return;
    }
    unawaited(_beginDrag(index, event.position, event.pointer));
  }

  /// True when [globalPosition] lies in a descendant [M3EListReorderExclude].
  bool _isOverReorderExclude(int index, Offset globalPosition) {
    final BuildContext? slotContext = _keyFor(index).currentContext;
    if (slotContext is! Element) {
      return false;
    }
    var hit = false;
    void visit(Element element) {
      if (hit) {
        return;
      }
      if (element.widget is M3EListReorderExclude) {
        final RenderObject? renderObject = element.renderObject;
        if (renderObject is RenderBox &&
            renderObject.hasSize &&
            renderObject.attached) {
          final Offset local = renderObject.globalToLocal(globalPosition);
          if ((Offset.zero & renderObject.size).contains(local)) {
            hit = true;
            return;
          }
        }
      }
      element.visitChildren(visit);
    }

    slotContext.visitChildren(visit);
    return hit;
  }

  Future<void> _beginDrag(int index, Offset globalPosition, int pointer) async {
    final Future<void> Function(int index)? prepare = widget.prepareDrag;
    if (prepare != null) {
      await prepare(index);
      if (!mounted || _activePointer != pointer || _dragIndex != null) {
        // Restore any snap-collapse if the drag never started.
        widget.onDragSettled?.call(index, index);
        return;
      }
    }
    _startDrag(index, globalPosition);
  }

  void _onPointerMove(PointerMoveEvent event) {
    if (event.pointer != _activePointer) {
      return;
    }
    if (_dragIndex != null) {
      if (!_settling) {
        _velocity.addPosition(event.timeStamp, event.position);
        _updateDrag(event.position);
      }
      return;
    }
    final Offset? down = _pointerDownGlobal;
    if (down != null && (event.position - down).distance > kTouchSlop) {
      _cancelPendingLongPress();
    }
  }

  void _onPointerUpOrCancel(PointerEvent event) {
    if (event.pointer != _activePointer) {
      return;
    }
    _cancelPendingLongPress();
    _activePointer = null;
    _pointerDownGlobal = null;
    if (_dragIndex != null && !_settling) {
      _endDrag();
    }
  }

  void _startDrag(int index, Offset globalPosition) {
    if (_settling || _dragIndex != null) {
      return;
    }
    if (widget.canStartDrag != null && !widget.canStartDrag!(index)) {
      return;
    }
    final stack = _stackKey.currentContext?.findRenderObject() as RenderBox?;
    final box = _keyFor(index).currentContext?.findRenderObject() as RenderBox?;
    if (stack == null || box == null || !box.hasSize) {
      return;
    }
    final Offset origin = stack.globalToLocal(box.localToGlobal(Offset.zero));
    final Offset pointer = stack.globalToLocal(globalPosition);
    _captureSlots(stack);
    _dragSize = box.size;
    _dragOrigin = origin;
    _pointer = pointer;
    _grab = pointer - origin;
    _velocity = VelocityTracker.withKind(_deviceKind);
    setState(() {
      _dragIndex = index;
      _targetIndex = index;
    });
    _sessionActive.value = true;
    M3EHaptics.trigger(M3EHapticFeedback.medium);
    _lift
      ..stop()
      ..value = 0
      ..animateTo(1);
    _settle
      ..stop()
      ..value = 0;
    _placeholder
      ..stop()
      ..value = _slotOrigins[index].dy;
    _retarget(index);
  }

  void _updateDrag(Offset globalPosition) {
    final int? from = _dragIndex;
    if (from == null || _settling || _slotOrigins.isEmpty) {
      return;
    }
    final stack = _stackKey.currentContext?.findRenderObject() as RenderBox?;
    if (stack == null) {
      return;
    }
    final Offset pointer = stack.globalToLocal(globalPosition);
    final Offset center = pointer - _grab + _dragSize.center(Offset.zero);
    var best = from;
    var closest = double.infinity;
    for (var i = 0; i < _slotOrigins.length; i++) {
      final Offset slotCenter = _slotOrigins[i] + _dragSize.center(Offset.zero);
      final double distance = (center - slotCenter).distance;
      if (distance < closest) {
        closest = distance;
        best = i;
      }
    }
    setState(() => _pointer = pointer);
    if (best != _targetIndex) {
      _targetIndex = best;
      _retarget(from);
      M3EHaptics.selection();
    }
  }

  void _retarget(int from) {
    final int to = _targetIndex ?? from;
    if (to < _slotOrigins.length) {
      _placeholder.animateTo(_slotOrigins[to].dy);
    }
    for (var i = 0; i < widget.itemCount && i < _slotOrigins.length; i++) {
      final double target = _retargetOffset(i, from, to);
      if (_offsetGoals[i] == target) {
        continue;
      }
      _offsetGoals[i] = target;
      _offsetCtrl(i)
        ..motion = _motion(M3EMotion.expressiveSpatialDefault)
        ..animateTo(target);
    }
  }

  double _retargetOffset(int i, int from, int to) {
    if (i == from) {
      return 0;
    }
    final int visual = _retargetVisualSlot(i, from, to);
    return _slotOrigins[visual].dy - _slotOrigins[i].dy;
  }

  int _retargetVisualSlot(int i, int from, int to) {
    if (from < to && i > from && i <= to) {
      return i - 1;
    }
    if (from > to && i >= to && i < from) {
      return i + 1;
    }
    return i;
  }

  void _endDrag() {
    final int? from = _dragIndex;
    if (from == null || _settling) {
      return;
    }
    final int to = _targetIndex ?? from;
    final double lift = _lift.value.clamp(0.0, 1.0);
    _settleFrom = (_pointer - _grab) + Offset(12 * lift, 12 * lift);
    _settleTo = to < _slotOrigins.length ? _slotOrigins[to] : _dragOrigin;
    final Offset travel = _settleTo - _settleFrom;
    _dropFrom = from;
    _dropTo = to;
    setState(() => _settling = true);
    if (travel.distance <= 1) {
      _finishDrag(from, to);
      return;
    }
    final Velocity velocity = _velocity.getVelocity();
    final double along =
        (velocity.pixelsPerSecond.dx * travel.dx +
            velocity.pixelsPerSecond.dy * travel.dy) /
        travel.distance;
    _settle
      ..stop()
      ..motion = _motion(widget.reorderState.settleMotion)
      ..value = 0
      ..animateTo(
        1,
        withVelocity: (along / travel.distance).clamp(-10.0, 10.0),
      ).whenComplete(() => _finishDrag(from, to));
  }

  void _finishDrag(int from, int to) {
    if (!mounted || _dragIndex == null) {
      return;
    }
    _settling = false;
    _dragIndex = null;
    _targetIndex = null;
    _dropFrom = null;
    _dropTo = null;
    _settle.stop();
    _placeholder.stop();
    for (final SingleMotionController offset in _offsets.values) {
      offset
        ..stop()
        ..value = 0;
    }
    _offsetGoals.clear();
    _lift
      ..stop()
      ..value = 0;
    _sessionActive.value = false;
    setState(() {});
    if (from != to) {
      widget.onReorder(from, to);
    }
    widget.onDragSettled?.call(from, to);
  }

  Widget _buildSlot(BuildContext context, int index) {
    final Widget built = KeyedSubtree(
      key: _keyFor(index),
      child: widget.itemBuilder(context, index),
    );

    final Widget listening = Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (PointerDownEvent e) => _onPointerDown(index, e),
      onPointerMove: _onPointerMove,
      onPointerUp: _onPointerUpOrCancel,
      onPointerCancel: _onPointerUpOrCancel,
      child: built,
    );

    final isDragSource = _dragIndex == index;

    // Source slot: keep height, hide content (floating proxy paints instead).
    final Widget positioned = isDragSource
        ? IgnorePointer(child: Opacity(opacity: 0, child: listening))
        : AnimatedBuilder(
            animation: _offsetCtrl(index),
            builder: (BuildContext context, Widget? child) {
              return Transform.translate(
                offset: Offset(0, _offsetCtrl(index).value),
                child: child,
              );
            },
            child: listening,
          );

    // Applied outside the keyed subtree so it stays detached from the item:
    // excluded from the measured drag size and the floating proxy's bounds,
    // instead of trailing inside the card like `M3ECardListItem` normally
    // does for non-reorder layouts.
    final isLast = index == widget.itemCount - 1;
    if (widget.gap <= 0 || isLast) {
      return positioned;
    }
    return Padding(
      padding: EdgeInsets.only(bottom: widget.gap),
      child: positioned,
    );
  }

  Widget _buildProxy(BuildContext context) {
    final int dragIndex = _dragIndex!;
    final theme = M3ETheme.of(context);
    final scheme = theme.colorScheme;
    final listTheme = theme.listTheme;
    final M3EListReorderState rs = widget.reorderState;
    final Color dragColor = rs.resolvedDragColor(scheme);
    final double dragRadius = rs.resolvedDragRadius(listTheme);
    final double lift = _lift.value.clamp(0.0, 1.0);
    final Offset moving = (_pointer - _grab) + Offset(12 * lift, 12 * lift);
    final Offset offset = _settling
        ? Offset.lerp(_settleFrom, _settleTo, _settle.value.clamp(0.0, 1.0))!
        : moving;

    return Positioned(
      left: offset.dx,
      top: offset.dy,
      width: _dragSize.width,
      height: _dragSize.height,
      child: IgnorePointer(
        child: PhysicalModel(
          color: dragColor,
          elevation: rs.dragElevation,
          borderRadius: BorderRadius.circular(dragRadius),
          child: M3EListDragProxyScope(
            color: dragColor,
            radius: dragRadius,
            child: widget.itemBuilder(context, dragIndex),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final Widget list = widget.scrollable
        ? ListView.builder(
            controller: widget.controller,
            physics: widget.physics,
            shrinkWrap: widget.shrinkWrap,
            padding: widget.padding,
            itemCount: widget.itemCount,
            itemBuilder: (BuildContext context, int index) =>
                _buildSlot(context, index),
          )
        : Column(
            mainAxisSize: MainAxisSize.min,
            children: List<Widget>.generate(
              widget.itemCount,
              (int index) => _buildSlot(context, index),
            ),
          );

    return M3EListReorderSessionScope(
      active: _sessionActive,
      child: Stack(
        key: _stackKey,
        clipBehavior: Clip.none,
        children: <Widget>[
          // Beneath the list so shifting neighbors cover it as the gap opens.
          if (_dragIndex != null)
            M3EListReorderPlaceholder(
              top: _placeholder,
              left: _slotOrigins.isEmpty ? _dragOrigin.dx : _slotOrigins[0].dx,
              size: _dragSize,
              opacity: _lift.value.clamp(0.0, 1.0),
              reorderState: widget.reorderState,
            ),
          list,
          if (_dragIndex != null) _buildProxy(context),
        ],
      ),
    );
  }
}
