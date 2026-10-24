part of '../m3e_app_bars.dart';

/// Title block and action row. Actions mode splits them into two layers.
class _M3EBarBody extends StatelessWidget {
  const _M3EBarBody({
    required this.expand,
    required this.topPadding,
    required this.actionRow,
    required this.bottomPadding,
    required this.titleInset,
    required this.contentPadding,
    required this.centerTitle,
    required this.search,
    required this.leading,
    required this.actions,
    required this.title,
    required this.leadingColor,
    required this.trailingColor,
    required this.iconSize,
    required this.separateActions,
    required this.containerColor,
    required this.shadowColor,
    required this.shape,
  });

  final double expand;
  final double topPadding;
  final double actionRow;
  final double bottomPadding;
  final double titleInset;
  final EdgeInsetsGeometry contentPadding;
  final bool centerTitle;
  final bool search;
  final Widget? leading;
  final List<Widget>? actions;
  final Widget? title;
  final Color leadingColor;
  final Color trailingColor;
  final double iconSize;
  final bool separateActions;
  final Color containerColor;
  final Color shadowColor;
  final ShapeBorder shape;

  @override
  Widget build(BuildContext context) {
    final EdgeInsets padding = contentPadding.resolve(
      Directionality.of(context),
    );
    final double pad = padding.left;
    final int actionCount = actions?.length ?? 0;
    final double lead = leading == null ? titleInset : pad + 48;
    final double trail = actionCount == 0 ? pad : pad + 48.0 * actionCount;
    final double shownExpand = expand.clamp(0.0, 1.0);
    final double boxTop =
        lerpDouble(0, topPadding + actionRow, shownExpand) ?? 0;
    final double boxBottom =
        lerpDouble(0, search ? 0 : bottomPadding, shownExpand) ?? 0;
    final double alignY = search
        ? (lerpDouble(0, -1, shownExpand) ?? 0)
        : (lerpDouble(0, 1, shownExpand) ?? 0);
    final Widget? titleBox = title == null
        ? null
        : PositionedDirectional(
            start: lerpDouble(lead, titleInset, shownExpand),
            end: lerpDouble(trail, titleInset, shownExpand),
            top: boxTop,
            bottom: boxBottom,
            child: Align(
              alignment: AlignmentDirectional(centerTitle ? 0 : -1, alignY),
              child: search
                  ? SizedBox(width: double.infinity, child: title)
                  : title,
            ),
          );
    final controls = <Widget>[
      PositionedDirectional(
        top: topPadding,
        start: pad,
        height: actionRow,
        child: IconTheme.merge(
          data: IconThemeData(size: iconSize, color: leadingColor),
          child: leading ?? const SizedBox.shrink(),
        ),
      ),
      if (actions != null && actions!.isNotEmpty)
        PositionedDirectional(
          top: topPadding,
          end: pad,
          height: actionRow,
          child: IconTheme.merge(
            data: IconThemeData(size: iconSize, color: trailingColor),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[for (final Widget action in actions!) action],
            ),
          ),
        ),
    ];
    if (!separateActions) {
      return Stack(children: <Widget>[?titleBox, ...controls]);
    }
    return Material(
      color: containerColor,
      shadowColor: shadowColor,
      surfaceTintColor: const Color(0x00000000),
      shape: shape,
      child: Stack(children: <Widget>[?titleBox]),
    );
  }
}

/// Paints the action band over the page. Behaves like a pinned header: it
/// always scrolls by its full [maxExtent], and the space it pushes the page
/// down by shrinks by exactly the scrolled distance, so the band's edge stays
/// on the content. It never paints shorter than [minExtent], which keeps the
/// action row pinned while the page scrolls beneath it.
class _M3EActionSliver extends SingleChildRenderObjectWidget {
  const _M3EActionSliver({
    required this.maxExtent,
    required this.minExtent,
    required super.child,
  });

  final double maxExtent;
  final double minExtent;

  @override
  RenderSliver createRenderObject(BuildContext context) {
    return _RenderM3EActionSliver(maxExtent: maxExtent, minExtent: minExtent);
  }

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _RenderM3EActionSliver renderObject,
  ) {
    if (renderObject.maxExtent == maxExtent &&
        renderObject.minExtent == minExtent) {
      return;
    }
    renderObject
      ..maxExtent = maxExtent
      ..minExtent = minExtent
      ..markNeedsLayout();
  }
}

class _RenderM3EActionSliver extends RenderSliverSingleBoxAdapter {
  _RenderM3EActionSliver({required this.maxExtent, required this.minExtent});

  double maxExtent;
  double minExtent;

  @override
  double childMainAxisPosition(RenderBox child) => 0;

  @override
  void performLayout() {
    final double scroll = math.max(0, maxExtent);
    final double floor = math.max(0, minExtent);
    final double open = scroll - constraints.scrollOffset;
    final double paint = math.max(floor, open);
    child?.layout(
      constraints.asBoxConstraints(minExtent: paint, maxExtent: paint),
      parentUsesSize: true,
    );
    final double remaining = math.max(
      0,
      constraints.remainingPaintExtent - constraints.overlap,
    );
    final double painted = math.min(paint, remaining);
    geometry = SliverGeometry(
      scrollExtent: scroll,
      paintOrigin: math.min(constraints.overlap, 0),
      paintExtent: painted,
      layoutExtent: open.clamp(0.0, painted),
      maxPaintExtent: math.max(scroll, floor),
      maxScrollObstructionExtent: floor,
      hitTestExtent: painted,
      hasVisualOverflow: true,
    );
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    final RenderBox? box = child;
    if (box == null || geometry?.visible != true) {
      return;
    }
    context.paintChild(box, offset);
  }

  @override
  void applyPaintTransform(RenderObject child, Matrix4 transform) {
    applyPaintTransformForBoxChild(child as RenderBox, transform);
  }
}

/// Actions painted over the bar, with no bar behind them once it slides
/// away. They stay in the page's own tree, so route transitions (slide,
/// fade, clip) move them together with the rest of the page.
class _M3EActionOverlay extends StatelessWidget {
  const _M3EActionOverlay({
    required this.color,
    required this.inset,
    required this.topPadding,
    required this.actionRow,
    required this.contentPadding,
    required this.hide,
    required this.tonal,
    required this.leadingColor,
    required this.trailingColor,
    required this.iconSize,
    required this.leading,
    required this.actions,
    required this.child,
  });

  final Color color;
  final double inset;
  final double topPadding;
  final double actionRow;
  final EdgeInsetsGeometry contentPadding;
  final double hide;
  final Color tonal;
  final Color leadingColor;
  final Color trailingColor;
  final double iconSize;
  final Widget? leading;
  final List<Widget>? actions;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    // The fill only backs the bar itself. The box stays at least as tall as
    // the action row, though, so the actions keep receiving taps after the
    // bar slides away beneath them (hit tests stop at a box's bounds).
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: inset + topPadding + actionRow),
      child: Stack(
        clipBehavior: Clip.none,
        children: <Widget>[
          ColoredBox(color: color, child: child),
          Positioned(
            top: inset + topPadding,
            left: 0,
            right: 0,
            height: actionRow,
            child: _M3EActionRow(
              contentPadding: contentPadding,
              actionRow: actionRow,
              hide: hide,
              tonal: tonal,
              leadingColor: leadingColor,
              trailingColor: trailingColor,
              iconSize: iconSize,
              leading: leading,
              actions: actions,
            ),
          ),
        ],
      ),
    );
  }
}

/// Leading and trailing controls. Each one carries its own fill.
class _M3EActionRow extends StatelessWidget {
  const _M3EActionRow({
    required this.contentPadding,
    required this.actionRow,
    required this.hide,
    required this.tonal,
    required this.leadingColor,
    required this.trailingColor,
    required this.iconSize,
    required this.leading,
    required this.actions,
  });

  final EdgeInsetsGeometry contentPadding;
  final double actionRow;
  final double hide;
  final Color tonal;
  final Color leadingColor;
  final Color trailingColor;
  final double iconSize;
  final Widget? leading;
  final List<Widget>? actions;

  @override
  Widget build(BuildContext context) {
    final EdgeInsets padding = contentPadding.resolve(
      Directionality.of(context),
    );
    final double alpha = hide.clamp(0.0, 1.0);
    return Padding(
      padding: EdgeInsets.only(left: padding.left, right: padding.right),
      child: Row(
        children: <Widget>[
          if (leading != null)
            IconTheme.merge(
              data: IconThemeData(size: iconSize, color: leadingColor),
              child: _plate(leading!, alpha),
            ),
          const Spacer(),
          if (actions != null)
            IconTheme.merge(
              data: IconThemeData(size: iconSize, color: trailingColor),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  for (final Widget action in actions!) _plate(action, alpha),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _plate(Widget child, double alpha) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: alpha <= 0 ? null : tonal.withValues(alpha: alpha),
        borderRadius: BorderRadius.circular(actionRow / 2),
      ),
      child: SizedBox(
        width: actionRow,
        height: actionRow,
        child: Center(child: child),
      ),
    );
  }
}
