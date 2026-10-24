part of '../m3e_fab_menu.dart';

/// Open menu overlay: scrim, scrolling items, and the morphing close FAB.
class _M3EFabMenuOverlay {
  const _M3EFabMenuOverlay(
    this.menu, {
    required this.closedSize,
    required this.openSize,
  });

  final _M3EFabMenuState menu;
  final double closedSize;
  final double openSize;

  Widget build(BuildContext context) {
    final bool right = menu._isRight;
    // Close top trailing corner shares the FAB's top trailing morph anchor.
    return FocusTraversalGroup(
      policy: OrderedTraversalPolicy(),
      child: FocusScope(
        node: menu._menuFocusScope,
        skipTraversal: true,
        child: CallbackShortcuts(
          bindings: <ShortcutActivator, VoidCallback>{
            const SingleActivator(LogicalKeyboardKey.escape): menu._close,
          },
          child: Stack(
            clipBehavior: Clip.none,
            children: <Widget>[
              _dismissBarrier(context),
              CompositedTransformFollower(
                link: menu._link,
                showWhenUnlinked: false,
                targetAnchor: right ? Alignment.topRight : Alignment.topLeft,
                followerAnchor: right ? Alignment.topRight : Alignment.topLeft,
                child: _menuStack(context),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _dismissBarrier(BuildContext context) {
    final theme = M3ETheme.of(context);
    final fabMenuTheme = theme.fabMenuTheme;
    return Positioned.fill(
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: menu._close,
        child: ColoredBox(color: fabMenuTheme.scrimColor(theme.colorScheme)),
      ),
    );
  }

  double _spaceAboveFab() {
    final box = menu._fabKey.currentContext?.findRenderObject();
    if (box is! RenderBox || !box.hasSize) {
      return 240;
    }
    final fabTop = box.localToGlobal(Offset.zero).dy;
    final safeTop = MediaQuery.paddingOf(menu.context).top;
    return (fabTop - safeTop).clamp(0.0, double.infinity);
  }

  Widget _menuStack(BuildContext context) {
    final theme = M3ETheme.of(context);
    final fabMenuTheme = theme.fabMenuTheme;
    final double spaceAbove = _spaceAboveFab();
    final double mediaWidth = MediaQuery.sizeOf(context).width;

    // Shift upward so the closed-FAB-sized close slot sits on the FAB top
    // trailing corner, while the full scroll area (above + slot) receives hits.
    return Transform.translate(
      offset: Offset(0, -spaceAbove),
      child: SizedBox(
        width: mediaWidth,
        height: spaceAbove + closedSize,
        child: Stack(
          children: <Widget>[
            Positioned.fill(
              // Clear the closed-size close slot (top-trailing morph anchor) plus
              // menuOffset — not openSize, or medium/large FABs overlap items.
              child: Padding(
                padding: EdgeInsets.only(
                  bottom: closedSize + fabMenuTheme.menuOffset,
                ),
                child: Align(
                  alignment: menu._isRight
                      ? Alignment.bottomRight
                      : Alignment.bottomLeft,
                  child: SingleChildScrollView(
                    primary: false,
                    reverse: true,
                    child: _M3EFabMenuItems(menu).build(theme),
                  ),
                ),
              ),
            ),
            Positioned(
              right: menu._isRight ? 0 : null,
              left: menu._isRight ? null : 0,
              bottom: 0,
              width: closedSize,
              height: closedSize,
              child: Align(
                alignment: menu._fabAlign,
                child: FocusTraversalOrder(
                  order: const NumericFocusOrder(0),
                  child: _overlayClose(theme),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _overlayClose(M3EThemeData theme) {
    final fabMenuTheme = theme.fabMenuTheme;
    final fabTheme = theme.fabTheme;
    final M3EFabColor closeColor = M3EFabMenuTheme.closeFabColor(
      menu._colorSet,
    );
    final double resolvedClosedSize = fabMenuTheme.resolveClosedContainer(
      size: menu.widget.size,
      fabTheme: fabTheme,
    );
    final metrics = fabTheme.resolve(
      size: menu.widget.size,
      color: menu.widget.color,
      scheme: theme.colorScheme,
    );
    final double regularIcon = fabTheme
        .resolve(
          size: M3EFabSize.regular,
          color: closeColor,
          scheme: theme.colorScheme,
        )
        .iconSize;

    return AnimatedBuilder(
      animation: menu._fabShapeCtrl,
      builder: (BuildContext context, Widget? child) {
        return _paintOverlayClose(
          theme: theme,
          t: menu._fabShapeCtrl.value.clamp(0.0, 1.0),
          resolvedClosedSize: resolvedClosedSize,
          closeColor: closeColor,
          metrics: metrics,
          regularIcon: regularIcon,
        );
      },
    );
  }

  Widget _paintOverlayClose({
    required M3EThemeData theme,
    required double t,
    required double resolvedClosedSize,
    required M3EFabColor closeColor,
    required M3EFabMetrics metrics,
    required double regularIcon,
  }) {
    final fabMenuTheme = theme.fabMenuTheme;
    // Shrink toward top trailing: large closed square → 56 circle.
    final double fabSize = lerpDouble(resolvedClosedSize, openSize, t)!;
    final double radius = lerpDouble(metrics.radius, openSize / 2, t)!;
    final double iconSize = lerpDouble(
      metrics.iconSize,
      fabMenuTheme.closeIconSize,
      t,
    )!;
    final double iconScale = iconSize / regularIcon;
    final bool filled = t > 0.35;
    return Align(
      alignment: menu._fabAlign,
      child: Semantics(
        button: true,
        label: 'Toggle menu',
        expanded: true,
        child: SizedBox(
          width: fabSize,
          height: fabSize,
          child: FittedBox(
            child: M3EFab(
              focusNode: menu._closeFocusNode,
              icon: Transform.scale(
                scale: iconScale,
                child: t > 0.5
                    ? menu._resolvedCollapseIcon
                    : menu._resolvedExpandIcon,
              ),
              color: filled ? closeColor : menu.widget.color,
              size: filled ? M3EFabSize.regular : menu.widget.size,
              cornerRadius: radius,
              decoration: filled ? null : menu.widget.decoration,
              elevation: fabMenuTheme.closeElevation,
              hoverElevation: fabMenuTheme.closeHoverElevation,
              onPressed: menu._close,
            ),
          ),
        ),
      ),
    );
  }
}
