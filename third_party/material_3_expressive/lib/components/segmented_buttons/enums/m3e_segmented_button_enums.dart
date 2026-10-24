/// Spec density levels 0, −1, −2, −3.
///
/// Density shrinks container height (−4dp per level).
enum M3ESegmentedButtonDensity {
  /// Level 0 — full token container height (40dp).
  regular,

  /// Level −1 (36dp).
  comfortable,

  /// Level −2 (32dp).
  compact,

  /// Level −3 (28dp).
  dense,
}

/// Helpers for [M3ESegmentedButtonDensity].
extension M3ESegmentedButtonDensityX on M3ESegmentedButtonDensity {
  /// Spec density level (0, −1, −2, −3).
  int get level => switch (this) {
    M3ESegmentedButtonDensity.regular => 0,
    M3ESegmentedButtonDensity.comfortable => -1,
    M3ESegmentedButtonDensity.compact => -2,
    M3ESegmentedButtonDensity.dense => -3,
  };

  /// Height delta in dp (−4.0 per density level).
  double get heightAdjustment => level * 4.0;
}
