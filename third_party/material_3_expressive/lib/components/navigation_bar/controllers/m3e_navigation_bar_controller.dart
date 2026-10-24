import 'package:flutter/foundation.dart';

/// Selects a destination and shows or hides the bar from outside.
///
/// Pass it to `M3ENavigationBar.controller`. [select] runs the bar's
/// `onDestinationSelected`. [index] follows the bar's selected index.
/// [show] and [hide] apply when `hideOnScroll` is on.
class M3ENavigationBarController extends ChangeNotifier {
  /// Creates a controller at [index].
  M3ENavigationBarController({this.index = 0, this.visible = true});

  /// Selected destination index.
  int index;

  /// Whether the bar is shown. Scroll and [show] / [hide] update this.
  bool visible;

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

  /// Shows the bar when hide-on-scroll is on.
  void show() => _setVisible(true);

  /// Hides the bar when hide-on-scroll is on.
  void hide() => _setVisible(false);

  /// Binds the bar. [select] is the bar's `onDestinationSelected`.
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

  /// Stores whether the bar is shown.
  void updateVisible({required bool visible}) => _setVisible(visible);

  void _setVisible(bool value) {
    if (visible == value) {
      return;
    }
    visible = value;
    notifyListeners();
  }
}
