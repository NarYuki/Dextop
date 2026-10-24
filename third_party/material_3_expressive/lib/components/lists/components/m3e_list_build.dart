part of '../m3e_lists.dart';

/// Row-tree construction for [_M3EListState]: snapshotting item builders,
/// laying out the column/scrollable/sliver/reorder body, and building each
/// row's card or expandable wrapper.
extension _M3EListBuild on _M3EListState {
  void _snapshot(BuildContext context) {
    _built.clear();
    _swipes.clear();
    _expanded.clear();
    _transforms.clear();
    for (var index = 0; index < widget.itemCount; index++) {
      final child = widget.itemBuilder(context, index);
      _built.add(child);
      if (child is M3EListItem) {
        _swipes.add(child.swipe);
        _expanded.add(child.expanded);
        _transforms.add(child.transform);
      } else {
        _swipes.add(null);
        _expanded.add(null);
        _transforms.add(null);
      }
    }
  }

  Widget _buildList(BuildContext context) {
    _snapshot(context);
    final M3EExpandableNestScope? nest = M3EExpandableNestScope.maybeOf(
      context,
    );
    _embeddedActive = widget.embedded || nest != null;
    final Widget? empty = _empty();
    if (empty != null) {
      return empty;
    }
    Widget list = M3EListKeyboardGroup(
      itemCount: widget.itemCount,
      semanticsLabel: widget.semanticsLabel,
      sliver: _isSliver,
      child: _layout(context),
    );
    if (widget.selection || widget.reorder) {
      list = M3EListFeatureHost(
        itemCount: widget.itemCount,
        selection: widget.selection,
        reorder: widget.reorder,
        selectionController: widget.selectionController,
        onSelectionChanged: widget.onSelectionChanged,
        selectionState: widget.selectionState,
        reorderState: widget.reorderState,
        child: list,
      );
    }
    list = M3EExpandableSnapCollapse(snap: _snapCollapse, child: list);
    return _margin(list);
  }

  Widget? _empty() {
    final Widget? empty = widget.emptyBuilder;
    if (widget.itemCount != 0 || empty == null) {
      return null;
    }
    return _margin(_isSliver ? SliverToBoxAdapter(child: empty) : empty);
  }

  bool get _isSliver => widget._layout == _M3EListLayout.sliver;

  Widget _margin(Widget child) {
    final EdgeInsetsGeometry? margin = widget.margin;
    if (margin == null) {
      return child;
    }
    if (_isSliver) {
      return SliverPadding(padding: margin, sliver: child);
    }
    return Padding(padding: margin, child: child);
  }

  Widget _layout(BuildContext context) {
    if (widget.reorder && widget._layout != _M3EListLayout.sliver) {
      return _reorder(context);
    }
    switch (widget._layout) {
      case _M3EListLayout.column:
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: _children(context),
        );
      case _M3EListLayout.scrollable:
        return ListView.builder(
          controller: widget.controller,
          physics: widget.physics,
          shrinkWrap: widget.shrinkWrap,
          padding: widget.listPadding,
          clipBehavior: widget.clipBehavior,
          itemCount: slots.length,
          itemBuilder: (BuildContext context, int index) =>
              _row(context, index),
        );
      case _M3EListLayout.sliver:
        return SliverList.builder(
          itemCount: slots.length,
          itemBuilder: (BuildContext context, int index) =>
              _row(context, index),
        );
    }
  }

  List<Widget> _children(BuildContext context) {
    return <Widget>[
      for (int index = 0; index < slots.length; index++) _row(context, index),
    ];
  }

  Widget _reorder(BuildContext context) {
    final M3EListReorderState reorderState =
        widget.reorderState ?? M3ETheme.of(context).listTheme.reorder;
    final M3EListItemTheme itemTheme = M3ETheme.of(context).listTheme.item;
    // Rows built for the reorder host suppress their own trailing gap (see
    // `_card`/`_expandable`) so the host can apply it as a detached spacer
    // instead — otherwise the gap paints as part of the dragged card.
    final double resolvedGap = _baselineOr(
      itemTheme,
      widget.gap,
      M3EListCardListTheme.defaultGap,
    );
    return M3EListReorderHost(
      itemCount: slots.length,
      onReorder: widget.onReorder!,
      reorderState: reorderState,
      gap: resolvedGap,
      scrollable: widget._layout == _M3EListLayout.scrollable,
      controller: widget.controller,
      physics: widget.physics,
      shrinkWrap: widget.shrinkWrap,
      padding: widget.listPadding,
      prepareDrag: prepareReorderDrag,
      onDragSettled: settleReorderDrag,
      canStartDrag: (int index) =>
          !isInteractionLocked &&
          index >= 0 &&
          index < slots.length &&
          slots[index].isVisible,
      itemBuilder: (BuildContext context, int index) =>
          _row(context, index, suppressOwnGap: true),
    );
  }

  Widget _row(
    BuildContext context,
    int slotIndex, {
    bool suppressOwnGap = false,
  }) {
    return Builder(
      builder: (BuildContext context) =>
          _rowBuilt(context, slotIndex, suppressOwnGap: suppressOwnGap),
    );
  }

  Widget _rowBuilt(
    BuildContext context,
    int slotIndex, {
    bool suppressOwnGap = false,
  }) {
    final List<int> visible = computeVisibleIndices();
    final int dataIndex = visible.indexOf(slotIndex);
    final bool collapsing =
        slotIndex >= 0 &&
        slotIndex < slots.length &&
        !slots[slotIndex].isVisible;
    if (collapsing || (dataIndex >= 0 && _swipeAt(dataIndex) != null)) {
      return buildSlot(
        context,
        slotIndex,
        visible: visible,
        suppressOwnGap: suppressOwnGap,
      );
    }
    if (dataIndex < 0) {
      return const SizedBox.shrink();
    }
    return _plain(
      context,
      dataIndex,
      visible.length,
      suppressOwnGap: suppressOwnGap,
    );
  }

  Widget _plain(
    BuildContext context,
    int index,
    int total, {
    bool suppressOwnGap = false,
  }) {
    if (_expandedAt(index) != null) {
      return M3EListItemIndex(
        index: index,
        child: _expandable(context, index, suppressOwnGap: suppressOwnGap),
      );
    }
    return _card(context, index, total, suppressOwnGap: suppressOwnGap);
  }

  Widget _childAt(BuildContext context, int index) {
    if (_expandedAt(index) != null) {
      return _expandable(context, index);
    }
    if (index < 0 || index >= _built.length) {
      return const SizedBox.shrink();
    }
    return _built[index];
  }

  Widget _card(
    BuildContext context,
    int index,
    int total, {
    bool suppressOwnGap = false,
  }) {
    final M3EListItemTheme itemTheme = M3ETheme.of(context).listTheme.item;
    final M3EListCardListTheme cardList = M3ETheme.of(context)
        .listTheme
        .cardList;
    final double usedOuter = _baselineOr(
      itemTheme,
      widget.outerRadius,
      M3EListCardListTheme.defaultOuterRadius,
    );
    final double usedInner = _baselineOr(
      itemTheme,
      widget.innerRadius,
      M3EListCardListTheme.defaultInnerRadius,
    );
    // Reorder rows get their gap from the reorder host instead (see
    // `_reorder`), so it can sit detached from the dragged card.
    final double usedGap = suppressOwnGap
        ? 0
        : _baselineOr(itemTheme, widget.gap, M3EListCardListTheme.defaultGap);
    final M3ECardPosition position = calculateCardPosition(index, total);
    final M3EListFeatureScope? features = M3EListFeatureScope.maybeOf(context);
    final bool enabled = _enabledAt(index);
    return M3EListTapBinder(
      onTap: _tapFor(features, index),
      onDoubleTap: _doubleTap(features, index),
      builder: (BuildContext context, VoidCallback? onPressed) {
        return M3ECardListItem(
          index: index,
          position: position,
          outerRadius: usedOuter,
          innerRadius: usedInner,
          gap: usedGap,
          embedded: _embeddedActive,
          color: widget.color,
          resolvedColor:
              widget.colorBuilder?.call(index) ??
              m3eSelectionFill(context, index),
          resolvedBorderRadius:
              widget.borderRadiusBuilder?.call(index, position) ??
              m3eSelectionRadius(
                context,
                index,
                outerRadius: itemTheme.selectedRadius,
              ),
          padding: widget.padding,
          onTap: !enabled || onPressed == null ? null : (_) => onPressed(),
          onLongPress: widget.reorder ? null : widget.onLongPress,
          semanticLabel: widget.semanticLabelBuilder?.call(index),
          mouseCursor: widget.mouseCursor,
          haptic: widget.haptic,
          variant: _variant(context, cardList),
          border: widget.border ?? cardList.border,
          child: M3EListItemIndex(
            index: index,
            child: index < _built.length
                ? _built[index]
                : const SizedBox.shrink(),
          ),
        );
      },
    );
  }

  M3ECardVariant _variant(BuildContext context, M3EListCardListTheme cardList) {
    final M3EExpandableNestScope? nest = M3EExpandableNestScope.maybeOf(
      context,
    );
    return widget.variant ?? nest?.variant ?? cardList.variant;
  }

  double _baselineOr(M3EListItemTheme theme, double value, double specDefault) {
    if (theme.isBaseline && value == specDefault) {
      return 0;
    }
    return value;
  }

  VoidCallback? _tapFor(M3EListFeatureScope? features, int index) {
    if (features == null || !features.selectionEnabled) {
      if (widget.onTap == null) {
        return null;
      }
      return () => widget.onTap!(index);
    }
    return () {
      final bool inMode = features.controller?.isSelectionMode ?? false;
      if (inMode) {
        features.onToggleSelection(index);
        return;
      }
      widget.onTap?.call(index);
    };
  }

  VoidCallback? _doubleTap(M3EListFeatureScope? features, int index) {
    if (features == null || !features.selectionEnabled) {
      return null;
    }
    if (features.selectionState.trigger != M3EListSelectionTrigger.doubleTap) {
      return null;
    }
    return () => features.onToggleSelection(index);
  }
}
