import 'package:flutter/widgets.dart';

/// Lets a list row publish an optional container-transform destination.
///
/// The row surface owns the callback. A descendant item calls [publish] with
/// its destination, or null when the row should not morph.
class M3EListTransformScope extends InheritedWidget {
  /// Creates a transform scope.
  const M3EListTransformScope({
    required this.publish,
    required super.child,
    super.key,
  });

  /// Stores the destination for the next row activation.
  final void Function(Widget? destination) publish;

  /// Nearest scope, or null when the row has no surface.
  static M3EListTransformScope? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<M3EListTransformScope>();
  }

  @override
  bool updateShouldNotify(M3EListTransformScope oldWidget) =>
      publish != oldWidget.publish;
}
