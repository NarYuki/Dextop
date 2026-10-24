import 'package:flutter/widgets.dart';

/// One keyboard-focusable row registered with an open menu.
class M3EMenuKeyTarget {
  /// Creates a focus target for a menu row.
  M3EMenuKeyTarget({
    required this.node,
    required this.label,
    required this.enabled,
    this.onOpenSubmenu,
  });

  /// Focus node owned by the row.
  final FocusNode node;

  /// Label used for typeahead.
  String label;

  /// Whether arrow keys may land on this row.
  bool enabled;

  /// Opens a cascading submenu when the row has one.
  VoidCallback? onOpenSubmenu;
}
