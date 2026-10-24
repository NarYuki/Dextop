part of '../m3e_dropdown_menus.dart';

/// Overlay, panel, and search builders for [_M3EDropdownMenuState].
extension _M3EDropdownMenuPanel<T> on _M3EDropdownMenuState<T> {
  Widget _buildOverlay(FormFieldState<List<M3EDropdownItem<T>>?> formState) {
    final renderBox = context.findRenderObject() as RenderBox?;
    if (renderBox == null || !renderBox.attached) {
      return const SizedBox.shrink();
    }

    final showOnTop = _resolveShowOnTop(renderBox);
    final marginOffset = widget.dropdownStyle.marginTop == 0
        ? Offset.zero
        : Offset(
            0,
            showOnTop
                ? -widget.dropdownStyle.marginTop
                : widget.dropdownStyle.marginTop,
          );

    return Stack(
      children: [
        Positioned.fill(
          child: Listener(
            behavior: HitTestBehavior.opaque,
            onPointerDown: _handleOutsideTap,
          ),
        ),
        CompositedTransformFollower(
          link: _layerLink,
          showWhenUnlinked: false,
          targetAnchor: showOnTop ? Alignment.topLeft : Alignment.bottomLeft,
          followerAnchor: showOnTop ? Alignment.bottomLeft : Alignment.topLeft,
          offset: marginOffset,
          child: SizedBox(
            width: renderBox.size.width,
            child: CallbackShortcuts(
              bindings: <ShortcutActivator, VoidCallback>{
                const SingleActivator(LogicalKeyboardKey.escape): _close,
              },
              child: FocusScope(
                node: _focusTrapScope,
                child: FocusTraversalGroup(
                  policy: ReadingOrderTraversalPolicy(),
                  child: RepaintBoundary(child: _buildDropdownPanel(showOnTop)),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  bool _resolveShowOnTop(RenderBox renderBox) {
    if (_openingShowOnTop != null) {
      return _openingShowOnTop!;
    }

    final renderBoxOffset = renderBox.localToGlobal(Offset.zero);
    final screenHeight = MediaQuery.of(context).size.height;
    final spaceBelow =
        screenHeight - renderBoxOffset.dy - renderBox.size.height;
    final spaceAbove = renderBoxOffset.dy;

    switch (widget.dropdownStyle.expandDirection) {
      case M3EDropdownExpandDirection.down:
        return false;
      case M3EDropdownExpandDirection.up:
        return true;
      case M3EDropdownExpandDirection.auto:
        return spaceBelow < widget.dropdownStyle.maxHeight &&
            spaceAbove > spaceBelow;
    }
  }

  bool _toggleFromInside(RenderBox renderBox, PointerDownEvent event) {
    final Offset localPosition = renderBox.globalToLocal(event.position);
    if (!renderBox.paintBounds.contains(localPosition)) {
      return false;
    }
    // The barrier is opaque, so the field does not receive this tap.
    if (_controller.isOpen) {
      _close();
    } else if (_portalController.isShowing) {
      _open();
    }
    return true;
  }

  void _handleOutsideTap(PointerDownEvent event) {
    final renderBox = context.findRenderObject() as RenderBox?;
    if (renderBox != null && renderBox.attached) {
      if (_toggleFromInside(renderBox, event)) {
        return;
      }
    }

    _close();
  }

  Widget _buildDropdownPanel(bool showOnTop) {
    final m3eTheme = M3ETheme.of(context);
    final menuTheme = m3eTheme.dropdownMenuTheme;
    final scheme = m3eTheme.colorScheme;
    final type = m3eTheme.typeScale;
    final dd = widget.dropdownStyle;

    return AnimatedBuilder(
      animation: _expandCtrl,
      builder: (context, child) => _wrapExpandAnimation(showOnTop, child),
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: dd.maxHeight),
        child: Material(
          elevation: dd.elevation,
          color: dd.backgroundColor ?? menuTheme.panelBackgroundColor(scheme),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(
              widget.dropdownStyle.containerRadius ?? widget.containerRadius,
            ),
          ),
          clipBehavior: Clip.antiAlias,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (dd.header != null) dd.header!,
              if (widget.searchEnabled) _buildSearch(context),
              _buildPanelBody(dd, scheme, type),
              if (dd.footer != null) dd.footer!,
            ],
          ),
        ),
      ),
    );
  }

  Widget _wrapExpandAnimation(bool showOnTop, Widget? child) {
    final progress = _expandCtrl.value.clamp(0.0, 1.5);
    final clampedScale = progress.clamp(0.0, 1.2);

    if (progress <= 0.01) {
      return const SizedBox.shrink();
    }

    return Opacity(
      opacity: progress.clamp(0.0, 1.0),
      child: Transform.scale(
        alignment: showOnTop ? Alignment.bottomCenter : Alignment.topCenter,
        scaleY: clampedScale,
        child: child,
      ),
    );
  }

  Widget _buildPanelBody(
    M3EDropdownPanelStyle dd,
    M3EColorScheme scheme,
    M3ETypeScale type,
  ) {
    final filtered = _controller.items;

    if (_isLoading) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_errorMessage != null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Text(
          _errorMessage!,
          style: type.bodyMedium.copyWith(color: scheme.error),
        ),
      );
    }
    if (filtered.isEmpty) {
      return widget.emptyBuilder?.call(context) ??
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              dd.noItemsFoundText,
              style: type.bodyMedium.copyWith(
                color: scheme.onSurface.withValues(alpha: 0.5),
              ),
            ),
          );
    }

    return Flexible(
      child: ListView.separated(
        padding: dd.contentPadding,
        shrinkWrap: true,
        itemCount: filtered.length,
        separatorBuilder: (_, _) =>
            widget.itemSeparator ??
            SizedBox(height: widget.itemStyle.itemGap ?? 5.0),
        itemBuilder: (context, index) =>
            _buildPanelItem(filtered[index], index, filtered.length),
      ),
    );
  }

  Widget _buildPanelItem(M3EDropdownItem<T> item, int index, int total) {
    if (widget.itemBuilder != null) {
      return widget.itemBuilder!(
        item,
        selected: item.selected,
        onTap: () => _onItemTap(item),
      );
    }

    return M3EDropdownMenuItemWidget<T>(
      key: ValueKey(item.value),
      item: item,
      index: index,
      total: total,
      style: widget.itemStyle,
      onTap: () => _onItemTap(item),
    );
  }

  Widget _buildSearch(BuildContext context) {
    final sd = widget.searchStyle;
    final m3eTheme = M3ETheme.of(context);
    final scheme = m3eTheme.colorScheme;
    final type = m3eTheme.typeScale;
    final containerRadius =
        widget.dropdownStyle.containerRadius ?? widget.containerRadius;
    final searchRadius =
        sd.borderRadius ?? BorderRadius.circular(containerRadius);

    return Padding(
      padding: sd.margin,
      child: TextField(
        controller: _searchTextController,
        autofocus: sd.autofocus,
        onTapOutside: (PointerDownEvent event) {
          FocusManager.instance.primaryFocus?.unfocus();
        },
        style: sd.textStyle ?? type.bodyMedium,
        mouseCursor: sd.mouseCursor,
        decoration: _buildSearchDecoration(sd, scheme, m3eTheme, searchRadius),
        onChanged: _handleSearchChanged,
      ),
    );
  }

  InputDecoration _buildSearchDecoration(
    M3EDropdownSearchStyle sd,
    M3EColorScheme scheme,
    M3EThemeData m3eTheme,
    BorderRadius searchRadius,
  ) {
    final Color fillColor = sd.fillColor ?? scheme.surface;
    final bool filled = sd.fillColor == null || sd.filled;
    return InputDecoration(
      hintText: sd.hintText,
      hintStyle: sd.hintStyle,
      filled: filled,
      fillColor: fillColor,
      prefixIcon: Icon(
        Icons.search,
        color: scheme.onSurface.withValues(alpha: 0.5),
        size: m3eTheme.resolvedIconTheme.size,
      ),
      suffixIcon: sd.showClearIcon && _searchTextController.text.isNotEmpty
          ? IconButton(
              icon:
                  sd.clearIcon ??
                  Icon(Icons.clear, size: m3eTheme.resolvedIconTheme.size),
              onPressed: () {
                _searchTextController.clear();
                _searchDebounce?.cancel();
                _controller.setSearchQuery('');
              },
            )
          : null,
      contentPadding: sd.contentPadding,
      border: OutlineInputBorder(
        borderRadius: searchRadius,
        borderSide: BorderSide.none,
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: searchRadius,
        borderSide: BorderSide.none,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: searchRadius,
        borderSide: BorderSide(color: scheme.primary, width: 1.5),
      ),
    );
  }

  void _handleSearchChanged(String value) {
    final debounceMs = widget.searchStyle.searchDebounceMs;
    if (debounceMs <= 0) {
      _controller.setSearchQuery(value);
      return;
    }

    _searchDebounce?.cancel();
    _searchDebounce = Timer(Duration(milliseconds: debounceMs), () {
      _controller.setSearchQuery(value);
    });
  }
}
