part of 'm3e_bottom_sheet_frame.dart';

extension _M3EBottomSheetFrameBuild on _M3EBottomSheetFrameState {
  Listenable get _motion => Listenable.merge(<Listenable?>[
    _extent,
    _layout,
    widget.entrance,
    widget.backProgress,
  ]);

  bool get _hasFullScreen =>
      _detents?.heights.containsKey(M3EBottomSheetValue.fullScreen) ?? false;

  Widget _buildFrame(BuildContext context) {
    final double topPadding = MediaQuery.paddingOf(context).top;
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        assert(
          constraints.hasBoundedWidth && constraints.hasBoundedHeight,
          'M3EBottomSheet needs a bounded parent, e.g. Positioned.fill.',
        );
        final Size area = constraints.biggest;
        _noteArea(area, topPadding);
        return SizedBox.fromSize(
          size: area,
          child: AnimatedBuilder(
            animation: _motion,
            builder: (BuildContext context, _) => _positioned(context, area),
          ),
        );
      },
    );
  }

  Widget _positioned(BuildContext context, Size area) {
    final double width = _sheetWidth(area.width);
    final double visible = _visibleHeight();
    final double maxHeight = widget.expandToFullScreen
        ? area.height
        : math.max(0, area.height - _topMargin);
    Widget sheet = ConstrainedBox(
      constraints: BoxConstraints(maxHeight: maxHeight),
      child: M3EBottomSheetSizeReporter(
        onSize: _onSize,
        child: _sheet(context),
      ),
    );
    final SystemUiOverlayStyle? overlay = widget.overlayStyle;
    if (overlay != null) {
      sheet = AnnotatedRegion<SystemUiOverlayStyle>(
        value: overlay,
        child: sheet,
      );
    }
    sheet = _predictiveBack(sheet, width, visible);
    final bool hidden = !_modal && visible <= 0;
    return Stack(
      children: <Widget>[
        Positioned(
          left: (area.width - width) / 2,
          top: area.height - visible,
          width: width,
          child: ExcludeFocus(
            excluding: hidden,
            child: ExcludeSemantics(excluding: hidden, child: sheet),
          ),
        ),
      ],
    );
  }

  double _sheetWidth(double available) {
    if (widget.expandToFullScreen) {
      return available;
    }
    final double compact = math.min(available, _theme.maxWidth);
    final double wide = clampDouble(
      available - _theme.wideSideMargin * 2,
      0,
      _theme.maxWidth,
    );
    return clampDouble(lerpDouble(compact, wide, _layout.value)!, 0, available);
  }

  double _visibleHeight() {
    final double entrance = widget.entrance?.value ?? 1;
    return clampDouble(_extent.value * entrance, 0, _sheetHeight);
  }

  double _fullScreenProgress() {
    final M3EBottomSheetDetents? detents = _detents;
    if (detents == null || !_hasFullScreen) {
      return 0;
    }
    final double from = detents.heightOf(M3EBottomSheetValue.expanded);
    final double to = detents.heightOf(M3EBottomSheetValue.fullScreen);
    if (to <= from) {
      return 0;
    }
    return clampDouble((_extent.value - from) / (to - from), 0, 1);
  }

  Widget _predictiveBack(Widget child, double width, double visible) {
    final ValueListenable<double>? back = widget.backProgress;
    if (back == null) {
      return child;
    }
    final double progress = clampDouble(back.value, 0, 1);
    final double height = clampDouble(_extent.value, 0, _sheetHeight);
    final double sx = width > 0
        ? 1 - _theme.predictiveBackShrinkX * progress / width
        : 1;
    final double sy = height > 0
        ? 1 - _theme.predictiveBackShrinkY * progress / height
        : 1;
    return Transform(
      origin: Offset(width / 2, visible),
      transform: Matrix4.diagonal3Values(sx, sy, 1),
      child: child,
    );
  }

  Widget _sheet(BuildContext context) {
    final double f = _fullScreenProgress();
    // Inner inset from the view, so content clears the system bars even in
    // edge-to-edge apps; the container still paints under them.
    final EdgeInsets system = M3ESafeArea.paddingOf(context);
    final Widget content = Padding(
      padding: EdgeInsets.only(
        left: system.left,
        right: system.right,
        bottom: system.bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          _topRegion(f),
          Flexible(
            child: PrimaryScrollController(
              controller: _scroll,
              automaticallyInheritForPlatforms: TargetPlatform.values.toSet(),
              child: widget.child,
            ),
          ),
        ],
      ),
    );
    final surface = M3EBottomSheetSurface(
      theme: _theme,
      variant: widget.variant,
      topRadius: lerpDouble(_theme.topCornerRadius, 0, f)!,
      child: content,
    );
    if (!widget.enableDrag) {
      return surface;
    }
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onVerticalDragStart: (_) => _startDrag(),
      onVerticalDragUpdate: (DragUpdateDetails d) => dragBy(d.primaryDelta!),
      onVerticalDragEnd: (DragEndDetails d) => _endDrag(d.primaryVelocity),
      onVerticalDragCancel: () => _endDrag(0),
      child: surface,
    );
  }

  void _startDrag() {
    _runId++;
    _extent.stop();
    _setDragging(true);
  }

  void _endDrag(double? velocity) {
    _setDragging(false);
    settle(-(velocity ?? 0));
  }

  Widget _topRegion(double f) {
    final Widget? strip = widget.showDragHandle ? _strip() : null;
    if (!_hasFullScreen) {
      return strip ?? const SizedBox.shrink();
    }
    final double stripHeight = strip == null ? 0 : _theme.dragRegionHeight;
    final double headerHeight = _topPadding + _theme.fullScreenHeaderHeight;
    return SizedBox(
      height: lerpDouble(stripHeight, headerHeight, f),
      child: Stack(
        children: <Widget>[
          if (strip != null && f < 1)
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: ExcludeFocus(
                excluding: f >= 0.5,
                child: Opacity(opacity: 1 - f, child: strip),
              ),
            ),
          if (f > 0)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: ExcludeFocus(
                excluding: f < 0.5,
                child: Opacity(opacity: f, child: _header()),
              ),
            ),
        ],
      ),
    );
  }

  Widget _strip() {
    final M3EBottomSheetDragHandleStyle style = _theme.dragHandle;
    final MouseCursor cursor = !canDrag
        ? MouseCursor.defer
        : (_dragging ? style.draggingCursor : style.dragCursor);
    return M3EBottomSheetDragRegion(
      height: _theme.dragRegionHeight,
      cursor: cursor,
      child: M3EBottomSheetDragHandle(
        theme: _theme,
        label: widget.labels.dragHandle,
        value: widget.labels.valueOf(_value),
        onActivate: cycle,
        actions: _handleActions(),
        focusNode: widget.handleFocusNode,
      ),
    );
  }

  Map<CustomSemanticsAction, VoidCallback> _handleActions() {
    final M3EBottomSheetDetents? detents = _detents;
    if (detents == null) {
      return const <CustomSemanticsAction, VoidCallback>{};
    }
    final M3EBottomSheetLabels labels = widget.labels;
    final M3EBottomSheetValue? up = detents.above(_extent.value);
    final M3EBottomSheetValue? down = detents.below(_extent.value);
    return <CustomSemanticsAction, VoidCallback>{
      if (up != null)
        CustomSemanticsAction(label: labels.expand): () => animateTo(up),
      if (down != null)
        CustomSemanticsAction(label: labels.collapse): () => animateTo(down),
      if (_canHide)
        CustomSemanticsAction(label: labels.dismiss): () =>
            _settleTo(M3EBottomSheetValue.hidden),
    };
  }

  Widget _header() {
    final M3EBottomSheetLabels labels = widget.labels;
    return M3EBottomSheetHeader(
      height: _theme.fullScreenHeaderHeight,
      icon: _modal ? M3EIcons.close : M3EIcons.expand_more,
      tooltip: _modal ? labels.close : labels.collapse,
      title: widget.fullScreenTitle,
      onPressed: _modal
          ? () => _settleTo(M3EBottomSheetValue.hidden)
          : () => animateTo(M3EBottomSheetValue.collapsed),
    );
  }
}
