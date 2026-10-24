/// Which swipe direction can dismiss a row.
///
/// In left-to-right layouts, [start] is a swipe to the right and [end] is a
/// swipe to the left.
enum M3EListSwipeEdge {
  /// Only a start-to-end swipe dismisses.
  start,

  /// Only an end-to-start swipe dismisses.
  end,

  /// Either direction dismisses.
  both,
}
