import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_card_group_layout.dart';
import '../styles/m3e_card_theme.dart';
import 'm3e_card_group_scope.dart';

/// A collection of cards that share a gap, elevation, and layout.
///
/// Filter and sort controls stay outside this group. A long-press drag
/// reorders when [onReorder] is set: the card lifts, neighbors spring toward
/// the opening, and the card settles before [onReorder] runs.
class M3ECardGroup extends StatefulWidget {
  /// Creates a card group.
  const M3ECardGroup({
    required this.children,
    this.layout = M3ECardGroupLayout.grid,
    this.onReorder,
    this.adaptOrientation = false,
    this.elevation,
    this.gap,
    super.key,
  });

  /// Cards to lay out.
  final List<Widget> children;

  /// Grid, staggered columns, vertical list, or horizontal row.
  final M3ECardGroupLayout layout;

  /// Called with the picked index and the index under the pointer on release.
  final void Function(int oldIndex, int newIndex)? onReorder;

  /// On expanded windows, lays a horizontal card out vertically.
  final bool adaptOrientation;

  /// Resting elevation for children that do not set their own.
  final double? elevation;

  /// Gap between cards. Null uses the theme gap, clamped to the theme max.
  final double? gap;

  @override
  State<M3ECardGroup> createState() => _M3ECardGroupState();
}

class _M3ECardGroupState extends State<M3ECardGroup>
    with TickerProviderStateMixin {
  final List<GlobalKey> _keys = <GlobalKey>[];
  final List<SingleMotionController> _shiftX = <SingleMotionController>[];
  final List<SingleMotionController> _shiftY = <SingleMotionController>[];
  final List<Offset> _shiftGoals = <Offset>[];
  final List<Offset> _slotOrigins = <Offset>[];
  late final SingleMotionController _lift;
  late final SingleMotionController _settle;
  int? _dragIndex;
  int? _targetIndex;
  int? _dropFrom;
  int? _dropTo;
  bool _settling = false;
  Offset _pointer = Offset.zero;
  Offset _grab = Offset.zero;
  Offset _dragOrigin = Offset.zero;
  Offset _settleFrom = Offset.zero;
  Offset _settleTo = Offset.zero;
  Size _dragSize = Size.zero;

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
    _syncKeys();
  }

  @override
  void didUpdateWidget(M3ECardGroup oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncKeys();
  }

  @override
  void dispose() {
    _lift.dispose();
    _settle.dispose();
    for (final SingleMotionController shift in _shiftX) {
      shift.dispose();
    }
    for (final SingleMotionController shift in _shiftY) {
      shift.dispose();
    }
    super.dispose();
  }

  void _onMotion() {
    if (!mounted) {
      return;
    }
    final int? from = _dropFrom;
    final int? to = _dropTo;
    if (_settling && from != null && to != null && _settle.value >= 1) {
      _commitReorder(from, to);
      return;
    }
    setState(() {});
  }

  void _commitReorder(int from, int to) {
    if (!mounted || _dragIndex == null) {
      return;
    }
    _settling = false;
    _dragIndex = null;
    _targetIndex = null;
    _dropFrom = null;
    _dropTo = null;
    _settle.stop();
    for (var i = 0; i < _shiftGoals.length; i++) {
      _shiftGoals[i] = Offset.zero;
      _shiftX[i]
        ..stop()
        ..value = 0;
      _shiftY[i]
        ..stop()
        ..value = 0;
    }
    setState(() {});
    if (from != to) {
      widget.onReorder?.call(from, to);
    }
  }

  void _syncKeys() {
    while (_keys.length < widget.children.length) {
      _keys.add(GlobalKey());
      _shiftX.add(_shiftController());
      _shiftY.add(_shiftController());
      _shiftGoals.add(Offset.zero);
    }
    while (_keys.length > widget.children.length) {
      _keys.removeLast();
      _shiftX.removeLast().dispose();
      _shiftY.removeLast().dispose();
      _shiftGoals.removeLast();
    }
  }

  SingleMotionController _shiftController() {
    return SingleMotionController(
      motion: _motion(M3EMotion.expressiveSpatialDefault),
      vsync: this,
    )..addListener(_onMotion);
  }

  void _startDrag(int index, LongPressStartDetails details) {
    if (widget.onReorder == null || _settling || _dragIndex != null) {
      return;
    }
    final box = _keys[index].currentContext?.findRenderObject() as RenderBox?;
    final group = context.findRenderObject() as RenderBox?;
    if (box == null || group == null || !box.hasSize) {
      return;
    }
    final Offset origin = group.globalToLocal(box.localToGlobal(Offset.zero));
    final Offset pointer = group.globalToLocal(details.globalPosition);
    _captureSlots(group);
    setState(() {
      _dragIndex = index;
      _targetIndex = index;
      _dragSize = box.size;
      _dragOrigin = origin;
      _pointer = pointer;
      _grab = pointer - origin;
    });
    M3EHaptics.trigger(M3EHapticFeedback.medium);
    _lift
      ..stop()
      ..value = 0
      ..animateTo(1);
    _settle
      ..stop()
      ..value = 0;
    _retarget(index);
  }

  void _captureSlots(RenderBox group) {
    _slotOrigins.clear();
    for (final GlobalKey key in _keys) {
      final box = key.currentContext?.findRenderObject() as RenderBox?;
      _slotOrigins.add(
        box != null && box.hasSize
            ? group.globalToLocal(box.localToGlobal(Offset.zero))
            : Offset.zero,
      );
    }
  }

  void _updateDrag(LongPressMoveUpdateDetails details) {
    final int? from = _dragIndex;
    if (from == null || _settling) {
      return;
    }
    final group = context.findRenderObject() as RenderBox?;
    if (group == null) {
      return;
    }
    final Offset pointer = group.globalToLocal(details.globalPosition);
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
    for (var i = 0; i < _shiftGoals.length && i < _slotOrigins.length; i++) {
      final int visual = _visualSlotFor(i, from: from, to: to);
      final Offset target = i == from
          ? Offset.zero
          : _slotOrigins[visual] - _slotOrigins[i];
      if (_shiftGoals[i] == target) {
        continue;
      }
      _shiftGoals[i] = target;
      _shiftX[i].animateTo(target.dx);
      _shiftY[i].animateTo(target.dy);
    }
  }

  /// Slot that card [i] visually occupies while the dragged card moves from
  /// [from] to [to], filling the gap it leaves behind.
  int _visualSlotFor(int i, {required int from, required int to}) {
    if (i == from) {
      return i;
    }
    if (from < to && i > from && i <= to) {
      return i - 1;
    }
    if (from > to && i >= to && i < from) {
      return i + 1;
    }
    return i;
  }

  void _endDrag(LongPressEndDetails details) {
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
      _commitReorder(from, to);
      return;
    }
    final Velocity velocity = details.velocity;
    final double along =
        (velocity.pixelsPerSecond.dx * travel.dx +
            velocity.pixelsPerSecond.dy * travel.dy) /
        travel.distance;
    _settle
      ..stop()
      ..value = 0
      ..animateTo(
        1,
        withVelocity: (along / travel.distance).clamp(-10.0, 10.0),
      ).whenComplete(() => _commitReorder(from, to));
  }

  @override
  Widget build(BuildContext context) {
    final cardTheme = M3ETheme.of(context).cardTheme;
    final double gap = cardTheme.resolveGap(widget.gap);
    return M3EComponentTheme(
      builder: (BuildContext context) {
        return LayoutBuilder(
          builder: (BuildContext context, BoxConstraints constraints) {
            final double width = constraints.maxWidth;
            final bool expanded = width >= cardTheme.expandedBreakpoint;
            final bool vertical = widget.adaptOrientation && expanded;
            final Widget layout = _layout(width, gap, cardTheme);
            return M3ECardGroupScope(
              vertical: vertical,
              restingElevation: widget.elevation,
              child: Stack(
                clipBehavior: Clip.none,
                children: <Widget>[
                  layout,
                  if (_dragIndex != null) _dragProxy(vertical),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Widget _dragProxy(bool vertical) {
    final int index = _dragIndex!;
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
        child: M3ECardGroupScope(
          vertical: vertical,
          dragged: true,
          restingElevation: widget.elevation,
          child: widget.children[index],
        ),
      ),
    );
  }

  Widget _layout(double width, double gap, M3ECardTheme cardTheme) {
    if (widget.layout == M3ECardGroupLayout.carousel) {
      return _carousel(gap, cardTheme);
    }
    if (widget.layout == M3ECardGroupLayout.list ||
        width < cardTheme.compactBreakpoint) {
      return _column(gap, stretch: true);
    }
    final int columns = _columnCount(width, gap, cardTheme);
    if (widget.layout == M3ECardGroupLayout.staggered) {
      return _staggered(columns, gap);
    }
    return _grid(columns, gap);
  }

  int _columnCount(double width, double gap, M3ECardTheme cardTheme) {
    final minWidth = cardTheme.columnMinWidth;
    final fit = ((width + gap) / (minWidth + gap)).floor().clamp(1, 12);
    if (width >= cardTheme.expandedBreakpoint && fit < 2) {
      return 1;
    }
    return fit;
  }

  Widget _slot(int index, {double? width}) {
    final dragging = _dragIndex == index;
    Widget child = dragging
        ? SizedBox(width: _dragSize.width, height: _dragSize.height)
        : widget.children[index];
    if (width != null) {
      child = SizedBox(width: width, child: child);
    }
    if (index < _shiftX.length && widget.onReorder != null) {
      final shift = Offset(_shiftX[index].value, _shiftY[index].value);
      if (shift != Offset.zero) {
        child = Transform.translate(offset: shift, child: child);
      }
    }
    child = KeyedSubtree(key: _keys[index], child: child);
    if (widget.onReorder == null) {
      return child;
    }
    return GestureDetector(
      onLongPressStart: (LongPressStartDetails details) =>
          _startDrag(index, details),
      onLongPressMoveUpdate: _updateDrag,
      onLongPressEnd: _endDrag,
      child: child,
    );
  }

  Widget _column(double gap, {required bool stretch}) {
    return Column(
      crossAxisAlignment: stretch
          ? CrossAxisAlignment.stretch
          : CrossAxisAlignment.start,
      children: <Widget>[
        for (var i = 0; i < widget.children.length; i++) ...<Widget>[
          if (i > 0) SizedBox(height: gap),
          _slot(i),
        ],
      ],
    );
  }

  Widget _grid(int columns, double gap) {
    return Column(
      children: <Widget>[
        for (
          var row = 0;
          row < widget.children.length;
          row += columns
        ) ...<Widget>[
          if (row > 0) SizedBox(height: gap),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              for (var column = 0; column < columns; column++) ...<Widget>[
                if (column > 0) SizedBox(width: gap),
                Expanded(
                  child: row + column < widget.children.length
                      ? _slot(row + column)
                      : const SizedBox.shrink(),
                ),
              ],
            ],
          ),
        ],
      ],
    );
  }

  Widget _staggered(int columns, double gap) {
    final buckets = List<List<int>>.generate(columns, (_) => <int>[]);
    for (var i = 0; i < widget.children.length; i++) {
      buckets[i % columns].add(i);
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        for (var column = 0; column < columns; column++) ...<Widget>[
          if (column > 0) SizedBox(width: gap),
          Expanded(
            child: Column(
              children: <Widget>[
                for (var i = 0; i < buckets[column].length; i++) ...<Widget>[
                  if (i > 0) SizedBox(height: gap),
                  _slot(buckets[column][i]),
                ],
              ],
            ),
          ),
        ],
      ],
    );
  }

  Widget _carousel(double gap, M3ECardTheme cardTheme) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: <Widget>[
          for (var i = 0; i < widget.children.length; i++) ...<Widget>[
            if (i > 0) SizedBox(width: gap),
            _slot(i, width: cardTheme.carouselCardWidth),
          ],
        ],
      ),
    );
  }
}
