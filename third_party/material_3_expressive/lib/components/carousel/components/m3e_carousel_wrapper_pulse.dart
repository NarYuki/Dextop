part of 'm3e_carousel_wrapper.dart';

extension _M3ECarouselWrapperPulse on _M3ECarouselWrapperState {
  /// Clip-window rect in resting-item coordinates for the current pulse.
  ({double left, double top, double width, double height}) _pulseFrameRect({
    required int index,
    required bool isActive,
    required bool isLeftNeighbor,
    required bool isRightNeighbor,
    required double edgeDelta,
    required double restWidth,
    required double restHeight,
  }) {
    if (_activeIndex == null || edgeDelta <= 0) {
      return _restPulseFrame(restWidth, restHeight);
    }
    if (isActive) {
      return _activePulseFrame(
        index: index,
        edgeDelta: edgeDelta,
        restWidth: restWidth,
        restHeight: restHeight,
      );
    }
    if (isLeftNeighbor) {
      return _leftNeighborPulseFrame(edgeDelta, restWidth, restHeight);
    }
    if (isRightNeighbor) {
      return _rightNeighborPulseFrame(edgeDelta, restWidth, restHeight);
    }
    return _restPulseFrame(restWidth, restHeight);
  }

  ({double left, double top, double width, double height}) _restPulseFrame(
    double restWidth,
    double restHeight,
  ) => (left: 0, top: 0, width: restWidth, height: restHeight);

  ({double left, double top, double width, double height}) _activePulseFrame({
    required int index,
    required double edgeDelta,
    required double restWidth,
    required double restHeight,
  }) {
    final (expandLeading, expandTrailing) = _expandSidesForActiveIndex(index);
    final double leading = expandLeading ? edgeDelta : 0;
    final double trailing = expandTrailing ? edgeDelta : 0;
    if (_vertical) {
      return (
        left: 0,
        top: -leading,
        width: restWidth,
        height: restHeight + leading + trailing,
      );
    }
    return (
      left: -leading,
      top: 0,
      width: restWidth + leading + trailing,
      height: restHeight,
    );
  }

  ({double left, double top, double width, double height})
  _leftNeighborPulseFrame(
    double edgeDelta,
    double restWidth,
    double restHeight,
  ) {
    // Squish the trailing edge shared with the active item.
    if (_vertical) {
      return (
        left: 0,
        top: 0,
        width: restWidth,
        height: math.max(restHeight - edgeDelta, 1),
      );
    }
    return (
      left: 0,
      top: 0,
      width: math.max(restWidth - edgeDelta, 1),
      height: restHeight,
    );
  }

  ({double left, double top, double width, double height})
  _rightNeighborPulseFrame(
    double edgeDelta,
    double restWidth,
    double restHeight,
  ) {
    // Squish the leading edge shared with the active item.
    if (_vertical) {
      return (
        left: 0,
        top: edgeDelta,
        width: restWidth,
        height: math.max(restHeight - edgeDelta, 1),
      );
    }
    return (
      left: edgeDelta,
      top: 0,
      width: math.max(restWidth - edgeDelta, 1),
      height: restHeight,
    );
  }
}
