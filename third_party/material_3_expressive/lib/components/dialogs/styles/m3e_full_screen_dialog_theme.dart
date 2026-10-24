import 'dart:ui' show lerpDouble;

import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// Tokens for full-screen dialogs (M3 "Dialog - Full screen").
///
/// Header height and close / action gaps live on `M3EDialogTheme`. Null
/// colors and styles resolve to the spec roles.
@immutable
class M3EFullScreenDialogTheme {
  /// M3EFullScreenDialogTheme.
  const M3EFullScreenDialogTheme({
    this.maxWidth = double.infinity,
    this.contentPadding = const EdgeInsets.fromLTRB(24, 24, 24, 0),
    this.elementGap = 8,
    this.closeTitleGap = 12,
    this.actionBarHeight = 56,
    this.actionBarPadding = const EdgeInsets.symmetric(horizontal: 24),
    this.cornerRadius = 0,
    this.containerElevation = M3EElevation.level0,
    this.headerElevation = M3EElevation.level0,
    this.headerScrolledElevation = M3EElevation.level0,
    this.actionBarElevation = M3EElevation.level0,
    this.actionBarScrolledElevation = M3EElevation.level0,
    this.dividerThickness = 1,
    this.showDivider = false,
    this.containerColor,
    this.onScrollContainerColor,
    this.headerColor,
    this.actionBarColor,
    this.surfaceTintColor,
    this.iconColor,
    this.headlineColor,
    this.dividerColor,
    this.actionColor,
    this.contentTextColor,
    this.headlineTextStyle,
    this.actionTextStyle,
    this.contentHeadlineTextStyle,
    this.contentTextStyle,
    this.enterSpring = M3EMotion.spatialDefault,
    this.scrollSpring = M3EMotion.effectsDefault,
  });

  /// Max content width. Infinite by default, so the dialog fills the view.
  final double maxWidth;

  /// Content padding: 24 top / left / right.
  final EdgeInsets contentPadding;

  /// Gap between content elements (8).
  final double elementGap;

  /// Gap after the 48dp close target, so icon to title is 24.
  final double closeTitleGap;

  /// Bottom action bar height (56).
  final double actionBarHeight;

  /// Bottom action bar padding.
  final EdgeInsets actionBarPadding;

  /// Container corner radius (0).
  final double cornerRadius;

  /// Container elevation (level 0).
  final double containerElevation;

  /// Header elevation at rest (level 0).
  final double headerElevation;

  /// Header elevation while content scrolls under it (level 0, like the app
  /// bar; only the color changes).
  final double headerScrolledElevation;

  /// Action bar elevation at rest (level 0).
  final double actionBarElevation;

  /// Action bar elevation while content scrolls under it (level 0).
  final double actionBarScrolledElevation;

  /// Divider thickness (1).
  final double dividerThickness;

  /// Whether the header divider shows by default (off).
  final bool showDivider;

  /// Container color. Null uses `surface`.
  final Color? containerColor;

  /// Header and action bar color on scroll. Null uses `surfaceContainer`.
  final Color? onScrollContainerColor;

  /// Header color. Null uses `surface`.
  final Color? headerColor;

  /// Action bar color. Null uses `surface`.
  final Color? actionBarColor;

  /// Header surface tint layer. Null paints no tint.
  final Color? surfaceTintColor;

  /// Close icon color. Null uses `onSurface`.
  final Color? iconColor;

  /// Header headline color. Null uses `onSurface`.
  final Color? headlineColor;

  /// Divider color. Null uses `surfaceContainerHighest`.
  final Color? dividerColor;

  /// Header and action bar action color. Null uses `primary`.
  final Color? actionColor;

  /// Body text color. Null uses `onSurfaceVariant`.
  final Color? contentTextColor;

  /// Header headline style. Null uses title large (22/28, 400).
  final TextStyle? headlineTextStyle;

  /// Action label style. Null uses label large (14/20, 500, 0.1).
  final TextStyle? actionTextStyle;

  /// Content-area headline style. Null uses headline small.
  final TextStyle? contentHeadlineTextStyle;

  /// Body text style. Null uses body medium.
  final TextStyle? contentTextStyle;

  /// Slide-in spring (spatial default).
  final M3ESpring enterSpring;

  /// On-scroll color / elevation spring (effects default).
  final M3ESpring scrollSpring;

  /// Resolved container color.
  Color resolveContainer(M3EColorScheme scheme) =>
      containerColor ?? scheme.surface;

  /// Resolved on-scroll color.
  Color resolveOnScroll(M3EColorScheme scheme) =>
      onScrollContainerColor ?? scheme.surfaceContainer;

  /// Resolved header color.
  Color resolveHeader(M3EColorScheme scheme) => headerColor ?? scheme.surface;

  /// Resolved action bar color.
  Color resolveActionBar(M3EColorScheme scheme) =>
      actionBarColor ?? scheme.surface;

  /// Resolved close icon color.
  Color resolveIcon(M3EColorScheme scheme) => iconColor ?? scheme.onSurface;

  /// Resolved divider color.
  Color resolveDivider(M3EColorScheme scheme) =>
      dividerColor ?? scheme.surfaceContainerHighest;

  /// Resolved action color.
  Color resolveAction(M3EColorScheme scheme) => actionColor ?? scheme.primary;

  /// Resolved header headline style.
  TextStyle resolveHeadline(M3EThemeData theme) =>
      (headlineTextStyle ?? theme.typeScale.titleLarge).copyWith(
        color: headlineColor ?? theme.colorScheme.onSurface,
      );

  /// Resolved action label style.
  TextStyle resolveActionText(M3EThemeData theme) =>
      actionTextStyle ?? theme.typeScale.labelLarge;

  /// Resolved content headline style.
  TextStyle resolveContentHeadline(M3EThemeData theme) =>
      (contentHeadlineTextStyle ?? theme.typeScale.headlineSmall).copyWith(
        color: headlineColor ?? theme.colorScheme.onSurface,
      );

  /// Resolved body text style.
  TextStyle resolveContent(M3EThemeData theme) =>
      (contentTextStyle ?? theme.typeScale.bodyMedium).copyWith(
        color: contentTextColor ?? theme.colorScheme.onSurfaceVariant,
      );

  /// Copy with non-null fields replaced.
  M3EFullScreenDialogTheme copyWith({
    double? maxWidth,
    EdgeInsets? contentPadding,
    double? elementGap,
    double? closeTitleGap,
    double? actionBarHeight,
    EdgeInsets? actionBarPadding,
    double? cornerRadius,
    double? containerElevation,
    double? headerElevation,
    double? headerScrolledElevation,
    double? actionBarElevation,
    double? actionBarScrolledElevation,
    double? dividerThickness,
    bool? showDivider,
    Color? containerColor,
    Color? onScrollContainerColor,
    Color? headerColor,
    Color? actionBarColor,
    Color? surfaceTintColor,
    Color? iconColor,
    Color? headlineColor,
    Color? dividerColor,
    Color? actionColor,
    Color? contentTextColor,
    TextStyle? headlineTextStyle,
    TextStyle? actionTextStyle,
    TextStyle? contentHeadlineTextStyle,
    TextStyle? contentTextStyle,
    M3ESpring? enterSpring,
    M3ESpring? scrollSpring,
  }) {
    return M3EFullScreenDialogTheme(
      maxWidth: maxWidth ?? this.maxWidth,
      contentPadding: contentPadding ?? this.contentPadding,
      elementGap: elementGap ?? this.elementGap,
      closeTitleGap: closeTitleGap ?? this.closeTitleGap,
      actionBarHeight: actionBarHeight ?? this.actionBarHeight,
      actionBarPadding: actionBarPadding ?? this.actionBarPadding,
      cornerRadius: cornerRadius ?? this.cornerRadius,
      containerElevation: containerElevation ?? this.containerElevation,
      headerElevation: headerElevation ?? this.headerElevation,
      headerScrolledElevation:
          headerScrolledElevation ?? this.headerScrolledElevation,
      actionBarElevation: actionBarElevation ?? this.actionBarElevation,
      actionBarScrolledElevation:
          actionBarScrolledElevation ?? this.actionBarScrolledElevation,
      dividerThickness: dividerThickness ?? this.dividerThickness,
      showDivider: showDivider ?? this.showDivider,
      containerColor: containerColor ?? this.containerColor,
      onScrollContainerColor:
          onScrollContainerColor ?? this.onScrollContainerColor,
      headerColor: headerColor ?? this.headerColor,
      actionBarColor: actionBarColor ?? this.actionBarColor,
      surfaceTintColor: surfaceTintColor ?? this.surfaceTintColor,
      iconColor: iconColor ?? this.iconColor,
      headlineColor: headlineColor ?? this.headlineColor,
      dividerColor: dividerColor ?? this.dividerColor,
      actionColor: actionColor ?? this.actionColor,
      contentTextColor: contentTextColor ?? this.contentTextColor,
      headlineTextStyle: headlineTextStyle ?? this.headlineTextStyle,
      actionTextStyle: actionTextStyle ?? this.actionTextStyle,
      contentHeadlineTextStyle:
          contentHeadlineTextStyle ?? this.contentHeadlineTextStyle,
      contentTextStyle: contentTextStyle ?? this.contentTextStyle,
      enterSpring: enterSpring ?? this.enterSpring,
      scrollSpring: scrollSpring ?? this.scrollSpring,
    );
  }

  /// Linear interpolation with [other]. Colors and styles lerp; layout and
  /// springs snap at the midpoint.
  M3EFullScreenDialogTheme lerp(M3EFullScreenDialogTheme? other, double t) {
    if (other == null) {
      return this;
    }
    final M3EFullScreenDialogTheme near = t < 0.5 ? this : other;
    return near.copyWith(
      cornerRadius: lerpDouble(cornerRadius, other.cornerRadius, t),
      headerScrolledElevation: lerpDouble(
        headerScrolledElevation,
        other.headerScrolledElevation,
        t,
      ),
      actionBarScrolledElevation: lerpDouble(
        actionBarScrolledElevation,
        other.actionBarScrolledElevation,
        t,
      ),
      containerColor: Color.lerp(containerColor, other.containerColor, t),
      onScrollContainerColor: Color.lerp(
        onScrollContainerColor,
        other.onScrollContainerColor,
        t,
      ),
      headerColor: Color.lerp(headerColor, other.headerColor, t),
      actionBarColor: Color.lerp(actionBarColor, other.actionBarColor, t),
      iconColor: Color.lerp(iconColor, other.iconColor, t),
      headlineColor: Color.lerp(headlineColor, other.headlineColor, t),
      dividerColor: Color.lerp(dividerColor, other.dividerColor, t),
      actionColor: Color.lerp(actionColor, other.actionColor, t),
      contentTextColor: Color.lerp(contentTextColor, other.contentTextColor, t),
    );
  }
}
