import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import 'm3e_list_card_list_theme.dart';
import 'm3e_list_dismissible_theme.dart';
import 'm3e_list_expandable_theme.dart';
import 'm3e_list_item_theme.dart';
import 'm3e_list_reorder_state.dart';
import 'm3e_list_selection_state.dart';

export 'm3e_list_card_list_theme.dart';
export 'm3e_list_dismissible_theme.dart';
export 'm3e_list_expandable_theme.dart';
export 'm3e_list_item_theme.dart';

/// Theme values for list-family widgets.
@immutable
class M3EListTheme extends M3EThemeExtension<M3EListTheme> {
  /// M3EListTheme.
  const M3EListTheme({
    this.item = M3EListItemTheme.defaults,
    this.cardList = M3EListCardListTheme.defaults,
    this.dismissible = M3EListDismissibleTheme.defaults,
    this.expandable = M3EListExpandableTheme.defaults,
    this.selection = M3EListSelectionState.defaults,
    this.reorder = M3EListReorderState.defaults,
  });

  /// defaults.

  static const M3EListTheme defaults = M3EListTheme();

  /// item.

  final M3EListItemTheme item;

  /// cardList.
  final M3EListCardListTheme cardList;

  /// dismissible.
  final M3EListDismissibleTheme dismissible;

  /// expandable.
  final M3EListExpandableTheme expandable;

  /// Selection visuals and triggers for list variants.
  final M3EListSelectionState selection;

  /// Reorder visuals and motion for list variants.
  final M3EListReorderState reorder;

  @override
  M3EListTheme copyWith({
    M3EListItemTheme? item,
    M3EListCardListTheme? cardList,
    M3EListDismissibleTheme? dismissible,
    M3EListExpandableTheme? expandable,
    M3EListSelectionState? selection,
    M3EListReorderState? reorder,
  }) {
    return M3EListTheme(
      item: item ?? this.item,
      cardList: cardList ?? this.cardList,
      dismissible: dismissible ?? this.dismissible,
      expandable: expandable ?? this.expandable,
      selection: selection ?? this.selection,
      reorder: reorder ?? this.reorder,
    );
  }

  @override
  M3EListTheme lerp(M3EListTheme? other, double t) {
    if (other is! M3EListTheme) {
      return this;
    }
    if (t < 0.5) {
      return this;
    }
    return other;
  }
}
