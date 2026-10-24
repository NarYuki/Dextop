import 'package:flutter/widgets.dart';

import '../enums/m3e_list_swipe_edge.dart';
import '../enums/m3e_list_swipe_mode.dart';
import 'm3e_list_swipe_action.dart';

/// Swipe configuration for one list item.
///
/// Null on the item means that row does not swipe. When set, the row can
/// reveal actions, dismiss, or both, using the same gesture rules as before.
@immutable
class M3EListItemSwipe {
  /// Creates a per-item swipe configuration.
  const M3EListItemSwipe({
    this.leading = const <M3EListSwipeAction>[],
    this.trailing = const <M3EListSwipeAction>[],
    this.mode = M3EListSwipeMode.both,
    this.edge = M3EListSwipeEdge.both,
    this.onDismiss,
    this.background,
    this.secondaryBackground,
  });

  /// Start-side actions (revealed by a rightward swipe in LTR).
  final List<M3EListSwipeAction> leading;

  /// End-side actions (revealed by a leftward swipe in LTR).
  final List<M3EListSwipeAction> trailing;

  /// Reveal only, dismiss only, or reveal then dismiss.
  final M3EListSwipeMode mode;

  /// Which manual swipe direction can dismiss.
  final M3EListSwipeEdge edge;

  /// Called when this row is dismissed. Return false to keep the row.
  final Future<bool> Function(DismissDirection direction)? onDismiss;

  /// Background revealed when swiping start-to-end.
  final Widget? background;

  /// Background revealed when swiping end-to-start.
  ///
  /// Falls back to [background] when null.
  final Widget? secondaryBackground;
}
