part of 'm3e_menu_popup.dart';

extension _M3EMenuPopupLayout<T> on _M3EMenuPopupState<T> {
  Widget _buildPopup(
    BuildContext context,
    M3EMenuTheme menuTheme,
    M3EColorScheme scheme,
  ) {
    final placement = M3EMenuPlacer.compute(
      screenSize: MediaQuery.sizeOf(context),
      anchorRect: widget.anchor,
      theme: menuTheme,
      position: widget.position,
      textDirection: Directionality.of(context),
      approximateItemCount: M3EMenuPlacer.approximateItemCount(widget.children),
      preferredWidth: widget.preferredWidth,
    );
    final scaleAlignment = placement.opensAbove
        ? Alignment.bottomCenter
        : Alignment.topCenter;

    return Actions(
      actions: _menuTraversalActions(),
      child: FocusScope(
        node: _focusScopeNode,
        child: Focus(
          focusNode: _keyFocus,
          onKeyEvent: _onKeyEvent,
          child: Stack(
            clipBehavior: Clip.none,
            children: <Widget>[
              _menuScrim(menuTheme, scheme),
              _anchoredMenu(
                placement: placement,
                menuTheme: menuTheme,
                scheme: scheme,
                scaleAlignment: scaleAlignment,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Map<Type, Action<Intent>> _menuTraversalActions() {
    return <Type, Action<Intent>>{
      NextFocusIntent: CallbackAction<NextFocusIntent>(
        onInvoke: (NextFocusIntent intent) {
          _exitMenu(forward: true);
          return null;
        },
      ),
      PreviousFocusIntent: CallbackAction<PreviousFocusIntent>(
        onInvoke: (PreviousFocusIntent intent) {
          _exitMenu(forward: false);
          return null;
        },
      ),
    };
  }

  Widget _menuScrim(M3EMenuTheme menuTheme, M3EColorScheme scheme) {
    return Positioned.fill(
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: _dismiss,
        child: ColoredBox(color: menuTheme.scrimColor(scheme)),
      ),
    );
  }

  Widget _anchoredMenu({
    required M3EMenuPlacement placement,
    required M3EMenuTheme menuTheme,
    required M3EColorScheme scheme,
    required Alignment scaleAlignment,
  }) {
    return Positioned(
      left: placement.left,
      width: placement.width,
      top: placement.top,
      bottom: placement.bottom,
      child: AnimatedBuilder(
        animation: _expandCtrl,
        builder: (BuildContext context, Widget? child) {
          return _expandTransform(
            progress: _expandCtrl.value,
            scaleAlignment: scaleAlignment,
            child: child,
          );
        },
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minWidth: menuTheme.minWidth,
            maxWidth: placement.width,
            maxHeight: placement.maxHeight,
          ),
          child: _buildSurfaces(
            menuTheme: menuTheme,
            scheme: scheme,
            maxHeight: placement.maxHeight,
          ),
        ),
      ),
    );
  }

  KeyEventResult _onKeyEvent(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.escape) {
      _dismiss(restoreFocus: true);
      return KeyEventResult.handled;
    }
    final KeyEventResult? arrows = _onArrowKey(key);
    if (arrows != null) {
      return arrows;
    }
    return _onCharacterKey(event, key);
  }

  KeyEventResult? _onArrowKey(LogicalKeyboardKey key) {
    if (key == LogicalKeyboardKey.arrowDown) {
      _move(1);
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.arrowUp) {
      _move(-1);
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.arrowRight ||
        key == LogicalKeyboardKey.arrowLeft) {
      final rtl = Directionality.of(context) == TextDirection.rtl;
      final forward = rtl
          ? key == LogicalKeyboardKey.arrowLeft
          : key == LogicalKeyboardKey.arrowRight;
      if (forward) {
        _openFocusedSubmenu();
      } else if (widget.isSubmenu) {
        _dismiss();
      }
      return KeyEventResult.handled;
    }
    return null;
  }

  KeyEventResult _onCharacterKey(KeyEvent event, LogicalKeyboardKey key) {
    final String? character = event.character;
    final String letter = character != null && character.length == 1
        ? character
        : key.keyLabel;
    if (letter.length == 1 && RegExp('[A-Za-z0-9]').hasMatch(letter)) {
      _typeahead(letter);
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  Widget _expandTransform({
    required double progress,
    required Alignment scaleAlignment,
    required Widget? child,
  }) {
    final clampedProgress = progress.clamp(0.0, 1.5);
    final clampedScale = clampedProgress.clamp(0.0, 1.2);
    if (clampedProgress <= 0.01) {
      return const SizedBox.shrink();
    }
    if (!_didInitialFocus) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _focusFirstEnabled();
        }
      });
    }
    return Opacity(
      opacity: clampedProgress.clamp(0.0, 1.0),
      child: Transform.scale(
        alignment: scaleAlignment,
        scaleY: clampedScale,
        child: child,
      ),
    );
  }

  Widget _buildSurfaces({
    required M3EMenuTheme menuTheme,
    required M3EColorScheme scheme,
    required double maxHeight,
  }) {
    final palette = menuTheme.colors(scheme, widget.colorStyle);
    final shadowPad = menuTheme.elevation * 2;
    final surfaces = m3eMenuPartitionSurfaces(widget.children);
    final scrolling = _estimatedHeight(menuTheme, widget.children) > maxHeight;
    final cards = scrolling
        ? <M3EMenuGroup>[M3EMenuGroup(children: _flattenForScroll(surfaces))]
        : surfaces;
    final painted = <Widget>[];
    for (var i = 0; i < cards.length; i++) {
      if (i > 0 && !scrolling) {
        painted.add(SizedBox(height: menuTheme.sectionGap));
      }
      painted.add(
        _surface(
          menuTheme: menuTheme,
          scheme: scheme,
          palette: palette,
          surface: cards[i],
        ),
      );
    }

    final Widget body = RawScrollbar(
      controller: _scroll,
      thumbVisibility: true,
      child: ListView(
        controller: _scroll,
        padding: EdgeInsets.all(shadowPad),
        shrinkWrap: true,
        children: painted,
      ),
    );

    return M3EMenuKeyScope(
      targets: _targets,
      child: M3EMenuStyleScope(
        colorStyle: widget.colorStyle,
        colors: palette,
        child: body,
      ),
    );
  }

  double _estimatedHeight(M3EMenuTheme theme, List<M3EMenuNode> nodes) {
    final count = M3EMenuPlacer.approximateItemCount(nodes);
    final double gaps = count > 1 ? (count - 1) * theme.itemGap : 0;
    return count * theme.entryHeight + gaps + theme.verticalPadding * 2;
  }

  List<M3EMenuNode> _flattenForScroll(List<M3EMenuGroup> surfaces) {
    final nodes = <M3EMenuNode>[];
    for (var i = 0; i < surfaces.length; i++) {
      if (i > 0) {
        nodes.add(const M3EMenuDivider());
      }
      final M3EMenuGroup surface = surfaces[i];
      if (surface.label != null) {
        nodes.add(
          M3EMenuGroup(label: surface.label, children: surface.children),
        );
      } else {
        nodes.addAll(surface.children);
      }
    }
    return nodes;
  }

  Widget _surface({
    required M3EMenuTheme menuTheme,
    required M3EColorScheme scheme,
    required M3EMenuColors palette,
    required M3EMenuGroup surface,
  }) {
    final Widget content = Padding(
      padding: EdgeInsets.symmetric(
        vertical: menuTheme.verticalPadding,
        horizontal: menuTheme.contentHorizontalPadding,
      ),
      child: M3EMenuContent(
        nodes: surface.children,
        sectionLabel: surface.label,
        selectedValue: widget.selectedValue,
        closeOnSelect: widget.closeOnSelect,
        onSelect: _handleSelect,
        onOpenSubmenu: _openSubmenu,
        autofocusFirst: false,
        applyGroupShapes: false,
      ),
    );
    final Widget clipped = DecoratedBox(
      decoration: ShapeDecoration(
        color: palette.container,
        shape: menuTheme.containerShape,
        shadows: M3EElevation.shadows(
          menuTheme.elevation,
          shadowColor: scheme.shadow,
        ),
      ),
      child: ClipPath(
        clipper: ShapeBorderClipper(shape: menuTheme.containerShape),
        child: content,
      ),
    );
    return clipped;
  }
}
