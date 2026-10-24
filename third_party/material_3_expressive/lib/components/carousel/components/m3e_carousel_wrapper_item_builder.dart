part of 'm3e_carousel_wrapper.dart';

/// Builds the pulsed item widgets, focus rings, and the underlying
/// [M3ECarouselView] for [_M3ECarouselWrapperState].
extension _M3ECarouselWrapperItemBuilder on _M3ECarouselWrapperState {
  List<Widget> _focusRings(double edgeDelta, M3EThemeData theme) {
    final BorderRadius radius = widget.shape is RoundedRectangleBorder
        ? ((widget.shape! as RoundedRectangleBorder).borderRadius
              as BorderRadius)
        : BorderRadius.zero;
    final M3ECarouselTheme carousel = theme.carouselTheme;
    final rings = <Widget>[];
    for (var index = 0; index < widget.children.length; index++) {
      if (index >= _itemFocus.length || !_itemFocus[index].hasPrimaryFocus) {
        continue;
      }
      if (index >= _itemRest.length || _itemRest[index].isEmpty) {
        continue;
      }
      final Size rest = _itemRest[index];
      final frame = _pulseFrameRect(
        index: index,
        isActive: _activeIndex == index,
        isLeftNeighbor: _leftVisibleNeighborIndex == index,
        isRightNeighbor: _rightVisibleNeighborIndex == index,
        edgeDelta: edgeDelta,
        restWidth: rest.width,
        restHeight: rest.height,
      );
      rings.add(
        CompositedTransformFollower(
          link: _itemLinks[index],
          showWhenUnlinked: false,
          child: IgnorePointer(
            child: SizedBox(
              width: frame.width,
              height: frame.height,
              child: M3EFocusRing(
                focused: theme.keyboardFocusIndicators,
                radius: radius,
                width: carousel.focusThickness,
                gap: carousel.focusOffset,
                color: theme.colorScheme.secondary,
                child: const SizedBox.expand(),
              ),
            ),
          ),
        ),
      );
    }
    return rings;
  }

  Widget _buildPulsedChild({
    required int index,
    required double edgeDelta,
    required double viewportMain,
  }) {
    final isActive = _activeIndex == index;
    final isLeftNeighbor = _leftVisibleNeighborIndex == index;
    final isRightNeighbor = _rightVisibleNeighborIndex == index;
    final BorderRadius finalRadius = widget.shape is RoundedRectangleBorder
        ? ((widget.shape! as RoundedRectangleBorder).borderRadius
              as BorderRadius)
        : BorderRadius.zero;
    final Clip clipBehavior = widget.itemClipBehavior != Clip.none
        ? widget.itemClipBehavior
        : Clip.antiAlias;
    final double stableInner = _stableInnerContentExtent(viewportMain);
    final double pulseBudget = widget.fixedPulseDelta * 2;
    // Resolve inherited values before the sliver lays the item out. Looking
    // them up while the scroll view is in layout drops the leading child.
    final M3EThemeData theme = M3ETheme.of(context);
    final bool compact = MediaQuery.sizeOf(context).width < 600;

    return _CarouselItemAnchor(
      index: index,
      onRegister: _registerItemBox,
      onUnregister: _unregisterItemBox,
      child: RepaintBoundary(
        // Content is laid out once at (max slot + pulse overflow) and stays
        // pinned in resting coordinates. Only the clip rect animates.
        child: LayoutBuilder(
          builder: (BuildContext context, BoxConstraints constraints) {
            final double gap = widget.trailingGap;
            final double restWidth = math.max(
              0,
              constraints.maxWidth - (_vertical ? 0 : gap),
            );
            final double restHeight = math.max(
              0,
              constraints.maxHeight - (_vertical ? gap : 0),
            );
            _rememberRest(index, Size(restWidth, restHeight));
            final ScrollPosition position = Scrollable.of(context).position;
            return Padding(
              padding: EdgeInsets.only(
                right: _vertical ? 0 : gap,
                bottom: _vertical ? gap : 0,
              ),
              child: _buildPulsedChildFrame(
                index: index,
                isActive: isActive,
                isLeftNeighbor: isLeftNeighbor,
                isRightNeighbor: isRightNeighbor,
                edgeDelta: edgeDelta,
                restWidth: restWidth,
                restHeight: restHeight,
                stableInner: stableInner,
                pulseBudget: pulseBudget,
                finalRadius: finalRadius,
                clipBehavior: clipBehavior,
                theme: theme,
                compact: compact,
                position: position,
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildPulsedChildFrame({
    required int index,
    required bool isActive,
    required bool isLeftNeighbor,
    required bool isRightNeighbor,
    required double edgeDelta,
    required double restWidth,
    required double restHeight,
    required double stableInner,
    required double pulseBudget,
    required BorderRadius finalRadius,
    required Clip clipBehavior,
    required M3EThemeData theme,
    required bool compact,
    required ScrollPosition position,
  }) {
    final frame = _pulseFrameRect(
      index: index,
      isActive: isActive,
      isLeftNeighbor: isLeftNeighbor,
      isRightNeighbor: isRightNeighbor,
      edgeDelta: edgeDelta,
      restWidth: restWidth,
      restHeight: restHeight,
    );

    // Weighted layouts center a stable image so the clip can parallax.
    // Full-screen pages are one size, so the image shifts with scroll instead.
    // Other fixed layouts pin the image so a settle does not slide it.
    final bool fullscreenParallax = _fullscreenParallax;
    final bool pinImage = widget.flexWeights == null && !fullscreenParallax;
    final double pinnedMain = _pinnedContentMain(
      index,
      _vertical ? restHeight : restWidth,
    );
    final content = _pinnedContentGeometry(
      pinImage: pinImage,
      restWidth: restWidth,
      restHeight: restHeight,
      stableInner: stableInner,
      pulseBudget: pulseBudget,
      pinnedMain: pinnedMain,
    );
    final metadata = _itemRenderMetadata(
      index: index,
      restWidth: restWidth,
      restHeight: restHeight,
      theme: theme,
    );

    return _buildItemInteractiveFrame(
      index: index,
      restWidth: restWidth,
      restHeight: restHeight,
      frame: frame,
      content: content,
      fullscreenParallax: fullscreenParallax,
      finalRadius: finalRadius,
      clipBehavior: clipBehavior,
      pulseBudget: pulseBudget,
      position: position,
      metadata: metadata,
    );
  }

  /// Theme, item, and derived display values for one carousel item.
  ({
    M3ECarouselTheme carousel,
    M3EColorScheme scheme,
    M3ECarouselItem item,
    bool enabled,
    bool hovered,
    String semanticsLabel,
    Widget picture,
    Widget labels,
    M3ECarouselScrim? scrim,
  })
  _itemRenderMetadata({
    required int index,
    required double restWidth,
    required double restHeight,
    required M3EThemeData theme,
  }) {
    final M3ECarouselItem item = _itemAt(index);
    final bool enabled = item.enabled;
    final Set<WidgetState> states = index < _itemStates.length
        ? _itemStates[index].value
        : const <WidgetState>{};
    final slotMain = _vertical ? restHeight : restWidth;
    final positionLabel =
        'List item, ${index + 1} of ${widget.children.length}';
    final spoken = item.semanticLabel;
    return (
      carousel: theme.carouselTheme,
      scheme: theme.colorScheme,
      item: item,
      enabled: enabled,
      hovered: states.contains(WidgetState.hovered),
      semanticsLabel: spoken == null
          ? positionLabel
          : '$spoken, $positionLabel',
      picture: item.image,
      labels: item.textOverlay(slotMain: slotMain) ?? const SizedBox.shrink(),
      scrim: item.showScrim,
    );
  }

  /// Focusable, tappable item shell: sizes to the resting slot, positions
  /// the animating clip [frame], and fills it with the item's [Material].
  Widget _buildItemInteractiveFrame({
    required int index,
    required double restWidth,
    required double restHeight,
    required ({double left, double top, double width, double height}) frame,
    required ({double width, double height, double left, double top}) content,
    required bool fullscreenParallax,
    required BorderRadius finalRadius,
    required Clip clipBehavior,
    required double pulseBudget,
    required ScrollPosition position,
    required ({
      M3ECarouselTheme carousel,
      M3EColorScheme scheme,
      M3ECarouselItem item,
      bool enabled,
      bool hovered,
      String semanticsLabel,
      Widget picture,
      Widget labels,
      M3ECarouselScrim? scrim,
    })
    metadata,
  }) {
    return SizedBox(
      width: restWidth,
      height: restHeight,
      child: TapRegion(
        onTapOutside: (_) => _clearItemFocus(index),
        child: Semantics(
          label: metadata.semanticsLabel,
          button: metadata.enabled,
          child: Focus(
            focusNode: index < _itemFocus.length ? _itemFocus[index] : null,
            onKeyEvent: (FocusNode node, KeyEvent event) =>
                _onItemKey(index, event),
            onFocusChange: (bool hasFocus) {
              if (index < _itemStates.length) {
                _itemStates[index].update(WidgetState.focused, hasFocus);
              }
              _onItemStates();
            },
            child: Stack(
              clipBehavior: Clip.none,
              children: <Widget>[
                Positioned(
                  left: frame.left,
                  top: frame.top,
                  width: frame.width,
                  height: frame.height,
                  child: _buildItemMaterial(
                    index: index,
                    restWidth: restWidth,
                    restHeight: restHeight,
                    frame: frame,
                    content: content,
                    fullscreenParallax: fullscreenParallax,
                    finalRadius: finalRadius,
                    clipBehavior: clipBehavior,
                    pulseBudget: pulseBudget,
                    position: position,
                    metadata: metadata,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// The item's [Material] surface: elevation/shape chrome around the
  /// picture, scrim, label, and ink layers.
  Widget _buildItemMaterial({
    required int index,
    required double restWidth,
    required double restHeight,
    required ({double left, double top, double width, double height}) frame,
    required ({double width, double height, double left, double top}) content,
    required bool fullscreenParallax,
    required BorderRadius finalRadius,
    required Clip clipBehavior,
    required double pulseBudget,
    required ScrollPosition position,
    required ({
      M3ECarouselTheme carousel,
      M3EColorScheme scheme,
      M3ECarouselItem item,
      bool enabled,
      bool hovered,
      String semanticsLabel,
      Widget picture,
      Widget labels,
      M3ECarouselScrim? scrim,
    })
    metadata,
  }) {
    return CompositedTransformTarget(
      link: _itemLinks[index],
      child: Material(
        color: metadata.scheme.surface,
        elevation: metadata.hovered && metadata.enabled
            ? metadata.carousel.hoverElevation
            : 0,
        shadowColor: metadata.scheme.shadow,
        surfaceTintColor: Colors.transparent,
        clipBehavior: clipBehavior,
        shape: RoundedRectangleBorder(borderRadius: finalRadius),
        child: Stack(
          children: _buildItemStackChildren(
            index: index,
            item: metadata.item,
            enabled: metadata.enabled,
            fullscreenParallax: fullscreenParallax,
            picture: metadata.picture,
            labels: metadata.labels,
            scrim: metadata.scrim,
            scrimColor: metadata.scheme.scrim,
            carousel: metadata.carousel,
            restWidth: restWidth,
            restHeight: restHeight,
            pulseBudget: pulseBudget,
            position: position,
            frame: frame,
            content: content,
          ),
        ),
      ),
    );
  }

  /// Content-layer width/height/offset so the largest resting slot (plus
  /// pulse overflow) stays laid out underneath the animating clip frame.
  ({double width, double height, double left, double top})
  _pinnedContentGeometry({
    required bool pinImage,
    required double restWidth,
    required double restHeight,
    required double stableInner,
    required double pulseBudget,
    required double pinnedMain,
  }) {
    final double contentWidth = pinImage
        ? (_vertical ? restWidth : pinnedMain + pulseBudget * 2)
        : _vertical
        ? restWidth
        : widget.reducedMotion
        ? restWidth + pulseBudget
        : math.max(stableInner, restWidth) + pulseBudget;
    final double contentHeight = pinImage
        ? (_vertical ? pinnedMain + pulseBudget * 2 : restHeight)
        : _vertical
        ? widget.reducedMotion
              ? restHeight + pulseBudget
              : math.max(stableInner, restHeight) + pulseBudget
        : restHeight;
    final double contentLeft = pinImage
        ? (_vertical ? 0 : -pulseBudget)
        : (restWidth - contentWidth) / 2;
    final double contentTop = pinImage
        ? (_vertical ? -pulseBudget : 0)
        : (restHeight - contentHeight) / 2;
    return (
      width: contentWidth,
      height: contentHeight,
      left: contentLeft,
      top: contentTop,
    );
  }

  /// Picture, scrim, label, and ink layers stacked inside an item's
  /// [Material], in paint order.
  List<Widget> _buildItemStackChildren({
    required int index,
    required M3ECarouselItem item,
    required bool enabled,
    required bool fullscreenParallax,
    required Widget picture,
    required Widget labels,
    required M3ECarouselScrim? scrim,
    required Color scrimColor,
    required M3ECarouselTheme carousel,
    required double restWidth,
    required double restHeight,
    required double pulseBudget,
    required ScrollPosition position,
    required ({double left, double top, double width, double height}) frame,
    required ({double width, double height, double left, double top}) content,
  }) {
    return <Widget>[
      if (fullscreenParallax)
        _buildParallaxPictureLayer(
          index: index,
          restWidth: restWidth,
          restHeight: restHeight,
          pulseBudget: pulseBudget,
          position: position,
          picture: picture,
          enabled: enabled,
          carousel: carousel,
        )
      else
        _buildPinnedPictureLayer(
          picture: picture,
          enabled: enabled,
          carousel: carousel,
          content: content,
          frame: frame,
        ),
      if (scrim != null)
        Positioned.fill(
          child: IgnorePointer(child: _buildScrimLayer(scrim, scrimColor)),
        ),
      Positioned.fill(
        child: IgnorePointer(
          child: Opacity(
            opacity: enabled ? 1 : carousel.disabledOpacity,
            child: labels,
          ),
        ),
      ),
      _buildItemInkWellLayer(index: index, item: item, enabled: enabled),
    ];
  }

  /// Solid or gradient scrim between the picture and the text.
  Widget _buildScrimLayer(M3ECarouselScrim scrim, Color scrimColor) {
    if (!scrim.isGradient) {
      return ColoredBox(color: scrim.paintColor);
    }
    final Widget gradient = DecoratedBox(
      decoration: BoxDecoration(gradient: scrim.resolveGradient(scrimColor)),
    );
    final double opacity = scrim.opacity.clamp(0, 1).toDouble();
    return opacity < 1 ? Opacity(opacity: opacity, child: gradient) : gradient;
  }

  /// Full-screen picture layer that shifts with scroll instead of resizing.
  Widget _buildParallaxPictureLayer({
    required int index,
    required double restWidth,
    required double restHeight,
    required double pulseBudget,
    required ScrollPosition position,
    required Widget picture,
    required bool enabled,
    required M3ECarouselTheme carousel,
  }) {
    return Positioned.fill(
      child: ListenableBuilder(
        listenable: position,
        builder: (BuildContext context, Widget? child) {
          final restMain = _vertical ? restHeight : restWidth;
          final double extra = restMain * 0.5;
          final double shift = _fullscreenParallaxShift(
            index,
            restMain,
            position,
          );
          final double main = restMain + extra + pulseBudget * 2;
          final double cross =
              (_vertical ? restWidth : restHeight) + pulseBudget * 2;
          final double mainOffset = (restMain - main) / 2 + shift;
          return Stack(
            children: <Widget>[
              Positioned(
                left: _vertical ? -pulseBudget : mainOffset,
                top: _vertical ? mainOffset : -pulseBudget,
                width: _vertical ? cross : main,
                height: _vertical ? main : cross,
                child: child!,
              ),
            ],
          );
        },
        child: IgnorePointer(
          child: Opacity(
            opacity: enabled ? 1 : carousel.disabledOpacity,
            child: picture,
          ),
        ),
      ),
    );
  }

  /// Pinned picture layer, centered under the clip frame so a settle never
  /// slides the image.
  Widget _buildPinnedPictureLayer({
    required Widget picture,
    required bool enabled,
    required M3ECarouselTheme carousel,
    required ({double width, double height, double left, double top}) content,
    required ({double left, double top, double width, double height}) frame,
  }) {
    final double left = content.left - frame.left;
    final double top = content.top - frame.top;
    return Positioned(
      left: left,
      top: top,
      width: math.max(content.width, frame.width - left),
      height: math.max(content.height, frame.height - top),
      child: IgnorePointer(
        child: Opacity(
          opacity: enabled ? 1 : carousel.disabledOpacity,
          child: picture,
        ),
      ),
    );
  }

  Widget _buildItemInkWellLayer({
    required int index,
    required M3ECarouselItem item,
    required bool enabled,
  }) {
    return Positioned.fill(
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(
          statesController: index < _itemStates.length
              ? _itemStates[index]
              : null,
          enableFeedback: false,
          splashFactory: InkSparkle.splashFactory,
          mouseCursor: enabled
              ? SystemMouseCursors.click
              : SystemMouseCursors.basic,
          canRequestFocus: false,
          onTap: enabled
              ? () {
                  item.onTap?.call();
                  if (index < _itemFocus.length) {
                    _itemFocus[index].requestFocus();
                  }
                  _handleTap(index);
                }
              : null,
          overlayColor: widget.overlayColor,
        ),
      ),
    );
  }

  Widget _buildCarouselView(List<Widget> carouselChildren) {
    // Free scroll drags and snaps. Otherwise a swipe steps one item and the
    // scroll view does not drag or fling on after the step.
    final ScrollPhysics? physics = widget.freeScroll
        ? null
        : const M3ECarouselStepPhysics();
    final List<double>? extents = widget.itemExtents;
    if (extents != null) {
      return _buildExtentsView(carouselChildren, physics, extents);
    }
    if (widget.flexWeights != null) {
      return _buildWeightedView(carouselChildren, physics);
    }
    return _buildFixedExtentView(carouselChildren, physics);
  }

  Widget _buildExtentsView(
    List<Widget> carouselChildren,
    ScrollPhysics? physics,
    List<double> extents,
  ) {
    return M3ECarouselView(
      physics: physics,
      padding: widget.padding,
      backgroundColor: widget.backgroundColor,
      elevation: widget.elevation,
      shape: widget.shape,
      itemClipBehavior: Clip.none,
      overlayColor: widget.overlayColor,
      itemSnapping: widget.itemSnapping,
      shrinkExtent: widget.shrinkExtent,
      scaleItems: widget.scaleItems,
      controller: _internalController,
      scrollDirection: widget.scrollDirection,
      reverse: widget.reverse,
      enableSplash: false,
      infinite: widget.infinite,
      leadingInset: widget.leadingInset,
      itemExtent: extents.isEmpty ? 1 : extents.first,
      restingExtents: extents,
      onIndexChanged: widget.onIndexChanged,
      onChange: widget.onChange,
      children: carouselChildren,
    );
  }

  Widget _buildWeightedView(
    List<Widget> carouselChildren,
    ScrollPhysics? physics,
  ) {
    return M3ECarouselView.weighted(
      physics: physics,
      padding: widget.padding,
      backgroundColor: widget.backgroundColor,
      elevation: widget.elevation,
      shape: widget.shape,
      itemClipBehavior: Clip.none,
      overlayColor: widget.overlayColor,
      itemSnapping: widget.itemSnapping,
      consumeMaxWeight: widget.consumeMaxWeight,
      shrinkExtent: widget.shrinkExtent,
      controller: _internalController,
      scrollDirection: widget.scrollDirection,
      reverse: widget.reverse,
      enableSplash: false,
      infinite: widget.infinite,
      flexWeights: widget.flexWeights!,
      onIndexChanged: widget.onIndexChanged,
      onChange: widget.onChange,
      children: carouselChildren,
    );
  }

  Widget _buildFixedExtentView(
    List<Widget> carouselChildren,
    ScrollPhysics? physics,
  ) {
    return M3ECarouselView(
      physics: physics,
      padding: widget.padding,
      backgroundColor: widget.backgroundColor,
      elevation: widget.elevation,
      shape: widget.shape,
      itemClipBehavior: Clip.none,
      overlayColor: widget.overlayColor,
      itemSnapping: widget.itemSnapping,
      shrinkExtent: widget.shrinkExtent,
      scaleItems: widget.scaleItems,
      controller: _internalController,
      scrollDirection: widget.scrollDirection,
      reverse: widget.reverse,
      enableSplash: false,
      infinite: widget.infinite,
      leadingInset: widget.leadingInset,
      itemExtent: widget.itemExtent!,
      onIndexChanged: widget.onIndexChanged,
      onChange: widget.onChange,
      children: carouselChildren,
    );
  }
}
