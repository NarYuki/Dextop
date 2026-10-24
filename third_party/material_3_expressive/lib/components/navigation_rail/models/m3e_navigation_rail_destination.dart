import 'package:material_ui/material_ui.dart';

/// Model for a navigation destination. One class per file.
class M3ENavigationRailDestination {
  /// Creates a [M3ENavigationRailDestination].
  const M3ENavigationRailDestination({
    required this.icon,
    this.selectedIcon,
    required this.label,
    this.badgeCount,
    this.semanticLabel,
    this.short = false,
  });

  /// Icon shown for the destination.
  final Widget icon;

  /// Optional icon when selected; falls back to [icon].
  final Widget? selectedIcon;

  /// Text label for the destination.
  final String label;

  /// Optional badge count to show.
  final int? badgeCount;

  /// Optional semantic label for accessibility.
  final String? semanticLabel;

  /// If true, uses short item height (56dp) instead of 64dp.
  final bool short;
}
