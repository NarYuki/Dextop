import 'package:flutter/foundation.dart';

/// Drives programmatic open / close for an `M3EFabMenu`.
class M3EFabMenuController extends ChangeNotifier {
  /// Creates a FAB menu controller.
  M3EFabMenuController();

  M3EFabMenuControllerClient? _client;
  bool _open = false;

  /// Whether the menu is open.
  bool get isOpen => _open;

  /// Binds the owning menu state.
  // ignore: use_setters_to_change_properties -- attach/detach pair.
  void attachClient(M3EFabMenuControllerClient client) {
    _client = client;
  }

  /// Releases the owning menu state without disposing this controller.
  void detachClient(M3EFabMenuControllerClient client) {
    if (identical(_client, client)) {
      _client = null;
    }
  }

  /// Updates open state from the menu (does not drive animation).
  void updateOpen({required bool open}) {
    if (_open == open) {
      return;
    }
    _open = open;
    notifyListeners();
  }

  /// Opens the menu.
  void open() {
    if (_open) {
      return;
    }
    _client?.onOpenRequested();
  }

  /// Closes the menu.
  void close() {
    if (!_open) {
      return;
    }
    _client?.onCloseRequested();
  }

  /// Toggles open / closed.
  void toggle() {
    if (_open) {
      close();
    } else {
      open();
    }
  }

  @override
  void dispose() {
    _client = null;
    super.dispose();
  }
}

/// Host bridge implemented by FAB menu state. Not intended for app code.
abstract class M3EFabMenuControllerClient {
  /// Opens the menu.
  void onOpenRequested();

  /// Closes the menu.
  void onCloseRequested();
}
