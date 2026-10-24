import 'package:flutter/widgets.dart';

/// A single tab within an `M3ETabs` bar.
@immutable
class M3ETab {
  /// Creates a tab with a label, an icon, or both.
  const M3ETab({
    this.label,
    this.icon,
    this.semanticLabel,
    this.badgeCount,
    this.badgeLabel,
    this.badgeDot = false,
    this.badgeInline = false,
  }) : assert(label != null || icon != null, 'A tab needs a label or icon.'),
       assert(
         badgeCount == null || badgeCount >= 0,
         'badgeCount must be non-negative',
       );

  /// Visible label. One line, wrapping to a second line when needed.
  final String? label;

  /// Optional icon. Primary tabs stack it above [label]. Secondary tabs
  /// place it before the label.
  final Widget? icon;

  /// Spoken label when [label] is absent or not enough on its own.
  final String? semanticLabel;

  /// Large badge count. Ignored when [badgeDot] or [badgeLabel] is set.
  final int? badgeCount;

  /// Large badge text. Kept to four characters when drawn.
  final String? badgeLabel;

  /// Small dot badge. Wins over [badgeLabel] and [badgeCount].
  final bool badgeDot;

  /// When true, the badge sits 4dp after the label. Otherwise a primary tab
  /// overlaps the icon. Secondary tabs always keep the badge after the label.
  final bool badgeInline;

  /// Whether this tab shows a badge.
  bool get hasBadge =>
      badgeDot || badgeCount != null || (badgeLabel?.isNotEmpty ?? false);
}
