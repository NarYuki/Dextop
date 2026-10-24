import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:material_3_expressive/components/dropdown_menus/m3e_dropdown_menus.dart'
    show M3EDropdownItemStyle;
import 'package:material_3_expressive/components/dropdown_menus/styles/m3e_dropdown_item_style.dart'
    show M3EDropdownItemStyle;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3EDropdownItemStyle;
import 'package:material_ui/material_ui.dart';

/// M3EDropdownFieldStyle.

@immutable
class M3EDropdownFieldStyle with Diagnosticable {
  /// Hint text shown when nothing is selected.
  final String? hintText;

  /// Style for the hint text.
  final TextStyle? hintStyle;

  /// Style for the selected value text (single-select mode).
  final TextStyle? selectedTextStyle;

  /// Style for the error message shown below the field.
  final TextStyle? errorStyle;

  /// An optional leading icon/widget.
  final Widget? prefixIcon;

  /// An optional trailing icon/widget. Overrides the default animated arrow
  /// when provided.
  final Widget? suffixIcon;

  /// A custom widget to clear all selections.
  ///
  /// Only used when [showClearIcon] is true.
  final Widget? clearIcon;

  /// Background color of the field.
  final Color? backgroundColor;

  /// Text color of the field.
  final Color? foregroundColor;

  /// Inner padding of the field content.
  final EdgeInsetsGeometry padding;

  /// Outer margin around the field.
  final EdgeInsetsGeometry margin;

  /// The border drawn around the field.
  final BorderSide? border;

  /// Border when the dropdown is focused / open.
  final BorderSide? focusedBorder;

  /// Border when the dropdown has a validation error.
  final BorderSide? errorBorder;

  /// Whether to show the default animated arrow indicator.
  ///
  /// Set to `false` to hide the built-in chevron. If [suffixIcon]
  /// is non-null the default arrow is _already_ replaced.
  ///
  /// Defaults to `true`.
  final bool showArrow;

  /// A custom widget shown while async data is loading.
  ///
  /// Defaults to a small [CircularProgressIndicator] when null.
  final Widget? loadingWidget;

  /// Border radius of the field.
  ///
  /// When null the widget's `outerRadius` inside [M3EDropdownItemStyle] is used as a circular radius.
  final BorderRadius? borderRadius;

  /// Whether to show a clear-all icon when items are selected.
  ///
  /// The icon replaces the suffix/arrow when visible and clears all
  /// selected items on tap. Defaults to `false`.
  final bool showClearIcon;

  /// Whether to animate the default suffix icon (arrow) rotation.
  ///
  /// Defaults to `true`.
  final bool animateSuffixIcon;

  /// Border radius of the field when the dropdown is expanded.
  ///
  /// When non-null the field animates from [borderRadius] to this value
  /// with a snappy spring when the dropdown opens, and back when it closes.
  final double? selectedBorderRadius;

  /// Border radius of the field when hovered.
  final double? hoverRadius;

  /// Border radius of the field when pressed.
  final double? pressedRadius;

  /// Splash factory for tap feedback on the field.
  final InteractiveInkFeatureFactory? splashFactory;

  /// Splash color for the ripple effect.
  final Color? splashColor;

  /// Highlight color when the field is pressed.
  final Color? highlightColor;

  /// Mouse cursor when hovering over the field.
  final MouseCursor? mouseCursor;

  /// Creates a [M3EDropdownFieldStyle].
  const M3EDropdownFieldStyle({
    this.hintText,
    this.hintStyle,
    this.selectedTextStyle,
    this.errorStyle,
    this.prefixIcon,
    this.suffixIcon,
    this.clearIcon,
    this.backgroundColor,
    this.foregroundColor,
    this.border,
    this.focusedBorder,
    this.errorBorder,
    this.padding = const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    this.margin = EdgeInsets.zero,
    this.showArrow = true,
    this.loadingWidget,
    this.borderRadius,
    this.selectedBorderRadius,
    this.hoverRadius,
    this.pressedRadius,
    this.splashFactory,
    this.splashColor,
    this.highlightColor,
    this.mouseCursor,
    this.showClearIcon = false,
    this.animateSuffixIcon = true,
  });

  /// Creates a copy of this style with the given fields replaced.
  M3EDropdownFieldStyle copyWith({
    String? hintText,
    TextStyle? hintStyle,
    TextStyle? selectedTextStyle,
    TextStyle? errorStyle,
    Widget? prefixIcon,
    Widget? suffixIcon,
    Widget? clearIcon,
    Color? backgroundColor,
    Color? foregroundColor,
    EdgeInsetsGeometry? padding,
    EdgeInsetsGeometry? margin,
    BorderSide? border,
    BorderSide? focusedBorder,
    BorderSide? errorBorder,
    bool? showArrow,
    Widget? loadingWidget,
    BorderRadius? borderRadius,
    bool? showClearIcon,
    bool? animateSuffixIcon,
    double? selectedBorderRadius,
    double? hoverRadius,
    double? pressedRadius,
    InteractiveInkFeatureFactory? splashFactory,
    Color? splashColor,
    Color? highlightColor,
    MouseCursor? mouseCursor,
  }) {
    return M3EDropdownFieldStyle(
      hintText: hintText ?? this.hintText,
      hintStyle: hintStyle ?? this.hintStyle,
      selectedTextStyle: selectedTextStyle ?? this.selectedTextStyle,
      errorStyle: errorStyle ?? this.errorStyle,
      prefixIcon: prefixIcon ?? this.prefixIcon,
      suffixIcon: suffixIcon ?? this.suffixIcon,
      clearIcon: clearIcon ?? this.clearIcon,
      backgroundColor: backgroundColor ?? this.backgroundColor,
      foregroundColor: foregroundColor ?? this.foregroundColor,
      padding: padding ?? this.padding,
      margin: margin ?? this.margin,
      border: border ?? this.border,
      focusedBorder: focusedBorder ?? this.focusedBorder,
      errorBorder: errorBorder ?? this.errorBorder,
      showArrow: showArrow ?? this.showArrow,
      loadingWidget: loadingWidget ?? this.loadingWidget,
      borderRadius: borderRadius ?? this.borderRadius,
      showClearIcon: showClearIcon ?? this.showClearIcon,
      animateSuffixIcon: animateSuffixIcon ?? this.animateSuffixIcon,
      selectedBorderRadius: selectedBorderRadius ?? this.selectedBorderRadius,
      hoverRadius: hoverRadius ?? this.hoverRadius,
      pressedRadius: pressedRadius ?? this.pressedRadius,
      splashFactory: splashFactory ?? this.splashFactory,
      splashColor: splashColor ?? this.splashColor,
      highlightColor: highlightColor ?? this.highlightColor,
      mouseCursor: mouseCursor ?? this.mouseCursor,
    );
  }

  /// Linearly interpolate between two field styles.
  static M3EDropdownFieldStyle lerp(
    M3EDropdownFieldStyle? a,
    M3EDropdownFieldStyle? b,
    double t,
  ) {
    if (a == null && b == null) {
      return const M3EDropdownFieldStyle();
    }
    return M3EDropdownFieldStyle(
      hintText: t < 0.5 ? a?.hintText : b?.hintText,
      hintStyle: TextStyle.lerp(a?.hintStyle, b?.hintStyle, t),
      selectedTextStyle: TextStyle.lerp(
        a?.selectedTextStyle,
        b?.selectedTextStyle,
        t,
      ),
      errorStyle: TextStyle.lerp(a?.errorStyle, b?.errorStyle, t),
      prefixIcon: t < 0.5 ? a?.prefixIcon : b?.prefixIcon,
      suffixIcon: t < 0.5 ? a?.suffixIcon : b?.suffixIcon,
      clearIcon: t < 0.5 ? a?.clearIcon : b?.clearIcon,
      backgroundColor: Color.lerp(a?.backgroundColor, b?.backgroundColor, t),
      foregroundColor: Color.lerp(a?.foregroundColor, b?.foregroundColor, t),
      padding: EdgeInsetsGeometry.lerp(a?.padding, b?.padding, t)!,
      margin: EdgeInsetsGeometry.lerp(a?.margin, b?.margin, t)!,
      border: BorderSide.lerp(
        a?.border ?? BorderSide.none,
        b?.border ?? BorderSide.none,
        t,
      ),
      focusedBorder: BorderSide.lerp(
        a?.focusedBorder ?? BorderSide.none,
        b?.focusedBorder ?? BorderSide.none,
        t,
      ),
      errorBorder: BorderSide.lerp(
        a?.errorBorder ?? BorderSide.none,
        b?.errorBorder ?? BorderSide.none,
        t,
      ),
      showArrow: t < 0.5 ? (a?.showArrow ?? true) : (b?.showArrow ?? true),
      loadingWidget: t < 0.5 ? a?.loadingWidget : b?.loadingWidget,
      borderRadius: BorderRadius.lerp(a?.borderRadius, b?.borderRadius, t),
      showClearIcon: t < 0.5
          ? (a?.showClearIcon ?? false)
          : (b?.showClearIcon ?? false),
      animateSuffixIcon: t < 0.5
          ? (a?.animateSuffixIcon ?? true)
          : (b?.animateSuffixIcon ?? true),
      selectedBorderRadius: lerpDouble(
        a?.selectedBorderRadius,
        b?.selectedBorderRadius,
        t,
      ),
      hoverRadius: lerpDouble(a?.hoverRadius, b?.hoverRadius, t),
      pressedRadius: lerpDouble(a?.pressedRadius, b?.pressedRadius, t),
      splashFactory: t < 0.5 ? a?.splashFactory : b?.splashFactory,
      splashColor: Color.lerp(a?.splashColor, b?.splashColor, t),
      highlightColor: Color.lerp(a?.highlightColor, b?.highlightColor, t),
      mouseCursor: t < 0.5 ? a?.mouseCursor : b?.mouseCursor,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is M3EDropdownFieldStyle &&
          hintText == other.hintText &&
          hintStyle == other.hintStyle &&
          selectedTextStyle == other.selectedTextStyle &&
          errorStyle == other.errorStyle &&
          prefixIcon == other.prefixIcon &&
          suffixIcon == other.suffixIcon &&
          clearIcon == other.clearIcon &&
          backgroundColor == other.backgroundColor &&
          foregroundColor == other.foregroundColor &&
          padding == other.padding &&
          margin == other.margin &&
          border == other.border &&
          focusedBorder == other.focusedBorder &&
          errorBorder == other.errorBorder &&
          showArrow == other.showArrow &&
          loadingWidget == other.loadingWidget &&
          borderRadius == other.borderRadius &&
          showClearIcon == other.showClearIcon &&
          animateSuffixIcon == other.animateSuffixIcon &&
          selectedBorderRadius == other.selectedBorderRadius &&
          hoverRadius == other.hoverRadius &&
          pressedRadius == other.pressedRadius &&
          splashFactory == other.splashFactory &&
          splashColor == other.splashColor &&
          highlightColor == other.highlightColor &&
          mouseCursor == other.mouseCursor;

  @override
  int get hashCode => Object.hashAll([
    hintText,
    hintStyle,
    selectedTextStyle,
    errorStyle,
    prefixIcon,
    suffixIcon,
    clearIcon,
    backgroundColor,
    foregroundColor,
    padding,
    margin,
    border,
    focusedBorder,
    errorBorder,
    showArrow,
    loadingWidget,
    borderRadius,
    showClearIcon,
    animateSuffixIcon,
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
      ..add(StringProperty('hintText', hintText))
      ..add(DiagnosticsProperty<TextStyle>('hintStyle', hintStyle))
      ..add(
        DiagnosticsProperty<TextStyle>('selectedTextStyle', selectedTextStyle),
      )
      ..add(DiagnosticsProperty<TextStyle>('errorStyle', errorStyle))
      ..add(ColorProperty('backgroundColor', backgroundColor))
      ..add(ColorProperty('foregroundColor', foregroundColor))
      ..add(DiagnosticsProperty<BorderRadius>('borderRadius', borderRadius))
      ..add(DoubleProperty('selectedBorderRadius', selectedBorderRadius));
  }
}
