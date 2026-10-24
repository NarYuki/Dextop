import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../cards/enums/m3e_card_variant.dart';

/// Theme values for `M3ECardList`.
@immutable
class M3EListCardListTheme {
  /// defaultOuterRadius.
  static const double defaultOuterRadius = 16;

  /// defaultInnerRadius.
  static const double defaultInnerRadius = 4;

  /// defaultGap.
  static const double defaultGap = 2;

  /// defaultItemPadding.
  ///
  /// Zero so list items own the spec padding and rows are not padded twice.
  static const EdgeInsets defaultItemPadding = EdgeInsets.zero;

  /// M3EListCardListTheme.

  const M3EListCardListTheme({
    this.outerRadius = defaultOuterRadius,
    this.innerRadius = defaultInnerRadius,
    this.gap = defaultGap,
    this.itemPadding = defaultItemPadding,
    this.variant = M3ECardVariant.filled,
    this.border,
    this.radiusSpring = M3EMotion.expressiveSpatialDefault,
  });

  /// defaults.

  static const M3EListCardListTheme defaults = M3EListCardListTheme();

  /// outerRadius.

  final double outerRadius;

  /// innerRadius.
  final double innerRadius;

  /// gap.
  final double gap;

  /// itemPadding.
  final EdgeInsetsGeometry itemPadding;

  /// Card variant for each card-list item.
  final M3ECardVariant variant;

  /// Optional card outline; null keeps the variant default.
  final BorderSide? border;

  /// Corner-radius morph spring for card list items.
  final M3ESpring radiusSpring;

  /// Resting fill for [variant]. Outlined stays on surface.
  Color backgroundColor(M3EColorScheme scheme, {M3ECardVariant? variant}) {
    return switch (variant ?? this.variant) {
      M3ECardVariant.filled => scheme.surfaceContainerHighest,
      M3ECardVariant.elevated => scheme.surfaceContainerLow,
      M3ECardVariant.outlined => scheme.surface,
    };
  }

  /// copyWith.

  M3EListCardListTheme copyWith({
    double? outerRadius,
    double? innerRadius,
    double? gap,
    EdgeInsetsGeometry? itemPadding,
    M3ECardVariant? variant,
    BorderSide? border,
    M3ESpring? radiusSpring,
  }) {
    return M3EListCardListTheme(
      outerRadius: outerRadius ?? this.outerRadius,
      innerRadius: innerRadius ?? this.innerRadius,
      gap: gap ?? this.gap,
      itemPadding: itemPadding ?? this.itemPadding,
      variant: variant ?? this.variant,
      border: border ?? this.border,
      radiusSpring: radiusSpring ?? this.radiusSpring,
    );
  }
}
