import 'package:flutter/widgets.dart';

import 'm3e_selection_flip.dart';

/// Flips horizontally between [child] and [selectedChild] when [selected].
///
/// Thin wrapper over [M3ESelectionFlip] for leading-slot usage.
class M3ESelectionLeading extends StatelessWidget {
  /// Creates a selection leading flip.
  const M3ESelectionLeading({
    required this.selected,
    required this.selectedChild,
    required this.child,
    this.duration = const Duration(milliseconds: 220),
    this.onTap,
    super.key,
  });

  /// Whether the selected face is shown.
  final bool selected;

  /// Unselected leading widget.
  final Widget child;

  /// Selected leading widget.
  final Widget selectedChild;

  /// Flip duration.
  final Duration duration;

  /// Optional tap handler (leading only; does not compete with row body).
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return M3ESelectionFlip(
      selected: selected,
      selectedChild: selectedChild,
      duration: duration,
      onTap: onTap,
      child: child,
    );
  }
}
