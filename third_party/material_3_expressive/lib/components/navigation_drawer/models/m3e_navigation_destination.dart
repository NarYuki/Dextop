import 'package:flutter/widgets.dart';

/// A destination shown in a navigation drawer.
@immutable
class M3ENavigationDestination {
  /// M3ENavigationDestination.
  const M3ENavigationDestination({
    required this.label,
    this.icon,
    this.selectedIcon,
    this.badgeLabel,
    this.showBadge = false,
    this.semanticLabel,
  });

  /// Icon shown when the destination is not selected.
  ///
  /// Omit it on every destination, or provide it on every destination.
  final Widget? icon;

  /// Optional icon shown when the destination is selected.
  final Widget? selectedIcon;

  /// Visible label. Kept to one line.
  final String label;

  /// Optional text for a trailing count, such as `24` or `100+`.
  final String? badgeLabel;

  /// Whether to show a small dot on the icon when [badgeLabel] is null.
  final bool showBadge;

  /// Accessibility name when [label] is ambiguous. Defaults to [label].
  final String? semanticLabel;
}
