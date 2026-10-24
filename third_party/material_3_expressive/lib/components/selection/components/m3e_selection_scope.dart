import 'package:flutter/widgets.dart';

import '../controllers/m3e_selection_controller.dart';

/// Provides the active [M3ESelectionController] to descendant selection widgets.
class M3ESelectionScope extends InheritedWidget {
  /// Creates a selection scope.
  const M3ESelectionScope({
    required this.controller,
    required this.itemCount,
    required super.child,
    super.key,
  });

  /// Shared selection controller.
  final M3ESelectionController controller;

  /// Item count from the wired selection list (for select-all).
  final int itemCount;

  /// The nearest scope, or null.
  static M3ESelectionScope? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<M3ESelectionScope>();
  }

  /// The nearest scope; throws if missing.
  static M3ESelectionScope of(BuildContext context) {
    final scope = maybeOf(context);
    assert(
      scope != null,
      'M3ESelectionScope.of() called with no M3ESelectionScope ancestor.',
    );
    return scope!;
  }

  @override
  bool updateShouldNotify(M3ESelectionScope oldWidget) {
    return controller != oldWidget.controller ||
        itemCount != oldWidget.itemCount;
  }
}
