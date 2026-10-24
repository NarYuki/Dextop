import 'package:flutter/foundation.dart';

/// Host that an [M3ESideSheetController] drives.
abstract class M3ESideSheetControllerClient {
  /// Whether the sheet is open (or opening).
  bool get isOpen;

  /// Opens the sheet.
  void open();

  /// Closes the sheet; a modal route pops with [result].
  void close([Object? result]);
}

/// Opens and closes a side sheet without user input.
///
/// Pass it to `M3ESideSheetLayout` or `M3ESideSheet.show`.
class M3ESideSheetController extends ChangeNotifier {
  /// M3ESideSheetController.
  M3ESideSheetController();

  M3ESideSheetControllerClient? _client;
  bool _isOpen = false;
  bool _disposed = false;

  /// Whether a sheet is attached.
  bool get isAttached => _client != null;

  /// Whether the attached sheet is open; false when detached.
  bool get isOpen => _isOpen;

  /// Opens the attached sheet.
  void open() => _client?.open();

  /// Closes the attached sheet; a modal route pops with [result].
  void close([Object? result]) => _client?.close(result);

  /// Opens a closed sheet and closes an open one.
  void toggle() => _isOpen ? close() : open();

  /// Binds [client]. Called by the sheet host.
  void attach(M3ESideSheetControllerClient client) {
    _client = client;
    _isOpen = client.isOpen;
    _notify();
  }

  /// Unbinds [client]. Called by the sheet host.
  void detach(M3ESideSheetControllerClient client) {
    if (_client != client) {
      return;
    }
    _client = null;
    _isOpen = false;
    _notify();
  }

  /// Reports whether the sheet is open. Called by the sheet host.
  void updateOpen({required bool isOpen}) {
    if (_isOpen == isOpen) {
      return;
    }
    _isOpen = isOpen;
    _notify();
  }

  // The host can unmount after the owner disposed this controller.
  void _notify() {
    if (!_disposed) {
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _client = null;
    super.dispose();
  }
}
