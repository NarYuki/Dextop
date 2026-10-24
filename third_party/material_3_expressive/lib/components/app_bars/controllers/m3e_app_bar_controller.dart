import 'dart:async';

import 'package:flutter/foundation.dart';

import '../enums/m3e_app_bar_enums.dart';

/// Drives an app bar: expand, collapse, show, hide, and follow the scroll.
///
/// Attach by passing the controller to `M3EAppBar.top`, `M3EAppBar.search`,
/// or `M3EAppBar.sliver`. [isCollapsed] and [isVisible] update as the bar
/// scrolls or the methods run.
class M3EAppBarController extends ChangeNotifier {
  bool _collapsed = false;
  bool _visible = true;
  Future<void> Function()? _expand;
  Future<void> Function()? _collapse;
  Future<void> Function()? _show;
  Future<void> Function()? _hide;
  Future<void> Function()? _followScroll;

  /// Whether the sliver has collapsed to the small content height.
  bool get isCollapsed => _collapsed;

  /// Whether the bar is showing. False after [hide].
  bool get isVisible => _visible;

  /// Scrolls the attached sliver back to the top so the bar expands.
  Future<void> expand() => _expand?.call() ?? Future<void>.value();

  /// Scrolls until the attached sliver is at its small content height.
  Future<void> collapse() => _collapse?.call() ?? Future<void>.value();

  /// Brings a hidden bar back.
  Future<void> show() => _show?.call() ?? Future<void>.value();

  /// Moves the bar off the content band. The system inset stays.
  ///
  /// With [M3EAppBarHideMode.actions], the action row stays and the rest
  /// slides away. [show] brings that content back. [followScroll] lets the
  /// selected hide mode track the scroll again.
  Future<void> hide() => _hide?.call() ?? Future<void>.value();

  /// Clears a manual [show] or [hide] so the bar follows its hide mode.
  Future<void> followScroll() => _followScroll?.call() ?? Future<void>.value();

  /// Binds the sliver that this controller drives.
  void attach({
    required bool collapsed,
    required bool visible,
    required Future<void> Function() expand,
    required Future<void> Function() collapse,
    required Future<void> Function() show,
    required Future<void> Function() hide,
    Future<void> Function()? followScroll,
  }) {
    _expand = expand;
    _collapse = collapse;
    _show = show;
    _hide = hide;
    _followScroll = followScroll;
    _collapsed = collapsed;
    _visible = visible;
    scheduleMicrotask(() {
      if (_expand != null) {
        notifyListeners();
      }
    });
  }

  /// Drops the bound sliver.
  void detach() {
    _expand = null;
    _collapse = null;
    _show = null;
    _hide = null;
    _followScroll = null;
  }

  /// Publishes collapsed and visible state from the bound sliver.
  void update({required bool collapsed, required bool visible}) {
    if (_collapsed == collapsed && _visible == visible) {
      return;
    }
    _collapsed = collapsed;
    _visible = visible;
    notifyListeners();
  }
}
