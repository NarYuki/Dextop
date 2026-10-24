import 'package:flutter/widgets.dart';

/// Drives reveal and dismiss on a dismissible list without a pointer drag.
class M3EDismissibleListController {
  void Function(int index, {required bool leading})? _reveal;
  void Function(int index, {required bool leading})? _dismiss;
  VoidCallback? _close;

  /// Whether a list is currently bound.
  bool get isAttached => _reveal != null;

  /// Binds the owning list. Replaces any previous binding.
  void attach({
    required void Function(int index, {required bool leading}) reveal,
    required void Function(int index, {required bool leading}) dismiss,
    required VoidCallback close,
  }) {
    _reveal = reveal;
    _dismiss = dismiss;
    _close = close;
  }

  /// Releases the binding when [reveal] is still the active one.
  void detach(void Function(int index, {required bool leading}) reveal) {
    if (!identical(_reveal, reveal)) {
      return;
    }
    _reveal = null;
    _dismiss = null;
    _close = null;
  }

  /// Opens the action preview for [index].
  ///
  /// [leading] true reveals the start-side actions (a rightward swipe in LTR).
  void reveal(int index, {bool leading = true}) {
    _reveal?.call(index, leading: leading);
  }

  /// Dismisses [index].
  ///
  /// [leading] true dismisses toward the end (a rightward swipe in LTR).
  void dismiss(int index, {bool leading = true}) {
    _dismiss?.call(index, leading: leading);
  }

  /// Springs an open action preview closed.
  void close() => _close?.call();
}
