part of '../m3e_button_group.dart';

/// Overflow and linear layout helpers for [_M3EButtonGroupState].
extension _M3EButtonGroupLayout on _M3EButtonGroupState {
  Widget _buildContent(BuildContext context, double spacing) {
    if (widget.actions.isEmpty) {
      return const SizedBox.shrink();
    }

    if (widget.overflowStrategy != null) {
      return _buildWithCustomStrategy(context, spacing);
    }

    switch (widget.overflow) {
      case M3EButtonGroupOverflow.none:
        return LayoutBuilder(
          builder: (context, constraints) {
            final maxMain = widget.direction == Axis.horizontal
                ? constraints.maxWidth
                : constraints.maxHeight;
            return _buildAnimatedLinearLayout(context, spacing, maxMain);
          },
        );
      case M3EButtonGroupOverflow.scroll:
        return _linearScrollable(context, spacing);
      case M3EButtonGroupOverflow.menu:
        return _linearWithOverflowMenu(context, spacing);
      case M3EButtonGroupOverflow.experimentalPaging:
        return _linearWithExperimentalPaging(context, spacing);
    }
  }

  Widget _buildWithCustomStrategy(BuildContext context, double spacing) {
    return LayoutBuilder(
      builder: (context, constraints) {
        return _buildCustomStrategyChild(context, constraints, spacing);
      },
    );
  }

  Widget _buildCustomStrategyChild(
    BuildContext context,
    BoxConstraints constraints,
    double spacing,
  ) {
    final maxMain = widget.direction == Axis.horizontal
        ? constraints.maxWidth
        : constraints.maxHeight;

    final strategy = widget.overflowStrategy!;
    var visibleCount = widget.actions.length;

    if (maxMain.isFinite) {
      final resolved = _resolveCustomStrategyVisibleCount(
        context,
        maxMain: maxMain,
        spacing: spacing,
        strategy: strategy,
      );
      if (resolved == null) {
        return _linearScrollable(context, spacing);
      }
      visibleCount = resolved;
    }

    final layout = strategy.buildLayout(
      context: context,
      actions: widget.actions,
      visibleCount: visibleCount,
      spacing: spacing,
      direction: widget.direction,
      style: widget.style,
      size: widget.size,
      decoration: widget.decoration,
      connected: widget._connected,
      isRtl: _isRtl,
      buildButton: (index, {required isFirst, required isLast}) {
        return _repaintButton(
          KeyedSubtree(
            key: ValueKey('custom-item-$index'),
            child: M3EButtonGroupItemScope(
              index: index,
              count:
                  visibleCount + (visibleCount < widget.actions.length ? 1 : 0),
              child: _buildButton(context, index, isFirst, isLast),
            ),
          ),
        );
      },
    );

    return _wrapCustomStrategyWithTrigger(
      context,
      spacing: spacing,
      strategy: strategy,
      visibleCount: visibleCount,
      layout: layout,
    );
  }

  int? _resolveCustomStrategyVisibleCount(
    BuildContext context, {
    required double maxMain,
    required double spacing,
    required M3EOverflowStrategy strategy,
  }) {
    final hasMeasurements =
        _overflowController.stableAllOverflowMeasured.value ||
        !_hasAnyLabel ||
        _allOverflowExtentsMeasured();
    if (!hasMeasurements) {
      return null;
    }

    final itemExtents = [
      for (int i = 0; i < widget.actions.length; i++)
        _itemMainExtentForOverflow(context, i),
    ];
    final triggerExtent = M3EButtonGroupOverflowController.roundConsumed(
      strategy.triggerExtent ?? _defaultOverflowTriggerExtent(),
    );
    return _overflowController.computeVisibleCountForMenu(
      maxMain: maxMain,
      itemExtents: itemExtents,
      triggerExtent: triggerExtent,
      separatorExtent: () => _separatorMainExtent(spacing),
    );
  }

  Widget _wrapCustomStrategyWithTrigger(
    BuildContext context, {
    required double spacing,
    required M3EOverflowStrategy strategy,
    required int visibleCount,
    required Widget layout,
  }) {
    final hiddenCount = widget.actions.length - visibleCount;
    if (hiddenCount <= 0) {
      return layout;
    }

    final trigger = strategy.buildOverflowTrigger(
      context: context,
      hiddenCount: hiddenCount,
      style: widget.style,
      size: widget.size,
      decoration: widget.decoration,
      connected: widget._connected,
      isFirst: visibleCount == 0,
      isLast: true,
      onPressed: () =>
          _onCustomOverflowPressed(context, strategy, visibleCount),
      isSelected:
          _selectedActionInRange(visibleCount, widget.actions.length - 1) !=
          null,
    );

    if (trigger == null) {
      return layout;
    }

    return _axisFlex([
      layout,
      _buildGap(context, visibleCount - 1, spacing),
      _repaintButton(
        KeyedSubtree(
          key: const ValueKey('custom-overflow-trigger'),
          child: M3EButtonGroupItemScope(
            index: M3EButtonConstants.kOverflowTriggerScopeIndex,
            count: 1,
            child: trigger,
          ),
        ),
      ),
    ]);
  }

  Future<void> _onCustomOverflowPressed(
    BuildContext context,
    M3EOverflowStrategy strategy,
    int visibleCount,
  ) async {
    final selectedAction = _selectedActionInRange(
      visibleCount,
      widget.actions.length - 1,
    );
    final selectedIndex = selectedAction != null
        ? widget.actions.indexOf(selectedAction)
        : null;
    final result = await strategy.showOverflowMenu(
      context: context,
      actions: widget.actions,
      firstHiddenIndex: visibleCount,
      selectedIndex: selectedIndex,
    );
    if (result != null && mounted) {
      strategy.onItemSelected(result);
      _handleOverflowActionSelection(result);
    }
  }

  Widget _repaintButton(Widget child) => RepaintBoundary(child: child);

  Widget _buildAnimatedLinearLayout(
    BuildContext context,
    double spacing,
    double maxMain,
  ) {
    final count = widget.actions.length;
    final children = <Widget>[
      for (var i = 0; i < count; i++)
        _repaintButton(
          KeyedSubtree(
            // Remount when layout inputs change so springs / Material button
            // style caches cannot keep a stale resting width.
            key: ValueKey<int>(
              Object.hash(
                i,
                widget.size.name,
                widget.density.name,
                widget.actions[i].minWidth,
                widget.actions[i].width,
                widget.type,
                widget.shape,
              ),
            ),
            child: M3EButtonGroupItemScope(
              index: i,
              count: count,
              child: _buildButton(context, i, i == 0, i == count - 1),
            ),
          ),
        ),
    ];

    if (!_supportsAnimatedSquish) {
      final interleaved = _interleaveWithGaps(context, children, spacing);
      // Spec: connected groups span their surface and grow segment widths.
      if (widget._connected &&
          widget.direction == Axis.horizontal &&
          maxMain.isFinite) {
        final fillWidth = M3ETheme.of(context).buttonGroupTheme.maxWidth != null
            ? math.min(maxMain, M3ETheme.of(context).buttonGroupTheme.maxWidth!)
            : maxMain;
        return SizedBox(
          width: fillWidth,
          child: Row(
            children: [
              for (var i = 0; i < interleaved.length; i++)
                if (i.isEven)
                  Expanded(child: interleaved[i])
                else
                  interleaved[i],
            ],
          ),
        );
      }
      return _axisFlex(interleaved);
    }
    return _buildSquishAnimatedLayout(children, spacing);
  }

  List<Widget> _interleaveWithGaps(
    BuildContext context,
    List<Widget> children,
    double spacing,
  ) {
    final flexChildren = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      flexChildren.add(children[i]);
      if (i < children.length - 1) {
        flexChildren.add(_buildGap(context, i, spacing));
      }
    }
    return flexChildren;
  }

  Widget _buildSquishAnimatedLayout(List<Widget> children, double spacing) {
    final spring = M3ETheme.of(context).buttonGroupTheme.neighborSquishSpring;
    return ValueListenableBuilder<int?>(
      valueListenable: _pressCoordinator.pressedIndexNotifier,
      builder: (context, pressedIndex, _) {
        return SingleMotionBuilder(
          motion: const MaterialSpringMotion.expressiveSpatialDefault()
              .copyWith(stiffness: spring.stiffness, damping: spring.damping),
          value: pressedIndex != null ? 1.0 : 0.0,
          builder: (context, animValue, _) {
            _pressCoordinator.onAnimationProgress(animValue);
            return _ButtonGroupRenderObjectWidget(
              direction: widget.direction,
              spacing: spacing,
              pressedIndex: _pressCoordinator.lastPressedIndex,
              animValue: animValue,
              expandedRatio: widget.expandedRatio,
              children: children,
            );
          },
        );
      },
    );
  }

  Widget _linearWithOverflowMenu(BuildContext context, double spacing) {
    return LayoutBuilder(
      builder: (context, constraints) {
        return _buildOverflowMenuChild(context, constraints, spacing);
      },
    );
  }

  Widget _buildOverflowMenuChild(
    BuildContext context,
    BoxConstraints constraints,
    double spacing,
  ) {
    final maxMain = widget.direction == Axis.horizontal
        ? constraints.maxWidth
        : constraints.maxHeight;
    if (!maxMain.isFinite) {
      return _buildAnimatedLinearLayout(context, spacing, maxMain);
    }

    if (!_hasStableOverflowMeasurements()) {
      return _linearScrollable(context, spacing);
    }

    final itemExtents = [
      for (int i = 0; i < widget.actions.length; i++)
        _itemMainExtentForOverflow(context, i),
    ];
    final visibleCount = _overflowController.computeVisibleCountForMenu(
      maxMain: maxMain,
      itemExtents: itemExtents,
      triggerExtent: M3EButtonGroupOverflowController.roundConsumed(
        _defaultOverflowTriggerExtent(),
      ),
      separatorExtent: () => _separatorMainExtent(spacing),
    );

    if (visibleCount >= widget.actions.length) {
      return _buildAnimatedLinearLayout(context, spacing, maxMain);
    }

    return _axisFlex(
      _buildOverflowMenuVisibleItems(context, spacing, visibleCount),
    );
  }

  bool _hasStableOverflowMeasurements() {
    return _overflowController.stableAllOverflowMeasured.value ||
        !_hasAnyLabel ||
        _allOverflowExtentsMeasured();
  }

  List<Widget> _buildOverflowMenuVisibleItems(
    BuildContext context,
    double spacing,
    int visibleCount,
  ) {
    final visibleItems = <Widget>[];
    final visibleScopeCount = visibleCount + 1;
    for (var i = 0; i < visibleCount; i++) {
      if (visibleItems.isNotEmpty) {
        visibleItems.add(_buildGap(context, i - 1, spacing));
      }
      visibleItems.add(
        _repaintButton(
          KeyedSubtree(
            key: ValueKey('button-menu-item-$i'),
            child: M3EButtonGroupItemScope(
              index: i,
              count: visibleScopeCount,
              child: _buildButton(context, i, i == 0, false),
            ),
          ),
        ),
      );
    }

    if (visibleItems.isNotEmpty) {
      visibleItems.add(_buildGap(context, visibleCount - 1, spacing));
    }
    visibleItems.add(
      _repaintButton(
        _buildOverflowMenuTrigger(
          context,
          firstHiddenIndex: visibleCount,
          isFirst: visibleCount == 0,
          isLast: true,
        ),
      ),
    );
    return visibleItems;
  }

  Widget _linearWithExperimentalPaging(BuildContext context, double spacing) {
    return LayoutBuilder(
      builder: (context, constraints) {
        return _buildExperimentalPagingChild(context, constraints, spacing);
      },
    );
  }

  Widget _buildExperimentalPagingChild(
    BuildContext context,
    BoxConstraints constraints,
    double spacing,
  ) {
    final maxMain = widget.direction == Axis.horizontal
        ? constraints.maxWidth
        : constraints.maxHeight;
    if (!maxMain.isFinite) {
      return _buildAnimatedLinearLayout(context, spacing, maxMain);
    }

    if (!_hasStableOverflowMeasurements()) {
      return _linearScrollable(context, spacing);
    }

    final itemExtents = [
      for (int i = 0; i < widget.actions.length; i++)
        _itemMainExtentForOverflow(context, i),
    ];

    return ValueListenableBuilder<int>(
      valueListenable: _overflowController.windowStartIndex,
      builder: (context, windowStartIndex, _) {
        return _buildPagingWindowItems(
          context,
          spacing: spacing,
          maxMain: maxMain,
          itemExtents: itemExtents,
        );
      },
    );
  }

  Widget _buildPagingWindowItems(
    BuildContext context, {
    required double spacing,
    required double maxMain,
    required List<double> itemExtents,
  }) {
    final pagingWindow = _overflowController.computePagingWindow(
      maxMain: maxMain,
      itemExtents: itemExtents,
      triggerExtent: M3EButtonGroupOverflowController.roundConsumed(
        _defaultOverflowTriggerExtent(),
      ),
      separatorBetweenItems: (_) => _separatorMainExtent(spacing),
      separatorBeforeOverflow: ({required isFirst}) =>
          isFirst ? 0.0 : _separatorMainExtent(spacing),
    );

    final visibleItems = <Widget>[];
    var localIndex = 0;
    if (pagingWindow.needsBack) {
      visibleItems.add(
        _repaintButton(
          KeyedSubtree(
            key: const ValueKey('button-paging-back'),
            child: _buildOverflowTrigger(
              context,
              targetIndex: 0,
              isBack: true,
              isFirst: true,
              isLast: false,
            ),
          ),
        ),
      );
      localIndex++;
    }

    localIndex = _appendPagingWindowButtons(
      context,
      spacing: spacing,
      pagingWindow: pagingWindow,
      visibleItems: visibleItems,
      localIndex: localIndex,
    );

    if (pagingWindow.needsForward) {
      if (visibleItems.isNotEmpty) {
        visibleItems.add(_buildGap(context, pagingWindow.end, spacing));
      }
      visibleItems.add(
        _repaintButton(
          KeyedSubtree(
            key: const ValueKey('button-paging-forward'),
            child: _buildOverflowTrigger(
              context,
              targetIndex: pagingWindow.end + 1,
              isBack: false,
              isFirst: false,
              isLast: true,
            ),
          ),
        ),
      );
    }

    return _axisFlex(visibleItems);
  }

  int _appendPagingWindowButtons(
    BuildContext context, {
    required double spacing,
    required M3EButtonGroupOverflowPagingWindow pagingWindow,
    required List<Widget> visibleItems,
    required int localIndex,
  }) {
    var index = localIndex;
    for (int i = pagingWindow.start; i <= pagingWindow.end; i++) {
      if (visibleItems.isNotEmpty) {
        visibleItems.add(_buildGap(context, i - 1, spacing));
      }
      final isLastVisible = i == pagingWindow.end && !pagingWindow.needsForward;
      visibleItems.add(
        _repaintButton(
          KeyedSubtree(
            key: ValueKey('button-paging-item-$i'),
            child: M3EButtonGroupItemScope(
              index: index++,
              count: _pagingScopeCount(pagingWindow),
              child: _buildButton(
                context,
                i,
                !pagingWindow.needsBack && i == pagingWindow.start,
                isLastVisible,
              ),
            ),
          ),
        ),
      );
    }
    return index;
  }

  Widget _axisFlex(List<Widget> children) => widget.direction == Axis.horizontal
      ? Row(mainAxisSize: MainAxisSize.min, children: children)
      : Column(mainAxisSize: MainAxisSize.min, children: children);

  Widget _buildGap(BuildContext context, int beforeIndex, double spacing) {
    return ListenableBuilder(
      listenable: Listenable.merge([
        _focusedIndexNotifier,
        M3EFocusInteraction.instance,
      ]),
      builder: (context, _) {
        // [spacing] is already size-token between-space (standard) or 2dp
        // (connected) from metricsFor. Focus rings only expand connected gaps,
        // and only while a keyboard ring is visible: a tap also takes focus,
        // which must not change the gap.
        final ringVisible =
            M3EFocusInteraction.instance.ringsAllowed &&
            M3EFocusRing.indicatorsEnabledOf(context) &&
            FocusManager.instance.highlightMode ==
                FocusHighlightMode.traditional;
        final gap = _FocusRingGapRenderer.resolveGap(
          connected: widget._connected,
          focusedIndex: ringVisible ? _focusedIndexNotifier.value : null,
          beforeIndex: beforeIndex,
          spacing: spacing,
          focusRingOutset: M3EFocusRing.outsetOf(context),
        );

        return AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          curve: Curves.easeOutCubic,
          width: widget.direction == Axis.horizontal ? gap : 0,
          height: widget.direction == Axis.vertical ? gap : 0,
        );
      },
    );
  }

  double _separatorMainExtent(double spacing) {
    return M3EButtonGroupOverflowController.roundConsumed(spacing);
  }

  bool _allOverflowExtentsMeasured() {
    for (var i = 0; i < widget.actions.length; i++) {
      final action = widget.actions[i];
      if (action.label != null || action.selectedLabel != null) {
        if (!_isMeasured(i)) {
          return false;
        }
      }
    }
    return true;
  }

  double _itemMainExtentForOverflow(BuildContext context, int index) {
    if (widget.direction == Axis.horizontal) {
      return M3EButtonGroupOverflowController.roundConsumed(
        _naturalSizeForButton(context, index),
      );
    }
    final buttonTheme = M3ETheme.of(context).buttonTheme;
    final measurements = buttonTheme.measurements(
      _mapSize(
        widget.size,
        actionWidth: widget.actions[index].width,
        iconOnly: widget.actions[index].isIconOnly,
      ),
    );
    return M3EButtonGroupOverflowController.roundConsumed(measurements.height);
  }

  double _defaultOverflowTriggerExtent() {
    if (widget.direction == Axis.vertical) {
      return _iconOnlyNaturalSizeCache;
    }
    return M3EButtonGroupOverflowController.roundConsumed(
      _iconOnlyNaturalSizeCache,
    );
  }

  int _pagingScopeCount(M3EButtonGroupOverflowPagingWindow window) {
    int count = window.end >= window.start
        ? (window.end - window.start + 1)
        : 0;
    if (window.needsBack) {
      count++;
    }
    if (window.needsForward) {
      count++;
    }
    return count;
  }

  Widget _buildOverflowTrigger(
    BuildContext context, {
    required int targetIndex,
    required bool isBack,
    required bool isFirst,
    required bool isLast,
  }) {
    return _buildOverflowIndicatorButton(
      context,
      start: isBack ? 0 : targetIndex,
      end: isBack
          ? _overflowController.windowStartIndex.value - 1
          : widget.actions.length - 1,
      icon: isBack
          ? const Icon(M3EIcons.arrow_back_rounded)
          : (widget.overflowIcon ?? const Icon(M3EIcons.more_horiz)),
      semanticLabel: isBack
          ? MaterialLocalizations.of(context).previousPageTooltip
          : 'More options',
      isFirst: isFirst,
      isLast: isLast,
      onPressed: () {
        _overflowController.windowStartIndex.value = targetIndex;
      },
    );
  }

  Widget _buildOverflowMenuTrigger(
    BuildContext context, {
    required int firstHiddenIndex,
    required bool isFirst,
    required bool isLast,
  }) {
    return _buildOverflowIndicatorButton(
      context,
      start: firstHiddenIndex,
      end: widget.actions.length - 1,
      icon: widget.overflowIcon ?? const Icon(M3EIcons.more_horiz),
      semanticLabel: MaterialLocalizations.of(context).showMenuTooltip,
      isFirst: isFirst,
      isLast: isLast,
      onPressed: () => _openOverflowMenu(context, firstHiddenIndex),
    );
  }
}
