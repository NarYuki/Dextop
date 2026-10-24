part of 'm3e_carousel_wrapper.dart';

/// Sizing, padding, and neighbor-visibility calculations for
/// [_M3ECarouselWrapperState].
extension _M3ECarouselWrapperGeometry on _M3ECarouselWrapperState {
  /// Full-screen pages keep one size, so the image shifts as the page moves.
  bool get _fullscreenParallax =>
      !widget.reducedMotion &&
      !widget.scaleItems &&
      widget.flexWeights == null &&
      widget.itemExtents == null &&
      widget.itemExtent != null;

  /// Image shift for a full-screen page, in the scroll direction.
  ///
  /// The image is taller or wider than the page and moves slower than the
  /// page, matching the centered clip on the weighted layouts.
  double _fullscreenParallaxShift(
    int index,
    double restMain,
    ScrollPosition position,
  ) {
    final double viewport = position.hasViewportDimension
        ? position.viewportDimension
        : restMain;
    if (viewport == 0) {
      return 0;
    }
    final double extent = widget.itemExtent ?? restMain;
    final double origin = index * extent + widget.leadingInset;
    final double delta =
        (origin + restMain / 2) - (position.pixels + viewport / 2);
    final double extra = restMain * 0.5;
    final double t = (delta / viewport).clamp(-1.0, 1.0);
    return -t * extra / 2;
  }

  /// Full card size on the scroll axis, so a peek clips a stable image.
  double _pinnedContentMain(int index, double restMain) {
    final double paddingMain = _mainAxisPadding(_resolvedPadding);
    if (widget.itemExtents != null && index < widget.itemExtents!.length) {
      return math.max(widget.itemExtents![index] - paddingMain, restMain);
    }
    if (widget.itemExtent != null) {
      return math.max(widget.itemExtent! - paddingMain, restMain);
    }
    return math.max(restMain, 0);
  }

  void _registerItemBox(int index, RenderBox box) {
    _itemBoxes[index] = box;
  }

  void _unregisterItemBox(int index, RenderBox box) {
    if (_itemBoxes[index] == box) {
      _itemBoxes.remove(index);
    }
  }

  void _unregisterViewportBox(RenderBox box) {
    if (_viewportBox == box) {
      _viewportBox = null;
    }
  }

  double _fallbackExtent() => widget.itemExtent ?? 100.0;

  bool get _vertical => widget.scrollDirection == Axis.vertical;

  EdgeInsets get _resolvedPadding {
    final EdgeInsetsGeometry padding =
        widget.padding ?? const EdgeInsets.all(4);
    return padding.resolve(Directionality.of(context));
  }

  double _mainAxisPadding(EdgeInsets padding) =>
      _vertical ? padding.vertical : padding.horizontal;

  /// Inner main-axis extent for the largest resting item slot.
  ///
  /// Content is laid out against this (plus pulse budget) so scroll size
  /// changes only move the clip window, and expanded pulse edges stay filled.
  double _stableInnerContentExtent(double viewportMain) {
    final double paddingMain = _mainAxisPadding(_resolvedPadding);
    if (widget.itemExtent != null) {
      return math.max(widget.itemExtent! - paddingMain, 0);
    }
    final List<int>? weights = widget.flexWeights;
    if (weights != null && weights.isNotEmpty && viewportMain > 0) {
      final int total = weights.fold<int>(0, (int a, int b) => a + b);
      if (total > 0) {
        final int maxWeight = weights.reduce(math.max);
        return math.max(viewportMain * maxWeight / total - paddingMain, 0);
      }
    }
    return _fallbackExtent();
  }

  (bool expandLeading, bool expandTrailing) _expandSidesForActiveIndex(
    int index,
  ) {
    final leadingVisible = _leftVisibleNeighborIndex != null;
    final trailingVisible = _rightVisibleNeighborIndex != null;
    final noVisibleNeighbors = !leadingVisible && !trailingVisible;
    final lastIndex = widget.children.length - 1;

    final expandLeading =
        leadingVisible ||
        (noVisibleNeighbors && index == lastIndex) ||
        (noVisibleNeighbors && index > 0 && index < lastIndex);
    final expandTrailing =
        trailingVisible ||
        (noVisibleNeighbors && index == 0) ||
        (noVisibleNeighbors && index > 0 && index < lastIndex);
    return (expandLeading, expandTrailing);
  }

  bool _isNeighborViewportVisible(int index, RenderBox carouselBox) {
    if (index < 0 || index >= widget.children.length) {
      return false;
    }

    final box = _itemBoxes[index];
    if (box == null || !box.hasSize || !box.attached) {
      return false;
    }

    final Offset carouselOrigin = carouselBox.localToGlobal(Offset.zero);
    final Offset itemOrigin = box.localToGlobal(Offset.zero);

    if (_vertical) {
      if (box.size.height <= 1.0) {
        return false;
      }
      final double carouselTop = carouselOrigin.dy;
      final double carouselBottom = carouselTop + carouselBox.size.height;
      final double itemTop = itemOrigin.dy;
      final double itemBottom = itemTop + box.size.height;
      return itemBottom > (carouselTop + 1.0) &&
          itemTop < (carouselBottom - 1.0);
    }

    if (box.size.width <= 1.0) {
      return false;
    }
    final double carouselLeft = carouselOrigin.dx;
    final double carouselRight = carouselLeft + carouselBox.size.width;
    final double itemLeft = itemOrigin.dx;
    final double itemRight = itemLeft + box.size.width;
    return itemRight > (carouselLeft + 1.0) && itemLeft < (carouselRight - 1.0);
  }

  void _snapshotVisibleNeighbors(int index, RenderBox? parentBox) {
    if (parentBox != null) {
      _leftVisibleNeighborIndex =
          _isNeighborViewportVisible(index - 1, parentBox) ? index - 1 : null;
      _rightVisibleNeighborIndex =
          _isNeighborViewportVisible(index + 1, parentBox) ? index + 1 : null;
      return;
    }

    _leftVisibleNeighborIndex = index > 0 ? index - 1 : null;
    _rightVisibleNeighborIndex = index < widget.children.length - 1
        ? index + 1
        : null;
  }
}
