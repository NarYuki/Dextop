part of '../m3e_dropdown_menus.dart';

/// Chip layout and removal animation for [_M3EDropdownMenuState].
extension _M3EDropdownMenuChipBuild<T> on _M3EDropdownMenuState<T> {
  Widget _buildChips(
    BuildContext context,
    List<M3EDropdownItem<T>> selected,
    Color fgColor,
  ) {
    final cd = widget.chipStyle;
    final m3eTheme = M3ETheme.of(context);
    final menuTheme = m3eTheme.dropdownMenuTheme;
    final scheme = m3eTheme.colorScheme;

    final labelStyle =
        cd.labelStyle ?? menuTheme.chipLabelStyle(m3eTheme.typeScale, scheme);
    final chipColor =
        cd.backgroundColor ?? menuTheme.chipBackgroundColor(scheme);

    final maxCount = cd.maxDisplayCount;
    final displayOptions = maxCount != null && selected.length > maxCount
        ? selected.take(maxCount).toList()
        : selected;
    final remainingCount = selected.length - displayOptions.length;

    _syncMoreChipsIndicator(remainingCount);
    _pruneChipControllers(displayOptions);
    _ensureChipSlideControllers(displayOptions);

    final built = _assembleChipWidgets(
      displayOptions: displayOptions,
      cd: cd,
      chipColor: chipColor,
      labelStyle: labelStyle,
      scheme: scheme,
      remainingCount: remainingCount,
    );

    _triggerInsertionSquish(displayOptions);
    _previousChipOrder = displayOptions.map((e) => e.value as Object).toList();

    return _layoutChips(cd, built.widgets, built.slideAnims);
  }

  void _syncMoreChipsIndicator(int remainingCount) {
    if (_shouldAnimateMoreChipsOut(remainingCount)) {
      _beginMoreChipsRemoval();
      return;
    }
    if (remainingCount > 0) {
      _restoreMoreChipsIndicator(remainingCount);
    }
  }

  bool _shouldAnimateMoreChipsOut(int remainingCount) {
    return remainingCount == 0 &&
        _moreChipsLastCount > 0 &&
        !_isMoreChipsRemoving;
  }

  void _beginMoreChipsRemoval() {
    _isMoreChipsRemoving = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      _moreKey.currentState?.animateOut(() {
        if (!mounted) {
          return;
        }
        setState(() {
          _isMoreChipsRemoving = false;
          _moreChipsLastCount = 0;
        });
      });
    });
  }

  void _restoreMoreChipsIndicator(int remainingCount) {
    _moreChipsLastCount = remainingCount;
    if (!_isMoreChipsRemoving) {
      return;
    }
    _isMoreChipsRemoving = false;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      _moreKey.currentState?.animateIn();
    });
  }

  void _pruneChipControllers(List<M3EDropdownItem<T>> displayOptions) {
    final currentKeys = displayOptions.map((e) => e.value as Object).toSet();
    _chipSlideControllers.removeWhere((k, ctrl) {
      if (!currentKeys.contains(k)) {
        ctrl.dispose();
        return true;
      }
      return false;
    });
    _chipKeys.removeWhere(
      (k, _) => !currentKeys.contains(k) && !_removingChips.contains(k),
    );
  }

  void _ensureChipSlideControllers(List<M3EDropdownItem<T>> displayOptions) {
    for (final option in displayOptions) {
      final key = option.value as Object;
      _chipSlideControllers.putIfAbsent(
        key,
        () => SingleMotionController(
          motion: M3EMotion.effectsFast.toMotion(),
          vsync: this,
        ),
      );
    }
  }

  ({List<Widget> widgets, List<Animation<double>> slideAnims})
  _assembleChipWidgets({
    required List<M3EDropdownItem<T>> displayOptions,
    required M3EDropdownChipStyle cd,
    required Color chipColor,
    required TextStyle labelStyle,
    required M3EColorScheme scheme,
    required int remainingCount,
  }) {
    final chipWidgets = <Widget>[];
    final slideAnims = <Animation<double>>[];

    for (var i = 0; i < displayOptions.length; i++) {
      final option = displayOptions[i];
      final optionKey = option.value as Object;
      final chipKey = _chipKeys.putIfAbsent(
        optionKey,
        () => GlobalKey<M3ESpringChipState>(),
      );

      slideAnims.add(_chipSlideControllers[optionKey]!);
      chipWidgets.add(
        M3ESpringChip<T>(
          key: chipKey,
          item: option,
          cd: cd,
          chipColor: chipColor,
          labelStyle: labelStyle,
          scheme: scheme,
          enabled: widget.enabled,
          onRemove: () =>
              _handleChipRemove(option, optionKey, chipKey, displayOptions, i),
          customChild: widget.selectedItemBuilder?.call(option),
        ),
      );
    }

    final showMoreChips = remainingCount > 0 || _isMoreChipsRemoving;
    if (showMoreChips) {
      chipWidgets.add(
        M3EMoreChipsIndicator(
          key: _moreKey,
          count: remainingCount > 0 ? remainingCount : _moreChipsLastCount,
          cd: cd,
          chipColor: chipColor,
          labelStyle: labelStyle,
        ),
      );
      slideAnims.add(const AlwaysStoppedAnimation(0));
    }

    return (widgets: chipWidgets, slideAnims: slideAnims);
  }

  void _triggerInsertionSquish(List<M3EDropdownItem<T>> displayOptions) {
    final newOrder = displayOptions.map((e) => e.value as Object).toList();
    final newKeys = newOrder.toSet().difference(_previousChipOrder.toSet());
    if (newKeys.isEmpty || _previousChipOrder.isEmpty) {
      return;
    }

    final chipsToPush = _chipsPushedByInsertion(newOrder, newKeys);
    if (chipsToPush.isEmpty) {
      return;
    }

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      for (var i = 0; i < chipsToPush.length; i++) {
        final stateKey = _chipKeys[chipsToPush[i]];
        Future.delayed(Duration(milliseconds: i * 25), () {
          if (stateKey?.currentState != null) {
            final intensity = (0.88 + (i * 0.02)).clamp(0.85, 0.98);
            stateKey!.currentState!.triggerSquish(intensity);
          }
        });
      }
    });
  }

  List<Object> _chipsPushedByInsertion(
    List<Object> newOrder,
    Set<Object> newKeys,
  ) {
    var earliestInsertIdx = newOrder.length;
    for (final nk in newKeys) {
      final idx = newOrder.indexOf(nk);
      if (idx < earliestInsertIdx) {
        earliestInsertIdx = idx;
      }
    }

    final chipsToPush = <Object>[];
    for (var i = earliestInsertIdx; i < newOrder.length; i++) {
      final k = newOrder[i];
      if (!newKeys.contains(k)) {
        chipsToPush.add(k);
      }
    }
    return chipsToPush;
  }

  Widget _layoutChips(
    M3EDropdownChipStyle cd,
    List<Widget> chipWidgets,
    List<Animation<double>> slideAnims,
  ) {
    if (cd.wrap) {
      return Wrap(
        spacing: cd.spacing,
        runSpacing: cd.runSpacing,
        children: chipWidgets,
      );
    }

    return ConstrainedBox(
      constraints: BoxConstraints.loose(const Size(double.infinity, 40)),
      child: Flow(
        delegate: M3EChipFlowDelegate(
          slideAnimations: slideAnims,
          spacing: cd.spacing,
        ),
        children: chipWidgets,
      ),
    );
  }

  void _handleChipRemove(
    M3EDropdownItem<T> option,
    Object optionKey,
    GlobalKey<M3ESpringChipState> chipKey,
    List<M3EDropdownItem<T>> displayOptions,
    int removedIndex,
  ) {
    final removedBox = chipKey.currentContext?.findRenderObject() as RenderBox?;
    final removedWidth =
        (removedBox?.size.width ?? 0) + widget.chipStyle.spacing;
    final chipsToAnimate = displayOptions.sublist(removedIndex + 1);

    _removingChips.add(optionKey);
    chipKey.currentState?.animateOut(() {
      if (!mounted) {
        return;
      }
      _finishChipRemoval(option, optionKey, chipsToAnimate, removedWidth);
    });
  }

  void _finishChipRemoval(
    M3EDropdownItem<T> option,
    Object optionKey,
    List<M3EDropdownItem<T>> chipsToAnimate,
    double removedWidth,
  ) {
    _controller.unselectWhere((e) => e.value == option.value);
    _formFieldKey.currentState?.didChange(_controller.selectedItems);
    _removingChips.remove(optionKey);

    final selectedItems = _controller.selectedItems;
    final maxDisplay = widget.chipStyle.maxDisplayCount ?? selectedItems.length;
    final remainingCount = selectedItems.length - maxDisplay;

    _animateChipsAfterRemoval(chipsToAnimate, removedWidth);

    if (remainingCount > 0) {
      Future.delayed(
        Duration(milliseconds: (chipsToAnimate.length + 1) * 20),
        () {
          _moreKey.currentState?.triggerSquish(0.95);
        },
      );
    }
  }

  void _animateChipsAfterRemoval(
    List<M3EDropdownItem<T>> chipsToAnimate,
    double removedWidth,
  ) {
    for (var i = 0; i < chipsToAnimate.length; i++) {
      final item = chipsToAnimate[i];
      final key = item.value as Object;
      final stateKey = _chipKeys[key];
      final slideCtrl = _chipSlideControllers[key];
      if (slideCtrl == null) {
        continue;
      }

      slideCtrl
        ..motion = M3EMotion.effectsFast.toMotion()
        ..animateTo(0, from: removedWidth);

      Future.delayed(Duration(milliseconds: i * 25), () {
        if (stateKey?.currentState != null) {
          final intensity = (0.88 + (i * 0.02)).clamp(0.85, 0.98);
          stateKey!.currentState!.triggerSquish(intensity);
        }
      });
    }
  }
}
