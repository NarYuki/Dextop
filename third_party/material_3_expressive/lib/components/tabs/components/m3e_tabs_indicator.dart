part of '../m3e_tabs.dart';

/// Focus-node allocation, controller sync, indicator measurement, and
/// keyboard-driven focus movement for [M3ETabs].
extension _M3ETabsIndicator on _M3ETabsState {
  void _alloc(int count) {
    _slotKeys = List<GlobalKey>.generate(count, (_) => GlobalKey());
    _contentKeys = List<GlobalKey>.generate(count, (_) => GlobalKey());
    _nodes = List<FocusNode>.generate(count, (int i) {
      return FocusNode(debugLabel: 'tab $i')..addListener(() => _onFocus(i));
    });
  }

  void _disposeNodes() {
    for (final FocusNode node in _nodes) {
      node.dispose();
    }
  }

  void _bindController() {
    final M3ETabsController? next = widget.controller;
    if (identical(_bound, next)) {
      _bound?.updateIndex(_index);
      return;
    }
    _bound?.detach(_controllerSelect);
    _bound = next;
    next?.attach(_controllerSelect, _index);
  }

  void _selectFromController(int index) {
    widget.onTabSelected(index);
  }

  void _scheduleMeasure() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _measure();
      }
    });
  }

  void _measure() {
    final bar = _barKey.currentContext?.findRenderObject() as RenderBox?;
    if (bar == null || !bar.hasSize) {
      return;
    }
    final theme = M3ETheme.of(context).tabTheme;
    final full = theme.indicatorFullWidth(widget.variant);
    final key = full ? _slotKeys[_index] : _contentKeys[_index];
    final box = key.currentContext?.findRenderObject() as RenderBox?;
    if (box == null || !box.hasSize) {
      return;
    }
    final Offset origin = box.localToGlobal(Offset.zero, ancestor: bar);
    final double scroll = _scroll.hasClients ? _scroll.offset : 0;
    late final double left;
    late final double width;
    if (full) {
      left = origin.dx + scroll;
      width = box.size.width;
    } else {
      final double inset = theme.indicatorInset;
      final double raw = box.size.width - inset * 2;
      width = math.max(theme.indicatorMinLength, raw);
      left = origin.dx + scroll + (box.size.width - width) / 2;
    }
    final spring = SpringMotion(theme.indicatorSpring.toDescription());
    _indicatorLeft.motion = spring;
    _indicatorWidth.motion = spring;
    if (!_placed) {
      _indicatorLeft.value = left;
      _indicatorWidth.value = width;
      _placed = true;
      return;
    }
    if ((_indicatorLeft.value - left).abs() > 0.5) {
      _indicatorLeft.animateTo(left);
    }
    if ((_indicatorWidth.value - width).abs() > 0.5) {
      _indicatorWidth.animateTo(width);
    }
  }

  void _onFocus(int index) {
    final bool any = _nodes.any((FocusNode node) => node.hasFocus);
    if (!any) {
      _inside = false;
      return;
    }
    if (_redirecting) {
      _reveal(index);
      return;
    }
    if (!_inside) {
      _inside = true;
      if (index != _index) {
        _redirecting = true;
        _nodes[_index].requestFocus();
        _redirecting = false;
        return;
      }
    }
    _reveal(index);
  }

  void _reveal(int index) {
    final BuildContext? target = _slotKeys[index].currentContext;
    if (target == null) {
      return;
    }
    Scrollable.ensureVisible(
      target,
      alignment: 0.5,
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeOut,
    );
  }

  void _move(int delta) {
    final int current = _nodes.indexWhere((FocusNode node) => node.hasFocus);
    if (current < 0) {
      return;
    }
    final int next = current + delta;
    if (next < 0 || next >= _nodes.length) {
      return;
    }
    _inside = true;
    _nodes[next].requestFocus();
  }

  int _step(int towardTrailing) {
    final rtl = Directionality.of(context) == TextDirection.rtl;
    return rtl ? -towardTrailing : towardTrailing;
  }
}
