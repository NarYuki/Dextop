import 'package:flutter/widgets.dart';

/// Maps [local] into the box of [hostKey] so siblings share one gradient span.
///
/// Returns [local] untouched until both boxes are laid out.
Rect m3eGradientSpanRect({
  required GlobalKey hostKey,
  required BuildContext context,
  required Rect local,
}) {
  final host = hostKey.currentContext?.findRenderObject() as RenderBox?;
  final box = context.findRenderObject() as RenderBox?;
  if (host == null || box == null || !host.hasSize || !box.hasSize) {
    return local;
  }
  final Offset origin = box.localToGlobal(Offset.zero, ancestor: host);
  return Rect.fromLTWH(
    -origin.dx,
    -origin.dy,
    host.size.width,
    host.size.height,
  );
}
