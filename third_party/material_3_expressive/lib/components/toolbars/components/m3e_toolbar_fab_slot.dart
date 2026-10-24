// Adjacent FAB slot — size morphs with FAB-driven toolbar expand progress.

import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../floating_action_buttons/m3e_floating_action_buttons.dart';
import '../res/m3e_toolbar_tokens.dart';

/// Hosts an adjacent [M3EFab], optionally sized for expand morph.
class M3EToolbarFabSlot extends StatelessWidget {
  /// M3EToolbarFabSlot.
  const M3EToolbarFabSlot({
    this.fab,
    this.icon,
    this.onPressed,
    this.color = M3EFabColor.secondary,
    this.containerSize,
    this.iconSize = M3EToolbarTokens.fabExpandedIcon,
    this.cornerRadius,
    super.key,
  });

  /// Custom FAB widget. When null, builds a default [M3EFab] from [icon].
  final Widget? fab;

  /// icon.
  final Widget? icon;

  /// onPressed.
  final VoidCallback? onPressed;

  /// color.
  final M3EFabColor color;

  /// When set, sizes the default FAB to this square (80 collapsed → 56
  /// expanded). Ignored when [fab] is provided (parent tight-lays out child).
  final double? containerSize;

  /// Icon size inside the default FAB. 28 collapsed, 24 expanded.
  final double iconSize;

  /// Corner radius of the default FAB. Falls back to the FAB theme.
  final double? cornerRadius;

  @override
  Widget build(BuildContext context) {
    if (fab != null) {
      final double? size = containerSize;
      if (size == null) {
        return fab!;
      }
      return SizedBox(
        width: size,
        height: size,
        child: FittedBox(child: fab),
      );
    }

    final double size = containerSize ?? M3EToolbarTokens.fabBaseline;
    final M3EThemeData theme = M3ETheme.of(context);
    final Widget button = M3ETheme(
      data: theme.copyWith(
        fabTheme: theme.fabTheme.copyWith(
          regularContainer: size,
          regularIconSize: iconSize,
          regularRadius: cornerRadius ?? theme.fabTheme.regularRadius,
        ),
      ),
      child: M3EFab(
        icon: icon ?? const SizedBox.shrink(),
        onPressed: onPressed,
        color: color,
        size: M3EFabSize.regular,
      ),
    );
    return SizedBox(width: size, height: size, child: button);
  }
}
