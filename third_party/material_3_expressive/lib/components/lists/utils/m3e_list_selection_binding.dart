import 'package:flutter/widgets.dart';

import '../../selection/controllers/m3e_selection_controller.dart';
import '../enums/m3e_list_selection_enums.dart';
import '../styles/m3e_list_selection_state.dart';

/// Applies single/multiple selection semantics and notifies [onChanged].
void applyListSelectionToggle({
  required M3ESelectionController controller,
  required int index,
  required M3EListSelectionMode mode,
  ValueChanged<Set<int>>? onChanged,
}) {
  if (mode == M3EListSelectionMode.single) {
    final bool wasSelected = controller.isSelected(index);
    controller.clear();
    if (!wasSelected) {
      controller.select(index);
    }
  } else {
    controller.toggle(index);
  }
  onChanged?.call(controller.selectedIndices);
}

/// Effective selection state (widget override over theme).
M3EListSelectionState mergeListSelectionState({
  required M3EListSelectionState theme,
  M3EListSelectionState? override,
}) => override ?? theme;
