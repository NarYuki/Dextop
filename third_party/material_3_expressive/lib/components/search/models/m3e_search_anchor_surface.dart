import 'package:flutter/widgets.dart';

/// Visible search bar surface inside a search anchor.
///
/// The anchor's search bar registers here so the container transform can
/// start from the pill itself instead of the anchor's margin box.
class M3ESearchAnchorSurface {
  /// Context of the registered surface (last registration wins).
  BuildContext? context;

  /// Clears [context] when [surface] is the registered surface.
  void detach(BuildContext surface) {
    if (context == surface) {
      context = null;
    }
  }

  /// Surface rect relative to [ancestor], or null when not laid out.
  Rect? rectIn(RenderObject? ancestor) {
    final BuildContext? surface = context;
    if (surface == null || !surface.mounted) {
      return null;
    }
    final RenderObject? box = surface.findRenderObject();
    if (box is! RenderBox || !box.hasSize || !box.attached) {
      return null;
    }
    return box.localToGlobal(Offset.zero, ancestor: ancestor) & box.size;
  }
}
