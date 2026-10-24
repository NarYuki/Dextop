import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../icon_buttons/enums/m3e_icon_button_enums.dart';
import '../enums/m3e_toolbar_enums.dart';
import '../models/m3e_toolbar_item.dart';
import '../utils/m3e_toolbar_item_layout.dart';
import 'm3e_toolbar_icon_button.dart';
import 'm3e_toolbar_overflow_menu.dart';

/// Floating toolbar actions that expand left/right (or top/bottom) from a
/// filled [M3EToolbarAction.isExpandTrigger] item.
///
/// Container width/height is spring-driven with elastic overshoot. Side icons
/// fade/scale in after ~40% of the width transition (spatial springs spec).
class M3EToolbarExpandingActions extends StatelessWidget {
  /// M3EToolbarExpandingActions.
  const M3EToolbarExpandingActions({
    required this.actions,
    required this.maxInline,
    required this.overflowIcon,
    required this.iconButtonSize,
    required this.overflowTextStyle,
    required this.destructiveColor,
    required this.axis,
    required this.expandProgress,
    required this.onTriggerPressed,
    required this.availableExtent,
    this.leading,
    this.trailing,
    this.gap = 0,
    this.opticalInset = 0,
    this.pillActiveSpring = true,
    this.colorStyle = M3EToolbarColorStyle.standard,
    super.key,
  });

  /// actions.

  final List<M3EToolbarItem> actions;

  /// maxInline.
  final int maxInline;

  /// overflowIcon.
  final Widget overflowIcon;

  /// iconButtonSize.
  final M3EIconButtonSize iconButtonSize;

  /// overflowTextStyle.
  final TextStyle overflowTextStyle;

  /// destructiveColor.
  final Color destructiveColor;

  /// axis.
  final Axis axis;

  /// Spring progress: 0 = collapsed (trigger only), 1 = fully expanded.
  /// May overshoot past 1 for elastic edge give.
  final double expandProgress;

  /// onTriggerPressed.

  final VoidCallback onTriggerPressed;

  /// Remaining cross-axis size after bar content padding.
  final double availableExtent;

  /// leading.
  final Widget? leading;

  /// trailing.
  final Widget? trailing;

  /// gap.
  final double gap;

  /// Icon-button target overhang; applied to widget slots for optical parity.
  final double opticalInset;

  /// When false, the parent toolbar reserves a fixed labeled-selection width.
  final bool pillActiveSpring;

  /// colorStyle.
  final M3EToolbarColorStyle colorStyle;

  static const double _iconRevealStart = 0.4;

  @override
  Widget build(BuildContext context) {
    final int triggerIndex = actions.indexWhere(
      (M3EToolbarItem item) => item is M3EToolbarAction && item.isExpandTrigger,
    );

    // No trigger → always show full actions row (no expand morph).
    if (triggerIndex < 0) {
      return _staticRow(context, actions);
    }

    final partitioned = M3EToolbarItemLayout.partitionInline(
      items: actions,
      maxInline: maxInline,
      triggerIndex: triggerIndex,
    );
    final List<M3EToolbarItem> inline = partitioned.inline;
    final List<M3EToolbarAction> overflow = partitioned.overflow;

    final int inlineTriggerIndex = inline.indexWhere(
      (M3EToolbarItem item) => item is M3EToolbarAction && item.isExpandTrigger,
    );
    final List<M3EToolbarItem> before = inline.sublist(0, inlineTriggerIndex);
    final trigger = inline[inlineTriggerIndex] as M3EToolbarAction;
    final List<M3EToolbarItem> after = inline.sublist(inlineTriggerIndex + 1);

    final double widthFactor = expandProgress.clamp(0.0, 1.5);
    final double reveal = _iconReveal(expandProgress);
    final bool sidesVisible = widthFactor > 0.01;

    final Widget triggerButton = M3EToolbarIconButton(
      action: trigger,
      size: iconButtonSize,
      onPressed: onTriggerPressed,
      colorStyle: colorStyle,
      pillActiveSpring: pillActiveSpring,
    );

    final Widget? beforeSide = sidesVisible
        ? _sideGroup(
            items: before,
            leading: leading,
            trailing: null,
            overflow: null,
            reveal: reveal,
            widthFactor: widthFactor,
            alignStart: false,
          )
        : null;
    final Widget? afterSide = sidesVisible
        ? _sideGroup(
            items: after,
            leading: null,
            trailing: trailing,
            overflow: overflow.isEmpty ? null : overflow,
            reveal: reveal,
            widthFactor: widthFactor,
            alignStart: true,
          )
        : null;

    final children = <Widget>[?beforeSide, triggerButton, ?afterSide];

    if (axis == Axis.vertical) {
      return Column(mainAxisSize: MainAxisSize.min, children: children);
    }
    return Row(mainAxisSize: MainAxisSize.min, children: children);
  }

  Widget _staticRow(BuildContext context, List<M3EToolbarItem> all) {
    final partitioned = M3EToolbarItemLayout.partitionInline(
      items: all,
      maxInline: maxInline,
    );

    final slots = <Widget>[
      for (final M3EToolbarItem item in partitioned.inline)
        M3EToolbarItemLayout.buildItem(
          item: item,
          availableExtent: availableExtent,
          axis: axis,
          opticalInset: opticalInset,
          buildAction: (M3EToolbarAction action) => M3EToolbarIconButton(
            action: action,
            size: iconButtonSize,
            colorStyle: colorStyle,
            pillActiveSpring: pillActiveSpring,
          ),
        ),
      if (partitioned.overflow.isNotEmpty)
        M3EToolbarOverflowMenu(
          actions: partitioned.overflow,
          icon: overflowIcon,
          iconButtonSize: iconButtonSize,
          textStyle: overflowTextStyle,
          destructiveColor: destructiveColor,
        ),
    ];

    // Leading/trailing slots are not measured here — only pure action rows
    // get a fixed labeled-selection reservation.
    final double? reservedWidth = (leading == null && trailing == null)
        ? _reservedSelectionWidth(
            context: context,
            inline: partitioned.inline,
            hasOverflow: partitioned.overflow.isNotEmpty,
          )
        : null;
    final evenlySpace = reservedWidth != null;
    final slotChildren = evenlySpace
        ? slots
        : M3EToolbarItemLayout.withGaps(slots, gap: gap, axis: axis);
    final children = <Widget>[
      if (leading != null) ...<Widget>[leading!, _gapBox()],
      ...slotChildren,
      if (trailing != null) ...<Widget>[_gapBox(), trailing!],
    ];

    final MainAxisSize mainAxisSize = evenlySpace
        ? MainAxisSize.max
        : MainAxisSize.min;
    final MainAxisAlignment alignment = evenlySpace
        ? MainAxisAlignment.spaceBetween
        : MainAxisAlignment.start;

    final Widget content = axis == Axis.vertical
        ? Column(
            mainAxisSize: mainAxisSize,
            mainAxisAlignment: alignment,
            children: children,
          )
        : Row(
            mainAxisSize: mainAxisSize,
            mainAxisAlignment: alignment,
            children: children,
          );

    if (reservedWidth == null) {
      return content;
    }
    return SizedBox(width: reservedWidth, child: content);
  }

  double? _reservedSelectionWidth({
    required BuildContext context,
    required List<M3EToolbarItem> inline,
    required bool hasOverflow,
  }) {
    if (pillActiveSpring) {
      return null;
    }
    final theme = M3ETheme.of(context);
    final iconTheme = theme.iconButtonTheme;
    final Size visual = iconTheme.visual(
      iconButtonSize,
      M3EIconButtonWidth.defaultWidth,
    );
    final Size target = iconTheme.target(
      iconButtonSize,
      M3EIconButtonWidth.defaultWidth,
    );
    final double iconSlot = visual.width > target.width
        ? visual.width
        : target.width;
    return M3EToolbarItemLayout.reservedLabeledSelectionExtent(
      inline: inline,
      axis: axis,
      iconVisualExtent: visual.width,
      iconSlotExtent: iconSlot,
      labelStyle: theme.typeScale.labelLarge.copyWith(
        fontWeight: FontWeight.w600,
      ),
      textScaler: MediaQuery.textScalerOf(context),
      gap: gap,
      labelGap: M3EToolbarIconButton.labelGap,
      includeOverflowSlot: hasOverflow,
    );
  }

  /// Side cluster that grows away from the trigger.
  ///
  /// [alignStart] true → anchored at start (grows toward end / right / down).
  Widget? _sideGroup({
    required List<M3EToolbarItem> items,
    required Widget? leading,
    required Widget? trailing,
    required List<M3EToolbarAction>? overflow,
    required double reveal,
    required double widthFactor,
    required bool alignStart,
  }) {
    final bool hasItems =
        items.isNotEmpty ||
        (overflow != null && overflow.isNotEmpty) ||
        leading != null ||
        trailing != null;
    if (!hasItems) {
      return null;
    }

    final slots = <Widget>[
      for (final M3EToolbarItem item in items)
        M3EToolbarItemLayout.buildItem(
          item: item,
          availableExtent: availableExtent,
          axis: axis,
          opticalInset: opticalInset,
          buildAction: (M3EToolbarAction action) => M3EToolbarIconButton(
            action: action,
            size: iconButtonSize,
            colorStyle: colorStyle,
            pillActiveSpring: pillActiveSpring,
          ),
        ),
      if (overflow != null && overflow.isNotEmpty)
        M3EToolbarOverflowMenu(
          actions: overflow,
          icon: overflowIcon,
          iconButtonSize: iconButtonSize,
          textStyle: overflowTextStyle,
          destructiveColor: destructiveColor,
        ),
    ];

    final children = <Widget>[
      if (leading != null) ...<Widget>[leading, _gapBox()],
      ...M3EToolbarItemLayout.withGaps(slots, gap: gap, axis: axis),
      if (trailing != null) ...<Widget>[_gapBox(), trailing],
    ];

    final Widget group = axis == Axis.vertical
        ? Column(mainAxisSize: MainAxisSize.min, children: children)
        : Row(mainAxisSize: MainAxisSize.min, children: children);

    final AlignmentGeometry alignment = axis == Axis.horizontal
        ? (alignStart
              ? AlignmentDirectional.centerStart
              : AlignmentDirectional.centerEnd)
        : (alignStart ? Alignment.topCenter : Alignment.bottomCenter);

    return ClipRect(
      child: Align(
        alignment: alignment,
        widthFactor: axis == Axis.horizontal ? widthFactor : null,
        heightFactor: axis == Axis.vertical ? widthFactor : null,
        child: Opacity(
          opacity: reveal,
          child: Transform.scale(
            alignment: alignment,
            scale: 0.85 + (0.15 * reveal),
            child: group,
          ),
        ),
      ),
    );
  }

  Widget _gapBox() => SizedBox(
    width: axis == Axis.horizontal ? gap : 0,
    height: axis == Axis.vertical ? gap : 0,
  );

  /// Icons begin revealing ~40% into the width spring.
  double _iconReveal(double progress) {
    if (progress <= _iconRevealStart) {
      return 0;
    }
    return ((progress - _iconRevealStart) / (1.0 - _iconRevealStart)).clamp(
      0.0,
      1.0,
    );
  }
}
