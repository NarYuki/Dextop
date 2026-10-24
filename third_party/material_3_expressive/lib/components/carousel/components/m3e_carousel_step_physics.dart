import 'package:flutter/widgets.dart';

/// Scroll physics for a carousel swipe that advances one item.
///
/// The scroll view does not drag. A programmatic step is not followed by a
/// ballistic settle, so the item that just became leading is not snapped away.
class M3ECarouselStepPhysics extends NeverScrollableScrollPhysics {
  /// Creates physics that ignore user dragging and end-of-step flings.
  const M3ECarouselStepPhysics({super.parent});

  @override
  M3ECarouselStepPhysics applyTo(ScrollPhysics? ancestor) {
    return const M3ECarouselStepPhysics();
  }

  @override
  Simulation? createBallisticSimulation(
    ScrollMetrics position,
    double velocity,
  ) {
    return null;
  }
}
