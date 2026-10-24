/// Controls when destination labels are shown in the navigation bar.
enum M3ENavBarLabelBehavior {
  /// Always show destination labels.
  alwaysShow,

  /// Show the label only on the selected destination.
  onlySelected,

  /// Never show destination labels.
  alwaysHide,
}

/// Controls when destination icons are shown in the navigation bar.
enum M3ENavBarIconBehavior {
  /// Always show destination icons.
  alwaysShow,

  /// Show the icon only on the selected destination.
  onlySelected,

  /// Never show destination icons.
  alwaysHide,
}

/// Destination item arrangement for the navigation bar.
enum M3ENavBarLayout {
  /// Vertical icon-over-label cells that expand equally.
  compact,

  /// Horizontal icon+label chips packed as a group inside a full-width bar.
  wide,
}

/// Horizontal placement of the destination group in [M3ENavBarLayout.wide].
enum M3ENavBarAlignment {
  /// Pack destinations toward the start (left in LTR).
  start,

  /// Center the destination group in the bar.
  center,

  /// Pack destinations toward the end (right in LTR).
  end,
}

/// The overall height variant of the navigation bar.
enum M3ENavBarSize {
  /// Compact navigation bar height.
  small,

  /// Standard navigation bar height.
  medium,
}

/// The container shape family of the navigation bar.
enum M3ENavBarShapeFamily {
  /// Rounded container corners.
  round,

  /// Square container corners.
  square,
}

/// Density adjustment for the navigation bar metrics.
enum M3ENavBarDensity {
  /// Default spacing and sizing.
  regular,

  /// Tighter spacing and sizing.
  compact,
}

/// The visual style of the selection indicator.
enum M3ENavBarIndicatorStyle {
  /// Pill-shaped selection indicator.
  pill,

  /// Underline selection indicator.
  underline,

  /// No selection indicator.
  none,
}
