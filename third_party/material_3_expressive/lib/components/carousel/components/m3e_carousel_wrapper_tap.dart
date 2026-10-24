part of 'm3e_carousel_wrapper.dart';

/// Tap handling and the container-transform launch for
/// [_M3ECarouselWrapperState].
extension _M3ECarouselWrapperTap on _M3ECarouselWrapperState {
  Future<void> _handleTap(int index) async {
    if (widget.onTap != null) {
      M3EHaptics.trigger(widget.haptic);
    }
    widget.onTap?.call(index);
    if (_pulseController.isAnimating) {
      return;
    }

    setState(() {
      _activeIndex = index;
      _snapshotVisibleNeighbors(index, _viewportBox);
    });

    await _pulseController.forward();
    await _pulseController.reverse();

    if (mounted) {
      setState(() {
        _activeIndex = null;
        _leftVisibleNeighborIndex = null;
        _rightVisibleNeighborIndex = null;
      });
    }
    final M3ECarouselItem item = _itemAt(index);
    final Widget? destination = item.transform;
    if (destination != null && item.enabled && mounted) {
      _openItemTransform(index, destination);
    }
  }

  void _openItemTransform(int index, Widget destination) {
    RenderBox? box = _itemBoxes[index];
    if (box == null || !box.hasSize || !box.attached) {
      final RenderObject? object = _itemFocus[index].context
          ?.findRenderObject();
      if (object is RenderBox && object.hasSize && object.attached) {
        box = object;
      }
    }
    if (box == null) {
      return;
    }
    final Rect origin = box.localToGlobal(Offset.zero) & box.size;
    final theme = M3ETheme.of(context);
    final radius = widget.shape is RoundedRectangleBorder
        ? (widget.shape! as RoundedRectangleBorder).borderRadius as BorderRadius
        : BorderRadius.zero;
    M3ECardContainerTransform.show<void>(
      context: context,
      origin: origin,
      originRadius: radius.topLeft.x,
      originColor: theme.colorScheme.surface,
      builder: (BuildContext context) => destination,
    );
  }
}
