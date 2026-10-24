part of '../m3e_carousel.dart';

/// Programmatic and gesture-driven step navigation for [_M3ECarouselState].
extension _M3ECarouselNavigation on _M3ECarouselState {
  Future<void> scrollFrame(int direction) async {
    if (!_controller.hasClients) {
      return;
    }
    final List<double>? varied = _aspectExtents;
    if (varied != null) {
      await _scrollVaried(direction, varied);
      return;
    }
    final M3ECarouselTheme theme = M3ETheme.of(context).carouselTheme;
    final bool weighted = layoutWeight.isNotEmpty;
    final step = M3ECarouselScrollHelper.nextStep(
      type: weighted ? widget.type : M3ECarouselType.uncontained,
      heroAlignment: widget.heroAlignment,
      isExtended: _extended(theme),
      uncontainedItemExtent: _stepExtent,
      leadingGap: _leadingGap(theme),
      layoutWeight: layoutWeight,
      mainExtent: _trackMain,
      childrenLength: widget.children.length,
      itemScrolled: itemScrolled,
      prevScrollPosition: _controller.position.pixels,
      direction: direction,
    );
    if (step == null) {
      return;
    }
    itemScrolled = step.itemScrolled;
    await _controller.animateTo(
      step.nextScrollPosition,
      duration: Duration(milliseconds: widget.scrollAnimationDuration),
      curve: Curves.ease,
    );
  }

  Future<void> _scrollVaried(int direction, List<double> extents) async {
    if (extents.isEmpty) {
      return;
    }
    if (direction == 0) {
      if (itemScrolled <= 0) {
        return;
      }
      itemScrolled -= 1;
    } else {
      if (itemScrolled >= extents.length - 1) {
        return;
      }
      itemScrolled += 1;
    }
    double offset = 0;
    for (var i = 0; i < itemScrolled; i++) {
      offset += extents[i];
    }
    await _controller.animateTo(
      offset,
      duration: Duration(milliseconds: widget.scrollAnimationDuration),
      curve: Curves.ease,
    );
  }

  Future<void> _moveTo(
    int index, {
    required bool jump,
    required Duration duration,
    required Curve curve,
  }) async {
    if (!mounted || !_controller.hasClients || widget.children.isEmpty) {
      return;
    }
    final int target = _clampIndex(index);
    itemScrolled = target;
    final double offset = _offsetFor(target);
    if (jump) {
      _controller.jumpTo(offset);
      return;
    }
    await _controller.animateTo(offset, duration: duration, curve: curve);
  }

  int _clampIndex(int index) {
    final List<double>? varied = _aspectExtents;
    if (varied != null && varied.isNotEmpty) {
      return index.clamp(0, varied.length - 1);
    }
    final int limit = M3ECarouselScrollHelper.maxIndex(
      type: layoutWeight.isEmpty ? M3ECarouselType.uncontained : widget.type,
      heroAlignment: widget.heroAlignment,
      isExtended: _extended(M3ETheme.of(context).carouselTheme),
      childrenLength: widget.children.length,
    );
    if (limit < 0) {
      return 0;
    }
    return index.clamp(0, limit);
  }

  double _offsetFor(int index) {
    final List<double>? varied = _aspectExtents;
    if (varied != null && varied.isNotEmpty) {
      double offset = 0;
      final int end = math.min(index, varied.length);
      for (var i = 0; i < end; i++) {
        offset += varied[i];
      }
      return offset;
    }
    final M3ECarouselTheme theme = M3ETheme.of(context).carouselTheme;
    return M3ECarouselScrollHelper.offsetForIndex(
      type: layoutWeight.isEmpty ? M3ECarouselType.uncontained : widget.type,
      heroAlignment: widget.heroAlignment,
      isExtended: _extended(theme),
      uncontainedItemExtent: _stepExtent,
      leadingGap: _leadingGap(theme),
      layoutWeight: layoutWeight,
      mainExtent: _trackMain,
      childrenLength: widget.children.length,
      index: index,
    );
  }

  void _bindController(M3ECarouselController controller) {
    controller.attachActions(
      currentItem: () => itemScrolled,
      step: scrollFrame,
      moveTo: _moveTo,
      showAll: () {
        if (!mounted) {
          return;
        }
        _openList(M3ETheme.of(context).carouselTheme);
      },
    );
  }

  void onDragEnd(DragEndDetails details) {
    final double? velocity = details.primaryVelocity;
    if (velocity == null) {
      return;
    }
    final int threshold = kIsWeb
        ? 0
        : widget.singleSwipeGestureSensitivityRange;
    if (velocity > threshold) {
      scrollFrame(0);
    } else if (velocity < -threshold) {
      scrollFrame(1);
    }
  }

  Widget _gestureLayer(Widget child) {
    if (_freeScroll()) {
      return child;
    }
    if (_horizontal) {
      return GestureDetector(onHorizontalDragEnd: onDragEnd, child: child);
    }
    return GestureDetector(onVerticalDragEnd: onDragEnd, child: child);
  }
}
