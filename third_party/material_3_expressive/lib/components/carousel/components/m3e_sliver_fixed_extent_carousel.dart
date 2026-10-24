part of 'm3e_carousel_view.dart';

/// A sliver that displays its box children in a linear array with a fixed extent
/// per item.
class _SliverFixedExtentCarousel extends SliverMultiBoxAdaptorWidget {
  const _SliverFixedExtentCarousel({
    required super.delegate,
    required this.minExtent,
    required this.itemExtent,
    required this.scaleItems,
    required this.infinite,
    this.restingExtents,
  });

  final double itemExtent;
  final double minExtent;
  final bool scaleItems;
  final bool infinite;

  /// Per-item resting size. When set, each item shrinks from its own size.
  final List<double>? restingExtents;

  @override
  RenderSliverFixedExtentBoxAdaptor createRenderObject(BuildContext context) {
    final element = context as SliverMultiBoxAdaptorElement;
    return _RenderSliverFixedExtentCarousel(
      childManager: element,
      minExtent: minExtent,
      maxExtent: itemExtent,
      scaleItems: scaleItems,
      infinite: infinite,
      restingExtents: restingExtents,
    );
  }

  @override
  void updateRenderObject(
    BuildContext context,
    _RenderSliverFixedExtentCarousel renderObject,
  ) {
    renderObject
      ..maxExtent = itemExtent
      ..minExtent = minExtent
      ..scaleItems = scaleItems
      ..infinite = infinite
      ..restingExtents = restingExtents;
  }
}

class _RenderSliverFixedExtentCarousel
    extends RenderSliverFixedExtentBoxAdaptor {
  _RenderSliverFixedExtentCarousel({
    required super.childManager,
    required this._maxExtent,
    required this._minExtent,
    required this._scaleItems,
    required this._infinite,
    List<double>? restingExtents,
  }) : _restingExtents = restingExtents == null
           ? null
           : List<double>.from(restingExtents);

  double get maxExtent => _maxExtent;
  double _maxExtent;

  set maxExtent(double value) {
    if (_maxExtent == value) {
      return;
    }
    _maxExtent = value;
    markNeedsLayout();
  }

  double get minExtent => _minExtent;
  double _minExtent;

  set minExtent(double value) {
    if (_minExtent == value) {
      return;
    }
    _minExtent = value;
    markNeedsLayout();
  }

  bool get scaleItems => _scaleItems;
  bool _scaleItems;

  set scaleItems(bool value) {
    if (_scaleItems == value) {
      return;
    }
    _scaleItems = value;
    markNeedsLayout();
  }

  bool get infinite => _infinite;
  bool _infinite;

  set infinite(bool value) {
    if (_infinite == value) {
      return;
    }
    _infinite = value;
    markNeedsLayout();
  }

  List<double>? get restingExtents => _restingExtents;
  List<double>? _restingExtents;

  set restingExtents(List<double>? value) {
    if (_sameExtents(_restingExtents, value)) {
      return;
    }
    _restingExtents = value == null ? null : List<double>.from(value);
    markNeedsLayout();
  }

  // This implements the [itemExtentBuilder] callback.
  double _buildItemExtent(
    int index,
    SliverLayoutDimensions currentLayoutDimensions,
  ) {
    final List<double>? extents = _restingExtents;
    if (extents != null && extents.isNotEmpty) {
      return _variedExtent(index, extents);
    }

    if (maxExtent == 0.0 || !scaleItems) {
      return maxExtent;
    }

    final int firstVisibleIndex = (constraints.scrollOffset / maxExtent)
        .floor();
    final offscreenItems = firstVisibleIndex;
    final double offscreenExtent =
        constraints.scrollOffset - offscreenItems * maxExtent;
    final double effectiveMinExtent = _effectiveMinExtent;

    // The leading item shrinks as it leaves. The trailing item grows into the
    // space that is left. Both stop at [effectiveMinExtent]; past that they
    // scroll off the edge instead of changing size.
    if (index == firstVisibleIndex) {
      return math.max(maxExtent - offscreenExtent, effectiveMinExtent);
    }

    final double scrollOffsetForLastIndex =
        constraints.scrollOffset + constraints.remainingPaintExtent;
    if (index ==
        getMaxChildIndexForScrollOffset(scrollOffsetForLastIndex, maxExtent)) {
      return clampDouble(
        scrollOffsetForLastIndex - maxExtent * index,
        effectiveMinExtent,
        maxExtent,
      );
    }

    return maxExtent;
  }

  /// Remainder of the viewport, and never smaller than [minExtent].
  ///
  /// Capped at [maxExtent] so a shrink extent larger than the item cannot
  /// invert [clampDouble].
  double get _effectiveMinExtent => math.min(
    maxExtent,
    math.max(constraints.remainingPaintExtent % maxExtent, minExtent),
  );

  /// The layout offset for the child with the given index.
  @override
  double indexToLayoutOffset(
    @Deprecated(
      'The itemExtent is already available within the scope of this function. '
      'This feature was deprecated after v3.20.0-7.0.pre.',
    )
    double itemExtent,
    int index,
  ) {
    final List<double>? extents = _restingExtents;
    if (extents != null && extents.isNotEmpty) {
      return _variedOffset(index, extents);
    }

    if (maxExtent == 0.0 || !scaleItems) {
      return maxExtent * index;
    }

    final int firstVisibleIndex = (constraints.scrollOffset / maxExtent)
        .floor();
    if (index == firstVisibleIndex) {
      final double firstVisibleItemExtent = _buildItemExtent(
        index,
        layoutDimensions,
      );
      // Past the shrink floor the item stops resizing and scrolls off.
      if (firstVisibleItemExtent <= _effectiveMinExtent) {
        return maxExtent * index - _effectiveMinExtent + maxExtent;
      }
      return constraints.scrollOffset;
    }
    return maxExtent * index;
  }

  /// The minimum child index that is visible at the given scroll offset.
  @override
  int getMinChildIndexForScrollOffset(
    double scrollOffset,
    @Deprecated(
      'The itemExtent is already available within the scope of this function. '
      'This feature was deprecated after v3.20.0-7.0.pre.',
    )
    double itemExtent,
  ) {
    final List<double>? extents = _restingExtents;
    if (extents != null && extents.isNotEmpty) {
      return _leadingIndex(scrollOffset, extents);
    }

    if (maxExtent == 0.0) {
      return 0;
    }

    final int firstVisibleIndex = (scrollOffset / maxExtent).floor();
    return math.max(firstVisibleIndex, 0);
  }

  /// The maximum child index that is visible at the given scroll offset.
  @override
  int getMaxChildIndexForScrollOffset(
    double scrollOffset,
    @Deprecated(
      'The itemExtent is already available within the scope of this function. '
      'This feature was deprecated after v3.20.0-7.0.pre.',
    )
    double itemExtent,
  ) {
    final List<double>? extents = _restingExtents;
    if (extents != null && extents.isNotEmpty) {
      return _trailingIndex(scrollOffset, extents);
    }

    if (maxExtent > 0.0) {
      final double actual = scrollOffset / maxExtent - 1;
      final int round = actual.round();
      if ((actual * maxExtent - round * maxExtent).abs() <
          precisionErrorTolerance) {
        return math.max(0, round);
      }
      return math.max(0, actual.ceil());
    }
    return 0;
  }

  @override
  double computeMaxScrollOffset(
    SliverConstraints constraints,
    double itemExtent,
  ) {
    final List<double>? extents = _restingExtents;
    if (extents != null && extents.isNotEmpty) {
      double sum = 0;
      for (final double extent in extents) {
        sum += extent;
      }
      return sum;
    }
    return super.computeMaxScrollOffset(constraints, itemExtent);
  }

  double _variedExtent(int index, List<double> extents) {
    if (index < 0 || index >= extents.length) {
      return 0;
    }
    final double rest = extents[index];
    if (!scaleItems) {
      return rest;
    }
    final int leading = _leadingIndex(constraints.scrollOffset, extents);
    if (index == leading) {
      final double into =
          constraints.scrollOffset - _extentPrefix(extents, index);
      return math.max(rest - into, _floorFor(rest));
    }
    final double end =
        constraints.scrollOffset + constraints.remainingPaintExtent;
    final int trailing = _trailingIndex(end, extents);
    if (index == trailing) {
      final double leftover = end - _extentPrefix(extents, index);
      return clampDouble(leftover, _floorFor(rest), rest);
    }
    return rest;
  }

  double _variedOffset(int index, List<double> extents) {
    if (!scaleItems || index < 0 || index >= extents.length) {
      return _extentPrefix(extents, index);
    }
    final int leading = _leadingIndex(constraints.scrollOffset, extents);
    if (index != leading) {
      return _extentPrefix(extents, index);
    }
    final double rest = extents[index];
    final double floor = _floorFor(rest);
    final double extent = _variedExtent(index, extents);
    if (extent <= floor) {
      return _extentPrefix(extents, index) + (rest - floor);
    }
    return constraints.scrollOffset;
  }

  /// Shrink floor for one item, matching the uniform uncontained rule.
  double _floorFor(double itemMax) {
    if (itemMax <= 0) {
      return 0;
    }
    return math.min(
      itemMax,
      math.max(constraints.remainingPaintExtent % itemMax, minExtent),
    );
  }

  int _leadingIndex(double offset, List<double> extents) {
    double start = 0;
    for (var i = 0; i < extents.length; i++) {
      final double next = start + extents[i];
      if (offset < next) {
        return i;
      }
      start = next;
    }
    return extents.length - 1;
  }

  /// Last item that has started before [end].
  int _trailingIndex(double end, List<double> extents) {
    double start = 0;
    var trailing = 0;
    for (var i = 0; i < extents.length; i++) {
      if (start >= end) {
        break;
      }
      trailing = i;
      start += extents[i];
    }
    return trailing;
  }

  @override
  double? get itemExtent => null;

  @override
  ItemExtentBuilder? get itemExtentBuilder => _buildItemExtent;
}
