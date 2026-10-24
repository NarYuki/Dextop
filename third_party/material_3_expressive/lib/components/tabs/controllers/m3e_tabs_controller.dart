import 'package:flutter/foundation.dart';

/// Selects a tab from outside the bar.
///
/// Pass it to `M3ETabs.controller`. [select] runs the bar's
/// `onTabSelected`. [index] follows the bar's selected index.
class M3ETabsController extends ChangeNotifier {
  /// Creates a controller at [index].
  M3ETabsController({this.index = 0});

  /// Selected tab index.
  int index;

  ValueChanged<int>? _select;

  /// Whether [attach] has been called.
  bool get isAttached => _select != null;

  /// Asks the attached bar to select [index].
  void select(int index) {
    final ValueChanged<int>? select = _select;
    if (select == null) {
      if (this.index == index) {
        return;
      }
      this.index = index;
      notifyListeners();
      return;
    }
    select(index);
  }

  /// Binds the bar. [select] is the bar's `onTabSelected`.
  void attach(ValueChanged<int> select, int index) {
    _select = select;
    this.index = index;
  }

  /// Drops the bar when [select] is still the bound callback.
  void detach(ValueChanged<int> select) {
    if (!identical(_select, select)) {
      return;
    }
    _select = null;
  }

  /// Stores the bar's current index.
  void updateIndex(int index) {
    if (this.index == index) {
      return;
    }
    this.index = index;
    notifyListeners();
  }
}
