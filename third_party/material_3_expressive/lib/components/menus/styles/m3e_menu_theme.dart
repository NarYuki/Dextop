import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/dropdown_menus/m3e_dropdown_menus.dart'
    show M3EDropdownMenu;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3EDropdownMenu;

import '../../../foundations/foundations.dart';
import '../enums/m3e_menu_color_style.dart';
import '../enums/m3e_menu_item_shape.dart';
import '../enums/m3e_menu_variant.dart';

part 'm3e_menu_theme_values.dart';

/// Resolved colors for one [M3EMenuColorStyle].
@immutable
class M3EMenuColors {
  /// M3EMenuColors.
  const M3EMenuColors({
    required this.container,
    required this.content,
    required this.iconContent,
    required this.supportingContent,
    required this.selectedContainer,
    required this.selectedContent,
    required this.stateLayer,
    required this.divider,
  });

  /// Elevated surface fill (callout 4).
  final Color container;

  /// Idle item label (callout 2 / 9).
  final Color content;

  /// Idle leading / trailing icons (callouts 1, 6).
  final Color iconContent;

  /// Idle supporting text, shortcuts, section labels (callouts 5, 8, 10).
  final Color supportingContent;

  /// Selected item fill (callout 7).
  final Color selectedContainer;

  /// Selected item icon / label (callouts 8 selected, 11).
  final Color selectedContent;

  /// Hover / focus / pressed overlay ink (callout 3).
  final Color stateLayer;

  /// divider.

  final Color divider;

  @override
  bool operator ==(Object other) {
    return other is M3EMenuColors &&
        other.container == container &&
        other.content == content &&
        other.iconContent == iconContent &&
        other.supportingContent == supportingContent &&
        other.selectedContainer == selectedContainer &&
        other.selectedContent == selectedContent &&
        other.stateLayer == stateLayer &&
        other.divider == divider;
  }

  @override
  int get hashCode => Object.hash(
    container,
    content,
    iconContent,
    supportingContent,
    selectedContainer,
    selectedContent,
    stateLayer,
    divider,
  );
}

/// Theme values for `M3EMenu`.
///
/// Field defaults match the expressive vertical menu. [baselineMetrics] is the
/// baseline form. [metricsFor] returns this theme for [M3EMenuVariant.vertical]
/// and [baselineMetrics] for [M3EMenuVariant.baseline].
@immutable
class M3EMenuTheme extends M3EThemeExtension<M3EMenuTheme> {
  /// M3EMenuTheme.
  const M3EMenuTheme({
    this.minWidth = 112,
    this.maxWidth = 280,
    this.maxHeight = 320,
    this.verticalPadding = 4,
    this.contentHorizontalPadding = 0,
    this.anchorOffset = 4,
    this.entryHeight = 48,
    this.entryHorizontalPadding = 8,
    this.entryVerticalPadding = 0,
    this.iconSize = 20,
    this.iconGap = 8,
    this.groupSpacing = 2,
    this.sectionGap = 2,
    this.groupLabelHorizontalPadding = 8,
    this.groupLabelVerticalPadding = 0,
    this.groupLabelHeight = 32,
    this.elevation = M3EElevation.level2,
    this.disabledOpacity = 0.38,
    this.scrimAlpha = 0.0,
    this.screenEdgePadding = 12,
    this.containerRadius = 16,
    this.containerBottomRadius = 12,
    this.itemRadius = 12,
    this.stateLayerInset = 4,
    this.focusIndicatorWidth = 3,
    this.focusIndicatorOffset = -3,
    this.focusIndicatorColor,
    this.backgroundColor,
    this.openMotion = M3EMotion.expressiveSpatialDefault,
    this.closeMotion = M3EMotion.expressiveSpatialDefault,
    this.openInstantly = false,
    this.itemGap = 4,
    this.dividerThickness = 1,
    this.dividerVerticalPadding = 0,
  });

  /// defaults.

  static const M3EMenuTheme defaults = M3EMenuTheme();

  /// Baseline menu metrics (4dp corners, 48dp rows, 24dp icons).
  static const M3EMenuTheme baselineMetrics = M3EMenuTheme(
    entryHorizontalPadding: 12,
    iconSize: 24,
    iconGap: 12,
    verticalPadding: 8,
    itemGap: 0,
    groupSpacing: 0,
    sectionGap: 0,
    groupLabelHorizontalPadding: 12,
    itemRadius: 4,
    containerRadius: 4,
    containerBottomRadius: 4,
    stateLayerInset: 0,
    dividerVerticalPadding: 8,
  );

  /// Metrics for [variant]. Vertical uses this theme. Baseline uses
  /// [baselineMetrics].
  M3EMenuTheme metricsFor(M3EMenuVariant variant) {
    return switch (variant) {
      M3EMenuVariant.vertical => this,
      M3EMenuVariant.baseline => baselineMetrics,
    };
  }

  /// minWidth.

  final double minWidth;

  /// maxWidth.
  final double maxWidth;

  /// maxHeight.
  final double maxHeight;

  /// Padding above and below the items inside each elevated surface.
  final double verticalPadding;

  /// Inset of the item column from the left/right of each elevated surface.
  final double contentHorizontalPadding;

  /// anchorOffset.

  final double anchorOffset;

  /// Minimum row height.
  final double entryHeight;

  /// Space from the item highlight edge to the icon or label.
  ///
  /// Combined with [stateLayerInset], this is the 12 from the container edge
  /// to the icon on the vertical measurements diagram.
  final double entryHorizontalPadding;

  /// Extra space inside [entryHeight]. The diagram row height already includes it.
  final double entryVerticalPadding;

  /// iconSize.
  final double iconSize;

  /// Space between an icon and the label, or between label and shortcut.
  final double iconGap;

  /// Legacy alias for spacing near groups; prefer [sectionGap] between surfaces.
  final double groupSpacing;

  /// Vertical gap between elevated menu surfaces.
  final double sectionGap;

  /// groupLabelHorizontalPadding.

  final double groupLabelHorizontalPadding;

  /// Extra vertical padding around a section label, inside [groupLabelHeight].
  final double groupLabelVerticalPadding;

  /// Section label row height.
  final double groupLabelHeight;

  /// elevation.
  final double elevation;

  /// disabledOpacity.
  final double disabledOpacity;

  /// scrimAlpha.
  final double scrimAlpha;

  /// screenEdgePadding.
  final double screenEdgePadding;

  /// Top corner radius of each elevated menu surface.
  final double containerRadius;

  /// Bottom corner radius of each elevated menu surface.
  final double containerBottomRadius;

  /// Corner radius of the item highlight. Vertical menus use 12.
  final double itemRadius;

  /// Horizontal inset from the surface edge to the item highlight.
  final double stateLayerInset;

  /// Focus ring stroke thickness.
  final double focusIndicatorWidth;

  /// Focus ring outline offset. Negative pulls the ring inward.
  final double focusIndicatorOffset;

  /// Focus ring color. Null resolves to [M3EColorScheme.secondary].
  final Color? focusIndicatorColor;

  /// When non-null, overrides the scheme-derived menu surface color.
  final Color? backgroundColor;

  /// Spring for expand — same default as [M3EDropdownMenu.openMotion].
  final M3ESpring openMotion;

  /// Spring for collapse — same default as [M3EDropdownMenu.closeMotion].
  final M3ESpring closeMotion;

  /// When true, the menu opens and closes without the spatial spring.
  final bool openInstantly;

  /// Space between items inside one surface. Distinct from [sectionGap].
  final double itemGap;

  /// Divider stroke thickness.
  final double dividerThickness;

  /// Space above and below a divider stroke.
  final double dividerVerticalPadding;

  /// The borderRadius.

  BorderRadius get borderRadius => BorderRadius.vertical(
    top: Radius.circular(containerRadius),
    bottom: Radius.circular(containerBottomRadius),
  );

  /// Outline of the elevated surface.
  ShapeBorder get containerShape =>
      RoundedRectangleBorder(borderRadius: borderRadius);

  /// The itemBorderRadius.

  BorderRadius get itemBorderRadius => BorderRadius.circular(itemRadius);

  /// Standalone / ungrouped item radius.
  BorderRadius get standaloneItemShape => itemBorderRadius;

  /// Leading item in a group — full radius (gapped items are not connected).
  BorderRadius get leadingItemShape => itemBorderRadius;

  /// The middleItemShape.

  BorderRadius get middleItemShape => itemBorderRadius;

  /// Trailing item in a group — full radius (gapped items are not connected).
  BorderRadius get trailingItemShape => itemBorderRadius;

  /// itemShape.

  BorderRadius itemShape(M3EMenuItemShape shape) {
    return switch (shape) {
      M3EMenuItemShape.standalone => standaloneItemShape,
      M3EMenuItemShape.leading => leadingItemShape,
      M3EMenuItemShape.middle => middleItemShape,
      M3EMenuItemShape.trailing => trailingItemShape,
    };
  }

  /// Applies Compose-style leading/middle/trailing shapes for [index] in [count].
  M3EMenuItemShape shapeForIndex(int index, int count) {
    if (count <= 1) {
      return M3EMenuItemShape.standalone;
    }
    if (index == 0) {
      return M3EMenuItemShape.leading;
    }
    if (index == count - 1) {
      return M3EMenuItemShape.trailing;
    }
    return M3EMenuItemShape.middle;
  }

  /// Color roles for [style].
  M3EMenuColors colors(
    M3EColorScheme scheme, [
    M3EMenuColorStyle style = M3EMenuColorStyle.standard,
  ]) => _menuThemeColors(this, scheme, style);

  /// containerColor.

  Color containerColor(
    M3EColorScheme scheme, [
    M3EMenuColorStyle style = M3EMenuColorStyle.standard,
  ]) => colors(scheme, style).container;

  /// dividerColor.

  Color dividerColor(
    M3EColorScheme scheme, [
    M3EMenuColorStyle style = M3EMenuColorStyle.standard,
  ]) => colors(scheme, style).divider;

  /// selectedContainerColor.

  Color selectedContainerColor(
    M3EColorScheme scheme, [
    M3EMenuColorStyle style = M3EMenuColorStyle.standard,
  ]) => colors(scheme, style).selectedContainer;

  /// scrimColor.

  Color scrimColor(M3EColorScheme scheme) =>
      M3EColorUtils.withOpacity(scheme.scrim, scrimAlpha);

  /// Focus ring color for [scheme].
  Color focusRingColor(M3EColorScheme scheme) =>
      focusIndicatorColor ?? scheme.secondary;

  /// entryForegroundColor.

  Color entryForegroundColor(
    M3EColorScheme scheme, {
    required bool enabled,
    bool isDestructive = false,
    bool selected = false,
    M3EMenuColorStyle style = M3EMenuColorStyle.standard,
  }) {
    final palette = colors(scheme, style);
    final Color base;
    if (isDestructive) {
      base = scheme.error;
    } else if (selected) {
      base = palette.selectedContent;
    } else {
      base = palette.content;
    }
    if (!enabled) {
      return M3EColorUtils.withOpacity(base, disabledOpacity);
    }
    return base;
  }

  /// Leading / trailing icon color.
  ///
  /// Vibrant hover, focus, and press use [M3EColorScheme.tertiary]. Standard
  /// icons stay on [M3EMenuColors.iconContent].
  Color entryIconForegroundColor(
    M3EColorScheme scheme, {
    required bool enabled,
    bool isDestructive = false,
    bool selected = false,
    bool hovered = false,
    bool focused = false,
    bool pressed = false,
    M3EMenuColorStyle style = M3EMenuColorStyle.standard,
  }) {
    final palette = colors(scheme, style);
    final Color base;
    if (isDestructive) {
      base = scheme.error;
    } else if (selected) {
      base = palette.selectedContent;
    } else if (style == M3EMenuColorStyle.vibrant &&
        (hovered || focused || pressed)) {
      base = scheme.tertiary;
    } else {
      base = palette.iconContent;
    }
    if (!enabled) {
      return M3EColorUtils.withOpacity(base, disabledOpacity);
    }
    return base;
  }

  /// entryLabelStyle.

  TextStyle entryLabelStyle(
    M3ETypeScale type,
    M3EColorScheme scheme, {
    required bool enabled,
    bool isDestructive = false,
    bool selected = false,
    M3EMenuColorStyle style = M3EMenuColorStyle.standard,
  }) => type.labelLarge.copyWith(
    color: entryForegroundColor(
      scheme,
      enabled: enabled,
      isDestructive: isDestructive,
      selected: selected,
      style: style,
    ),
  );

  /// supportingTextStyle.

  TextStyle supportingTextStyle(
    M3ETypeScale type,
    M3EColorScheme scheme, {
    required bool enabled,
    bool selected = false,
    M3EMenuColorStyle style = M3EMenuColorStyle.standard,
  }) {
    final palette = colors(scheme, style);
    final Color base = selected
        ? palette.selectedContent
        : palette.supportingContent;
    return type.bodySmall.copyWith(
      color: enabled ? base : M3EColorUtils.withOpacity(base, disabledOpacity),
    );
  }

  /// trailingTextStyle.

  TextStyle trailingTextStyle(
    M3ETypeScale type,
    M3EColorScheme scheme, {
    required bool enabled,
    bool selected = false,
    M3EMenuColorStyle style = M3EMenuColorStyle.standard,
  }) {
    final palette = colors(scheme, style);
    final Color base = selected
        ? palette.selectedContent
        : palette.supportingContent;
    return type.labelLarge.copyWith(
      color: enabled ? base : M3EColorUtils.withOpacity(base, disabledOpacity),
    );
  }

  /// groupLabelStyle.

  TextStyle groupLabelStyle(
    M3ETypeScale type,
    M3EColorScheme scheme, [
    M3EMenuColorStyle style = M3EMenuColorStyle.standard,
  ]) =>
      type.labelLarge.copyWith(color: colors(scheme, style).supportingContent);

  @override
  M3EMenuTheme copyWith({
    double? minWidth,
    double? maxWidth,
    double? maxHeight,
    double? verticalPadding,
    double? contentHorizontalPadding,
    double? anchorOffset,
    double? entryHeight,
    double? entryHorizontalPadding,
    double? entryVerticalPadding,
    double? iconSize,
    double? iconGap,
    double? groupSpacing,
    double? sectionGap,
    double? groupLabelHorizontalPadding,
    double? groupLabelVerticalPadding,
    double? groupLabelHeight,
    double? elevation,
    double? disabledOpacity,
    double? scrimAlpha,
    double? screenEdgePadding,
    double? containerRadius,
    double? containerBottomRadius,
    double? itemRadius,
    double? stateLayerInset,
    double? focusIndicatorWidth,
    double? focusIndicatorOffset,
    Color? focusIndicatorColor,
    Color? backgroundColor,
    M3ESpring? openMotion,
    M3ESpring? closeMotion,
    bool? openInstantly,
    double? itemGap,
    double? dividerThickness,
    double? dividerVerticalPadding,
  }) {
    return M3EMenuTheme(
      minWidth: minWidth ?? this.minWidth,
      maxWidth: maxWidth ?? this.maxWidth,
      maxHeight: maxHeight ?? this.maxHeight,
      verticalPadding: verticalPadding ?? this.verticalPadding,
      contentHorizontalPadding:
          contentHorizontalPadding ?? this.contentHorizontalPadding,
      anchorOffset: anchorOffset ?? this.anchorOffset,
      entryHeight: entryHeight ?? this.entryHeight,
      entryHorizontalPadding:
          entryHorizontalPadding ?? this.entryHorizontalPadding,
      entryVerticalPadding: entryVerticalPadding ?? this.entryVerticalPadding,
      iconSize: iconSize ?? this.iconSize,
      iconGap: iconGap ?? this.iconGap,
      groupSpacing: groupSpacing ?? this.groupSpacing,
      sectionGap: sectionGap ?? this.sectionGap,
      groupLabelHorizontalPadding:
          groupLabelHorizontalPadding ?? this.groupLabelHorizontalPadding,
      groupLabelVerticalPadding:
          groupLabelVerticalPadding ?? this.groupLabelVerticalPadding,
      groupLabelHeight: groupLabelHeight ?? this.groupLabelHeight,
      elevation: elevation ?? this.elevation,
      disabledOpacity: disabledOpacity ?? this.disabledOpacity,
      scrimAlpha: scrimAlpha ?? this.scrimAlpha,
      screenEdgePadding: screenEdgePadding ?? this.screenEdgePadding,
      containerRadius: containerRadius ?? this.containerRadius,
      containerBottomRadius:
          containerBottomRadius ?? this.containerBottomRadius,
      itemRadius: itemRadius ?? this.itemRadius,
      stateLayerInset: stateLayerInset ?? this.stateLayerInset,
      focusIndicatorWidth: focusIndicatorWidth ?? this.focusIndicatorWidth,
      focusIndicatorOffset: focusIndicatorOffset ?? this.focusIndicatorOffset,
      focusIndicatorColor: focusIndicatorColor ?? this.focusIndicatorColor,
      backgroundColor: backgroundColor ?? this.backgroundColor,
      openMotion: openMotion ?? this.openMotion,
      closeMotion: closeMotion ?? this.closeMotion,
      openInstantly: openInstantly ?? this.openInstantly,
      itemGap: itemGap ?? this.itemGap,
      dividerThickness: dividerThickness ?? this.dividerThickness,
      dividerVerticalPadding:
          dividerVerticalPadding ?? this.dividerVerticalPadding,
    );
  }

  @override
  M3EMenuTheme lerp(M3EMenuTheme? other, double t) {
    if (other is! M3EMenuTheme) {
      return this;
    }
    return _lerpMenuTheme(this, other, t);
  }
}
