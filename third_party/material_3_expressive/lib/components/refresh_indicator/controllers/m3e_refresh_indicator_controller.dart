import 'package:flutter/foundation.dart';

/// Manually shows a refresh indicator, like Material
/// `RefreshIndicatorState.show` via a `GlobalKey`.
///
/// Pass into `M3ERefreshIndicator.controller`, then call [show].
class M3ERefreshIndicatorController extends ChangeNotifier {
  Future<void> Function({bool atTop})? _show;

  /// Whether this controller is attached to a refresh indicator.
  bool get isAttached => _show != null;

  /// Shows the indicator and runs `onRefresh`.
  ///
  /// Returns the same future as a successful pull-to-refresh.
  Future<void> show({bool atTop = true}) {
    final Future<void> Function({bool atTop})? show = _show;
    if (show == null) {
      return Future<void>.value();
    }
    return show(atTop: atTop);
  }

  /// Called by the refresh indicator when it mounts.
  // ignore: use_setters_to_change_properties
  void attach(Future<void> Function({bool atTop}) show) {
    _show = show;
  }

  /// Called by the refresh indicator when it unmounts or the controller changes.
  ///
  /// Pass the **same** function instance used in [attach] (a stored closure,
  /// not a fresh method tear-off — those are never [identical]). Deferred
  /// State.dispose after a keyed rebuild must not clear a newer attachment.
  void detach(Future<void> Function({bool atTop}) show) {
    if (identical(_show, show)) {
      _show = null;
    }
  }
}
