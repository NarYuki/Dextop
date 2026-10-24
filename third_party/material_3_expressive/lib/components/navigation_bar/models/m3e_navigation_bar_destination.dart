import 'package:material_ui/material_ui.dart';

import '../components/m3e_nav_badge_view.dart';

/// A single destination in the M3E navigation bar.
///
/// Provide at least one of [icon] or a non-empty [label].
class M3ENavigationBarDestination {
  /// M3ENavigationBarDestination.
  const M3ENavigationBarDestination({
    this.icon,
    this.label,
    this.selectedIcon,
    this.badgeCount,
    this.badgeDot = false,
    this.semanticLabel,
  }) : assert(
         icon != null || (label != null && label.length > 0),
         'Provide an icon and/or a non-empty label',
       );

  /// icon.
  final Widget? icon;

  /// selectedIcon.
  final Widget? selectedIcon;

  /// label.
  final String? label;

  /// Optional badge counter.
  final int? badgeCount;

  /// If true, show a small dot instead of a counter.
  final bool badgeDot;

  /// semanticLabel.
  final String? semanticLabel;

  /// Resolved semantics string for the destination.
  String get resolvedSemanticLabel {
    if (semanticLabel != null && semanticLabel!.isNotEmpty) {
      return semanticLabel!;
    }
    if (label != null && label!.isNotEmpty) {
      return label!;
    }
    return 'Destination';
  }

  /// Whether this destination has a usable icon widget.
  bool get hasIcon => icon != null;

  /// Whether this destination has a non-empty label.
  bool get hasLabel => label != null && label!.isNotEmpty;

  /// buildIcon.
  Widget buildIcon({bool selected = false}) {
    assert(hasIcon, 'Destination has no icon');
    final base = selected && selectedIcon != null ? selectedIcon! : icon!;
    if (badgeCount != null || badgeDot) {
      return M3ENavBadge(count: badgeCount, showDot: badgeDot, child: base);
    }
    return base;
  }
}
