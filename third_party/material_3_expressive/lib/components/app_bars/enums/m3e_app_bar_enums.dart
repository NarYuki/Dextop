/// The top app bar layouts. Mirrors `AppBarM3EVariant`.
///
/// [small] is one row. Every other size grows a second band and collapses
/// back to [small] as the page scrolls. `M3EAppBar.top`, `M3EAppBar.search`,
/// and `M3EAppBar.sliver` all accept a variant.
enum M3EAppBarVariant {
  /// One row, 64dp. The title stays on one line.
  small,

  /// Expressive two-row bar, 112dp (136dp with a subtitle).
  mediumFlexible,

  /// Expressive two-row bar, 120dp (152dp with a subtitle).
  largeFlexible,

  /// Baseline two-row bar, 112dp (136dp with a subtitle).
  medium,

  /// Baseline two-row bar, 152dp (184dp with a subtitle).
  large,
}

/// The corner shape family for an app bar container. Mirrors
/// `AppBarM3EShapeFamily`.
enum M3EAppBarShapeFamily {
  /// Rounded container corners.
  round,

  /// Squared container corners.
  square,
}

/// How an app bar reacts when content scrolls forward.
///
/// `hideOnScroll` selects entire when this value is none.
enum M3EAppBarHideMode {
  /// The bar stays in place.
  none,

  /// The whole bar slides up, then back down toward the top of the scroll.
  entire,

  /// The container, title, subtitle, search, and image slide up. Leading and
  /// trailing actions stay, on a tonal fill, until the scroll returns.
  actions,
}

/// The vertical density of an app bar. Mirrors `AppBarM3EDensity`.
enum M3EAppBarDensity {
  /// Standard M3 heights.
  regular,

  /// Heights reduced by 8dp.
  compact,
}
