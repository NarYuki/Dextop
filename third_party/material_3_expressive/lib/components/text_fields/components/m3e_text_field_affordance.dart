import 'package:flutter/widgets.dart';

import '../../icon_buttons/m3e_icon_buttons.dart';

/// A trailing icon slot of `M3ETextField`: error icon, clear or password
/// toggle.
///
/// With [onPressed] it is an icon button labelled by [semanticLabel]. Without
/// it, it is a non-actionable icon (such as the error icon) labelled by
/// [semanticLabel].
class M3ETextFieldAffordance extends StatelessWidget {
  /// Creates a trailing affordance.
  const M3ETextFieldAffordance({
    required this.icon,
    required this.color,
    required this.iconSize,
    required this.semanticLabel,
    this.onPressed,
    super.key,
  });

  /// Glyph.
  final IconData icon;

  /// Icon color.
  final Color color;

  /// Icon size.
  final double iconSize;

  /// Accessibility label.
  final String semanticLabel;

  /// Press handler; null for a non-actionable icon.
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    if (onPressed == null) {
      return Semantics(
        label: semanticLabel,
        child: ExcludeSemantics(
          child: Icon(icon, size: iconSize, color: color),
        ),
      );
    }
    return M3EIconButton(
      icon: Icon(icon, size: iconSize),
      onPressed: onPressed,
      semanticLabel: semanticLabel,
      variant: M3EIconButtonVariant.standard,
      decoration: M3EIconButtonDecoration(
        foregroundColor: WidgetStatePropertyAll<Color?>(color),
      ),
    );
  }
}
