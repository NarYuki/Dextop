/// How buttons in the group are visually connected.
enum M3EButtonGroupType {
  /// Independent buttons with size-token between-space (18 / 12 / 8 / 8 / 8).
  standard,

  /// Joined segments with a 2dp gap; inner corners morph on press/selection.
  connected,
}

/// Spec density levels 0, −1, −2, −3.
///
/// Density shrinks container height (−4dp per level). Between-space is unchanged.
enum M3EButtonGroupDensity {
  /// Level 0 — full token container height.
  regular,

  /// Level −1.
  comfortable,

  /// Level −2.
  compact,

  /// Level −3.
  dense,
}

/// Helpers for [M3EButtonGroupDensity].
extension M3EButtonGroupDensityX on M3EButtonGroupDensity {
  /// Spec density level (0, −1, −2, −3).
  int get level => switch (this) {
    M3EButtonGroupDensity.regular => 0,
    M3EButtonGroupDensity.comfortable => -1,
    M3EButtonGroupDensity.compact => -2,
    M3EButtonGroupDensity.dense => -3,
  };

  /// Height delta in dp (−4.0 per density level).
  double get heightAdjustment => level * 4.0;
}

/// How the group handles actions that do not fit the available main-axis space.
enum M3EButtonGroupOverflow {
  /// Clip / overflow without a special strategy (content may exceed bounds).
  none,

  /// Scroll along the main axis when constraints are bounded.
  scroll,

  /// Collapse trailing actions into an overflow menu trigger.
  menu,

  /// Shift a visible paging window with back/forward overflow triggers.
  experimentalPaging,
}

/// Presentation style for [M3EButtonGroupOverflow.menu].
enum M3EButtonGroupOverflowMenuStyle {
  /// Overflow actions in a popup menu.
  popup,

  /// Overflow actions in a modal bottom sheet.
  bottomSheet,
}
