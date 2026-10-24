import 'package:flutter/widgets.dart';

import '../models/m3e_search_anchor_surface.dart';

/// Shares a [M3ESearchAnchorSurface] with search bars built by an anchor.
class M3ESearchAnchorScope extends InheritedWidget {
  /// M3ESearchAnchorScope.
  const M3ESearchAnchorScope({
    required this.surface,
    required super.child,
    super.key,
  });

  /// Surface the anchor measures for its container transform.
  final M3ESearchAnchorSurface surface;

  /// Nearest surface, without a rebuild dependency.
  static M3ESearchAnchorSurface? maybeOf(BuildContext context) {
    return context
        .getInheritedWidgetOfExactType<M3ESearchAnchorScope>()
        ?.surface;
  }

  @override
  bool updateShouldNotify(M3ESearchAnchorScope oldWidget) =>
      surface != oldWidget.surface;
}
