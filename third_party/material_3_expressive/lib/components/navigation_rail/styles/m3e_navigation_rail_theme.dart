import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

part 'm3e_navigation_rail_theme_copy.dart';
part 'm3e_navigation_rail_theme_lerp.dart';
part 'm3e_navigation_rail_theme_resolve.dart';

/// Theme values for `M3ENavigationRail`.
@immutable
class M3ENavigationRailTheme extends M3EThemeExtension<M3ENavigationRailTheme> {
  /// M3ENavigationRailTheme.
  const M3ENavigationRailTheme({
    this.collapsedWidth = 96,
    this.narrowCollapsedWidth = 80,
    this.collapsedHorizontalPadding = 20,
    this.narrowHorizontalPadding = 12,
    this.expandedMinWidth = 220,
    this.expandedMaxWidth = 360,
    this.itemExpandedHeight = 56,
    this.itemCollapsedHeight = 64,
    this.shortItemHeight = 56,
    this.iconSize = 24,
    this.indicatorLeading = 16,
    this.indicatorTrailing = 16,
    this.expandedItemInset = 24,
    this.iconLabelGap = 8,
    this.verticalIconLabelGap = 4,
    this.itemVerticalGap = 4,
    this.expandedItemGap = 0,
    this.itemVerticalPadding = 6,
    this.topSpace = 44,
    this.destinationTopPadding = 12,
    this.expandedTrailingSpace = 20,
    this.headerMinSpace = 40,
    this.sectionHeaderSpacingTop = 12,
    this.sectionHeaderSpacingBottom = 8,
    this.verticalIndicatorWidth = 56,
    this.verticalIndicatorHeight = 32,
    this.verticalIndicatorRadius = 16,
    this.expandedIndicatorHeight = 56,
    this.expandedIndicatorRadius = 28,
    this.noLabelIndicatorSize = 56,
    this.containerColor,
    this.modalContainerColor,
    this.scrolledContainerColor,
    this.activeIndicatorColor,
    this.activeIconAndLabel,
    this.activeLabelColor,
    this.inactiveIconAndLabel,
    this.menuColor,
    this.badgeBackground,
    this.badgeLargeLabel,
    this.stateLayerColor,
    this.focusRingColor,
    this.dividerColor,
    this.indicatorShapeFull,
    this.indicatorFillsWidth = false,
    this.elevation = M3EElevation.level0,
    this.scrolledElevation = M3EElevation.level1,
    this.modalScrimOpacity = 0.32,
    this.containerRadius = 0,
    this.modalContainerRadius = 0,
    this.dividerThickness = 1,
    this.hoverOpacity = 0.08,
    this.focusOpacity = 0.1,
    this.pressedOpacity = 0.1,
    this.focusRingThickness = 3,
    this.focusRingInset = 3,
    this.labelFontSize = 12,
    this.labelLineHeight = 16,
    this.labelLetterSpacing = 0.1,
    this.inactiveLabelWeight = FontWeight.w500,
    this.activeLabelWeight = FontWeight.w700,
    this.scaledLabelMaxLines = 2,
    this.truncationTextScale = 2,
    this.indicatorLeadSpring = const M3ESpring(stiffness: 380, damping: 0.45),
    this.indicatorTrailSpring = const M3ESpring(stiffness: 380, damping: 0.55),
    this.indicatorScaleSpring = M3EMotion.expressiveSpatialDefault,
    this.indicatorFadeSpring = M3EMotion.effectsFast,
    this.iconScaleSpring = const M3ESpring(stiffness: 380, damping: 0.5),
    this.widthSpring = M3EMotion.expressiveSpatialDefault,
  });

  /// defaults.
  static const M3ENavigationRailTheme defaults = M3ENavigationRailTheme();

  /// Collapsed rail width.
  final double collapsedWidth;

  /// Narrow collapsed width. Not selected automatically.
  final double narrowCollapsedWidth;

  /// Side inset that centers the collapsed pill in [collapsedWidth].
  final double collapsedHorizontalPadding;

  /// Side inset for [narrowCollapsedWidth].
  final double narrowHorizontalPadding;

  /// expandedMinWidth.
  final double expandedMinWidth;

  /// expandedMaxWidth.
  final double expandedMaxWidth;

  /// Expanded destination target height.
  final double itemExpandedHeight;

  /// Collapsed destination height when a label is shown.
  final double itemCollapsedHeight;

  /// Collapsed destination height without a label.
  final double shortItemHeight;

  /// iconSize.
  final double iconSize;

  /// Leading inset inside an expanded pill.
  final double indicatorLeading;

  /// Trailing inset inside an expanded pill.
  final double indicatorTrailing;

  /// Inset from the expanded rail edge to the destination pill and the FAB.
  final double expandedItemInset;

  /// Gap between an expanded icon and its label.
  final double iconLabelGap;

  /// Gap between a collapsed icon and its label.
  final double verticalIconLabelGap;

  /// Gap between collapsed destinations.
  final double itemVerticalGap;

  /// Gap between expanded destinations.
  final double expandedItemGap;

  /// Vertical padding inside a collapsed destination.
  final double itemVerticalPadding;

  /// Space from the top of the rail to the menu.
  final double topSpace;

  /// Inner top padding of the destination list when a menu, FAB, or leading
  /// control is present. Omitted when those controls are absent.
  final double destinationTopPadding;

  /// Space below the last expanded destination.
  final double expandedTrailingSpace;

  /// headerMinSpace.
  final double headerMinSpace;

  /// sectionHeaderSpacingTop.
  final double sectionHeaderSpacingTop;

  /// sectionHeaderSpacingBottom.
  final double sectionHeaderSpacingBottom;

  /// Collapsed pill width.
  final double verticalIndicatorWidth;

  /// Collapsed pill height.
  final double verticalIndicatorHeight;

  /// Collapsed pill corner radius.
  final double verticalIndicatorRadius;

  /// Expanded pill height.
  final double expandedIndicatorHeight;

  /// Expanded pill corner radius.
  final double expandedIndicatorRadius;

  /// Circular indicator size when the destination has no label.
  final double noLabelIndicatorSize;

  /// containerColor.
  final Color? containerColor;

  /// Modal rail container color.
  final Color? modalContainerColor;

  /// Container color while horizontal body content is scrolled.
  final Color? scrolledContainerColor;

  /// activeIndicatorColor.
  final Color? activeIndicatorColor;

  /// Active icon color.
  final Color? activeIconAndLabel;

  /// Active label color.
  final Color? activeLabelColor;

  /// inactiveIconAndLabel.
  final Color? inactiveIconAndLabel;

  /// menuColor.
  final Color? menuColor;

  /// badgeBackground.
  final Color? badgeBackground;

  /// badgeLargeLabel.
  final Color? badgeLargeLabel;

  /// Hover, focus, and press layer color.
  final Color? stateLayerColor;

  /// Keyboard focus ring color.
  final Color? focusRingColor;

  /// Optional content-edge divider color.
  final Color? dividerColor;

  /// indicatorShapeFull.
  final ShapeBorder? indicatorShapeFull;

  /// When true, the expanded pill fills the destination instead of hugging it.
  final bool indicatorFillsWidth;

  /// Resting elevation.
  final double elevation;

  /// Elevation while horizontal content is scrolled under the rail.
  final double scrolledElevation;

  /// Modal scrim opacity.
  final double modalScrimOpacity;

  /// Resting container corner radius.
  final double containerRadius;

  /// Modal container corner radius.
  final double modalContainerRadius;

  /// Content-edge divider thickness.
  final double dividerThickness;

  /// Hover state-layer opacity.
  final double hoverOpacity;

  /// Focus state-layer opacity.
  final double focusOpacity;

  /// Press state-layer opacity.
  final double pressedOpacity;

  /// Focus ring thickness.
  final double focusRingThickness;

  /// Focus ring inset from the pill edge.
  final double focusRingInset;

  /// Label size.
  final double labelFontSize;

  /// Label line height.
  final double labelLineHeight;

  /// Label tracking.
  final double labelLetterSpacing;

  /// Inactive label weight.
  final FontWeight inactiveLabelWeight;

  /// Active label weight.
  final FontWeight activeLabelWeight;

  /// Maximum label lines while text scale is above 1 and at most 2.
  final int scaledLabelMaxLines;

  /// Text scale above which labels may ellipsize.
  final double truncationTextScale;

  /// Retained theme field. Selection no longer travels, so this spring is unused.
  final M3ESpring indicatorLeadSpring;

  /// Retained theme field. Selection no longer travels, so this spring is unused.
  final M3ESpring indicatorTrailSpring;

  /// Spatial spring for the selection indicator width scale.
  final M3ESpring indicatorScaleSpring;

  /// Effects spring for the selection indicator fade.
  final M3ESpring indicatorFadeSpring;

  /// Icon scale pop on newly selected items.
  final M3ESpring iconScaleSpring;

  /// Spring for rail width, show/hide, and modal travel.
  final M3ESpring widthSpring;

  @override
  M3ENavigationRailTheme copyWith({
    double? collapsedWidth,
    double? narrowCollapsedWidth,
    double? collapsedHorizontalPadding,
    double? narrowHorizontalPadding,
    double? expandedMinWidth,
    double? expandedMaxWidth,
    double? itemExpandedHeight,
    double? itemCollapsedHeight,
    double? itemHeight,
    double? itemShortHeight,
    double? shortItemHeight,
    double? iconSize,
    double? indicatorLeading,
    double? indicatorTrailing,
    double? expandedItemInset,
    double? iconLabelGap,
    double? verticalIconLabelGap,
    double? itemVerticalGap,
    double? expandedItemGap,
    double? itemVerticalPadding,
    double? topSpace,
    double? destinationTopPadding,
    double? expandedTrailingSpace,
    double? headerMinSpace,
    double? sectionHeaderSpacingTop,
    double? sectionHeaderSpacingBottom,
    double? verticalIndicatorWidth,
    double? verticalIndicatorHeight,
    double? verticalIndicatorRadius,
    double? expandedIndicatorHeight,
    double? expandedIndicatorRadius,
    double? noLabelIndicatorSize,
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
    bool? indicatorFillsWidth,
    double? elevation,
    double? scrolledElevation,
    double? modalScrimOpacity,
    double? containerRadius,
    double? modalContainerRadius,
    double? dividerThickness,
    double? hoverOpacity,
    double? focusOpacity,
    double? pressedOpacity,
    double? focusRingThickness,
    double? focusRingInset,
    double? labelFontSize,
    double? labelLineHeight,
    double? labelLetterSpacing,
    FontWeight? inactiveLabelWeight,
    FontWeight? activeLabelWeight,
    int? scaledLabelMaxLines,
    double? truncationTextScale,
    M3ESpring? indicatorLeadSpring,
    M3ESpring? indicatorTrailSpring,
    M3ESpring? indicatorScaleSpring,
    M3ESpring? indicatorFadeSpring,
    M3ESpring? iconScaleSpring,
    M3ESpring? widthSpring,
  }) {
    return _copyNavigationRailTheme(
      collapsedWidth: collapsedWidth,
      narrowCollapsedWidth: narrowCollapsedWidth,
      collapsedHorizontalPadding: collapsedHorizontalPadding,
      narrowHorizontalPadding: narrowHorizontalPadding,
      expandedMinWidth: expandedMinWidth,
      expandedMaxWidth: expandedMaxWidth,
      itemExpandedHeight: itemHeight ?? itemExpandedHeight,
      itemCollapsedHeight: itemShortHeight ?? itemCollapsedHeight,
      shortItemHeight: shortItemHeight,
      iconSize: iconSize,
      indicatorLeading: indicatorLeading,
      indicatorTrailing: indicatorTrailing,
      expandedItemInset: expandedItemInset,
      iconLabelGap: iconLabelGap,
      verticalIconLabelGap: verticalIconLabelGap,
      itemVerticalGap: itemVerticalGap,
      expandedItemGap: expandedItemGap,
      itemVerticalPadding: itemVerticalPadding,
      topSpace: topSpace,
      destinationTopPadding: destinationTopPadding,
      expandedTrailingSpace: expandedTrailingSpace,
      headerMinSpace: headerMinSpace,
      sectionHeaderSpacingTop: sectionHeaderSpacingTop,
      sectionHeaderSpacingBottom: sectionHeaderSpacingBottom,
      verticalIndicatorWidth: verticalIndicatorWidth,
      verticalIndicatorHeight: verticalIndicatorHeight,
      verticalIndicatorRadius: verticalIndicatorRadius,
      expandedIndicatorHeight: expandedIndicatorHeight,
      expandedIndicatorRadius: expandedIndicatorRadius,
      noLabelIndicatorSize: noLabelIndicatorSize,
      containerColor: containerColor,
      modalContainerColor: modalContainerColor,
      scrolledContainerColor: scrolledContainerColor,
      activeIndicatorColor: activeIndicatorColor,
      activeIconAndLabel: activeIconAndLabel,
      activeLabelColor: activeLabelColor,
      inactiveIconAndLabel: inactiveIconAndLabel,
      menuColor: menuColor,
      badgeBackground: badgeBackground,
      badgeLargeLabel: badgeLargeLabel,
      stateLayerColor: stateLayerColor,
      focusRingColor: focusRingColor,
      dividerColor: dividerColor,
      indicatorShapeFull: indicatorShapeFull,
      indicatorFillsWidth: indicatorFillsWidth,
      elevation: elevation,
      scrolledElevation: scrolledElevation,
      modalScrimOpacity: modalScrimOpacity,
      containerRadius: containerRadius,
      modalContainerRadius: modalContainerRadius,
      dividerThickness: dividerThickness,
      hoverOpacity: hoverOpacity,
      focusOpacity: focusOpacity,
      pressedOpacity: pressedOpacity,
      focusRingThickness: focusRingThickness,
      focusRingInset: focusRingInset,
      labelFontSize: labelFontSize,
      labelLineHeight: labelLineHeight,
      labelLetterSpacing: labelLetterSpacing,
      inactiveLabelWeight: inactiveLabelWeight,
      activeLabelWeight: activeLabelWeight,
      scaledLabelMaxLines: scaledLabelMaxLines,
      truncationTextScale: truncationTextScale,
      indicatorLeadSpring: indicatorLeadSpring,
      indicatorTrailSpring: indicatorTrailSpring,
      indicatorScaleSpring: indicatorScaleSpring,
      indicatorFadeSpring: indicatorFadeSpring,
      iconScaleSpring: iconScaleSpring,
      widthSpring: widthSpring,
    );
  }

  @override
  M3ENavigationRailTheme lerp(M3ENavigationRailTheme? other, double t) {
    if (other is! M3ENavigationRailTheme) {
      return this;
    }
    return _lerpNavigationRailTheme(other, t);
  }

  double _ld(double a, double b, double t) => a + (b - a) * t;
}
