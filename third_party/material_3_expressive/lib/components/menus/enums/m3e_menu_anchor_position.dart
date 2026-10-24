/// Preferred placement of a menu relative to its anchor.
enum M3EMenuAnchorPosition {
  /// Below the anchor, start-aligned (LTR: left).
  bottomStart,

  /// Below the anchor, end-aligned (LTR: right).
  bottomEnd,

  /// Above the anchor, start-aligned.
  topStart,

  /// Above the anchor, end-aligned.
  topEnd,

  /// Cascading submenu: end side of the anchor (Compose `MenuAnchorPosition.End`).
  end,

  /// Cascading submenu: start side of the anchor.
  start,
}
