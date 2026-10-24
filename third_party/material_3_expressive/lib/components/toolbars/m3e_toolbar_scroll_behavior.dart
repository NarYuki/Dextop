import 'package:flutter/widgets.dart';

import 'controllers/m3e_toolbar_visibility_controller.dart';
import 'enums/m3e_toolbar_enums.dart';

/// Integrates scroll notifications with a [M3EToolbarVisibilityController].
class M3EToolbarScrollBehavior {
  /// M3EToolbarScrollBehavior.
  const M3EToolbarScrollBehavior({
    this.exitDirection = M3EToolbarExitDirection.bottom,
    required this.controller,
    this.action = M3EToolbarScrollAction.hide,
  });

  /// Creates a behavior that exits (slides away) while scrolling (opt-in).
  factory M3EToolbarScrollBehavior.exitAlways({
    M3EToolbarExitDirection exitDirection = M3EToolbarExitDirection.bottom,
    M3EToolbarVisibilityController? controller,
  }) {
    return M3EToolbarScrollBehavior(
      exitDirection: exitDirection,
      controller: controller ?? M3EToolbarVisibilityController(),
    );
  }

  /// Creates a behavior that collapses to the adjacent FAB / expand-trigger
  /// action while scrolling (opt-in), instead of sliding the whole pill away.
  ///
  /// Requires the toolbar to have an adjacent FAB (with
  /// `M3EToolbar.fabExpandsToolbar` true) or an
  /// `M3EToolbarAction.isExpandTrigger` action — asserted in debug builds.
  factory M3EToolbarScrollBehavior.collapseAlways({
    M3EToolbarVisibilityController? controller,
  }) {
    return M3EToolbarScrollBehavior(
      controller: controller ?? M3EToolbarVisibilityController(),
      action: M3EToolbarScrollAction.collapse,
    );
  }

  /// Direction the toolbar slides when hiding. Unused when [action] is
  /// `M3EToolbarScrollAction.collapse`.
  final M3EToolbarExitDirection exitDirection;

  /// Shared visibility state (manual
  /// [M3EToolbarVisibilityController.show] /
  /// [M3EToolbarVisibilityController.hide] or scroll-driven).
  final M3EToolbarVisibilityController controller;

  /// Whether scrolling slides the pill away (`hide`, default) or collapses
  /// it to the adjacent FAB / expand-trigger action (`collapse`).
  ///
  /// The two are mutually exclusive by construction — pick one via
  /// [M3EToolbarScrollBehavior.exitAlways] or
  /// [M3EToolbarScrollBehavior.collapseAlways]. Pass the same behavior to the
  /// toolbar and its [M3EToolbarScrollWrapper]; a collapse request reaching a
  /// scroll-exit toolbar is asserted in debug builds.
  final M3EToolbarScrollAction action;
}

/// Listens to [ScrollNotification]s on [child] and updates [behavior].
///
/// Place this around the scrollable content, not around the toolbar itself.
/// The toolbar reads the same [M3EToolbarScrollBehavior.controller].
class M3EToolbarScrollWrapper extends StatefulWidget {
  /// M3EToolbarScrollWrapper.
  const M3EToolbarScrollWrapper({
    required this.behavior,
    required this.child,
    super.key,
  });

  /// behavior.
  final M3EToolbarScrollBehavior behavior;

  /// child.
  final Widget child;

  @override
  State<M3EToolbarScrollWrapper> createState() =>
      _M3EToolbarScrollWrapperState();
}

class _M3EToolbarScrollWrapperState extends State<M3EToolbarScrollWrapper> {
  bool _handleScrollNotification(ScrollNotification notification) {
    if (notification is ScrollUpdateNotification) {
      _handleScrollUpdate(notification);
    }
    return false;
  }

  /// Triggers the instant the scroll delta changes direction, at any scroll
  /// position and at any drag speed:
  /// [M3EToolbarScrollAction.hide] springs the pill fully open/closed
  /// ([M3EToolbarVisibilityController.show] / [M3EToolbarVisibilityController.hide]
  /// are no-ops when already at or heading to that target, so repeated
  /// deltas in the same direction don't restart the spring);
  /// [M3EToolbarScrollAction.collapse] flips the collapse-request flag
  /// instead, which the toolbar mirrors onto its own expand state.
  void _handleScrollUpdate(ScrollUpdateNotification notification) {
    final double? delta = notification.scrollDelta;
    if (delta == null || delta == 0) {
      return;
    }
    final M3EToolbarVisibilityController controller =
        widget.behavior.controller;
    final bool hiding = delta > 0;
    switch (widget.behavior.action) {
      case M3EToolbarScrollAction.hide:
        if (hiding) {
          controller.hide();
        } else {
          controller.show();
        }
      case M3EToolbarScrollAction.collapse:
        if (hiding) {
          controller.requestCollapse();
        } else {
          controller.requestExpand();
        }
    }
  }

  @override
  Widget build(BuildContext context) {
    return NotificationListener<ScrollNotification>(
      onNotification: _handleScrollNotification,
      child: widget.child,
    );
  }
}
