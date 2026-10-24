import 'package:flutter/widgets.dart';

import '../../cards/enums/m3e_card_variant.dart';

/// Nesting hints for a card list under an expanded expandable row.
///
/// When [closeBottom] is true (parent is last / single), the nested list's
/// last row should use outer bottom radii so the group visually closes.
class M3EExpandableNestScope extends InheritedWidget {
  /// Creates a nest scope for an expandable sublist.
  const M3EExpandableNestScope({
    required this.closeBottom,
    required this.outerRadius,
    required super.child,
    this.surfaceColor,
    this.variant,
    this.rowIndex,
    super.key,
  });

  /// Whether the nested list should close the group with outer bottom radii.
  final bool closeBottom;

  /// Outer corner radius to match the expandable parent.
  final double outerRadius;

  /// Parent row fill. Nested rows use this when they do not set their own.
  final Color? surfaceColor;

  /// Parent list variant. A nested list uses this when it does not set one.
  final M3ECardVariant? variant;

  /// Expandable row that owns this nested list, when keyboard focus should
  /// walk from that header into the sublist.
  final int? rowIndex;

  /// Nearest nest scope, if any.
  static M3EExpandableNestScope? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<M3EExpandableNestScope>();
  }

  @override
  bool updateShouldNotify(M3EExpandableNestScope oldWidget) {
    return closeBottom != oldWidget.closeBottom ||
        outerRadius != oldWidget.outerRadius ||
        surfaceColor != oldWidget.surfaceColor ||
        variant != oldWidget.variant ||
        rowIndex != oldWidget.rowIndex;
  }
}
