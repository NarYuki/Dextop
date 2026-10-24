import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../floating_action_buttons/enums/m3e_fab.dart';
import '../../floating_action_buttons/styles/m3e_fab_theme.dart';
import '../enums/m3e_fab_menu_color_set.dart';

/// Theme values for `M3EFabMenu`.
@immutable
class M3EFabMenuTheme extends M3EThemeExtension<M3EFabMenuTheme> {
  /// Creates FAB menu theme tokens.
  const M3EFabMenuTheme({
    this.menuOffset = 8,
    this.scrimOpacity = 0.0,
    this.itemGap = 4,
    this.itemHeight = 56,
    this.itemLeading = 24,
    this.itemTrailing = 24,
    this.iconSize = 24,
    this.iconLabelGap = 8,
    this.closeIconSize = 20,
    this.itemElevation = 0,
    this.closeElevation = M3EElevation.level3,
    this.closeHoverElevation = M3EElevation.level4,
    this.openFabContainer = 56,
    this.closedFabContainer,
    this.focusRingWidth = 3,
    this.focusRingGap = 2,
    this.focusRingColor,
    this.itemBorderWidth = 1,
    this.itemOutlineColor,
    this.itemOutlineGradient,
    this.itemBackgroundGradient,
    this.itemForegroundGradient,
    this.expandSpring = const M3ESpring(stiffness: 380, damping: 0.55),
    this.fabShapeSpring = const M3ESpring(stiffness: 380, damping: 0.7),
    this.expandStagger = const Duration(milliseconds: 30),
  });

  /// Package defaults.
  static const M3EFabMenuTheme defaults = M3EFabMenuTheme();

  /// Vertical gap between the close FAB and the nearest menu item. Spec: 8.
  final double menuOffset;

  /// Scrim opacity over [M3EColorScheme.scrim].
  final double scrimOpacity;

  /// Spacing between adjacent menu items. Spec: 4.
  final double itemGap;

  /// Menu item height. Spec: 56.
  final double itemHeight;

  /// Start padding inside an item. Spec: 24.
  final double itemLeading;

  /// End padding inside an item. Spec: 24.
  final double itemTrailing;

  /// Item icon size. Spec: 24.
  final double iconSize;

  /// Gap between item icon and label. Spec: 8.
  final double iconLabelGap;

  /// Close FAB icon size when open. Spec: 20.
  final double closeIconSize;

  /// Item surface elevation. Defaults to 0.
  final double itemElevation;

  /// Close FAB resting elevation. Spec unclear; defaults to level 3.
  final double closeElevation;

  /// Close FAB hover elevation. Spec unclear; defaults to level 4.
  final double closeHoverElevation;

  /// Close FAB size when open. Spec: 56.
  final double openFabContainer;

  /// Optional override for closed FAB size; null uses [M3EFabSize] metrics.
  final double? closedFabContainer;

  /// Focus ring stroke width. Spec unclear; defaults to 3.
  final double focusRingWidth;

  /// Gap between surface and focus ring. Spec unclear; defaults to 2.
  final double focusRingGap;

  /// Focus ring color; defaults to secondary.
  final Color? focusRingColor;

  /// Item outline thickness when an outline is set.
  final double itemBorderWidth;

  /// Solid outline for item pills.
  final Color? itemOutlineColor;

  /// Gradient outline for item pills. Wins over [itemOutlineColor].
  final Gradient? itemOutlineGradient;

  /// Gradient fill for item pills, replacing resolved container color.
  final Gradient? itemBackgroundGradient;

  /// Gradient tint for item icons and labels.
  final Gradient? itemForegroundGradient;

  /// Menu item expand / collapse spring.
  final M3ESpring expandSpring;

  /// FAB container shape morph spring.
  final M3ESpring fabShapeSpring;

  /// Stagger between cascading item reveals.
  final Duration expandStagger;

  /// Scrim color for the dismiss barrier.
  Color scrimColor(M3EColorScheme scheme) =>
      scheme.scrim.withValues(alpha: scrimOpacity);

  /// Resolved focus ring color.
  Color resolveFocusRingColor(M3EColorScheme scheme) =>
      focusRingColor ?? scheme.secondary;

  /// Maps a FAB color style to a menu color set.
  static M3EFabMenuColorSet colorSetFor(M3EFabColor color) {
    switch (color) {
      case M3EFabColor.secondary:
      case M3EFabColor.secondaryFilled:
        return M3EFabMenuColorSet.secondary;
      case M3EFabColor.tertiary:
      case M3EFabColor.tertiaryFilled:
        return M3EFabMenuColorSet.tertiary;
      case M3EFabColor.primary:
      case M3EFabColor.primaryFilled:
      case M3EFabColor.surface:
        return M3EFabMenuColorSet.primary;
    }
  }

  /// Filled [M3EFabColor] for the open close button of [set].
  static M3EFabColor closeFabColor(M3EFabMenuColorSet set) {
    switch (set) {
      case M3EFabMenuColorSet.primary:
        return M3EFabColor.primaryFilled;
      case M3EFabMenuColorSet.secondary:
        return M3EFabColor.secondaryFilled;
      case M3EFabMenuColorSet.tertiary:
        return M3EFabColor.tertiaryFilled;
    }
  }

  /// Closed container size for [size], using theme override when set.
  double resolveClosedContainer({
    required M3EFabSize size,
    required M3EFabTheme fabTheme,
  }) {
    final override = closedFabContainer;
    if (override != null) {
      return override;
    }
    switch (size) {
      case M3EFabSize.small:
        return fabTheme.smallContainer;
      case M3EFabSize.regular:
        return fabTheme.regularContainer;
      case M3EFabSize.medium:
        return fabTheme.mediumContainer;
      case M3EFabSize.large:
        return fabTheme.largeContainer;
    }
  }

  /// Item container color for [set].
  Color itemContainerColor(M3EColorScheme scheme, M3EFabMenuColorSet set) {
    switch (set) {
      case M3EFabMenuColorSet.primary:
        return scheme.primaryContainer;
      case M3EFabMenuColorSet.secondary:
        return scheme.secondaryContainer;
      case M3EFabMenuColorSet.tertiary:
        return scheme.tertiaryContainer;
    }
  }

  /// Item icon/label color for [set].
  Color itemForegroundColor(M3EColorScheme scheme, M3EFabMenuColorSet set) {
    switch (set) {
      case M3EFabMenuColorSet.primary:
        return scheme.onPrimaryContainer;
      case M3EFabMenuColorSet.secondary:
        return scheme.onSecondaryContainer;
      case M3EFabMenuColorSet.tertiary:
        return scheme.onTertiaryContainer;
    }
  }

  /// Close container color for [set] (filled role).
  Color closeContainerColor(M3EColorScheme scheme, M3EFabMenuColorSet set) {
    switch (set) {
      case M3EFabMenuColorSet.primary:
        return scheme.primary;
      case M3EFabMenuColorSet.secondary:
        return scheme.secondary;
      case M3EFabMenuColorSet.tertiary:
        return scheme.tertiary;
    }
  }

  /// Close icon color for [set] (on-filled role).
  Color closeForegroundColor(M3EColorScheme scheme, M3EFabMenuColorSet set) {
    switch (set) {
      case M3EFabMenuColorSet.primary:
        return scheme.onPrimary;
      case M3EFabMenuColorSet.secondary:
        return scheme.onSecondary;
      case M3EFabMenuColorSet.tertiary:
        return scheme.onTertiary;
    }
  }

  /// Item label [TextStyle] (titleMedium).
  TextStyle itemLabelStyle(
    M3ETypeScale type,
    M3EColorScheme scheme,
    M3EFabMenuColorSet set,
  ) => type.titleMedium.copyWith(color: itemForegroundColor(scheme, set));

  @override
  M3EFabMenuTheme copyWith({
    double? menuOffset,
    double? scrimOpacity,
    double? itemGap,
    double? itemHeight,
    double? itemLeading,
    double? itemTrailing,
    double? iconSize,
    double? iconLabelGap,
    double? closeIconSize,
    double? itemElevation,
    double? closeElevation,
    double? closeHoverElevation,
    double? openFabContainer,
    double? closedFabContainer,
    bool clearClosedFabContainer = false,
    double? focusRingWidth,
    double? focusRingGap,
    Color? focusRingColor,
    bool clearFocusRingColor = false,
    double? itemBorderWidth,
    Color? itemOutlineColor,
    Gradient? itemOutlineGradient,
    Gradient? itemBackgroundGradient,
    Gradient? itemForegroundGradient,
    M3ESpring? expandSpring,
    M3ESpring? fabShapeSpring,
    Duration? expandStagger,
  }) {
    return M3EFabMenuTheme(
      menuOffset: menuOffset ?? this.menuOffset,
      scrimOpacity: scrimOpacity ?? this.scrimOpacity,
      itemGap: itemGap ?? this.itemGap,
      itemHeight: itemHeight ?? this.itemHeight,
      itemLeading: itemLeading ?? this.itemLeading,
      itemTrailing: itemTrailing ?? this.itemTrailing,
      iconSize: iconSize ?? this.iconSize,
      iconLabelGap: iconLabelGap ?? this.iconLabelGap,
      closeIconSize: closeIconSize ?? this.closeIconSize,
      itemElevation: itemElevation ?? this.itemElevation,
      closeElevation: closeElevation ?? this.closeElevation,
      closeHoverElevation: closeHoverElevation ?? this.closeHoverElevation,
      openFabContainer: openFabContainer ?? this.openFabContainer,
      closedFabContainer: clearClosedFabContainer
          ? null
          : (closedFabContainer ?? this.closedFabContainer),
      focusRingWidth: focusRingWidth ?? this.focusRingWidth,
      focusRingGap: focusRingGap ?? this.focusRingGap,
      focusRingColor: clearFocusRingColor
          ? null
          : (focusRingColor ?? this.focusRingColor),
      itemBorderWidth: itemBorderWidth ?? this.itemBorderWidth,
      itemOutlineColor: itemOutlineColor ?? this.itemOutlineColor,
      itemOutlineGradient: itemOutlineGradient ?? this.itemOutlineGradient,
      itemBackgroundGradient:
          itemBackgroundGradient ?? this.itemBackgroundGradient,
      itemForegroundGradient:
          itemForegroundGradient ?? this.itemForegroundGradient,
      expandSpring: expandSpring ?? this.expandSpring,
      fabShapeSpring: fabShapeSpring ?? this.fabShapeSpring,
      expandStagger: expandStagger ?? this.expandStagger,
    );
  }

  @override
  M3EFabMenuTheme lerp(M3EFabMenuTheme? other, double t) {
    if (other is! M3EFabMenuTheme) {
      return this;
    }
    return M3EFabMenuTheme(
      menuOffset: _lerpDouble(menuOffset, other.menuOffset, t)!,
      scrimOpacity: _lerpDouble(scrimOpacity, other.scrimOpacity, t)!,
      itemGap: _lerpDouble(itemGap, other.itemGap, t)!,
      itemHeight: _lerpDouble(itemHeight, other.itemHeight, t)!,
      itemLeading: _lerpDouble(itemLeading, other.itemLeading, t)!,
      itemTrailing: _lerpDouble(itemTrailing, other.itemTrailing, t)!,
      iconSize: _lerpDouble(iconSize, other.iconSize, t)!,
      iconLabelGap: _lerpDouble(iconLabelGap, other.iconLabelGap, t)!,
      closeIconSize: _lerpDouble(closeIconSize, other.closeIconSize, t)!,
      itemElevation: _lerpDouble(itemElevation, other.itemElevation, t)!,
      closeElevation: _lerpDouble(closeElevation, other.closeElevation, t)!,
      closeHoverElevation: _lerpDouble(
        closeHoverElevation,
        other.closeHoverElevation,
        t,
      )!,
      openFabContainer: _lerpDouble(
        openFabContainer,
        other.openFabContainer,
        t,
      )!,
      closedFabContainer: t < 0.5
          ? closedFabContainer
          : other.closedFabContainer,
      focusRingWidth: _lerpDouble(focusRingWidth, other.focusRingWidth, t)!,
      focusRingGap: _lerpDouble(focusRingGap, other.focusRingGap, t)!,
      focusRingColor:
          Color.lerp(focusRingColor, other.focusRingColor, t) ??
          focusRingColor ??
          other.focusRingColor,
      itemBorderWidth: _lerpDouble(itemBorderWidth, other.itemBorderWidth, t)!,
      itemOutlineColor: Color.lerp(itemOutlineColor, other.itemOutlineColor, t),
      itemOutlineGradient: t < 0.5
          ? itemOutlineGradient
          : other.itemOutlineGradient,
      itemBackgroundGradient: t < 0.5
          ? itemBackgroundGradient
          : other.itemBackgroundGradient,
      itemForegroundGradient: t < 0.5
          ? itemForegroundGradient
          : other.itemForegroundGradient,
      expandSpring: t < 0.5 ? expandSpring : other.expandSpring,
      fabShapeSpring: t < 0.5 ? fabShapeSpring : other.fabShapeSpring,
      expandStagger: t < 0.5 ? expandStagger : other.expandStagger,
    );
  }

  double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
