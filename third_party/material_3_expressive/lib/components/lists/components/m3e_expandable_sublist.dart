import 'package:flutter/widgets.dart';

import '../enums/m3e_list_enums.dart';
import '../styles/m3e_expandable_style.dart';
import 'm3e_card_list_item.dart';

/// Corner radii for an expandable parent when [isExpanded] with a list child.
BorderRadius m3eExpandableParentRadius({
  required M3ECardPosition globalPosition,
  required double outerRadius,
  required double innerRadius,
  required bool isExpanded,
  required bool hasSublist,
}) {
  if (!isExpanded || !hasSublist) {
    return calculateCardRadius(
      position: globalPosition,
      outerRadius: outerRadius,
      innerRadius: innerRadius,
    );
  }

  // Expanded with nested list: keep top from global position, bottom = inner.
  switch (globalPosition) {
    case M3ECardPosition.single:
    case M3ECardPosition.first:
      return BorderRadius.vertical(
        top: Radius.circular(outerRadius),
        bottom: Radius.circular(innerRadius),
      );
    case M3ECardPosition.last:
    case M3ECardPosition.middle:
      return BorderRadius.circular(innerRadius);
  }
}

/// Animates a nested list expansion under an expandable parent header.
///
/// Does not wrap rows in cards — pass a card-backed list (or similar) as
/// [child] so gaps, selection, and reorder stay with that list. The expandable
/// parent supplies nest-scope hints so a last parent can close nested bottom
/// radii.
class M3EExpandableSublist extends StatelessWidget {
  /// Creates a nested-list expansion wrapper.
  const M3EExpandableSublist({
    required this.child,
    required this.progress,
    required this.style,
    this.expanded,
    this.topGap,
    super.key,
  });

  /// Whether the parent is expanded (the animation's target).
  ///
  /// When set, rows take focus as soon as the parent starts expanding and
  /// drop it as soon as it starts collapsing, so keyboard arrows reach them
  /// while the spring settles. When null, rows take focus only once fully
  /// revealed.

  /// Nested list widget (typically a card-backed list).
  final Widget child;

  /// Expand progress `0..1` (may overshoot).
  final double progress;

  /// Expandable decoration (default gap when [topGap] is null).
  final M3EExpandableStyle style;

  /// Target expansion state; see the constructor.
  final bool? expanded;

  /// Override for the gap above the nested list; defaults to
  /// [M3EExpandableStyle.gap].
  final double? topGap;

  @override
  Widget build(BuildContext context) {
    // Keep [child] mounted at heightFactor 0 so nested selection / reorder
    // state survives collapse until the user resets it.
    final double t = progress.clamp(0.0, 1.2);
    final double heightFactor = progress <= 0 ? 0.0 : t.clamp(0.0, 1.0);
    final double gap = topGap ?? style.gap;
    final bool excludeFocus = expanded != null
        ? !expanded!
        : heightFactor < 1.0;
    final bool collapsed = heightFactor <= 0;

    return ExcludeFocus(
      excluding: excludeFocus,
      child: TickerMode(
        enabled: !collapsed,
        child: ClipRect(
          child: Align(
            alignment: Alignment.topCenter,
            heightFactor: heightFactor,
            child: Opacity(
              opacity: heightFactor,
              child: Transform.translate(
                offset: Offset(0, collapsed ? 0 : (1.0 - t) * 12),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    if (gap > 0) SizedBox(height: gap),
                    child,
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
