import 'package:flutter/widgets.dart';

/// Whether an ancestor reorder host currently has an active drag.
///
/// [active] is updated synchronously when a drag begins/ends so gesture
/// callbacks can gate before the next frame.
class M3EListReorderSessionScope extends InheritedWidget {
  /// Creates a reorder session scope.
  const M3EListReorderSessionScope({
    required this.active,
    required super.child,
    super.key,
  });

  /// True while a reorder drag is in progress.
  final ValueNotifier<bool> active;

  /// Nearest session (registers a dependency). Prefer [isActive] in callbacks.
  static M3EListReorderSessionScope? maybeOf(BuildContext context) {
    return context
        .dependOnInheritedWidgetOfExactType<M3EListReorderSessionScope>();
  }

  /// Whether a reorder drag is active above [context] (safe outside build).
  static bool isActive(BuildContext context) {
    final M3EListReorderSessionScope? scope = context
        .getInheritedWidgetOfExactType<M3EListReorderSessionScope>();
    return scope?.active.value ?? false;
  }

  @override
  bool updateShouldNotify(M3EListReorderSessionScope oldWidget) =>
      active != oldWidget.active;
}
