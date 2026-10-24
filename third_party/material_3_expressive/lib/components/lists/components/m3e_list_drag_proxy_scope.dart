import 'package:flutter/widgets.dart';

/// Marks the floating reorder proxy so cards can drop outline and use drag fill.
class M3EListDragProxyScope extends InheritedWidget {
  /// Creates a drag-proxy appearance scope.
  const M3EListDragProxyScope({
    required this.color,
    required this.radius,
    required super.child,
    super.key,
  });

  /// Fill used while dragging.
  final Color color;

  /// Corner radius used while dragging.
  final double radius;

  /// Nearest scope, or null when not building the drag proxy.
  static M3EListDragProxyScope? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<M3EListDragProxyScope>();
  }

  @override
  bool updateShouldNotify(M3EListDragProxyScope oldWidget) {
    return color != oldWidget.color || radius != oldWidget.radius;
  }
}
