/// How a card responds to a horizontal swipe.
enum M3ECardSwipeMode {
  /// A drag past the threshold dismisses. A shorter drag springs closed.
  dismiss,

  /// A drag settles open on the background action and does not dismiss.
  reveal,

  /// A short drag reveals the action. A longer drag or flick dismisses.
  both,
}
