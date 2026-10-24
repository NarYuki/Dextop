import 'package:flutter/foundation.dart';

/// Selects a destination and opens or closes a navigation drawer.
///
/// Pass it to `M3ENavigationDrawer.controller`. [select] runs the drawer's
/// `onDestinationSelected`. [index] follows the drawer's selected index.
/// [attach] does not notify listeners.
class M3ENavigationDrawerController extends ChangeNotifier {
  /// Creates a controller at [index].
  M3ENavigationDrawerController({this.index = 0, this.isOpen = false});

  /// Selected destination index.
  int index;

  /// Whether a dismissible or modal drawer is open.
  bool isOpen;

  ValueChanged<int>? _select;
  ValueChanged<bool>? _setOpen;

  /// Whether [attach] has been called.
  bool get isAttached => _select != null;

  /// Asks the attached drawer to select [index].
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

  /// Opens a dismissible or modal drawer.
  void open() => _apply(true);

  /// Closes a dismissible or modal drawer.
  void close() => _apply(false);

  /// Switches between open and closed.
  void toggle() => _apply(!isOpen);

  /// Binds the drawer.
  void attach({
    required ValueChanged<int> select,
    required ValueChanged<bool> setOpen,
    required int index,
    required bool isOpen,
  }) {
    _select = select;
    _setOpen = setOpen;
    this.index = index;
    this.isOpen = isOpen;
  }

  /// Drops the drawer when [select] is still the bound callback.
  void detach(ValueChanged<int> select) {
    if (!identical(_select, select)) {
      return;
    }
    _select = null;
    _setOpen = null;
  }

  /// Stores the drawer's current index.
  void updateIndex(int index) {
    if (this.index == index) {
      return;
    }
    this.index = index;
    notifyListeners();
  }

  /// Stores whether the drawer is open.
  void updateIsOpen({required bool isOpen}) {
    if (this.isOpen == isOpen) {
      return;
    }
    this.isOpen = isOpen;
    notifyListeners();
  }

  void _apply(bool value) {
    final ValueChanged<bool>? setOpen = _setOpen;
    if (setOpen == null) {
      if (isOpen == value) {
        return;
      }
      isOpen = value;
      notifyListeners();
      return;
    }
    setOpen(value);
  }
}
