import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../cards/enums/m3e_card_variant.dart';
import '../enums/m3e_list_enums.dart';
import 'm3e_card_radius_motion.dart';
import 'm3e_expandable_nest_scope.dart';
import 'm3e_list_drag_proxy_scope.dart';
import 'm3e_list_item_scope.dart';
import 'm3e_list_row_surface.dart';

/// Internal helper to calculate [M3ECardPosition] based on index and total.
M3ECardPosition calculateCardPosition(int index, int total) => total == 1
    ? M3ECardPosition.single
    : index == 0
    ? M3ECardPosition.first
    : index == total - 1
    ? M3ECardPosition.last
    : M3ECardPosition.middle;

/// Internal helper to calculate [BorderRadius] based on [M3ECardPosition].
///
/// When [embedded] is true, items use [innerRadius] by default so the list can
/// sit under another card row. Set [closeBottom] when the parent expandable is
/// last/single, or rounds its sublist bottom, so the nested last (or single)
/// row uses [outerRadius] on the bottom corners.
BorderRadius calculateCardRadius({
  required M3ECardPosition position,
  required double outerRadius,
  required double innerRadius,
  bool embedded = false,
  bool closeBottom = false,
}) {
  if (embedded) {
    final bool isTail =
        position == M3ECardPosition.last || position == M3ECardPosition.single;
    if (closeBottom && isTail) {
      return BorderRadius.vertical(
        top: Radius.circular(innerRadius),
        bottom: Radius.circular(outerRadius),
      );
    }
    return BorderRadius.circular(innerRadius);
  }
  switch (position) {
    case M3ECardPosition.single:
      return BorderRadius.circular(outerRadius);
    case M3ECardPosition.first:
      return BorderRadius.vertical(
        top: Radius.circular(outerRadius),
        bottom: Radius.circular(innerRadius),
      );
    case M3ECardPosition.last:
      return BorderRadius.vertical(
        top: Radius.circular(innerRadius),
        bottom: Radius.circular(outerRadius),
      );
    case M3ECardPosition.middle:
      return BorderRadius.circular(innerRadius);
  }
}

/// A single card item within a card-backed list.
class M3ECardListItem extends StatelessWidget {
  /// M3ECardListItem.
  const M3ECardListItem({
    required this.index,
    required this.position,
    required this.child,
    required this.outerRadius,
    required this.innerRadius,
    required this.gap,
    this.embedded = false,
    this.color,
    this.padding,
    this.onTap,
    this.onLongPress,
    this.semanticLabel,
    this.mouseCursor,
    this.haptic = M3EHapticFeedback.none,
    this.variant = M3ECardVariant.filled,
    this.border,
    this.resolvedColor,
    this.resolvedBorderRadius,
    super.key,
  });

  /// index.

  final int index;

  /// position.
  final M3ECardPosition position;

  /// child.
  final Widget child;

  /// outerRadius.
  final double outerRadius;

  /// innerRadius.
  final double innerRadius;

  /// gap.
  final double gap;

  /// When true, first/last/single use [innerRadius] like middle items, unless
  /// an ancestor [M3EExpandableNestScope] requests closing the bottom.
  final bool embedded;

  /// color.
  final Color? color;

  /// padding.
  final EdgeInsetsGeometry? padding;

  /// Function.
  final void Function(int index)? onTap;

  /// Function.
  final void Function(int index)? onLongPress;

  /// semanticLabel.
  final String? semanticLabel;

  /// mouseCursor.
  final MouseCursor? mouseCursor;

  /// haptic.
  final M3EHapticFeedback haptic;

  /// Card variant for this item.
  final M3ECardVariant variant;

  /// Optional card outline.
  final BorderSide? border;

  /// Per-item color override. When null for an index, uses [color].
  final Color? resolvedColor;

  /// Per-item radius override. When null, uses position-based radii.
  final BorderRadius? resolvedBorderRadius;

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final scheme = theme.colorScheme;
    final cardListTheme = theme.listTheme.cardList;
    final M3EListDragProxyScope? dragProxy = M3EListDragProxyScope.maybeOf(
      context,
    );

    final M3EExpandableNestScope? nest = M3EExpandableNestScope.maybeOf(
      context,
    );
    final bool closeBottom = nest?.closeBottom ?? false;
    final double effectiveOuter = nest?.outerRadius ?? outerRadius;

    final BorderRadius borderRadius = dragProxy != null
        ? BorderRadius.circular(dragProxy.radius)
        : (resolvedBorderRadius ??
              calculateCardRadius(
                position: position,
                outerRadius: effectiveOuter,
                innerRadius: innerRadius,
                embedded: embedded,
                closeBottom: closeBottom,
              ));

    final M3ECardVariant effectiveVariant = dragProxy != null
        ? M3ECardVariant.filled
        : variant;
    final BorderSide? effectiveBorder = dragProxy != null ? null : border;
    final Color? inheritedSurface = color == null && resolvedColor == null
        ? nest?.surfaceColor
        : null;
    final Color? effectiveColor = dragProxy != null
        ? dragProxy.color
        : (resolvedColor ??
              color ??
              inheritedSurface ??
              (variant == M3ECardVariant.outlined
                  ? null
                  : cardListTheme.backgroundColor(scheme, variant: variant)));

    final bool isLast =
        position == M3ECardPosition.last || position == M3ECardPosition.single;

    final VoidCallback? wrappedOnTap = onTap != null
        ? () => onTap!(index)
        : null;
    final VoidCallback? wrappedOnLongPress = onLongPress != null
        ? () => onLongPress!(index)
        : null;
    final parentPads = padding != null;

    return Padding(
      padding: EdgeInsets.only(bottom: isLast ? 0 : gap),
      child: M3ECardRadiusMotion(
        radius: borderRadius,
        builder: (BuildContext context, BorderRadius animatedRadius) {
          return M3EListRowSurface(
            index: index,
            variant: effectiveVariant,
            radius: animatedRadius,
            color: effectiveColor,
            border: effectiveBorder,
            elevation: dragProxy != null ? 0 : null,
            padding: parentPads ? padding! : EdgeInsets.zero,
            onTap: dragProxy != null ? null : wrappedOnTap,
            onLongPress: dragProxy != null ? null : wrappedOnLongPress,
            dragged: dragProxy != null,
            mouseCursor: mouseCursor,
            semanticLabel: semanticLabel,
            haptic: haptic,
            child: M3EListItemScope(padsChild: parentPads, child: child),
          );
        },
      ),
    );
  }
}
