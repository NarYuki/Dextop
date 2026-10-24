import 'package:material_3_expressive/foundations/foundations.dart';
import 'package:material_ui/material_ui.dart';

/// Styling options for the checkbox shown in multi-select bottom sheets.
@immutable
class M3ESplitButtonCheckboxStyle {
  /// M3ESplitButtonCheckboxStyle.
  const M3ESplitButtonCheckboxStyle({
    this.activeColor,
    this.iconColor,
    this.nonActiveColor,
    this.borderColor,
    this.activeBorderRadius,
    this.nonActiveBorderRadius,
    this.icon = const Icon(M3EIcons.check_rounded),
  });

  /// activeColor.

  final Color? activeColor;

  /// iconColor.
  final Color? iconColor;

  /// nonActiveColor.
  final Color? nonActiveColor;

  /// borderColor.
  final Color? borderColor;

  /// activeBorderRadius.
  final BorderRadius? activeBorderRadius;

  /// nonActiveBorderRadius.
  final BorderRadius? nonActiveBorderRadius;

  /// icon.
  final Widget? icon;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is M3ESplitButtonCheckboxStyle &&
          activeColor == other.activeColor &&
          iconColor == other.iconColor &&
          nonActiveColor == other.nonActiveColor &&
          borderColor == other.borderColor &&
          activeBorderRadius == other.activeBorderRadius &&
          nonActiveBorderRadius == other.nonActiveBorderRadius &&
          icon == other.icon;

  @override
  int get hashCode => Object.hash(
    activeColor,
    iconColor,
    nonActiveColor,
    borderColor,
    activeBorderRadius,
    nonActiveBorderRadius,
    icon,
  );
}
