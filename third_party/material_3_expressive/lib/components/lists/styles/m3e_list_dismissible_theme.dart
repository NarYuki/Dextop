import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import 'm3e_list_card_list_theme.dart';

/// Theme values for dismissible list widgets.
///
/// Stack geometry ([defaultOuterRadius], [defaultInnerRadius], [defaultGap],
/// [defaultItemPadding]) matches [M3EListCardListTheme] so card and dismissible
/// lists share the same item layout defaults.
@immutable
class M3EListDismissibleTheme {
  /// defaultOuterRadius.
  static const double defaultOuterRadius =
      M3EListCardListTheme.defaultOuterRadius;

  /// defaultInnerRadius.
  static const double defaultInnerRadius =
      M3EListCardListTheme.defaultInnerRadius;

  /// defaultGap.
  static const double defaultGap = M3EListCardListTheme.defaultGap;

  /// defaultActionGap.
  ///
  /// Matches [defaultActionSpacing] / [defaultActionEdgePadding] so the gap
  /// between actions equals the gap between actions and the list item.
  static const double defaultActionGap = 2;

  /// defaultActionSpacing.
  static const double defaultActionSpacing = 2;

  /// defaultActionEdgePadding.
  static const double defaultActionEdgePadding = 2;

  /// Fraction of actions width at which the preview snaps open on release.
  static const double defaultActionPreviewThreshold = 0.35;

  /// Extra drag past the actions width before rubber-band overdrag.
  static const double defaultActionOverdragExtent = 24;

  /// Vertical inset subtracted from the row height for action pills.
  static const double defaultActionVerticalInset = 8;

  /// Minimum action / dismiss pill height.
  static const double defaultActionMinHeight = 28;

  /// Minimum visual width for action pills (keeps icons inside while revealing).
  static const double defaultActionMinWidth = 40;

  /// defaultDismissThreshold.
  static const double defaultDismissThreshold = 0.2;

  /// defaultNeighbourPull.
  static const double defaultNeighbourPull = 8;

  /// defaultNeighbourReach.
  static const int defaultNeighbourReach = 3;

  /// defaultBackgroundBorderRadius.
  static const double defaultBackgroundBorderRadius = 100;

  /// defaultCollapseSpeed.
  static const double defaultCollapseSpeed = 50;

  /// defaultItemPadding.
  static const EdgeInsets defaultItemPadding =
      M3EListCardListTheme.defaultItemPadding;

  /// M3EListDismissibleTheme.

  const M3EListDismissibleTheme({
    this.outerRadius = defaultOuterRadius,
    this.innerRadius = defaultInnerRadius,
    this.gap = defaultGap,
    this.actionGap = defaultActionGap,
    this.actionSpacing = defaultActionSpacing,
    this.actionEdgePadding = defaultActionEdgePadding,
    this.actionPreviewThreshold = defaultActionPreviewThreshold,
    this.actionOverdragExtent = defaultActionOverdragExtent,
    this.actionVerticalInset = defaultActionVerticalInset,
    this.actionMinHeight = defaultActionMinHeight,
    this.actionMinWidth = defaultActionMinWidth,
    this.dismissThreshold = defaultDismissThreshold,
    this.neighbourPull = defaultNeighbourPull,
    this.neighbourReach = defaultNeighbourReach,
    this.backgroundBorderRadius = defaultBackgroundBorderRadius,
    this.collapseSpeed = defaultCollapseSpeed,
    this.itemPadding = defaultItemPadding,
    this.neighbourSpring = const M3ESpring(stiffness: 800, damping: 0.7),
    this.reEngageSpring = const M3ESpring(stiffness: 800, damping: 0.9),
    this.detachPushSpring = const M3ESpring(stiffness: 800, damping: 0.95),
    this.roundnessSnapSpring = const M3ESpring(stiffness: 1000, damping: 0.4),
    this.springBackSpring = const M3ESpring(stiffness: 380, damping: 0.6),
    this.flySpring = const M3ESpring(stiffness: 400, damping: 0.8),
    this.collapseDamping = 0.8,
  });

  /// defaults.

  static const M3EListDismissibleTheme defaults = M3EListDismissibleTheme();

  /// outerRadius.

  final double outerRadius;

  /// innerRadius.
  final double innerRadius;

  /// gap.
  final double gap;

  /// Horizontal gap between a swiped card and its revealed action background.
  final double actionGap;

  /// Spacing between revealed swipe action buttons.
  final double actionSpacing;

  /// Horizontal padding at the leading / trailing edges of the action row.
  final double actionEdgePadding;

  /// Fraction of actions width required to snap the preview open on release.
  final double actionPreviewThreshold;

  /// Extra pixels past the actions width before rubber-band overdrag.
  final double actionOverdragExtent;

  /// Vertical inset subtracted from the list row height for action pills.
  final double actionVerticalInset;

  /// Minimum height for action / dismiss pills.
  final double actionMinHeight;

  /// Minimum visual width for action pills while revealing / hiding.
  final double actionMinWidth;

  /// dismissThreshold.
  final double dismissThreshold;

  /// neighbourPull.
  final double neighbourPull;

  /// neighbourReach.
  final int neighbourReach;

  /// backgroundBorderRadius.
  final double backgroundBorderRadius;

  /// collapseSpeed.
  final double collapseSpeed;

  /// itemPadding.
  final EdgeInsetsGeometry itemPadding;

  /// Neighbour fraction spring (base; stiffness scaled by multiplier).
  final M3ESpring neighbourSpring;

  /// Roundness re-engage spring (base; stiffness scaled by multiplier).
  final M3ESpring reEngageSpring;

  /// Detach push spring (base; stiffness scaled by multiplier).
  final M3ESpring detachPushSpring;

  /// Roundness snap spring (base; stiffness scaled by multiplier).
  final M3ESpring roundnessSnapSpring;

  /// Drag spring-back spring (base; stiffness scaled by speedMul).
  final M3ESpring springBackSpring;

  /// Fly-away spring (base; stiffness scaled by speedMul).
  final M3ESpring flySpring;

  /// Damping for collapse; stiffness still uses [collapseSpeed] × speedMul.
  final double collapseDamping;

  /// backgroundColor.

  Color backgroundColor(M3EColorScheme scheme) => scheme.surface;

  /// copyWith.

  M3EListDismissibleTheme copyWith({
    double? outerRadius,
    double? innerRadius,
    double? gap,
    double? actionGap,
    double? actionSpacing,
    double? actionEdgePadding,
    double? actionPreviewThreshold,
    double? actionOverdragExtent,
    double? actionVerticalInset,
    double? actionMinHeight,
    double? actionMinWidth,
    double? dismissThreshold,
    double? neighbourPull,
    int? neighbourReach,
    double? backgroundBorderRadius,
    double? collapseSpeed,
    EdgeInsetsGeometry? itemPadding,
    M3ESpring? neighbourSpring,
    M3ESpring? reEngageSpring,
    M3ESpring? detachPushSpring,
    M3ESpring? roundnessSnapSpring,
    M3ESpring? springBackSpring,
    M3ESpring? flySpring,
    double? collapseDamping,
  }) {
    return M3EListDismissibleTheme(
      outerRadius: outerRadius ?? this.outerRadius,
      innerRadius: innerRadius ?? this.innerRadius,
      gap: gap ?? this.gap,
      actionGap: actionGap ?? this.actionGap,
      actionSpacing: actionSpacing ?? this.actionSpacing,
      actionEdgePadding: actionEdgePadding ?? this.actionEdgePadding,
      actionPreviewThreshold:
          actionPreviewThreshold ?? this.actionPreviewThreshold,
      actionOverdragExtent: actionOverdragExtent ?? this.actionOverdragExtent,
      actionVerticalInset: actionVerticalInset ?? this.actionVerticalInset,
      actionMinHeight: actionMinHeight ?? this.actionMinHeight,
      actionMinWidth: actionMinWidth ?? this.actionMinWidth,
      dismissThreshold: dismissThreshold ?? this.dismissThreshold,
      neighbourPull: neighbourPull ?? this.neighbourPull,
      neighbourReach: neighbourReach ?? this.neighbourReach,
      backgroundBorderRadius:
          backgroundBorderRadius ?? this.backgroundBorderRadius,
      collapseSpeed: collapseSpeed ?? this.collapseSpeed,
      itemPadding: itemPadding ?? this.itemPadding,
      neighbourSpring: neighbourSpring ?? this.neighbourSpring,
      reEngageSpring: reEngageSpring ?? this.reEngageSpring,
      detachPushSpring: detachPushSpring ?? this.detachPushSpring,
      roundnessSnapSpring: roundnessSnapSpring ?? this.roundnessSnapSpring,
      springBackSpring: springBackSpring ?? this.springBackSpring,
      flySpring: flySpring ?? this.flySpring,
      collapseDamping: collapseDamping ?? this.collapseDamping,
    );
  }
}
