/// Controls the direction in which the dropdown overlay expands.
///
/// * [M3EDropdownExpandDirection.auto]: Automatically determines the direction based on
///   available space. Falls back to showing above if there's not enough space below.
/// * [M3EDropdownExpandDirection.down]: Forces the dropdown to expand downward.
/// * [M3EDropdownExpandDirection.up]: Forces the dropdown to expand upward.
enum M3EDropdownExpandDirection {
  /// Automatically determine the best direction based on available space.
  auto,

  /// Force the dropdown to expand downward.
  down,

  /// Force the dropdown to expand upward.
  up,
}
