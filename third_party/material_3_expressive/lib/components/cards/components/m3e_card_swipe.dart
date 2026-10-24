part of '../m3e_cards.dart';

/// Swipe-to-reveal and swipe-to-dismiss gesture handling for [_M3ECardState],
/// plus the keyboard equivalents (arrow keys reveal, escape settles).
extension _M3ECardSwipe on _M3ECardState {
  bool get _canDismiss =>
      widget.onSwipe != null && widget.swipeMode != M3ECardSwipeMode.reveal;

  bool get _canReveal => widget.swipeMode != M3ECardSwipeMode.dismiss;

  bool get _canSwipe {
    if (!widget.enabled) {
      return false;
    }
    final bool reveal =
        _canReveal &&
        (widget.swipeMode == M3ECardSwipeMode.reveal ||
            widget.onSwipe != null ||
            widget.swipeAction != null ||
            widget.leadingSwipeAction != null ||
            widget.trailingSwipeAction != null);
    return _canDismiss || reveal;
  }

  bool _hasSide({required bool leading}) {
    final Widget? side = leading
        ? widget.leadingSwipeAction
        : widget.trailingSwipeAction;
    if (side != null) {
      return true;
    }
    final bool eitherSide =
        widget.leadingSwipeAction != null || widget.trailingSwipeAction != null;
    if (eitherSide) {
      return false;
    }
    return true;
  }

  void _handleSwipeUpdate(DragUpdateDetails details) {
    if (!_canSwipe) {
      return;
    }
    _swipeEpoch++;
    var next = _swipeMotion.value + details.delta.dx;
    if (widget.swipeMode == M3ECardSwipeMode.reveal) {
      final double min = _hasSide(leading: false) ? -_kSwipeReveal : 0;
      final double max = _hasSide(leading: true) ? _kSwipeReveal : 0;
      if (next < min) {
        next = min;
      } else if (next > max) {
        next = max;
      }
    }
    _swipeMotion
      ..stop()
      ..value = next;
  }

  void _handleSwipeEnd(DragEndDetails details) {
    if (!_canSwipe) {
      _settleSwipe(0, dismiss: false);
      return;
    }
    final double width = context.size?.width ?? 0;
    final double travel = _swipeDx.abs();
    final double velocity = details.primaryVelocity ?? 0;
    final double direction = _swipeDx == 0 ? velocity.sign : _swipeDx.sign;
    if (direction == 0 || width <= 0) {
      _settleSwipe(0, dismiss: false);
      return;
    }
    final bool flingClosed =
        velocity != 0 && velocity.sign != direction && velocity.abs() >= 800;
    if (flingClosed) {
      _settleSwipe(0, dismiss: false);
      return;
    }
    final bool leading = direction > 0;
    final double threshold = M3ETheme.of(context).cardTheme.swipeThreshold;
    final bool flingAway = velocity.sign == direction && velocity.abs() >= 800;
    final bool dismiss =
        _canDismiss &&
        (travel / width >= threshold || (flingAway && travel > 24));
    final bool reveal =
        !dismiss &&
        _canReveal &&
        _hasSide(leading: leading) &&
        travel >= _kSwipeReveal / 2;
    final double target = dismiss
        ? direction * (width + 32)
        : reveal
        ? direction * _kSwipeReveal
        : 0;
    _settleSwipe(target, dismiss: dismiss);
  }

  void _settleSwipe(double target, {required bool dismiss}) {
    final int epoch = ++_swipeEpoch;
    _dismissPending = dismiss;
    _swipeMotion.animateTo(target).whenComplete(() {
      if (!mounted || epoch != _swipeEpoch) {
        return;
      }
      _commitDismissIfOffscreen(_swipeDx);
    });
  }

  void _commitDismissIfOffscreen(double dx) {
    if (!_dismissPending) {
      return;
    }
    final double width = context.size?.width ?? 0;
    if (width <= 0 || dx.abs() < width) {
      return;
    }
    _dismissPending = false;
    _swipeMotion.stop();
    widget.onSwipe?.call();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      _swipeMotion.animateTo(0);
    });
  }

  bool _scrollsInternally() {
    if (kIsWeb) {
      return WidgetsBinding.instance.mouseTracker.mouseIsConnected;
    }
    return switch (defaultTargetPlatform) {
      TargetPlatform.linux ||
      TargetPlatform.macOS ||
      TargetPlatform.windows => true,
      _ => false,
    };
  }

  KeyEventResult _onCardKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent || _editableFocused()) {
      return KeyEventResult.ignored;
    }
    final LogicalKeyboardKey key = event.logicalKey;
    if (key == LogicalKeyboardKey.escape) {
      return _handleEscapeKey();
    }
    return _handleRevealArrowKey(key);
  }

  /// Settles an open swipe back to rest when [_swipeDx] is non-zero.
  KeyEventResult _handleEscapeKey() {
    if (_swipeDx.abs() < 1) {
      return KeyEventResult.ignored;
    }
    _settleSwipe(0, dismiss: false);
    if (_actionable) {
      _focusNode.requestFocus();
    }
    return KeyEventResult.handled;
  }

  /// Toggles the leading or trailing swipe reveal for the arrow key matching
  /// [key] and the current text direction.
  KeyEventResult _handleRevealArrowKey(LogicalKeyboardKey key) {
    if (!_canReveal) {
      return KeyEventResult.ignored;
    }
    final right = key == LogicalKeyboardKey.arrowRight;
    final left = key == LogicalKeyboardKey.arrowLeft;
    if (!right && !left) {
      return KeyEventResult.ignored;
    }
    final ltr = Directionality.of(context) != TextDirection.rtl;
    final leading = ltr ? right : left;
    if (!_hasSide(leading: leading)) {
      return KeyEventResult.ignored;
    }
    final target = leading ? _kSwipeReveal : -_kSwipeReveal;
    if ((_swipeDx - target).abs() < 8) {
      _settleSwipe(0, dismiss: false);
    } else {
      _settleSwipe(target, dismiss: false);
    }
    return KeyEventResult.handled;
  }

  Widget _swipeWrap(Widget child, BorderRadius radius) {
    if (!_canSwipe) {
      return child;
    }
    return ClipRRect(
      borderRadius: radius,
      child: Stack(
        children: <Widget>[
          Positioned.fill(child: _swipeBackdrop()),
          Transform.translate(
            offset: Offset(_swipeDx, 0),
            child: GestureDetector(
              onHorizontalDragUpdate: _handleSwipeUpdate,
              onHorizontalDragEnd: _handleSwipeEnd,
              onHorizontalDragCancel: () => _settleSwipe(0, dismiss: false),
              child: child,
            ),
          ),
        ],
      ),
    );
  }

  Widget _swipeBackdrop() {
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    final bool leading = _swipeDx >= 0;
    final Color fill = leading
        ? (widget.leadingSwipeColor ?? scheme.primaryContainer)
        : (widget.trailingSwipeColor ?? scheme.primaryContainer);
    final bool open = leading ? _swipeDx >= 40 : _swipeDx <= -40;
    return ColoredBox(
      color: fill,
      child: Align(
        alignment: leading ? Alignment.centerLeft : Alignment.centerRight,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: ExcludeFocus(
            excluding: !open,
            child: FocusTraversalOrder(
              order: NumericFocusOrder(leading ? 6 : 7),
              child: _backdropAction(scheme, leading: leading),
            ),
          ),
        ),
      ),
    );
  }

  Widget _backdropAction(M3EColorScheme scheme, {required bool leading}) {
    final Widget? side = leading
        ? widget.leadingSwipeAction
        : widget.trailingSwipeAction;
    if (side != null) {
      return side;
    }
    if (widget.leadingSwipeAction == null &&
        widget.trailingSwipeAction == null &&
        widget.swipeAction != null) {
      return widget.swipeAction!;
    }
    if (widget.leadingSwipeAction == null &&
        widget.trailingSwipeAction == null) {
      return Icon(M3EIcons.favorite_border, color: scheme.onPrimaryContainer);
    }
    return const SizedBox.shrink();
  }
}
