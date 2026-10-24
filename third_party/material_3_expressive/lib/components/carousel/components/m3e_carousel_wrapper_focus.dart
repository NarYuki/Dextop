part of 'm3e_carousel_wrapper.dart';

extension _M3ECarouselWrapperFocus on _M3ECarouselWrapperState {
  void _syncFocusNodes() {
    while (_itemFocus.length < widget.children.length) {
      _itemFocus.add(
        FocusNode(debugLabel: 'carousel item ${_itemFocus.length}'),
      );
      final states = WidgetStatesController()..addListener(_onItemStates);
      _itemStates.add(states);
      _itemLinks.add(LayerLink());
      _itemRest.add(Size.zero);
    }
    while (_itemFocus.length > widget.children.length) {
      _itemFocus.removeLast().dispose();
      _itemStates.removeLast().dispose();
      _itemLinks.removeLast();
      _itemRest.removeLast();
    }
  }

  void _rememberRest(int index, Size rest) {
    if (index >= _itemRest.length || _itemRest[index] == rest) {
      return;
    }
    _itemRest[index] = rest;
    if (index >= _itemFocus.length || !_itemFocus[index].hasPrimaryFocus) {
      return;
    }
    if (_restRebuildScheduled) {
      return;
    }
    _restRebuildScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _restRebuildScheduled = false;
      if (mounted) {
        setState(() {});
      }
    });
  }

  void _clearItemFocus(int index) {
    M3EFocusInteraction.instance.notePointerInteraction();
    if (index < _itemFocus.length && _itemFocus[index].hasFocus) {
      _itemFocus[index].unfocus();
    }
  }

  void _onItemStates() {
    if (mounted) {
      setState(() {});
    }
  }

  void _moveFocus(int index) {
    if (index < 0 || index >= widget.children.length) {
      return;
    }
    widget.onFocusedIndex?.call(index);
    _internalController.animateToItem(index).whenComplete(() {
      if (mounted) {
        _itemFocus[index].requestFocus();
      }
    });
  }

  KeyEventResult _onItemKey(int index, KeyEvent event) {
    if (event is! KeyDownEvent) {
      return KeyEventResult.ignored;
    }
    final LogicalKeyboardKey key = event.logicalKey;
    if (key == LogicalKeyboardKey.arrowUp ||
        key == LogicalKeyboardKey.arrowDown) {
      return KeyEventResult.ignored;
    }
    if (_isHorizontalArrowKey(key)) {
      return _handleArrowKey(index, key);
    }
    if (key == LogicalKeyboardKey.tab) {
      return _handleTabKey(index);
    }
    if (key == LogicalKeyboardKey.space || key == LogicalKeyboardKey.enter) {
      return _handleActivationKey(index);
    }
    return KeyEventResult.ignored;
  }

  bool _isHorizontalArrowKey(LogicalKeyboardKey key) =>
      key == LogicalKeyboardKey.arrowLeft ||
      key == LogicalKeyboardKey.arrowRight;

  KeyEventResult _handleArrowKey(int index, LogicalKeyboardKey key) {
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final next =
        key ==
        (rtl ? LogicalKeyboardKey.arrowLeft : LogicalKeyboardKey.arrowRight);
    _moveFocus(index + (next ? 1 : -1));
    return KeyEventResult.handled;
  }

  KeyEventResult _handleTabKey(int index) {
    final bool shift = HardwareKeyboard.instance.isShiftPressed;
    final int target = index + (shift ? -1 : 1);
    if (target < 0 || target >= widget.children.length) {
      return KeyEventResult.ignored;
    }
    _moveFocus(target);
    return KeyEventResult.handled;
  }

  KeyEventResult _handleActivationKey(int index) {
    final M3ECarouselItem item = _itemAt(index);
    if (!item.enabled) {
      return KeyEventResult.handled;
    }
    item.onTap?.call();
    _handleTap(index);
    return KeyEventResult.handled;
  }

  M3ECarouselItem _itemAt(int index) => widget.children[index];
}
