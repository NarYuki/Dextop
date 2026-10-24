/// Bottom sheet variants.
enum M3EBottomSheetVariant {
  /// Sits in the layout next to the main UI, with no scrim.
  standard,

  /// Shown above a scrim; blocks the rest of the app.
  modal,
}

/// Preset heights a bottom sheet can rest at.
enum M3EBottomSheetValue {
  /// Fully off screen.
  hidden,

  /// Optional peek height of a standard sheet.
  preview,

  /// Initial height, capped at half the screen.
  collapsed,

  /// Content height, up to the screen minus the top margin.
  expanded,

  /// Fills the screen. Only with `expandToFullScreen`.
  fullScreen,
}
