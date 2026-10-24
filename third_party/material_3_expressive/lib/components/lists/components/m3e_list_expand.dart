part of '../m3e_lists.dart';

/// Expand/collapse and container-transform behavior for [_M3EListState],
/// plus the snap-collapse/settle glue used while a row is reordered.
extension _M3EListExpand on _M3EListState {
  void _bindExpandController(M3EExpandableListController? controller) {
    controller?.attach(open: openTransform, close: closeTransform);
  }

  Widget _expandable(
    BuildContext context,
    int index, {
    bool suppressOwnGap = false,
  }) {
    final M3EListExpandableTheme expandable = M3ETheme.of(context)
        .listTheme
        .expandable;
    final M3EExpandableExpanded? expanded = _expandedAt(index);
    final bool hasList = expanded != null && expanded.isList;
    final M3EExpandableStyle decoration = _decoration(context, expandable);
    final M3ESpring expandMotion = _resolveExpandMotion(
      widget.expandMotion,
      hasList,
      expandable.expandMotion,
    );
    final M3ESpring collapseMotion = _resolveExpandMotion(
      widget.collapseMotion,
      hasList,
      expandable.collapseMotion,
    );
    final bool allowMultiple =
        widget.allowMultipleExpanded ?? expandable.allowMultipleExpanded;
    final M3EListCardListTheme cardList = M3ETheme.of(context)
        .listTheme
        .cardList;
    return _buildExpandableItem(
      context: context,
      index: index,
      expanded: expanded,
      decoration: decoration,
      expandMotion: expandMotion,
      collapseMotion: collapseMotion,
      allowMultiple: allowMultiple,
      cardList: cardList,
      suppressOwnGap: suppressOwnGap,
    );
  }

  Widget _buildExpandableItem({
    required BuildContext context,
    required int index,
    required M3EExpandableExpanded? expanded,
    required M3EExpandableStyle decoration,
    required M3ESpring expandMotion,
    required M3ESpring collapseMotion,
    required bool allowMultiple,
    required M3EListCardListTheme cardList,
    bool suppressOwnGap = false,
  }) {
    return M3EExpandableItem(
      index: index,
      totalCount: widget.itemCount,
      isExpanded: _expandedIndices.contains(index),
      headerBuilder: (BuildContext context, int i, double progress) =>
          _expandHeaderAt(i),
      bodyBuilder: (BuildContext context, int i, double progress) {
        return _expandBody(context, i, progress, decoration);
      },
      expanded: expanded,
      decoration: decoration,
      expandMotion: expandMotion,
      collapseMotion: collapseMotion,
      nestVariant: _variant(context, cardList),
      // Reorder rows get their trailing gap from the reorder host instead
      // (see `_reorder`), so it sits detached from the dragged card. The
      // decoration's own gap still separates a header from its own nested
      // sublist (M3EExpandableSublist), and must stay intact.
      trailingGap: suppressOwnGap ? 0 : null,
      onToggle: () => _handleExpandToggle(
        index,
        expanded,
        allowMultiple,
        decoration.haptic,
      ),
      onTransform: () => openTransform(index),
      onTransformAnchor: (BuildContext anchor) {
        _transformAnchors[index] = anchor;
      },
    );
  }

  M3ESpring _resolveExpandMotion(
    M3ESpring? override,
    bool hasList,
    M3ESpring themeMotion,
  ) {
    return override ??
        (hasList ? M3EMotion.expressiveSpatialPress : themeMotion);
  }

  Widget _expandHeaderAt(int i) {
    if (i < 0 || i >= _built.length) {
      return const SizedBox.shrink();
    }
    return _built[i];
  }

  void _handleExpandToggle(
    int index,
    M3EExpandableExpanded? expanded,
    bool allowMultiple,
    M3EHapticFeedback haptic,
  ) {
    if (!_enabledAt(index)) {
      return;
    }
    if (expanded != null && expanded.isTransform) {
      openTransform(index);
      return;
    }
    _toggle(index, allowMultiple: allowMultiple, haptic: haptic);
  }

  M3EExpandableStyle _decoration(
    BuildContext context,
    M3EListExpandableTheme expandable,
  ) {
    final M3EExpandableStyle base =
        widget.expandStyle ?? M3EExpandableStyle.fromTheme(expandable);
    final Color? color = widget.color;
    if (color == null) {
      return base;
    }
    return base.copyWith(color: color);
  }

  Widget _expandBody(
    BuildContext context,
    int index,
    double progress,
    M3EExpandableStyle decoration,
  ) {
    final M3EExpandableExpanded? expanded = _expandedAt(index);
    if (expanded == null ||
        expanded.isList ||
        expanded.isTransform ||
        progress <= 0) {
      return const SizedBox.shrink();
    }
    return ClipRect(
      child: Align(
        alignment: decoration.bodyAlignment,
        heightFactor: progress.clamp(0.0, 1.0),
        child: expanded.child,
      ),
    );
  }

  void _toggle(
    int index, {
    required bool allowMultiple,
    required M3EHapticFeedback haptic,
  }) {
    M3EHaptics.trigger(haptic);
    final bool opening = !_expandedIndices.contains(index);
    setState(() {
      if (opening) {
        if (!allowMultiple) {
          _expandedIndices.clear();
        }
        _expandedIndices.add(index);
      } else {
        _expandedIndices.remove(index);
      }
    });
    widget.onExpansionChanged?.call(index, isExpanded: opening);
  }

  /// Morphs [index] into its transform destination.
  void openTransform(int index) {
    final BuildContext? itemContext = _transformAnchors[index];
    if (itemContext == null || !itemContext.mounted) {
      return;
    }
    final RenderObject? object = itemContext.findRenderObject();
    if (object is! RenderBox || !object.hasSize) {
      return;
    }
    final M3EExpandableExpanded? expanded = _expandedAt(index);
    final Widget? destination = expanded != null && expanded.isTransform
        ? expanded.child
        : (index >= 0 && index < _transforms.length
              ? _transforms[index]
              : null);
    if (destination == null) {
      return;
    }
    final M3EThemeData theme = M3ETheme.of(itemContext);
    final M3ECardContainerTransformHandle<void>? previous = _transformHandle;
    _transformHandle = null;
    previous?.close();
    final M3ECardContainerTransformHandle<void> handle =
        M3ECardContainerTransform.show<void>(
          context: itemContext,
          origin: object.localToGlobal(Offset.zero) & object.size,
          originRadius: theme.listTheme.cardList.outerRadius,
          originColor: theme.colorScheme.surfaceContainerHighest,
          builder: (BuildContext context) => destination,
        );
    _transformHandle = handle;
    handle.future.whenComplete(() {
      if (identical(_transformHandle, handle)) {
        _transformHandle = null;
      }
    });
  }

  /// Reverses the open container transform.
  void closeTransform() {
    _transformHandle?.close();
    _transformHandle = null;
  }

  /// Snap-collapses [index] so reorder can measure the header.
  Future<void> prepareReorderDrag(int index) async {
    _collapsedForReorder = null;
    if (!_expandedIndices.contains(index)) {
      return;
    }
    _collapsedForReorder = index;
    _snapCollapse = true;
    setState(() => _expandedIndices.remove(index));
    await WidgetsBinding.instance.endOfFrame;
    if (!mounted) {
      return;
    }
    await WidgetsBinding.instance.endOfFrame;
  }

  /// Restores a row collapsed for reorder and remaps indices.
  void settleReorderDrag(int from, int to) {
    final int? pending = _collapsedForReorder;
    _collapsedForReorder = null;
    _snapCollapse = false;
    setState(() {
      _expandedIndices = _expandedIndices
          .map((int i) => _remapListIndex(i, from, to))
          .toSet();
      if (pending != null && pending == from) {
        _expandedIndices.add(to);
      }
    });
    if (from != to) {
      _remapSelection(from, to);
    }
  }

  void _remapSelection(int from, int to) {
    final M3EListFeatureScope? scope = M3EListFeatureScope.maybeOf(context);
    final M3ESelectionController? controller = scope?.controller;
    if (controller == null || scope == null || !scope.selectionEnabled) {
      return;
    }
    final Set<int> remapped = controller.selectedIndices
        .map((int i) => _remapListIndex(i, from, to))
        .toSet();
    if (remapped.length == controller.selectedIndices.length &&
        remapped.containsAll(controller.selectedIndices)) {
      return;
    }
    controller.clear();
    remapped.forEach(controller.select);
    widget.onSelectionChanged?.call(controller.selectedIndices);
  }
}
