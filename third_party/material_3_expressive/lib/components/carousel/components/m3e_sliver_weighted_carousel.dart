part of 'm3e_carousel_view.dart';

/// A sliver that arranges its box children in a linear array, constraining them
/// to specific weights determined by the [weights] property.
class _SliverWeightedCarousel extends SliverMultiBoxAdaptorWidget {
  const _SliverWeightedCarousel({
    required super.delegate,
    required this.consumeMaxWeight,
    required this.shrinkExtent,
    required this.weights,
    required this.infinite,
  });

  final bool consumeMaxWeight;
  final double shrinkExtent;
  final List<int> weights;
  final bool infinite;

  @override
  RenderSliverFixedExtentBoxAdaptor createRenderObject(BuildContext context) {
    final element = context as SliverMultiBoxAdaptorElement;
    return _RenderSliverWeightedCarousel(
      childManager: element,
      consumeMaxWeight: consumeMaxWeight,
      shrinkExtent: shrinkExtent,
      weights: weights,
      infinite: infinite,
    );
  }

  @override
  void updateRenderObject(
    BuildContext context,
    _RenderSliverWeightedCarousel renderObject,
  ) {
    renderObject
      ..consumeMaxWeight = consumeMaxWeight
      ..shrinkExtent = shrinkExtent
      ..weights = weights
      ..infinite = infinite;
  }
}

// A sliver that places its box children in a linear array and constrains them
// to have the corresponding weight which is determined by [weights].
class _RenderSliverWeightedCarousel extends RenderSliverFixedExtentBoxAdaptor
    with _M3EWeightedCarouselLayoutMixin {
  _RenderSliverWeightedCarousel({
    required super.childManager,
    required this._consumeMaxWeight,
    required this._shrinkExtent,
    required this._weights,
    required this._infinite,
  });

  @override
  bool get consumeMaxWeight => _consumeMaxWeight;
  bool _consumeMaxWeight;

  @override
  set consumeMaxWeight(bool value) {
    if (_consumeMaxWeight == value) {
      return;
    }
    _consumeMaxWeight = value;
    markNeedsLayout();
  }

  @override
  double get shrinkExtent => _shrinkExtent;
  double _shrinkExtent;

  @override
  set shrinkExtent(double value) {
    if (_shrinkExtent == value) {
      return;
    }
    _shrinkExtent = value;
    markNeedsLayout();
  }

  @override
  List<int> get weights => _weights;
  List<int> _weights;

  @override
  set weights(List<int> value) {
    if (_weights == value) {
      return;
    }
    _weights = value;
    markNeedsLayout();
  }

  @override
  bool get infinite => _infinite;
  bool _infinite;

  @override
  set infinite(bool value) {
    if (_infinite == value) {
      return;
    }
    _infinite = value;
    markNeedsLayout();
  }
}
