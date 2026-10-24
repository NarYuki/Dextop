import 'dart:math';

import '../enums/m3e_carousel_type.dart';

/// Scroll-step helpers for carousel frame navigation.
abstract final class M3ECarouselScrollHelper {
  const M3ECarouselScrollHelper._();

  /// Computes the next scroll position and updated [itemScrolled] for one step.
  ///
  /// Returns `null` when the step is out of bounds.
  static ({double nextScrollPosition, int itemScrolled})? nextStep({
    required M3ECarouselType type,
    required M3ECarouselHeroAlignment heroAlignment,
    required bool isExtended,
    required double uncontainedItemExtent,
    required double leadingGap,
    required List<int> layoutWeight,
    required double mainExtent,
    required int childrenLength,
    required int itemScrolled,
    required double prevScrollPosition,
    required int direction,
  }) {
    return switch (type) {
      M3ECarouselType.hero => _heroStep(
        heroAlignment: heroAlignment,
        layoutWeight: layoutWeight,
        mainExtent: mainExtent,
        childrenLength: childrenLength,
        itemScrolled: itemScrolled,
        prevScrollPosition: prevScrollPosition,
        direction: direction,
      ),
      M3ECarouselType.contained => _containedStep(
        isExtended: isExtended,
        layoutWeight: layoutWeight,
        mainExtent: mainExtent,
        childrenLength: childrenLength,
        itemScrolled: itemScrolled,
        prevScrollPosition: prevScrollPosition,
        direction: direction,
      ),
      M3ECarouselType.uncontained ||
      M3ECarouselType.uncontainedMultiAspect ||
      M3ECarouselType.fullScreen => _uncontainedStep(
        uncontainedItemExtent: uncontainedItemExtent,
        leadingGap: leadingGap,
        childrenLength: childrenLength,
        itemScrolled: itemScrolled,
        direction: direction,
      ),
    };
  }

  static ({double nextScrollPosition, int itemScrolled})? _heroStep({
    required M3ECarouselHeroAlignment heroAlignment,
    required List<int> layoutWeight,
    required double mainExtent,
    required int childrenLength,
    required int itemScrolled,
    required double prevScrollPosition,
    required int direction,
  }) {
    final double delta = _weightDelta(
      layoutWeight: layoutWeight,
      mainExtent: mainExtent,
      weight: layoutWeight.reduce(
        heroAlignment == M3ECarouselHeroAlignment.left ? max : min,
      ),
    );
    final int limit = switch (heroAlignment) {
      M3ECarouselHeroAlignment.center => direction == 0 ? 0 : 3,
      M3ECarouselHeroAlignment.left => direction == 0 ? 0 : 2,
      M3ECarouselHeroAlignment.right => direction == 0 ? 0 : 2,
    };
    return _boundedStep(
      direction: direction,
      itemScrolled: itemScrolled,
      minIndex: limit,
      maxIndex: childrenLength - limit,
      prevScrollPosition: prevScrollPosition,
      delta: delta,
    );
  }

  static ({double nextScrollPosition, int itemScrolled})? _containedStep({
    required bool isExtended,
    required List<int> layoutWeight,
    required double mainExtent,
    required int childrenLength,
    required int itemScrolled,
    required double prevScrollPosition,
    required int direction,
  }) {
    final double delta = _weightDelta(
      layoutWeight: layoutWeight,
      mainExtent: mainExtent,
      weight: layoutWeight.isEmpty ? 0 : layoutWeight.first,
    );
    final int trailingLimit = childrenLength - (isExtended ? 4 : 3);
    return _boundedStep(
      direction: direction,
      itemScrolled: itemScrolled,
      minIndex: 0,
      maxIndex: trailingLimit,
      prevScrollPosition: prevScrollPosition,
      delta: delta,
    );
  }

  static ({double nextScrollPosition, int itemScrolled})? _uncontainedStep({
    required double uncontainedItemExtent,
    required double leadingGap,
    required int childrenLength,
    required int itemScrolled,
    required int direction,
  }) {
    if (direction == 0) {
      if (itemScrolled <= 0) {
        return null;
      }
      final int next = itemScrolled - 1;
      return (
        nextScrollPosition: _uncontainedOffset(
          next,
          uncontainedItemExtent,
          leadingGap,
        ),
        itemScrolled: next,
      );
    }
    if (itemScrolled >= childrenLength - 1) {
      return null;
    }
    final int next = itemScrolled + 1;
    return (
      nextScrollPosition: _uncontainedOffset(
        next,
        uncontainedItemExtent,
        leadingGap,
      ),
      itemScrolled: next,
    );
  }

  /// Largest step index for [type]. Negative when the track cannot step.
  static int maxIndex({
    required M3ECarouselType type,
    required M3ECarouselHeroAlignment heroAlignment,
    required bool isExtended,
    required int childrenLength,
  }) {
    return switch (type) {
      M3ECarouselType.hero =>
        childrenLength -
            switch (heroAlignment) {
              M3ECarouselHeroAlignment.center => 3,
              M3ECarouselHeroAlignment.left ||
              M3ECarouselHeroAlignment.right => 2,
            },
      M3ECarouselType.contained => childrenLength - (isExtended ? 4 : 3),
      M3ECarouselType.uncontained ||
      M3ECarouselType.uncontainedMultiAspect ||
      M3ECarouselType.fullScreen => childrenLength - 1,
    };
  }

  /// Scroll offset that places [index] where a swipe would land it.
  static double offsetForIndex({
    required M3ECarouselType type,
    required M3ECarouselHeroAlignment heroAlignment,
    required bool isExtended,
    required double uncontainedItemExtent,
    required double leadingGap,
    required List<int> layoutWeight,
    required double mainExtent,
    required int childrenLength,
    required int index,
  }) {
    final int limit = maxIndex(
      type: type,
      heroAlignment: heroAlignment,
      isExtended: isExtended,
      childrenLength: childrenLength,
    );
    final int target = limit < 0 ? 0 : index.clamp(0, limit);
    if (layoutWeight.isEmpty) {
      return _uncontainedOffset(target, uncontainedItemExtent, leadingGap);
    }
    return switch (type) {
      M3ECarouselType.hero =>
        target *
            _weightDelta(
              layoutWeight: layoutWeight,
              mainExtent: mainExtent,
              weight: layoutWeight.reduce(
                heroAlignment == M3ECarouselHeroAlignment.left ? max : min,
              ),
            ),
      M3ECarouselType.contained =>
        target *
            _weightDelta(
              layoutWeight: layoutWeight,
              mainExtent: mainExtent,
              weight: layoutWeight.isEmpty ? 0 : layoutWeight.first,
            ),
      M3ECarouselType.uncontained ||
      M3ECarouselType.uncontainedMultiAspect ||
      M3ECarouselType.fullScreen => _uncontainedOffset(
        target,
        uncontainedItemExtent,
        leadingGap,
      ),
    };
  }

  static double _weightDelta({
    required List<int> layoutWeight,
    required double mainExtent,
    required int weight,
  }) {
    final int total = layoutWeight.fold<int>(0, (int a, int b) => a + b);
    if (total == 0) {
      return 0;
    }
    return weight / total * mainExtent;
  }

  /// Scroll offset that leaves [leadingGap] before [index].
  ///
  /// Index 0 stays at 0 so the start padding shows. Later indexes stop on the
  /// previous item's trailing padding, which is already inside [extent].
  /// Adding [leadingGap] again would park the card on the view edge.
  static double _uncontainedOffset(
    int index,
    double extent,
    double leadingGap,
  ) {
    if (index <= 0 || leadingGap < 0) {
      return 0;
    }
    return index * extent;
  }

  static ({double nextScrollPosition, int itemScrolled})? _boundedStep({
    required int direction,
    required int itemScrolled,
    required int minIndex,
    required int maxIndex,
    required double prevScrollPosition,
    required double delta,
  }) {
    if (direction == 0) {
      if (itemScrolled <= minIndex) {
        return null;
      }
      return (
        nextScrollPosition: prevScrollPosition - delta,
        itemScrolled: itemScrolled - 1,
      );
    }
    if (itemScrolled >= maxIndex) {
      return null;
    }
    return (
      nextScrollPosition: prevScrollPosition + delta,
      itemScrolled: itemScrolled + 1,
    );
  }
}
