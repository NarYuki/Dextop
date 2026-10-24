import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/search/m3e_search.dart'
    show M3ESearchAnchor;
import 'package:material_3_expressive/components/search/m3e_search_anchor.dart'
    show M3ESearchAnchor;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ESearchAnchor;

import '../../../foundations/foundations.dart';
import '../enums/m3e_search_enums.dart';

/// Theme values for [M3ESearchAnchor] search views.
///
/// Defaults follow the M3 Expressive "Search - View" token set. [style]
/// picks the contained (expressive) or divided (baseline) values.
@immutable
class M3ESearchViewTheme extends M3EThemeExtension<M3ESearchViewTheme> {
  /// M3ESearchViewTheme.
  const M3ESearchViewTheme({
    this.elevation = M3EElevation.level3,
    this.cornerRadius = 28,
    this.headerHeight = 72,
    this.minWidth = 360,
    this.minHeight = 240,
    this.barHorizontalPadding = 8,
    this.shrinkWrap = false,
    this.style = M3ESearchViewStyle.contained,
    this.maxWidth = 720,
    this.maxHeightFactor = 2 / 3,
    this.compactBreakpoint = 600,
    this.containedElevation = M3EElevation.level0,
    this.containedLeadingMargin = 12,
    this.containedTrailingMargin = 12,
    this.containedBarHeight = 56,
    this.dockedBarResultsGap = 2,
    this.dockedResultsRadius = 28,
    this.dockedResultsInset = 4,
    this.dockedResultsBottomPadding = 4,
    this.dividedDockedHeaderHeight = 56,
    this.dividedDockedListPadding = 16,
    this.fullScreenRadius = 0,
    this.scrimOpacity = 0.32,
    this.containerTransformSpring = M3EMotion.expressiveSpatialDefault,
    this.fadeSpring = M3EMotion.effectsDefault,
    this.predictiveBackSpring = M3EMotion.expressiveSpatialDefault,
    this.predictiveBackMinScale = 0.9,
    this.predictiveBackEdgeMargin = 8,
    this.predictiveBackMaxOffsetY = 24,
    this.containedFullScreenBarVerticalPadding = 8,
    this.containedFullScreenResultsInset = 12,
    this.dividedResultsInset = 16,
  });

  /// defaults.

  static const M3ESearchViewTheme defaults = M3ESearchViewTheme();

  /// Divided docked container elevation (level 3).
  final double elevation;

  /// Docked container radius in the divided style (28).
  final double cornerRadius;

  /// Divided full-screen header height (72).
  final double headerHeight;

  /// Docked min width (360).
  final double minWidth;

  /// Docked min height (240).
  final double minHeight;

  /// Legacy: no longer applied. The header bar uses the search bar spacing.
  final double barHorizontalPadding;

  /// Whether the docked view hugs its results.
  final bool shrinkWrap;

  /// Default style when the anchor does not set one (contained).
  final M3ESearchViewStyle style;

  /// Docked max width (720).
  final double maxWidth;

  /// Docked max height as a fraction of the screen height (2/3).
  final double maxHeightFactor;

  /// Window width below which the view is full-screen (600).
  final double compactBreakpoint;

  /// Contained view elevation (level 0).
  final double containedElevation;

  /// Contained bar leading margin when focused (12).
  final double containedLeadingMargin;

  /// Contained bar trailing margin when focused (12).
  final double containedTrailingMargin;

  /// Contained bar container height (56).
  final double containedBarHeight;

  /// Contained docked gap between the bar and results (2).
  final double dockedBarResultsGap;

  /// Contained docked results container radius (28).
  final double dockedResultsRadius;

  /// Contained docked results side inset (4).
  final double dockedResultsInset;

  /// Contained docked results bottom padding (4).
  final double dockedResultsBottomPadding;

  /// Divided docked header height (56).
  final double dividedDockedHeaderHeight;

  /// Divided docked list top / bottom padding (16).
  final double dividedDockedListPadding;

  /// Full-screen container radius (0).
  final double fullScreenRadius;

  /// Docked scrim opacity on [M3EColorScheme.scrim] (0.32).
  final double scrimOpacity;

  /// Spring for the bar → view container transform.
  final M3ESpring containerTransformSpring;

  /// Spring for results / divider fades.
  final M3ESpring fadeSpring;

  /// Spring that settles the view after a predictive back gesture.
  final M3ESpring predictiveBackSpring;

  /// Smallest scale while a predictive back gesture runs (0.9).
  final double predictiveBackMinScale;

  /// Gap kept from the screen edge while detaching (8).
  final double predictiveBackEdgeMargin;

  /// Max vertical follow of the back gesture (24).
  final double predictiveBackMaxOffsetY;

  /// Contained full-screen space above and below the bar (8), giving a 72
  /// header like the divided style.
  final double containedFullScreenBarVerticalPadding;

  /// Contained full-screen results side inset (12), aligned with the bar.
  final double containedFullScreenResultsInset;

  /// Divided results side inset (16), aligned with the header's 16 space.
  final double dividedResultsInset;

  /// Docked container color: surface container high.

  Color backgroundColor(M3EColorScheme scheme) => scheme.surfaceContainerHigh;

  /// Divided full-screen container color: surface container high.

  Color fullScreenBackgroundColor(M3EColorScheme scheme) =>
      scheme.surfaceContainerHigh;

  /// Contained full-screen background: surface container low.

  Color containedBackgroundColor(M3EColorScheme scheme) =>
      scheme.surfaceContainerLow;

  /// Contained bar and docked results color: surface container high.

  Color containedContainerColor(M3EColorScheme scheme) =>
      scheme.surfaceContainerHigh;

  /// Container surface tint layer color: primary.

  Color surfaceTintColor(M3EColorScheme scheme) => scheme.primary;

  /// Docked scrim color.

  Color scrimColor(M3EColorScheme scheme) =>
      scheme.scrim.withValues(alpha: scrimOpacity);

  /// Legacy full-screen header padding. No longer applied.

  EdgeInsetsGeometry fullScreenHeaderPadding() =>
      const EdgeInsets.symmetric(horizontal: 16, vertical: 8);

  /// Divider color: outline.

  Color dividerColor(M3EColorScheme scheme) => scheme.outline;

  /// Header input text: body large, on surface.

  TextStyle headerTextStyle(M3ETypeScale type, M3EColorScheme scheme) =>
      type.bodyLarge.copyWith(color: scheme.onSurface);

  /// Header supporting text: body large, on surface variant.

  TextStyle headerHintStyle(M3ETypeScale type, M3EColorScheme scheme) =>
      type.bodyLarge.copyWith(color: scheme.onSurfaceVariant);

  /// Rounded docked container shape.

  ShapeBorder dockedShape(double radius) =>
      RoundedRectangleBorder(borderRadius: BorderRadius.circular(radius));

  /// Full-screen container shape.

  ShapeBorder fullScreenShape() => RoundedRectangleBorder(
    borderRadius: BorderRadius.circular(fullScreenRadius),
  );

  /// Docked size constraints.

  BoxConstraints constraints({double? maxWidth, double? maxHeight}) {
    return BoxConstraints(
      minWidth: minWidth,
      maxWidth: maxWidth ?? this.maxWidth,
      minHeight: minHeight,
      maxHeight: maxHeight ?? double.infinity,
    );
  }

  /// Legacy header bar padding. No longer applied by default.

  EdgeInsetsGeometry barPadding() =>
      EdgeInsets.symmetric(horizontal: barHorizontalPadding);

  @override
  M3ESearchViewTheme copyWith({
    double? elevation,
    double? cornerRadius,
    double? headerHeight,
    double? minWidth,
    double? minHeight,
    double? barHorizontalPadding,
    bool? shrinkWrap,
    M3ESearchViewStyle? style,
    double? maxWidth,
    double? maxHeightFactor,
    double? compactBreakpoint,
    double? containedElevation,
    double? containedLeadingMargin,
    double? containedTrailingMargin,
    double? containedBarHeight,
    double? dockedBarResultsGap,
    double? dockedResultsRadius,
    double? dockedResultsInset,
    double? dockedResultsBottomPadding,
    double? dividedDockedHeaderHeight,
    double? dividedDockedListPadding,
    double? fullScreenRadius,
    double? scrimOpacity,
    M3ESpring? containerTransformSpring,
    M3ESpring? fadeSpring,
    M3ESpring? predictiveBackSpring,
    double? predictiveBackMinScale,
    double? predictiveBackEdgeMargin,
    double? predictiveBackMaxOffsetY,
    double? containedFullScreenBarVerticalPadding,
    double? containedFullScreenResultsInset,
    double? dividedResultsInset,
  }) {
    return M3ESearchViewTheme(
      elevation: elevation ?? this.elevation,
      cornerRadius: cornerRadius ?? this.cornerRadius,
      headerHeight: headerHeight ?? this.headerHeight,
      minWidth: minWidth ?? this.minWidth,
      minHeight: minHeight ?? this.minHeight,
      barHorizontalPadding: barHorizontalPadding ?? this.barHorizontalPadding,
      shrinkWrap: shrinkWrap ?? this.shrinkWrap,
      style: style ?? this.style,
      maxWidth: maxWidth ?? this.maxWidth,
      maxHeightFactor: maxHeightFactor ?? this.maxHeightFactor,
      compactBreakpoint: compactBreakpoint ?? this.compactBreakpoint,
      containedElevation: containedElevation ?? this.containedElevation,
      containedLeadingMargin:
          containedLeadingMargin ?? this.containedLeadingMargin,
      containedTrailingMargin:
          containedTrailingMargin ?? this.containedTrailingMargin,
      containedBarHeight: containedBarHeight ?? this.containedBarHeight,
      dockedBarResultsGap: dockedBarResultsGap ?? this.dockedBarResultsGap,
      dockedResultsRadius: dockedResultsRadius ?? this.dockedResultsRadius,
      dockedResultsInset: dockedResultsInset ?? this.dockedResultsInset,
      dockedResultsBottomPadding:
          dockedResultsBottomPadding ?? this.dockedResultsBottomPadding,
      dividedDockedHeaderHeight:
          dividedDockedHeaderHeight ?? this.dividedDockedHeaderHeight,
      dividedDockedListPadding:
          dividedDockedListPadding ?? this.dividedDockedListPadding,
      fullScreenRadius: fullScreenRadius ?? this.fullScreenRadius,
      scrimOpacity: scrimOpacity ?? this.scrimOpacity,
      containerTransformSpring:
          containerTransformSpring ?? this.containerTransformSpring,
      fadeSpring: fadeSpring ?? this.fadeSpring,
      predictiveBackSpring: predictiveBackSpring ?? this.predictiveBackSpring,
      predictiveBackMinScale:
          predictiveBackMinScale ?? this.predictiveBackMinScale,
      predictiveBackEdgeMargin:
          predictiveBackEdgeMargin ?? this.predictiveBackEdgeMargin,
      predictiveBackMaxOffsetY:
          predictiveBackMaxOffsetY ?? this.predictiveBackMaxOffsetY,
      containedFullScreenBarVerticalPadding:
          containedFullScreenBarVerticalPadding ??
          this.containedFullScreenBarVerticalPadding,
      containedFullScreenResultsInset:
          containedFullScreenResultsInset ??
          this.containedFullScreenResultsInset,
      dividedResultsInset: dividedResultsInset ?? this.dividedResultsInset,
    );
  }

  @override
  M3ESearchViewTheme lerp(M3ESearchViewTheme? other, double t) {
    if (other is! M3ESearchViewTheme) {
      return this;
    }
    final M3ESearchViewTheme b = t < 0.5 ? this : other;
    double l(double Function(M3ESearchViewTheme x) f) =>
        _lerp(f(this), f(other), t);
    return M3ESearchViewTheme(
      elevation: l((M3ESearchViewTheme x) => x.elevation),
      cornerRadius: l((M3ESearchViewTheme x) => x.cornerRadius),
      headerHeight: l((M3ESearchViewTheme x) => x.headerHeight),
      minWidth: l((M3ESearchViewTheme x) => x.minWidth),
      minHeight: l((M3ESearchViewTheme x) => x.minHeight),
      barHorizontalPadding: l((M3ESearchViewTheme x) => x.barHorizontalPadding),
      maxWidth: l((M3ESearchViewTheme x) => x.maxWidth),
      maxHeightFactor: l((M3ESearchViewTheme x) => x.maxHeightFactor),
      compactBreakpoint: l((M3ESearchViewTheme x) => x.compactBreakpoint),
      containedElevation: l((M3ESearchViewTheme x) => x.containedElevation),
      containedLeadingMargin: l(
        (M3ESearchViewTheme x) => x.containedLeadingMargin,
      ),
      containedTrailingMargin: l(
        (M3ESearchViewTheme x) => x.containedTrailingMargin,
      ),
      containedBarHeight: l((M3ESearchViewTheme x) => x.containedBarHeight),
      dockedBarResultsGap: l((M3ESearchViewTheme x) => x.dockedBarResultsGap),
      dockedResultsRadius: l((M3ESearchViewTheme x) => x.dockedResultsRadius),
      dockedResultsInset: l((M3ESearchViewTheme x) => x.dockedResultsInset),
      dockedResultsBottomPadding: l(
        (M3ESearchViewTheme x) => x.dockedResultsBottomPadding,
      ),
      dividedDockedHeaderHeight: l(
        (M3ESearchViewTheme x) => x.dividedDockedHeaderHeight,
      ),
      dividedDockedListPadding: l(
        (M3ESearchViewTheme x) => x.dividedDockedListPadding,
      ),
      fullScreenRadius: l((M3ESearchViewTheme x) => x.fullScreenRadius),
      scrimOpacity: l((M3ESearchViewTheme x) => x.scrimOpacity),
      predictiveBackMinScale: l(
        (M3ESearchViewTheme x) => x.predictiveBackMinScale,
      ),
      predictiveBackEdgeMargin: l(
        (M3ESearchViewTheme x) => x.predictiveBackEdgeMargin,
      ),
      predictiveBackMaxOffsetY: l(
        (M3ESearchViewTheme x) => x.predictiveBackMaxOffsetY,
      ),
      containedFullScreenBarVerticalPadding: l(
        (M3ESearchViewTheme x) => x.containedFullScreenBarVerticalPadding,
      ),
      containedFullScreenResultsInset: l(
        (M3ESearchViewTheme x) => x.containedFullScreenResultsInset,
      ),
      dividedResultsInset: l((M3ESearchViewTheme x) => x.dividedResultsInset),
      shrinkWrap: b.shrinkWrap,
      style: b.style,
      containerTransformSpring: b.containerTransformSpring,
      fadeSpring: b.fadeSpring,
      predictiveBackSpring: b.predictiveBackSpring,
    );
  }

  double _lerp(double a, double b, double t) => a + (b - a) * t;
}
