part of 'm3e_carousel_view.dart';

/// Scroll physics used by a [M3ECarouselView].
///
/// These physics cause the carousel item to snap to item boundaries.
class M3ECarouselScrollPhysics extends ScrollPhysics {
  /// Creates physics for a [M3ECarouselView].
  const M3ECarouselScrollPhysics({super.parent});

  @override
  M3ECarouselScrollPhysics applyTo(ScrollPhysics? ancestor) {
    return M3ECarouselScrollPhysics(parent: buildParent(ancestor));
  }

  double _getTargetPixels(
    _CarouselPosition position,
    Tolerance tolerance,
    double velocity,
  ) {
    final List<double>? extents = position.restingExtents;
    if (extents != null && extents.isNotEmpty) {
      return _variedExtentTarget(position, extents, tolerance, velocity);
    }
    return _uniformExtentTarget(position, tolerance, velocity);
  }

  /// Target scroll offset when items have individual resting extents.
  double _variedExtentTarget(
    _CarouselPosition position,
    List<double> extents,
    Tolerance tolerance,
    double velocity,
  ) {
    final double item = _applyVelocityBias(
      _itemFromExtents(position.pixels, extents),
      tolerance,
      velocity,
    );
    final int index = item.round().clamp(0, extents.length - 1);
    return _extentPrefix(extents, index);
  }

  /// Target scroll offset when every item shares one extent or flex weight.
  double _uniformExtentTarget(
    _CarouselPosition position,
    Tolerance tolerance,
    double velocity,
  ) {
    final double itemWidth =
        position.viewportDimension * _uniformFraction(position);
    final double actual = math.max(0, position.pixels) / itemWidth;
    final double round = actual.roundToDouble();
    final snapped = (actual - round).abs() < precisionErrorTolerance
        ? round
        : actual;
    final double item = _applyVelocityBias(snapped, tolerance, velocity);
    return item.roundToDouble() * itemWidth;
  }

  /// Fraction of the viewport one item occupies.
  double _uniformFraction(_CarouselPosition position) {
    if (position.itemExtent != null) {
      return position.itemExtent! / position.viewportDimension;
    }
    assert(position.flexWeights != null, 'carousel invariant');
    return position.flexWeights!.first / position.flexWeights!.sum;
  }

  /// Nudges [item] half a step toward the fling direction once the velocity
  /// clears [tolerance], so a decisive swipe advances to the next item.
  double _applyVelocityBias(double item, Tolerance tolerance, double velocity) {
    if (velocity < -tolerance.velocity) {
      return item - 0.5;
    }
    if (velocity > tolerance.velocity) {
      return item + 0.5;
    }
    return item;
  }

  @override
  Simulation? createBallisticSimulation(
    ScrollMetrics position,
    double velocity,
  ) {
    assert(
      position is _CarouselPosition,
      'CarouselScrollPhysics can only be used with Scrollables that uses '
      'the CarouselController',
    );

    final metrics = position as _CarouselPosition;
    if ((velocity <= 0.0 && metrics.pixels <= metrics.minScrollExtent) ||
        (velocity >= 0.0 && metrics.pixels >= metrics.maxScrollExtent)) {
      return super.createBallisticSimulation(metrics, velocity);
    }

    final Tolerance tolerance = toleranceFor(metrics);
    final double target = _getTargetPixels(metrics, tolerance, velocity);
    if (target != metrics.pixels) {
      return ScrollSpringSimulation(
        spring,
        metrics.pixels,
        target,
        velocity,
        tolerance: tolerance,
      );
    }
    return null;
  }

  @override
  bool get allowImplicitScrolling => true;
}
