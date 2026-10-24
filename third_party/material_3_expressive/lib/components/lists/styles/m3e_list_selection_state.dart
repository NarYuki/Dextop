import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_list_selection_enums.dart';
import 'm3e_list_theme.dart';

/// Visual and interaction defaults for list selection.
@immutable
class M3EListSelectionState {
  /// Creates list selection state.
  const M3EListSelectionState({
    this.mode = M3EListSelectionMode.multiple,
    this.selectedIcon,
    this.trigger = M3EListSelectionTrigger.icon,
    this.highlightColor,
    this.iconFlipDuration = const Duration(milliseconds: 220),
  });

  /// Theme defaults (no selected icon → no leading flip).
  static const M3EListSelectionState defaults = M3EListSelectionState();

  /// Single or multiple selection.
  final M3EListSelectionMode mode;

  /// Optional icon shown when selected; flips with the row's leading widget.
  ///
  /// When null, the leading widget is left unchanged (no flip).
  final Widget? selectedIcon;

  /// How selection is toggled.
  final M3EListSelectionTrigger trigger;

  /// Selected fill override; defaults to secondary container.
  final Color? highlightColor;

  /// Duration of the selection icon flip.
  final Duration iconFlipDuration;

  /// Whether a leading flip can be shown.
  bool get hasSelectedIcon => selectedIcon != null;

  /// Selected card fill.
  Color selectedColor(M3EColorScheme scheme) =>
      highlightColor ?? scheme.secondaryContainer;

  /// Selected equal-corner radius (matches card-list outer radius).
  double selectedOuterRadius(M3EListTheme listTheme) =>
      listTheme.cardList.outerRadius;

  /// copyWith.
  M3EListSelectionState copyWith({
    M3EListSelectionMode? mode,
    Widget? selectedIcon,
    bool clearSelectedIcon = false,
    M3EListSelectionTrigger? trigger,
    Color? highlightColor,
    Duration? iconFlipDuration,
  }) {
    return M3EListSelectionState(
      mode: mode ?? this.mode,
      selectedIcon: clearSelectedIcon
          ? null
          : (selectedIcon ?? this.selectedIcon),
      trigger: trigger ?? this.trigger,
      highlightColor: highlightColor ?? this.highlightColor,
      iconFlipDuration: iconFlipDuration ?? this.iconFlipDuration,
    );
  }
}
