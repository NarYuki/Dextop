part of '../m3e_app_bars.dart';

/// Follows the page scrollable and ignores overlay scrollables.
class _M3EPageScroll {
  ScrollPosition? tracked;

  /// Whether [notification] comes from the page this bar should follow.
  bool accepts(ScrollNotification notification, BuildContext context) {
    final ScrollPosition? position = _matchingPosition(notification, context);
    if (position == null) {
      return false;
    }
    final ScrollController? primary = PrimaryScrollController.maybeOf(context);
    if (primary != null && primary.hasClients) {
      // Several scrollables can share the route's primary controller (see
      // [_m3eSinglePrimaryPosition]); any of them is the page.
      return primary.positions.contains(position);
    }
    return _acceptsTracked(position, notification);
  }

  /// The scroll position [notification] reports, when it is one this bar can
  /// follow at all (right axis, same route, still mounted).
  ScrollPosition? _matchingPosition(
    ScrollNotification notification,
    BuildContext context,
  ) {
    if (!defaultScrollNotificationPredicate(notification) ||
        notification.metrics.axis != Axis.vertical) {
      return null;
    }
    final BuildContext? target = notification.context;
    if (target == null || !target.mounted) {
      return null;
    }
    final ScrollPosition? position = Scrollable.maybeOf(target)?.position;
    if (position == null) {
      return null;
    }
    final ModalRoute<Object?>? barRoute = ModalRoute.of(context);
    final ModalRoute<Object?>? targetRoute = ModalRoute.of(target);
    if (barRoute != null &&
        targetRoute != null &&
        !identical(barRoute, targetRoute)) {
      return null;
    }
    return position;
  }

  /// Desktop lists do not attach to the route's primary controller.
  /// An idle scrollable must not clear a bar that is already scrolled under.
  /// A replaced position (refresh rebuilds the scrollable) is adopted so the
  /// bar keeps following the list, including when that list is back at rest.
  bool _acceptsTracked(
    ScrollPosition position,
    ScrollNotification notification,
  ) {
    final replacing = tracked != null;
    if (!_scrollPositionAlive(tracked)) {
      tracked = null;
    }
    if (tracked != null) {
      return identical(position, tracked);
    }
    if (notification.metrics.extentBefore > 0 || replacing) {
      tracked = position;
      return true;
    }
    return false;
  }
}

/// The route's [PrimaryScrollController] position, when exactly one
/// scrollable is attached to it.
///
/// On Android and iOS every primary vertical scrollable on a route attaches
/// to that controller automatically, so it can hold several positions at
/// once, and [ScrollController.position] throws then. With none or several
/// there is no single page position to read, so this returns null.
ScrollPosition? _m3eSinglePrimaryPosition(BuildContext context) {
  final ScrollController? primary = PrimaryScrollController.maybeOf(context);
  if (primary == null || primary.positions.length != 1) {
    return null;
  }
  return primary.positions.single;
}

bool _scrollPositionAlive(ScrollPosition? position) {
  if (position == null || !position.hasPixels) {
    return false;
  }
  final ScrollContext scrollContext = position.context;
  if (scrollContext is ScrollableState &&
      (!scrollContext.mounted ||
          !identical(scrollContext.position, position))) {
    return false;
  }
  final BuildContext? notificationContext = scrollContext.notificationContext;
  return notificationContext != null && notificationContext.mounted;
}
