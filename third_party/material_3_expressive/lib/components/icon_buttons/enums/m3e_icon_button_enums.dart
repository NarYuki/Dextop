/// Visual scale labels (A–E in the spec).
enum M3EIconButtonSize {
  /// Extra-small icon button.
  xs,

  /// Small icon button.
  sm,

  /// Medium (default) icon button.
  md,

  /// Large icon button.
  lg,

  /// Extra-large icon button.
  xl,
}

/// Width variants of the button's container (not the icon glyph).
enum M3EIconButtonWidth {
  /// Default container width for the size.
  defaultWidth,

  /// Narrower container than the default.
  narrow,

  /// Wider container than the default.
  wide,
}

/// The two resting shape variants.
enum M3EIconButtonShapeVariant {
  /// Round resting shape.
  round,

  /// Square resting shape.
  square,
}

/// Visual variants (kept from previous API).
enum M3EIconButtonVariant {
  /// Transparent container with on-surface content.
  standard,

  /// Filled container with contrasting content.
  filled,

  /// Tonal (secondary container) fill.
  tonal,

  /// Outlined container with a border.
  outlined,
}
