part of 'm3e_menu_theme.dart';

double _menuLerpDouble(double a, double b, double t) => a + (b - a) * t;

M3EMenuColors _menuThemeColors(
  M3EMenuTheme theme,
  M3EColorScheme scheme,
  M3EMenuColorStyle style,
) {
  switch (style) {
    case M3EMenuColorStyle.standard:
      return M3EMenuColors(
        container: theme.backgroundColor ?? scheme.surfaceContainerLow,
        content: scheme.onSurface,
        iconContent: scheme.onSurfaceVariant,
        supportingContent: scheme.onSurfaceVariant,
        selectedContainer: scheme.tertiaryContainer,
        selectedContent: scheme.onTertiaryContainer,
        stateLayer: scheme.onSurface,
        divider: scheme.outlineVariant,
      );
    case M3EMenuColorStyle.vibrant:
      return M3EMenuColors(
        container: theme.backgroundColor ?? scheme.tertiaryContainer,
        content: scheme.onTertiaryContainer,
        iconContent: scheme.onTertiaryContainer,
        supportingContent: scheme.onTertiaryContainer,
        selectedContainer: scheme.tertiary,
        selectedContent: scheme.onTertiary,
        stateLayer: scheme.onTertiaryContainer,
        divider: scheme.onTertiaryContainer.withValues(alpha: 0.24),
      );
  }
}

M3EMenuTheme _lerpMenuTheme(M3EMenuTheme a, M3EMenuTheme b, double t) {
  return _lerpMenuThemeMetrics(a, b, t).copyWith(
    containerRadius: _menuLerpDouble(a.containerRadius, b.containerRadius, t),
    containerBottomRadius: _menuLerpDouble(
      a.containerBottomRadius,
      b.containerBottomRadius,
      t,
    ),
    itemRadius: _menuLerpDouble(a.itemRadius, b.itemRadius, t),
    stateLayerInset: _menuLerpDouble(a.stateLayerInset, b.stateLayerInset, t),
    focusIndicatorWidth: _menuLerpDouble(
      a.focusIndicatorWidth,
      b.focusIndicatorWidth,
      t,
    ),
    focusIndicatorOffset: _menuLerpDouble(
      a.focusIndicatorOffset,
      b.focusIndicatorOffset,
      t,
    ),
    focusIndicatorColor: Color.lerp(
      a.focusIndicatorColor,
      b.focusIndicatorColor,
      t,
    ),
    backgroundColor: Color.lerp(a.backgroundColor, b.backgroundColor, t),
    openMotion: t < 0.5 ? a.openMotion : b.openMotion,
    closeMotion: t < 0.5 ? a.closeMotion : b.closeMotion,
    openInstantly: t < 0.5 ? a.openInstantly : b.openInstantly,
    itemGap: _menuLerpDouble(a.itemGap, b.itemGap, t),
    dividerThickness: _menuLerpDouble(
      a.dividerThickness,
      b.dividerThickness,
      t,
    ),
    dividerVerticalPadding: _menuLerpDouble(
      a.dividerVerticalPadding,
      b.dividerVerticalPadding,
      t,
    ),
  );
}

M3EMenuTheme _lerpMenuThemeMetrics(M3EMenuTheme a, M3EMenuTheme b, double t) {
  return M3EMenuTheme(
    minWidth: _menuLerpDouble(a.minWidth, b.minWidth, t),
    maxWidth: _menuLerpDouble(a.maxWidth, b.maxWidth, t),
    maxHeight: _menuLerpDouble(a.maxHeight, b.maxHeight, t),
    verticalPadding: _menuLerpDouble(a.verticalPadding, b.verticalPadding, t),
    contentHorizontalPadding: _menuLerpDouble(
      a.contentHorizontalPadding,
      b.contentHorizontalPadding,
      t,
    ),
    anchorOffset: _menuLerpDouble(a.anchorOffset, b.anchorOffset, t),
    entryHeight: _menuLerpDouble(a.entryHeight, b.entryHeight, t),
    entryHorizontalPadding: _menuLerpDouble(
      a.entryHorizontalPadding,
      b.entryHorizontalPadding,
      t,
    ),
    entryVerticalPadding: _menuLerpDouble(
      a.entryVerticalPadding,
      b.entryVerticalPadding,
      t,
    ),
    iconSize: _menuLerpDouble(a.iconSize, b.iconSize, t),
    iconGap: _menuLerpDouble(a.iconGap, b.iconGap, t),
    groupSpacing: _menuLerpDouble(a.groupSpacing, b.groupSpacing, t),
    sectionGap: _menuLerpDouble(a.sectionGap, b.sectionGap, t),
    groupLabelHorizontalPadding: _menuLerpDouble(
      a.groupLabelHorizontalPadding,
      b.groupLabelHorizontalPadding,
      t,
    ),
    groupLabelVerticalPadding: _menuLerpDouble(
      a.groupLabelVerticalPadding,
      b.groupLabelVerticalPadding,
      t,
    ),
    groupLabelHeight: _menuLerpDouble(
      a.groupLabelHeight,
      b.groupLabelHeight,
      t,
    ),
    elevation: _menuLerpDouble(a.elevation, b.elevation, t),
    disabledOpacity: _menuLerpDouble(a.disabledOpacity, b.disabledOpacity, t),
    scrimAlpha: _menuLerpDouble(a.scrimAlpha, b.scrimAlpha, t),
    screenEdgePadding: _menuLerpDouble(
      a.screenEdgePadding,
      b.screenEdgePadding,
      t,
    ),
  );
}
