/// Modality for the expanded rail.
enum M3ENavigationRailModality {
  /// Occupies layout space.
  standard,

  /// Overlays content with a scrim. Open it from a button. Dismiss leaves no rail.
  modal,
}

/// M3 Expressive types for the rail.
enum M3ENavigationRailType {
  /// Slim 96dp rail.
  collapsed,

  /// Slim 96dp rail with no button to expand.
  alwaysCollapse,

  /// Wide 220–360dp rail that replaces the drawer.
  expanded,

  /// Wide 220–360dp rail that replaces the drawer with no button to collapse.
  alwaysExpand,
}

/// Convenience extension for checking the current rail type.
extension M3ENavigationRailTypeX on M3ENavigationRailType {
  /// Whether this type equals [M3ENavigationRailType.collapsed].
  bool get isCollapsed => this == M3ENavigationRailType.collapsed;

  /// Whether this type equals [M3ENavigationRailType.expanded].
  bool get isExpanded => this == M3ENavigationRailType.expanded;
}

/// Vertical alignment of the destination group.
enum M3ENavigationRailAlignment {
  /// Packs destinations under the menu and FAB.
  top,

  /// Centers destinations in the space under the menu and FAB.
  center,
}

/// Controls how labels are shown for rail destinations.
///
/// - alwaysShow (default): show all labels (subject to width constraints).
/// - onlySelected: show the label only for the selected destination.
/// - alwaysHide: never show labels even when expanded.
enum M3ENavigationRailLabelBehavior {
  /// Always show labels (subject to width constraints).
  alwaysShow,

  /// Show the label only for the selected destination.
  onlySelected,

  /// Never show labels even when expanded.
  alwaysHide,
}
