import 'dart:ui' show lerpDouble;

import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// Color, type and action-state tokens for basic dialogs.
///
/// Null colors and styles resolve to the M3 "Dialog - Basic" roles.
@immutable
class M3EDialogAppearance {
  /// M3EDialogAppearance.
  const M3EDialogAppearance({
    this.containerColor,
    this.surfaceTintColor,
    this.iconColor,
    this.headlineColor,
    this.headlineTextStyle,
    this.subheadColor,
    this.subheadTextStyle,
    this.supportingTextColor,
    this.supportingTextStyle,
    this.dividerColor,
    this.actionColor,
    this.actionTextStyle,
    this.actionHoverOpacity = 0.08,
    this.actionFocusOpacity = 0.1,
    this.actionPressedOpacity = 0.1,
  });

  /// Container color. Null uses `surfaceContainerHigh`.
  final Color? containerColor;

  /// Surface tint layer. Null paints no tint.
  final Color? surfaceTintColor;

  /// Hero icon color. Null uses `secondary`.
  final Color? iconColor;

  /// Headline color. Null uses `onSurface`.
  final Color? headlineColor;

  /// Headline style. Null uses headline small (24/32, 400).
  final TextStyle? headlineTextStyle;

  /// Subhead color. Null uses `onSurface`.
  final Color? subheadColor;

  /// Subhead style. Null uses headline small (24/32, 400).
  final TextStyle? subheadTextStyle;

  /// Supporting text color. Null uses `onSurfaceVariant`.
  final Color? supportingTextColor;

  /// Supporting text style. Null uses body medium (14/20, 400, 0.25).
  final TextStyle? supportingTextStyle;

  /// Divider color. Null uses `outline`.
  final Color? dividerColor;

  /// Action label and state layer color. Null uses `primary`.
  final Color? actionColor;

  /// Action label style. Null uses label large (14/20, 500, 0.1).
  final TextStyle? actionTextStyle;

  /// Action hover state layer opacity (0.08).
  final double actionHoverOpacity;

  /// Action keyboard-focus state layer opacity (0.1).
  final double actionFocusOpacity;

  /// Action pressed state layer opacity (0.1), shown while held.
  final double actionPressedOpacity;

  /// Resolved container color.
  Color resolveContainer(M3EColorScheme scheme) =>
      containerColor ?? scheme.surfaceContainerHigh;

  /// Resolved icon color.
  Color resolveIcon(M3EColorScheme scheme) => iconColor ?? scheme.secondary;

  /// Resolved headline style.
  TextStyle resolveHeadline(M3EThemeData theme) =>
      (headlineTextStyle ?? theme.typeScale.headlineSmall).copyWith(
        color: headlineColor ?? theme.colorScheme.onSurface,
      );

  /// Resolved subhead style.
  TextStyle resolveSubhead(M3EThemeData theme) =>
      (subheadTextStyle ?? theme.typeScale.headlineSmall).copyWith(
        color: subheadColor ?? theme.colorScheme.onSurface,
      );

  /// Resolved supporting text style.
  TextStyle resolveSupporting(M3EThemeData theme) =>
      (supportingTextStyle ?? theme.typeScale.bodyMedium).copyWith(
        color: supportingTextColor ?? theme.colorScheme.onSurfaceVariant,
      );

  /// Resolved divider color.
  Color resolveDivider(M3EColorScheme scheme) => dividerColor ?? scheme.outline;

  /// Resolved action color.
  Color resolveAction(M3EColorScheme scheme) => actionColor ?? scheme.primary;

  /// Resolved action label style.
  TextStyle resolveActionText(M3EThemeData theme) =>
      actionTextStyle ?? theme.typeScale.labelLarge;

  /// State layer color for action [states] (pressed over focus over hover).
  ///
  /// Buttons pass live states: pressed only while held, and focused only
  /// for keyboard focus, so neither fill sticks after a click.
  Color? actionOverlay(Color color, Set<WidgetState> states) {
    if (states.contains(WidgetState.disabled)) {
      return null;
    }
    final double opacity = states.contains(WidgetState.pressed)
        ? actionPressedOpacity
        : states.contains(WidgetState.focused)
        ? actionFocusOpacity
        : states.contains(WidgetState.hovered)
        ? actionHoverOpacity
        : 0;
    return color.withValues(alpha: opacity);
  }

  /// Copy with non-null fields replaced.
  M3EDialogAppearance copyWith({
    Color? containerColor,
    Color? surfaceTintColor,
    Color? iconColor,
    Color? headlineColor,
    TextStyle? headlineTextStyle,
    Color? subheadColor,
    TextStyle? subheadTextStyle,
    Color? supportingTextColor,
    TextStyle? supportingTextStyle,
    Color? dividerColor,
    Color? actionColor,
    TextStyle? actionTextStyle,
    double? actionHoverOpacity,
    double? actionFocusOpacity,
    double? actionPressedOpacity,
  }) {
    return M3EDialogAppearance(
      containerColor: containerColor ?? this.containerColor,
      surfaceTintColor: surfaceTintColor ?? this.surfaceTintColor,
      iconColor: iconColor ?? this.iconColor,
      headlineColor: headlineColor ?? this.headlineColor,
      headlineTextStyle: headlineTextStyle ?? this.headlineTextStyle,
      subheadColor: subheadColor ?? this.subheadColor,
      subheadTextStyle: subheadTextStyle ?? this.subheadTextStyle,
      supportingTextColor: supportingTextColor ?? this.supportingTextColor,
      supportingTextStyle: supportingTextStyle ?? this.supportingTextStyle,
      dividerColor: dividerColor ?? this.dividerColor,
      actionColor: actionColor ?? this.actionColor,
      actionTextStyle: actionTextStyle ?? this.actionTextStyle,
      actionHoverOpacity: actionHoverOpacity ?? this.actionHoverOpacity,
      actionFocusOpacity: actionFocusOpacity ?? this.actionFocusOpacity,
      actionPressedOpacity: actionPressedOpacity ?? this.actionPressedOpacity,
    );
  }

  /// Linear interpolation with [other].
  M3EDialogAppearance lerp(M3EDialogAppearance? other, double t) {
    if (other == null) {
      return this;
    }
    return M3EDialogAppearance(
      containerColor: Color.lerp(containerColor, other.containerColor, t),
      surfaceTintColor: Color.lerp(surfaceTintColor, other.surfaceTintColor, t),
      iconColor: Color.lerp(iconColor, other.iconColor, t),
      headlineColor: Color.lerp(headlineColor, other.headlineColor, t),
      headlineTextStyle: TextStyle.lerp(
        headlineTextStyle,
        other.headlineTextStyle,
        t,
      ),
      subheadColor: Color.lerp(subheadColor, other.subheadColor, t),
      subheadTextStyle: TextStyle.lerp(
        subheadTextStyle,
        other.subheadTextStyle,
        t,
      ),
      supportingTextColor: Color.lerp(
        supportingTextColor,
        other.supportingTextColor,
        t,
      ),
      supportingTextStyle: TextStyle.lerp(
        supportingTextStyle,
        other.supportingTextStyle,
        t,
      ),
      dividerColor: Color.lerp(dividerColor, other.dividerColor, t),
      actionColor: Color.lerp(actionColor, other.actionColor, t),
      actionTextStyle: TextStyle.lerp(
        actionTextStyle,
        other.actionTextStyle,
        t,
      ),
      actionHoverOpacity: lerpDouble(
        actionHoverOpacity,
        other.actionHoverOpacity,
        t,
      )!,
      actionFocusOpacity: lerpDouble(
        actionFocusOpacity,
        other.actionFocusOpacity,
        t,
      )!,
      actionPressedOpacity: lerpDouble(
        actionPressedOpacity,
        other.actionPressedOpacity,
        t,
      )!,
    );
  }
}
