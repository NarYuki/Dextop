part of '../m3e_range_slider.dart';

/// Resolved theme values for [M3ERangeSlider] layout.
class _M3ERangeSliderResolved {
  const _M3ERangeSliderResolved({
    required this.sliderTheme,
    required this.colors,
    required this.direction,
    required this.rtl,
    required this.handleThickness,
    required this.wavelength,
    required this.waveSpeed,
    required this.amplitudeFactor,
    required this.trackThickness,
    required this.cornerRadius,
    required this.thumbLength,
    required this.dotSize,
    required this.dotSpacing,
    required this.tickSize,
    required this.useCustomDots,
  });

  final M3ESliderTheme sliderTheme;
  final M3ESliderColors colors;
  final TextDirection direction;
  final bool rtl;
  final double handleThickness;
  final double wavelength;
  final double waveSpeed;
  final double amplitudeFactor;
  final double trackThickness;
  final double cornerRadius;
  final double thumbLength;
  final double dotSize;
  final double dotSpacing;
  final double tickSize;
  final bool useCustomDots;
}

extension on _M3ERangeSliderState {
  _M3ERangeSliderResolved _resolve(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3ESliderTheme sliderTheme = theme.sliderTheme.applyingSize(
      widget.size,
    );
    final M3ESliderColors colors = sliderTheme.colors(
      theme.colorScheme,
      enabled: _enabled,
    );
    final TextDirection direction = Directionality.of(context);
    final double wavelength = widget.wavelength ?? sliderTheme.wavelength;
    return _M3ERangeSliderResolved(
      sliderTheme: sliderTheme,
      colors: colors,
      direction: direction,
      rtl: direction == TextDirection.rtl,
      handleThickness: _pressed || _showFocusOutline
          ? sliderTheme.focusHandleWidth
          : sliderTheme.handleWidth,
      wavelength: wavelength,
      waveSpeed: widget.waveSpeed ?? wavelength,
      amplitudeFactor: _amplitudeFactor(sliderTheme),
      trackThickness: widget.trackThickness ?? sliderTheme.trackHeight,
      cornerRadius: widget.cornerRadius ?? sliderTheme.trackCornerRadius,
      thumbLength: widget.thumbLength ?? sliderTheme.handleHeight,
      dotSize: widget.dotSize ?? sliderTheme.stopIndicatorSize,
      dotSpacing: widget.dotSpacing ?? sliderTheme.stopIndicatorTrailingSpace,
      tickSize: sliderTheme.tickSize,
      useCustomDots: widget.dotBuilder != null,
    );
  }

  Widget _buildLayout(
    BuildContext context,
    BoxConstraints constraints,
    _M3ERangeSliderResolved resolved,
  ) {
    final double width = constraints.maxWidth;
    final double height = math.max(
      resolved.sliderTheme.height,
      resolved.thumbLength,
    );
    final Widget track = _buildTrackLayer(resolved);
    final double startX = _thumbX(_startFraction, width, resolved.rtl);
    final double endX = _thumbX(_endFraction, width, resolved.rtl);

    final Widget stackBody = _buildStackBody(
      width: width,
      height: height,
      track: track,
      startX: startX,
      endX: endX,
      resolved: resolved,
    );
    final Widget gestureDetector = _buildGestureDetector(
      width: width,
      startX: startX,
      endX: endX,
      resolved: resolved,
      child: stackBody,
    );
    return _buildFocusRegion(gestureDetector);
  }

  Widget _buildGestureDetector({
    required double width,
    required double startX,
    required double endX,
    required _M3ERangeSliderResolved resolved,
    required Widget child,
  }) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onHorizontalDragStart: !_enabled
          ? null
          : (DragStartDetails d) =>
                _selectThumb(d.localPosition.dx, startX, endX),
      onHorizontalDragUpdate: !_enabled
          ? null
          : (DragUpdateDetails d) =>
                _update(d.localPosition.dx, width, resolved.rtl),
      onHorizontalDragEnd: !_enabled ? null : (_) => _endInteraction(),
      onHorizontalDragCancel: !_enabled ? null : _endInteraction,
      onTapDown: !_enabled
          ? null
          : (TapDownDetails d) {
              _selectThumb(d.localPosition.dx, startX, endX);
              _update(d.localPosition.dx, width, resolved.rtl);
            },
      onTapUp: !_enabled ? null : (_) => _endInteraction(),
      onTapCancel: !_enabled ? null : _endInteraction,
      child: child,
    );
  }

  Widget _buildFocusRegion(Widget gestureDetector) {
    return TapRegion(
      onTapOutside: M3EFocus.tapOutsideHandler(_focusNode),
      child: Focus(
        focusNode: _focusNode,
        autofocus: widget.autofocus,
        canRequestFocus: _enabled,
        onKeyEvent: _handleKeyEvent,
        child: MouseRegion(
          cursor: _enabled
              ? SystemMouseCursors.click
              : SystemMouseCursors.basic,
          onEnter: (_) => _setHovered(true),
          onExit: (_) => _setHovered(false),
          child: gestureDetector,
        ),
      ),
    );
  }

  void _setHovered(bool value) {
    if (_hovered == value || !mounted) {
      return;
    }
    setState(() => _hovered = value);
  }

  void _selectThumb(double dx, double startX, double endX) {
    _isFocusedFromPointer = true;
    M3EFocusInteraction.instance.notePointerInteraction();
    _focusNode.requestFocus();
    _dragging = true;
    final double distStart = (dx - startX).abs();
    final double distEnd = (dx - endX).abs();
    setState(() {
      _activeThumb = distStart <= distEnd
          ? _M3ERangeThumb.start
          : _M3ERangeThumb.end;
    });
  }

  double _thumbX(double fraction, double width, bool rtl) {
    final double f = rtl ? 1.0 - fraction : fraction;
    return f * width;
  }

  Widget _buildTrackLayer(_M3ERangeSliderResolved resolved) {
    Widget buildTrack({required double phase}) {
      return M3ERangeSliderTrack(
        startFraction: _startFraction,
        endFraction: _endFraction,
        tickFractions: _ticks,
        colors: resolved.colors,
        theme: resolved.sliderTheme,
        axis: Axis.horizontal,
        textDirection: resolved.direction,
        handleThickness: resolved.handleThickness,
        trackHeight: resolved.trackThickness,
        cornerRadius: resolved.cornerRadius,
        stopIndicatorSize: resolved.dotSize,
        tickSize: resolved.tickSize,
        edgeInset: resolved.dotSpacing,
        drawDots: !resolved.useCustomDots,
        isWavy: widget.wavy,
        waveAmplitude: resolved.sliderTheme.waveAmplitude,
        wavelength: resolved.wavelength,
        phase: phase,
        amplitudeFactor: resolved.amplitudeFactor,
      );
    }

    final Widget track = widget.wavy
        ? AnimatedBuilder(
            animation: _waveController,
            builder: (BuildContext context, Widget? child) {
              return buildTrack(
                phase: _phase(resolved.wavelength, resolved.waveSpeed),
              );
            },
          )
        : buildTrack(phase: 0);

    if (!resolved.useCustomDots) {
      return track;
    }
    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        track,
        M3ESliderDotOverlay(
          builder: widget.dotBuilder!,
          mode: M3ESliderPaintMode.range,
          trackKind: M3ESliderTrackKind.standard,
          activeStartFraction: _startFraction,
          activeEndFraction: _endFraction,
          tickFractions: _ticks,
          colors: resolved.colors,
          trackHeight: resolved.trackThickness,
          handleGap: resolved.sliderTheme.handleGap,
          handleThickness: resolved.handleThickness,
          stopIndicatorSize: resolved.dotSize,
          tickSize: resolved.tickSize,
          edgeInset: resolved.dotSpacing,
          axis: Axis.horizontal,
          textDirection: resolved.direction,
        ),
      ],
    );
  }

  Widget _buildThumb({
    required bool pressed,
    required bool focused,
    required bool overlap,
    required _M3ERangeSliderResolved resolved,
  }) {
    return M3ESliderThumb(
      color: resolved.colors.thumb,
      pressed: pressed,
      focused: focused,
      hovered: _hovered,
      overlap: overlap,
      overlapColor: M3ETheme.of(context).colorScheme.onPrimary,
      width: resolved.sliderTheme.handleWidth,
      height: resolved.thumbLength,
      pressedThickness: resolved.sliderTheme.focusHandleWidth,
    );
  }

  Widget _buildStackBody({
    required double width,
    required double height,
    required Widget track,
    required double startX,
    required double endX,
    required _M3ERangeSliderResolved resolved,
  }) {
    final overlap = (endX - startX).abs() <= resolved.sliderTheme.handleWidth;
    final showValue = _pressed || _showFocusOutline;
    final valueX = _keyboardThumb == _M3ERangeThumb.start ? startX : endX;
    return SizedBox(
      width: width,
      height: height,
      child: Stack(
        clipBehavior: Clip.none,
        alignment: Alignment.center,
        children: <Widget>[
          Positioned.fill(child: track),
          Positioned(
            left: startX - 12,
            width: 24,
            height: height,
            child: Center(
              child: _buildThumb(
                pressed: _activeThumb == _M3ERangeThumb.start,
                focused:
                    _showFocusOutline && _keyboardThumb == _M3ERangeThumb.start,
                overlap: overlap,
                resolved: resolved,
              ),
            ),
          ),
          Positioned(
            left: endX - 12,
            width: 24,
            height: height,
            child: Center(
              child: _buildThumb(
                pressed: _activeThumb == _M3ERangeThumb.end,
                focused:
                    _showFocusOutline && _keyboardThumb == _M3ERangeThumb.end,
                overlap: overlap,
                resolved: resolved,
              ),
            ),
          ),
          if (showValue)
            Positioned(
              left: valueX - resolved.sliderTheme.valueIndicatorWidth / 2,
              bottom:
                  height / 2 +
                  resolved.thumbLength / 2 +
                  resolved.sliderTheme.valueIndicatorBottomSpace,
              child: M3ESliderValueIndicator(
                label: _indicatorLabel(),
                colors: resolved.colors,
              ),
            ),
        ],
      ),
    );
  }
}
