/// Vertical position of `M3ETextField` icons or prefix/suffix text when the
/// field is taller than one line (multi-line or text area).
enum M3ETextFieldSlotAlignment {
  /// Stays where it sits in a single-line field, level with the first line.
  firstLine,

  /// Vertically centered in the field.
  center,

  /// Level with the last line, at the bottom of the field.
  bottom,
}
