part of '../m3e_split_buttons.dart';

/// Menu open/show helpers for [_M3ESplitButtonState].
extension _M3ESplitButtonMenu<T> on _M3ESplitButtonState<T> {
  Future<void> _openMenu(BuildContext context) async {
    if (!widget.enabled) {
      return;
    }
    if (widget.menuBuilder != null) {
      await _showNativeMenu(context);
      return;
    }
    if (widget.m3eMenuBuilder != null) {
      await _showM3eCustomMenu(context);
      return;
    }

    final items = widget.items!;
    final menuStyle =
        widget.decoration?.menuStyle ?? M3ESplitButtonMenuStyle.popup;

    switch (menuStyle) {
      case M3ESplitButtonMenuStyle.popup:
        await _showSpringPopup(context, items);
      case M3ESplitButtonMenuStyle.bottomSheet:
        await _showBottomSheet(context, items);
      case M3ESplitButtonMenuStyle.native:
        await _showNativeMenu(context, items: items);
    }
  }

  Future<void> _showM3eCustomMenu(BuildContext context) async {
    final menuStyle =
        widget.decoration?.menuStyle ?? M3ESplitButtonMenuStyle.popup;
    if (menuStyle == M3ESplitButtonMenuStyle.bottomSheet) {
      assert(() {
        debugPrint(
          'M3ESplitButton.m3eMenuBuilder does not support '
          'M3ESplitButtonMenuStyle.bottomSheet; falling back to popup.',
        );
        return true;
      }(), 'm3eMenuBuilder does not support bottomSheet menu style');
    }
    final useNativeTheme = menuStyle == M3ESplitButtonMenuStyle.native;
    await _showM3eMenuNodes(
      context,
      widget.m3eMenuBuilder!(context),
      applyPopupTheme: !useNativeTheme,
    );
  }

  Future<void> _showSpringPopup(
    BuildContext context,
    List<M3ESplitButtonItem<T>> items,
  ) async {
    final iconSize = _splitTheme.splitIcon(widget.size);
    final nodes = <M3EMenuNode>[
      for (final item in items) _splitItemToMenuNode(item, iconSize: iconSize),
    ];
    await _showM3eMenuNodes(context, nodes, applyPopupTheme: true);
  }

  Future<void> _showM3eMenuNodes(
    BuildContext context,
    List<M3EMenuNode> nodes, {
    required bool applyPopupTheme,
  }) async {
    setState(() => _menuOpen = true);

    final tCtx = _trailingKey.currentContext;
    if (tCtx == null) {
      _closeMenu();
      return;
    }
    final Rect? anchor = m3eOverlayRectFor(tCtx);
    final tb = tCtx.findRenderObject() as RenderBox?;
    if (anchor == null || tb == null || !tb.hasSize) {
      _closeMenu();
      return;
    }

    final popupDec =
        widget.decoration?.popupDecoration ??
        M3ESplitButtonPopupDecoration(
          backgroundColor: _splitTheme.popupBackgroundColor(_scheme),
          elevation: _splitTheme.popupElevation,
          borderRadius: BorderRadius.circular(_splitTheme.popupBorderRadius),
          offset: _splitTheme.popupOffset,
          minWidth: _splitTheme.popupMinWidth,
          maxWidth: _splitTheme.popupMaxWidth,
          maxHeight: _splitTheme.popupMaxHeight,
          padding: _splitTheme.popupPadding,
          motion: _splitTheme.popupMotion,
        );

    final M3EMenuTheme? menuTheme = applyPopupTheme
        ? M3ETheme.of(context).menuTheme.copyWith(
            minWidth: popupDec.minWidth,
            maxWidth: popupDec.maxWidth,
            maxHeight: popupDec.maxHeight,
            elevation: popupDec.elevation ?? _splitTheme.popupElevation,
            backgroundColor: popupDec.backgroundColor,
            anchorOffset: popupDec.offset.dy != 0
                ? popupDec.offset.dy
                : M3ETheme.of(context).menuTheme.anchorOffset,
          )
        : null;

    final double preferredWidth = applyPopupTheme
        ? (tb.size.width + 176.0).clamp(popupDec.minWidth, popupDec.maxWidth)
        : tb.size.width;

    final res = await showM3EMenu<T>(
      context: context,
      anchor: anchor,
      children: nodes,
      selectedValue: widget.selectedValue,
      preferredWidth: preferredWidth,
      callerFocusNode: _trailingFocusNode,
      themeOverride: menuTheme,
    );

    if (!mounted) {
      return;
    }
    _closeMenu();
    if (res != null && widget.onSelected != null) {
      widget.onSelected!(res);
    }
  }

  /// Maps a split-button item to a menu node using [M3EMenuTheme] colors
  /// (not the split button's on-container color).
  M3EMenuNode _splitItemToMenuNode(
    M3ESplitButtonItem<T> item, {
    required double iconSize,
  }) {
    final selected =
        widget.selectedValue != null && widget.selectedValue == item.value;
    final theme = M3ETheme.of(context);
    final menuTheme = theme.menuTheme;
    final scheme = theme.colorScheme;
    final foreground = menuTheme.entryForegroundColor(
      scheme,
      enabled: item.enabled,
    );
    final labelStyle = menuTheme.entryLabelStyle(
      theme.typeScale,
      scheme,
      enabled: item.enabled,
    );

    if (item.child is IconData) {
      return M3EMenuSelectable(
        label: item.child.toString(),
        value: item.value as Object,
        enabled: item.enabled,
        selected: selected,
        leading: Icon(item.child as IconData, size: iconSize),
      );
    }

    if (item.child is Widget) {
      return M3EMenuWidget(
        value: item.value,
        enabled: item.enabled,
        selected: selected,
        child: IconTheme.merge(
          data: IconThemeData(color: foreground, size: iconSize),
          child: DefaultTextStyle.merge(
            style: labelStyle,
            child: item.child as Widget,
          ),
        ),
      );
    }

    return M3EMenuSelectable(
      label: item.child.toString(),
      value: item.value as Object,
      enabled: item.enabled,
      selected: selected,
    );
  }

  Future<void> _showBottomSheet(
    BuildContext context,
    List<M3ESplitButtonItem<T>> items,
  ) async {
    setState(() => _menuOpen = true);

    final (_, onCont, _, _) = _resolveColorsAndShapes(
      context,
      segmentEnabled: widget.enabled,
    );

    final bottomSheetDec =
        widget.decoration?.bottomSheetDecoration ??
        const M3ESplitButtonBottomSheetDecoration();

    final isMultiSelect =
        bottomSheetDec.selectionMode == M3ESplitButtonSelectionMode.multiple;

    if (isMultiSelect) {
      await _showMultiSelectBottomSheet(context, items, bottomSheetDec, onCont);
    } else {
      await _showSingleSelectBottomSheet(
        context,
        items,
        bottomSheetDec,
        onCont,
      );
    }
  }

  Future<void> _showMultiSelectBottomSheet(
    BuildContext context,
    List<M3ESplitButtonItem<T>> items,
    M3ESplitButtonBottomSheetDecoration bottomSheetDec,
    Color onCont,
  ) async {
    final result = await showSplitButtonBottomSheet<T>(
      context: context,
      items: items,
      decoration: bottomSheetDec,
      foregroundColor: widget.decorationMenuForegroundColor ?? onCont,
      iconSize: _splitTheme.splitIcon(widget.size),
      callerFocusNode: _trailingFocusNode,
      selectedValues: _selectedValues?.cast<T>(),
    );

    if (!mounted) {
      return;
    }
    if (result is List) {
      final Set<T> selectedSet = result.cast<T>().toSet();
      _selectedValues = selectedSet;
      widget.onMultiSelected?.call(selectedSet);
    }
    _closeMenu();
  }

  Future<void> _showSingleSelectBottomSheet(
    BuildContext context,
    List<M3ESplitButtonItem<T>> items,
    M3ESplitButtonBottomSheetDecoration bottomSheetDec,
    Color onCont,
  ) async {
    final res = await showSplitButtonBottomSheet<T>(
      context: context,
      items: items,
      decoration: bottomSheetDec,
      foregroundColor: widget.decorationMenuForegroundColor ?? onCont,
      iconSize: _splitTheme.splitIcon(widget.size),
      callerFocusNode: _trailingFocusNode,
    );

    if (!mounted) {
      return;
    }
    _closeMenu();
    if (res != null && widget.onSelected != null) {
      widget.onSelected!(res as T);
    }
  }

  Future<void> _showNativeMenu(
    BuildContext context, {
    List<M3ESplitButtonItem<T>>? items,
  }) async {
    setState(() => _menuOpen = true);

    final trailingBox = _trailingRenderBox();
    final tSize = trailingBox?.size ?? Size.zero;
    final double minMenuWidth = tSize.width > 0
        ? tSize.width
        : _splitTheme.splitTrailingWidth(widget.size);

    if (items == null) {
      await _showCustomNativeMenu(context, minMenuWidth);
      return;
    }

    if (trailingBox == null) {
      _closeMenu();
      return;
    }

    await _showItemsNativeMenu(context, items, trailingBox, minMenuWidth);
  }

  RenderBox? _trailingRenderBox() {
    final tCtx = _trailingKey.currentContext;
    if (tCtx == null) {
      return null;
    }
    return tCtx.findRenderObject() as RenderBox?;
  }

  Future<void> _showCustomNativeMenu(
    BuildContext context,
    double minMenuWidth,
  ) async {
    final res = await showMenu<T>(
      context: context,
      position: _menuPosition(context),
      constraints: BoxConstraints(minWidth: minMenuWidth),
      items: widget.menuBuilder!(context),
    );
    if (!mounted) {
      return;
    }
    _closeMenu();
    if (res != null && widget.onSelected != null) {
      widget.onSelected!(res);
    }
  }

  Future<void> _showItemsNativeMenu(
    BuildContext context,
    List<M3ESplitButtonItem<T>> items,
    RenderBox tb,
    double minMenuWidth,
  ) async {
    final iconSize = _splitTheme.splitIcon(widget.size);
    final nodes = <M3EMenuNode>[
      for (final item in items) _splitItemToMenuNode(item, iconSize: iconSize),
    ];

    final BuildContext? tCtx = _trailingKey.currentContext;
    final Rect anchor =
        (tCtx != null ? m3eOverlayRectFor(tCtx) : null) ??
        (tb.localToGlobal(Offset.zero) & tb.size);
    final res = await showM3EMenu<T>(
      context: context,
      anchor: anchor,
      children: nodes,
      selectedValue: widget.selectedValue,
      preferredWidth: minMenuWidth,
      callerFocusNode: _trailingFocusNode,
    );

    if (!mounted) {
      return;
    }
    _closeMenu();
    if (res != null && widget.onSelected != null) {
      widget.onSelected!(res);
    }
  }

  RelativeRect _menuPosition(BuildContext context) {
    final overlay =
        Overlay.of(context).context.findRenderObject()! as RenderBox;

    final BuildContext tCtx = _trailingKey.currentContext ?? context;
    final targetBox = tCtx.findRenderObject()! as RenderBox;

    final Offset targetTopLeft = targetBox.localToGlobal(
      Offset.zero,
      ancestor: overlay,
    );
    final targetRect = Rect.fromLTWH(
      targetTopLeft.dx,
      targetTopLeft.dy,
      targetBox.size.width,
      targetBox.size.height,
    );

    const kMenuVerticalOffset = 4;
    final double top = targetRect.bottom + kMenuVerticalOffset;
    final left = targetRect.left;
    final right = overlay.size.width - targetRect.right;

    return RelativeRect.fromLTRB(left, top, right, overlay.size.height - top);
  }
}
