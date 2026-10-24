import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';

/// M3EDropdownItemStyle.

@immutable
class M3EDropdownItemStyle with Diagnosticable {
  /// Item background color.
  final Color? backgroundColor;

  /// Background color for selected items.
  final Color? selectedBackgroundColor;

  /// Background color for disabled items.
  final Color? disabledBackgroundColor;

  /// Text color.
  final Color? textColor;

  /// Text color for selected items.
  final Color? selectedTextColor;

  /// Text color for disabled items.
  final Color? disabledTextColor;

  /// Text style for item labels.
  final TextStyle? textStyle;

  /// Text style for selected item labels.
  final TextStyle? selectedTextStyle;

  /// Icon shown next to selected items.
  final Widget? selectedIcon;

  /// Outer radius applied to the first and last dropdown item cards
  /// (the "cap" corners), mirroring the M3E card list treatment.
  ///
  /// Defaults to `12.0`.
  final double? outerRadius;

  /// Inner radius applied to middle dropdown item cards.
  ///
  /// Defaults to `6.0`.
  final double innerRadius;

  /// Gap between items.
  ///
  /// When set, specifies the gap between items list.
  final double? itemGap;

  /// Inner padding applied to each dropdown item.
  ///
  /// Defaults to `EdgeInsets.symmetric(horizontal: 16, vertical: 12)`.
  final EdgeInsetsGeometry itemPadding;

  /// Border radius applied to a selected item.
  ///
  /// When null, falls back to [outerRadius] (or its default of `12.0`).
  final double? selectedBorderRadius;

  /// Radius applied to the item when hovered.
  ///
  /// Defaults to `8.0`.
  final double hoverRadius;

  /// Radius applied to the item when pressed.
  ///
  /// Defaults to `4.0`.
  final double pressedRadius;

  /// Splash factory for tap feedback on individual items.
  final InteractiveInkFeatureFactory? splashFactory;

  /// Splash color for the ripple effect.
  final Color? splashColor;

  /// Highlight color when the item is pressed.
  final Color? highlightColor;

  /// Mouse cursor when hovering over an item.
  final MouseCursor? mouseCursor;

  /// Creates a [M3EDropdownItemStyle].
  const M3EDropdownItemStyle({
    this.backgroundColor,
    this.selectedBackgroundColor,
    this.disabledBackgroundColor,
    this.textColor,
    this.selectedTextColor,
    this.disabledTextColor,
    this.textStyle,
    this.selectedTextStyle,
    this.selectedIcon,
    this.outerRadius,
    this.innerRadius = 6.0,
    this.itemGap,
    this.itemPadding = const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
    this.selectedBorderRadius,
    this.hoverRadius = 8.0,
    this.pressedRadius = 4.0,
    this.splashFactory,
    this.splashColor,
    this.highlightColor,
    this.mouseCursor,
  });

  /// Creates a copy of this style with the given fields replaced.
  M3EDropdownItemStyle copyWith({
    Color? backgroundColor,
    Color? selectedBackgroundColor,
    Color? disabledBackgroundColor,
    Color? textColor,
    Color? selectedTextColor,
    Color? disabledTextColor,
    TextStyle? textStyle,
    TextStyle? selectedTextStyle,
    Widget? selectedIcon,
    double? outerRadius,
    double? innerRadius,
    double? itemGap,
    EdgeInsetsGeometry? itemPadding,
    double? selectedBorderRadius,
    double? hoverRadius,
    double? pressedRadius,
    InteractiveInkFeatureFactory? splashFactory,
    Color? splashColor,
    Color? highlightColor,
    MouseCursor? mouseCursor,
  }) {
    return M3EDropdownItemStyle(
      backgroundColor: backgroundColor ?? this.backgroundColor,
      selectedBackgroundColor:
          selectedBackgroundColor ?? this.selectedBackgroundColor,
      disabledBackgroundColor:
          disabledBackgroundColor ?? this.disabledBackgroundColor,
      textColor: textColor ?? this.textColor,
      selectedTextColor: selectedTextColor ?? this.selectedTextColor,
      disabledTextColor: disabledTextColor ?? this.disabledTextColor,
      textStyle: textStyle ?? this.textStyle,
      selectedTextStyle: selectedTextStyle ?? this.selectedTextStyle,
      selectedIcon: selectedIcon ?? this.selectedIcon,
      outerRadius: outerRadius ?? this.outerRadius,
      innerRadius: innerRadius ?? this.innerRadius,
      itemGap: itemGap ?? this.itemGap,
      itemPadding: itemPadding ?? this.itemPadding,
      selectedBorderRadius: selectedBorderRadius ?? this.selectedBorderRadius,
      hoverRadius: hoverRadius ?? this.hoverRadius,
      pressedRadius: pressedRadius ?? this.pressedRadius,
      splashFactory: splashFactory ?? this.splashFactory,
      splashColor: splashColor ?? this.splashColor,
      highlightColor: highlightColor ?? this.highlightColor,
      mouseCursor: mouseCursor ?? this.mouseCursor,
    );
  }

  /// Linearly interpolate between two dropdown item styles.
  static M3EDropdownItemStyle lerp(
    M3EDropdownItemStyle? a,
    M3EDropdownItemStyle? b,
    double t,
  ) {
    if (a == null && b == null) {
      return const M3EDropdownItemStyle();
    }
    return M3EDropdownItemStyle(
      backgroundColor: Color.lerp(a?.backgroundColor, b?.backgroundColor, t),
      selectedBackgroundColor: Color.lerp(
        a?.selectedBackgroundColor,
        b?.selectedBackgroundColor,
        t,
      ),
      disabledBackgroundColor: Color.lerp(
        a?.disabledBackgroundColor,
        b?.disabledBackgroundColor,
        t,
      ),
      textColor: Color.lerp(a?.textColor, b?.textColor, t),
      selectedTextColor: Color.lerp(
        a?.selectedTextColor,
        b?.selectedTextColor,
        t,
      ),
      disabledTextColor: Color.lerp(
        a?.disabledTextColor,
        b?.disabledTextColor,
        t,
      ),
      textStyle: TextStyle.lerp(a?.textStyle, b?.textStyle, t),
      selectedTextStyle: TextStyle.lerp(
        a?.selectedTextStyle,
        b?.selectedTextStyle,
        t,
      ),
      selectedIcon: t < 0.5 ? a?.selectedIcon : b?.selectedIcon,
      outerRadius: lerpDouble(a?.outerRadius, b?.outerRadius, t),
      innerRadius: lerpDouble(a?.innerRadius, b?.innerRadius, t) ?? 6.0,
      itemGap: lerpDouble(a?.itemGap, b?.itemGap, t),
      itemPadding:
          EdgeInsetsGeometry.lerp(a?.itemPadding, b?.itemPadding, t) ??
          const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      selectedBorderRadius: lerpDouble(
        a?.selectedBorderRadius,
        b?.selectedBorderRadius,
        t,
      ),
      hoverRadius: lerpDouble(a?.hoverRadius, b?.hoverRadius, t) ?? 8.0,
      pressedRadius: lerpDouble(a?.pressedRadius, b?.pressedRadius, t) ?? 4.0,
      splashFactory: t < 0.5 ? a?.splashFactory : b?.splashFactory,
      splashColor: Color.lerp(a?.splashColor, b?.splashColor, t),
      highlightColor: Color.lerp(a?.highlightColor, b?.highlightColor, t),
      mouseCursor: t < 0.5 ? a?.mouseCursor : b?.mouseCursor,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is M3EDropdownItemStyle &&
          backgroundColor == other.backgroundColor &&
          selectedBackgroundColor == other.selectedBackgroundColor &&
          disabledBackgroundColor == other.disabledBackgroundColor &&
          textColor == other.textColor &&
          selectedTextColor == other.selectedTextColor &&
          disabledTextColor == other.disabledTextColor &&
          textStyle == other.textStyle &&
          selectedTextStyle == other.selectedTextStyle &&
          selectedIcon == other.selectedIcon &&
          outerRadius == other.outerRadius &&
          innerRadius == other.innerRadius &&
          itemGap == other.itemGap &&
          itemPadding == other.itemPadding &&
          selectedBorderRadius == other.selectedBorderRadius &&
          hoverRadius == other.hoverRadius &&
          pressedRadius == other.pressedRadius &&
          splashFactory == other.splashFactory &&
          splashColor == other.splashColor &&
          highlightColor == other.highlightColor &&
          mouseCursor == other.mouseCursor;

  @override
  int get hashCode => Object.hashAll([
    backgroundColor,
    selectedBackgroundColor,
    disabledBackgroundColor,
    textColor,
    selectedTextColor,
    disabledTextColor,
    textStyle,
    selectedTextStyle,
    selectedIcon,
    outerRadius,
    innerRadius,
    itemGap,
    itemPadding,
    selectedBorderRadius,
    hoverRadius,
    pressedRadius,
    splashFactory,
    splashColor,
    highlightColor,
    mouseCursor,
  ]);

  @override
  void debugFillProperties(DiagnosticPropertiesBuilder properties) {
    super.debugFillProperties(properties);
    properties
      ..add(ColorProperty('backgroundColor', backgroundColor))
      ..add(ColorProperty('selectedBackgroundColor', selectedBackgroundColor))
      ..add(DoubleProperty('outerRadius', outerRadius))
      ..add(DoubleProperty('innerRadius', innerRadius))
      ..add(
        DiagnosticsProperty<EdgeInsetsGeometry>('itemPadding', itemPadding),
      );
  }
}
