part of 'm3e_carousel_theme.dart';

/// Field-group interpolation helpers used by [M3ECarouselTheme.lerp].
extension _M3ECarouselThemeInterpolation on M3ECarouselTheme {
  /// Interpolates the motion/interaction-facing fields of `lerp`.
  ({
    double uncontainedItemExtent,
    double uncontainedShrinkExtent,
    double borderRadiusValue,
    int scrollAnimationDuration,
    int singleSwipeGestureSensitivityRange,
    EdgeInsetsGeometry itemPadding,
    double elevation,
    Clip itemClipBehavior,
    double outlineWidth,
    double hoverElevation,
    double focusThickness,
    double focusOffset,
    double hoverStateOpacity,
    double focusStateOpacity,
    double pressedStateOpacity,
  })
  _lerpMotionValues(M3ECarouselTheme other, double t) {
    return (
      uncontainedItemExtent: _lerpDouble(
        uncontainedItemExtent,
        other.uncontainedItemExtent,
        t,
      )!,
      uncontainedShrinkExtent: _lerpDouble(
        uncontainedShrinkExtent,
        other.uncontainedShrinkExtent,
        t,
      )!,
      borderRadiusValue: _lerpDouble(
        borderRadiusValue,
        other.borderRadiusValue,
        t,
      )!,
      scrollAnimationDuration: _lerpInt(
        scrollAnimationDuration,
        other.scrollAnimationDuration,
        t,
      ),
      singleSwipeGestureSensitivityRange: _lerpInt(
        singleSwipeGestureSensitivityRange,
        other.singleSwipeGestureSensitivityRange,
        t,
      ),
      itemPadding:
          EdgeInsets.lerp(
            itemPadding as EdgeInsets?,
            other.itemPadding as EdgeInsets?,
            t,
          ) ??
          itemPadding,
      elevation: _lerpDouble(elevation, other.elevation, t)!,
      itemClipBehavior: t < 0.5 ? itemClipBehavior : other.itemClipBehavior,
      outlineWidth: _lerpDouble(outlineWidth, other.outlineWidth, t)!,
      hoverElevation: _lerpDouble(hoverElevation, other.hoverElevation, t)!,
      focusThickness: _lerpDouble(focusThickness, other.focusThickness, t)!,
      focusOffset: _lerpDouble(focusOffset, other.focusOffset, t)!,
      hoverStateOpacity: _lerpDouble(
        hoverStateOpacity,
        other.hoverStateOpacity,
        t,
      )!,
      focusStateOpacity: _lerpDouble(
        focusStateOpacity,
        other.focusStateOpacity,
        t,
      )!,
      pressedStateOpacity: _lerpDouble(
        pressedStateOpacity,
        other.pressedStateOpacity,
        t,
      )!,
    );
  }

  /// Interpolates the sizing/opacity-facing fields of `lerp`.
  ({
    double disabledOpacity,
    double disabledOutlineOpacity,
    double smallMinWidth,
    double smallMaxWidth,
    double largeMaxWidth,
    double itemGap,
    double fullScreenGap,
    double showAllGap,
    double showAllPadding,
    double headerInset,
    double arrowSize,
    double expandedBreakpoint,
    double minAspect,
    double maxAspect,
    double fullScreenRadius,
  })
  _lerpSizingValues(M3ECarouselTheme other, double t) {
    return (
      disabledOpacity: _lerpDouble(disabledOpacity, other.disabledOpacity, t)!,
      disabledOutlineOpacity: _lerpDouble(
        disabledOutlineOpacity,
        other.disabledOutlineOpacity,
        t,
      )!,
      smallMinWidth: _lerpDouble(smallMinWidth, other.smallMinWidth, t)!,
      smallMaxWidth: _lerpDouble(smallMaxWidth, other.smallMaxWidth, t)!,
      largeMaxWidth: _lerpDouble(largeMaxWidth, other.largeMaxWidth, t)!,
      itemGap: _lerpDouble(itemGap, other.itemGap, t)!,
      fullScreenGap: _lerpDouble(fullScreenGap, other.fullScreenGap, t)!,
      showAllGap: _lerpDouble(showAllGap, other.showAllGap, t)!,
      showAllPadding: _lerpDouble(showAllPadding, other.showAllPadding, t)!,
      headerInset: _lerpDouble(headerInset, other.headerInset, t)!,
      arrowSize: _lerpDouble(arrowSize, other.arrowSize, t)!,
      expandedBreakpoint: _lerpDouble(
        expandedBreakpoint,
        other.expandedBreakpoint,
        t,
      )!,
      minAspect: _lerpDouble(minAspect, other.minAspect, t)!,
      maxAspect: _lerpDouble(maxAspect, other.maxAspect, t)!,
      fullScreenRadius: _lerpDouble(
        fullScreenRadius,
        other.fullScreenRadius,
        t,
      )!,
    );
  }

  double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;

  int _lerpInt(int a, int b, double t) => (a + (b - a) * t).round();
}
