/// Visual style of focused search.
enum M3ESearchViewStyle {
  /// Expressive style (recommended).
  ///
  /// The search bar keeps its filled pill (surface container high). Full-screen
  /// views sit on surface container low; docked results sit in their own
  /// rounded container 2dp below the bar. No divider.
  contained,

  /// Baseline style. Not recommended in M3 Expressive.
  ///
  /// One surface container high surface with a 72 (full-screen) or 56
  /// (docked) header and an outline divider above the results.
  divided,
}

/// How a search bar reacts to its scrolling content.
enum M3ESearchBarScrollBehavior {
  /// Stays fixed at the top of the scroll view.
  fixed,

  /// Scrolls away with content and reappears when scrolling toward the top.
  scrollAway,
}
