import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../cards/enums/m3e_card_variant.dart';
import '../enums/m3e_list_enums.dart';

part 'm3e_list_item_theme_resolvers.dart';

/// Theme values for `M3EListItem`.
@immutable
class M3EListItemTheme extends _M3EListItemThemeFields
    with _M3EListItemThemeColors, _M3EListItemThemeTextStyles {
  /// M3EListItemTheme.
  const M3EListItemTheme({
    this.appearance = M3EListAppearance.expressive,
    this.style = M3EListStyle.segmented,
    this.horizontalPadding = 16,
    this.verticalPadding = 10,
    this.baselineVerticalPadding = 8,
    this.threeLineVerticalPadding = 12,
    this.minHeight = 56,
    this.twoLineHeight = 72,
    this.threeLineHeight = 88,
    this.iconSize = 20,
    this.baselineIconSize = 24,
    this.gap = 12,
    this.baselineGap = 16,
    this.avatarSize = 40,
    this.leadingImageSize = 56,
    this.leadingImageRadius = 8,
    this.leadingVideoWidth = 100,
    this.leadingVideoHeight = 56,
    this.smallLeadingVideoWidth = 100,
    this.smallLeadingVideoHeight = 56,
    this.largeLeadingVideoWidth = 114,
    this.largeLeadingVideoHeight = 64,
    this.focusIndicatorThickness = 3,
    this.focusIndicatorInset = 3,
    this.dividerThickness = 1,
    this.dividerLeadingInset = 16,
    this.dividerTrailingInset = 16,
    this.leadingIconTopPadding = 8,
    this.leadingIconTopPaddingTall = 12,
    this.minTarget = 48,
    this.selectedRadius = 16,
    this.hoverOpacity = M3EStateOpacity.hover,
    this.focusOpacity = M3EStateOpacity.focus,
    this.pressedOpacity = M3EStateOpacity.pressed,
    this.disabledStateOpacity = 0.1,
    this.draggedStateOpacity = M3EStateOpacity.dragged,
    this.disabledContentOpacity = M3EStateOpacity.disabledContent,
    this.draggedElevation = M3EElevation.level2,
    this.variant = M3ECardVariant.filled,
    this.border,
    this.containerColor,
    this.selectedContainerColor,
    this.labelColor,
    this.supportingColor,
    this.iconColorOverride,
    this.selectedContentColor,
    this.selectedStateIconColor,
    this.avatarColor,
    this.avatarLabelColor,
    this.dividerColor,
    this.focusIndicatorColor,
    this.stateLayerColor,
  });

  /// defaults.

  static const M3EListItemTheme defaults = M3EListItemTheme();

  /// Expressive or baseline token set.
  final M3EListAppearance appearance;

  /// Segmented resting container, or a standard list with no rest fill.
  final M3EListStyle style;

  /// Leading and trailing inset.
  final double horizontalPadding;

  /// Expressive top and bottom inset.
  final double verticalPadding;

  /// Baseline top and bottom inset for short items.
  final double baselineVerticalPadding;

  /// Baseline top and bottom inset for three-line or large-leading items.
  final double threeLineVerticalPadding;

  /// One-line container height.
  final double minHeight;

  /// Two-line container height.
  final double twoLineHeight;

  /// Three-line container height.
  final double threeLineHeight;

  /// Expressive leading and trailing icon size.
  final double iconSize;

  /// Baseline leading and trailing icon size.
  final double baselineIconSize;

  /// Expressive gap between slots.
  final double gap;

  /// Baseline gap between slots.
  final double baselineGap;

  /// Leading avatar diameter.
  final double avatarSize;

  /// Leading image width and height.
  final double leadingImageSize;

  /// Expressive leading image corner radius.
  final double leadingImageRadius;

  /// Generic leading video width.
  final double leadingVideoWidth;

  /// Generic leading video height.
  final double leadingVideoHeight;

  /// Small leading video width.
  final double smallLeadingVideoWidth;

  /// Small leading video height.
  final double smallLeadingVideoHeight;

  /// Large leading video width.
  final double largeLeadingVideoWidth;

  /// Large leading video height.
  final double largeLeadingVideoHeight;

  /// Keyboard focus ring thickness.
  final double focusIndicatorThickness;

  /// Keyboard focus ring inset (spec offset is negative).
  final double focusIndicatorInset;

  /// Divider thickness.
  final double dividerThickness;

  /// Divider leading inset.
  final double dividerLeadingInset;

  /// Divider trailing inset.
  final double dividerTrailingInset;

  /// Baseline leading-icon top inset.
  final double leadingIconTopPadding;

  /// Baseline leading-icon top inset when the item is 88 or taller.
  final double leadingIconTopPaddingTall;

  /// Minimum tap target.
  final double minTarget;

  /// Selected corner radius.
  final double selectedRadius;

  /// Hover state-layer opacity.
  @override
  final double hoverOpacity;

  /// Focus state-layer opacity.
  @override
  final double focusOpacity;

  /// Pressed state-layer opacity.
  @override
  final double pressedOpacity;

  /// Disabled state-layer opacity.
  @override
  final double disabledStateOpacity;

  /// Dragged state-layer opacity.
  @override
  final double draggedStateOpacity;

  /// Disabled content opacity.
  @override
  final double disabledContentOpacity;

  /// Dragged elevation.
  final double draggedElevation;

  /// Card variant for outlined list rows.
  final M3ECardVariant variant;

  /// Optional outline; null keeps the variant default.
  final BorderSide? border;

  /// Container override. Null uses [M3EColorScheme.surface].
  @override
  final Color? containerColor;

  /// Selected container override. Null uses secondary container.
  @override
  final Color? selectedContainerColor;

  /// Label override. Null uses on-surface.
  @override
  final Color? labelColor;

  /// Supporting, overline, and trailing text override.
  @override
  final Color? supportingColor;

  /// Unselected icon override. Null uses on-surface variant.
  @override
  final Color? iconColorOverride;

  /// Selected label and icon override. Null uses on-secondary container.
  @override
  final Color? selectedContentColor;

  /// Selected hover, focus, and press icon override. Null uses on-surface.
  @override
  final Color? selectedStateIconColor;

  /// Avatar container override. Null uses primary container.
  @override
  final Color? avatarColor;

  /// Avatar label override. Null uses on-primary container.
  @override
  final Color? avatarLabelColor;

  /// Divider override. Null uses outline variant.
  @override
  final Color? dividerColor;

  /// Focus ring override. Null uses secondary.
  @override
  final Color? focusIndicatorColor;

  /// State-layer override. Null uses on-surface.
  @override
  final Color? stateLayerColor;

  /// Whether this theme uses baseline tokens.
  bool get isBaseline => appearance == M3EListAppearance.baseline;

  /// Corner radius for [resting] after hover, focus, press, drag, and selection.
  ///
  /// Segmented rest keeps [resting] (4dp inner / 16dp outer). Active states
  /// and a selected row use [selectedRadius]. Standard rest is square.
  /// Baseline stays square until the row is selected.
  BorderRadius radiusFor({
    required BorderRadius resting,
    required bool selected,
    required bool hovered,
    required bool focused,
    required bool pressed,
    required bool dragged,
  }) {
    if (isBaseline) {
      if (selected) {
        return BorderRadius.circular(selectedRadius);
      }
      return BorderRadius.zero;
    }
    final bool active = selected || hovered || focused || pressed || dragged;
    if (active) {
      return BorderRadius.circular(selectedRadius);
    }
    if (style == M3EListStyle.standard) {
      return BorderRadius.zero;
    }
    return resting;
  }

  /// Whether a resting container is painted.
  bool restingContainer({
    required bool selected,
    required bool hovered,
    required bool focused,
    required bool pressed,
    required bool dragged,
  }) {
    if (selected || hovered || focused || pressed || dragged) {
      return true;
    }
    return isBaseline || style == M3EListStyle.segmented;
  }

  /// Top and bottom inset for the current appearance.
  double verticalPaddingFor({required bool tall, required bool largeLeading}) {
    if (isBaseline && (tall || largeLeading)) {
      return threeLineVerticalPadding;
    }
    if (isBaseline) {
      return baselineVerticalPadding;
    }
    return verticalPadding;
  }

  /// Icon size for the current appearance.
  double resolvedIconSize() => isBaseline ? baselineIconSize : iconSize;

  /// Slot gap for the current appearance.
  double resolvedGap() => isBaseline ? baselineGap : gap;

  /// Container height for [lines] of text (1, 2, or 3+).
  double heightForLines(int lines) {
    if (lines >= 3) {
      return threeLineHeight;
    }
    if (lines == 2) {
      return twoLineHeight;
    }
    return minHeight;
  }

  /// Baseline leading-icon top inset.
  double leadingIconTopFor({required bool tall}) =>
      tall ? leadingIconTopPaddingTall : leadingIconTopPadding;

  /// copyWith.
  M3EListItemTheme copyWith({
    M3EListAppearance? appearance,
    M3EListStyle? style,
    double? horizontalPadding,
    double? verticalPadding,
    double? baselineVerticalPadding,
    double? threeLineVerticalPadding,
    double? minHeight,
    double? twoLineHeight,
    double? threeLineHeight,
    double? iconSize,
    double? baselineIconSize,
    double? gap,
    double? baselineGap,
    double? avatarSize,
    double? leadingImageSize,
    double? leadingImageRadius,
    double? leadingVideoWidth,
    double? leadingVideoHeight,
    double? smallLeadingVideoWidth,
    double? smallLeadingVideoHeight,
    double? largeLeadingVideoWidth,
    double? largeLeadingVideoHeight,
    double? focusIndicatorThickness,
    double? focusIndicatorInset,
    double? dividerThickness,
    double? dividerLeadingInset,
    double? dividerTrailingInset,
    double? leadingIconTopPadding,
    double? leadingIconTopPaddingTall,
    double? minTarget,
    double? selectedRadius,
    double? hoverOpacity,
    double? focusOpacity,
    double? pressedOpacity,
    double? disabledStateOpacity,
    double? draggedStateOpacity,
    double? disabledContentOpacity,
    double? draggedElevation,
    M3ECardVariant? variant,
    BorderSide? border,
    Color? containerColor,
    Color? selectedContainerColor,
    Color? labelColor,
    Color? supportingColor,
    Color? iconColorOverride,
    Color? selectedContentColor,
    Color? selectedStateIconColor,
    Color? avatarColor,
    Color? avatarLabelColor,
    Color? dividerColor,
    Color? focusIndicatorColor,
    Color? stateLayerColor,
  }) {
    return M3EListItemTheme(
      appearance: appearance ?? this.appearance,
      style: style ?? this.style,
      horizontalPadding: horizontalPadding ?? this.horizontalPadding,
      verticalPadding: verticalPadding ?? this.verticalPadding,
      baselineVerticalPadding:
          baselineVerticalPadding ?? this.baselineVerticalPadding,
      threeLineVerticalPadding:
          threeLineVerticalPadding ?? this.threeLineVerticalPadding,
      minHeight: minHeight ?? this.minHeight,
      twoLineHeight: twoLineHeight ?? this.twoLineHeight,
      threeLineHeight: threeLineHeight ?? this.threeLineHeight,
      iconSize: iconSize ?? this.iconSize,
      baselineIconSize: baselineIconSize ?? this.baselineIconSize,
      gap: gap ?? this.gap,
      baselineGap: baselineGap ?? this.baselineGap,
      avatarSize: avatarSize ?? this.avatarSize,
      leadingImageSize: leadingImageSize ?? this.leadingImageSize,
      leadingImageRadius: leadingImageRadius ?? this.leadingImageRadius,
      leadingVideoWidth: leadingVideoWidth ?? this.leadingVideoWidth,
      leadingVideoHeight: leadingVideoHeight ?? this.leadingVideoHeight,
      smallLeadingVideoWidth:
          smallLeadingVideoWidth ?? this.smallLeadingVideoWidth,
      smallLeadingVideoHeight:
          smallLeadingVideoHeight ?? this.smallLeadingVideoHeight,
      largeLeadingVideoWidth:
          largeLeadingVideoWidth ?? this.largeLeadingVideoWidth,
      largeLeadingVideoHeight:
          largeLeadingVideoHeight ?? this.largeLeadingVideoHeight,
      focusIndicatorThickness:
          focusIndicatorThickness ?? this.focusIndicatorThickness,
      focusIndicatorInset: focusIndicatorInset ?? this.focusIndicatorInset,
      dividerThickness: dividerThickness ?? this.dividerThickness,
      dividerLeadingInset: dividerLeadingInset ?? this.dividerLeadingInset,
      dividerTrailingInset: dividerTrailingInset ?? this.dividerTrailingInset,
      leadingIconTopPadding:
          leadingIconTopPadding ?? this.leadingIconTopPadding,
      leadingIconTopPaddingTall:
          leadingIconTopPaddingTall ?? this.leadingIconTopPaddingTall,
      minTarget: minTarget ?? this.minTarget,
      selectedRadius: selectedRadius ?? this.selectedRadius,
      hoverOpacity: hoverOpacity ?? this.hoverOpacity,
      focusOpacity: focusOpacity ?? this.focusOpacity,
      pressedOpacity: pressedOpacity ?? this.pressedOpacity,
      disabledStateOpacity: disabledStateOpacity ?? this.disabledStateOpacity,
      draggedStateOpacity: draggedStateOpacity ?? this.draggedStateOpacity,
      disabledContentOpacity:
          disabledContentOpacity ?? this.disabledContentOpacity,
      draggedElevation: draggedElevation ?? this.draggedElevation,
      variant: variant ?? this.variant,
      border: border ?? this.border,
      containerColor: containerColor ?? this.containerColor,
      selectedContainerColor:
          selectedContainerColor ?? this.selectedContainerColor,
      labelColor: labelColor ?? this.labelColor,
      supportingColor: supportingColor ?? this.supportingColor,
      iconColorOverride: iconColorOverride ?? this.iconColorOverride,
      selectedContentColor: selectedContentColor ?? this.selectedContentColor,
      selectedStateIconColor:
          selectedStateIconColor ?? this.selectedStateIconColor,
      avatarColor: avatarColor ?? this.avatarColor,
      avatarLabelColor: avatarLabelColor ?? this.avatarLabelColor,
      dividerColor: dividerColor ?? this.dividerColor,
      focusIndicatorColor: focusIndicatorColor ?? this.focusIndicatorColor,
      stateLayerColor: stateLayerColor ?? this.stateLayerColor,
    );
  }
}
