part of 'm3e_navigation_bar_theme.dart';

typedef _NavBarLerpA1 = ({
  double heightSmall,
  double heightMedium,
  double iconSize,
  double indicatorThickness,
  double compactHeightReduction,
  double compactIndicatorReduction,
  M3ESpring indicatorScaleSpring,
  M3ESpring indicatorFadeSpring,
});

typedef _NavBarLerpA2 = ({
  double elevation,
  double verticalIndicatorWidth,
  double verticalIndicatorHeight,
  double verticalIndicatorRadius,
  double verticalPaddingTop,
  double verticalPaddingBottom,
  double verticalIconLabelGap,
  double horizontalIndicatorHeight,
  double horizontalPadding,
  double horizontalIndicatorInset,
  double horizontalIconLabelGap,
});

typedef _NavBarLerpB = ({
  double baselineExtraPadding,
  double itemGap,
  double wideItemGap,
  double wideEdgePadding,
  double hoverIndicatorWidth,
  double wideBreakpoint,
  double focusRingThickness,
  double focusRingInset,
  double hoverOpacity,
  double focusOpacity,
  double pressedOpacity,
  double labelFontSize,
  double labelLineHeight,
  double labelLetterSpacing,
  FontWeight inactiveLabelWeight,
  FontWeight activeLabelWeight,
  int restingLabelMaxLines,
  int scaledLabelMaxLines,
  double truncationTextScale,
});

double _navBarMix(double a, double b, double t) => a + (b - a) * t;

/// Grouped field resolution for [M3ENavigationBarTheme.lerp], split out so
/// no single function grows past the component length guidelines.
extension _M3ENavigationBarThemeLerp on M3ENavigationBarTheme {
  M3ENavigationBarTheme _lerpNavigationBarTheme(
    M3ENavigationBarTheme other,
    double t,
  ) {
    final _NavBarLerpA1 a1 = _lerpNavBarGroupA1(other, t);
    final _NavBarLerpA2 a2 = _lerpNavBarGroupA2(other, t);
    final _NavBarLerpB b = _lerpNavBarGroupB(other, t);
    return M3ENavigationBarTheme(
      heightSmall: a1.heightSmall,
      heightMedium: a1.heightMedium,
      iconSize: a1.iconSize,
      indicatorThickness: a1.indicatorThickness,
      compactHeightReduction: a1.compactHeightReduction,
      compactIndicatorReduction: a1.compactIndicatorReduction,
      indicatorScaleSpring: a1.indicatorScaleSpring,
      indicatorFadeSpring: a1.indicatorFadeSpring,
      elevation: a2.elevation,
      verticalIndicatorWidth: a2.verticalIndicatorWidth,
      verticalIndicatorHeight: a2.verticalIndicatorHeight,
      verticalIndicatorRadius: a2.verticalIndicatorRadius,
      verticalPaddingTop: a2.verticalPaddingTop,
      verticalPaddingBottom: a2.verticalPaddingBottom,
      verticalIconLabelGap: a2.verticalIconLabelGap,
      horizontalIndicatorHeight: a2.horizontalIndicatorHeight,
      horizontalPadding: a2.horizontalPadding,
      horizontalIndicatorInset: a2.horizontalIndicatorInset,
      horizontalIconLabelGap: a2.horizontalIconLabelGap,
      baselineExtraPadding: b.baselineExtraPadding,
      itemGap: b.itemGap,
      wideItemGap: b.wideItemGap,
      wideEdgePadding: b.wideEdgePadding,
      hoverIndicatorWidth: b.hoverIndicatorWidth,
      wideBreakpoint: b.wideBreakpoint,
      focusRingThickness: b.focusRingThickness,
      focusRingInset: b.focusRingInset,
      hoverOpacity: b.hoverOpacity,
      focusOpacity: b.focusOpacity,
      pressedOpacity: b.pressedOpacity,
      labelFontSize: b.labelFontSize,
      labelLineHeight: b.labelLineHeight,
      labelLetterSpacing: b.labelLetterSpacing,
      inactiveLabelWeight: b.inactiveLabelWeight,
      activeLabelWeight: b.activeLabelWeight,
      restingLabelMaxLines: b.restingLabelMaxLines,
      scaledLabelMaxLines: b.scaledLabelMaxLines,
      truncationTextScale: b.truncationTextScale,
    );
  }

  _NavBarLerpA1 _lerpNavBarGroupA1(M3ENavigationBarTheme other, double t) {
    return (
      heightSmall: _navBarMix(heightSmall, other.heightSmall, t),
      heightMedium: _navBarMix(heightMedium, other.heightMedium, t),
      iconSize: _navBarMix(iconSize, other.iconSize, t),
      indicatorThickness: _navBarMix(
        indicatorThickness,
        other.indicatorThickness,
        t,
      ),
      compactHeightReduction: _navBarMix(
        compactHeightReduction,
        other.compactHeightReduction,
        t,
      ),
      compactIndicatorReduction: _navBarMix(
        compactIndicatorReduction,
        other.compactIndicatorReduction,
        t,
      ),
      indicatorScaleSpring: t < 0.5
          ? indicatorScaleSpring
          : other.indicatorScaleSpring,
      indicatorFadeSpring: t < 0.5
          ? indicatorFadeSpring
          : other.indicatorFadeSpring,
    );
  }

  _NavBarLerpA2 _lerpNavBarGroupA2(M3ENavigationBarTheme other, double t) {
    return (
      elevation: _navBarMix(elevation, other.elevation, t),
      verticalIndicatorWidth: _navBarMix(
        verticalIndicatorWidth,
        other.verticalIndicatorWidth,
        t,
      ),
      verticalIndicatorHeight: _navBarMix(
        verticalIndicatorHeight,
        other.verticalIndicatorHeight,
        t,
      ),
      verticalIndicatorRadius: _navBarMix(
        verticalIndicatorRadius,
        other.verticalIndicatorRadius,
        t,
      ),
      verticalPaddingTop: _navBarMix(
        verticalPaddingTop,
        other.verticalPaddingTop,
        t,
      ),
      verticalPaddingBottom: _navBarMix(
        verticalPaddingBottom,
        other.verticalPaddingBottom,
        t,
      ),
      verticalIconLabelGap: _navBarMix(
        verticalIconLabelGap,
        other.verticalIconLabelGap,
        t,
      ),
      horizontalIndicatorHeight: _navBarMix(
        horizontalIndicatorHeight,
        other.horizontalIndicatorHeight,
        t,
      ),
      horizontalPadding: _navBarMix(
        horizontalPadding,
        other.horizontalPadding,
        t,
      ),
      horizontalIndicatorInset: _navBarMix(
        horizontalIndicatorInset,
        other.horizontalIndicatorInset,
        t,
      ),
      horizontalIconLabelGap: _navBarMix(
        horizontalIconLabelGap,
        other.horizontalIconLabelGap,
        t,
      ),
    );
  }

  _NavBarLerpB _lerpNavBarGroupB(M3ENavigationBarTheme other, double t) {
    return (
      baselineExtraPadding: _navBarMix(
        baselineExtraPadding,
        other.baselineExtraPadding,
        t,
      ),
      itemGap: _navBarMix(itemGap, other.itemGap, t),
      wideItemGap: _navBarMix(wideItemGap, other.wideItemGap, t),
      wideEdgePadding: _navBarMix(wideEdgePadding, other.wideEdgePadding, t),
      hoverIndicatorWidth: _navBarMix(
        hoverIndicatorWidth,
        other.hoverIndicatorWidth,
        t,
      ),
      wideBreakpoint: _navBarMix(wideBreakpoint, other.wideBreakpoint, t),
      focusRingThickness: _navBarMix(
        focusRingThickness,
        other.focusRingThickness,
        t,
      ),
      focusRingInset: _navBarMix(focusRingInset, other.focusRingInset, t),
      hoverOpacity: _navBarMix(hoverOpacity, other.hoverOpacity, t),
      focusOpacity: _navBarMix(focusOpacity, other.focusOpacity, t),
      pressedOpacity: _navBarMix(pressedOpacity, other.pressedOpacity, t),
      labelFontSize: _navBarMix(labelFontSize, other.labelFontSize, t),
      labelLineHeight: _navBarMix(labelLineHeight, other.labelLineHeight, t),
      labelLetterSpacing: _navBarMix(
        labelLetterSpacing,
        other.labelLetterSpacing,
        t,
      ),
      inactiveLabelWeight: t < 0.5
          ? inactiveLabelWeight
          : other.inactiveLabelWeight,
      activeLabelWeight: t < 0.5 ? activeLabelWeight : other.activeLabelWeight,
      restingLabelMaxLines: t < 0.5
          ? restingLabelMaxLines
          : other.restingLabelMaxLines,
      scaledLabelMaxLines: t < 0.5
          ? scaledLabelMaxLines
          : other.scaledLabelMaxLines,
      truncationTextScale: _navBarMix(
        truncationTextScale,
        other.truncationTextScale,
        t,
      ),
    );
  }
}
