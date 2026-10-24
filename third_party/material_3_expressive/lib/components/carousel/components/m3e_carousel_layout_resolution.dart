part of '../m3e_carousel.dart';

/// Resolves layout inputs (slot patterns, weights, extents, padding) for
/// [_M3ECarouselState] from the widget's [M3ECarouselType] and theme.
extension _M3ECarouselLayoutResolution on _M3ECarouselState {
  bool _extended(M3ECarouselTheme theme) {
    if (widget.type == M3ECarouselType.hero) {
      return false;
    }
    if (widget.isExtended != null) {
      return widget.isExtended!;
    }
    return frameWidth >= theme.expandedBreakpoint;
  }

  /// Gap before the first uncontained item. Later rests keep that gap too.
  double _leadingGap(M3ECarouselTheme theme) {
    final bool uncontained =
        widget.type == M3ECarouselType.uncontained ||
        widget.type == M3ECarouselType.uncontainedMultiAspect;
    if (!_horizontal || !uncontained || widget.itemPadding != null) {
      return 0;
    }
    return theme.itemGap;
  }

  List<int> _pattern(bool extended) {
    switch (widget.type) {
      case M3ECarouselType.hero:
        switch (widget.heroAlignment) {
          case M3ECarouselHeroAlignment.left:
            return <int>[8, 2];
          case M3ECarouselHeroAlignment.center:
            return <int>[2, 6, 2];
          case M3ECarouselHeroAlignment.right:
            return <int>[2, 8];
        }
      case M3ECarouselType.contained:
        return extended ? <int>[4, 3, 2, 1] : <int>[5, 4, 1];
      case M3ECarouselType.uncontained:
      case M3ECarouselType.uncontainedMultiAspect:
      case M3ECarouselType.fullScreen:
        return const <int>[];
    }
  }

  List<int> _resolvedWeights(
    M3ECarouselTheme theme,
    bool extended,
    bool reduced,
    double trackMain,
  ) {
    if (reduced ||
        widget.type == M3ECarouselType.uncontained ||
        widget.type == M3ECarouselType.uncontainedMultiAspect ||
        _fullScreen) {
      return const <int>[];
    }
    return M3ECarouselLayout.clampWeights(
      pattern: _pattern(extended),
      viewport: trackMain,
      smallMin: theme.smallMinWidth,
      smallMax: theme.smallMaxWidth,
      largeMax: widget.largeMaxWidth ?? theme.largeMaxWidth,
    );
  }

  double? _fixedExtent(M3ECarouselTheme theme, bool reduced, double trackMain) {
    if (widget.type == M3ECarouselType.uncontainedMultiAspect) {
      return null;
    }
    if (_fullScreen) {
      // One item is the viewport. A gap here makes later pages land short.
      return trackMain;
    }
    if (widget.type == M3ECarouselType.uncontained) {
      return widget.uncontainedItemExtent;
    }
    if (!reduced) {
      return null;
    }
    if (widget.type == M3ECarouselType.hero) {
      return math.max(trackMain - theme.smallMaxWidth, trackMain * 0.5);
    }
    return trackMain;
  }

  List<double>? _aspectExtentsFor(
    M3ECarouselTheme theme,
    EdgeInsets container,
  ) {
    if (widget.type != M3ECarouselType.uncontainedMultiAspect) {
      return null;
    }
    final double cross = _horizontal
        ? math.max(0, frameHeight - container.vertical)
        : math.max(0, frameWidth - container.horizontal);
    final EdgeInsets itemPadding = _itemPadding(theme, false);
    final double gap = _horizontal
        ? itemPadding.horizontal
        : itemPadding.vertical;
    return <double>[
      for (int i = 0; i < widget.children.length; i++)
        cross *
                widget.children[i].aspectRatio.clamp(
                  theme.minAspect,
                  theme.maxAspect,
                ) +
            gap,
    ];
  }

  EdgeInsets _containerPadding(M3ECarouselTheme theme, bool reduced) {
    if (reduced && widget.type == M3ECarouselType.contained) {
      return EdgeInsets.zero;
    }
    return theme.containerPaddingFor(widget.type);
  }

  EdgeInsets _itemPadding(M3ECarouselTheme theme, bool reduced) {
    if (widget.itemPadding != null) {
      return widget.itemPadding!.resolve(Directionality.of(context));
    }
    final EdgeInsets themed = theme.itemPadding.resolve(
      Directionality.of(context),
    );
    const fallback = EdgeInsets.symmetric(horizontal: 4);
    if (themed != fallback) {
      return themed;
    }
    if (_fullScreen || (reduced && widget.type == M3ECarouselType.contained)) {
      return EdgeInsets.zero;
    }
    final bool bleed =
        widget.type == M3ECarouselType.uncontained ||
        widget.type == M3ECarouselType.uncontainedMultiAspect;
    if (bleed) {
      // The gap is on the trailing side so the leading edge matches the flush
      // trailing edge of the track.
      if (_horizontal) {
        return EdgeInsetsDirectional.only(end: theme.itemGap)
            .resolve(Directionality.of(context));
      }
      return EdgeInsets.only(top: theme.itemGap);
    }
    return _horizontal
        ? EdgeInsets.symmetric(horizontal: theme.itemGap / 2)
        : EdgeInsets.symmetric(vertical: theme.itemGap / 2);
  }
}
