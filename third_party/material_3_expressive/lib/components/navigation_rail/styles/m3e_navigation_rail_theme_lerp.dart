part of 'm3e_navigation_rail_theme.dart';

typedef _RailStructureLerpA = ({
  double collapsedWidth,
  double narrowCollapsedWidth,
  double collapsedHorizontalPadding,
  double narrowHorizontalPadding,
  double expandedMinWidth,
  double expandedMaxWidth,
  double itemExpandedHeight,
  double itemCollapsedHeight,
  double shortItemHeight,
  double iconSize,
  double indicatorLeading,
  double indicatorTrailing,
  double expandedItemInset,
  double iconLabelGap,
  double verticalIconLabelGap,
});

typedef _RailStructureLerpB = ({
  double itemVerticalGap,
  double expandedItemGap,
  double itemVerticalPadding,
  double topSpace,
  double destinationTopPadding,
  double expandedTrailingSpace,
  double headerMinSpace,
  double sectionHeaderSpacingTop,
  double sectionHeaderSpacingBottom,
  double verticalIndicatorWidth,
  double verticalIndicatorHeight,
  double verticalIndicatorRadius,
  double expandedIndicatorHeight,
  double expandedIndicatorRadius,
  double noLabelIndicatorSize,
});

typedef _RailVisualLerpA = ({
  Color? containerColor,
  Color? modalContainerColor,
  Color? scrolledContainerColor,
  Color? activeIndicatorColor,
  Color? activeIconAndLabel,
  Color? activeLabelColor,
  Color? inactiveIconAndLabel,
  Color? menuColor,
  Color? badgeBackground,
  Color? badgeLargeLabel,
  Color? stateLayerColor,
  Color? focusRingColor,
  Color? dividerColor,
  ShapeBorder? indicatorShapeFull,
  bool indicatorFillsWidth,
  double elevation,
  double scrolledElevation,
  double modalScrimOpacity,
  double containerRadius,
  double modalContainerRadius,
});

typedef _RailVisualLerpB = ({
  double dividerThickness,
  double hoverOpacity,
  double focusOpacity,
  double pressedOpacity,
  double focusRingThickness,
  double focusRingInset,
  double labelFontSize,
  double labelLineHeight,
  double labelLetterSpacing,
  FontWeight inactiveLabelWeight,
  FontWeight activeLabelWeight,
  int scaledLabelMaxLines,
  double truncationTextScale,
  M3ESpring indicatorLeadSpring,
  M3ESpring indicatorTrailSpring,
  M3ESpring indicatorScaleSpring,
  M3ESpring indicatorFadeSpring,
  M3ESpring iconScaleSpring,
  M3ESpring widthSpring,
});

/// Extension holding the private helpers for
/// [M3ENavigationRailTheme.lerp]. Reuses the [_RailStructureOverrides],
/// [_RailVisualOverrides] record types and the `_assembleRailTheme` merge
/// step defined alongside `copyWith`'s helpers.
extension _M3ENavigationRailThemeLerp on M3ENavigationRailTheme {
  M3ENavigationRailTheme _lerpNavigationRailTheme(
    M3ENavigationRailTheme other,
    double t,
  ) {
    final _RailStructureOverrides structure = _lerpRailStructure(other, t);
    final _RailVisualOverrides visuals = _lerpRailVisuals(other, t);
    return _assembleRailTheme(structure, visuals);
  }

  _RailStructureOverrides _lerpRailStructure(
    M3ENavigationRailTheme other,
    double t,
  ) {
    final _RailStructureLerpA a = _lerpRailStructureA(other, t);
    final _RailStructureLerpB b = _lerpRailStructureB(other, t);
    return (
      collapsedWidth: a.collapsedWidth,
      narrowCollapsedWidth: a.narrowCollapsedWidth,
      collapsedHorizontalPadding: a.collapsedHorizontalPadding,
      narrowHorizontalPadding: a.narrowHorizontalPadding,
      expandedMinWidth: a.expandedMinWidth,
      expandedMaxWidth: a.expandedMaxWidth,
      itemExpandedHeight: a.itemExpandedHeight,
      itemCollapsedHeight: a.itemCollapsedHeight,
      shortItemHeight: a.shortItemHeight,
      iconSize: a.iconSize,
      indicatorLeading: a.indicatorLeading,
      indicatorTrailing: a.indicatorTrailing,
      expandedItemInset: a.expandedItemInset,
      iconLabelGap: a.iconLabelGap,
      verticalIconLabelGap: a.verticalIconLabelGap,
      itemVerticalGap: b.itemVerticalGap,
      expandedItemGap: b.expandedItemGap,
      itemVerticalPadding: b.itemVerticalPadding,
      topSpace: b.topSpace,
      destinationTopPadding: b.destinationTopPadding,
      expandedTrailingSpace: b.expandedTrailingSpace,
      headerMinSpace: b.headerMinSpace,
      sectionHeaderSpacingTop: b.sectionHeaderSpacingTop,
      sectionHeaderSpacingBottom: b.sectionHeaderSpacingBottom,
      verticalIndicatorWidth: b.verticalIndicatorWidth,
      verticalIndicatorHeight: b.verticalIndicatorHeight,
      verticalIndicatorRadius: b.verticalIndicatorRadius,
      expandedIndicatorHeight: b.expandedIndicatorHeight,
      expandedIndicatorRadius: b.expandedIndicatorRadius,
      noLabelIndicatorSize: b.noLabelIndicatorSize,
    );
  }

  _RailStructureLerpA _lerpRailStructureA(
    M3ENavigationRailTheme other,
    double t,
  ) {
    return (
      collapsedWidth: _ld(collapsedWidth, other.collapsedWidth, t),
      narrowCollapsedWidth: _ld(
        narrowCollapsedWidth,
        other.narrowCollapsedWidth,
        t,
      ),
      collapsedHorizontalPadding: _ld(
        collapsedHorizontalPadding,
        other.collapsedHorizontalPadding,
        t,
      ),
      narrowHorizontalPadding: _ld(
        narrowHorizontalPadding,
        other.narrowHorizontalPadding,
        t,
      ),
      expandedMinWidth: _ld(expandedMinWidth, other.expandedMinWidth, t),
      expandedMaxWidth: _ld(expandedMaxWidth, other.expandedMaxWidth, t),
      itemExpandedHeight: _ld(itemExpandedHeight, other.itemExpandedHeight, t),
      itemCollapsedHeight: _ld(
        itemCollapsedHeight,
        other.itemCollapsedHeight,
        t,
      ),
      shortItemHeight: _ld(shortItemHeight, other.shortItemHeight, t),
      iconSize: _ld(iconSize, other.iconSize, t),
      indicatorLeading: _ld(indicatorLeading, other.indicatorLeading, t),
      indicatorTrailing: _ld(indicatorTrailing, other.indicatorTrailing, t),
      expandedItemInset: _ld(expandedItemInset, other.expandedItemInset, t),
      iconLabelGap: _ld(iconLabelGap, other.iconLabelGap, t),
      verticalIconLabelGap: _ld(
        verticalIconLabelGap,
        other.verticalIconLabelGap,
        t,
      ),
    );
  }

  _RailStructureLerpB _lerpRailStructureB(
    M3ENavigationRailTheme other,
    double t,
  ) {
    return (
      itemVerticalGap: _ld(itemVerticalGap, other.itemVerticalGap, t),
      expandedItemGap: _ld(expandedItemGap, other.expandedItemGap, t),
      itemVerticalPadding: _ld(
        itemVerticalPadding,
        other.itemVerticalPadding,
        t,
      ),
      topSpace: _ld(topSpace, other.topSpace, t),
      destinationTopPadding: _ld(
        destinationTopPadding,
        other.destinationTopPadding,
        t,
      ),
      expandedTrailingSpace: _ld(
        expandedTrailingSpace,
        other.expandedTrailingSpace,
        t,
      ),
      headerMinSpace: _ld(headerMinSpace, other.headerMinSpace, t),
      sectionHeaderSpacingTop: _ld(
        sectionHeaderSpacingTop,
        other.sectionHeaderSpacingTop,
        t,
      ),
      sectionHeaderSpacingBottom: _ld(
        sectionHeaderSpacingBottom,
        other.sectionHeaderSpacingBottom,
        t,
      ),
      verticalIndicatorWidth: _ld(
        verticalIndicatorWidth,
        other.verticalIndicatorWidth,
        t,
      ),
      verticalIndicatorHeight: _ld(
        verticalIndicatorHeight,
        other.verticalIndicatorHeight,
        t,
      ),
      verticalIndicatorRadius: _ld(
        verticalIndicatorRadius,
        other.verticalIndicatorRadius,
        t,
      ),
      expandedIndicatorHeight: _ld(
        expandedIndicatorHeight,
        other.expandedIndicatorHeight,
        t,
      ),
      expandedIndicatorRadius: _ld(
        expandedIndicatorRadius,
        other.expandedIndicatorRadius,
        t,
      ),
      noLabelIndicatorSize: _ld(
        noLabelIndicatorSize,
        other.noLabelIndicatorSize,
        t,
      ),
    );
  }

  _RailVisualOverrides _lerpRailVisuals(
    M3ENavigationRailTheme other,
    double t,
  ) {
    final _RailVisualLerpA a = _lerpRailVisualsA(other, t);
    final _RailVisualLerpB b = _lerpRailVisualsB(other, t);
    return (
      containerColor: a.containerColor,
      modalContainerColor: a.modalContainerColor,
      scrolledContainerColor: a.scrolledContainerColor,
      activeIndicatorColor: a.activeIndicatorColor,
      activeIconAndLabel: a.activeIconAndLabel,
      activeLabelColor: a.activeLabelColor,
      inactiveIconAndLabel: a.inactiveIconAndLabel,
      menuColor: a.menuColor,
      badgeBackground: a.badgeBackground,
      badgeLargeLabel: a.badgeLargeLabel,
      stateLayerColor: a.stateLayerColor,
      focusRingColor: a.focusRingColor,
      dividerColor: a.dividerColor,
      indicatorShapeFull: a.indicatorShapeFull,
      indicatorFillsWidth: a.indicatorFillsWidth,
      elevation: a.elevation,
      scrolledElevation: a.scrolledElevation,
      modalScrimOpacity: a.modalScrimOpacity,
      containerRadius: a.containerRadius,
      modalContainerRadius: a.modalContainerRadius,
      dividerThickness: b.dividerThickness,
      hoverOpacity: b.hoverOpacity,
      focusOpacity: b.focusOpacity,
      pressedOpacity: b.pressedOpacity,
      focusRingThickness: b.focusRingThickness,
      focusRingInset: b.focusRingInset,
      labelFontSize: b.labelFontSize,
      labelLineHeight: b.labelLineHeight,
      labelLetterSpacing: b.labelLetterSpacing,
      inactiveLabelWeight: b.inactiveLabelWeight,
      activeLabelWeight: b.activeLabelWeight,
      scaledLabelMaxLines: b.scaledLabelMaxLines,
      truncationTextScale: b.truncationTextScale,
      indicatorLeadSpring: b.indicatorLeadSpring,
      indicatorTrailSpring: b.indicatorTrailSpring,
      indicatorScaleSpring: b.indicatorScaleSpring,
      indicatorFadeSpring: b.indicatorFadeSpring,
      iconScaleSpring: b.iconScaleSpring,
      widthSpring: b.widthSpring,
    );
  }

  _RailVisualLerpA _lerpRailVisualsA(M3ENavigationRailTheme other, double t) {
    return (
      containerColor: Color.lerp(containerColor, other.containerColor, t),
      modalContainerColor: Color.lerp(
        modalContainerColor,
        other.modalContainerColor,
        t,
      ),
      scrolledContainerColor: Color.lerp(
        scrolledContainerColor,
        other.scrolledContainerColor,
        t,
      ),
      activeIndicatorColor: Color.lerp(
        activeIndicatorColor,
        other.activeIndicatorColor,
        t,
      ),
      activeIconAndLabel: Color.lerp(
        activeIconAndLabel,
        other.activeIconAndLabel,
        t,
      ),
      activeLabelColor: Color.lerp(activeLabelColor, other.activeLabelColor, t),
      inactiveIconAndLabel: Color.lerp(
        inactiveIconAndLabel,
        other.inactiveIconAndLabel,
        t,
      ),
      menuColor: Color.lerp(menuColor, other.menuColor, t),
      badgeBackground: Color.lerp(badgeBackground, other.badgeBackground, t),
      badgeLargeLabel: Color.lerp(badgeLargeLabel, other.badgeLargeLabel, t),
      stateLayerColor: Color.lerp(stateLayerColor, other.stateLayerColor, t),
      focusRingColor: Color.lerp(focusRingColor, other.focusRingColor, t),
      dividerColor: Color.lerp(dividerColor, other.dividerColor, t),
      indicatorShapeFull: ShapeBorder.lerp(
        indicatorShapeFull,
        other.indicatorShapeFull,
        t,
      ),
      indicatorFillsWidth: t < 0.5
          ? indicatorFillsWidth
          : other.indicatorFillsWidth,
      elevation: _ld(elevation, other.elevation, t),
      scrolledElevation: _ld(scrolledElevation, other.scrolledElevation, t),
      modalScrimOpacity: _ld(modalScrimOpacity, other.modalScrimOpacity, t),
      containerRadius: _ld(containerRadius, other.containerRadius, t),
      modalContainerRadius: _ld(
        modalContainerRadius,
        other.modalContainerRadius,
        t,
      ),
    );
  }

  _RailVisualLerpB _lerpRailVisualsB(M3ENavigationRailTheme other, double t) {
    return (
      dividerThickness: _ld(dividerThickness, other.dividerThickness, t),
      hoverOpacity: _ld(hoverOpacity, other.hoverOpacity, t),
      focusOpacity: _ld(focusOpacity, other.focusOpacity, t),
      pressedOpacity: _ld(pressedOpacity, other.pressedOpacity, t),
      focusRingThickness: _ld(focusRingThickness, other.focusRingThickness, t),
      focusRingInset: _ld(focusRingInset, other.focusRingInset, t),
      labelFontSize: _ld(labelFontSize, other.labelFontSize, t),
      labelLineHeight: _ld(labelLineHeight, other.labelLineHeight, t),
      labelLetterSpacing: _ld(labelLetterSpacing, other.labelLetterSpacing, t),
      inactiveLabelWeight: t < 0.5
          ? inactiveLabelWeight
          : other.inactiveLabelWeight,
      activeLabelWeight: t < 0.5 ? activeLabelWeight : other.activeLabelWeight,
      scaledLabelMaxLines: t < 0.5
          ? scaledLabelMaxLines
          : other.scaledLabelMaxLines,
      truncationTextScale: _ld(
        truncationTextScale,
        other.truncationTextScale,
        t,
      ),
      indicatorLeadSpring: t < 0.5
          ? indicatorLeadSpring
          : other.indicatorLeadSpring,
      indicatorTrailSpring: t < 0.5
          ? indicatorTrailSpring
          : other.indicatorTrailSpring,
      indicatorScaleSpring: t < 0.5
          ? indicatorScaleSpring
          : other.indicatorScaleSpring,
      indicatorFadeSpring: t < 0.5
          ? indicatorFadeSpring
          : other.indicatorFadeSpring,
      iconScaleSpring: t < 0.5 ? iconScaleSpring : other.iconScaleSpring,
      widthSpring: t < 0.5 ? widthSpring : other.widthSpring,
    );
  }
}
