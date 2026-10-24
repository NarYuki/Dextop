part of 'm3e_carousel_view.dart';

mixin _M3EWeightedCarouselLayoutMixin on RenderSliverFixedExtentBoxAdaptor {
  bool get consumeMaxWeight;
  set consumeMaxWeight(bool value);

  double get shrinkExtent;
  set shrinkExtent(double value);

  List<int> get weights;
  set weights(List<int> value);

  bool get infinite;
  set infinite(bool value);

  // This is to implement the itemExtentBuilder callback to return each item extent
  // while scrolling.
  //
  // The given `index` is compared with `_firstVisibleItemIndex` to know how
  // many items are placed before the current one in the view.
  double _buildItemExtent(
    int index,
    // Signature matches ItemExtentBuilder; extents come from scroll state.
    SliverLayoutDimensions currentLayoutDimensions,
  ) {
    // If constraints.viewportMainAxisExtent is 0, firstChildExtent will be 0 and cause division error.
    if (constraints.viewportMainAxisExtent == 0) {
      return 0;
    }
    if (index == _firstVisibleItemIndex) {
      return math.max(_distanceToLeadingEdge, effectiveShrinkExtent);
    }
    if (index > _firstVisibleItemIndex &&
        index - _firstVisibleItemIndex + 1 <= weights.length) {
      return _extentWithinWeights(index);
    }
    if (index > _firstVisibleItemIndex &&
        index - _firstVisibleItemIndex + 1 > weights.length) {
      return _extentBeyondWeights(index, currentLayoutDimensions);
    }
    return math.max(minChildExtent, effectiveShrinkExtent);
  }

  double _extentWithinWeights(int index) {
    assert(
      index - _firstVisibleItemIndex < weights.length,
      'carousel layout invariant',
    );
    final int currIndexOnWeightList = index - _firstVisibleItemIndex;
    final int currWeight = weights[currIndexOnWeightList];
    final double progress = _firstVisibleItemOffscreenExtent / firstChildExtent;
    final int prevWeight = weights[currIndexOnWeightList - 1];
    final double finalIncrease = (prevWeight - currWeight) / weights.max;
    return extentUnit * currWeight + finalIncrease * progress * maxChildExtent;
  }

  double _extentBeyondWeights(
    int index,
    SliverLayoutDimensions currentLayoutDimensions,
  ) {
    double visibleItemsTotalExtent = _distanceToLeadingEdge;
    for (int i = _firstVisibleItemIndex + 1; i < index; i++) {
      visibleItemsTotalExtent += _buildItemExtent(i, currentLayoutDimensions);
    }
    return math.max(
      constraints.remainingPaintExtent - visibleItemsTotalExtent,
      effectiveShrinkExtent,
    );
  }

  // To ge the extent unit based on the viewport extent and the sum of weights.
  double get extentUnit =>
      constraints.viewportMainAxisExtent /
      (weights.reduce((int total, int extent) => total + extent));

  double get firstChildExtent => weights.first * extentUnit;

  double get maxChildExtent => weights.max * extentUnit;

  double get minChildExtent => weights.min * extentUnit;

  // The shrink extent for first and last visible items should be no larger
  // than [minChildExtent] to ensure a smooth transition.
  double get effectiveShrinkExtent =>
      clampDouble(shrinkExtent, 0, minChildExtent);

  // The index of the first visible item. The returned value can be negative when
  // the leading items with smaller weights need to be fully expanded. For example,
  // assuming a weights [1, 7, 1], when item 0 is expanding to the maximum size
  // (with weight 7), we leave some space before item 0 assuming there is another
  // item -1 as the first visible item.
  int get _firstVisibleItemIndex {
    // If constraints.viewportMainAxisExtent is 0, firstChildExtent will be 0 and cause division error.
    if (constraints.viewportMainAxisExtent == 0.0) {
      return 0;
    }
    var smallerWeightCount = 0;
    for (final int weight in weights) {
      if (weight == weights.max) {
        break;
      }
      smallerWeightCount += 1;
    }
    int index;

    final double actual = constraints.scrollOffset / firstChildExtent;
    final int round = (constraints.scrollOffset / firstChildExtent).round();
    if ((actual - round).abs() < precisionErrorTolerance) {
      index = round;
    } else {
      index = actual.floor();
    }
    return consumeMaxWeight ? index - smallerWeightCount : index;
  }

  // This value indicates the scrolling progress of items following the first
  // item. It informs them how much the first item has moved off-screen,
  // enabling them to adjust their sizes (grow or shrink) accordingly.
  double get _firstVisibleItemOffscreenExtent {
    // If constraints.viewportMainAxisExtent is 0, firstChildExtent will be 0 and cause division error.
    if (constraints.viewportMainAxisExtent == 0.0) {
      return 0;
    }
    int index;
    final double actual = constraints.scrollOffset / firstChildExtent;
    final int round = (constraints.scrollOffset / firstChildExtent).round();
    if ((actual - round).abs() < precisionErrorTolerance) {
      index = round;
    } else {
      index = actual.floor();
    }
    return constraints.scrollOffset - index * firstChildExtent;
  }

  // Given the off-screen extent for the first visible item, we can know the
  // on-screen extent for the first visible item.
  double get _distanceToLeadingEdge =>
      firstChildExtent - _firstVisibleItemOffscreenExtent;

  // Given an index, this method returns the layout offset for the item. The `index`
  // is firstly compared to `_firstVisibleItemIndex` and compute the distance
  // between them, then compute all the current extents for items that are located
  // in front.
  @override
  double indexToLayoutOffset(
    @Deprecated(
      'The itemExtent is already available within the scope of this function. '
      'This feature was deprecated after v3.20.0-7.0.pre.',
    )
    double itemExtent,
    int index,
  ) {
    if (index == _firstVisibleItemIndex) {
      if (_distanceToLeadingEdge <= effectiveShrinkExtent) {
        return constraints.scrollOffset -
            effectiveShrinkExtent +
            _distanceToLeadingEdge;
      }
      return constraints.scrollOffset;
    }
    double visibleItemsTotalExtent = _distanceToLeadingEdge;
    for (int i = _firstVisibleItemIndex + 1; i < index; i++) {
      visibleItemsTotalExtent += _buildItemExtent(i, layoutDimensions);
    }
    return constraints.scrollOffset + visibleItemsTotalExtent;
  }

  @override
  int getMinChildIndexForScrollOffset(
    double scrollOffset,
    @Deprecated(
      'The itemExtent is already available within the scope of this function. '
      'This feature was deprecated after v3.20.0-7.0.pre.',
    )
    double itemExtent,
  ) {
    return math.max(_firstVisibleItemIndex, 0);
  }

  @override
  int getMaxChildIndexForScrollOffset(
    double scrollOffset,
    @Deprecated(
      'The itemExtent is already available within the scope of this function. '
      'This feature was deprecated after v3.20.0-7.0.pre.',
    )
    double itemExtent,
  ) {
    final int? childCount = childManager.estimatedChildCount;
    if (infinite && childCount == null) {
      return _maxIndexForInfiniteViewport();
    }
    if (childCount != null) {
      return _maxIndexForFiniteChildren(childCount);
    }
    return 0;
  }

  int _maxIndexForInfiniteViewport() {
    double visibleItemsTotalExtent = _distanceToLeadingEdge;
    int index = _firstVisibleItemIndex + 1;
    // Calculate upper bound based on viewport extent and minimum possible item extent.
    // In worst case, all items would be at minimum extent i.e. minChildExtent.
    final double safeMinExtent = math.max(minChildExtent, 1);
    final int estimatedUpperBound =
        _firstVisibleItemIndex +
        (constraints.viewportMainAxisExtent / safeMinExtent).ceil();
    while (visibleItemsTotalExtent < constraints.viewportMainAxisExtent &&
        index < estimatedUpperBound) {
      visibleItemsTotalExtent += _buildItemExtent(index, layoutDimensions);
      if (visibleItemsTotalExtent >= constraints.viewportMainAxisExtent) {
        return index;
      }
      index++;
    }
    return index;
  }

  int _maxIndexForFiniteChildren(int childCount) {
    double visibleItemsTotalExtent = _distanceToLeadingEdge;
    for (int i = _firstVisibleItemIndex + 1; i < childCount; i++) {
      visibleItemsTotalExtent += _buildItemExtent(i, layoutDimensions);
      if (visibleItemsTotalExtent >= constraints.viewportMainAxisExtent) {
        return i;
      }
    }
    return childCount;
  }

  @override
  double computeMaxScrollOffset(
    SliverConstraints constraints,
    @Deprecated(
      'The itemExtent is already available within the scope of this function. '
      'This feature was deprecated after v3.20.0-7.0.pre.',
    )
    double itemExtent,
  ) {
    if (infinite) {
      return double.infinity;
    }
    return childManager.childCount * maxChildExtent;
  }

  BoxConstraints _getChildConstraints(int index) {
    final double extent = itemExtentBuilder!(index, layoutDimensions)!;
    return constraints.asBoxConstraints(minExtent: extent, maxExtent: extent);
  }

  // This method is mostly the same as its parent class [RenderSliverFixedExtentList].
  // The difference is when we allow some space before the leading items or after
  // the trailing items with smaller weights, we leave extra scroll offset.
  // TODO(quncCccccc): add the calculation for the extra scroll offset on the super class to simplify the implementation here.
  @override
  void performLayout() {
    assert(
      (itemExtent != null && itemExtentBuilder == null) ||
          (itemExtent == null && itemExtentBuilder != null),
      'Exactly one of itemExtent or itemExtentBuilder must be non-null',
    );
    assert(
      itemExtentBuilder != null || (itemExtent!.isFinite && itemExtent! >= 0),
      'itemExtent must be finite and non-negative when itemExtentBuilder is null',
    );

    final SliverConstraints constraints = this.constraints;
    childManager
      ..didStartLayout()
      ..setDidUnderflow(false);

    final double scrollOffset =
        constraints.scrollOffset + constraints.cacheOrigin;
    assert(scrollOffset >= 0.0, 'scrollOffset must be non-negative');
    final double remainingExtent = constraints.remainingCacheExtent;
    assert(remainingExtent >= 0.0, 'remainingCacheExtent must be non-negative');
    // TODO(Piinks): Clean up when deprecation expires.
    const double deprecatedExtraItemExtent = -1;
    final int firstIndex = getMinChildIndexForScrollOffset(
      scrollOffset,
      deprecatedExtraItemExtent,
    );
    final double targetEndScrollOffset = scrollOffset + remainingExtent;
    final int? targetLastIndex = targetEndScrollOffset.isFinite
        ? getMaxChildIndexForScrollOffset(
            targetEndScrollOffset,
            deprecatedExtraItemExtent,
          )
        : null;

    _collectLayoutGarbage(
      firstIndex: firstIndex,
      targetLastIndex: targetLastIndex,
    );
    if (!_ensureInitialChild(
      firstIndex: firstIndex,
      constraints: constraints,
      deprecatedExtraItemExtent: deprecatedExtraItemExtent,
    )) {
      return;
    }

    final RenderBox? trailingSeed = _layoutLeadingOrSeed(
      firstIndex: firstIndex,
      deprecatedExtraItemExtent: deprecatedExtraItemExtent,
    );
    if (trailingSeed == null) {
      return;
    }
    final double extraLayoutOffset = _trailingExtraLayoutOffset();
    final double estimatedFromChildren = _layoutTrailingChildren(
      trailingChildWithLayout: trailingSeed,
      targetLastIndex: targetLastIndex,
      extraLayoutOffset: extraLayoutOffset,
      deprecatedExtraItemExtent: deprecatedExtraItemExtent,
    );
    _finalizeWeightedGeometry(
      constraints: constraints,
      firstIndex: firstIndex,
      targetLastIndex: targetLastIndex,
      extraLayoutOffset: extraLayoutOffset,
      estimatedMaxScrollOffset: estimatedFromChildren,
      deprecatedExtraItemExtent: deprecatedExtraItemExtent,
    );
  }

  void _collectLayoutGarbage({
    required int firstIndex,
    required int? targetLastIndex,
  }) {
    if (firstChild != null) {
      final int leadingGarbage = calculateLeadingGarbage(
        firstIndex: firstIndex,
      );
      final int trailingGarbage = targetLastIndex != null
          ? calculateTrailingGarbage(lastIndex: targetLastIndex)
          : 0;
      collectGarbage(leadingGarbage, trailingGarbage);
      return;
    }
    collectGarbage(0, 0);
  }

  bool _ensureInitialChild({
    required int firstIndex,
    required SliverConstraints constraints,
    required double deprecatedExtraItemExtent,
  }) {
    if (firstChild != null) {
      return true;
    }
    final double layoutOffset = indexToLayoutOffset(
      deprecatedExtraItemExtent,
      firstIndex,
    );
    if (addInitialChild(index: firstIndex, layoutOffset: layoutOffset)) {
      return true;
    }
    // There are either no children, or we are past the end of all our children.
    final double max = firstIndex <= 0
        ? 0.0
        : computeMaxScrollOffset(constraints, deprecatedExtraItemExtent);
    geometry = SliverGeometry(scrollExtent: max, maxPaintExtent: max);
    childManager.didFinishLayout();
    return false;
  }

  /// Returns the trailing seed after leading inserts, or `null` when a scroll
  /// offset correction was applied and layout must abort.
  RenderBox? _layoutLeadingOrSeed({
    required int firstIndex,
    required double deprecatedExtraItemExtent,
  }) {
    RenderBox? trailingChildWithLayout;
    for (int index = indexOf(firstChild!) - 1; index >= firstIndex; --index) {
      final RenderBox? child = insertAndLayoutLeadingChild(
        _getChildConstraints(index),
      );
      if (child == null) {
        // Items before the previously first child are no longer present.
        // Reset the scroll offset to offset all items prior and up to the
        // missing item. Let parent re-layout everything.
        geometry = SliverGeometry(
          scrollOffsetCorrection: indexToLayoutOffset(
            deprecatedExtraItemExtent,
            index,
          ),
        );
        return null;
      }
      final childParentData =
          child.parentData! as SliverMultiBoxAdaptorParentData
            ..layoutOffset = indexToLayoutOffset(
              deprecatedExtraItemExtent,
              index,
            );
      assert(childParentData.index == index, 'carousel invariant');
      trailingChildWithLayout ??= child;
    }
    if (trailingChildWithLayout != null) {
      return trailingChildWithLayout;
    }
    return _layoutFirstChildSeed(firstIndex, deprecatedExtraItemExtent);
  }

  RenderBox _layoutFirstChildSeed(
    int firstIndex,
    double deprecatedExtraItemExtent,
  ) {
    firstChild!.layout(_getChildConstraints(indexOf(firstChild!)));
    (firstChild!.parentData! as SliverMultiBoxAdaptorParentData).layoutOffset =
        indexToLayoutOffset(deprecatedExtraItemExtent, firstIndex);
    return firstChild!;
  }

  double _trailingExtraLayoutOffset() {
    if (!consumeMaxWeight) {
      return 0;
    }
    double extraLayoutOffset = 0;
    for (int i = weights.length - 1; i >= 0; i--) {
      if (weights[i] == weights.max) {
        break;
      }
      extraLayoutOffset += weights[i] * extentUnit;
    }
    return extraLayoutOffset;
  }

  double _layoutTrailingChildren({
    required RenderBox trailingChildWithLayout,
    required int? targetLastIndex,
    required double extraLayoutOffset,
    required double deprecatedExtraItemExtent,
  }) {
    var trailing = trailingChildWithLayout;
    var estimatedMaxScrollOffset = double.infinity;
    for (
      int index = indexOf(trailing) + 1;
      targetLastIndex == null || index <= targetLastIndex;
      ++index
    ) {
      RenderBox? child = childAfter(trailing);
      if (child == null || indexOf(child) != index) {
        child = insertAndLayoutChild(
          _getChildConstraints(index),
          after: trailing,
        );
        if (child == null) {
          // We have run out of children.
          estimatedMaxScrollOffset =
              indexToLayoutOffset(deprecatedExtraItemExtent, index) +
              extraLayoutOffset;
          break;
        }
      } else {
        child.layout(_getChildConstraints(index));
      }
      trailing = child;
      final childParentData =
          child.parentData! as SliverMultiBoxAdaptorParentData;
      assert(childParentData.index == index, 'carousel invariant');
      childParentData.layoutOffset = indexToLayoutOffset(
        deprecatedExtraItemExtent,
        childParentData.index!,
      );
    }
    return estimatedMaxScrollOffset;
  }

  double _trailingScrollOffset({
    required int lastIndex,
    required double extraLayoutOffset,
    required double deprecatedExtraItemExtent,
  }) {
    if (!infinite && lastIndex + 1 == childManager.childCount) {
      var trailingScrollOffset = indexToLayoutOffset(
        deprecatedExtraItemExtent,
        lastIndex,
      );
      trailingScrollOffset += math.max(
        weights.last * extentUnit,
        _buildItemExtent(lastIndex, layoutDimensions),
      );
      return trailingScrollOffset + extraLayoutOffset;
    }
    return indexToLayoutOffset(deprecatedExtraItemExtent, lastIndex + 1);
  }

  void _finalizeWeightedGeometry({
    required SliverConstraints constraints,
    required int firstIndex,
    required int? targetLastIndex,
    required double extraLayoutOffset,
    required double estimatedMaxScrollOffset,
    required double deprecatedExtraItemExtent,
  }) {
    final int lastIndex = indexOf(lastChild!);
    final double leadingScrollOffset = indexToLayoutOffset(
      deprecatedExtraItemExtent,
      firstIndex,
    );
    final double trailingScrollOffset = _trailingScrollOffset(
      lastIndex: lastIndex,
      extraLayoutOffset: extraLayoutOffset,
      deprecatedExtraItemExtent: deprecatedExtraItemExtent,
    );

    assert(debugAssertChildListIsNonEmptyAndContiguous(), 'carousel invariant');
    assert(indexOf(firstChild!) == firstIndex, 'carousel invariant');
    assert(
      targetLastIndex == null || lastIndex <= targetLastIndex,
      'carousel invariant',
    );

    final double estimated = math.min(
      estimatedMaxScrollOffset,
      estimateMaxScrollOffset(
        constraints,
        firstIndex: firstIndex,
        lastIndex: lastIndex,
        leadingScrollOffset: leadingScrollOffset,
        trailingScrollOffset: trailingScrollOffset,
      ),
    );
    final double paintExtent = calculatePaintOffset(
      constraints,
      from: consumeMaxWeight ? 0 : leadingScrollOffset,
      to: trailingScrollOffset,
    );
    final double cacheExtent = calculateCacheOffset(
      constraints,
      from: consumeMaxWeight ? 0 : leadingScrollOffset,
      to: trailingScrollOffset,
    );
    final double targetEndScrollOffsetForPaint =
        constraints.scrollOffset + constraints.remainingPaintExtent;
    final int? targetLastIndexForPaint = targetEndScrollOffsetForPaint.isFinite
        ? getMaxChildIndexForScrollOffset(
            targetEndScrollOffsetForPaint,
            deprecatedExtraItemExtent,
          )
        : null;

    geometry = SliverGeometry(
      scrollExtent: estimated,
      paintExtent: paintExtent,
      cacheExtent: cacheExtent,
      maxPaintExtent: estimated,
      // Conservative to avoid flickering away the clip during scroll.
      hasVisualOverflow:
          (targetLastIndexForPaint != null &&
              lastIndex >= targetLastIndexForPaint) ||
          constraints.scrollOffset > 0.0,
    );

    // We may have started the layout while scrolled to the end, which would not
    // expose a new child.
    if (estimated == trailingScrollOffset) {
      childManager.setDidUnderflow(true);
    }
    childManager.didFinishLayout();
  }

  @override
  double? get itemExtent => null;

  /// The main-axis extent builder of each item.
  ///
  /// If this is non-null, the [itemExtent] must be null.
  /// If this is null, the [itemExtent] must be non-null.
  @override
  ItemExtentBuilder? get itemExtentBuilder => _buildItemExtent;
}
