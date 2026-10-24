import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../selection/components/m3e_selection_flip.dart';
import '../components/m3e_list_feature_scope.dart';
import '../components/m3e_list_key_target.dart';
import '../components/m3e_list_keyboard.dart';
import '../components/m3e_list_trailing_override.dart';
import '../enums/m3e_list_selection_enums.dart';

/// Resolves leading widget for list selection flip (leading only).
///
/// Flips between [leading] and the selection icon when selection is enabled.
/// Otherwise returns [leading] unchanged.
Widget? m3eResolveListLeading({
  required BuildContext context,
  required int? index,
  required Widget? leading,
}) {
  if (index == null) {
    return leading;
  }
  final M3EListFeatureScope? scope = M3EListFeatureScope.maybeOf(context);
  if (scope == null || !scope.selectionEnabled) {
    return leading;
  }
  final iconTrigger =
      scope.selectionState.trigger == M3EListSelectionTrigger.icon;
  if (!scope.selectionState.hasSelectedIcon && !iconTrigger) {
    return leading;
  }

  final single = scope.selectionState.mode == M3EListSelectionMode.single;
  final Widget selectedIcon =
      scope.selectionState.selectedIcon ??
      Icon(single ? M3EIcons.radio_button_checked : M3EIcons.check_box);
  final Widget child =
      leading ??
      Icon(
        single
            ? M3EIcons.radio_button_unchecked
            : M3EIcons.check_box_outline_blank,
      );
  final bool selected = scope.isSelected(index);
  final VoidCallback? onIconTap = iconTrigger
      ? () => scope.onToggleSelection(index)
      : null;

  final Widget flip = M3ESelectionFlip(
    selected: selected,
    selectedChild: selectedIcon,
    duration: scope.selectionState.iconFlipDuration,
    onTap: onIconTap,
    child: child,
  );
  if (onIconTap == null || M3EListKeyboardGroup.maybeOf(context) == null) {
    return flip;
  }
  // Arrow-reachable row action instead of its own Tab stop.
  return M3EListKeyTarget(
    index: index,
    onActivate: onIconTap,
    child: ExcludeFocus(child: flip),
  );
}

/// Resolves trailing widget; reorder drag handle replaces any trailing when on.
///
/// The handle stays in this slot at rest and while the row is dragged.
Widget? m3eResolveListTrailing({
  required BuildContext context,
  required Widget? trailing,
}) {
  final M3EListTrailingOverride? override = M3EListTrailingOverride.maybeOf(
    context,
  );
  if (override != null) {
    return override.trailing;
  }
  final M3EListFeatureScope? scope = M3EListFeatureScope.maybeOf(context);
  if (scope == null ||
      !scope.reorderEnabled ||
      !scope.reorderState.showDragHandle) {
    return trailing;
  }

  return const Icon(M3EIcons.drag_handle);
}
