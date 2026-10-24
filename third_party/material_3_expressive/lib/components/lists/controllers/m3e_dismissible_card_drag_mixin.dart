part of 'm3e_dismissible_card_controller.dart';

/// M3EDismissibleCardDragMixin.

mixin M3EDismissibleCardDragMixin<T extends StatefulWidget>
    on M3EDismissibleCardMixin<T> {
  @override
  double computeNeighbourOffset(int slotPos, int dragPos) {
    if (dragPos < 0 || slotPos < 0) {
      return 0;
    }
    final distance = (slotPos - dragPos).abs();
    if (distance == 0 || distance > style.neighbourReach) {
      return 0;
    }

    final reach = style.neighbourReach;
    final falloff = reach > 1 ? (reach - distance) / (reach - 1) : 1.0;

    return _neighbourFraction *
        style.neighbourPull *
        falloff *
        _dragOffset.sign;
  }

  /// handleDragStart.

  @override
  void handleDragStart(M3EDismissibleSlot slot) {
    if (!slot.isVisible || _collapsingCount > 0) {
      return;
    }

    _springCtrl?.stop(canceled: true);
    _nbrCtrl?.stop(canceled: true);
    _pushCtrl?.stop(canceled: true);
    _roundnessCtrl?.stop(canceled: true);

    setState(() {
      final isSameSlot = _dragSlotRef == slot;
      _dragSlotRef = slot;
      _dragSlotIndex = _slots.indexOf(slot);
      _isDismissDragging = true;
      _lockHover();
      if (!isSameSlot) {
        _dragOffset = 0.0;
        _neighbourFraction = 0.0;
        _pastThreshold = false;
        _pastActionThreshold = false;
        _detachPush = 0.0;
        _roundnessFraction = 0.0;
      }
    });
  }

  /// handleDragUpdate.

  @override
  void handleDragUpdate(DragUpdateDetails d) {
    if (_dragSlotRef == null) {
      return;
    }

    final swipeSpeed = d.delta.dx.abs();
    final multiplier = (1.0 + (swipeSpeed / 5.0)).clamp(1.0, 4.0);
    var newOffset = _dragOffset + d.delta.dx;
    var newNeighbour = _neighbourFraction;
    var newRoundness = _roundnessFraction;

    final dataIndex = _dataIndexForDragSlot();
    final swipingRight = newOffset > 0;
    final actionList = dataIndex == null
        ? const <M3EListSwipeAction>[]
        : actionsFor(dataIndex, swipingRight: swipingRight);
    final hasActions = actionList.isNotEmpty;

    if (swipeMode == M3EListSwipeMode.dismiss &&
        newOffset != 0 &&
        !_edgeDismisses(newOffset > 0)) {
      newOffset = 0;
    }

    if (hasActions && swipeMode != M3EListSwipeMode.dismiss) {
      newOffset = _applyActionRevealConstraints(newOffset, actionList);
    }

    final savedOffset = _dragOffset;
    _dragOffset = newOffset;
    final newProgress = _dragProgress;
    _dragOffset = savedOffset;

    final visuals = _applyDismissThresholdVisuals(
      hasActions: hasActions,
      newProgress: newProgress,
      newOffset: newOffset,
      savedOffset: savedOffset,
      multiplier: multiplier,
      neighbour: newNeighbour,
      roundness: newRoundness,
    );
    newNeighbour = visuals.neighbour;
    newRoundness = visuals.roundness;

    setState(() {
      _dragOffset = newOffset;
      _neighbourFraction = newNeighbour;
      _roundnessFraction = newRoundness;
    });
  }

  /// Damps overdrag past action width and updates action-threshold haptics.
  double _applyActionRevealConstraints(
    double newOffset,
    List<M3EListSwipeAction> actionList,
  ) {
    final actionsWidth = _computeActionsWidth(actionList);
    final maxExtent = actionsWidth + style.actionOverdragExtent;
    var offset = newOffset;
    if (offset.abs() > maxExtent) {
      final overdrag = offset.abs() - maxExtent;
      final dampedOverdrag = math.sqrt(overdrag) * 3.0;
      offset = (maxExtent + dampedOverdrag) * offset.sign;
    }

    final crossedAction = offset.abs() >= actionsWidth;
    if (crossedAction && !_pastActionThreshold) {
      _pastActionThreshold = true;
      if (style.enableFeedback) {
        M3EHaptics.trigger(style.hapticOnThreshold);
      }
    } else if (!crossedAction && _pastActionThreshold) {
      _pastActionThreshold = false;
    }
    return offset;
  }

  /// Applies dismiss-threshold cross / re-engage / pre-threshold visuals.
  ({double neighbour, double roundness}) _applyDismissThresholdVisuals({
    required bool hasActions,
    required double newProgress,
    required double newOffset,
    required double savedOffset,
    required double multiplier,
    required double neighbour,
    required double roundness,
  }) {
    // With actions, do not enter full-dismiss threshold visuals.
    final crossedNow = !hasActions && newProgress >= 1.0;
    if (crossedNow && !_pastThreshold) {
      _onCrossThreshold(newOffset, multiplier);
      return (neighbour: neighbour, roundness: roundness);
    }
    if (!crossedNow && _pastThreshold) {
      _onReEngageThreshold(newOffset, savedOffset, multiplier);
      return (neighbour: neighbour, roundness: roundness);
    }
    if (!_pastThreshold) {
      final pre = _onPreThreshold(newProgress);
      return (neighbour: pre.neighbour, roundness: pre.roundness);
    }
    return (neighbour: neighbour, roundness: roundness);
  }

  void _onCrossThreshold(double newOffset, double multiplier) {
    _pastThreshold = true;

    final pushDir = newOffset.sign;
    _startPushController(
      multiplier: multiplier,
      target: style.background == null || style.secondaryBackground == null
          ? pushDir * _kDetachPushPixels
          : 0,
    );
    _startNeighbourController(multiplier: multiplier, target: 0);
    _startRoundnessController(multiplier: multiplier, target: 1);
  }

  void _onReEngageThreshold(
    double newOffset,
    double savedOffset,
    double multiplier,
  ) {
    _pastThreshold = false;
    _reEngaging = true;

    _startPushController(
      multiplier: multiplier,
      initialValue: _detachPush,
      target: 0,
    );

    _dragOffset = newOffset;
    final target = _dragProgress;
    _dragOffset = savedOffset;

    _nbrCtrl?.dispose();
    _nbrCtrl =
        SingleMotionController(
            motion: _spatialMotion(
              style.neighbourSpring,
              stiffness: style.neighbourSpring.stiffness * multiplier,
            ),
            vsync: this,
            initialValue: _neighbourFraction,
          )
          ..addListener(() {
            if (mounted) {
              setState(() => _neighbourFraction = _nbrCtrl!.value);
            }
          })
          ..addStatusListener((s) {
            if (s == AnimationStatus.completed ||
                s == AnimationStatus.dismissed) {
              _reEngaging = false;
              _onMotionSettled(s);
            }
          })
          ..animateTo(target);

    _roundnessCtrl?.dispose();
    _roundnessCtrl =
        SingleMotionController(
            motion: _spatialMotion(
              style.reEngageSpring,
              stiffness: style.reEngageSpring.stiffness * multiplier,
            ),
            vsync: this,
            initialValue: _roundnessFraction,
          )
          ..addListener(() {
            if (mounted) {
              setState(() => _roundnessFraction = _roundnessCtrl!.value);
            }
          })
          ..addStatusListener(_onMotionSettled)
          ..animateTo(target * _kPreThresholdRoundnessScale);
  }

  ({double neighbour, double roundness}) _onPreThreshold(double newProgress) {
    if (_reEngaging) {
      _reEngaging = false;
      _nbrCtrl?.stop(canceled: true);
      _roundnessCtrl?.stop(canceled: true);
    }
    if (style.dismissHapticStream) {
      _playPullHaptics();
    }
    return (
      neighbour: newProgress,
      roundness: (newProgress * _kMaxPreDetachRoundness).clamp(
        0.0,
        _kMaxPreDetachRoundness,
      ),
    );
  }

  /// handleDragEnd.

  @override
  void handleDragEnd(DragEndDetails d) {
    if (_isDismissDragging) {
      setState(() => _isDismissDragging = false);
    }
    if (_dragSlotRef == null) {
      return;
    }
    _reindexDragSlot();
    if (_dragSlotIndex < 0) {
      _resetDragState();
      return;
    }

    final velocity = d.velocity.pixelsPerSecond.dx.abs();
    final speedMul = (1.0 + (velocity / 1000.0)).clamp(1.0, 4.0);

    final bool swipingRight = _dragOffset > 0;
    final int? dataIndex = _dataIndexForDragSlot();
    final List<M3EListSwipeAction> actionList = dataIndex == null
        ? const <M3EListSwipeAction>[]
        : actionsFor(dataIndex, swipingRight: swipingRight);
    final canReveal = swipeMode != M3EListSwipeMode.dismiss;
    final bool canDismiss =
        swipeMode != M3EListSwipeMode.reveal && _edgeDismisses(swipingRight);

    if (canReveal && actionList.isNotEmpty) {
      _settleDragWithActions(
        actionList: actionList,
        swipingRight: swipingRight,
        canDismiss: canDismiss,
        speedMul: speedMul,
      );
      return;
    }

    if (canDismiss && _dragProgress >= 1.0) {
      _dismiss(_dragSlotIndex, speedMul, _dismissDirectionFor(swipingRight));
      return;
    }

    _springBack(speedMul);
  }

  DismissDirection _dismissDirectionFor(bool swipingRight) =>
      swipingRight ? DismissDirection.startToEnd : DismissDirection.endToStart;

  void _settleDragWithActions({
    required List<M3EListSwipeAction> actionList,
    required bool swipingRight,
    required bool canDismiss,
    required double speedMul,
  }) {
    final double actionsWidth = _computeActionsWidth(actionList);
    final bool pastActions = _dragOffset.abs() > actionsWidth;
    if (canDismiss && pastActions && _dragProgress >= 1.0) {
      _dismiss(_dragSlotIndex, speedMul, _dismissDirectionFor(swipingRight));
      return;
    }
    if (_dragOffset.abs() >= actionsWidth * style.actionPreviewThreshold) {
      _snapToRevealed(actionsWidth * (swipingRight ? 1.0 : -1.0), speedMul);
    } else {
      _springBack(speedMul);
    }
  }

  M3EDismissibleListController? _swipeController;

  /// Binds [controller] so reveal and dismiss can run without a drag.
  void bindSwipeController(M3EDismissibleListController? controller) {
    if (identical(_swipeController, controller)) {
      return;
    }
    _swipeController?.detach(_revealIndex);
    _swipeController = controller;
    controller?.attach(
      reveal: _revealIndex,
      dismiss: _dismissIndex,
      close: () => closeActionPreview(),
    );
  }

  /// Drops the swipe controller binding.
  void unbindSwipeController() {
    _swipeController?.detach(_revealIndex);
    _swipeController = null;
  }

  bool _edgeDismisses(bool swipingRight) {
    return switch (dismissEdge) {
      M3EListSwipeEdge.both => true,
      M3EListSwipeEdge.start => swipingRight,
      M3EListSwipeEdge.end => !swipingRight,
    };
  }

  void _revealIndex(int index, {required bool leading}) {
    if (!_armIndex(index)) {
      return;
    }
    final List<M3EListSwipeAction> actionList = actionsFor(
      index,
      swipingRight: leading,
    );
    final double width = actionList.isEmpty
        ? 80
        : _computeActionsWidth(actionList);
    _snapToRevealed(width * (leading ? 1 : -1), 1);
  }

  void _dismissIndex(int index, {required bool leading}) {
    if (!_armIndex(index)) {
      return;
    }
    _dismiss(
      _dragSlotIndex,
      1,
      leading ? DismissDirection.startToEnd : DismissDirection.endToStart,
    );
  }

  bool _armIndex(int index) {
    final List<int> visible = computeVisibleIndices();
    if (index < 0 || index >= visible.length) {
      return false;
    }
    final int slotIndex = visible[index];
    setState(() {
      _dragSlotRef = _slots[slotIndex];
      _dragSlotIndex = slotIndex;
      _isDismissDragging = false;
    });
    return true;
  }

  /// Closes an open action preview.
  void closeActionPreview({double speedMul = 1.0}) {
    if (_dragSlotRef == null) {
      return;
    }
    _springBack(speedMul);
  }

  Future<void> _dismiss(
    int slotIndex,
    double speedMul,
    DismissDirection direction,
  ) async {
    if (slotIndex < 0 || slotIndex >= _slots.length) {
      return;
    }
    final slot = _slots[slotIndex];
    final visible = computeVisibleIndices();
    final dataIndex = visible.indexOf(slotIndex);
    if (dataIndex < 0) {
      return;
    }

    final flyInitial = _captureDismissSlot(slot, dataIndex, direction);
    _disposeDragControllers();
    _markSlotCollapsing(slot);

    M3EHaptics.trigger(style.hapticOnThreshold);

    if (style.autoExecutePrimaryOnFullSwipe) {
      final swipingRight = direction == DismissDirection.startToEnd;
      final List<M3EListSwipeAction> actionList = actionsFor(
        dataIndex,
        swipingRight: swipingRight,
      );
      if (actionList.isNotEmpty) {
        final M3EListSwipeAction primary = actionList.firstWhere(
          (M3EListSwipeAction a) => a.isPrimary,
          orElse: () => actionList.last,
        );
        primary.onPressed?.call();
      }
    }

    final colCtrl = _createCollapseController(slot, speedMul);
    _startFlyOut(slot, flyInitial, speedMul, colCtrl);
    onDismissCallback?.call(dataIndex, direction);
  }

  double _captureDismissSlot(
    M3EDismissibleSlot slot,
    int dataIndex,
    DismissDirection direction,
  ) {
    final size = _cardSize(slot);
    slot
      ..capturedHeight = size.height
      ..capturedWidth = size.width
      ..frozenChild = swipeItemBuilder(context, dataIndex)
      ..dismissedDirection = direction;

    final flyInitial = _dragOffset + _detachPush;
    slot.flyNotifier.value = flyInitial;
    return flyInitial;
  }

  void _disposeDragControllers() {
    _pushCtrl?.dispose();
    _pushCtrl = null;
    _nbrCtrl?.dispose();
    _nbrCtrl = null;
    _roundnessCtrl?.dispose();
    _roundnessCtrl = null;
  }

  void _markSlotCollapsing(M3EDismissibleSlot slot) {
    setState(() {
      slot.markCollapsing();
      _collapsingCount++;
      _dragSlotRef = null;
      _dragSlotIndex = -1;
      _dragOffset = 0.0;
      _detachPush = 0.0;
      _neighbourFraction = 0.0;
      _pastThreshold = false;
      _pastActionThreshold = false;
      _isDismissDragging = false;
      _reEngaging = false;
      _roundnessFraction = 0.0;
      _hoverLocked = false;
    });
  }

  SingleMotionController _createCollapseController(
    M3EDismissibleSlot slot,
    double speedMul,
  ) {
    final colCtrl = SingleMotionController(
      motion: _spatialMotion(
        M3ESpring(
          stiffness: style.collapseSpeed,
          damping: style.collapseDamping,
        ),
        stiffness: style.collapseSpeed * speedMul,
      ),
      vsync: this,
    );
    slot.collapseCtrl = colCtrl;
    colCtrl.addStatusListener((s) {
      if (s == AnimationStatus.completed || s == AnimationStatus.dismissed) {
        _finishCollapse(slot, colCtrl);
      }
    });
    return colCtrl;
  }

  void _finishCollapse(
    M3EDismissibleSlot slot,
    SingleMotionController colCtrl,
  ) {
    if (mounted) {
      final idx = _slots.indexOf(slot);
      if (idx >= 0) {
        setState(() {
          _slots.removeAt(idx);
          _collapsingCount--;
          _reindexDragSlot();
        });
        _measureKeys.remove(slot);
      }
    }
    slot
      ..disposeFlyNotifier()
      ..dispose();
    colCtrl.dispose();
  }

  void _startFlyOut(
    M3EDismissibleSlot slot,
    double flyInitial,
    double speedMul,
    SingleMotionController colCtrl,
  ) {
    final flySign = flyInitial.sign;
    final flyTarget = flySign == 0
        ? slot.capturedWidth + 80.0
        : flySign * (slot.capturedWidth + 80.0);

    slot.flyCtrl?.dispose();
    final fly = style.flySpring;
    final flyCtrl = SingleMotionController(
      motion: _spatialMotion(fly, stiffness: fly.stiffness * speedMul),
      vsync: this,
      initialValue: flyInitial,
    );
    slot.flyCtrl = flyCtrl;

    var collapseStarted = false;
    void startCollapse() {
      if (collapseStarted) {
        return;
      }
      collapseStarted = true;
      colCtrl.animateTo(1);
    }

    flyCtrl
      ..addListener(() {
        slot.flyNotifier.value = flyCtrl.value;
        final totalDist = (flyTarget - flyInitial).abs();
        if (totalDist > 0) {
          final currentDist = (flyCtrl.value - flyInitial).abs();
          if (currentDist / totalDist > 0.9) {
            startCollapse();
          }
        }
      })
      ..addStatusListener((s) {
        if (s == AnimationStatus.completed || s == AnimationStatus.dismissed) {
          slot.flyCtrl = null;
          flyCtrl.dispose();
          startCollapse();
        }
      })
      ..animateTo(flyTarget);
  }

  /// buildSlot.
}
