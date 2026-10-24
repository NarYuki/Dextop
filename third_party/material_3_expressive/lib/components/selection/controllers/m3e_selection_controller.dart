import 'package:flutter/foundation.dart';

/// Index-based multi-select state for selection surfaces.
class M3ESelectionController extends ChangeNotifier {
  /// Creates a selection controller.
  M3ESelectionController({Set<int>? initialSelected})
    : _selected = <int>{...?initialSelected};

  final Set<int> _selected;

  /// Currently selected indices (unmodifiable view).
  Set<int> get selectedIndices => Set<int>.unmodifiable(_selected);

  /// Whether any item is selected.
  bool get isSelectionMode => _selected.isNotEmpty;

  /// Number of selected indices.
  int get selectedCount => _selected.length;

  /// Whether [index] is selected.
  bool isSelected(int index) => _selected.contains(index);

  /// Tristate value for a select-all control given [itemCount].
  ///
  /// Returns `true` when all selected, `false` when none, and `null` when
  /// some but not all are selected.
  bool? allSelectedFor(int itemCount) {
    if (itemCount <= 0 || _selected.isEmpty) {
      return false;
    }
    if (_selected.length >= itemCount) {
      return true;
    }
    return null;
  }

  /// Selects [index] if not already selected.
  void select(int index) {
    if (_selected.add(index)) {
      notifyListeners();
    }
  }

  /// Deselects [index] if selected.
  void deselect(int index) {
    if (_selected.remove(index)) {
      notifyListeners();
    }
  }

  /// Toggles selection for [index]. Returns the new selected state.
  bool toggle(int index) {
    final bool nowSelected;
    if (_selected.contains(index)) {
      _selected.remove(index);
      nowSelected = false;
    } else {
      _selected.add(index);
      nowSelected = true;
    }
    notifyListeners();
    return nowSelected;
  }

  /// Selects every index in `0..itemCount-1`.
  void selectAll(int itemCount) {
    if (itemCount <= 0) {
      return;
    }
    var changed = false;
    for (var i = 0; i < itemCount; i++) {
      changed = _selected.add(i) || changed;
    }
    if (changed) {
      notifyListeners();
    }
  }

  /// Clears all selection.
  void clear() {
    if (_selected.isEmpty) {
      return;
    }
    _selected.clear();
    notifyListeners();
  }
}
