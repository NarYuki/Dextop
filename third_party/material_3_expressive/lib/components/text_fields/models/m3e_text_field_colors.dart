import 'dart:ui' show lerpDouble;

import 'package:flutter/widgets.dart';

/// Resolved colors and stroke width for one `M3ETextField` state.
@immutable
class M3ETextFieldColors {
  /// Creates a resolved palette.
  const M3ETextFieldColors({
    required this.container,
    required this.stateLayer,
    required this.stroke,
    required this.strokeWidth,
    required this.label,
    required this.leadingIcon,
    required this.trailingIcon,
    required this.inputText,
    required this.supportingText,
    required this.caret,
    required this.affix,
    required this.focusRing,
  });

  /// Container fill.
  final Color container;

  /// Hover state layer over the container.
  final Color stateLayer;

  /// Active indicator (filled) or outline (outlined) color.
  final Color stroke;

  /// Active indicator height or outline width.
  final double strokeWidth;

  /// Label text color.
  final Color label;

  /// Leading icon color.
  final Color leadingIcon;

  /// Trailing icon color.
  final Color trailingIcon;

  /// Input text color.
  final Color inputText;

  /// Supporting text and character counter color.
  final Color supportingText;

  /// Caret color.
  final Color caret;

  /// Prefix, suffix and placeholder color.
  final Color affix;

  /// Keyboard focus ring color.
  final Color focusRing;

  /// Interpolates between [a] and [b].
  static M3ETextFieldColors lerp(
    M3ETextFieldColors a,
    M3ETextFieldColors b,
    double t,
  ) {
    Color c(Color x, Color y) => Color.lerp(x, y, t)!;
    return M3ETextFieldColors(
      container: c(a.container, b.container),
      stateLayer: c(a.stateLayer, b.stateLayer),
      stroke: c(a.stroke, b.stroke),
      strokeWidth: lerpDouble(a.strokeWidth, b.strokeWidth, t)!,
      label: c(a.label, b.label),
      leadingIcon: c(a.leadingIcon, b.leadingIcon),
      trailingIcon: c(a.trailingIcon, b.trailingIcon),
      inputText: c(a.inputText, b.inputText),
      supportingText: c(a.supportingText, b.supportingText),
      caret: c(a.caret, b.caret),
      affix: c(a.affix, b.affix),
      focusRing: c(a.focusRing, b.focusRing),
    );
  }

  @override
  bool operator ==(Object other) {
    return other is M3ETextFieldColors &&
        other.container == container &&
        other.stateLayer == stateLayer &&
        other.stroke == stroke &&
        other.strokeWidth == strokeWidth &&
        other.label == label &&
        other.leadingIcon == leadingIcon &&
        other.trailingIcon == trailingIcon &&
        other.inputText == inputText &&
        other.supportingText == supportingText &&
        other.caret == caret &&
        other.affix == affix &&
        other.focusRing == focusRing;
  }

  @override
  int get hashCode => Object.hash(
    container,
    stateLayer,
    stroke,
    strokeWidth,
    label,
    leadingIcon,
    trailingIcon,
    inputText,
    supportingText,
    caret,
    affix,
    focusRing,
  );
}
