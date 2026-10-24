/// Layout constants for the M3E navigation bar.
abstract final class M3ENavBarConstants {
  const M3ENavBarConstants._();

  /// Flexible (small) bar content height.
  static const double heightSmall = 64;

  /// Baseline (medium) bar content height.
  static const double heightMedium = 80;

  /// Vertical active pill width.
  static const double flexibleIndicatorWidth = 56;

  /// Inactive hover pill width.
  static const double hoverIndicatorWidth = 24;

  /// Horizontal active pill height.
  static const double horizontalIndicatorHeight = 40;

  /// Vertical pill corner radius.
  static const double indicatorRadius = 16;

  /// Flexible vertical padding above the icon pill.
  static const double verticalPaddingTop = 6;

  /// Flexible vertical padding below the label.
  static const double verticalPaddingBottom = 6;

  /// Gap between the vertical icon pill and the label.
  static const double verticalIconLabelGap = 4;

  /// Horizontal bar padding above and below the pill.
  static const double horizontalBarPadding = 12;

  /// Leading and trailing inset inside a horizontal pill.
  static const double horizontalIndicatorInset = 16;

  /// Gap between the icon and the label inside a horizontal pill.
  static const double horizontalIconLabelGap = 4;

  /// Extra padding baseline adds above and below the flexible padding.
  static const double baselineExtraPadding = 8;

  /// Gap between vertical destination items.
  static const double itemGap = 0;

  /// Window width where auto layout switches to horizontal items.
  static const double mediumWindowBreakpoint = 600;

  /// Inset focus ring thickness.
  static const double focusRingThickness = 3;

  /// Inset of the focus ring from the destination edge.
  static const double focusRingInset = 3;

  /// Hover state-layer opacity.
  static const double stateHoverOpacity = 0.08;

  /// Focus state-layer opacity.
  static const double stateFocusOpacity = 0.1;

  /// Pressed state-layer opacity.
  static const double statePressedOpacity = 0.1;

  /// Label size at 1x.
  static const double labelFontSize = 12;

  /// Label line height at 1x.
  static const double labelLineHeight = 16;

  /// Label tracking.
  static const double labelLetterSpacing = 0.5;

  /// Text scale above which a label may ellipsize.
  static const double truncationTextScale = 2;

  /// Label lines at 1x.
  static const int restingLabelMaxLines = 1;

  /// Label lines above 1x.
  static const int scaledLabelMaxLines = 2;

  /// Container elevation.
  static const double elevation = 0;

  /// Default fixed width of each destination chip in wide layout.
  ///
  /// The bar uses the widest label instead when `wideDestinationWidth` is null.
  static const double wideDestinationWidth = 128;

  /// Gap between wide destination chips.
  ///
  /// Horizontal layout uses the theme wide-item gap, which defaults to this
  /// value. Vertical layout uses [itemGap].
  static const double wideDestinationGap = 8;

  /// Horizontal inset of the wide destination group from the bar edges.
  static const double wideBarHorizontalPadding = 16;

  /// Typical Material max destination count used for the documented default
  /// [wideBreakpoint].
  static const int wideBreakpointDestinationCount = 5;

  /// Default autoLayout threshold sized for
  /// [wideBreakpointDestinationCount] chips at [wideDestinationWidth].
  ///
  /// The bar's live threshold is [mediumWindowBreakpoint] unless
  /// `wideBreakpoint` is set.
  static const double wideBreakpoint =
      wideBreakpointDestinationCount * wideDestinationWidth +
      (wideBreakpointDestinationCount - 1) * wideDestinationGap +
      2 * wideBarHorizontalPadding;

  /// Minimum bar width needed to fit [destinationCount] fixed-width wide chips.
  static double minWideBarWidth(
    int destinationCount, {
    double itemWidth = wideDestinationWidth,
  }) {
    assert(destinationCount > 0, 'destinationCount must be > 0');
    return destinationCount * itemWidth +
        (destinationCount - 1) * wideDestinationGap +
        2 * wideBarHorizontalPadding;
  }

  /// Compact resting pill width (icon chip). Kept for the navigation rail.
  static const double compactIndicatorWidth = 64;

  /// Compact resting / fluid pill height.
  static const double indicatorHeight = 32;

  /// Subtracted from the bar content height (excludes system nav inset) for
  /// the wide active pill height.
  static const double wideIndicatorHeightReduction = 32;

  /// Gap between icon and label inside a wide destination chip.
  static const double wideIconLabelGap = 8;

  /// Horizontal padding inside a wide destination pill around its content.
  static const double widePillHorizontalPadding = 16;

  /// Extra inset past the stadium radius inside a wide pill.
  static const double widePillCapClearance = 4;

  /// Remeasure window after geometry-affecting layoutToken changes (behaviors,
  /// alignment, compact↔wide) so the fluid pill tracks settling chip sizes.
  static const Duration layoutSettleDuration = Duration(milliseconds: 160);
}
