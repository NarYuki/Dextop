import 'package:flutter/foundation.dart';

/// Selects a destination and expands, collapses, or hides the rail.
///
/// Pass it to `M3ENavigationRail.controller`. [select] runs the rail's
/// `onDestinationSelected`. [index] follows the rail's selected index.
class M3ENavigationRailController extends ChangeNotifier {
  /// Creates a controller at [index].
  M3ENavigationRailController({
    this.index = 0,
    this.expanded = true,
    this.visible = true,
  });

  /// Selected destination index.
  int index;

  /// Whether the rail is expanded.
  bool expanded;

  /// Whether an immersive rail is shown.
  bool visible;

  ValueChanged<int>? _select;
  ValueChanged<bool>? _setExpanded;
  ValueChanged<bool>? _setVisible;

  /// Whether [attach] has been called.
  bool get isAttached => _select != null;

  /// Asks the attached rail to select [index].
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

  /// Expands the rail.
  void expand() => _applyExpanded(true);

  /// Collapses the rail.
  void collapse() => _applyExpanded(false);

  /// Switches between expanded and collapsed.
  void toggle() => _applyExpanded(!expanded);

  /// Shows an immersive rail.
  void show() => _applyVisible(true);

  /// Hides an immersive rail.
  void hide() => _applyVisible(false);

  /// Binds the rail.
  void attach({
    required ValueChanged<int> select,
    required ValueChanged<bool> setExpanded,
    required ValueChanged<bool> setVisible,
    required int index,
    required bool expanded,
    required bool visible,
  }) {
    _select = select;
    _setExpanded = setExpanded;
    _setVisible = setVisible;
    this.index = index;
    this.expanded = expanded;
    this.visible = visible;
  }

  /// Drops the rail when [select] is still the bound callback.
  void detach(ValueChanged<int> select) {
    if (!identical(_select, select)) {
      return;
    }
    _select = null;
    _setExpanded = null;
    _setVisible = null;
  }

  /// Stores the rail's current index.
  void updateIndex(int index) {
    if (this.index == index) {
      return;
    }
    this.index = index;
    notifyListeners();
  }

  /// Stores whether the rail is expanded.
  void updateExpanded({required bool expanded}) {
    if (this.expanded == expanded) {
      return;
    }
    this.expanded = expanded;
    notifyListeners();
  }

  /// Stores whether an immersive rail is shown.
  void updateVisible({required bool visible}) {
    if (this.visible == visible) {
      return;
    }
    this.visible = visible;
    notifyListeners();
  }

  void _applyExpanded(bool value) {
    final ValueChanged<bool>? setExpanded = _setExpanded;
    if (setExpanded == null) {
      if (expanded == value) {
        return;
      }
      expanded = value;
      notifyListeners();
      return;
    }
    setExpanded(value);
  }

  void _applyVisible(bool value) {
    final ValueChanged<bool>? setVisible = _setVisible;
    if (setVisible == null) {
      if (visible == value) {
        return;
      }
      visible = value;
      notifyListeners();
      return;
    }
    setVisible(value);
  }
}
