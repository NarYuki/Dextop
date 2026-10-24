import 'package:flutter/foundation.dart';

/// Snapshot of carousel scroll layout for the carousel `onChange` callback.
@immutable
class M3ECarouselChangeDetails {
  /// Creates carousel change details.
  const M3ECarouselChangeDetails({
    required this.leadingIndex,
    required this.focalIndex,
    required this.itemCount,
  });

  /// First visible layout slot (from scroll offset).
  final int leadingIndex;

  /// Largest (hero) item — equals [leadingIndex] for uncontained carousels.
  final int focalIndex;

  /// Total child count in the carousel.
  final int itemCount;

  /// Whether [index] is the current focal (largest) item.
  bool isFocal(int index) => index == focalIndex;

  @override
  bool operator ==(Object other) {
    return other is M3ECarouselChangeDetails &&
        other.leadingIndex == leadingIndex &&
        other.focalIndex == focalIndex &&
        other.itemCount == itemCount;
  }

  @override
  int get hashCode => Object.hash(leadingIndex, focalIndex, itemCount);

  @override
  String toString() {
    return 'M3ECarouselChangeDetails('
        'leadingIndex: $leadingIndex, '
        'focalIndex: $focalIndex, '
        'itemCount: $itemCount)';
  }
}
