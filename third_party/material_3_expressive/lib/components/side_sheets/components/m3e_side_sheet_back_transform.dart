import 'package:flutter/widgets.dart';

import '../styles/m3e_side_sheet_theme.dart';

/// Predictive-back scale of a side sheet.
///
/// The sheet lifts off the top and bottom edges and scales toward the
/// gesture: it grows when the swipe starts at its anchored edge and shrinks
/// otherwise, pivoting on the anchored edge.
class M3ESideSheetBackTransform extends StatelessWidget {
  /// M3ESideSheetBackTransform.
  const M3ESideSheetBackTransform({
    required this.motion,
    required this.progress,
    required this.anchoredRight,
    required this.fromAnchoredEdge,
    required this.child,
    super.key,
  });

  /// Predictive-back amounts.
  final M3ESideSheetMotion motion;

  /// Gesture progress (0–1).
  final double progress;

  /// Whether the sheet sits on the right window edge.
  final bool anchoredRight;

  /// Whether the swipe started at the sheet's anchored edge.
  final bool fromAnchoredEdge;

  /// The sheet.
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final double p = progress.clamp(0, 1).toDouble();
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints c) {
        final double w = c.maxWidth;
        final double h = c.maxHeight;
        final bool sized = w.isFinite && h.isFinite && w > 0 && h > 0;
        final double dx = fromAnchoredEdge
            ? motion.predictiveBackGrowX
            : -motion.predictiveBackShrinkX;
        final double sx = sized ? 1 + dx * p / w : 1;
        final double sy = sized ? 1 - motion.predictiveBackLiftY * p / h : 1;
        return Transform(
          origin: sized ? Offset(anchoredRight ? w : 0, h / 2) : null,
          transform: Matrix4.diagonal3Values(sx, sy, 1),
          child: child,
        );
      },
    );
  }
}
