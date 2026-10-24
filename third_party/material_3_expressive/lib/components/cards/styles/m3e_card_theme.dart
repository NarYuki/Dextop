import 'dart:ui' show lerpDouble;

import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_card_variant.dart';

part 'm3e_card_theme_ops.dart';
part 'm3e_card_theme_resolve.dart';

/// Theme values for cards and card groups.
@immutable
class M3ECardTheme extends M3EThemeExtension<M3ECardTheme> {
  /// Creates card theme tokens.
  const M3ECardTheme({
    this.contentPadding = const EdgeInsets.all(16),
    this.radius = 12,
    this.gap = 8,
    this.maxGap = 8,
    this.iconSize = 24,
    this.outlineWidth = 1,
    this.focusThickness = 3,
    this.focusGap = 2,
    this.disabledOpacity = 0.38,
    this.disabledOutlineOpacity = 0.12,
    this.scrimOpacity = 0.32,
    this.swipeThreshold = 0.4,
    this.compactBreakpoint = 600,
    this.expandedBreakpoint = 840,
    this.columnMinWidth = 280,
    this.carouselCardWidth = 280,
    this.transformEndRadius = 0,
    this.transformContentFadeStart = 0.4,
    this.transformContentVisibleAt = 0.55,
    this.transformSpring = M3EMotion.expressiveSpatialDefault,
    this.contentOverlayPlate = false,
    this.elevatedElevation = M3EElevation.level1,
    this.elevatedHoverElevation = M3EElevation.level2,
    this.elevatedFocusElevation = M3EElevation.level1,
    this.elevatedPressedElevation = M3EElevation.level1,
    this.elevatedDraggedElevation = M3EElevation.level4,
    this.filledElevation = M3EElevation.level0,
    this.filledHoverElevation = M3EElevation.level1,
    this.filledFocusElevation = M3EElevation.level0,
    this.filledPressedElevation = M3EElevation.level0,
    this.filledDraggedElevation = M3EElevation.level3,
    this.outlinedElevation = M3EElevation.level0,
    this.outlinedHoverElevation = M3EElevation.level1,
    this.outlinedFocusElevation = M3EElevation.level0,
    this.outlinedPressedElevation = M3EElevation.level0,
    this.outlinedDraggedElevation = M3EElevation.level3,
    this.containerColor,
    this.elevatedContainerColor,
    this.filledContainerColor,
    this.outlinedContainerColor,
    this.disabledElevatedColor,
    this.disabledFilledColor,
    this.outlineColorOverride,
    this.disabledOutlineColor,
    this.focusedOutlineColor,
    this.iconColor,
    this.shadowColor,
    this.surfaceTint,
    this.focusColor,
    this.stateLayerColor,
    this.scrimColor,
    this.transformScrimColor,
    this.transformOpenColor,
    this.headlineColor,
    this.subheadColor,
    this.supportingColor,
    this.overlayPlateColor,
    this.maxHeight,
  });

  /// Package defaults.
  static const M3ECardTheme defaults = M3ECardTheme();

  /// Padding inside the container, 16 on every side.
  ///
  /// Media and an edge-to-edge divider sit outside this inset. A padding
  /// divider lines up with it on both sides.
  final EdgeInsets contentPadding;

  /// Corner radius for every state.
  final double radius;

  /// Space between cards in a collection.
  final double gap;

  /// Largest allowed collection gap. Raise this to allow a larger [gap].
  final double maxGap;

  /// Icon size drawn by card content.
  final double iconSize;

  /// Outlined stroke width.
  final double outlineWidth;

  /// Focus ring thickness.
  final double focusThickness;

  /// Gap between the card edge and the focus ring.
  final double focusGap;

  /// Disabled elevated and filled container opacity.
  final double disabledOpacity;

  /// Disabled outlined stroke opacity.
  final double disabledOutlineOpacity;

  /// Scrim opacity when text sits on media, and for the container transform.
  final double scrimOpacity;

  /// Fraction of the card width a swipe must travel.
  final double swipeThreshold;

  /// Width below which a group is one full-width column.
  final double compactBreakpoint;

  /// Width at which a group may use several columns or turn vertical.
  final double expandedBreakpoint;

  /// Narrowest column when fitting as many cards as the width allows.
  final double columnMinWidth;

  /// Width of a card in a horizontal row.
  final double carouselCardWidth;

  /// Corner radius of the open container-transform surface.
  final double transformEndRadius;

  /// Progress at which incoming transform content starts to fade in.
  final double transformContentFadeStart;

  /// Progress at which incoming transform content is shown.
  final double transformContentVisibleAt;

  /// Spring for the container transform.
  final M3ESpring transformSpring;

  /// When true, text on media sits on a plate instead of a scrim.
  final bool contentOverlayPlate;

  /// Resting elevation for an elevated card.
  final double elevatedElevation;

  /// Hover elevation for an elevated card.
  final double elevatedHoverElevation;

  /// Focus elevation for an elevated card.
  final double elevatedFocusElevation;

  /// Pressed elevation for an elevated card.
  final double elevatedPressedElevation;

  /// Dragged elevation for an elevated card.
  final double elevatedDraggedElevation;

  /// Resting elevation for a filled card.
  final double filledElevation;

  /// Hover elevation for a filled card.
  final double filledHoverElevation;

  /// Focus elevation for a filled card.
  final double filledFocusElevation;

  /// Pressed elevation for a filled card.
  final double filledPressedElevation;

  /// Dragged elevation for a filled card.
  final double filledDraggedElevation;

  /// Resting elevation for an outlined card.
  final double outlinedElevation;

  /// Hover elevation for an outlined card.
  final double outlinedHoverElevation;

  /// Focus elevation for an outlined card.
  final double outlinedFocusElevation;

  /// Pressed elevation for an outlined card.
  final double outlinedPressedElevation;

  /// Dragged elevation for an outlined card.
  final double outlinedDraggedElevation;

  /// Optional fill used for every variant.
  final Color? containerColor;

  /// Optional elevated fill.
  final Color? elevatedContainerColor;

  /// Optional filled fill.
  final Color? filledContainerColor;

  /// Optional outlined fill.
  final Color? outlinedContainerColor;

  /// Optional disabled elevated fill.
  final Color? disabledElevatedColor;

  /// Optional disabled filled fill.
  final Color? disabledFilledColor;

  /// Optional outline color.
  final Color? outlineColorOverride;

  /// Optional disabled outline color.
  final Color? disabledOutlineColor;

  /// Optional focused outline color.
  final Color? focusedOutlineColor;

  /// Optional icon color.
  final Color? iconColor;

  /// Optional shadow color.
  final Color? shadowColor;

  /// Elevation tint role. Stored only; it is not blended onto the fill.
  final Color? surfaceTint;

  /// Optional focus ring color.
  final Color? focusColor;

  /// Optional state-layer color.
  final Color? stateLayerColor;

  /// Optional media scrim color.
  final Color? scrimColor;

  /// Optional container-transform scrim color.
  final Color? transformScrimColor;

  /// Optional open container-transform fill.
  final Color? transformOpenColor;

  /// Optional headline color.
  final Color? headlineColor;

  /// Optional subhead color.
  final Color? subheadColor;

  /// Optional supporting-text color.
  final Color? supportingColor;

  /// Optional plate color behind text on media.
  final Color? overlayPlateColor;

  /// Cap for unexpanded content. Null means the card grows with its child.
  final double? maxHeight;

  @override
  M3ECardTheme copyWith({
    EdgeInsets? contentPadding,
    double? radius,
    double? gap,
    double? maxGap,
    double? iconSize,
    double? outlineWidth,
    double? focusThickness,
    double? focusGap,
    double? disabledOpacity,
    double? disabledOutlineOpacity,
    double? scrimOpacity,
    double? swipeThreshold,
    double? compactBreakpoint,
    double? expandedBreakpoint,
    double? columnMinWidth,
    double? carouselCardWidth,
    double? transformEndRadius,
    double? transformContentFadeStart,
    double? transformContentVisibleAt,
    M3ESpring? transformSpring,
    bool? contentOverlayPlate,
    double? elevatedElevation,
    double? elevatedHoverElevation,
    double? elevatedFocusElevation,
    double? elevatedPressedElevation,
    double? elevatedDraggedElevation,
    double? filledElevation,
    double? filledHoverElevation,
    double? filledFocusElevation,
    double? filledPressedElevation,
    double? filledDraggedElevation,
    double? outlinedElevation,
    double? outlinedHoverElevation,
    double? outlinedFocusElevation,
    double? outlinedPressedElevation,
    double? outlinedDraggedElevation,
    Color? containerColor,
    Color? elevatedContainerColor,
    Color? filledContainerColor,
    Color? outlinedContainerColor,
    Color? disabledElevatedColor,
    Color? disabledFilledColor,
    Color? outlineColorOverride,
    Color? disabledOutlineColor,
    Color? focusedOutlineColor,
    Color? iconColor,
    Color? shadowColor,
    Color? surfaceTint,
    Color? focusColor,
    Color? stateLayerColor,
    Color? scrimColor,
    Color? transformScrimColor,
    Color? transformOpenColor,
    Color? headlineColor,
    Color? subheadColor,
    Color? supportingColor,
    Color? overlayPlateColor,
    double? maxHeight,
    bool clearMaxHeight = false,
  }) => _cardCopyWith(
    this,
    contentPadding: contentPadding,
    radius: radius,
    gap: gap,
    maxGap: maxGap,
    iconSize: iconSize,
    outlineWidth: outlineWidth,
    focusThickness: focusThickness,
    focusGap: focusGap,
    disabledOpacity: disabledOpacity,
    disabledOutlineOpacity: disabledOutlineOpacity,
    scrimOpacity: scrimOpacity,
    swipeThreshold: swipeThreshold,
    compactBreakpoint: compactBreakpoint,
    expandedBreakpoint: expandedBreakpoint,
    columnMinWidth: columnMinWidth,
    carouselCardWidth: carouselCardWidth,
    transformEndRadius: transformEndRadius,
    transformContentFadeStart: transformContentFadeStart,
    transformContentVisibleAt: transformContentVisibleAt,
    transformSpring: transformSpring,
    contentOverlayPlate: contentOverlayPlate,
    elevatedElevation: elevatedElevation,
    elevatedHoverElevation: elevatedHoverElevation,
    elevatedFocusElevation: elevatedFocusElevation,
    elevatedPressedElevation: elevatedPressedElevation,
    elevatedDraggedElevation: elevatedDraggedElevation,
    filledElevation: filledElevation,
    filledHoverElevation: filledHoverElevation,
    filledFocusElevation: filledFocusElevation,
    filledPressedElevation: filledPressedElevation,
    filledDraggedElevation: filledDraggedElevation,
    outlinedElevation: outlinedElevation,
    outlinedHoverElevation: outlinedHoverElevation,
    outlinedFocusElevation: outlinedFocusElevation,
    outlinedPressedElevation: outlinedPressedElevation,
    outlinedDraggedElevation: outlinedDraggedElevation,
    containerColor: containerColor,
    elevatedContainerColor: elevatedContainerColor,
    filledContainerColor: filledContainerColor,
    outlinedContainerColor: outlinedContainerColor,
    disabledElevatedColor: disabledElevatedColor,
    disabledFilledColor: disabledFilledColor,
    outlineColorOverride: outlineColorOverride,
    disabledOutlineColor: disabledOutlineColor,
    focusedOutlineColor: focusedOutlineColor,
    iconColor: iconColor,
    shadowColor: shadowColor,
    surfaceTint: surfaceTint,
    focusColor: focusColor,
    stateLayerColor: stateLayerColor,
    scrimColor: scrimColor,
    transformScrimColor: transformScrimColor,
    transformOpenColor: transformOpenColor,
    headlineColor: headlineColor,
    subheadColor: subheadColor,
    supportingColor: supportingColor,
    overlayPlateColor: overlayPlateColor,
    maxHeight: maxHeight,
    clearMaxHeight: clearMaxHeight,
  );

  @override
  M3ECardTheme lerp(M3ECardTheme? other, double t) {
    if (other is! M3ECardTheme) {
      return this;
    }
    return _cardThemeLerp(this, other, t);
  }

  @override
  bool operator ==(Object other) =>
      other is M3ECardTheme && _cardThemeEquals(this, other);

  @override
  int get hashCode => _cardThemeHash(this);
}
