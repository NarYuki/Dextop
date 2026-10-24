import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../../icon_buttons/m3e_icon_buttons.dart';
import '../models/m3e_list_swipe_action.dart';

/// Renders a [M3EListSwipeAction] with [M3EIconButton].
class M3EListSwipeActionButton extends StatelessWidget {
  /// Creates a swipe action button.
  const M3EListSwipeActionButton({
    required this.action,
    required this.onTriggered,
    required this.width,
    required this.height,
    this.minWidth = 40,
    super.key,
  });

  /// Action model.
  final M3EListSwipeAction action;

  /// Called after [M3EListSwipeAction.onPressed] (e.g. close preview).
  final VoidCallback onTriggered;

  /// Allocated cell width — fills the reveal slot.
  final double width;

  /// Visual height — typically the list row reveal height.
  final double height;

  /// Minimum visual width so the icon stays inside the pill while revealing.
  final double minWidth;

  @override
  Widget build(BuildContext context) {
    final double resolvedHeight = action.height ?? height;
    final double visualWidth = math.max(width, minWidth);
    final M3EIconButtonVariant variant = action.isPrimary
        ? M3EIconButtonVariant.filled
        : M3EIconButtonVariant.tonal;

    void handlePress() {
      M3EHaptics.trigger(action.haptic);
      action.onPressed?.call();
      onTriggered();
    }

    final Color? bg = action.backgroundColor;
    final Color? fg = action.foregroundColor;
    final M3EIconButtonDecoration? decoration = (bg == null && fg == null)
        ? null
        : M3EIconButtonDecoration(
            backgroundColor: bg == null
                ? null
                : WidgetStatePropertyAll<Color?>(bg),
            foregroundColor: fg == null
                ? null
                : WidgetStatePropertyAll<Color?>(fg),
          );

    return SizedBox(
      width: width,
      height: resolvedHeight,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(resolvedHeight / 2),
        child: OverflowBox(
          minWidth: visualWidth,
          maxWidth: visualWidth,
          minHeight: resolvedHeight,
          maxHeight: resolvedHeight,
          child: M3EIconButton(
            icon: action.icon,
            onPressed: handlePress,
            variant: variant,
            enableFeedback: false,
            visualSize: Size(visualWidth, resolvedHeight),
            decoration: decoration,
          ),
        ),
      ),
    );
  }
}
