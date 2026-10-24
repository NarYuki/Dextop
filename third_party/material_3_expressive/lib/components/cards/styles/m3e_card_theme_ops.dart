part of 'm3e_card_theme.dart';

/// Field-by-field resolution for [M3ECardTheme.copyWith], `lerp`, `==`, and
/// `hashCode`.
///
/// These are plain top-level functions (not class members) so the override
/// methods on [M3ECardTheme] itself can stay short, thin wrappers.
double _cardLerpDouble(double a, double b, double t) => a + (b - a) * t;

M3ECardTheme _cardCopyWith(
  M3ECardTheme s, {
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
}) {
  return M3ECardTheme(
    contentPadding: contentPadding ?? s.contentPadding,
    radius: radius ?? s.radius,
    gap: gap ?? s.gap,
    maxGap: maxGap ?? s.maxGap,
    iconSize: iconSize ?? s.iconSize,
    outlineWidth: outlineWidth ?? s.outlineWidth,
    focusThickness: focusThickness ?? s.focusThickness,
    focusGap: focusGap ?? s.focusGap,
    disabledOpacity: disabledOpacity ?? s.disabledOpacity,
    disabledOutlineOpacity: disabledOutlineOpacity ?? s.disabledOutlineOpacity,
    scrimOpacity: scrimOpacity ?? s.scrimOpacity,
    swipeThreshold: swipeThreshold ?? s.swipeThreshold,
    compactBreakpoint: compactBreakpoint ?? s.compactBreakpoint,
    expandedBreakpoint: expandedBreakpoint ?? s.expandedBreakpoint,
    columnMinWidth: columnMinWidth ?? s.columnMinWidth,
    carouselCardWidth: carouselCardWidth ?? s.carouselCardWidth,
    transformEndRadius: transformEndRadius ?? s.transformEndRadius,
    transformContentFadeStart:
        transformContentFadeStart ?? s.transformContentFadeStart,
    transformContentVisibleAt:
        transformContentVisibleAt ?? s.transformContentVisibleAt,
    transformSpring: transformSpring ?? s.transformSpring,
    contentOverlayPlate: contentOverlayPlate ?? s.contentOverlayPlate,
    elevatedElevation: elevatedElevation ?? s.elevatedElevation,
    elevatedHoverElevation: elevatedHoverElevation ?? s.elevatedHoverElevation,
    elevatedFocusElevation: elevatedFocusElevation ?? s.elevatedFocusElevation,
    elevatedPressedElevation:
        elevatedPressedElevation ?? s.elevatedPressedElevation,
    elevatedDraggedElevation:
        elevatedDraggedElevation ?? s.elevatedDraggedElevation,
    filledElevation: filledElevation ?? s.filledElevation,
    filledHoverElevation: filledHoverElevation ?? s.filledHoverElevation,
    filledFocusElevation: filledFocusElevation ?? s.filledFocusElevation,
    filledPressedElevation: filledPressedElevation ?? s.filledPressedElevation,
    filledDraggedElevation: filledDraggedElevation ?? s.filledDraggedElevation,
    outlinedElevation: outlinedElevation ?? s.outlinedElevation,
    outlinedHoverElevation: outlinedHoverElevation ?? s.outlinedHoverElevation,
    outlinedFocusElevation: outlinedFocusElevation ?? s.outlinedFocusElevation,
    outlinedPressedElevation:
        outlinedPressedElevation ?? s.outlinedPressedElevation,
    outlinedDraggedElevation:
        outlinedDraggedElevation ?? s.outlinedDraggedElevation,
    containerColor: containerColor ?? s.containerColor,
    elevatedContainerColor: elevatedContainerColor ?? s.elevatedContainerColor,
    filledContainerColor: filledContainerColor ?? s.filledContainerColor,
    outlinedContainerColor: outlinedContainerColor ?? s.outlinedContainerColor,
    disabledElevatedColor: disabledElevatedColor ?? s.disabledElevatedColor,
    disabledFilledColor: disabledFilledColor ?? s.disabledFilledColor,
    outlineColorOverride: outlineColorOverride ?? s.outlineColorOverride,
    disabledOutlineColor: disabledOutlineColor ?? s.disabledOutlineColor,
    focusedOutlineColor: focusedOutlineColor ?? s.focusedOutlineColor,
    iconColor: iconColor ?? s.iconColor,
    shadowColor: shadowColor ?? s.shadowColor,
    surfaceTint: surfaceTint ?? s.surfaceTint,
    focusColor: focusColor ?? s.focusColor,
    stateLayerColor: stateLayerColor ?? s.stateLayerColor,
    scrimColor: scrimColor ?? s.scrimColor,
    transformScrimColor: transformScrimColor ?? s.transformScrimColor,
    transformOpenColor: transformOpenColor ?? s.transformOpenColor,
    headlineColor: headlineColor ?? s.headlineColor,
    subheadColor: subheadColor ?? s.subheadColor,
    supportingColor: supportingColor ?? s.supportingColor,
    overlayPlateColor: overlayPlateColor ?? s.overlayPlateColor,
    maxHeight: clearMaxHeight ? null : (maxHeight ?? s.maxHeight),
  );
}

/// Interpolates [M3ECardTheme] field-by-field, delegating each concern to
/// its own small helper (below) so no single function enumerates all 58
/// fields at once.
M3ECardTheme _cardThemeLerp(M3ECardTheme a, M3ECardTheme b, double t) {
  final _CardLayoutLerp layout = _cardLerpLayout(a, b, t);
  final _CardElevationLerp elevation = _cardLerpElevation(a, b, t);
  final _CardColorLerp colors = _cardLerpColors(a, b, t);
  final _CardTransformLerp transform = _cardLerpTransform(a, b, t);
  return M3ECardTheme(
    contentPadding: layout.contentPadding,
    radius: layout.radius,
    gap: layout.gap,
    maxGap: layout.maxGap,
    iconSize: layout.iconSize,
    outlineWidth: layout.outlineWidth,
    focusThickness: layout.focusThickness,
    focusGap: layout.focusGap,
    disabledOpacity: layout.disabledOpacity,
    disabledOutlineOpacity: layout.disabledOutlineOpacity,
    scrimOpacity: layout.scrimOpacity,
    swipeThreshold: layout.swipeThreshold,
    compactBreakpoint: layout.compactBreakpoint,
    expandedBreakpoint: layout.expandedBreakpoint,
    columnMinWidth: layout.columnMinWidth,
    carouselCardWidth: layout.carouselCardWidth,
    contentOverlayPlate: layout.contentOverlayPlate,
    maxHeight: layout.maxHeight,
    transformEndRadius: transform.transformEndRadius,
    transformContentFadeStart: transform.transformContentFadeStart,
    transformContentVisibleAt: transform.transformContentVisibleAt,
    transformSpring: transform.transformSpring,
    transformScrimColor: transform.transformScrimColor,
    transformOpenColor: transform.transformOpenColor,
    elevatedElevation: elevation.elevatedElevation,
    elevatedHoverElevation: elevation.elevatedHoverElevation,
    elevatedFocusElevation: elevation.elevatedFocusElevation,
    elevatedPressedElevation: elevation.elevatedPressedElevation,
    elevatedDraggedElevation: elevation.elevatedDraggedElevation,
    filledElevation: elevation.filledElevation,
    filledHoverElevation: elevation.filledHoverElevation,
    filledFocusElevation: elevation.filledFocusElevation,
    filledPressedElevation: elevation.filledPressedElevation,
    filledDraggedElevation: elevation.filledDraggedElevation,
    outlinedElevation: elevation.outlinedElevation,
    outlinedHoverElevation: elevation.outlinedHoverElevation,
    outlinedFocusElevation: elevation.outlinedFocusElevation,
    outlinedPressedElevation: elevation.outlinedPressedElevation,
    outlinedDraggedElevation: elevation.outlinedDraggedElevation,
    containerColor: colors.containerColor,
    elevatedContainerColor: colors.elevatedContainerColor,
    filledContainerColor: colors.filledContainerColor,
    outlinedContainerColor: colors.outlinedContainerColor,
    disabledElevatedColor: colors.disabledElevatedColor,
    disabledFilledColor: colors.disabledFilledColor,
    outlineColorOverride: colors.outlineColorOverride,
    disabledOutlineColor: colors.disabledOutlineColor,
    focusedOutlineColor: colors.focusedOutlineColor,
    iconColor: colors.iconColor,
    shadowColor: colors.shadowColor,
    surfaceTint: colors.surfaceTint,
    focusColor: colors.focusColor,
    stateLayerColor: colors.stateLayerColor,
    scrimColor: colors.scrimColor,
    headlineColor: colors.headlineColor,
    subheadColor: colors.subheadColor,
    supportingColor: colors.supportingColor,
    overlayPlateColor: colors.overlayPlateColor,
  );
}

/// Layout, sizing, and behavior tokens lerped as one group.
typedef _CardLayoutLerp = ({
  EdgeInsets contentPadding,
  double radius,
  double gap,
  double maxGap,
  double iconSize,
  double outlineWidth,
  double focusThickness,
  double focusGap,
  double disabledOpacity,
  double disabledOutlineOpacity,
  double scrimOpacity,
  double swipeThreshold,
  double compactBreakpoint,
  double expandedBreakpoint,
  double columnMinWidth,
  double carouselCardWidth,
  bool contentOverlayPlate,
  double? maxHeight,
});

_CardLayoutLerp _cardLerpLayout(M3ECardTheme a, M3ECardTheme b, double t) {
  return (
    contentPadding: EdgeInsets.lerp(a.contentPadding, b.contentPadding, t)!,
    radius: _cardLerpDouble(a.radius, b.radius, t),
    gap: _cardLerpDouble(a.gap, b.gap, t),
    maxGap: _cardLerpDouble(a.maxGap, b.maxGap, t),
    iconSize: _cardLerpDouble(a.iconSize, b.iconSize, t),
    outlineWidth: _cardLerpDouble(a.outlineWidth, b.outlineWidth, t),
    focusThickness: _cardLerpDouble(a.focusThickness, b.focusThickness, t),
    focusGap: _cardLerpDouble(a.focusGap, b.focusGap, t),
    disabledOpacity: _cardLerpDouble(a.disabledOpacity, b.disabledOpacity, t),
    disabledOutlineOpacity: _cardLerpDouble(
      a.disabledOutlineOpacity,
      b.disabledOutlineOpacity,
      t,
    ),
    scrimOpacity: _cardLerpDouble(a.scrimOpacity, b.scrimOpacity, t),
    swipeThreshold: _cardLerpDouble(a.swipeThreshold, b.swipeThreshold, t),
    compactBreakpoint: _cardLerpDouble(
      a.compactBreakpoint,
      b.compactBreakpoint,
      t,
    ),
    expandedBreakpoint: _cardLerpDouble(
      a.expandedBreakpoint,
      b.expandedBreakpoint,
      t,
    ),
    columnMinWidth: _cardLerpDouble(a.columnMinWidth, b.columnMinWidth, t),
    carouselCardWidth: _cardLerpDouble(
      a.carouselCardWidth,
      b.carouselCardWidth,
      t,
    ),
    contentOverlayPlate: t < 0.5
        ? a.contentOverlayPlate
        : b.contentOverlayPlate,
    maxHeight: lerpDouble(a.maxHeight, b.maxHeight, t),
  );
}

/// Container-transform tokens lerped as one group.
typedef _CardTransformLerp = ({
  double transformEndRadius,
  double transformContentFadeStart,
  double transformContentVisibleAt,
  M3ESpring transformSpring,
  Color? transformScrimColor,
  Color? transformOpenColor,
});

_CardTransformLerp _cardLerpTransform(
  M3ECardTheme a,
  M3ECardTheme b,
  double t,
) {
  return (
    transformEndRadius: _cardLerpDouble(
      a.transformEndRadius,
      b.transformEndRadius,
      t,
    ),
    transformContentFadeStart: _cardLerpDouble(
      a.transformContentFadeStart,
      b.transformContentFadeStart,
      t,
    ),
    transformContentVisibleAt: _cardLerpDouble(
      a.transformContentVisibleAt,
      b.transformContentVisibleAt,
      t,
    ),
    transformSpring: M3ESpring(
      stiffness: _cardLerpDouble(
        a.transformSpring.stiffness,
        b.transformSpring.stiffness,
        t,
      ),
      damping: _cardLerpDouble(
        a.transformSpring.damping,
        b.transformSpring.damping,
        t,
      ),
    ),
    transformScrimColor: Color.lerp(
      a.transformScrimColor,
      b.transformScrimColor,
      t,
    ),
    transformOpenColor: Color.lerp(
      a.transformOpenColor,
      b.transformOpenColor,
      t,
    ),
  );
}

/// Per-state elevation for every [M3ECardVariant], lerped as one group.
typedef _CardElevationLerp = ({
  double elevatedElevation,
  double elevatedHoverElevation,
  double elevatedFocusElevation,
  double elevatedPressedElevation,
  double elevatedDraggedElevation,
  double filledElevation,
  double filledHoverElevation,
  double filledFocusElevation,
  double filledPressedElevation,
  double filledDraggedElevation,
  double outlinedElevation,
  double outlinedHoverElevation,
  double outlinedFocusElevation,
  double outlinedPressedElevation,
  double outlinedDraggedElevation,
});

_CardElevationLerp _cardLerpElevation(
  M3ECardTheme a,
  M3ECardTheme b,
  double t,
) {
  return (
    elevatedElevation: _cardLerpDouble(
      a.elevatedElevation,
      b.elevatedElevation,
      t,
    ),
    elevatedHoverElevation: _cardLerpDouble(
      a.elevatedHoverElevation,
      b.elevatedHoverElevation,
      t,
    ),
    elevatedFocusElevation: _cardLerpDouble(
      a.elevatedFocusElevation,
      b.elevatedFocusElevation,
      t,
    ),
    elevatedPressedElevation: _cardLerpDouble(
      a.elevatedPressedElevation,
      b.elevatedPressedElevation,
      t,
    ),
    elevatedDraggedElevation: _cardLerpDouble(
      a.elevatedDraggedElevation,
      b.elevatedDraggedElevation,
      t,
    ),
    filledElevation: _cardLerpDouble(a.filledElevation, b.filledElevation, t),
    filledHoverElevation: _cardLerpDouble(
      a.filledHoverElevation,
      b.filledHoverElevation,
      t,
    ),
    filledFocusElevation: _cardLerpDouble(
      a.filledFocusElevation,
      b.filledFocusElevation,
      t,
    ),
    filledPressedElevation: _cardLerpDouble(
      a.filledPressedElevation,
      b.filledPressedElevation,
      t,
    ),
    filledDraggedElevation: _cardLerpDouble(
      a.filledDraggedElevation,
      b.filledDraggedElevation,
      t,
    ),
    outlinedElevation: _cardLerpDouble(
      a.outlinedElevation,
      b.outlinedElevation,
      t,
    ),
    outlinedHoverElevation: _cardLerpDouble(
      a.outlinedHoverElevation,
      b.outlinedHoverElevation,
      t,
    ),
    outlinedFocusElevation: _cardLerpDouble(
      a.outlinedFocusElevation,
      b.outlinedFocusElevation,
      t,
    ),
    outlinedPressedElevation: _cardLerpDouble(
      a.outlinedPressedElevation,
      b.outlinedPressedElevation,
      t,
    ),
    outlinedDraggedElevation: _cardLerpDouble(
      a.outlinedDraggedElevation,
      b.outlinedDraggedElevation,
      t,
    ),
  );
}

/// Optional container, outline, and text colors lerped as one group.
typedef _CardColorLerp = ({
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
  Color? headlineColor,
  Color? subheadColor,
  Color? supportingColor,
  Color? overlayPlateColor,
});

_CardColorLerp _cardLerpColors(M3ECardTheme a, M3ECardTheme b, double t) {
  return (
    containerColor: Color.lerp(a.containerColor, b.containerColor, t),
    elevatedContainerColor: Color.lerp(
      a.elevatedContainerColor,
      b.elevatedContainerColor,
      t,
    ),
    filledContainerColor: Color.lerp(
      a.filledContainerColor,
      b.filledContainerColor,
      t,
    ),
    outlinedContainerColor: Color.lerp(
      a.outlinedContainerColor,
      b.outlinedContainerColor,
      t,
    ),
    disabledElevatedColor: Color.lerp(
      a.disabledElevatedColor,
      b.disabledElevatedColor,
      t,
    ),
    disabledFilledColor: Color.lerp(
      a.disabledFilledColor,
      b.disabledFilledColor,
      t,
    ),
    outlineColorOverride: Color.lerp(
      a.outlineColorOverride,
      b.outlineColorOverride,
      t,
    ),
    disabledOutlineColor: Color.lerp(
      a.disabledOutlineColor,
      b.disabledOutlineColor,
      t,
    ),
    focusedOutlineColor: Color.lerp(
      a.focusedOutlineColor,
      b.focusedOutlineColor,
      t,
    ),
    iconColor: Color.lerp(a.iconColor, b.iconColor, t),
    shadowColor: Color.lerp(a.shadowColor, b.shadowColor, t),
    surfaceTint: Color.lerp(a.surfaceTint, b.surfaceTint, t),
    focusColor: Color.lerp(a.focusColor, b.focusColor, t),
    stateLayerColor: Color.lerp(a.stateLayerColor, b.stateLayerColor, t),
    scrimColor: Color.lerp(a.scrimColor, b.scrimColor, t),
    headlineColor: Color.lerp(a.headlineColor, b.headlineColor, t),
    subheadColor: Color.lerp(a.subheadColor, b.subheadColor, t),
    supportingColor: Color.lerp(a.supportingColor, b.supportingColor, t),
    overlayPlateColor: Color.lerp(a.overlayPlateColor, b.overlayPlateColor, t),
  );
}

bool _cardThemeEquals(M3ECardTheme a, M3ECardTheme b) {
  return a.contentPadding == b.contentPadding &&
      a.radius == b.radius &&
      a.gap == b.gap &&
      a.maxGap == b.maxGap &&
      a.iconSize == b.iconSize &&
      a.outlineWidth == b.outlineWidth &&
      a.focusThickness == b.focusThickness &&
      a.focusGap == b.focusGap &&
      a.disabledOpacity == b.disabledOpacity &&
      a.disabledOutlineOpacity == b.disabledOutlineOpacity &&
      a.scrimOpacity == b.scrimOpacity &&
      a.swipeThreshold == b.swipeThreshold &&
      a.compactBreakpoint == b.compactBreakpoint &&
      a.expandedBreakpoint == b.expandedBreakpoint &&
      a.columnMinWidth == b.columnMinWidth &&
      a.carouselCardWidth == b.carouselCardWidth &&
      a.transformEndRadius == b.transformEndRadius &&
      a.transformContentFadeStart == b.transformContentFadeStart &&
      a.transformContentVisibleAt == b.transformContentVisibleAt &&
      a.transformSpring == b.transformSpring &&
      a.contentOverlayPlate == b.contentOverlayPlate &&
      a.elevatedElevation == b.elevatedElevation &&
      a.elevatedHoverElevation == b.elevatedHoverElevation &&
      a.elevatedFocusElevation == b.elevatedFocusElevation &&
      a.elevatedPressedElevation == b.elevatedPressedElevation &&
      a.elevatedDraggedElevation == b.elevatedDraggedElevation &&
      a.filledElevation == b.filledElevation &&
      a.filledHoverElevation == b.filledHoverElevation &&
      a.filledFocusElevation == b.filledFocusElevation &&
      a.filledPressedElevation == b.filledPressedElevation &&
      a.filledDraggedElevation == b.filledDraggedElevation &&
      a.outlinedElevation == b.outlinedElevation &&
      a.outlinedHoverElevation == b.outlinedHoverElevation &&
      a.outlinedFocusElevation == b.outlinedFocusElevation &&
      a.outlinedPressedElevation == b.outlinedPressedElevation &&
      a.outlinedDraggedElevation == b.outlinedDraggedElevation &&
      a.containerColor == b.containerColor &&
      a.elevatedContainerColor == b.elevatedContainerColor &&
      a.filledContainerColor == b.filledContainerColor &&
      a.outlinedContainerColor == b.outlinedContainerColor &&
      a.disabledElevatedColor == b.disabledElevatedColor &&
      a.disabledFilledColor == b.disabledFilledColor &&
      a.outlineColorOverride == b.outlineColorOverride &&
      a.disabledOutlineColor == b.disabledOutlineColor &&
      a.focusedOutlineColor == b.focusedOutlineColor &&
      a.iconColor == b.iconColor &&
      a.shadowColor == b.shadowColor &&
      a.surfaceTint == b.surfaceTint &&
      a.focusColor == b.focusColor &&
      a.stateLayerColor == b.stateLayerColor &&
      a.scrimColor == b.scrimColor &&
      a.transformScrimColor == b.transformScrimColor &&
      a.transformOpenColor == b.transformOpenColor &&
      a.headlineColor == b.headlineColor &&
      a.subheadColor == b.subheadColor &&
      a.supportingColor == b.supportingColor &&
      a.overlayPlateColor == b.overlayPlateColor &&
      a.maxHeight == b.maxHeight;
}

int _cardThemeHash(M3ECardTheme s) => Object.hashAll(<Object?>[
  s.contentPadding,
  s.radius,
  s.gap,
  s.maxGap,
  s.iconSize,
  s.outlineWidth,
  s.focusThickness,
  s.focusGap,
  s.disabledOpacity,
  s.disabledOutlineOpacity,
  s.scrimOpacity,
  s.swipeThreshold,
  s.compactBreakpoint,
  s.expandedBreakpoint,
  s.columnMinWidth,
  s.carouselCardWidth,
  s.transformEndRadius,
  s.transformContentFadeStart,
  s.transformContentVisibleAt,
  s.transformSpring,
  s.contentOverlayPlate,
  s.elevatedElevation,
  s.elevatedHoverElevation,
  s.elevatedFocusElevation,
  s.elevatedPressedElevation,
  s.elevatedDraggedElevation,
  s.filledElevation,
  s.filledHoverElevation,
  s.filledFocusElevation,
  s.filledPressedElevation,
  s.filledDraggedElevation,
  s.outlinedElevation,
  s.outlinedHoverElevation,
  s.outlinedFocusElevation,
  s.outlinedPressedElevation,
  s.outlinedDraggedElevation,
  s.containerColor,
  s.elevatedContainerColor,
  s.filledContainerColor,
  s.outlinedContainerColor,
  s.disabledElevatedColor,
  s.disabledFilledColor,
  s.outlineColorOverride,
  s.disabledOutlineColor,
  s.focusedOutlineColor,
  s.iconColor,
  s.shadowColor,
  s.surfaceTint,
  s.focusColor,
  s.stateLayerColor,
  s.scrimColor,
  s.transformScrimColor,
  s.transformOpenColor,
  s.headlineColor,
  s.subheadColor,
  s.supportingColor,
  s.overlayPlateColor,
  s.maxHeight,
]);
