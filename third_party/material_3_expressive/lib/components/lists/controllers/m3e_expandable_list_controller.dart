import 'package:flutter/widgets.dart';

/// Opens and closes an expandable row's container transform.
class M3EExpandableListController {
  void Function(int index)? _open;
  VoidCallback? _close;

  /// Attaches the owning list. Replaced when the list rebuilds its binding.
  void attach({
    required void Function(int index) open,
    required VoidCallback close,
  }) {
    _open = open;
    _close = close;
  }

  /// Drops the owning list.
  void detach() {
    _open = null;
    _close = null;
  }

  /// Morphs [index] to its transform destination.
  void open(int index) => _open?.call(index);

  /// Reverses the open transform.
  void close() => _close?.call();
}
