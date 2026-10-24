part of 'm3e_navigation_drawer_theme.dart';

/// Grouped field resolution for [M3ENavigationDrawerTheme.lerp], split out
/// so no single function grows past the component length guidelines.
typedef _DrawerGeometryLerp = ({
  double width,
  double destinationHeight,
  double headlineHorizontalPadding,
  double headlineVerticalPadding,
  double iconSize,
  double destinationHorizontalPadding,
  double destinationVerticalPadding,
  double destinationInnerHorizontalPadding,
  double iconLabelGap,
  double contentPadding,
  double sectionIndent,
  double dividerInset,
  double dividerSpacing,
  double dividerThickness,
  double endCornerRadius,
  double indicatorRadius,
});

typedef _DrawerInteractionLerp = ({
  double elevation,
  double scrimOpacity,
  double hoverOpacity,
  double focusOpacity,
  double pressedOpacity,
  double focusRingThickness,
  double focusRingInset,
  double dismissDragThreshold,
});

typedef _DrawerMotionLerp = ({
  FontWeight activeLabelWeight,
  FontWeight inactiveLabelWeight,
  M3ESpring indicatorScaleSpring,
  M3ESpring indicatorFadeSpring,
  M3ESpring revealSpring,
});

typedef _DrawerColorLerp = ({
  Color? standardContainerColor,
  Color? modalContainerColor,
  Color? headlineForegroundColor,
  Color? activeForegroundColor,
  Color? inactiveForegroundColor,
  Color? indicatorColor,
  Color? selectedBadgeColor,
  Color? unselectedBadgeColor,
  Color? activeStateLayerColor,
  Color? inactiveStateLayerColor,
  Color? pressedStateLayerColor,
  Color? focusRingColor,
  Color? dividerColor,
  Color? scrimColor,
});

double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;

M3ENavigationDrawerTheme _lerpNavigationDrawerTheme(
  M3ENavigationDrawerTheme a,
  M3ENavigationDrawerTheme b,
  double t,
) {
  final _DrawerGeometryLerp geometry = _lerpDrawerGeometry(a, b, t);
  final _DrawerInteractionLerp interaction = _lerpDrawerInteraction(a, b, t);
  final _DrawerMotionLerp motion = _lerpDrawerMotion(a, b, t);
  final _DrawerColorLerp colors = _lerpDrawerColors(a, b, t);
  return M3ENavigationDrawerTheme(
    width: geometry.width,
    destinationHeight: geometry.destinationHeight,
    headlineHorizontalPadding: geometry.headlineHorizontalPadding,
    headlineVerticalPadding: geometry.headlineVerticalPadding,
    iconSize: geometry.iconSize,
    destinationHorizontalPadding: geometry.destinationHorizontalPadding,
    destinationVerticalPadding: geometry.destinationVerticalPadding,
    destinationInnerHorizontalPadding:
        geometry.destinationInnerHorizontalPadding,
    iconLabelGap: geometry.iconLabelGap,
    contentPadding: geometry.contentPadding,
    sectionIndent: geometry.sectionIndent,
    dividerInset: geometry.dividerInset,
    dividerSpacing: geometry.dividerSpacing,
    dividerThickness: geometry.dividerThickness,
    endCornerRadius: geometry.endCornerRadius,
    indicatorRadius: geometry.indicatorRadius,
    elevation: interaction.elevation,
    scrimOpacity: interaction.scrimOpacity,
    hoverOpacity: interaction.hoverOpacity,
    focusOpacity: interaction.focusOpacity,
    pressedOpacity: interaction.pressedOpacity,
    focusRingThickness: interaction.focusRingThickness,
    focusRingInset: interaction.focusRingInset,
    dismissDragThreshold: interaction.dismissDragThreshold,
    activeLabelWeight: motion.activeLabelWeight,
    inactiveLabelWeight: motion.inactiveLabelWeight,
    indicatorScaleSpring: motion.indicatorScaleSpring,
    indicatorFadeSpring: motion.indicatorFadeSpring,
    revealSpring: motion.revealSpring,
    standardContainerColor: colors.standardContainerColor,
    modalContainerColor: colors.modalContainerColor,
    headlineForegroundColor: colors.headlineForegroundColor,
    activeForegroundColor: colors.activeForegroundColor,
    inactiveForegroundColor: colors.inactiveForegroundColor,
    indicatorColor: colors.indicatorColor,
    selectedBadgeColor: colors.selectedBadgeColor,
    unselectedBadgeColor: colors.unselectedBadgeColor,
    activeStateLayerColor: colors.activeStateLayerColor,
    inactiveStateLayerColor: colors.inactiveStateLayerColor,
    pressedStateLayerColor: colors.pressedStateLayerColor,
    focusRingColor: colors.focusRingColor,
    dividerColor: colors.dividerColor,
    scrimColor: colors.scrimColor,
  );
}

_DrawerGeometryLerp _lerpDrawerGeometry(
  M3ENavigationDrawerTheme a,
  M3ENavigationDrawerTheme b,
  double t,
) {
  return (
    width: _lerpDouble(a.width, b.width, t)!,
    destinationHeight: _lerpDouble(
      a.destinationHeight,
      b.destinationHeight,
      t,
    )!,
    headlineHorizontalPadding: _lerpDouble(
      a.headlineHorizontalPadding,
      b.headlineHorizontalPadding,
      t,
    )!,
    headlineVerticalPadding: _lerpDouble(
      a.headlineVerticalPadding,
      b.headlineVerticalPadding,
      t,
    )!,
    iconSize: _lerpDouble(a.iconSize, b.iconSize, t)!,
    destinationHorizontalPadding: _lerpDouble(
      a.destinationHorizontalPadding,
      b.destinationHorizontalPadding,
      t,
    )!,
    destinationVerticalPadding: _lerpDouble(
      a.destinationVerticalPadding,
      b.destinationVerticalPadding,
      t,
    )!,
    destinationInnerHorizontalPadding: _lerpDouble(
      a.destinationInnerHorizontalPadding,
      b.destinationInnerHorizontalPadding,
      t,
    )!,
    iconLabelGap: _lerpDouble(a.iconLabelGap, b.iconLabelGap, t)!,
    contentPadding: _lerpDouble(a.contentPadding, b.contentPadding, t)!,
    sectionIndent: _lerpDouble(a.sectionIndent, b.sectionIndent, t)!,
    dividerInset: _lerpDouble(a.dividerInset, b.dividerInset, t)!,
    dividerSpacing: _lerpDouble(a.dividerSpacing, b.dividerSpacing, t)!,
    dividerThickness: _lerpDouble(a.dividerThickness, b.dividerThickness, t)!,
    endCornerRadius: _lerpDouble(a.endCornerRadius, b.endCornerRadius, t)!,
    indicatorRadius: _lerpDouble(a.indicatorRadius, b.indicatorRadius, t)!,
  );
}

_DrawerInteractionLerp _lerpDrawerInteraction(
  M3ENavigationDrawerTheme a,
  M3ENavigationDrawerTheme b,
  double t,
) {
  return (
    elevation: _lerpDouble(a.elevation, b.elevation, t)!,
    scrimOpacity: _lerpDouble(a.scrimOpacity, b.scrimOpacity, t)!,
    hoverOpacity: _lerpDouble(a.hoverOpacity, b.hoverOpacity, t)!,
    focusOpacity: _lerpDouble(a.focusOpacity, b.focusOpacity, t)!,
    pressedOpacity: _lerpDouble(a.pressedOpacity, b.pressedOpacity, t)!,
    focusRingThickness: _lerpDouble(
      a.focusRingThickness,
      b.focusRingThickness,
      t,
    )!,
    focusRingInset: _lerpDouble(a.focusRingInset, b.focusRingInset, t)!,
    dismissDragThreshold: _lerpDouble(
      a.dismissDragThreshold,
      b.dismissDragThreshold,
      t,
    )!,
  );
}

_DrawerMotionLerp _lerpDrawerMotion(
  M3ENavigationDrawerTheme a,
  M3ENavigationDrawerTheme b,
  double t,
) {
  return (
    activeLabelWeight: t < 0.5 ? a.activeLabelWeight : b.activeLabelWeight,
    inactiveLabelWeight: t < 0.5
        ? a.inactiveLabelWeight
        : b.inactiveLabelWeight,
    indicatorScaleSpring: t < 0.5
        ? a.indicatorScaleSpring
        : b.indicatorScaleSpring,
    indicatorFadeSpring: t < 0.5
        ? a.indicatorFadeSpring
        : b.indicatorFadeSpring,
    revealSpring: t < 0.5 ? a.revealSpring : b.revealSpring,
  );
}

_DrawerColorLerp _lerpDrawerColors(
  M3ENavigationDrawerTheme a,
  M3ENavigationDrawerTheme b,
  double t,
) {
  return (
    standardContainerColor: Color.lerp(
      a.standardContainerColor,
      b.standardContainerColor,
      t,
    ),
    modalContainerColor: Color.lerp(
      a.modalContainerColor,
      b.modalContainerColor,
      t,
    ),
    headlineForegroundColor: Color.lerp(
      a.headlineForegroundColor,
      b.headlineForegroundColor,
      t,
    ),
    activeForegroundColor: Color.lerp(
      a.activeForegroundColor,
      b.activeForegroundColor,
      t,
    ),
    inactiveForegroundColor: Color.lerp(
      a.inactiveForegroundColor,
      b.inactiveForegroundColor,
      t,
    ),
    indicatorColor: Color.lerp(a.indicatorColor, b.indicatorColor, t),
    selectedBadgeColor: Color.lerp(
      a.selectedBadgeColor,
      b.selectedBadgeColor,
      t,
    ),
    unselectedBadgeColor: Color.lerp(
      a.unselectedBadgeColor,
      b.unselectedBadgeColor,
      t,
    ),
    activeStateLayerColor: Color.lerp(
      a.activeStateLayerColor,
      b.activeStateLayerColor,
      t,
    ),
    inactiveStateLayerColor: Color.lerp(
      a.inactiveStateLayerColor,
      b.inactiveStateLayerColor,
      t,
    ),
    pressedStateLayerColor: Color.lerp(
      a.pressedStateLayerColor,
      b.pressedStateLayerColor,
      t,
    ),
    focusRingColor: Color.lerp(a.focusRingColor, b.focusRingColor, t),
    dividerColor: Color.lerp(a.dividerColor, b.dividerColor, t),
    scrimColor: Color.lerp(a.scrimColor, b.scrimColor, t),
  );
}
