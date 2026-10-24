import 'dart:math' as math;
import 'dart:ui' show lerpDouble;

import 'package:flutter/gestures.dart' show kTouchSlop;
import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../../cards/m3e_cards.dart';
import '../components/m3e_card_radius_motion.dart';
import '../components/m3e_list_drag_proxy_scope.dart';
import '../components/m3e_list_feature_scope.dart';
import '../components/m3e_list_item_scope.dart';
import '../components/m3e_list_reorder_session_scope.dart';
import '../components/m3e_list_row_surface.dart';
import '../components/m3e_list_swipe_action_button.dart';
import '../components/m3e_list_swipe_overflow.dart';
import '../enums/m3e_list_enums.dart';
import '../enums/m3e_list_selection_enums.dart';
import '../enums/m3e_list_swipe_edge.dart';
import '../enums/m3e_list_swipe_mode.dart';
import '../models/m3e_dismissible_slot.dart';
import '../models/m3e_list_swipe_action.dart';
import '../styles/m3e_dismissible_list_style.dart';
import '../utils/m3e_list_selection_fill.dart';
import 'm3e_dismissible_list_controller.dart';

part 'm3e_dismissible_card_build_mixin.dart';
part 'm3e_dismissible_card_collapse_render.dart';
part 'm3e_dismissible_card_drag_mixin.dart';
part 'm3e_dismissible_card_spring_settle.dart';

const int _kVibrationThresholdMs = 60;
const double _kMaxPreDetachRoundness = 0.6;
const double _kPreThresholdRoundnessScale = 0.4;
const double _kDetachPushPixels = 30;

/// M3EDismissibleCardMixin.

// ─────────────────────────────────────────────────────────────────────────────
// Mixin — all shared drag / animation / build logic
// ─────────────────────────────────────────────────────────────────────────────

mixin M3EDismissibleCardMixin<T extends StatefulWidget>
    on State<T>, TickerProviderStateMixin<T> {
  /// The swipeItemCount.
  int get swipeItemCount;

  /// swipeItemBuilder.
  Widget swipeItemBuilder(BuildContext context, int dataIndex);

  /// The style.
  M3EDismissibleListStyle get style;

  SpringMotion _spatialMotion(M3ESpring spring, {double? stiffness}) =>
      const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: stiffness ?? spring.stiffness,
        damping: spring.damping,
      );

  /// Callback invoked when a dismissible item is dismissed.
  Future<bool> Function(int index, DismissDirection direction)?
  get onDismissCallback;

  /// The Function.
  void Function(int index)? get onTapCallback;

  /// Optional long-press callback (visible item index).
  void Function(int index)? get onLongPressCallback => null;

  /// Optional per-index card color override.
  Color? Function(int index)? get colorBuilder => null;

  /// Optional per-index border radius override.
  BorderRadius? Function(int index, M3ECardPosition position)?
  get borderRadiusBuilder => null;

  /// Start-to-end swipe actions for a data index.
  List<M3EListSwipeAction> Function(int index)? get leadingActionsBuilder =>
      null;

  /// End-to-start swipe actions for a data index.
  List<M3EListSwipeAction> Function(int index)? get trailingActionsBuilder =>
      null;

  /// When true, all cards use [M3EDismissibleListStyle.innerRadius].
  bool get embedded => false;

  /// When true, long-press is owned by the list reorder host.
  bool get listReorderEnabled => false;

  final List<M3EDismissibleSlot> _slots = [];
  M3EDismissibleSlot? _dragSlotRef;
  int _dragSlotIndex = -1;
  double _dragOffset = 0;
  bool _pastThreshold = false;
  bool _pastActionThreshold = false;
  bool _isDismissDragging = false;
  bool _reEngaging = false;
  double _neighbourFraction = 0;
  double _roundnessFraction = 0;
  double _detachPush = 0;
  int _collapsingCount = 0;
  final Stopwatch _hapticStopwatch = Stopwatch()..start();
  final Map<M3EDismissibleSlot, GlobalKey> _measureKeys = {};

  SingleMotionController? _springCtrl;
  SingleMotionController? _nbrCtrl;
  SingleMotionController? _pushCtrl;
  SingleMotionController? _roundnessCtrl;

  /// computeVisibleIndices.

  List<int> computeVisibleIndices() => [
    for (int i = 0; i < _slots.length; i++)
      if (_slots[i].isVisible) i,
  ];

  /// The slots.

  List<M3EDismissibleSlot> get slots => List.unmodifiable(_slots);

  /// The isInteractionLocked.
  bool get isInteractionLocked => _dragSlotRef != null || _collapsingCount > 0;

  /// Gesture policy. Reveal never dismisses. Dismiss never settles open.
  M3EListSwipeMode get swipeMode => M3EListSwipeMode.both;

  /// Which direction a manual swipe is allowed to dismiss.
  M3EListSwipeEdge get dismissEdge => M3EListSwipeEdge.both;

  /// When true, [swipeItemBuilder] already paints the row surface.
  bool swipeItemPaintsSurface(int dataIndex) => false;

  /// Accumulated horizontal delta before dismiss locks (reorder-safe).
  double _dismissDxAcc = 0;

  /// True while a row is held open on its action preview.
  bool get isActionPreviewOpen =>
      _dragSlotRef != null && (_pastActionThreshold || _dragOffset.abs() > 0.5);

  /// Stays set through the return spring, including frames where a controller
  /// is between ticks.
  bool _hoverLocked = false;

  /// True while dismiss drag or settle springs are moving cards.
  bool get _suppressCardHover {
    if (_isDismissDragging || _hoverLocked) {
      return true;
    }
    return _hoverMotionActive;
  }

  bool get _hoverMotionActive =>
      _isMotionAnimating(_springCtrl) ||
      _isMotionAnimating(_nbrCtrl) ||
      _isMotionAnimating(_pushCtrl) ||
      _isMotionAnimating(_roundnessCtrl);

  void _lockHover() {
    _hoverLocked = true;
  }

  void _unlockHoverIfIdle() {
    if (_isDismissDragging || _hoverMotionActive) {
      return;
    }
    _hoverLocked = false;
  }

  bool _isMotionAnimating(SingleMotionController? controller) =>
      controller != null && controller.isAnimating;

  void _onMotionSettled(AnimationStatus status) {
    if (status != AnimationStatus.completed &&
        status != AnimationStatus.dismissed) {
      return;
    }
    if (mounted) {
      setState(_unlockHoverIfIdle);
    }
  }

  /// Actions for the given data index in the swipe direction (empty if none).
  List<M3EListSwipeAction> actionsFor(
    int dataIndex, {
    required bool swipingRight,
  }) {
    final List<M3EListSwipeAction>? built = swipingRight
        ? leadingActionsBuilder?.call(dataIndex)
        : trailingActionsBuilder?.call(dataIndex);
    return built ?? const <M3EListSwipeAction>[];
  }

  double _computeActionsWidth(List<M3EListSwipeAction> actionList) {
    if (actionList.isEmpty) {
      return 0;
    }
    var total = 0.0;
    for (final action in actionList) {
      total += action.width;
    }
    if (actionList.length > 1) {
      total += (actionList.length - 1) * style.actionSpacing;
    }
    return total += 2 * style.actionEdgePadding;
  }

  /// Data index of the row currently being swiped, if any.
  int? get activeSwipeIndex => _dataIndexForDragSlot();

  int? _dataIndexForDragSlot() {
    if (_dragSlotIndex < 0) {
      return null;
    }
    final int dataIndex = computeVisibleIndices().indexOf(_dragSlotIndex);
    return dataIndex < 0 ? null : dataIndex;
  }

  /// initSlots.

  void initSlots() => _syncSlots();

  /// syncSlotsIfNeeded.

  void syncSlotsIfNeeded(int oldItemCount) {
    if (swipeItemCount != oldItemCount) {
      _syncSlots();
    }
  }

  /// disposeSlots.

  void disposeSlots() {
    _springCtrl?.dispose();
    _nbrCtrl?.dispose();
    _pushCtrl?.dispose();
    _roundnessCtrl?.dispose();
    for (final slot in _slots) {
      slot
        ..dispose()
        ..disposeFlyNotifier();
    }
    _collapsingCount = 0;
  }

  void _syncSlots() {
    final visibleCount = _slots.where((s) => s.isVisible).length;
    if (visibleCount > swipeItemCount) {
      _removeExcessVisibleSlots(visibleCount - swipeItemCount);
    } else if (visibleCount < swipeItemCount) {
      _addMissingSlots(swipeItemCount - visibleCount);
    }
    _reindexDragSlot();
  }

  void _removeExcessVisibleSlots(int toRemove) {
    var remaining = toRemove;
    for (var i = _slots.length - 1; i >= 0 && remaining > 0; i--) {
      if (!_slots[i].isVisible) {
        continue;
      }
      final slot = _slots[i];
      _slots.removeAt(i);
      _measureKeys.remove(slot);
      slot.dispose();
      remaining--;
    }
  }

  void _addMissingSlots(int toAdd) {
    for (var i = 0; i < toAdd; i++) {
      _slots.add(M3EDismissibleSlot());
    }
  }

  void _reindexDragSlot() {
    if (_dragSlotRef == null) {
      _dragSlotIndex = -1;
      return;
    }
    _dragSlotIndex = _slots.indexOf(_dragSlotRef!);
    if (_dragSlotIndex < 0) {
      _dragSlotRef = null;
      _dragOffset = 0.0;
      _detachPush = 0.0;
    }
  }

  GlobalKey _measureKey(M3EDismissibleSlot slot) =>
      _measureKeys.putIfAbsent(slot, () => GlobalKey());

  Size _cardSize(M3EDismissibleSlot slot) {
    final box =
        _measureKeys[slot]?.currentContext?.findRenderObject() as RenderBox?;
    if (box == null || !box.hasSize) {
      return const Size(320, 52);
    }
    return box.size;
  }

  double get _dragProgress {
    if (_dragSlotRef == null) {
      return 0;
    }
    final w = _cardSize(_dragSlotRef!).width;
    return (_dragOffset.abs() / (w * style.dismissThreshold)).clamp(0.0, 1.0);
  }

  /// computeRadius.

  BorderRadius computeRadius(
    int slotIndex,
    int slotPos,
    int dragPos,
    List<int> visible,
  ) {
    final s = style;
    if (slotPos < 0) {
      return BorderRadius.circular(s.outerRadius);
    }

    final total = visible.length;
    final isFirst = slotPos == 0;
    final isLast = slotPos == total - 1;
    final or = s.outerRadius;
    final sr = s.selectedBorderRadius ?? or;
    final ir = s.innerRadius;

    final BorderRadius? early = _computeRadiusEarlyExit(
      slotIndex: slotIndex,
      total: total,
      or: or,
      sr: sr,
      ir: ir,
      isFirst: isFirst,
      isLast: isLast,
      dragPos: dragPos,
      slotPos: slotPos,
    );
    if (early != null) {
      return early;
    }

    final facingR = lerpDouble(ir, or, _roundnessFraction)!;
    final subtleR = _pastThreshold
        ? ir
        : lerpDouble(ir, or, _roundnessFraction * 0.3)!;
    return _computeDragNeighborRadius(
      slotIndex: slotIndex,
      slotPos: slotPos,
      dragPos: dragPos,
      isFirst: isFirst,
      isLast: isLast,
      or: or,
      sr: sr,
      facingR: facingR,
      subtleR: subtleR,
    );
  }

  BorderRadius? _computeRadiusEarlyExit({
    required int slotIndex,
    required int total,
    required double or,
    required double sr,
    required double ir,
    required bool isFirst,
    required bool isLast,
    required int dragPos,
    required int slotPos,
  }) {
    if (embedded) {
      if (slotIndex == _dragSlotIndex && _pastThreshold) {
        return BorderRadius.circular(sr);
      }
      return BorderRadius.circular(ir);
    }
    if (total == 1) {
      return BorderRadius.circular(or);
    }
    if (dragPos < 0 || (slotPos - dragPos).abs() > 1) {
      return BorderRadius.only(
        topLeft: Radius.circular(isFirst ? or : ir),
        topRight: Radius.circular(isFirst ? or : ir),
        bottomLeft: Radius.circular(isLast ? or : ir),
        bottomRight: Radius.circular(isLast ? or : ir),
      );
    }
    return null;
  }

  BorderRadius _computeDragNeighborRadius({
    required int slotIndex,
    required int slotPos,
    required int dragPos,
    required bool isFirst,
    required bool isLast,
    required double or,
    required double sr,
    required double facingR,
    required double subtleR,
  }) {
    final isDragged = slotIndex == _dragSlotIndex;
    if (isDragged) {
      if (_pastThreshold) {
        return BorderRadius.circular(sr);
      }
      return BorderRadius.only(
        topLeft: Radius.circular(isFirst ? or : facingR),
        topRight: Radius.circular(isFirst ? or : facingR),
        bottomLeft: Radius.circular(isLast ? or : facingR),
        bottomRight: Radius.circular(isLast ? or : facingR),
      );
    }
    if (slotPos < dragPos) {
      return BorderRadius.only(
        topLeft: Radius.circular(isFirst ? or : subtleR),
        topRight: Radius.circular(isFirst ? or : subtleR),
        bottomLeft: Radius.circular(isLast ? or : facingR),
        bottomRight: Radius.circular(isLast ? or : facingR),
      );
    }
    return BorderRadius.only(
      topLeft: Radius.circular(isFirst ? or : facingR),
      topRight: Radius.circular(isFirst ? or : facingR),
      bottomLeft: Radius.circular(isLast ? or : subtleR),
      bottomRight: Radius.circular(isLast ? or : subtleR),
    );
  }

  /// computeNeighbourOffset.

  // Implemented by M3EDismissibleCardDragMixin / M3EDismissibleCardBuildMixin
  double computeNeighbourOffset(int slotPos, int dragPos);

  /// handleDragStart.
  void handleDragStart(M3EDismissibleSlot slot);

  /// handleDragUpdate.
  void handleDragUpdate(DragUpdateDetails d);

  /// handleDragEnd.
  void handleDragEnd(DragEndDetails d);

  /// buildSlot.
  ///
  /// When [suppressOwnGap] is true (building for the reorder host), the
  /// row's own trailing gap is omitted so the host can apply it externally,
  /// detached from the dragged card (see `M3EListReorderHost`).
  Widget buildSlot(
    BuildContext context,
    int slotIndex, {
    List<int>? visible,
    bool suppressOwnGap = false,
  });
}
