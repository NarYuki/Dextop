import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_app_bar_enums.dart';

/// Resolved height, padding, and icon metrics for an app bar.
///
/// Heights are the content band only. The system inset sits outside that band.
@immutable
class M3EAppBarMetrics {
  /// M3EAppBarMetrics.
  const M3EAppBarMetrics({
    required this.smallHeight,
    required this.collapsedHeight,
    required this.mediumExpanded,
    required this.mediumFlexibleExpanded,
    required this.largeExpanded,
    required this.largeFlexibleExpanded,
    required this.mediumSubtitleExtra,
    required this.largeSubtitleExtra,
    required this.contentPadding,
    required this.iconSize,
    required this.elevation,
    required this.scrolledElevation,
    required this.titleInset,
    required this.flexibleBottomPadding,
  });

  /// Small content band height.
  final double smallHeight;

  /// Collapsed content band height.
  final double collapsedHeight;

  /// Baseline medium content height, without a subtitle.
  final double mediumExpanded;

  /// Flexible medium content height, without a subtitle.
  final double mediumFlexibleExpanded;

  /// Baseline large content height, without a subtitle.
  final double largeExpanded;

  /// Flexible large content height, without a subtitle.
  final double largeFlexibleExpanded;

  /// Extra content height for a medium subtitle line.
  final double mediumSubtitleExtra;

  /// Extra content height for a large subtitle line.
  final double largeSubtitleExtra;

  /// contentPadding.
  final EdgeInsetsGeometry contentPadding;

  /// iconSize.
  final double iconSize;

  /// elevation.
  final double elevation;

  /// Elevation when content is scrolled under the bar.
  final double scrolledElevation;

  /// Horizontal inset of the expanded title.
  final double titleInset;

  /// Bottom inset of the expanded title.
  final double flexibleBottomPadding;

  /// Content-band height for [variant].
  ///
  /// A small bar keeps [smallHeight] when a subtitle is present. Other
  /// variants add the subtitle line on top of the base height.
  double expandedHeight(M3EAppBarVariant variant, {required bool hasSubtitle}) {
    final double base = switch (variant) {
      M3EAppBarVariant.small => smallHeight,
      M3EAppBarVariant.medium => mediumExpanded,
      M3EAppBarVariant.mediumFlexible => mediumFlexibleExpanded,
      M3EAppBarVariant.large => largeExpanded,
      M3EAppBarVariant.largeFlexible => largeFlexibleExpanded,
    };
    if (!hasSubtitle || variant == M3EAppBarVariant.small) {
      return base;
    }
    final double extra = switch (variant) {
      M3EAppBarVariant.medium ||
      M3EAppBarVariant.mediumFlexible => mediumSubtitleExtra,
      M3EAppBarVariant.large ||
      M3EAppBarVariant.largeFlexible => largeSubtitleExtra,
      M3EAppBarVariant.small => 0,
    };
    return base + extra;
  }
}

/// Theme values for `M3EAppBar`.
@immutable
class M3EAppBarTheme extends M3EThemeExtension<M3EAppBarTheme> {
  /// M3EAppBarTheme.
  const M3EAppBarTheme({
    this.contentPadding = const EdgeInsets.symmetric(horizontal: 4),
    this.titleGap = 8,
    this.iconSize = 24,
    this.elevation = M3EElevation.level0,
    this.scrolledElevation = M3EElevation.level0,
    this.compactHeightReduction = 8,
    this.smallHeight = 64,
    this.collapsedHeight = 64,
    this.mediumExpanded = 112,
    this.mediumFlexibleExpanded = 112,
    this.largeExpanded = 152,
    this.largeFlexibleExpanded = 120,
    this.mediumSubtitleExtra = 24,
    this.largeSubtitleExtra = 32,
    this.titleInset = 16,
    this.flexibleTopPadding = 8,
    this.flexibleBottomPadding = 12,
    this.actionRowHeight = 48,
    this.avatarSize = 32,
    this.searchBarHeight = 56,
    this.searchBarRadius = 28,
    this.searchElevation = M3EElevation.level0,
    this.searchViewElevation = M3EElevation.level0,
    this.searchFullScreenHeader = 72,
    this.searchDockedHeader = 56,
    this.searchOuterMargin = 4,
    this.focusRingWidth = 3,
    this.focusRingOffset = 2,
    this.hoverOpacity = 0.08,
    this.pressedOpacity = 0.1,
    this.bottomHeight = 80,
    this.bottomIconSize = 24,
    this.bottomPadding = const EdgeInsets.symmetric(vertical: 8),
  });

  /// defaults.

  static const M3EAppBarTheme defaults = M3EAppBarTheme();

  /// Padding around the toolbar content row (inside the bar, outside safe area).
  final EdgeInsetsGeometry contentPadding;

  /// Horizontal inset on the title/search slot (both sides).
  final double titleGap;

  /// iconSize.
  final double iconSize;

  /// Resting elevation.
  final double elevation;

  /// Elevation when content is scrolled under the bar.
  final double scrolledElevation;

  /// compactHeightReduction.
  final double compactHeightReduction;

  /// smallHeight.
  final double smallHeight;

  /// collapsedHeight.
  final double collapsedHeight;

  /// Baseline medium height without a subtitle.
  final double mediumExpanded;

  /// Flexible medium height without a subtitle.
  final double mediumFlexibleExpanded;

  /// Baseline large height without a subtitle.
  final double largeExpanded;

  /// Flexible large height without a subtitle.
  final double largeFlexibleExpanded;

  /// Extra height for a medium subtitle.
  final double mediumSubtitleExtra;

  /// Extra height for a large subtitle.
  final double largeSubtitleExtra;

  /// Horizontal inset of the expanded title.
  final double titleInset;

  /// Top padding of a flexible bar's content band.
  final double flexibleTopPadding;

  /// Bottom padding of a flexible bar's content band.
  final double flexibleBottomPadding;

  /// Height of the flexible action row.
  final double actionRowHeight;

  /// Circular avatar diameter.
  final double avatarSize;

  /// Search field height inside the 64dp bar row.
  final double searchBarHeight;

  /// Search field corner radius.
  final double searchBarRadius;

  /// Search field elevation.
  final double searchElevation;

  /// Search view elevation.
  final double searchViewElevation;

  /// Full-screen search view header height.
  final double searchFullScreenHeader;

  /// Docked search view header height.
  final double searchDockedHeader;

  /// Outer side margin of the search row.
  final double searchOuterMargin;

  /// Focus ring thickness.
  final double focusRingWidth;

  /// Focus ring outset.
  final double focusRingOffset;

  /// Search hover state-layer opacity.
  final double hoverOpacity;

  /// Search pressed state-layer opacity.
  final double pressedOpacity;

  /// bottomHeight.
  final double bottomHeight;

  /// bottomIconSize.
  final double bottomIconSize;

  /// bottomPadding.
  final EdgeInsetsGeometry bottomPadding;

  /// metrics.

  M3EAppBarMetrics metrics(M3EAppBarDensity density) {
    var small = smallHeight;
    var collapsed = collapsedHeight;
    var medium = mediumExpanded;
    var mediumFlexible = mediumFlexibleExpanded;
    var large = largeExpanded;
    var largeFlexible = largeFlexibleExpanded;

    if (density == M3EAppBarDensity.compact) {
      small -= compactHeightReduction;
      collapsed -= compactHeightReduction;
      medium -= compactHeightReduction;
      mediumFlexible -= compactHeightReduction;
      large -= compactHeightReduction;
      largeFlexible -= compactHeightReduction;
    }

    return M3EAppBarMetrics(
      smallHeight: small,
      collapsedHeight: collapsed,
      mediumExpanded: medium,
      mediumFlexibleExpanded: mediumFlexible,
      largeExpanded: large,
      largeFlexibleExpanded: largeFlexible,
      mediumSubtitleExtra: mediumSubtitleExtra,
      largeSubtitleExtra: largeSubtitleExtra,
      contentPadding: contentPadding,
      iconSize: iconSize,
      elevation: elevation,
      scrolledElevation: scrolledElevation,
      titleInset: titleInset,
      flexibleBottomPadding: flexibleBottomPadding,
    );
  }

  /// Resting container color.
  Color backgroundColor(M3EColorScheme scheme) => scheme.surface;

  /// Container color when content is scrolled under the bar.
  Color scrolledBackgroundColor(M3EColorScheme scheme) =>
      scheme.surfaceContainer;

  /// bottomBackgroundColor.

  Color bottomBackgroundColor(M3EColorScheme scheme) => scheme.surfaceContainer;

  /// Title color.
  Color titleColor(M3EColorScheme scheme) => scheme.onSurface;

  /// Subtitle color.
  Color subtitleColor(M3EColorScheme scheme) => scheme.onSurfaceVariant;

  /// Leading icon color.
  Color leadingColor(M3EColorScheme scheme) => scheme.onSurface;

  /// Trailing icon color.
  Color trailingColor(M3EColorScheme scheme) => scheme.onSurfaceVariant;

  /// Search field fill. Dark schemes may use [M3EColorScheme.surfaceBright].
  Color searchFieldColor(M3EColorScheme scheme, {required bool scrolledUnder}) {
    if (scrolledUnder) {
      return scheme.surfaceContainerHighest;
    }
    if (scheme.brightness == Brightness.dark) {
      return scheme.surfaceBright;
    }
    return scheme.surfaceContainerHigh;
  }

  /// Search view container color.
  Color searchViewColor(M3EColorScheme scheme) => scheme.surfaceContainerHigh;

  /// Tonal fill behind actions while the rest of the bar is hidden.
  Color actionsTonalColor(M3EColorScheme scheme) => scheme.surfaceContainerHigh;

  /// titleStyle.

  TextStyle titleStyle(
    M3ETypeScale type, {
    bool collapsed = true,
    M3EAppBarVariant variant = M3EAppBarVariant.medium,
  }) {
    if (collapsed) {
      return type.titleLarge;
    }
    return switch (variant) {
      M3EAppBarVariant.small => type.titleLarge,
      M3EAppBarVariant.medium => type.headlineSmall,
      M3EAppBarVariant.mediumFlexible => type.headlineMedium,
      M3EAppBarVariant.large ||
      M3EAppBarVariant.largeFlexible => type.displaySmall,
    };
  }

  /// Subtitle type role for [variant].
  TextStyle subtitleStyle(M3ETypeScale type, M3EAppBarVariant variant) {
    return switch (variant) {
      M3EAppBarVariant.small || M3EAppBarVariant.medium => type.labelMedium,
      M3EAppBarVariant.mediumFlexible => type.labelLarge,
      M3EAppBarVariant.large ||
      M3EAppBarVariant.largeFlexible => type.titleMedium,
    };
  }

  /// Search input text.
  TextStyle searchTextStyle(M3ETypeScale type, M3EColorScheme scheme) =>
      type.bodyLarge.copyWith(color: scheme.onSurface);

  /// Search hint text.
  TextStyle searchHintStyle(M3ETypeScale type, M3EColorScheme scheme) =>
      type.bodyLarge.copyWith(color: scheme.onSurfaceVariant);

  /// shape.

  ShapeBorder shape(M3EAppBarShapeFamily family) {
    final radius = family == M3EAppBarShapeFamily.round
        ? M3EShapes.radiusSmall
        : M3EShapes.radiusNone;
    return RoundedRectangleBorder(borderRadius: radius);
  }

  @override
  M3EAppBarTheme copyWith({
    EdgeInsetsGeometry? contentPadding,
    double? titleGap,
    double? iconSize,
    double? elevation,
    double? scrolledElevation,
    double? compactHeightReduction,
    double? smallHeight,
    double? collapsedHeight,
    double? mediumExpanded,
    double? mediumFlexibleExpanded,
    double? largeExpanded,
    double? largeFlexibleExpanded,
    double? mediumSubtitleExtra,
    double? largeSubtitleExtra,
    double? titleInset,
    double? flexibleTopPadding,
    double? flexibleBottomPadding,
    double? actionRowHeight,
    double? avatarSize,
    double? searchBarHeight,
    double? searchBarRadius,
    double? searchElevation,
    double? searchViewElevation,
    double? searchFullScreenHeader,
    double? searchDockedHeader,
    double? searchOuterMargin,
    double? focusRingWidth,
    double? focusRingOffset,
    double? hoverOpacity,
    double? pressedOpacity,
    double? bottomHeight,
    double? bottomIconSize,
    EdgeInsetsGeometry? bottomPadding,
  }) {
    return M3EAppBarTheme(
      contentPadding: contentPadding ?? this.contentPadding,
      titleGap: titleGap ?? this.titleGap,
      iconSize: iconSize ?? this.iconSize,
      elevation: elevation ?? this.elevation,
      scrolledElevation: scrolledElevation ?? this.scrolledElevation,
      compactHeightReduction:
          compactHeightReduction ?? this.compactHeightReduction,
      smallHeight: smallHeight ?? this.smallHeight,
      collapsedHeight: collapsedHeight ?? this.collapsedHeight,
      mediumExpanded: mediumExpanded ?? this.mediumExpanded,
      mediumFlexibleExpanded:
          mediumFlexibleExpanded ?? this.mediumFlexibleExpanded,
      largeExpanded: largeExpanded ?? this.largeExpanded,
      largeFlexibleExpanded:
          largeFlexibleExpanded ?? this.largeFlexibleExpanded,
      mediumSubtitleExtra: mediumSubtitleExtra ?? this.mediumSubtitleExtra,
      largeSubtitleExtra: largeSubtitleExtra ?? this.largeSubtitleExtra,
      titleInset: titleInset ?? this.titleInset,
      flexibleTopPadding: flexibleTopPadding ?? this.flexibleTopPadding,
      flexibleBottomPadding:
          flexibleBottomPadding ?? this.flexibleBottomPadding,
      actionRowHeight: actionRowHeight ?? this.actionRowHeight,
      avatarSize: avatarSize ?? this.avatarSize,
      searchBarHeight: searchBarHeight ?? this.searchBarHeight,
      searchBarRadius: searchBarRadius ?? this.searchBarRadius,
      searchElevation: searchElevation ?? this.searchElevation,
      searchViewElevation: searchViewElevation ?? this.searchViewElevation,
      searchFullScreenHeader:
          searchFullScreenHeader ?? this.searchFullScreenHeader,
      searchDockedHeader: searchDockedHeader ?? this.searchDockedHeader,
      searchOuterMargin: searchOuterMargin ?? this.searchOuterMargin,
      focusRingWidth: focusRingWidth ?? this.focusRingWidth,
      focusRingOffset: focusRingOffset ?? this.focusRingOffset,
      hoverOpacity: hoverOpacity ?? this.hoverOpacity,
      pressedOpacity: pressedOpacity ?? this.pressedOpacity,
      bottomHeight: bottomHeight ?? this.bottomHeight,
      bottomIconSize: bottomIconSize ?? this.bottomIconSize,
      bottomPadding: bottomPadding ?? this.bottomPadding,
    );
  }

  @override
  M3EAppBarTheme lerp(M3EAppBarTheme? other, double t) {
    if (other is! M3EAppBarTheme) {
      return this;
    }
    return _lerpGeometry(
      other,
      t,
    )._lerpSearch(other, t)._lerpSurfaceStates(other, t);
  }

  /// Bar heights, paddings, and icon sizing.
  M3EAppBarTheme _lerpGeometry(M3EAppBarTheme other, double t) {
    return copyWith(
      contentPadding:
          EdgeInsetsGeometry.lerp(contentPadding, other.contentPadding, t) ??
          contentPadding,
      titleGap: _lerpDouble(titleGap, other.titleGap, t),
      iconSize: _lerpDouble(iconSize, other.iconSize, t),
      elevation: _lerpDouble(elevation, other.elevation, t),
      scrolledElevation: _lerpDouble(
        scrolledElevation,
        other.scrolledElevation,
        t,
      ),
      compactHeightReduction: _lerpDouble(
        compactHeightReduction,
        other.compactHeightReduction,
        t,
      ),
      smallHeight: _lerpDouble(smallHeight, other.smallHeight, t),
      collapsedHeight: _lerpDouble(collapsedHeight, other.collapsedHeight, t),
      mediumExpanded: _lerpDouble(mediumExpanded, other.mediumExpanded, t),
      mediumFlexibleExpanded: _lerpDouble(
        mediumFlexibleExpanded,
        other.mediumFlexibleExpanded,
        t,
      ),
      largeExpanded: _lerpDouble(largeExpanded, other.largeExpanded, t),
      largeFlexibleExpanded: _lerpDouble(
        largeFlexibleExpanded,
        other.largeFlexibleExpanded,
        t,
      ),
      mediumSubtitleExtra: _lerpDouble(
        mediumSubtitleExtra,
        other.mediumSubtitleExtra,
        t,
      ),
      largeSubtitleExtra: _lerpDouble(
        largeSubtitleExtra,
        other.largeSubtitleExtra,
        t,
      ),
      titleInset: _lerpDouble(titleInset, other.titleInset, t),
      flexibleTopPadding: _lerpDouble(
        flexibleTopPadding,
        other.flexibleTopPadding,
        t,
      ),
      flexibleBottomPadding: _lerpDouble(
        flexibleBottomPadding,
        other.flexibleBottomPadding,
        t,
      ),
      actionRowHeight: _lerpDouble(actionRowHeight, other.actionRowHeight, t),
      avatarSize: _lerpDouble(avatarSize, other.avatarSize, t),
    );
  }

  /// Search bar/view sizing and elevation.
  M3EAppBarTheme _lerpSearch(M3EAppBarTheme other, double t) {
    return copyWith(
      searchBarHeight: _lerpDouble(searchBarHeight, other.searchBarHeight, t),
      searchBarRadius: _lerpDouble(searchBarRadius, other.searchBarRadius, t),
      searchElevation: _lerpDouble(searchElevation, other.searchElevation, t),
      searchViewElevation: _lerpDouble(
        searchViewElevation,
        other.searchViewElevation,
        t,
      ),
      searchFullScreenHeader: _lerpDouble(
        searchFullScreenHeader,
        other.searchFullScreenHeader,
        t,
      ),
      searchDockedHeader: _lerpDouble(
        searchDockedHeader,
        other.searchDockedHeader,
        t,
      ),
      searchOuterMargin: _lerpDouble(
        searchOuterMargin,
        other.searchOuterMargin,
        t,
      ),
    );
  }

  /// Focus ring, hover/pressed opacity, and the bottom bar band.
  M3EAppBarTheme _lerpSurfaceStates(M3EAppBarTheme other, double t) {
    return copyWith(
      focusRingWidth: _lerpDouble(focusRingWidth, other.focusRingWidth, t),
      focusRingOffset: _lerpDouble(focusRingOffset, other.focusRingOffset, t),
      hoverOpacity: _lerpDouble(hoverOpacity, other.hoverOpacity, t),
      pressedOpacity: _lerpDouble(pressedOpacity, other.pressedOpacity, t),
      bottomHeight: _lerpDouble(bottomHeight, other.bottomHeight, t),
      bottomIconSize: _lerpDouble(bottomIconSize, other.bottomIconSize, t),
      bottomPadding:
          EdgeInsetsGeometry.lerp(bottomPadding, other.bottomPadding, t) ??
          bottomPadding,
    );
  }

  double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
