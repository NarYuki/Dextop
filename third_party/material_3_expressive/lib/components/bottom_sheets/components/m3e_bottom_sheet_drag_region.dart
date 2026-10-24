import 'package:flutter/widgets.dart';

/// Top strip of a bottom sheet that holds the drag handle.
///
/// Shows the grab cursor, and grabbing while the sheet is dragged.
class M3EBottomSheetDragRegion extends StatelessWidget {
  /// M3EBottomSheetDragRegion.
  const M3EBottomSheetDragRegion({
    required this.height,
    required this.cursor,
    required this.child,
    super.key,
  });

  /// Strip height (48).
  final double height;

  /// Cursor over the strip.
  final MouseCursor cursor;

  /// Drag handle.
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: cursor,
      child: SizedBox(
        height: height,
        width: double.infinity,
        child: Center(child: child),
      ),
    );
  }
}
