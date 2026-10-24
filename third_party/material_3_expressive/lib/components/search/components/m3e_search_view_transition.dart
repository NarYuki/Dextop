part of 'm3e_search_view.dart';

/// Resolved geometry for one frame of the search view.
class _ViewGeometry {
  const _ViewGeometry({
    required this.rect,
    required this.begin,
    required this.fullScreen,
    required this.t,
  });

  /// Current surface rect (spring value, may overshoot).
  final Rect rect;

  /// Anchor bar rect the transform starts from.
  final Rect begin;

  /// Whether the target layout is full-screen.
  final bool fullScreen;

  /// Transform progress clamped to 0..1 for colors and radii.
  final double t;
}

extension _M3ESearchViewTransition on _M3ESearchViewContentState {
  bool _resolveFullScreen(Size screen, M3ESearchViewTheme viewTheme) {
    return _route.isFullScreen ?? screen.width < viewTheme.compactBreakpoint;
  }

  _ViewGeometry _resolveGeometry({
    required Size screen,
    required M3ESearchViewTheme viewTheme,
    required M3ESearchViewStyle style,
  }) {
    final RenderObject? navigatorBox = Navigator.of(context).context
        .findRenderObject();
    _anchorRect = _route.anchorRect(navigatorBox) ?? _anchorRect;
    _paneRect = _route.paneRect(navigatorBox) ?? _paneRect;
    final Rect begin = _anchorRect ?? Rect.fromLTWH(0, 0, screen.width, 56);
    final bool fullScreen = _resolveFullScreen(screen, viewTheme);
    _lastFullScreen = fullScreen;
    Rect target = fullScreen
        ? Offset.zero & screen
        : _dockedRect(
            screen: screen,
            begin: begin,
            viewTheme: viewTheme,
            style: style,
          );
    if (_swapFrom != null && _swap.value < 1) {
      target = Rect.lerp(_swapFrom, target, _swap.value)!;
    }

    // Undershoot below the anchor would squeeze the bar row; clamp at 0.
    final double p = math.max(0, _progress.value);
    final Rect rect = Rect.lerp(begin, target, p)!;
    _lastRect = rect;
    return _ViewGeometry(
      rect: rect,
      begin: begin,
      fullScreen: fullScreen,
      t: clampDouble(_progress.value, 0, 1),
    );
  }

  Rect _dockedRect({
    required Size screen,
    required Rect begin,
    required M3ESearchViewTheme viewTheme,
    required M3ESearchViewStyle style,
  }) {
    final BoxConstraints box =
        _route.viewConstraints ?? viewTheme.constraints();
    double left = begin.left;
    double right = begin.right;
    final Rect? pane = _paneRect;
    if (style == M3ESearchViewStyle.contained && pane != null) {
      // Widen around the resting pill by the unfocused → focused margin
      // change (24 → 12), never past the focused margin of the pane.
      final double rest = M3ETheme.of(context).searchBarTheme.unfocusedMargin;
      final double growStart = math.max(
        0,
        math.min(rest, begin.left - pane.left) -
            viewTheme.containedLeadingMargin,
      );
      final double growEnd = math.max(
        0,
        math.min(rest, pane.right - begin.right) -
            viewTheme.containedTrailingMargin,
      );
      left -= growStart;
      right += growEnd;
    }
    final double width = clampDouble(
      right - left,
      math.min(box.minWidth, screen.width),
      math.min(box.maxWidth, screen.width),
    );
    // Keep the view centered on the widened bar when width is clamped.
    left += (right - left - width) / 2;
    right = left + width;
    final double height = clampDouble(
      screen.height * viewTheme.maxHeightFactor,
      math.min(box.minHeight, screen.height),
      math.min(box.maxHeight, screen.height),
    );
    final double x = clampDouble(left, 0, math.max(0, screen.width - width));
    double y = begin.top;
    if (y + height > screen.height) {
      y = math.max(0, screen.height - height);
    }
    return Rect.fromLTWH(x, y, width, height);
  }

  /// Springs between layouts when the window crosses the breakpoint.
  void _syncLayoutSwap() {
    final bool fullScreen = _resolveFullScreen(
      MediaQuery.sizeOf(context),
      _viewTheme,
    );
    if (_lastFullScreen == null || _lastFullScreen == fullScreen) {
      return;
    }
    _swapFrom = _lastRect;
    _swap
      ..value = 0
      ..animateWith(
        m3eSearchSpringSimulation(_viewTheme.containerTransformSpring),
      );
  }

  /// Predictive back transform around the surface center.
  Matrix4 _backTransform(Rect rect, M3ESearchViewTheme viewTheme) {
    final double p = clampDouble(_back.value, 0, 1);
    if (p == 0) {
      return Matrix4.identity();
    }
    final double scale = lerpDouble(1, viewTheme.predictiveBackMinScale, p)!;
    final double slack =
        rect.width * (1 - viewTheme.predictiveBackMinScale) / 2 -
        viewTheme.predictiveBackEdgeMargin;
    final double sign = _backEdge == SwipeEdge.left ? 1 : -1;
    final double dx = sign * p * math.max(0, slack);
    final double dy = clampDouble(
      _backDy,
      -viewTheme.predictiveBackMaxOffsetY,
      viewTheme.predictiveBackMaxOffsetY,
    );
    final Offset center = rect.size.center(Offset.zero);
    return Matrix4.identity()
      ..translateByDouble(center.dx + dx, center.dy + dy * p, 0, 1)
      ..scaleByDouble(scale, scale, 1, 1)
      ..translateByDouble(-center.dx, -center.dy, 0, 1);
  }

  bool get _isCurrentRoute => ModalRoute.of(context)?.isCurrent ?? false;

  bool _startBack(PredictiveBackEvent event) {
    if (!mounted || !_isCurrentRoute || !_opening) {
      return false;
    }
    _back.stop();
    _backEdge = event.swipeEdge;
    _backStartY = event.touchOffset?.dy;
    _backDy = 0;
    _back.value = event.progress;
    return true;
  }

  void _updateBack(PredictiveBackEvent event) {
    final double? y = event.touchOffset?.dy;
    if (y != null && _backStartY != null) {
      _backDy = y - _backStartY!;
    }
    _back.value = event.progress;
  }

  void _settleBack() {
    _back.animateWith(
      m3eSearchSpringSimulation(
        _viewTheme.predictiveBackSpring,
        from: _back.value,
        to: 0,
        velocity: _back.velocity,
      ),
    );
  }

  /// ArrowDown in the field moves into the results.
  void _focusFirstResult() {
    final List<FocusNode> nodes = _resultsNode.traversalDescendants.toList();
    if (nodes.isNotEmpty) {
      nodes.first.requestFocus();
    }
  }

  /// Arrow keys between results; up from the first returns to the field.
  void _moveResult(int delta) {
    final List<FocusNode> nodes = _resultsNode.traversalDescendants.toList();
    final int index = nodes.indexWhere((FocusNode n) => n.hasPrimaryFocus);
    if (index == -1) {
      return;
    }
    final int next = index + delta;
    if (next < 0) {
      _viewFocusNode.requestFocus();
    } else if (next < nodes.length) {
      nodes[next].requestFocus();
    }
  }
}
