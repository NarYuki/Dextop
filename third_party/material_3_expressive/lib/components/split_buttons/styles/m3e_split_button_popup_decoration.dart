import 'package:material_ui/material_ui.dart';

import '../../buttons/styles/m3e_button_motion.dart';

/// Styling options for split-button popup menus.
@immutable
class M3ESplitButtonPopupDecoration {
  /// M3ESplitButtonPopupDecoration.
  const M3ESplitButtonPopupDecoration({
    this.backgroundColor,
    this.elevation,
    this.borderRadius,
    this.border,
    this.offset = const Offset(0, 4),
    this.minWidth = 120,
    this.maxWidth = 280,
    this.maxHeight = 400,
    this.padding,
    this.motion = M3EButtonMotion.standardPopup,
    this.selectedColor,
    this.selectedBorderRadius,
  });

  /// backgroundColor.

  final Color? backgroundColor;

  /// elevation.
  final double? elevation;

  /// borderRadius.
  final BorderRadius? borderRadius;

  /// border.
  final Border? border;

  /// offset.
  final Offset offset;

  /// minWidth.
  final double minWidth;

  /// maxWidth.
  final double maxWidth;

  /// maxHeight.
  final double maxHeight;

  /// padding.
  final EdgeInsetsGeometry? padding;

  /// motion.
  final M3EButtonMotion motion;

  /// selectedColor.
  final Color? selectedColor;

  /// selectedBorderRadius.
  final BorderRadius? selectedBorderRadius;

  /// copyWith.

  M3ESplitButtonPopupDecoration copyWith({
    Color? backgroundColor,
    double? elevation,
    BorderRadius? borderRadius,
    Border? border,
    Offset? offset,
    double? minWidth,
    double? maxWidth,
    double? maxHeight,
    EdgeInsetsGeometry? padding,
    M3EButtonMotion? motion,
    Color? selectedColor,
    BorderRadius? selectedBorderRadius,
  }) {
    return M3ESplitButtonPopupDecoration(
      backgroundColor: backgroundColor ?? this.backgroundColor,
      elevation: elevation ?? this.elevation,
      borderRadius: borderRadius ?? this.borderRadius,
      border: border ?? this.border,
      offset: offset ?? this.offset,
      minWidth: minWidth ?? this.minWidth,
      maxWidth: maxWidth ?? this.maxWidth,
      maxHeight: maxHeight ?? this.maxHeight,
      padding: padding ?? this.padding,
      motion: motion ?? this.motion,
      selectedColor: selectedColor ?? this.selectedColor,
      selectedBorderRadius: selectedBorderRadius ?? this.selectedBorderRadius,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is M3ESplitButtonPopupDecoration &&
          backgroundColor == other.backgroundColor &&
          elevation == other.elevation &&
          borderRadius == other.borderRadius &&
          border == other.border &&
          offset == other.offset &&
          minWidth == other.minWidth &&
          maxWidth == other.maxWidth &&
          maxHeight == other.maxHeight &&
          padding == other.padding &&
          motion == other.motion &&
          selectedColor == other.selectedColor &&
          selectedBorderRadius == other.selectedBorderRadius;

  @override
  int get hashCode => Object.hash(
    backgroundColor,
    elevation,
    borderRadius,
    border,
    offset,
    minWidth,
    maxWidth,
    maxHeight,
    padding,
    motion,
    selectedColor,
    selectedBorderRadius,
  );
}
