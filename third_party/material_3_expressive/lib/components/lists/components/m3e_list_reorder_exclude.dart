import 'package:flutter/widgets.dart';

/// Marks a subtree where an ancestor reorder host's long-press must not
/// start a drag (e.g. an expandable nested sublist with its own gestures).
class M3EListReorderExclude extends StatelessWidget {
  /// Creates an exclude region for parent list reorder.
  const M3EListReorderExclude({required this.child, super.key});

  /// Subtree that owns its own pointer interaction.
  final Widget child;

  @override
  Widget build(BuildContext context) => child;
}
