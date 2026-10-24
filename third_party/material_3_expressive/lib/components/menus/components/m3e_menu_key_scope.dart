import 'package:flutter/widgets.dart';

import 'm3e_menu_key_target.dart';

/// Shares the open menu's focus targets with each row.
class M3EMenuKeyScope extends InheritedWidget {
  /// Creates a scope over [targets].
  const M3EMenuKeyScope({
    required this.targets,
    required super.child,
    super.key,
  });

  /// Rows in visual order. Mutated as rows mount and unmount.
  final List<M3EMenuKeyTarget> targets;

  /// The nearest scope, or null when the row is not inside a menu popup.
  static M3EMenuKeyScope? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<M3EMenuKeyScope>();
  }

  @override
  bool updateShouldNotify(covariant M3EMenuKeyScope oldWidget) {
    return targets != oldWidget.targets;
  }
}
