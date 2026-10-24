part of '../m3e_button_group.dart';

/// Build and selection helpers for [_M3EButtonGroupState].
extension _M3EButtonGroupBuild on _M3EButtonGroupState {
  Widget _buildGroup(BuildContext context) {
    final groupTheme = M3ETheme.of(context).buttonGroupTheme;
    final metrics = groupTheme.metricsFor(
      widget.size,
      widget.density,
      isConnected: widget._connected,
    );
    final spacing = widget.spacing ?? metrics.spacing;
    _isRtl = Directionality.of(context) == TextDirection.rtl;

    Widget group = M3EButtonGroupProvider(
      controller: _overflowController,
      child: M3EButtonGroupScope(
        type: widget.type,
        shape: widget.shape,
        size: widget.size,
        density: widget.density,
        direction: widget.direction,
        child: _buildContent(context, spacing),
      ),
    );

    if (_hasAnyLabel) {
      group = _withOffstageMeasurer(context, group);
    }

    return _frameGroup(context, groupTheme, group);
  }

  Widget _withOffstageMeasurer(BuildContext context, Widget group) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        group,
        SizedBox.shrink(
          child: OverflowBox(
            alignment: Alignment.topLeft,
            minWidth: 0,
            maxWidth: double.infinity,
            minHeight: 0,
            maxHeight: double.infinity,
            child: IgnorePointer(
              child: Opacity(
                opacity: 0,
                child: _buildOffstageMeasurer(context),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _frameGroup(
    BuildContext context,
    M3EButtonGroupTheme groupTheme,
    Widget group,
  ) {
    Widget result = FocusTraversalGroup(
      policy: _M3EButtonGroupTabTraversalPolicy(),
      child: Semantics(
        container: true,
        label: widget.semanticLabel,
        child: group,
      ),
    );

    if (widget._connected && groupTheme.maxWidth != null) {
      result = ConstrainedBox(
        constraints: widget.direction == Axis.horizontal
            ? BoxConstraints(maxWidth: groupTheme.maxWidth!)
            : BoxConstraints(maxHeight: groupTheme.maxWidth!),
        child: result,
      );
    }

    if (widget.clipBehavior != Clip.none) {
      result = ClipRRect(
        clipBehavior: widget.clipBehavior,
        borderRadius: groupTheme.groupRadiusFor(widget.shape, widget.size),
        child: result,
      );
    }

    return result;
  }

  void _applyOverflowActionSelection(int index) {
    final action = widget.actions[index];
    if (!action.enabled) {
      return;
    }

    final isCurrentlySelected = _isActionSelected(index);

    if (_isMultiSelect) {
      final current = widget.selectedIndices ?? <int>{};
      final next = isCurrentlySelected
          ? ({...current}..remove(index))
          : {...current, index};
      if (widget.selectionRequired && next.isEmpty) {
        return;
      }
      _lastOverflowSelectionIndex = index;
      widget.onSelectedIndicesChanged?.call(next);
      return;
    }

    final nextSelectedIndex = isCurrentlySelected ? null : index;
    if (widget.selectionRequired && nextSelectedIndex == null) {
      return;
    }
    final isNowSelected = nextSelectedIndex == index;

    _lastOverflowSelectionIndex = index;
    widget.onSelectedIndexChanged?.call(nextSelectedIndex);

    if (!isNowSelected) {
      _lastOverflowSelectionIndex = null;
    }
  }

  bool _resolveActionSelected(int index) {
    if (_isMultiSelect) {
      return widget.selectedIndices?.contains(index) ?? false;
    }
    if (widget.onSelectedIndexChanged != null || widget.selectedIndex != null) {
      return widget.selectedIndex == index;
    }
    return widget.actions[index].isSelected ?? false;
  }

  M3EButtonGroupAction? _selectedActionInRange(int start, int end) {
    if (start < 0 || end >= widget.actions.length || start > end) {
      return null;
    }
    for (var i = start; i <= end; i++) {
      if (_isActionSelected(i)) {
        return widget.actions[i];
      }
    }
    final selectedIndex = _lastOverflowSelectionIndex;
    if (selectedIndex == null) {
      return null;
    }
    if (selectedIndex < start || selectedIndex > end) {
      return null;
    }
    return widget.actions[selectedIndex];
  }

  Widget _buildButton(
    BuildContext context,
    int index,
    bool isFirst,
    bool isLast,
  ) {
    final action = widget.actions[index];
    final bool selected = _isActionSelected(index);
    final groupTheme = M3ETheme.of(context).buttonGroupTheme;
    // Spec container heights: 32 / 40 / 56 / 96 / 136 (visual). XS/S a11y
    // 48dp targets are enforced via minimumSize width, not visual height.
    final segmentHeight = groupTheme.containerHeightFor(
      widget.size,
      density: widget.density,
    );
    final button = _buildButtonWidget(
      action: action,
      index: index,
      selected: selected,
      isVisualFirst: _isRtl ? isLast : isFirst,
      isVisualLast: _isRtl ? isFirst : isLast,
    );
    return SizedBox(
      height: segmentHeight,
      child: _maybeWrapButtonWidthMotion(
        action: action,
        index: index,
        selected: selected,
        button: button,
      ),
    );
  }

  Widget _buildButtonWidget({
    required M3EButtonGroupAction action,
    required int index,
    required bool selected,
    required bool isVisualFirst,
    required bool isVisualLast,
  }) {
    final onPressed = action.enabled
        ? () => _onSelectionChange(index, !selected)
        : null;
    return M3EButton(
      icon: action.icon,
      selectedIcon: action.selectedIcon,
      label: action.label,
      selectedLabel: action.selectedLabel,
      isSelected: selected,
      onPressed: onPressed,
      enabled: action.enabled,
      style: widget.style,
      size: _mapSize(
        widget.size,
        actionWidth: action.width,
        iconOnly: action.isIconOnly,
      ),
      shape: widget.shape,
      isGroupConnected: widget._connected,
      isFirstInGroup: isVisualFirst,
      isLastInGroup: isVisualLast,
      decoration: _cachedDecorations[index],
      statesController: _controllers[index],
      focusNode: action.focusNode ?? _focusNodes[index],
      autofocus: action.autofocus,
      enableFeedback: action.enableFeedback ?? widget.enableFeedback,
      onFocusChange: (focused) {
        action.onFocusChange?.call(focused);
      },
      semanticLabel: action.semanticLabel,
      tooltip: action.tooltip,
    );
  }

  void _onSelectionChange(int index, bool selected) {
    if (_isMultiSelect) {
      final current = widget.selectedIndices ?? <int>{};
      final next = selected
          ? {...current, index}
          : ({...current}..remove(index));
      if (widget.selectionRequired && next.isEmpty) {
        return;
      }
      widget.onSelectedIndicesChanged?.call(next);
      return;
    }
    if (widget.onSelectedIndexChanged != null) {
      if (widget.selectionRequired && !selected) {
        return;
      }
      widget.onSelectedIndexChanged!.call(selected ? index : null);
    }
  }

  Widget _maybeWrapButtonWidthMotion({
    required M3EButtonGroupAction action,
    required int index,
    required bool selected,
    required Widget button,
  }) {
    if (widget._connected ||
        action.width != null ||
        !_needsDistinctSelectedMeasurement(action) ||
        index >= _measuredUnselectedWidths.length) {
      return button;
    }

    final unselectedWidth = _measuredUnselectedWidths[index];
    final selectedWidth = _measuredSelectedWidths[index];
    // Intrinsic until measured (e.g. right after size/density change).
    if (unselectedWidth == null || selectedWidth == null) {
      return button;
    }
    final spring = M3ETheme.of(context).buttonGroupTheme.neighborSquishSpring;
    final motion = const MaterialSpringMotion.expressiveSpatialDefault()
        .copyWith(stiffness: spring.stiffness, damping: spring.damping);

    return SingleMotionBuilder(
      motion: motion,
      value: selected ? 1.0 : 0.0,
      builder: (context, progress, child) {
        final isShrinkingCollapse =
            !selected && selectedWidth > unselectedWidth;
        final p = isShrinkingCollapse
            ? progress.clamp(-0.45, 1.0)
            : progress.clamp(0.0, 1.0);
        final width = unselectedWidth + ((selectedWidth - unselectedWidth) * p);
        return SizedBox(width: width, child: child);
      },
      child: button,
    );
  }

  M3EButtonSize _mapSize(
    M3EButtonSize s, {
    double? actionWidth,
    bool iconOnly = false,
  }) {
    final base = switch (s.name) {
      'xs' => M3EButtonSize.xs,
      'sm' => M3EButtonSize.sm,
      'md' => M3EButtonSize.md,
      'lg' => M3EButtonSize.lg,
      'xl' => M3EButtonSize.xl,
      _ => M3EButtonSize.md,
    };
    final segmentHeight = M3ETheme.of(context).buttonGroupTheme
        .containerHeightFor(s, density: widget.density);
    // Preserve token [name] so group spacing / radius tables still resolve.
    // Override height for density; zero hPadding for icon-only actions.
    return base.copyWith(
      height: segmentHeight,
      hPadding: iconOnly ? 0 : (s.hPadding ?? base.hPadding),
      iconSize: s.iconSize ?? base.iconSize,
      iconGap: s.iconGap ?? base.iconGap,
      width: actionWidth ?? s.width ?? base.width,
    );
  }
}
