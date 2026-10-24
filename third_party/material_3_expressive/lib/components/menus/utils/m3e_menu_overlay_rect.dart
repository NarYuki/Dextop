import 'package:flutter/widgets.dart';

/// Global (overlay) rectangle for [context]'s render box, including transforms.
///
/// Prefer this over `localToGlobal(Offset.zero) & size` when ancestors use
/// [Transform] (e.g. menu open scale), which otherwise mis-anchors submenus.
Rect? m3eOverlayRectFor(BuildContext context) {
  final RenderObject? object = context.findRenderObject();
  if (object is! RenderBox || !object.hasSize || !object.attached) {
    return null;
  }
  final OverlayState? overlay = Overlay.maybeOf(context);
  final RenderObject? ancestor = overlay?.context.findRenderObject();
  return MatrixUtils.transformRect(
    object.getTransformTo(ancestor),
    Offset.zero & object.size,
  );
}
