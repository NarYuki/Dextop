/// How a dismissible row responds to a horizontal swipe.
enum M3EListSwipeMode {
  /// A drag past the threshold dismisses. A shorter drag springs closed.
  dismiss,

  /// A drag settles open on the background actions and does not dismiss.
  reveal,

  /// A short drag reveals the actions. A longer drag dismisses.
  both,
}
