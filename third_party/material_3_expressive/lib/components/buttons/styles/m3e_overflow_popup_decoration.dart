import 'package:material_3_expressive/components/button_group/m3e_button_group.dart'
    show M3EButtonGroup;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3EButtonGroup;
import 'package:material_ui/material_ui.dart';

import 'm3e_button_motion.dart';

/// Styling configuration for the overflow popup menu in [M3EButtonGroup].
class M3EOverflowPopupDecoration {
  /// backgroundColor.
  final Color? backgroundColor;

  /// elevation.
  final double elevation;

  /// borderRadius.
  final BorderRadius? borderRadius;

  /// border.
  final BorderSide? border;

  /// minWidth.
  final double minWidth;

  /// maxWidth.
  final double maxWidth;

  /// maxHeight.
  final double maxHeight;

  /// padding.
  final EdgeInsetsGeometry padding;

  /// offset.
  final Offset offset;

  /// useCardList.
  final bool useCardList;

  /// outerRadius.
  final double outerRadius;

  /// innerRadius.
  final double innerRadius;

  /// selectedBorderRadius.
  final double? selectedBorderRadius;

  /// selectedBackgroundColor.
  final Color? selectedBackgroundColor;

  /// itemBackgroundColor.
  final Color? itemBackgroundColor;

  /// itemGap.
  final double itemGap;

  /// trailing.
  final Widget? trailing;

  /// itemPadding.
  final EdgeInsetsGeometry itemPadding;

  /// motion.
  final M3EButtonMotion motion;

  /// M3EOverflowPopupDecoration.

  const M3EOverflowPopupDecoration({
    this.backgroundColor,
    this.elevation = 10.0,
    this.borderRadius,
    this.border,
    this.minWidth = 220.0,
    this.maxWidth = 280.0,
    this.maxHeight = 320.0,
    this.padding = const EdgeInsets.all(8),
    this.offset = const Offset(0, 6),
    this.useCardList = true,
    this.outerRadius = 12.0,
    this.innerRadius = 4.0,
    this.selectedBorderRadius,
    this.selectedBackgroundColor,
    this.itemBackgroundColor,
    this.itemGap = 3.0,
    this.trailing,
    this.itemPadding = const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
    this.motion = M3EButtonMotion.standardOverflow,
  });
}
