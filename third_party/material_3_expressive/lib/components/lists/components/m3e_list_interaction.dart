import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// Interaction snapshot for the current list row.
class M3EListInteractionScope extends InheritedWidget {
  /// Creates a row interaction scope.
  const M3EListInteractionScope({
    required this.state,
    required this.enabled,
    required this.selected,
    required super.child,
    super.key,
  });

  /// Hover, focus, press, and drag.
  final M3EInteractionState state;

  /// Whether the row accepts input.
  final bool enabled;

  /// Whether the row is selected.
  final bool selected;

  /// Nearest scope, or a resting snapshot.
  static M3EListInteractionScope? maybeOf(BuildContext context) {
    return context
        .dependOnInheritedWidgetOfExactType<M3EListInteractionScope>();
  }

  @override
  bool updateShouldNotify(M3EListInteractionScope oldWidget) {
    return state != oldWidget.state ||
        enabled != oldWidget.enabled ||
        selected != oldWidget.selected;
  }
}
