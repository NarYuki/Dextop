import 'dart:async';

/// Signature for the callback invoked when the user triggers a refresh.
///
/// Mirrors the `RefreshCallback` typedef of the reference implementation: the
/// returned future is awaited by the indicator, which keeps spinning until it
/// completes.
typedef M3ERefreshCallback = Future<void> Function();

/// The lifecycle states an `M3ERefreshIndicator` moves through.
///
/// Mirrors `RefreshIndicatorStatus` from the reference implementation.
enum M3ERefreshStatus {
  /// Pointer is down and the indicator is following the drag.
  drag,

  /// Pull has fully revealed the spinner; releasing now will trigger a refresh.
  /// Does not start the refresh callback by itself.
  armed,

  /// Pointer released past the threshold; the indicator is snapping into the
  /// refreshing position.
  snap,

  /// The refresh callback is running.
  refresh,

  /// The refresh finished and the indicator is scaling away.
  done,

  /// The gesture was abandoned before arming and the indicator is retracting.
  canceled,
}

/// Controls where a pull is allowed to start a refresh.
///
/// Mirrors `RefreshIndicatorTriggerMode` from the reference implementation.
enum M3ERefreshTriggerMode {
  /// A refresh can begin from a drag anywhere within the scrollable.
  anywhere,

  /// A refresh can only begin when the scrollable is already at its edge.
  onEdge,
}
