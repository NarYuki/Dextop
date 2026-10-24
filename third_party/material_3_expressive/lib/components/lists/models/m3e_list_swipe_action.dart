import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// A swipe action revealed beside a dismissible list row.
class M3EListSwipeAction {
  /// Creates a swipe action.
  const M3EListSwipeAction({
    required this.icon,
    this.backgroundColor,
    this.foregroundColor,
    this.onPressed,
    this.isPrimary = false,
    this.width = 52,
    this.height,
    this.haptic = M3EHapticFeedback.medium,
  });

  /// Icon for the action.
  final Widget icon;

  /// Optional background override.
  final Color? backgroundColor;

  /// Optional foreground override.
  final Color? foregroundColor;

  /// Called when the action is invoked.
  final VoidCallback? onPressed;

  /// Primary end-aligned action for auto-execute on full dismiss.
  final bool isPrimary;

  /// Preferred cell width used for snap / preview extent (not a visual max).
  final double width;

  /// Optional fixed height; otherwise fills the list row reveal height.
  final double? height;

  /// Haptic level when the action is pressed.
  final M3EHapticFeedback haptic;
}
