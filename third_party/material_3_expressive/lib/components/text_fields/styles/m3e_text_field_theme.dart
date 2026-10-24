import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_text_field_slot_alignment.dart';
import '../enums/m3e_text_field_variant.dart';
import '../models/m3e_text_field_colors.dart';
import '../models/m3e_text_field_states.dart';
import 'm3e_text_field_color_theme.dart';

/// Theme values for `M3ETextField`.
///
/// Defaults match the Material 3 text field spec for both variants.
@immutable
class M3ETextFieldTheme extends M3EThemeExtension<M3ETextFieldTheme> {
  /// M3ETextFieldTheme.
  const M3ETextFieldTheme({
    this.containerHeight = 56,
    this.verticalPadding = 8,
    this.horizontalPadding = 16,
    this.iconEdgePadding = 12,
    this.iconSize = 24,
    this.iconTextGap = 16,
    this.notchPadding = 4,
    this.supportingTopPadding = 4,
    this.supportingHorizontalPadding = 16,
    this.supportingCounterGap = 16,
    this.density = 0,
    this.densityStep = 4,
    this.constraints,
    this.filledShape = const BorderRadius.vertical(
      top: Radius.circular(M3EShapes.extraSmall),
    ),
    this.outlinedShape = M3EShapes.radiusExtraSmall,
    this.activeIndicatorWidth = 1,
    this.focusActiveIndicatorWidth = 2,
    this.outlineWidth = 1,
    this.focusOutlineWidth = 3,
    this.focusRingWidth = 3,
    this.focusRingGap = 2,
    this.selectionOpacity = 0.4,
    this.labelSpring = M3EMotion.spatialFast,
    this.effectsSpring = M3EMotion.effectsFast,
    this.inputTextStyle,
    this.labelTextStyle,
    this.floatingLabelTextStyle,
    this.supportingTextStyle,
    this.colors = const M3ETextFieldColorTheme(),
    this.iconAlignment = M3ETextFieldSlotAlignment.center,
    this.affixAlignment = M3ETextFieldSlotAlignment.firstLine,
  });

  /// defaults.
  static const M3ETextFieldTheme defaults = M3ETextFieldTheme();

  /// Container height (and target size) at density 0.
  final double containerHeight;

  /// Filled top/bottom padding around the floating label and input.
  final double verticalPadding;

  /// Start/end padding on a side without an icon.
  final double horizontalPadding;

  /// Edge to leading/trailing icon padding.
  final double iconEdgePadding;

  /// Leading/trailing icon (and image) size.
  final double iconSize;

  /// Icon to label/input padding.
  final double iconTextGap;

  /// Outlined notch padding on each side of the floating label.
  final double notchPadding;

  /// Container to supporting text / counter padding.
  final double supportingTopPadding;

  /// Supporting text / counter start and end inset.
  final double supportingHorizontalPadding;

  /// Supporting text to counter gap.
  final double supportingCounterGap;

  /// Default density, from 0 (spec) down to -3. Not dense by default.
  final int density;

  /// Height removed per density step.
  final double densityStep;

  /// Optional width bounds for medium and expanded layouts.
  final BoxConstraints? constraints;

  /// Filled container shape: rounded top, square bottom.
  final BorderRadius filledShape;

  /// Outlined container shape.
  final BorderRadius outlinedShape;

  /// Filled active indicator height.
  final double activeIndicatorWidth;

  /// Filled focused active indicator height.
  final double focusActiveIndicatorWidth;

  /// Outlined outline width.
  final double outlineWidth;

  /// Outlined focused outline width.
  final double focusOutlineWidth;

  /// Keyboard focus ring thickness.
  final double focusRingWidth;

  /// Gap between the container and the keyboard focus ring.
  final double focusRingGap;

  /// Text selection highlight opacity (primary).
  final double selectionOpacity;

  /// Spatial spring for the label moving between rest and float.
  final M3ESpring labelSpring;

  /// Effects spring for colors, stroke width and the outline notch.
  final M3ESpring effectsSpring;

  /// Input text style. Default: body large.
  final TextStyle? inputTextStyle;

  /// Label style in an empty field. Default: body large.
  final TextStyle? labelTextStyle;

  /// Label style in a populated or focused field. Default: body small.
  final TextStyle? floatingLabelTextStyle;

  /// Supporting text and counter style. Default: body small.
  final TextStyle? supportingTextStyle;

  /// Color tokens.
  final M3ETextFieldColorTheme colors;

  /// Leading/trailing icon position in multi-line fields. Default: centered,
  /// per the spec's "icon alignment: vertically centered".
  final M3ETextFieldSlotAlignment iconAlignment;

  /// Prefix/suffix text position in multi-line fields. Default: first line.
  final M3ETextFieldSlotAlignment affixAlignment;

  /// [density] clamped to the supported range.
  static int clampDensity(int density) => density.clamp(-3, 0);

  /// Height change per side for [density] (zero or negative).
  double densityOffset(int density) => clampDensity(density) * densityStep / 2;

  /// Container height for [density].
  double heightFor(int density) => containerHeight + densityOffset(density) * 2;

  /// Filled top/bottom padding for [density].
  double verticalPaddingFor(int density) =>
      verticalPadding + densityOffset(density);

  /// Width of an icon slot (edge padding on both sides of the icon).
  double get iconSlotWidth => iconSize + iconEdgePadding * 2;

  /// Text side padding next to an icon slot.
  double get iconSlotTextPadding => iconTextGap - iconEdgePadding;

  /// Container shape for [variant].
  BorderRadius shapeFor(M3ETextFieldVariant variant) =>
      variant == M3ETextFieldVariant.outlined ? outlinedShape : filledShape;

  /// Resolved input style.
  TextStyle resolveInputStyle(M3ETypeScale type) =>
      inputTextStyle ?? type.bodyLarge;

  /// Resolved resting label style.
  TextStyle resolveLabelStyle(M3ETypeScale type) =>
      labelTextStyle ?? type.bodyLarge;

  /// Resolved floating label style.
  TextStyle resolveFloatingLabelStyle(M3ETypeScale type) =>
      floatingLabelTextStyle ?? type.bodySmall;

  /// Resolved supporting text style.
  TextStyle resolveSupportingStyle(M3ETypeScale type) =>
      supportingTextStyle ?? type.bodySmall;

  /// Line height of [style] in logical pixels.
  static double lineHeightOf(TextStyle style) =>
      (style.fontSize ?? 14) * (style.height ?? 1);

  /// Stroke width for [variant] in [states].
  double strokeWidthFor(
    M3ETextFieldVariant variant,
    M3ETextFieldStates states,
  ) {
    final bool focused = states.isFocused;
    if (variant == M3ETextFieldVariant.outlined) {
      return focused ? focusOutlineWidth : outlineWidth;
    }
    return focused ? focusActiveIndicatorWidth : activeIndicatorWidth;
  }

  /// Resolves the palette for [variant] in [states].
  M3ETextFieldColors resolveColors(
    M3EColorScheme scheme, {
    required M3ETextFieldVariant variant,
    required M3ETextFieldStates states,
  }) {
    return colors.resolve(
      scheme,
      outlined: variant == M3ETextFieldVariant.outlined,
      states: states,
      strokeWidth: strokeWidthFor(variant, states),
    );
  }

  @override
  M3ETextFieldTheme copyWith({
    double? containerHeight,
    double? verticalPadding,
    double? horizontalPadding,
    double? iconEdgePadding,
    double? iconSize,
    double? iconTextGap,
    double? notchPadding,
    double? supportingTopPadding,
    double? supportingHorizontalPadding,
    double? supportingCounterGap,
    int? density,
    double? densityStep,
    BoxConstraints? constraints,
    BorderRadius? filledShape,
    BorderRadius? outlinedShape,
    double? activeIndicatorWidth,
    double? focusActiveIndicatorWidth,
    double? outlineWidth,
    double? focusOutlineWidth,
    double? focusRingWidth,
    double? focusRingGap,
    double? selectionOpacity,
    M3ESpring? labelSpring,
    M3ESpring? effectsSpring,
    TextStyle? inputTextStyle,
    TextStyle? labelTextStyle,
    TextStyle? floatingLabelTextStyle,
    TextStyle? supportingTextStyle,
    M3ETextFieldColorTheme? colors,
    M3ETextFieldSlotAlignment? iconAlignment,
    M3ETextFieldSlotAlignment? affixAlignment,
  }) {
    return M3ETextFieldTheme(
      containerHeight: containerHeight ?? this.containerHeight,
      verticalPadding: verticalPadding ?? this.verticalPadding,
      horizontalPadding: horizontalPadding ?? this.horizontalPadding,
      iconEdgePadding: iconEdgePadding ?? this.iconEdgePadding,
      iconSize: iconSize ?? this.iconSize,
      iconTextGap: iconTextGap ?? this.iconTextGap,
      notchPadding: notchPadding ?? this.notchPadding,
      supportingTopPadding: supportingTopPadding ?? this.supportingTopPadding,
      supportingHorizontalPadding:
          supportingHorizontalPadding ?? this.supportingHorizontalPadding,
      supportingCounterGap: supportingCounterGap ?? this.supportingCounterGap,
      density: density ?? this.density,
      densityStep: densityStep ?? this.densityStep,
      constraints: constraints ?? this.constraints,
      filledShape: filledShape ?? this.filledShape,
      outlinedShape: outlinedShape ?? this.outlinedShape,
      activeIndicatorWidth: activeIndicatorWidth ?? this.activeIndicatorWidth,
      focusActiveIndicatorWidth:
          focusActiveIndicatorWidth ?? this.focusActiveIndicatorWidth,
      outlineWidth: outlineWidth ?? this.outlineWidth,
      focusOutlineWidth: focusOutlineWidth ?? this.focusOutlineWidth,
      focusRingWidth: focusRingWidth ?? this.focusRingWidth,
      focusRingGap: focusRingGap ?? this.focusRingGap,
      selectionOpacity: selectionOpacity ?? this.selectionOpacity,
      labelSpring: labelSpring ?? this.labelSpring,
      effectsSpring: effectsSpring ?? this.effectsSpring,
      inputTextStyle: inputTextStyle ?? this.inputTextStyle,
      labelTextStyle: labelTextStyle ?? this.labelTextStyle,
      floatingLabelTextStyle:
          floatingLabelTextStyle ?? this.floatingLabelTextStyle,
      supportingTextStyle: supportingTextStyle ?? this.supportingTextStyle,
      colors: colors ?? this.colors,
      iconAlignment: iconAlignment ?? this.iconAlignment,
      affixAlignment: affixAlignment ?? this.affixAlignment,
    );
  }

  @override
  M3ETextFieldTheme lerp(M3ETextFieldTheme? other, double t) {
    if (other is! M3ETextFieldTheme) {
      return this;
    }
    final M3ETextFieldTheme b = t < 0.5 ? this : other;
    double l(double Function(M3ETextFieldTheme x) f) =>
        f(this) + (f(other) - f(this)) * t;
    return b.copyWith(
      containerHeight: l((M3ETextFieldTheme x) => x.containerHeight),
      verticalPadding: l((M3ETextFieldTheme x) => x.verticalPadding),
      horizontalPadding: l((M3ETextFieldTheme x) => x.horizontalPadding),
      iconEdgePadding: l((M3ETextFieldTheme x) => x.iconEdgePadding),
      iconSize: l((M3ETextFieldTheme x) => x.iconSize),
      iconTextGap: l((M3ETextFieldTheme x) => x.iconTextGap),
      notchPadding: l((M3ETextFieldTheme x) => x.notchPadding),
      supportingTopPadding: l((M3ETextFieldTheme x) => x.supportingTopPadding),
      supportingHorizontalPadding: l(
        (M3ETextFieldTheme x) => x.supportingHorizontalPadding,
      ),
      supportingCounterGap: l((M3ETextFieldTheme x) => x.supportingCounterGap),
      densityStep: l((M3ETextFieldTheme x) => x.densityStep),
      filledShape: BorderRadius.lerp(filledShape, other.filledShape, t),
      outlinedShape: BorderRadius.lerp(outlinedShape, other.outlinedShape, t),
      activeIndicatorWidth: l((M3ETextFieldTheme x) => x.activeIndicatorWidth),
      focusActiveIndicatorWidth: l(
        (M3ETextFieldTheme x) => x.focusActiveIndicatorWidth,
      ),
      outlineWidth: l((M3ETextFieldTheme x) => x.outlineWidth),
      focusOutlineWidth: l((M3ETextFieldTheme x) => x.focusOutlineWidth),
      focusRingWidth: l((M3ETextFieldTheme x) => x.focusRingWidth),
      focusRingGap: l((M3ETextFieldTheme x) => x.focusRingGap),
      selectionOpacity: l((M3ETextFieldTheme x) => x.selectionOpacity),
      colors: colors.lerp(other.colors, t),
    );
  }
}
