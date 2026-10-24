part of '../m3e_refresh_indicator.dart';

/// Scroll, drag, and refresh lifecycle helpers for [M3ERefreshIndicatorState].
extension _M3ERefreshIndicatorScroll on M3ERefreshIndicatorState {
  void _setupColorTween() {
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    final M3ERefreshIndicatorTheme refreshTheme = M3ETheme.of(context)
        .refreshIndicatorTheme;

    if (widget._indicatorType == _IndicatorType.contained) {
      _effectiveValueColor =
          widget.color ?? refreshTheme.containedActiveColor(scheme);
      _effectiveContainerColor =
          widget.backgroundColor ??
          refreshTheme.containedContainerColor(scheme);
    } else {
      _effectiveValueColor = widget.color ?? refreshTheme.activeColor(scheme);
      _effectiveContainerColor =
          widget.backgroundColor ?? refreshTheme.containerColorDefault();
    }

    final Color color = _effectiveValueColor;
    if (color.a == 0.0) {
      _valueColor = AlwaysStoppedAnimation<Color>(color);
    } else {
      _valueColor = _positionController.drive(
        ColorTween(
          begin: color.withValues(alpha: 0),
          end: color.withValues(alpha: color.a),
        ).chain(
          CurveTween(
            curve: const Interval(
              0,
              1.0 / M3ERefreshIndicatorTheme.kDragSizeFactorLimit,
            ),
          ),
        ),
      );
    }
  }

  bool _isAtLeadingEdge(ScrollMetrics metrics) {
    return switch (metrics.axisDirection) {
      AxisDirection.down => metrics.extentBefore == 0.0,
      AxisDirection.up => metrics.extentAfter == 0.0,
      AxisDirection.left || AxisDirection.right => false,
    };
  }

  bool _isPullingPastLeadingEdge(OverscrollNotification notification) {
    return switch (notification.metrics.axisDirection) {
      AxisDirection.down => notification.overscroll < 0.0,
      AxisDirection.up => notification.overscroll > 0.0,
      AxisDirection.left || AxisDirection.right => false,
    };
  }

  bool _shouldStart(ScrollNotification notification) {
    if (_status != null || !_isAtLeadingEdge(notification.metrics)) {
      return false;
    }
    if (!_isStartGesture(notification)) {
      return false;
    }
    return _start(notification.metrics.axisDirection);
  }

  bool _isStartGesture(ScrollNotification notification) {
    final bool startedFromEdgeDrag =
        notification is ScrollStartNotification &&
        notification.dragDetails != null &&
        widget.triggerMode == M3ERefreshTriggerMode.onEdge;
    final bool startedFromAnywhereDrag =
        notification is ScrollUpdateNotification &&
        notification.dragDetails != null &&
        widget.triggerMode == M3ERefreshTriggerMode.anywhere;
    final bool startedFromOverscroll =
        notification is OverscrollNotification &&
        notification.dragDetails != null &&
        _isPullingPastLeadingEdge(notification);
    return startedFromEdgeDrag ||
        startedFromAnywhereDrag ||
        startedFromOverscroll;
  }

  void _applyDragDelta(ScrollNotification notification) {
    final double? delta = _dragDeltaFrom(notification);
    if (delta != null) {
      _dragOffset = _dragOffset! + delta;
    }
    // Pad / reveal cap: further pull does not grow list spacing past max.
    final double maxPad = _resolvedContentDragOffset(context);
    _dragOffset = math.min(math.max(0, _dragOffset ?? 0), maxPad);
    _checkDragOffset();
  }

  double? _dragDeltaFrom(ScrollNotification notification) {
    final AxisDirection direction = notification.metrics.axisDirection;
    if (_usePointerPullTracking) {
      return _dragDeltaFromPointerPull(notification, direction);
    }
    if (notification is ScrollUpdateNotification &&
        notification.scrollDelta != null) {
      return _signedDragDelta(direction, notification.scrollDelta!);
    }
    if (notification is OverscrollNotification) {
      return _signedDragDelta(direction, notification.overscroll);
    }
    return null;
  }

  /// Web / native desktop: 1:1 pointer tracking. Mobile overscroll amounts
  /// under-report on desktop clamping physics. Dedupes ScrollUpdate+Overscroll
  /// in the same event-loop turn. Reveal/arm still use shared [_revealProgress].
  double? _dragDeltaFromPointerPull(
    ScrollNotification notification,
    AxisDirection direction,
  ) {
    final DragUpdateDetails? details = switch (notification) {
      final ScrollUpdateNotification n => n.dragDetails,
      final OverscrollNotification n => n.dragDetails,
      _ => null,
    };
    if (details != null) {
      return _pointerDeltaOncePerTurn(direction, details.delta);
    }
    if (notification is OverscrollNotification) {
      return _signedDragDelta(direction, notification.overscroll);
    }
    return null;
  }

  double? _pointerDeltaOncePerTurn(AxisDirection direction, Offset delta) {
    // Gesture handlers run outside a frame — cannot use currentFrameTimeStamp.
    if (_pointerDeltaLocked) {
      return null;
    }
    final double? signed = _pointerDragDelta(direction, delta);
    if (signed == null || signed == 0) {
      return null;
    }
    _pointerDeltaLocked = true;
    scheduleMicrotask(() {
      _pointerDeltaLocked = false;
    });
    return signed;
  }

  double? _pointerDragDelta(AxisDirection direction, Offset delta) {
    return switch (direction) {
      AxisDirection.down => delta.dy,
      AxisDirection.up => -delta.dy,
      AxisDirection.left || AxisDirection.right => null,
    };
  }

  double? _signedDragDelta(AxisDirection direction, double amount) {
    return switch (direction) {
      AxisDirection.down => -amount,
      AxisDirection.up => amount,
      AxisDirection.left || AxisDirection.right => null,
    };
  }

  bool _handleScrollNotification(ScrollNotification notification) {
    if (!widget.notificationPredicate(notification)) {
      return false;
    }
    if (_tryBeginDrag(notification)) {
      return false;
    }
    if (_abortIfAxisChanged(notification) ||
        _abortIfLeftLeadingEdge(notification)) {
      return false;
    }
    _continueActiveDrag(notification);
    return false;
  }

  bool _tryBeginDrag(ScrollNotification notification) {
    if (!_shouldStart(notification)) {
      return false;
    }
    setState(() {
      _status = M3ERefreshStatus.drag;
      widget.onStatusChange?.call(_status);
    });
    _applyDragDelta(notification);
    return true;
  }

  bool _abortIfAxisChanged(ScrollNotification notification) {
    final bool? indicatorAtTopNow =
        switch (notification.metrics.axisDirection) {
          AxisDirection.down || AxisDirection.up => true,
          AxisDirection.left || AxisDirection.right => null,
        };
    if (indicatorAtTopNow == _isIndicatorAtTop) {
      return false;
    }
    if (_status == M3ERefreshStatus.drag || _status == M3ERefreshStatus.armed) {
      _dismiss(M3ERefreshStatus.canceled);
    }
    return true;
  }

  bool _abortIfLeftLeadingEdge(ScrollNotification notification) {
    final bool pulling =
        _status == M3ERefreshStatus.drag || _status == M3ERefreshStatus.armed;
    if (!pulling || _isAtLeadingEdge(notification.metrics)) {
      return false;
    }
    _dismiss(M3ERefreshStatus.canceled);
    return true;
  }

  void _continueActiveDrag(ScrollNotification notification) {
    if (notification is ScrollUpdateNotification) {
      _handleScrollUpdate(notification);
    } else if (notification is OverscrollNotification) {
      _handleOverscroll(notification);
    } else if (notification is ScrollEndNotification) {
      _handleScrollEnd();
    }
  }

  void _handleScrollUpdate(ScrollUpdateNotification notification) {
    if (_status != M3ERefreshStatus.drag && _status != M3ERefreshStatus.armed) {
      return;
    }
    // Finger up: decide refresh vs cancel from full reveal — do not follow
    // ballistic settle. Pointer-pull platforms often emit layout ScrollUpdates
    // without dragDetails while the pointer is still down; wait for ScrollEnd.
    if (notification.dragDetails == null) {
      if (_usePointerPullTracking) {
        return;
      }
      _onPointerReleased();
      return;
    }
    _applyDragDelta(notification);
  }

  void _handleOverscroll(OverscrollNotification notification) {
    if (_status != M3ERefreshStatus.drag && _status != M3ERefreshStatus.armed) {
      return;
    }
    if (notification.dragDetails == null) {
      // Pointer-pull: apply overscroll amount; release on ScrollEnd.
      if (_usePointerPullTracking) {
        _applyDragDelta(notification);
        return;
      }
      _onPointerReleased();
      return;
    }
    _applyDragDelta(notification);
  }

  void _handleScrollEnd() {
    switch (_status) {
      case M3ERefreshStatus.drag:
      case M3ERefreshStatus.armed:
        _onPointerReleased();
      case M3ERefreshStatus.canceled:
      case M3ERefreshStatus.done:
      case M3ERefreshStatus.refresh:
      case M3ERefreshStatus.snap:
      case null:
        break;
    }
  }

  /// Load only when fully revealed; otherwise reverse without onRefresh.
  void _onPointerReleased() {
    if (_status != M3ERefreshStatus.drag && _status != M3ERefreshStatus.armed) {
      return;
    }
    if (_isFullyRevealed(context)) {
      _show();
    } else {
      _dismiss(M3ERefreshStatus.canceled);
    }
  }

  bool _handleIndicatorNotification(
    OverscrollIndicatorNotification notification,
  ) {
    if (notification.depth != 0 || !notification.leading) {
      return false;
    }
    if (_status == M3ERefreshStatus.drag || _status == M3ERefreshStatus.armed) {
      notification.disallowIndicator();
      return true;
    }
    return false;
  }

  bool _start(AxisDirection direction) {
    assert(_status == null, 'assertion failed');
    assert(_isIndicatorAtTop == null, 'assertion failed');
    assert(_dragOffset == null, 'assertion failed');
    switch (direction) {
      case AxisDirection.down:
      case AxisDirection.up:
        _isIndicatorAtTop = true;
      case AxisDirection.left:
      case AxisDirection.right:
        _isIndicatorAtTop = null;
        return false;
    }
    _dragOffset = 0.0;
    _restingInset = null;
    _scaleController.value = 0.0;
    _positionController.value = 0.0;
    _contentPadController.value = 0.0;
    _bubbleController.value = 1.0;
    return true;
  }

  void _checkDragOffset() {
    if (kIsWeb) {
      _checkDragOffsetWeb();
      return;
    }
    assert(
      _status == M3ERefreshStatus.drag || _status == M3ERefreshStatus.armed,
      'assertion failed',
    );
    final double maxPad = _resolvedContentDragOffset(context);
    _contentPadController.value = math.min(
      math.max(0, _dragOffset ?? 0),
      maxPad,
    );

    // Position factor tracks reveal for material value color / cancel retract.
    final double reveal = _revealProgress(context);
    final double limit = M3ERefreshIndicatorTheme.kDragSizeFactorLimit;
    final double target = reveal / limit;
    if (target == _positionController.value) {
      setState(() {});
    } else {
      _positionController.value = clampDouble(target, 0, 1);
    }

    // Visual-only armed when fully revealed (does not start onRefresh).
    if (_status == M3ERefreshStatus.drag && reveal >= 1.0) {
      _status = M3ERefreshStatus.armed;
      widget.onStatusChange?.call(_status);
    } else if (_status == M3ERefreshStatus.armed && reveal < 1.0) {
      _status = M3ERefreshStatus.drag;
      widget.onStatusChange?.call(_status);
    }
  }

  Future<void> _dismiss(M3ERefreshStatus newMode) async {
    await Future<void>.value();
    assert(
      newMode == M3ERefreshStatus.canceled || newMode == M3ERefreshStatus.done,
      'assertion failed',
    );

    if (!_seedDismissFromDragIfCanceled(newMode)) {
      return;
    }

    setState(() {
      _status = newMode;
      widget.onStatusChange?.call(_status);
    });
    try {
      await _runDismissAnimation();
    } catch (_) {
      _forceStopDismissControllers();
    }
    if (mounted && _status == newMode) {
      _resetAfterDismiss();
    }
  }

  /// Seeds pad/position for a continuous cancel reverse. Returns false if
  /// unmounted during cancel seed.
  bool _seedDismissFromDragIfCanceled(M3ERefreshStatus newMode) {
    if (newMode != M3ERefreshStatus.canceled || _dragOffset == null) {
      return true;
    }
    if (!mounted) {
      return false;
    }
    _contentPadController.value = _currentPad(context);
    final double reveal = _revealProgress(context);
    final double limit = M3ERefreshIndicatorTheme.kDragSizeFactorLimit;
    _positionController.value = clampDouble(reveal / limit, 0, 1);
    return true;
  }

  Future<void> _runDismissAnimation() {
    if (kIsWeb) {
      return _animateDismissWeb();
    }
    return _animateDismiss();
  }

  void _forceStopDismissControllers() {
    // Web early-stop must not leave status stuck on done/canceled.
    if (_scaleController.isAnimating) {
      _scaleController.stop(canceled: false);
    }
    if (_contentPadController.isAnimating) {
      _contentPadController.stop(canceled: false);
    }
    if (_positionController.isAnimating) {
      _positionController.stop(canceled: false);
    }
    _scaleController.value = 1;
    _contentPadController.value = 0;
  }

  void _resetAfterDismiss() {
    _dragOffset = null;
    _isIndicatorAtTop = null;
    _restingInset = null;
    if (kIsWeb) {
      _clearWebSpinnerCache();
    }
    setState(() {
      _status = null;
    });
  }

  Future<void> _springTo(
    AnimationController controller, {
    required double target,
    M3ESpring? spring,
    double? velocity,
  }) {
    final resolved =
        spring ?? M3ETheme.of(context).refreshIndicatorTheme.settleSpring;
    return controller.animateWith(
      SpringSimulation(
        resolved.toDescription(),
        controller.value,
        target,
        velocity ?? controller.velocity,
      ),
    );
  }

  Future<void> _animateDismiss() async {
    switch (_status!) {
      case M3ERefreshStatus.done:
        await Future.wait(<Future<void>>[
          _springTo(_scaleController, target: 1),
          _springTo(_contentPadController, target: 0),
        ]);
      case M3ERefreshStatus.canceled:
        // Scale/fade out + pad retract (reveal follows pad via _currentPad).
        await Future.wait(<Future<void>>[
          _springTo(_contentPadController, target: 0),
          _springTo(_positionController, target: 0),
        ]);
      case M3ERefreshStatus.armed:
      case M3ERefreshStatus.drag:
      case M3ERefreshStatus.refresh:
      case M3ERefreshStatus.snap:
        assert(false, 'assertion failed');
    }
  }

  void _show() {
    assert(_status != M3ERefreshStatus.refresh, 'assertion failed');
    assert(_status != M3ERefreshStatus.snap, 'assertion failed');
    final completer = Completer<void>();
    _pendingRefreshFuture = completer.future;

    final maxPad = _resolvedContentDragOffset(context);
    final home = _maxVisualPull(context);
    _dragOffset = maxPad;
    _contentPadController.value = maxPad;
    _restingInset = home;
    _bubbleController.value = 1;
    _positionController.value =
        1.0 / M3ERefreshIndicatorTheme.kDragSizeFactorLimit;

    setState(() {
      _status = M3ERefreshStatus.refresh;
      widget.onStatusChange?.call(_status);
    });

    widget.onRefresh().whenComplete(() {
      if (mounted && _status == M3ERefreshStatus.refresh) {
        completer.complete();
        _dismiss(M3ERefreshStatus.done);
      }
    });

    if (kIsWeb) {
      unawaited(_springContentPadToZeroWeb());
    } else {
      unawaited(_springTo(_contentPadController, target: 0));
    }
    _playReleaseBubble();
  }

  /// Spatial spring scale bubble at the fixed rest inset (layout unchanged).
  void _playReleaseBubble() {
    _bubbleController
      ..motion = _releaseBubbleMotion
      ..value = _resolvedReleaseBubbleFromScale
      ..animateTo(1);
  }
}
