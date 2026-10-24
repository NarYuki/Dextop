import 'dart:math' as math;

import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

import '../enums/m3e_tooltip_placement.dart';

/// Positions a tooltip relative to [target] with preferred placement, gap, and
/// 8dp-style steps to stay inside [overlaySize].
class M3ETooltipPositionDelegate extends SingleChildLayoutDelegate {
  /// Creates a position delegate.
  M3ETooltipPositionDelegate({
    required this.target,
    required this.preferred,
    required this.gap,
    required this.step,
    required this.overlaySize,
    required this.textDirection,
  });

  /// Target rect in overlay coordinates.
  final Rect target;

  /// Preferred placement.
  final M3ETooltipPlacement preferred;

  /// Gap between target and tooltip.
  final double gap;

  /// Shift increment when resolving overflow. Spec: 8dp.
  final double step;

  /// Overlay / viewport size.
  final Size overlaySize;

  /// Used to resolve start/end placements.
  final TextDirection textDirection;

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) {
    return constraints.loosen();
  }

  @override
  Offset getPositionForChild(Size size, Size childSize) {
    final order = <M3ETooltipPlacement>[preferred, ..._fallbacks(preferred)];
    Offset? best;
    var bestScore = double.infinity;
    for (final placement in order) {
      final Offset pos = _clampStepped(_raw(placement, childSize), childSize);
      final double score =
          _overflowArea(pos, childSize) + _targetOverlap(pos, childSize);
      if (score < bestScore) {
        bestScore = score;
        best = pos;
      }
      if (score == 0) {
        break;
      }
    }
    return best ?? Offset.zero;
  }

  List<M3ETooltipPlacement> _fallbacks(M3ETooltipPlacement preferred) {
    return switch (preferred) {
      M3ETooltipPlacement.above => const <M3ETooltipPlacement>[
        M3ETooltipPlacement.below,
        M3ETooltipPlacement.bottomEnd,
        M3ETooltipPlacement.bottomStart,
      ],
      M3ETooltipPlacement.below => const <M3ETooltipPlacement>[
        M3ETooltipPlacement.above,
        M3ETooltipPlacement.bottomEnd,
        M3ETooltipPlacement.bottomStart,
      ],
      M3ETooltipPlacement.bottomEnd => const <M3ETooltipPlacement>[
        M3ETooltipPlacement.bottomStart,
        M3ETooltipPlacement.below,
        M3ETooltipPlacement.above,
      ],
      M3ETooltipPlacement.bottomStart => const <M3ETooltipPlacement>[
        M3ETooltipPlacement.bottomEnd,
        M3ETooltipPlacement.below,
        M3ETooltipPlacement.above,
      ],
    };
  }

  Offset _raw(M3ETooltipPlacement placement, Size childSize) {
    final rtl = textDirection == TextDirection.rtl;
    switch (placement) {
      case M3ETooltipPlacement.above:
        return Offset(
          target.center.dx - childSize.width / 2,
          target.top - gap - childSize.height,
        );
      case M3ETooltipPlacement.below:
        return Offset(
          target.center.dx - childSize.width / 2,
          target.bottom + gap,
        );
      case M3ETooltipPlacement.bottomEnd:
      case M3ETooltipPlacement.bottomStart:
        // Trailing/leading relative to text direction.
        final trailing = placement == M3ETooltipPlacement.bottomEnd;
        final bool alignToVisualEnd = trailing ^ rtl;
        final double left = alignToVisualEnd
            ? target.right - childSize.width
            : target.left;
        return Offset(left, target.bottom + gap);
    }
  }

  Offset _clampStepped(Offset pos, Size childSize) {
    final double maxX = math.max(0, overlaySize.width - childSize.width);
    final double maxY = math.max(0, overlaySize.height - childSize.height);
    var dx = pos.dx;
    var dy = pos.dy;

    while (dx < 0) {
      dx += step;
    }
    while (dx > maxX) {
      dx -= step;
    }
    while (dy < 0) {
      dy += step;
    }
    while (dy > maxY) {
      dy -= step;
    }

    return Offset(dx.clamp(0.0, maxX), dy.clamp(0.0, maxY));
  }

  /// Area of this bubble that covers the anchor. A clean placement scores 0
  /// so the tooltip flips beside the control instead of sitting on it.
  double _targetOverlap(Offset pos, Size childSize) {
    final Rect child = pos & childSize;
    if (!child.overlaps(target)) {
      return 0;
    }
    final Rect hit = child.intersect(target);
    if (hit.width <= 0 || hit.height <= 0) {
      return 0;
    }
    return hit.width * hit.height;
  }

  double _overflowArea(Offset pos, Size childSize) {
    final Rect child = pos & childSize;
    final Rect overlay = Offset.zero & overlaySize;
    final Rect clipped = child.intersect(overlay);
    if (clipped.width <= 0 || clipped.height <= 0) {
      return childSize.width * childSize.height;
    }
    return childSize.width * childSize.height - clipped.width * clipped.height;
  }

  @override
  bool shouldRelayout(covariant M3ETooltipPositionDelegate oldDelegate) {
    return target != oldDelegate.target ||
        preferred != oldDelegate.preferred ||
        gap != oldDelegate.gap ||
        step != oldDelegate.step ||
        overlaySize != oldDelegate.overlaySize ||
        textDirection != oldDelegate.textDirection;
  }
}
