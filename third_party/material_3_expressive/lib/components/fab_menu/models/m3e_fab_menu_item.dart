import 'package:flutter/widgets.dart';

/// A single action shown when an `M3EFabMenu` is open.
@immutable
class M3EFabMenuItem {
  /// Creates a FAB menu item.
  const M3EFabMenuItem({
    required this.icon,
    required this.label,
    this.onPressed,
    this.openBuilder,
    this.openColor,
    this.transformScrimColor,
  });

  /// Leading icon (decorative for semantics).
  final Widget icon;

  /// Visible label; also used as the button semantic label.
  final String label;

  /// Tap callback. Invoked before the menu closes when [openBuilder] is null.
  final VoidCallback? onPressed;

  /// When set, tap opens a container transform to this destination.
  final WidgetBuilder? openBuilder;

  /// Destination fill color for the container transform.
  final Color? openColor;

  /// Scrim color behind the open container transform.
  final Color? transformScrimColor;
}
