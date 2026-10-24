import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../selection/m3e_selection.dart';
import '../components/m3e_list_feature_scope.dart';

/// Highlight fill for [index] when an enclosing scope has it selected.
///
/// Checks [M3EListFeatureScope] first, then [M3ESelectionScope].
Color? m3eSelectionFill(BuildContext context, int index) {
  final M3EThemeData theme = M3ETheme.of(context);
  final M3EListFeatureScope? listScope = M3EListFeatureScope.maybeOf(context);
  if (listScope != null &&
      listScope.selectionEnabled &&
      listScope.isSelected(index)) {
    return listScope.selectionState.selectedColor(theme.colorScheme);
  }

  final M3ESelectionScope? scope = M3ESelectionScope.maybeOf(context);
  if (scope == null || !scope.controller.isSelected(index)) {
    return null;
  }
  return theme.selectionTheme.selectedColor(theme.colorScheme);
}

/// Selected equal-corner radius when list selection is active for [index].
BorderRadius? m3eSelectionRadius(
  BuildContext context,
  int index, {
  double? outerRadius,
}) {
  final M3EListFeatureScope? listScope = M3EListFeatureScope.maybeOf(context);
  if (listScope != null &&
      listScope.selectionEnabled &&
      listScope.isSelected(index)) {
    final double radius =
        outerRadius ??
        listScope.selectionState.selectedOuterRadius(
          M3ETheme.of(context).listTheme,
        );
    return BorderRadius.circular(radius);
  }

  final M3ESelectionScope? scope = M3ESelectionScope.maybeOf(context);
  if (scope == null || !scope.controller.isSelected(index)) {
    return null;
  }
  final double radius =
      outerRadius ?? M3ETheme.of(context).listTheme.cardList.outerRadius;
  return BorderRadius.circular(radius);
}
