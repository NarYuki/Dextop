/// Container treatment for expressive lists.
///
/// [segmented] keeps a resting container with 16dp outer and 4dp inner
/// corners. [standard] has no resting container; hover, focus, press,
/// drag, and selection paint a 16dp fill.
enum M3EListStyle {
  /// Resting container with segmented corners.
  segmented,

  /// No resting container. Interaction and selection paint the fill.
  standard,
}

/// Visual system for a list.
///
/// [expressive] is the default. [baseline] keeps square corners and the
/// older size tokens.
enum M3EListAppearance {
  /// Segmented corners, 10dp vertical padding, and 20dp icons.
  expressive,

  /// Square unselected corners, 8dp vertical padding, and 24dp icons.
  baseline,
}

/// The position of a card within a list, used to determine its corner radii.
enum M3ECardPosition {
  /// The first item in a list with more than one item.
  first,

  /// An item between the first and last items.
  middle,

  /// The last item in a list with more than one item.
  last,

  /// The only item in a list.
  single,
}
