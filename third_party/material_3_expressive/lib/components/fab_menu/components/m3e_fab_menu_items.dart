part of '../m3e_fab_menu.dart';

/// Springing menu pills and the focus ring painted above them.
class _M3EFabMenuItems {
  const _M3EFabMenuItems(this.menu);

  final _M3EFabMenuState menu;

  Widget build(M3EThemeData theme) {
    final fabMenuTheme = theme.fabMenuTheme;
    // Stack paints the focus ring after all pills so neighbors cannot cover it.
    // (itemGap 4 < ring outset 5; overlay transforms also defeat elevation.)
    return Stack(
      key: menu._itemsStackKey,
      clipBehavior: Clip.none,
      children: <Widget>[
        Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: menu._isRight
              ? CrossAxisAlignment.end
              : CrossAxisAlignment.start,
          children: <Widget>[
            for (int i = 0; i < menu.widget.items.length; i++)
              if (menu._itemVisible[i])
                FocusTraversalOrder(
                  order: NumericFocusOrder(i + 1.0),
                  child: Padding(
                    key: ValueKey<Object>('fab-menu-item-$i'),
                    padding: EdgeInsets.only(
                      bottom: i == menu.widget.items.length - 1
                          ? 0
                          : fabMenuTheme.itemGap,
                    ),
                    child: _item(theme, menu.widget.items[i], i),
                  ),
                ),
          ],
        ),
        Positioned.fill(
          child: IgnorePointer(
            child: ValueListenableBuilder<int?>(
              valueListenable: menu._focusedItemIndex,
              builder: (BuildContext context, int? focusedIndex, _) {
                if (focusedIndex == null) {
                  return const SizedBox.shrink();
                }
                return AnimatedBuilder(
                  animation: menu._itemCtrls[focusedIndex],
                  builder: (BuildContext context, _) {
                    return _focusedItemRing(theme, focusedIndex);
                  },
                );
              },
            ),
          ),
        ),
      ],
    );
  }

  Widget _focusedItemRing(M3EThemeData theme, int index) {
    final stackBox =
        menu._itemsStackKey.currentContext?.findRenderObject() as RenderBox?;
    final itemBox =
        menu._itemKeys[index].currentContext?.findRenderObject() as RenderBox?;
    if (stackBox == null ||
        itemBox == null ||
        !stackBox.hasSize ||
        !itemBox.hasSize) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (menu.mounted && menu._focusedItemIndex.value == index) {
          menu.setState(() {});
        }
      });
      return const SizedBox.shrink();
    }

    final topLeft = itemBox.localToGlobal(Offset.zero, ancestor: stackBox);
    final fabMenuTheme = theme.fabMenuTheme;
    return Stack(
      clipBehavior: Clip.none,
      children: <Widget>[
        Positioned(
          left: topLeft.dx,
          top: topLeft.dy,
          width: itemBox.size.width,
          height: itemBox.size.height,
          child: M3EFocusRing(
            focused: true,
            radius: BorderRadius.circular(fabMenuTheme.itemHeight / 2),
            width: fabMenuTheme.focusRingWidth,
            gap: fabMenuTheme.focusRingGap,
            color: fabMenuTheme.resolveFocusRingColor(theme.colorScheme),
            child: const SizedBox.expand(),
          ),
        ),
      ],
    );
  }

  Widget _item(M3EThemeData theme, M3EFabMenuItem item, int index) {
    final scheme = theme.colorScheme;
    final fabMenuTheme = theme.fabMenuTheme;
    final SingleMotionController ctrl = menu._itemCtrls[index];
    final Gradient? fill = fabMenuTheme.itemBackgroundGradient;
    final Color containerColor = fabMenuTheme.itemContainerColor(
      scheme,
      menu._colorSet,
    );

    // Only the pill container width springs (and may overshoot). Icon + label
    // stay at their intrinsic size, edge-aligned, and clipped by the stadium.
    return AnimatedBuilder(
      animation: ctrl,
      builder: (BuildContext context, Widget? child) {
        return _itemFrame(
          theme: theme,
          index: index,
          scheme: scheme,
          fill: fill,
          containerColor: containerColor,
          widthFactor: menu._widthFactor(ctrl.value).clamp(0.001, 1.5),
          child: child,
        );
      },
      child: M3ETappable(
        onTap: () => menu._onItemTap(item, index),
        semanticLabel: item.label,
        materialInk: true,
        onStateChanged: (M3EInteractionState state) =>
            menu._setItemFocused(index, state.focused),
        builder: (BuildContext context, M3EInteractionState state) {
          return _itemBody(theme, item, scheme, state);
        },
      ),
    );
  }

  Widget _itemFrame({
    required M3EThemeData theme,
    required int index,
    required M3EColorScheme scheme,
    required Gradient? fill,
    required Color containerColor,
    required double widthFactor,
    required Widget? child,
  }) {
    final fabMenuTheme = theme.fabMenuTheme;
    final Alignment edge = menu._menuItemAlign;
    final Widget body = Align(
      alignment: edge,
      widthFactor: widthFactor,
      child: child,
    );
    return Align(
      alignment: edge,
      child: KeyedSubtree(
        key: menu._itemKeys[index],
        child: _itemOutline(
          fabMenuTheme,
          Material(
            color: fill == null ? containerColor : const Color(0x00000000),
            elevation: fabMenuTheme.itemElevation,
            shadowColor: scheme.shadow,
            surfaceTintColor: const Color(0x00000000),
            shape: const StadiumBorder(),
            clipBehavior: Clip.antiAlias,
            child: fill == null
                ? body
                : DecoratedBox(
                    decoration: BoxDecoration(gradient: fill),
                    child: body,
                  ),
          ),
        ),
      ),
    );
  }

  Widget _itemOutline(M3EFabMenuTheme fabMenuTheme, Widget child) {
    final Gradient? gradient = fabMenuTheme.itemOutlineGradient;
    final Color? color = fabMenuTheme.itemOutlineColor;
    if (gradient == null && color == null) {
      return child;
    }
    return m3eGradientOutlineLayer(
      clipRadius: BorderRadius.circular(fabMenuTheme.itemHeight / 2),
      gradient: gradient,
      color: gradient == null ? color : null,
      width: fabMenuTheme.itemBorderWidth,
      child: child,
    );
  }

  Widget _itemBody(
    M3EThemeData theme,
    M3EFabMenuItem item,
    M3EColorScheme scheme,
    M3EInteractionState state,
  ) {
    final fabMenuTheme = theme.fabMenuTheme;
    final Gradient? foreground = fabMenuTheme.itemForegroundGradient;
    final Color contentColor = foreground == null
        ? fabMenuTheme.itemForegroundColor(scheme, menu._colorSet)
        : m3eGradientForegroundSourceColor;
    Widget content = Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        ExcludeSemantics(
          child: IconTheme.merge(
            data: IconThemeData(
              color: contentColor,
              size: fabMenuTheme.iconSize,
            ),
            child: item.icon,
          ),
        ),
        SizedBox(width: fabMenuTheme.iconLabelGap),
        Text(
          item.label,
          style: fabMenuTheme
              .itemLabelStyle(theme.typeScale, scheme, menu._colorSet)
              .copyWith(color: contentColor),
        ),
      ],
    );
    if (foreground != null) {
      content = ShaderMask(
        blendMode: BlendMode.srcIn,
        shaderCallback: (Rect bounds) => foreground.createShader(bounds),
        child: content,
      );
    }
    return SizedBox(
      height: fabMenuTheme.itemHeight,
      child: M3EStateLayerOverlay(
        state: state,
        color: fabMenuTheme.itemForegroundColor(scheme, menu._colorSet),
        shape: M3EShapes.stadium,
        alignment: Alignment.center,
        child: Padding(
          padding: EdgeInsetsDirectional.only(
            start: fabMenuTheme.itemLeading,
            end: fabMenuTheme.itemTrailing,
          ),
          child: content,
        ),
      ),
    );
  }
}
