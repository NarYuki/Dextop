import 'package:flutter/widgets.dart';

/// Marks a dismissible row and lets it reveal its swipe actions without a
/// drag.
class M3EListSwipeOverflow extends InheritedWidget {
  /// Creates a swipe-overflow scope.
  const M3EListSwipeOverflow({this.onReveal, required super.child, super.key});

  /// Opens the action preview. Null when the row has no swipe actions.
  final VoidCallback? onReveal;

  /// Nearest scope, or null when the row is not a dismissible row.
  static M3EListSwipeOverflow? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<M3EListSwipeOverflow>();
  }

  @override
  bool updateShouldNotify(M3EListSwipeOverflow oldWidget) {
    return onReveal != oldWidget.onReveal;
  }
}
