import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';

/// M3EDropdownSearchStyle.

@immutable
class M3EDropdownSearchStyle with Diagnosticable {
  /// Hint text shown in the search field.
  final String hintText;

  /// Search field hint style.
  final TextStyle? hintStyle;

  /// Search field text style.
  final TextStyle? textStyle;

  /// Fill color for the search field.
  final Color? fillColor;

  /// Whether the search field is filled.
  final bool filled;

  /// Whether to auto-focus the search field when the dropdown opens.
  final bool autofocus;

  /// Whether to show a clear‐search icon.
  final bool showClearIcon;

  /// Custom clear icon widget for the search field.
  final Widget? clearIcon;

  /// The debounce duration in milliseconds for search input.
  ///
  /// When set to a value greater than 0, search callbacks will
  /// only fire after the user stops typing for this duration.
  /// Defaults to 0 (no debounce).
  final int searchDebounceMs;

  /// Border radius of the search field.
  ///
  /// When null, falls back to the panel container radius.
  final BorderRadius? borderRadius;

  /// Content padding inside the search text field.
  ///
  /// Defaults to `EdgeInsets.symmetric(horizontal: 12, vertical: 8)`.
  final EdgeInsetsGeometry contentPadding;

  /// Outer margin (padding around the search field container).
  ///
  /// Defaults to `EdgeInsets.fromLTRB(12, 8, 12, 4)`.
  final EdgeInsetsGeometry margin;

  /// Mouse cursor when hovering over the search field.
  final MouseCursor? mouseCursor;

  /// Creates a [M3EDropdownSearchStyle].
  const M3EDropdownSearchStyle({
    this.hintText = 'Search…',
    this.hintStyle,
    this.textStyle,
    this.fillColor,
    this.filled = false,
    this.autofocus = false,
    this.showClearIcon = true,
    this.clearIcon,
    this.searchDebounceMs = 0,
    this.borderRadius,
    this.contentPadding = const EdgeInsets.symmetric(
      horizontal: 12,
      vertical: 8,
    ),
    this.margin = const EdgeInsets.fromLTRB(12, 8, 12, 4),
    this.mouseCursor,
  });

  /// Creates a copy of this style with the given fields replaced.
  M3EDropdownSearchStyle copyWith({
    String? hintText,
    TextStyle? hintStyle,
    TextStyle? textStyle,
    Color? fillColor,
    bool? filled,
    bool? autofocus,
    bool? showClearIcon,
    Widget? clearIcon,
    int? searchDebounceMs,
    BorderRadius? borderRadius,
    EdgeInsetsGeometry? contentPadding,
    EdgeInsetsGeometry? margin,
    MouseCursor? mouseCursor,
  }) {
    return M3EDropdownSearchStyle(
      hintText: hintText ?? this.hintText,
      hintStyle: hintStyle ?? this.hintStyle,
      textStyle: textStyle ?? this.textStyle,
      fillColor: fillColor ?? this.fillColor,
      filled: filled ?? this.filled,
      autofocus: autofocus ?? this.autofocus,
      showClearIcon: showClearIcon ?? this.showClearIcon,
      clearIcon: clearIcon ?? this.clearIcon,
      searchDebounceMs: searchDebounceMs ?? this.searchDebounceMs,
      borderRadius: borderRadius ?? this.borderRadius,
      contentPadding: contentPadding ?? this.contentPadding,
      margin: margin ?? this.margin,
      mouseCursor: mouseCursor ?? this.mouseCursor,
    );
  }

  /// Linearly interpolate between two search styles.
  static M3EDropdownSearchStyle lerp(
    M3EDropdownSearchStyle? a,
    M3EDropdownSearchStyle? b,
    double t,
  ) {
    if (a == null && b == null) {
      return const M3EDropdownSearchStyle();
    }
    return M3EDropdownSearchStyle(
      hintText: t < 0.5
          ? (a?.hintText ?? 'Search…')
          : (b?.hintText ?? 'Search…'),
      hintStyle: TextStyle.lerp(a?.hintStyle, b?.hintStyle, t),
      textStyle: TextStyle.lerp(a?.textStyle, b?.textStyle, t),
      fillColor: Color.lerp(a?.fillColor, b?.fillColor, t),
      filled: t < 0.5 ? (a?.filled ?? false) : (b?.filled ?? false),
      autofocus: t < 0.5 ? (a?.autofocus ?? false) : (b?.autofocus ?? false),
      showClearIcon: t < 0.5
          ? (a?.showClearIcon ?? true)
          : (b?.showClearIcon ?? true),
      clearIcon: t < 0.5 ? a?.clearIcon : b?.clearIcon,
      searchDebounceMs:
          lerpDouble(
            a?.searchDebounceMs.toDouble(),
            b?.searchDebounceMs.toDouble(),
            t,
          )?.round() ??
          0,
      borderRadius: BorderRadius.lerp(a?.borderRadius, b?.borderRadius, t),
      contentPadding:
          EdgeInsetsGeometry.lerp(a?.contentPadding, b?.contentPadding, t) ??
          const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      margin:
          EdgeInsetsGeometry.lerp(a?.margin, b?.margin, t) ??
          const EdgeInsets.fromLTRB(12, 8, 12, 4),
      mouseCursor: t < 0.5 ? a?.mouseCursor : b?.mouseCursor,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is M3EDropdownSearchStyle &&
          hintText == other.hintText &&
          hintStyle == other.hintStyle &&
          textStyle == other.textStyle &&
          fillColor == other.fillColor &&
          filled == other.filled &&
          autofocus == other.autofocus &&
          showClearIcon == other.showClearIcon &&
          clearIcon == other.clearIcon &&
          searchDebounceMs == other.searchDebounceMs &&
          borderRadius == other.borderRadius &&
          contentPadding == other.contentPadding &&
          margin == other.margin &&
          mouseCursor == other.mouseCursor;

  @override
  int get hashCode => Object.hash(
    hintText,
    hintStyle,
    textStyle,
    fillColor,
    filled,
    autofocus,
    showClearIcon,
    clearIcon,
    searchDebounceMs,
    borderRadius,
    contentPadding,
    margin,
    mouseCursor,
  );

  @override
  void debugFillProperties(DiagnosticPropertiesBuilder properties) {
    super.debugFillProperties(properties);
    properties
      ..add(StringProperty('hintText', hintText))
      ..add(ColorProperty('fillColor', fillColor))
      ..add(DiagnosticsProperty<BorderRadius>('borderRadius', borderRadius));
  }
}
