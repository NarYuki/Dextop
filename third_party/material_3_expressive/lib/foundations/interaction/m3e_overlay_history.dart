import 'package:flutter/widgets.dart';

/// One local-history entry that closes an overlay before its route pops.
///
/// System back and [Navigator.maybePop] remove the latest entry first.
/// [release] drops the entry when the overlay closes another way, so the
/// next back is a normal route pop.
final class M3EOverlayHistory {
  M3EOverlayHistory._(this._entry);

  final LocalHistoryEntry _entry;
  bool _released = false;

  /// Registers [onBack] on the [ModalRoute] of [context].
  ///
  /// Returns null when [context] has no route.
  static M3EOverlayHistory? register(
    BuildContext context, {
    required VoidCallback onBack,
  }) {
    return registerRoute(ModalRoute.of(context), onBack: onBack);
  }

  /// Registers [onBack] on [route].
  ///
  /// Returns null when [route] is null.
  static M3EOverlayHistory? registerRoute(
    ModalRoute<dynamic>? route, {
    required VoidCallback onBack,
  }) {
    if (route == null) {
      return null;
    }
    late final M3EOverlayHistory history;
    final entry = LocalHistoryEntry(
      onRemove: () {
        if (history._released) {
          return;
        }
        history._released = true;
        onBack();
      },
    );
    history = M3EOverlayHistory._(entry);
    route.addLocalHistoryEntry(entry);
    return history;
  }

  /// Drops the entry without calling the back callback.
  void release() {
    if (_released) {
      return;
    }
    _released = true;
    _entry.remove();
  }
}
