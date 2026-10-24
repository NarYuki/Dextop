import 'package:flutter/widgets.dart';

import 'm3e_list_transform_scope.dart';

/// Publishes [destination] to the enclosing list row surface.
class M3EListTransformPublisher extends StatelessWidget {
  /// Creates a publisher.
  const M3EListTransformPublisher({
    required this.child,
    this.destination,
    super.key,
  });

  /// Full-screen surface. Null leaves the row's current tap behavior.
  final Widget? destination;

  /// Row content.
  final Widget child;

  @override
  Widget build(BuildContext context) {
    M3EListTransformScope.maybeOf(context)?.publish(destination);
    return child;
  }
}
