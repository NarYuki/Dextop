import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_carousel_type.dart';

part 'm3e_carousel_theme_interpolation.dart';

/// Theme values for `M3ECarousel`.
@immutable
class M3ECarouselTheme extends M3EThemeExtension<M3ECarouselTheme> {
  /// defaultUncontainedItemExtent.
  static const double defaultUncontainedItemExtent = 270;

  /// defaultUncontainedShrinkExtent.
  static const double defaultUncontainedShrinkExtent = 150;

  /// defaultBorderRadiusValue.
  static const double defaultBorderRadiusValue = 28;

  /// defaultScrollAnimationDuration.
  static const int defaultScrollAnimationDuration = 500;

  /// defaultSingleSwipeGestureSensitivityRange.
  static const int defaultSingleSwipeGestureSensitivityRange = 300;

  /// Outline width.
  static const double defaultOutlineWidth = 1;

  /// Hover elevation. Other states stay flat.
  static const double defaultHoverElevation = 1;

  /// Focus ring thickness.
  static const double defaultFocusThickness = 3;

  /// Gap between the item and the focus ring.
  static const double defaultFocusOffset = 2;

  /// Hover state-layer opacity.
  static const double defaultHoverStateOpacity = 0.08;

  /// Focus state-layer opacity.
  static const double defaultFocusStateOpacity = 0.1;

  /// Pressed state-layer opacity.
  static const double defaultPressedStateOpacity = 0.1;

  /// Disabled content opacity.
  static const double defaultDisabledOpacity = 0.38;

  /// Disabled outline opacity.
  static const double defaultDisabledOutlineOpacity = 0.12;

  /// Small item minimum width.
  static const double defaultSmallMinWidth = 40;

  /// Small item maximum width.
  static const double defaultSmallMaxWidth = 56;

  /// Large item maximum width.
  static const double defaultLargeMaxWidth = 560;

  /// Gap between items.
  static const double defaultItemGap = 8;

  /// Gap between full-screen items.
  static const double defaultFullScreenGap = 16;

  /// Space between the carousel and Show all.
  static const double defaultShowAllGap = 4;

  /// Padding inside the Show all control.
  static const double defaultShowAllPadding = 4;

  /// Header leading inset.
  static const double defaultHeaderInset = 16;

  /// Header arrow target size.
  static const double defaultArrowSize = 48;

  /// Width at which more items fit on screen.
  static const double defaultExpandedBreakpoint = 840;

  /// Narrowest multi-aspect ratio (9:16).
  static const double defaultMinAspect = 9 / 16;

  /// Widest multi-aspect ratio (16:9).
  static const double defaultMaxAspect = 16 / 9;

  /// Full-screen corner radius.
  static const double defaultFullScreenRadius = 0;

  /// M3ECarouselTheme.

  const M3ECarouselTheme({
    this.uncontainedItemExtent = defaultUncontainedItemExtent,
    this.uncontainedShrinkExtent = defaultUncontainedShrinkExtent,
    this.borderRadiusValue = defaultBorderRadiusValue,
    this.scrollAnimationDuration = defaultScrollAnimationDuration,
    this.singleSwipeGestureSensitivityRange =
        defaultSingleSwipeGestureSensitivityRange,
    this.itemPadding = const EdgeInsets.symmetric(horizontal: 4),
    this.elevation = 0,
    this.itemClipBehavior = Clip.antiAlias,
    this.outlineWidth = defaultOutlineWidth,
    this.hoverElevation = defaultHoverElevation,
    this.focusThickness = defaultFocusThickness,
    this.focusOffset = defaultFocusOffset,
    this.hoverStateOpacity = defaultHoverStateOpacity,
    this.focusStateOpacity = defaultFocusStateOpacity,
    this.pressedStateOpacity = defaultPressedStateOpacity,
    this.disabledOpacity = defaultDisabledOpacity,
    this.disabledOutlineOpacity = defaultDisabledOutlineOpacity,
    this.smallMinWidth = defaultSmallMinWidth,
    this.smallMaxWidth = defaultSmallMaxWidth,
    this.largeMaxWidth = defaultLargeMaxWidth,
    this.itemGap = defaultItemGap,
    this.fullScreenGap = defaultFullScreenGap,
    this.showAllGap = defaultShowAllGap,
    this.showAllPadding = defaultShowAllPadding,
    this.headerInset = defaultHeaderInset,
    this.arrowSize = defaultArrowSize,
    this.expandedBreakpoint = defaultExpandedBreakpoint,
    this.minAspect = defaultMinAspect,
    this.maxAspect = defaultMaxAspect,
    this.fullScreenRadius = defaultFullScreenRadius,
  });

  /// defaults.

  static const M3ECarouselTheme defaults = M3ECarouselTheme();

  /// uncontainedItemExtent.

  final double uncontainedItemExtent;

  /// uncontainedShrinkExtent.
  final double uncontainedShrinkExtent;

  /// borderRadiusValue.
  final double borderRadiusValue;

  /// scrollAnimationDuration.
  final int scrollAnimationDuration;

  /// singleSwipeGestureSensitivityRange.
  final int singleSwipeGestureSensitivityRange;

  /// itemPadding.
  final EdgeInsetsGeometry itemPadding;

  /// elevation.
  final double elevation;

  /// itemClipBehavior.
  final Clip itemClipBehavior;

  /// Outline width.
  final double outlineWidth;

  /// Elevation while hovered.
  final double hoverElevation;

  /// Focus ring thickness.
  final double focusThickness;

  /// Gap outside the item before the focus ring.
  final double focusOffset;

  /// Hover state-layer opacity.
  final double hoverStateOpacity;

  /// Focus state-layer opacity.
  final double focusStateOpacity;

  /// Pressed state-layer opacity.
  final double pressedStateOpacity;

  /// Disabled content opacity.
  final double disabledOpacity;

  /// Disabled outline opacity.
  final double disabledOutlineOpacity;

  /// Smallest small-item width.
  final double smallMinWidth;

  /// Largest small-item width.
  final double smallMaxWidth;

  /// Largest large-item width.
  final double largeMaxWidth;

  /// Gap between items.
  final double itemGap;

  /// Kept so existing themes still construct. Full-screen items meet edge to
  /// edge; this value is not inserted between them.
  final double fullScreenGap;

  /// Gap above Show all.
  final double showAllGap;

  /// Padding inside Show all.
  final double showAllPadding;

  /// Header leading inset.
  final double headerInset;

  /// Header arrow target.
  final double arrowSize;

  /// Width where the extended layout starts.
  final double expandedBreakpoint;

  /// Minimum multi-aspect ratio.
  final double minAspect;

  /// Maximum multi-aspect ratio.
  final double maxAspect;

  /// Full-screen corner radius.
  final double fullScreenRadius;

  /// Corner radius for [type].
  double radiusFor(M3ECarouselType type) {
    if (type == M3ECarouselType.fullScreen) {
      return fullScreenRadius;
    }
    return borderRadiusValue;
  }

  /// Container padding around the scroll track.
  ///
  /// Hero and contained keep 16 on the sides. Uncontained tracks touch the
  /// horizontal edges; the item gap is the leading space at rest.
  EdgeInsets containerPaddingFor(M3ECarouselType type) {
    final double halfGap = itemGap / 2;
    final double vertical = 8;
    switch (type) {
      case M3ECarouselType.fullScreen:
        return EdgeInsets.zero;
      case M3ECarouselType.uncontained:
      case M3ECarouselType.uncontainedMultiAspect:
        return EdgeInsets.symmetric(vertical: vertical);
      case M3ECarouselType.hero:
      case M3ECarouselType.contained:
        return EdgeInsets.fromLTRB(
          16 - halfGap,
          vertical,
          16 - halfGap,
          vertical,
        );
    }
  }

  /// The borderRadius.

  BorderRadius get borderRadius =>
      BorderRadius.all(Radius.circular(borderRadiusValue));

  /// The shape.

  ShapeBorder get shape => RoundedRectangleBorder(borderRadius: borderRadius);

  /// backgroundColor.

  Color backgroundColor(M3EColorScheme scheme) => scheme.surface;

  /// overlayColor.

  WidgetStateProperty<Color?> overlayColor(M3EColorScheme scheme) {
    return WidgetStateProperty.resolveWith((Set<WidgetState> states) {
      if (states.contains(WidgetState.pressed)) {
        return scheme.onSurface.withValues(alpha: pressedStateOpacity);
      }
      if (states.contains(WidgetState.hovered)) {
        return scheme.onSurface.withValues(alpha: hoverStateOpacity);
      }
      if (states.contains(WidgetState.focused)) {
        return scheme.onSurface.withValues(alpha: focusStateOpacity);
      }
      return null;
    });
  }

  @override
  M3ECarouselTheme copyWith({
    double? uncontainedItemExtent,
    double? uncontainedShrinkExtent,
    double? borderRadiusValue,
    int? scrollAnimationDuration,
    int? singleSwipeGestureSensitivityRange,
    EdgeInsetsGeometry? itemPadding,
    double? elevation,
    Clip? itemClipBehavior,
    double? outlineWidth,
    double? hoverElevation,
    double? focusThickness,
    double? focusOffset,
    double? hoverStateOpacity,
    double? focusStateOpacity,
    double? pressedStateOpacity,
    double? disabledOpacity,
    double? disabledOutlineOpacity,
    double? smallMinWidth,
    double? smallMaxWidth,
    double? largeMaxWidth,
    double? itemGap,
    double? fullScreenGap,
    double? showAllGap,
    double? showAllPadding,
    double? headerInset,
    double? arrowSize,
    double? expandedBreakpoint,
    double? minAspect,
    double? maxAspect,
    double? fullScreenRadius,
  }) {
    return M3ECarouselTheme(
      uncontainedItemExtent:
          uncontainedItemExtent ?? this.uncontainedItemExtent,
      uncontainedShrinkExtent:
          uncontainedShrinkExtent ?? this.uncontainedShrinkExtent,
      borderRadiusValue: borderRadiusValue ?? this.borderRadiusValue,
      scrollAnimationDuration:
          scrollAnimationDuration ?? this.scrollAnimationDuration,
      singleSwipeGestureSensitivityRange:
          singleSwipeGestureSensitivityRange ??
          this.singleSwipeGestureSensitivityRange,
      itemPadding: itemPadding ?? this.itemPadding,
      elevation: elevation ?? this.elevation,
      itemClipBehavior: itemClipBehavior ?? this.itemClipBehavior,
      outlineWidth: outlineWidth ?? this.outlineWidth,
      hoverElevation: hoverElevation ?? this.hoverElevation,
      focusThickness: focusThickness ?? this.focusThickness,
      focusOffset: focusOffset ?? this.focusOffset,
      hoverStateOpacity: hoverStateOpacity ?? this.hoverStateOpacity,
      focusStateOpacity: focusStateOpacity ?? this.focusStateOpacity,
      pressedStateOpacity: pressedStateOpacity ?? this.pressedStateOpacity,
      disabledOpacity: disabledOpacity ?? this.disabledOpacity,
      disabledOutlineOpacity:
          disabledOutlineOpacity ?? this.disabledOutlineOpacity,
      smallMinWidth: smallMinWidth ?? this.smallMinWidth,
      smallMaxWidth: smallMaxWidth ?? this.smallMaxWidth,
      largeMaxWidth: largeMaxWidth ?? this.largeMaxWidth,
      itemGap: itemGap ?? this.itemGap,
      fullScreenGap: fullScreenGap ?? this.fullScreenGap,
      showAllGap: showAllGap ?? this.showAllGap,
      showAllPadding: showAllPadding ?? this.showAllPadding,
      headerInset: headerInset ?? this.headerInset,
      arrowSize: arrowSize ?? this.arrowSize,
      expandedBreakpoint: expandedBreakpoint ?? this.expandedBreakpoint,
      minAspect: minAspect ?? this.minAspect,
      maxAspect: maxAspect ?? this.maxAspect,
      fullScreenRadius: fullScreenRadius ?? this.fullScreenRadius,
    );
  }

  @override
  M3ECarouselTheme lerp(M3ECarouselTheme? other, double t) {
    if (other is! M3ECarouselTheme) {
      return this;
    }
    final motion = _lerpMotionValues(other, t);
    final sizing = _lerpSizingValues(other, t);
    return M3ECarouselTheme(
      uncontainedItemExtent: motion.uncontainedItemExtent,
      uncontainedShrinkExtent: motion.uncontainedShrinkExtent,
      borderRadiusValue: motion.borderRadiusValue,
      scrollAnimationDuration: motion.scrollAnimationDuration,
      singleSwipeGestureSensitivityRange:
          motion.singleSwipeGestureSensitivityRange,
      itemPadding: motion.itemPadding,
      elevation: motion.elevation,
      itemClipBehavior: motion.itemClipBehavior,
      outlineWidth: motion.outlineWidth,
      hoverElevation: motion.hoverElevation,
      focusThickness: motion.focusThickness,
      focusOffset: motion.focusOffset,
      hoverStateOpacity: motion.hoverStateOpacity,
      focusStateOpacity: motion.focusStateOpacity,
      pressedStateOpacity: motion.pressedStateOpacity,
      disabledOpacity: sizing.disabledOpacity,
      disabledOutlineOpacity: sizing.disabledOutlineOpacity,
      smallMinWidth: sizing.smallMinWidth,
      smallMaxWidth: sizing.smallMaxWidth,
      largeMaxWidth: sizing.largeMaxWidth,
      itemGap: sizing.itemGap,
      fullScreenGap: sizing.fullScreenGap,
      showAllGap: sizing.showAllGap,
      showAllPadding: sizing.showAllPadding,
      headerInset: sizing.headerInset,
      arrowSize: sizing.arrowSize,
      expandedBreakpoint: sizing.expandedBreakpoint,
      minAspect: sizing.minAspect,
      maxAspect: sizing.maxAspect,
      fullScreenRadius: sizing.fullScreenRadius,
    );
  }
}
