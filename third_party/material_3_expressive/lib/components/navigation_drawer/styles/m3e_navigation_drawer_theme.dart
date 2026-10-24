import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

part 'm3e_navigation_drawer_theme_lerp.dart';

/// Theme values for `M3ENavigationDrawer`.
@immutable
class M3ENavigationDrawerTheme
    extends M3EThemeExtension<M3ENavigationDrawerTheme> {
  /// M3ENavigationDrawerTheme.
  const M3ENavigationDrawerTheme({
    this.width = 360,
    this.destinationHeight = 56,
    this.headlineHorizontalPadding = 28,
    this.headlineVerticalPadding = 16,
    this.iconSize = 24,
    this.destinationHorizontalPadding = 12,
    this.destinationVerticalPadding = 0,
    this.destinationInnerHorizontalPadding = 16,
    this.iconLabelGap = 12,
    this.contentPadding = 28,
    this.sectionIndent = 12,
    this.dividerInset = 28,
    this.dividerSpacing = 16,
    this.dividerThickness = 1,
    this.endCornerRadius = 16,
    this.indicatorRadius = 28,
    this.elevation = 0,
    this.scrimOpacity = 0.32,
    this.hoverOpacity = 0.08,
    this.focusOpacity = 0.1,
    this.pressedOpacity = 0.1,
    this.focusRingThickness = 3,
    this.focusRingInset = 3,
    this.dismissDragThreshold = 0.5,
    this.activeLabelWeight = FontWeight.w700,
    this.inactiveLabelWeight = FontWeight.w500,
    this.indicatorScaleSpring = M3EMotion.expressiveSpatialDefault,
    this.indicatorFadeSpring = M3EMotion.effectsFast,
    this.revealSpring = M3EMotion.expressiveSpatialDefault,
    this.standardContainerColor,
    this.modalContainerColor,
    this.headlineForegroundColor,
    this.activeForegroundColor,
    this.inactiveForegroundColor,
    this.indicatorColor,
    this.selectedBadgeColor,
    this.unselectedBadgeColor,
    this.activeStateLayerColor,
    this.inactiveStateLayerColor,
    this.pressedStateLayerColor,
    this.focusRingColor,
    this.dividerColor,
    this.scrimColor,
  });

  /// defaults.

  static const M3ENavigationDrawerTheme defaults = M3ENavigationDrawerTheme();

  /// Drawer width. The sheet is this wide even while a slide clips it.
  final double width;

  /// Destination row and active indicator height.
  final double destinationHeight;

  /// Horizontal inset of the headline.
  final double headlineHorizontalPadding;

  /// Space above the headline and between the headline and the first item.
  final double headlineVerticalPadding;

  /// iconSize.
  final double iconSize;

  /// Inset from each drawer edge to the active indicator.
  final double destinationHorizontalPadding;

  /// Vertical space between destination rows.
  final double destinationVerticalPadding;

  /// Padding inside the indicator, from the pill edge to the icon.
  final double destinationInnerHorizontalPadding;

  /// Gap between the icon and the label.
  final double iconLabelGap;

  /// Horizontal inset for a section label.
  final double contentPadding;

  /// Extra start inset for destinations in a section after the first group.
  final double sectionIndent;

  /// Horizontal inset of a divider.
  final double dividerInset;

  /// Space above and below a divider.
  final double dividerSpacing;

  /// Divider thickness.
  final double dividerThickness;

  /// End-edge corner radius. Start corners stay square.
  final double endCornerRadius;

  /// Active indicator corner radius.
  final double indicatorRadius;

  /// Sheet elevation. Zero paints no shadow.
  final double elevation;

  /// Scrim opacity when a modal drawer is fully open.
  final double scrimOpacity;

  /// Hover state-layer opacity.
  final double hoverOpacity;

  /// Focus state-layer opacity.
  final double focusOpacity;

  /// Pressed state-layer opacity.
  final double pressedOpacity;

  /// Keyboard focus ring thickness.
  final double focusRingThickness;

  /// Inset of the focus ring from the indicator edge.
  final double focusRingInset;

  /// Fraction of the width a drag must pass to dismiss a modal drawer.
  final double dismissDragThreshold;

  /// Label weight for the selected destination.
  final FontWeight activeLabelWeight;

  /// Label and badge weight for an unselected destination.
  final FontWeight inactiveLabelWeight;

  /// Spatial spring for the selection indicator width scale.
  final M3ESpring indicatorScaleSpring;

  /// Effects spring for the selection indicator fade.
  final M3ESpring indicatorFadeSpring;

  /// Spatial spring for the enter and exit slide.
  final M3ESpring revealSpring;

  /// Override for the standard sheet color. Defaults to surface.
  final Color? standardContainerColor;

  /// Override for the modal sheet color. Defaults to surface container.
  final Color? modalContainerColor;

  /// Override for headline and section label color.
  final Color? headlineForegroundColor;

  /// Override for the selected icon and label.
  final Color? activeForegroundColor;

  /// Override for the unselected icon and label.
  final Color? inactiveForegroundColor;

  /// Override for the active indicator fill.
  final Color? indicatorColor;

  /// Override for a badge on the selected row.
  final Color? selectedBadgeColor;

  /// Override for a badge on an unselected row.
  final Color? unselectedBadgeColor;

  /// Override for hover and focus layers on the selected row.
  final Color? activeStateLayerColor;

  /// Override for hover and focus layers on an unselected row.
  final Color? inactiveStateLayerColor;

  /// Override for the pressed layer on every row.
  final Color? pressedStateLayerColor;

  /// Override for the keyboard focus ring.
  final Color? focusRingColor;

  /// Override for the divider.
  final Color? dividerColor;

  /// Override for the modal scrim.
  final Color? scrimColor;

  /// containerColor.

  Color containerColor(M3EColorScheme scheme) =>
      standardContainerColor ?? scheme.surface;

  /// Modal sheet color.
  Color resolveModalContainer(M3EColorScheme scheme) =>
      modalContainerColor ?? scheme.surfaceContainer;

  /// Headline and section label color.
  Color resolveHeadline(M3EColorScheme scheme) =>
      headlineForegroundColor ?? scheme.onSurfaceVariant;

  /// destinationForegroundColor.

  Color destinationForegroundColor(
    M3EColorScheme scheme, {
    required bool selected,
  }) => selected
      ? (activeForegroundColor ?? scheme.onSecondaryContainer)
      : (inactiveForegroundColor ?? scheme.onSurfaceVariant);

  /// Badge color. Selected badges use on secondary container.
  Color badgeColor(M3EColorScheme scheme, {required bool selected}) => selected
      ? (selectedBadgeColor ?? scheme.onSecondaryContainer)
      : (unselectedBadgeColor ?? scheme.onSurfaceVariant);

  /// destinationBackgroundColor.

  Color destinationBackgroundColor(
    M3EColorScheme scheme, {
    required bool selected,
  }) => selected
      ? (indicatorColor ?? scheme.secondaryContainer)
      : const Color(0x00000000);

  /// Hover, focus, or press layer for a destination.
  Color stateLayerColor(
    M3EColorScheme scheme, {
    required bool selected,
    required bool pressed,
    required bool focused,
  }) {
    if (pressed) {
      return (pressedStateLayerColor ?? scheme.onSecondaryContainer).withValues(
        alpha: pressedOpacity,
      );
    }
    final Color role = selected
        ? (activeStateLayerColor ?? scheme.onSecondaryContainer)
        : (inactiveStateLayerColor ?? scheme.onSurface);
    if (focused) {
      return role.withValues(alpha: focusOpacity);
    }
    return role.withValues(alpha: hoverOpacity);
  }

  /// Keyboard focus ring color.
  Color focusRingColorResolved(M3EColorScheme scheme) =>
      focusRingColor ?? scheme.secondary;

  /// Divider color.
  Color resolveDivider(M3EColorScheme scheme) => dividerColor ?? scheme.outline;

  /// Modal scrim color before opacity is applied.
  Color resolveScrim(M3EColorScheme scheme) => scrimColor ?? scheme.scrim;

  /// destinationShape.

  ShapeBorder destinationShape() {
    return RoundedRectangleBorder(
      borderRadius: M3EShapes.resolve(indicatorRadius),
    );
  }

  @override
  M3ENavigationDrawerTheme copyWith({
    double? width,
    double? destinationHeight,
    double? headlineHorizontalPadding,
    double? headlineVerticalPadding,
    double? iconSize,
    double? destinationHorizontalPadding,
    double? destinationVerticalPadding,
    double? destinationInnerHorizontalPadding,
    double? iconLabelGap,
    double? contentPadding,
    double? sectionIndent,
    double? dividerInset,
    double? dividerSpacing,
    double? dividerThickness,
    double? endCornerRadius,
    double? indicatorRadius,
    double? elevation,
    double? scrimOpacity,
    double? hoverOpacity,
    double? focusOpacity,
    double? pressedOpacity,
    double? focusRingThickness,
    double? focusRingInset,
    double? dismissDragThreshold,
    FontWeight? activeLabelWeight,
    FontWeight? inactiveLabelWeight,
    M3ESpring? indicatorScaleSpring,
    M3ESpring? indicatorFadeSpring,
    M3ESpring? revealSpring,
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
  }) {
    return M3ENavigationDrawerTheme(
      width: width ?? this.width,
      destinationHeight: destinationHeight ?? this.destinationHeight,
      headlineHorizontalPadding:
          headlineHorizontalPadding ?? this.headlineHorizontalPadding,
      headlineVerticalPadding:
          headlineVerticalPadding ?? this.headlineVerticalPadding,
      iconSize: iconSize ?? this.iconSize,
      destinationHorizontalPadding:
          destinationHorizontalPadding ?? this.destinationHorizontalPadding,
      destinationVerticalPadding:
          destinationVerticalPadding ?? this.destinationVerticalPadding,
      destinationInnerHorizontalPadding:
          destinationInnerHorizontalPadding ??
          this.destinationInnerHorizontalPadding,
      iconLabelGap: iconLabelGap ?? this.iconLabelGap,
      contentPadding: contentPadding ?? this.contentPadding,
      sectionIndent: sectionIndent ?? this.sectionIndent,
      dividerInset: dividerInset ?? this.dividerInset,
      dividerSpacing: dividerSpacing ?? this.dividerSpacing,
      dividerThickness: dividerThickness ?? this.dividerThickness,
      endCornerRadius: endCornerRadius ?? this.endCornerRadius,
      indicatorRadius: indicatorRadius ?? this.indicatorRadius,
      elevation: elevation ?? this.elevation,
      scrimOpacity: scrimOpacity ?? this.scrimOpacity,
      hoverOpacity: hoverOpacity ?? this.hoverOpacity,
      focusOpacity: focusOpacity ?? this.focusOpacity,
      pressedOpacity: pressedOpacity ?? this.pressedOpacity,
      focusRingThickness: focusRingThickness ?? this.focusRingThickness,
      focusRingInset: focusRingInset ?? this.focusRingInset,
      dismissDragThreshold: dismissDragThreshold ?? this.dismissDragThreshold,
      activeLabelWeight: activeLabelWeight ?? this.activeLabelWeight,
      inactiveLabelWeight: inactiveLabelWeight ?? this.inactiveLabelWeight,
      indicatorScaleSpring: indicatorScaleSpring ?? this.indicatorScaleSpring,
      indicatorFadeSpring: indicatorFadeSpring ?? this.indicatorFadeSpring,
      revealSpring: revealSpring ?? this.revealSpring,
      standardContainerColor:
          standardContainerColor ?? this.standardContainerColor,
      modalContainerColor: modalContainerColor ?? this.modalContainerColor,
      headlineForegroundColor:
          headlineForegroundColor ?? this.headlineForegroundColor,
      activeForegroundColor:
          activeForegroundColor ?? this.activeForegroundColor,
      inactiveForegroundColor:
          inactiveForegroundColor ?? this.inactiveForegroundColor,
      indicatorColor: indicatorColor ?? this.indicatorColor,
      selectedBadgeColor: selectedBadgeColor ?? this.selectedBadgeColor,
      unselectedBadgeColor: unselectedBadgeColor ?? this.unselectedBadgeColor,
      activeStateLayerColor:
          activeStateLayerColor ?? this.activeStateLayerColor,
      inactiveStateLayerColor:
          inactiveStateLayerColor ?? this.inactiveStateLayerColor,
      pressedStateLayerColor:
          pressedStateLayerColor ?? this.pressedStateLayerColor,
      focusRingColor: focusRingColor ?? this.focusRingColor,
      dividerColor: dividerColor ?? this.dividerColor,
      scrimColor: scrimColor ?? this.scrimColor,
    );
  }

  @override
  M3ENavigationDrawerTheme lerp(M3ENavigationDrawerTheme? other, double t) {
    if (other is! M3ENavigationDrawerTheme) {
      return this;
    }
    return _lerpNavigationDrawerTheme(this, other, t);
  }
}
