/// Per-item corner shape within a menu group (Compose `MenuDefaults.itemShape`).
enum M3EMenuItemShape {
  /// Single item, or ungrouped item — uses the full container radius.
  standalone,

  /// First item in a multi-item group.
  leading,

  /// Middle item in a multi-item group.
  middle,

  /// Last item in a multi-item group.
  trailing,
}
