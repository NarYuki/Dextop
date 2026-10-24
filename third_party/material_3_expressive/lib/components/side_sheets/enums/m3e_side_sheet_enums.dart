/// Side sheet variants.
enum M3ESideSheetVariant {
  /// Sits next to the main content; the content stays interactive.
  standard,

  /// Shown over a scrim; must be dismissed to reach the content.
  modal,
}

/// Window edge a side sheet is anchored to.
///
/// Edges follow the text direction, so [end] is the right edge in
/// left-to-right languages and the left edge in right-to-left ones.
enum M3ESideSheetEdge {
  /// Trailing edge (default).
  end,

  /// Leading edge.
  start,
}

/// How `M3ESideSheetLayout` presents its sheet.
enum M3ESideSheetLayoutMode {
  /// Modal below the compact breakpoint, standard at and above it.
  adaptive,

  /// Always standard.
  standard,

  /// Always modal.
  modal,
}
