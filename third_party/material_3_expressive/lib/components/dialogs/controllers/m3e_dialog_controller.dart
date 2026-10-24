import 'package:flutter/foundation.dart';

import '../enums/m3e_dialog_enums.dart';
import '../models/m3e_dialog_discard_labels.dart';

/// Host that an [M3EDialogController] drives.
abstract class M3EDialogControllerClient {
  /// Pops the dialog with [result], skipping dismiss guards.
  void closeDialog([Object? result]);

  /// Runs the dismiss path (guards, discard confirm). True when it closed.
  Future<bool> requestDismiss();
}

/// Drives an open dialog programmatically.
///
/// Pass to `M3EDialog.show`, `showFullScreen` or `showAdaptive`. Set
/// [hasUnsavedChanges] so dismissing shows a discard confirmation.
class M3EDialogController extends ChangeNotifier {
  /// M3EDialogController.
  M3EDialogController({
    this._hasUnsavedChanges = false,
    this.discardLabels = const M3EDialogDiscardLabels(),
  });

  /// Copy for the discard confirmation.
  final M3EDialogDiscardLabels discardLabels;

  M3EDialogControllerClient? _client;
  bool _hasUnsavedChanges;
  M3EDialogVariant? _variant;
  bool _disposed = false;

  /// Whether a dialog is attached and showing.
  bool get isOpen => _client != null;

  /// Current layout of the open dialog, or null when closed.
  M3EDialogVariant? get variant => _variant;

  /// Whether dismissing must confirm discarding changes.
  bool get hasUnsavedChanges => _hasUnsavedChanges;

  set hasUnsavedChanges(bool value) {
    if (_hasUnsavedChanges == value) {
      return;
    }
    _hasUnsavedChanges = value;
    _notify();
  }

  /// Closes the dialog with [result], skipping guards.
  void close([Object? result]) => _client?.closeDialog(result);

  /// Dismisses through guards and discard confirmation.
  Future<bool> dismiss() async => await _client?.requestDismiss() ?? false;

  /// Binds [client]. Called by the dialog host.
  void attach(M3EDialogControllerClient client) {
    _client = client;
    _notify();
  }

  /// Unbinds [client]. Called by the dialog host.
  void detach(M3EDialogControllerClient client) {
    if (_client != client) {
      return;
    }
    _client = null;
    _variant = null;
    _notify();
  }

  /// Reports the layout in use. Called by the dialog host.
  void updateVariant(M3EDialogVariant? value) {
    if (_variant == value) {
      return;
    }
    _variant = value;
    _notify();
  }

  // The host can unmount after the owner disposed this controller (e.g. on
  // the route future), so late host updates are dropped.
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
