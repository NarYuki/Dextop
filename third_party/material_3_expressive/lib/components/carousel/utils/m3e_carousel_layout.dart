import 'dart:math' as math;

/// Turns a weight pattern into slot sizes that follow the carousel spec.
abstract final class M3ECarouselLayout {
  const M3ECarouselLayout._();

  /// Clamps small slots to [smallMin]–[smallMax] and large slots to [largeMax].
  ///
  /// The leftover width is shared by the other slots so the row still fills
  /// [viewport].
  static List<int> clampWeights({
    required List<int> pattern,
    required double viewport,
    required double smallMin,
    required double smallMax,
    required double largeMax,
  }) {
    if (pattern.isEmpty || viewport <= 0) {
      return pattern;
    }
    final int sum = pattern.fold<int>(0, (int a, int b) => a + b);
    if (sum <= 0) {
      return pattern;
    }
    final int minWeight = pattern.reduce(math.min);
    final int maxWeight = pattern.reduce(math.max);
    final double unit = viewport / sum;
    final slots = <double>[for (final int weight in pattern) weight * unit];
    final small = <bool>[for (final int weight in pattern) weight == minWeight];
    final large = <bool>[
      for (final int weight in pattern)
        weight == maxWeight && weight != minWeight,
    ];

    _clampExtremeSlots(
      slots: slots,
      small: small,
      large: large,
      smallMin: smallMin,
      smallMax: smallMax,
      largeMax: largeMax,
    );

    final List<int> targets = _receiverIndices(small: small, large: large);
    _shareRemainingWidth(
      slots: slots,
      targets: targets,
      viewport: viewport,
      large: large,
      largeMax: largeMax,
    );

    return _roundSlots(slots: slots, targets: targets, viewport: viewport);
  }

  /// Clamps small slots to [smallMin]–[smallMax] and caps large slots at
  /// [largeMax], in place.
  static void _clampExtremeSlots({
    required List<double> slots,
    required List<bool> small,
    required List<bool> large,
    required double smallMin,
    required double smallMax,
    required double largeMax,
  }) {
    for (var i = 0; i < slots.length; i++) {
      if (small[i]) {
        slots[i] = slots[i].clamp(smallMin, smallMax);
      } else if (large[i]) {
        slots[i] = math.min(slots[i], largeMax);
      }
    }
  }

  /// Indices that absorb leftover viewport width: the mid slots, or the
  /// large slots when there are no mid slots.
  static List<int> _receiverIndices({
    required List<bool> small,
    required List<bool> large,
  }) {
    final receivers = <int>[
      for (int i = 0; i < small.length; i++)
        if (!small[i] && !large[i]) i,
    ];
    if (receivers.isNotEmpty) {
      return receivers;
    }
    return <int>[
      for (int i = 0; i < small.length; i++)
        if (!small[i]) i,
    ];
  }

  /// Distributes unused viewport width across [targets], in place.
  static void _shareRemainingWidth({
    required List<double> slots,
    required List<int> targets,
    required double viewport,
    required List<bool> large,
    required double largeMax,
  }) {
    if (targets.isEmpty) {
      return;
    }
    final double used = slots.fold<double>(0, (double a, double b) => a + b);
    final double share = (viewport - used) / targets.length;
    for (final index in targets) {
      slots[index] += share;
      if (large[index]) {
        slots[index] = math.min(slots[index], largeMax);
      }
    }
  }

  /// Rounds slots to whole pixels, nudging the last target to absorb any
  /// rounding drift so the row still sums to [viewport].
  static List<int> _roundSlots({
    required List<double> slots,
    required List<int> targets,
    required double viewport,
  }) {
    final rounded = <int>[
      for (final double slot in slots) math.max(1, slot.round()),
    ];
    if (targets.isEmpty) {
      return rounded;
    }
    final int drift =
        viewport.round() - rounded.fold<int>(0, (int a, int b) => a + b);
    final int last = targets.last;
    rounded[last] = math.max(1, rounded[last] + drift);
    return rounded;
  }
}
