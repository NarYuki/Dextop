// Delegates to [M3EBadge] for the shared badge implementation.

import 'package:flutter/widgets.dart';

import 'package:material_3_expressive/components/badges/m3e_badges.dart';

/// Navigation-bar badge wrapper around [M3EBadge].
///
/// Uses theme Compose placement on the icon (do not force [offset] to zero).
class M3ENavBadge extends StatelessWidget {
  /// M3ENavBadge.
  const M3ENavBadge({
    super.key,
    required this.child,
    this.count,
    this.showDot = false,
    this.maxCount = 999,
    this.backgroundColor,
    this.foregroundColor,
    this.semanticLabel,
    this.offset,
  }) : assert(
         count == null || count >= 0,
         'count must be null or non-negative',
       );

  /// child.
  final Widget child;

  /// count.
  final int? count;

  /// showDot.
  final bool showDot;

  /// maxCount.
  final int maxCount;

  /// backgroundColor.
  final Color? backgroundColor;

  /// foregroundColor.
  final Color? foregroundColor;

  /// semanticLabel.
  final String? semanticLabel;

  /// Optional placement override. When null, [M3EBadge] uses theme
  /// `smallOffset` / `largeOffset` so the badge sits on the icon.
  final Offset? offset;

  @override
  Widget build(BuildContext context) {
    return M3EBadge(
      count: count,
      showDot: showDot,
      maxCount: maxCount,
      offset: offset,
      backgroundColor: backgroundColor,
      foregroundColor: foregroundColor,
      semanticLabel: semanticLabel,
      child: child,
    );
  }
}
