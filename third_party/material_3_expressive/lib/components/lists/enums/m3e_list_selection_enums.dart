/// Selection cardinality for list selection.
enum M3EListSelectionMode {
  /// At most one index may be selected.
  single,

  /// Any number of indices may be selected.
  multiple,
}

/// Gesture that toggles list selection.
enum M3EListSelectionTrigger {
  /// Tap the leading selection flip target.
  icon,

  /// Double-tap the list row (single taps stay immediate).
  doubleTap,
}
