import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_list_reorder_state.dart';

/// Destination slot fill shown while a list row is being reordered.
///
/// Must be a direct child of a [Stack]. Its vertical position follows [top],
/// so the fill spring-tracks the gap neighbors open for the dragged row.
class M3EListReorderPlaceholder extends StatelessWidget {
  /// Creates a reorder placeholder.
  const M3EListReorderPlaceholder({
    required this.top,
    required this.left,
    required this.size,
    required this.opacity,
    required this.reorderState,
    super.key,
  });

  /// Animated top edge within the host stack.
  final SingleMotionController top;

  /// Left edge within the host stack.
  final double left;

  /// Slot size (the dragged row's measured size).
  final Size size;

  /// Fill opacity (follows the lift progress).
  final double opacity;

  /// Supplies placeholder color, border, and radius.
  final M3EListReorderState reorderState;

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final BorderSide? border = reorderState.placeholderBorder;

    return AnimatedBuilder(
      animation: top,
      builder: (BuildContext context, Widget? child) {
        return Positioned(
          left: left,
          top: top.value,
          width: size.width,
          height: size.height,
          child: child!,
        );
      },
      child: IgnorePointer(
        child: Opacity(
          opacity: opacity,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: reorderState.resolvedPlaceholderColor(theme.colorScheme),
              border: border == null ? null : Border.fromBorderSide(border),
              borderRadius: BorderRadius.circular(
                reorderState.resolvedPlaceholderRadius(theme.listTheme),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
