part of '../m3e_button_group.dart';

/// Measurement, focus, and signature helpers for [_M3EButtonGroupState].
extension _M3EButtonGroupMeasurement on _M3EButtonGroupState {
  void _assertControlledSelection() {
    assert(_controlledSelectionIsValid(), '');
  }

  bool _controlledSelectionIsValid() {
    final hasControlledGroup =
        widget.onSelectedIndexChanged != null ||
        widget.onSelectedIndicesChanged != null;
    if (!hasControlledGroup) {
      return true;
    }
    for (final action in widget.actions) {
      if (action.isSelected != null) {
        throw FlutterError(
          'M3EButtonGroup: Do not set action.isSelected when the group uses '
          'onSelectedIndexChanged or onSelectedIndicesChanged.\n'
          'Use selectedIndex / selectedIndices on the group instead. '
          'Mixing per-action selection with group-controlled selection '
          'produces undefined behavior.',
        );
      }
    }
    return true;
  }

  void _bootstrapState() {
    _measurement = _ButtonGroupMeasurementOrchestrator();
    _pressCoordinator = _ButtonGroupPressCoordinator(
      isMounted: () => !_stateDisposed && mounted,
    );
    _overflowController = M3EButtonGroupOverflowController();
    _scrollOverflowController = ScrollController();
    _overflowController.stableAllOverflowMeasured.addListener(
      _handleOverflowChange,
    );
    _layoutSignature = _computeLayoutSignature(widget);
    _focusNodeSignature = _computeFocusNodeSignature(widget.actions);
    _initControllers();
    _initFocusNodes();
    _hasAnyLabel = _computeHasAnyLabel();
    _initMeasurementState();
    _scheduleMeasurementIfNeeded();
  }

  void _applyWidgetUpdate(M3EButtonGroup old) {
    final selectionChangedIndex = _selectionChangedIndex(old);
    // Always deep-hash layout inputs so minWidth / content updates are not
    // skipped when a caller reuses an actions list reference.
    final nextLayoutSignature = _computeLayoutSignature(widget);
    final nextFocusNodeSignature = _computeFocusNodeSignature(widget.actions);

    _syncControllersAndFocusNodes(
      old,
      nextFocusNodeSignature: nextFocusNodeSignature,
    );
    _updateDecorations();
    _syncLayoutMeasurement(old, nextLayoutSignature: nextLayoutSignature);
    _syncOverflowWindowOnUpdate();
    _layoutSignature = nextLayoutSignature;
    _focusNodeSignature = nextFocusNodeSignature;
    if (_supportsAnimatedSquish && selectionChangedIndex != null) {
      _pressCoordinator.animateSelection(selectionChangedIndex);
    }
  }

  int? _selectionChangedIndex(M3EButtonGroup old) {
    if (old.selectedIndex != widget.selectedIndex) {
      return widget.selectedIndex ?? old.selectedIndex;
    }
    final oldIndices = old.selectedIndices ?? const <int>{};
    final newIndices = widget.selectedIndices ?? const <int>{};
    final changed = <int>{
      ...oldIndices.difference(newIndices),
      ...newIndices.difference(oldIndices),
    };
    if (changed.isNotEmpty) {
      return changed.first;
    }
    final count = math.min(old.actions.length, widget.actions.length);
    for (var i = 0; i < count; i++) {
      if (old.actions[i].isSelected != widget.actions[i].isSelected) {
        return i;
      }
    }
    return null;
  }

  void _syncControllersAndFocusNodes(
    M3EButtonGroup old, {
    required int nextFocusNodeSignature,
  }) {
    final lengthChanged = old.actions.length != widget.actions.length;
    final focusNodesChanged = nextFocusNodeSignature != _focusNodeSignature;
    if (lengthChanged) {
      _disposeControllers();
      _initControllers();
      _pressCoordinator.clearPressedIndex();
    }
    if (lengthChanged || focusNodesChanged) {
      _disposeFocusNodes();
      _initFocusNodes();
    }
  }

  void _syncLayoutMeasurement(
    M3EButtonGroup old, {
    required int nextLayoutSignature,
  }) {
    final lengthChanged = old.actions.length != widget.actions.length;
    final layoutChanged = nextLayoutSignature != _layoutSignature;
    final sizeOrDensityChanged =
        old.size != widget.size || old.density != widget.density;
    if (lengthChanged || layoutChanged) {
      _measurementGeneration++;
      _hasAnyLabel = _computeHasAnyLabel();
      _updateDecorations();
      // Any layout-affecting update (size, density, minWidth, labels, …)
      // drops stale extents so the group hugs the new content immediately.
      _initMeasurementState(clearMeasuredWidths: true);
      _scheduleMeasurementIfNeeded();
    } else if (!identical(old.actions, widget.actions) ||
        old.decoration != widget.decoration) {
      // Color-only / decoration-only action updates still refresh styles.
      _updateDecorations();
    }
    if (sizeOrDensityChanged) {
      _updateIconOnlyNaturalSizeCache();
    }
  }

  void _syncOverflowWindowOnUpdate() {
    if (_overflowController.windowStartIndex.value >= widget.actions.length) {
      _overflowController.windowStartIndex.value = 0;
    }
    if (widget.selectedIndex != _lastOverflowSelectionIndex) {
      _lastOverflowSelectionIndex = null;
    }
  }

  bool _computeHasAnyLabel() => widget.actions.any(
    (action) => action.label != null || action.selectedLabel != null,
  );

  bool _needsDistinctSelectedMeasurement(M3EButtonGroupAction action) {
    return action.selectedLabel != null || action.selectedIcon != null;
  }

  void _initMeasurementState({bool clearMeasuredWidths = false}) {
    _measurement.initMeasurementState(
      actionCount: widget.actions.length,
      overflowController: _overflowController,
      clearMeasuredWidths: clearMeasuredWidths,
    );
  }

  void _disposeMeasurerControllers() {
    _measurement.disposeMeasurerControllers();
  }

  bool _isMeasured(int index) {
    return _measurement.isMeasured(index);
  }

  void _handleOverflowChange() {
    if (!_stateDisposed && mounted) {
      setState(() {});
    }
  }

  void _updateDecorations() {
    final groupTheme = M3ETheme.of(context).buttonGroupTheme;
    final segmentHeight = groupTheme.containerHeightFor(
      widget.size,
      density: widget.density,
    );
    final minimumTarget = groupTheme.minTargetFor(widget.size);
    _cachedDecorations = List.generate(
      widget.actions.length,
      (index) => _decorationAt(index, groupTheme, segmentHeight, minimumTarget),
    );
  }

  M3EButtonDecoration _decorationAt(
    int index,
    M3EButtonGroupTheme groupTheme,
    double segmentHeight,
    double minimumTarget,
  ) {
    final action = widget.actions[index];
    final uniformMinimum = _uniformActionMinimum(
      action,
      segmentHeight,
      minimumTarget,
    );
    final actionDecoration = action.decoration;
    final groupDecoration = widget.decoration;
    final connected = widget._connected;
    final radii = _actionRadii(
      actionDecoration,
      groupDecoration,
      groupTheme,
      segmentHeight,
      connected,
    );
    return M3EButtonDecoration(
      backgroundColor:
          actionDecoration?.backgroundColor ?? groupDecoration?.backgroundColor,
      foregroundColor:
          actionDecoration?.foregroundColor ?? groupDecoration?.foregroundColor,
      side: actionDecoration?.side ?? groupDecoration?.side,
      overlayColor:
          actionDecoration?.overlayColor ?? groupDecoration?.overlayColor,
      surfaceTintColor:
          actionDecoration?.surfaceTintColor ??
          groupDecoration?.surfaceTintColor,
      minimumSize: uniformMinimum,
      fixedSize: actionDecoration?.fixedSize ?? groupDecoration?.fixedSize,
      maximumSize:
          actionDecoration?.maximumSize ?? groupDecoration?.maximumSize,
      tapTargetSize:
          actionDecoration?.tapTargetSize ??
          groupDecoration?.tapTargetSize ??
          MaterialTapTargetSize.shrinkWrap,
      visualDensity:
          actionDecoration?.visualDensity ??
          groupDecoration?.visualDensity ??
          VisualDensity.standard,
      mouseCursor:
          actionDecoration?.mouseCursor ?? groupDecoration?.mouseCursor,
      motion: actionDecoration?.motion ?? groupDecoration?.motion,
      haptic:
          actionDecoration?.haptic ?? groupDecoration?.haptic ?? widget.haptic,
      selectedRadius: radii.selected,
      unselectedRadius: radii.unselected,
      pressedRadius: radii.pressed,
      hoveredRadius: radii.hovered,
      connectedInnerRadius: radii.connectedInner,
      backgroundGradient:
          actionDecoration?.backgroundGradient ??
          groupDecoration?.backgroundGradient,
      foregroundGradient:
          actionDecoration?.foregroundGradient ??
          groupDecoration?.foregroundGradient,
      overlayGradient:
          actionDecoration?.overlayGradient ?? groupDecoration?.overlayGradient,
      outlineGradient:
          actionDecoration?.outlineGradient ?? groupDecoration?.outlineGradient,
      backgroundBuilder:
          actionDecoration?.backgroundBuilder ??
          groupDecoration?.backgroundBuilder,
      foregroundBuilder:
          actionDecoration?.foregroundBuilder ??
          groupDecoration?.foregroundBuilder,
    );
  }

  Size _uniformActionMinimum(
    M3EButtonGroupAction action,
    double segmentHeight,
    double minimumTarget,
  ) {
    final requestedMinimum =
        action.decoration?.minimumSize ?? widget.decoration?.minimumSize;
    final restingMinWidth =
        action.minWidth ?? (action.isIconOnly ? segmentHeight : null) ?? 0;
    return Size(
      math.max(
        math.max(requestedMinimum?.width ?? 0, restingMinWidth),
        widget._connected ? minimumTarget : 0,
      ),
      math.max(requestedMinimum?.height ?? 0, segmentHeight),
    );
  }

  void _updateIconOnlyNaturalSizeCache() {
    final groupTheme = M3ETheme.of(context).buttonGroupTheme;
    _iconOnlyNaturalSizeCache = groupTheme.containerHeightFor(
      widget.size,
      density: widget.density,
    );
  }

  void _measureButtonWidths(int generation, {int attempt = 0}) {
    if (_stateDisposed || !mounted || generation != _measurementGeneration) {
      return;
    }
    final scan = _scanLabeledButtonWidths();
    if (_stateDisposed || !mounted || generation != _measurementGeneration) {
      return;
    }
    if (scan.pending && attempt < 5) {
      _scheduleButtonWidthMeasure(generation, attempt);
      return;
    }
    _publishButtonWidthMeasure(scan.anyChanged);
  }

  ({bool anyChanged, bool pending}) _scanLabeledButtonWidths() {
    var anyChanged = false;
    var pending = false;
    for (var i = 0; i < widget.actions.length; i++) {
      final action = widget.actions[i];
      if (action.label == null && action.selectedLabel == null) {
        continue;
      }
      if (_measureLabeledButtonWidth(i)) {
        anyChanged = true;
      }
      if (!_isMeasured(i)) {
        pending = true;
      }
    }
    return (anyChanged: anyChanged, pending: pending);
  }

  void _scheduleButtonWidthMeasure(int generation, int attempt) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_stateDisposed && mounted && generation == _measurementGeneration) {
        _measureButtonWidths(generation, attempt: attempt + 1);
      }
    });
  }

  void _publishButtonWidthMeasure(bool anyChanged) {
    if (!anyChanged) {
      if (_allOverflowExtentsMeasured()) {
        _overflowController.stableAllOverflowMeasured.value = true;
      }
      return;
    }
    setState(() {
      if (_allOverflowExtentsMeasured()) {
        _overflowController.stableAllOverflowMeasured.value = true;
      }
    });
  }

  bool _measureLabeledButtonWidth(int index) {
    final action = widget.actions[index];
    if (action.label == null && action.selectedLabel == null) {
      return false;
    }

    var changed = _captureMeasuredWidth(
      key: _unselectedKeys[index],
      into: _measuredUnselectedWidths,
      index: index,
    );

    if (!_needsDistinctSelectedMeasurement(action)) {
      final resolved =
          _measuredUnselectedWidths[index] ?? _iconOnlyNaturalSizeCache;
      if (_measuredSelectedWidths[index] != resolved) {
        _measuredSelectedWidths[index] = resolved;
        changed = true;
      }
      return changed;
    }

    return _captureMeasuredWidth(
          key: _selectedKeys[index],
          into: _measuredSelectedWidths,
          index: index,
        ) ||
        changed;
  }

  bool _captureMeasuredWidth({
    required GlobalKey key,
    required List<double?> into,
    required int index,
  }) {
    final render = key.currentContext?.findRenderObject() as RenderBox?;
    if (render == null || !render.hasSize) {
      return false;
    }
    final measured = render.size.width;
    if (into[index] == measured) {
      return false;
    }
    into[index] = measured;
    return true;
  }

  void _scheduleMeasurementIfNeeded() {
    if (_hasAnyLabel) {
      final gen = _measurementGeneration;
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _measureButtonWidths(gen),
      );
    }
  }

  void _initControllers() {
    _controllers = List.generate(widget.actions.length, (i) {
      final c = WidgetStatesController();
      c.addListener(() => _onButtonStateChanged(i, c));
      return c;
    });
  }

  Widget _buildOffstageMeasurer(BuildContext context) {
    return IgnorePointer(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (int i = 0; i < widget.actions.length; i++)
            _buildOffstageMeasurerItem(i),
        ],
      ),
    );
  }

  Widget _buildOffstageMeasurerItem(int index) {
    final action = widget.actions[index];
    final decoration = index < _cachedDecorations.length
        ? _cachedDecorations[index]
        : widget.decoration;

    if (!_needsDistinctSelectedMeasurement(action)) {
      return M3EButton(
        key: _unselectedKeys[index],
        onPressed: () {},
        style: widget.style,
        size: _mapSize(
          widget.size,
          actionWidth: action.width,
          iconOnly: action.isIconOnly,
        ),
        decoration: decoration,
        icon: action.icon,
        label: action.label,
        isSelected: false,
        selectedIcon: action.selectedIcon,
        selectedLabel: action.selectedLabel,
        enabled: action.enabled,
        enableFeedback: action.enableFeedback ?? widget.enableFeedback,
      );
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        M3EButton(
          key: _unselectedKeys[index],
          onPressed: () {},
          style: widget.style,
          size: _mapSize(
            widget.size,
            actionWidth: action.width,
            iconOnly: action.isIconOnly,
          ),
          decoration: decoration,
          icon: action.icon,
          label: action.label,
          isSelected: false,
          selectedIcon: action.selectedIcon,
          selectedLabel: action.selectedLabel,
          enabled: action.enabled,
          enableFeedback: action.enableFeedback ?? widget.enableFeedback,
        ),
        M3EButton(
          key: _selectedKeys[index],
          onPressed: () {},
          style: widget.style,
          size: _mapSize(
            widget.size,
            actionWidth: action.width,
            iconOnly: action.isIconOnly,
          ),
          decoration: decoration,
          icon: action.icon,
          label: action.selectedLabel ?? action.label,
          isSelected: true,
          selectedIcon: action.selectedIcon,
          selectedLabel: action.selectedLabel,
          enabled: action.enabled,
          enableFeedback: action.enableFeedback ?? widget.enableFeedback,
        ),
      ],
    );
  }

  void _disposeControllers() {
    for (final c in _controllers) {
      c.dispose();
    }
    _controllers.clear();
  }

  void _initFocusNodes() {
    _focusNodes = _ButtonGroupFocusManager.buildInternalFocusNodes(
      widget.actions,
    );
  }

  void _disposeFocusNodes() {
    _ButtonGroupFocusManager.disposeInternalFocusNodes(_focusNodes);
  }

  int _computeFocusNodeSignature(List<M3EButtonGroupAction> actions) {
    return _ButtonGroupFocusManager.computeFocusNodeSignature(actions);
  }

  int _computeLayoutSignature(M3EButtonGroup group) {
    var actionsHash = 0;
    for (final action in group.actions) {
      actionsHash = Object.hash(actionsHash, _actionLayoutSignature(action));
    }

    // Decoration is excluded: colors / sides do not change intrinsic size.
    final styleHash = Object.hash(
      group.type,
      group.shape,
      group.size,
      group.style,
      group.density,
    );

    return Object.hash(
      group.direction,
      group.neighborSquish,
      group.expandedRatio,
      group.overflow,
      group.overflowMenuStyle,
      group.spacing,
      styleHash,
      actionsHash,
    );
  }

  int _actionLayoutSignature(M3EButtonGroupAction action) {
    // Decoration is excluded: selection-dependent colors must not wipe
    // measured widths. Size comes from content / explicit width.
    return Object.hash(
      _widgetContentHash(action.icon),
      _widgetContentHash(action.selectedIcon),
      _widgetContentHash(action.label),
      _widgetContentHash(action.selectedLabel),
      action.enabled,
      action.minWidth,
      action.width,
    );
  }

  int _widgetContentHash(Widget? w) {
    if (w == null) {
      return 0;
    }
    if (w is Icon) {
      return Object.hash(w.icon, w.size);
    }
    if (w is Text) {
      return Object.hash(w.data, w.style?.fontSize, w.style?.fontWeight);
    }
    if (w is Flex) {
      return Object.hash(
        w.runtimeType,
        w.mainAxisSize,
        w.direction,
        Object.hashAll(w.children.map(_widgetContentHash)),
      );
    }
    if (w is Padding) {
      return Object.hash(w.padding, _widgetContentHash(w.child));
    }
    if (w is SizedBox) {
      return Object.hash(w.width, w.height, _widgetContentHash(w.child));
    }
    if (w is Center) {
      return Object.hash(
        w.widthFactor,
        w.heightFactor,
        _widgetContentHash(w.child),
      );
    }
    if (w is Align) {
      return Object.hash(
        w.alignment,
        w.widthFactor,
        w.heightFactor,
        _widgetContentHash(w.child),
      );
    }
    // Unknown custom widgets: identity is the safest size-signal fallback.
    return w.hashCode;
  }

  void _onButtonStateChanged(int index, WidgetStatesController c) {
    if (_stateDisposed || !mounted) {
      return;
    }

    final isPressed = c.value.contains(WidgetState.pressed);
    _pressCoordinator.handlePressedStateChange(
      index: index,
      isPressed: isPressed,
    );
    _syncFocusedIndexFromStates(
      index,
      isFocused: c.value.contains(WidgetState.focused),
    );
  }

  void _syncFocusedIndexFromStates(int index, {required bool isFocused}) {
    if (isFocused && _focusedIndexNotifier.value != index) {
      _runFocusNotifierUpdate(() => _focusedIndexNotifier.value = index);
      return;
    }
    if (!isFocused && _focusedIndexNotifier.value == index) {
      _runFocusNotifierUpdate(() {
        if (_focusedIndexNotifier.value == index) {
          _focusedIndexNotifier.value = null;
        }
      });
    }
  }

  void _runFocusNotifierUpdate(VoidCallback update) {
    if (_stateDisposed || !mounted) {
      return;
    }
    if (SchedulerBinding.instance.schedulerPhase ==
        SchedulerPhase.persistentCallbacks) {
      SchedulerBinding.instance.addPostFrameCallback((_) {
        if (!_stateDisposed && mounted) {
          update();
        }
      });
      return;
    }
    update();
  }

  double _naturalSizeForButton(BuildContext context, int index) {
    if (index < 0 || index >= widget.actions.length) {
      return _iconOnlyNaturalSizeCache;
    }

    final action = widget.actions[index];
    if (action.width != null) {
      return action.width!;
    }

    final restingFloor =
        action.minWidth ?? (action.isIconOnly ? _iconOnlyNaturalSizeCache : 0);
    final fallback = M3ETheme.of(context).buttonGroupTheme
        .fallbackChildWidth(widget.size);

    if (index >= _measuredUnselectedWidths.length) {
      return math.max(restingFloor, fallback);
    }

    final unselectedWidth = _measuredUnselectedWidths[index];
    final selectedWidth = _measuredSelectedWidths[index];
    if (unselectedWidth == null) {
      return math.max(restingFloor, fallback);
    }
    final resolvedSelected = selectedWidth ?? unselectedWidth;

    if (!widget._connected && _needsDistinctSelectedMeasurement(action)) {
      return math.max(
        restingFloor,
        math.max(unselectedWidth, resolvedSelected),
      );
    }

    final bool selected = _isActionSelected(index);
    final measured = selected ? resolvedSelected : unselectedWidth;
    return math.max(restingFloor, measured);
  }
}
