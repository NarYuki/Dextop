part of 'm3e_navigation_rail_theme.dart';

/// Color and text-style resolution helpers for [M3ENavigationRailTheme],
/// split out to keep the theme class under the component length
/// guidelines. Public (not underscore-prefixed) because these methods are
/// called from other component files outside this library, and a
/// library-private extension is invisible across file/library boundaries.
extension M3ENavigationRailThemeColors on M3ENavigationRailTheme {
  /// Active icon color.
  Color activeIconColor(M3EColorScheme scheme) =>
      activeIconAndLabel ?? scheme.onSecondaryContainer;

  /// Active label color.
  Color activeLabelColorResolved(M3EColorScheme scheme) =>
      activeLabelColor ?? scheme.onSurface;

  /// Kept for callers that still ask for one active color.
  Color activeIconAndLabelColor(M3EColorScheme scheme) =>
      activeIconColor(scheme);

  /// inactiveIconAndLabelColor.
  Color inactiveIconAndLabelColor(M3EColorScheme scheme) =>
      inactiveIconAndLabel ?? scheme.onSurfaceVariant;

  /// activeIndicatorColorResolved.
  Color activeIndicatorColorResolved(M3EColorScheme scheme) =>
      activeIndicatorColor ?? scheme.secondaryContainer;

  /// containerColorResolved.
  Color containerColorResolved(M3EColorScheme scheme) =>
      containerColor ?? scheme.surface;

  /// Modal container color.
  Color modalContainerColorResolved(M3EColorScheme scheme) =>
      modalContainerColor ?? scheme.surfaceContainer;

  /// Scrolled container color.
  Color scrolledContainerColorResolved(M3EColorScheme scheme) =>
      scrolledContainerColor ?? scheme.surfaceContainer;

  /// State layer color.
  Color stateLayerColorResolved(M3EColorScheme scheme) =>
      stateLayerColor ?? scheme.onSecondaryContainer;

  /// Focus ring color.
  Color focusRingColorResolved(M3EColorScheme scheme) =>
      focusRingColor ?? scheme.secondary;

  /// Menu icon color.
  Color menuColorResolved(M3EColorScheme scheme) =>
      menuColor ?? scheme.onSurfaceVariant;

  /// Badge fill.
  Color badgeBackgroundResolved(M3EColorScheme scheme) =>
      badgeBackground ?? scheme.error;

  /// Badge label.
  Color badgeLabelResolved(M3EColorScheme scheme) =>
      badgeLargeLabel ?? scheme.onError;

  /// Divider color.
  Color dividerColorResolved(M3EColorScheme scheme) =>
      dividerColor ?? scheme.outlineVariant;

  /// Label style for [selected].
  TextStyle labelStyle(M3EColorScheme scheme, {required bool selected}) {
    return TextStyle(
      fontSize: labelFontSize,
      height: labelLineHeight / labelFontSize,
      letterSpacing: labelLetterSpacing,
      fontWeight: selected ? activeLabelWeight : inactiveLabelWeight,
      color: selected
          ? activeLabelColorResolved(scheme)
          : inactiveIconAndLabelColor(scheme),
    );
  }
}
