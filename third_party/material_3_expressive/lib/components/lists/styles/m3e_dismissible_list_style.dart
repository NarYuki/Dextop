import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import 'm3e_list_theme.dart';

/// Immutable visual and interaction configuration for dismissible M3E lists.
class M3EDismissibleListStyle {
  /// Outer corner radius for first / last / single items.
  final double outerRadius;

  /// Border radius applied to the dragged card once it crosses the dismiss
  /// threshold. Defaults to [outerRadius].
  final double? selectedBorderRadius;

  /// Inner corner radius for middle items.
  final double innerRadius;

  /// Vertical gap between cards.
  final double gap;

  /// Horizontal gap between a swiped card and its revealed action.
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

  /// When true, full dismiss invokes the primary swipe action for that side.
  final bool autoExecutePrimaryOnFullSwipe;

  /// Card background colour.
  final Color? color;

  /// Inner padding of each card's content area.
  final EdgeInsetsGeometry? padding;

  /// Outer margin around each card.
  final EdgeInsetsGeometry? margin;

  /// Optional border drawn on every card.
  final BorderSide? border;

  /// Resting elevation.
  final double elevation;

  /// Background revealed when swiping start‑to‑end (left→right in LTR).
  final Widget? background;

  /// Background revealed when swiping end‑to‑start (right→left in LTR).
  final Widget? secondaryBackground;

  /// Background Border Radius
  final double backgroundBorderRadius;

  /// Secondary Background Border Radius
  final double? secondaryBackgroundBorderRadius;

  /// Collapse speed. Higher = faster.
  final double collapseSpeed;

  /// splashColor.

  final Color? splashColor;

  /// highlightColor.
  final Color? highlightColor;

  /// splashFactory.
  final InteractiveInkFeatureFactory? splashFactory;

  /// Whether detected gestures provide feedback.
  final bool enableFeedback;

  /// Haptic intensity on tap.
  final M3EHapticFeedback hapticOnTap;

  /// Fraction of card width the user must drag before a dismiss triggers.
  final double dismissThreshold;

  /// Haptic intensity when the drag crosses / re‑crosses.
  final M3EHapticFeedback hapticOnThreshold;

  /// Fire continuous light haptics during the drag.
  final bool dismissHapticStream;

  /// Maximum pixel offset applied to neighbouring cards.
  final double neighbourPull;

  /// How many cards are affected.
  final int neighbourReach;

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

  /// M3EDismissibleListStyle.

  const M3EDismissibleListStyle({
    this.outerRadius = M3EListDismissibleTheme.defaultOuterRadius,
    this.selectedBorderRadius,
    this.innerRadius = M3EListDismissibleTheme.defaultInnerRadius,
    this.gap = M3EListDismissibleTheme.defaultGap,
    this.actionGap = M3EListDismissibleTheme.defaultActionGap,
    this.actionSpacing = M3EListDismissibleTheme.defaultActionSpacing,
    this.actionEdgePadding = M3EListDismissibleTheme.defaultActionEdgePadding,
    this.actionPreviewThreshold =
        M3EListDismissibleTheme.defaultActionPreviewThreshold,
    this.actionOverdragExtent =
        M3EListDismissibleTheme.defaultActionOverdragExtent,
    this.actionVerticalInset =
        M3EListDismissibleTheme.defaultActionVerticalInset,
    this.actionMinHeight = M3EListDismissibleTheme.defaultActionMinHeight,
    this.actionMinWidth = M3EListDismissibleTheme.defaultActionMinWidth,
    this.autoExecutePrimaryOnFullSwipe = true,
    this.color,
    this.padding = M3EListDismissibleTheme.defaultItemPadding,
    this.margin,
    this.border,
    this.elevation = 0.0,
    this.background,
    this.secondaryBackground,
    this.backgroundBorderRadius =
        M3EListDismissibleTheme.defaultBackgroundBorderRadius,
    this.secondaryBackgroundBorderRadius =
        M3EListDismissibleTheme.defaultBackgroundBorderRadius,
    this.collapseSpeed = M3EListDismissibleTheme.defaultCollapseSpeed,
    this.splashColor,
    this.highlightColor,
    this.splashFactory,
    this.enableFeedback = true,
    this.hapticOnTap = M3EHapticFeedback.none,
    this.dismissThreshold = M3EListDismissibleTheme.defaultDismissThreshold,
    this.hapticOnThreshold = M3EHapticFeedback.none,
    this.dismissHapticStream = false,
    this.neighbourPull = M3EListDismissibleTheme.defaultNeighbourPull,
    this.neighbourReach = M3EListDismissibleTheme.defaultNeighbourReach,
    this.neighbourSpring = const M3ESpring(stiffness: 800, damping: 0.7),
    this.reEngageSpring = const M3ESpring(stiffness: 800, damping: 0.9),
    this.detachPushSpring = const M3ESpring(stiffness: 800, damping: 0.95),
    this.roundnessSnapSpring = const M3ESpring(stiffness: 1000, damping: 0.4),
    this.springBackSpring = const M3ESpring(stiffness: 380, damping: 0.6),
    this.flySpring = const M3ESpring(stiffness: 400, damping: 0.8),
    this.collapseDamping = 0.8,
  });

  /// Builds a style from [M3EListDismissibleTheme] token values.
  factory M3EDismissibleListStyle.fromTheme(M3EListDismissibleTheme theme) {
    return M3EDismissibleListStyle(
      outerRadius: theme.outerRadius,
      innerRadius: theme.innerRadius,
      gap: theme.gap,
      actionGap: theme.actionGap,
      actionSpacing: theme.actionSpacing,
      actionEdgePadding: theme.actionEdgePadding,
      actionPreviewThreshold: theme.actionPreviewThreshold,
      actionOverdragExtent: theme.actionOverdragExtent,
      actionVerticalInset: theme.actionVerticalInset,
      actionMinHeight: theme.actionMinHeight,
      actionMinWidth: theme.actionMinWidth,
      padding: theme.itemPadding,
      backgroundBorderRadius: theme.backgroundBorderRadius,
      secondaryBackgroundBorderRadius: theme.backgroundBorderRadius,
      collapseSpeed: theme.collapseSpeed,
      dismissThreshold: theme.dismissThreshold,
      neighbourPull: theme.neighbourPull,
      neighbourReach: theme.neighbourReach,
      neighbourSpring: theme.neighbourSpring,
      reEngageSpring: theme.reEngageSpring,
      detachPushSpring: theme.detachPushSpring,
      roundnessSnapSpring: theme.roundnessSnapSpring,
      springBackSpring: theme.springBackSpring,
      flySpring: theme.flySpring,
      collapseDamping: theme.collapseDamping,
    );
  }

  /// copyWith.
  M3EDismissibleListStyle copyWith({
    double? outerRadius,
    double? selectedBorderRadius,
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
    bool? autoExecutePrimaryOnFullSwipe,
    Color? color,
    EdgeInsetsGeometry? padding,
    EdgeInsetsGeometry? margin,
    BorderSide? border,
    double? elevation,
    Widget? background,
    Widget? secondaryBackground,
    double? backgroundBorderRadius,
    double? secondaryBackgroundBorderRadius,
    double? collapseSpeed,
    Color? splashColor,
    Color? highlightColor,
    InteractiveInkFeatureFactory? splashFactory,
    bool? enableFeedback,
    M3EHapticFeedback? hapticOnTap,
    double? dismissThreshold,
    M3EHapticFeedback? hapticOnThreshold,
    bool? dismissHapticStream,
    double? neighbourPull,
    int? neighbourReach,
    M3ESpring? neighbourSpring,
    M3ESpring? reEngageSpring,
    M3ESpring? detachPushSpring,
    M3ESpring? roundnessSnapSpring,
    M3ESpring? springBackSpring,
    M3ESpring? flySpring,
    double? collapseDamping,
  }) {
    return M3EDismissibleListStyle(
      outerRadius: outerRadius ?? this.outerRadius,
      selectedBorderRadius: selectedBorderRadius ?? this.selectedBorderRadius,
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
      autoExecutePrimaryOnFullSwipe:
          autoExecutePrimaryOnFullSwipe ?? this.autoExecutePrimaryOnFullSwipe,
      color: color ?? this.color,
      padding: padding ?? this.padding,
      margin: margin ?? this.margin,
      border: border ?? this.border,
      elevation: elevation ?? this.elevation,
      background: background ?? this.background,
      secondaryBackground: secondaryBackground ?? this.secondaryBackground,
      backgroundBorderRadius:
          backgroundBorderRadius ?? this.backgroundBorderRadius,
      secondaryBackgroundBorderRadius:
          secondaryBackgroundBorderRadius ??
          this.secondaryBackgroundBorderRadius,
      collapseSpeed: collapseSpeed ?? this.collapseSpeed,
      splashColor: splashColor ?? this.splashColor,
      highlightColor: highlightColor ?? this.highlightColor,
      splashFactory: splashFactory ?? this.splashFactory,
      enableFeedback: enableFeedback ?? this.enableFeedback,
      hapticOnTap: hapticOnTap ?? this.hapticOnTap,
      dismissThreshold: dismissThreshold ?? this.dismissThreshold,
      hapticOnThreshold: hapticOnThreshold ?? this.hapticOnThreshold,
      dismissHapticStream: dismissHapticStream ?? this.dismissHapticStream,
      neighbourPull: neighbourPull ?? this.neighbourPull,
      neighbourReach: neighbourReach ?? this.neighbourReach,
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
