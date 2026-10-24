part of '../m3e_refresh_indicator.dart';

/// Indicator geometry and spinner builders for [M3ERefreshIndicatorState].
extension _M3ERefreshIndicatorBuild on M3ERefreshIndicatorState {
  /// Resting top inset / max downward travel of the spinner.
  double _maxVisualPull(BuildContext context) {
    if (widget.displacement > 0) {
      return widget.displacement;
    }
    return widget.indicatorPadding;
  }

  /// Current list pad (capped), used for reveal delay timing.
  double _currentPad(BuildContext context) {
    switch (_status) {
      case M3ERefreshStatus.drag:
      case M3ERefreshStatus.armed:
        return math.min(
          math.max(0, _dragOffset ?? 0.0),
          _resolvedContentDragOffset(context),
        );
      case M3ERefreshStatus.snap:
      case M3ERefreshStatus.refresh:
      case M3ERefreshStatus.done:
      case M3ERefreshStatus.canceled:
        return math.max(0, _contentPadController.value);
      case null:
        return 0;
    }
  }

  /// 0–1 progress after the pad passes twice the indicator padding.
  ///
  /// At 1 the spinner is fully scaled/faded and at its resting displacement.
  double _revealProgress(BuildContext context) {
    switch (_status) {
      case M3ERefreshStatus.refresh:
      case M3ERefreshStatus.snap:
        return 1;
      case M3ERefreshStatus.done:
        return _scaleFactor.value.clamp(0.0, 1.0);
      case M3ERefreshStatus.drag:
      case M3ERefreshStatus.armed:
      case M3ERefreshStatus.canceled:
        final double pad = _currentPad(context);
        final double delay = _revealDelayPx;
        final double maxPad = _resolvedContentDragOffset(context);
        final double extent = math.max(0, maxPad - delay);
        if (extent <= 0) {
          return pad > 0 ? 1.0 : 0.0;
        }
        return ((pad - delay) / extent).clamp(0.0, 1.0);
      case null:
        return 0;
    }
  }

  bool _isFullyRevealed(BuildContext context) =>
      _revealProgress(context) >= 1.0;

  /// Drag-driven spinner turns while pulling; auto spin once loading starts.
  double? _dragRotationTurns(BuildContext context) {
    switch (_status) {
      case M3ERefreshStatus.drag:
      case M3ERefreshStatus.armed:
        return math.max(0, _dragOffset ?? 0) / 80;
      case M3ERefreshStatus.snap:
      case M3ERefreshStatus.refresh:
      case M3ERefreshStatus.done:
      case M3ERefreshStatus.canceled:
      case null:
        return null;
    }
  }

  /// Indicator top inset: tracks reveal up to resting displacement, then locked.
  double _indicatorInset(BuildContext context) {
    switch (_status) {
      case M3ERefreshStatus.refresh:
      case M3ERefreshStatus.snap:
      case M3ERefreshStatus.done:
        return _restingInset ?? _maxVisualPull(context);
      case M3ERefreshStatus.drag:
      case M3ERefreshStatus.armed:
      case M3ERefreshStatus.canceled:
        return _revealProgress(context) * _maxVisualPull(context);
      case null:
        return 0;
    }
  }

  Widget _buildPositionedIndicator(BuildContext context) {
    if (kIsWeb) {
      return _buildPositionedIndicatorWeb(context);
    }

    final bool atTop = _isIndicatorAtTop!;
    final bool showIndeterminate =
        _status == M3ERefreshStatus.refresh || _status == M3ERefreshStatus.done;
    final double reveal = _revealProgress(context);
    final double inset = _indicatorInset(context);
    final double bubble = switch (_status) {
      M3ERefreshStatus.refresh ||
      M3ERefreshStatus.snap => _bubbleController.value,
      _ => 1.0,
    };

    return Positioned(
      top: atTop ? widget.edgeOffset + inset : null,
      bottom: atTop ? null : widget.edgeOffset + inset,
      left: 0,
      right: 0,
      child: IgnorePointer(
        child: Align(
          alignment: atTop ? Alignment.topCenter : Alignment.bottomCenter,
          child: Opacity(
            opacity: reveal.clamp(0.0, 1.0),
            child: Transform.scale(
              scale: reveal * bubble,
              alignment: atTop ? Alignment.topCenter : Alignment.bottomCenter,
              child: _buildIndicator(context, showIndeterminate),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildIndicator(BuildContext context, bool showIndeterminate) {
    switch (widget._indicatorType) {
      case _IndicatorType.expressive:
      case _IndicatorType.contained:
        return _buildContainedLoadingIndicator();
      case _IndicatorType.material:
        return _buildMaterialIndicator(context, showIndeterminate);
      case _IndicatorType.adaptive:
        return _buildAdaptiveIndicator(context, showIndeterminate);
      case _IndicatorType.noSpinner:
        return const SizedBox.shrink();
    }
  }

  /// Contained loading spinner for expressive and contained refresh kinds.
  Widget _buildContainedLoadingIndicator({Key? key, bool freezeMorph = false}) {
    final theme = M3ETheme.of(context);
    final indicator = M3ELoadingIndicator(
      key: key,
      variant: M3ELoadingIndicatorVariant.contained,
      color: _effectiveValueColor,
      containerColor: _effectiveContainerColor,
      polygons: widget.polygons,
      constraints: widget.indicatorConstraints,
      semanticLabel: widget.semanticsLabel,
      semanticValue: widget.semanticsValue,
      rotationTurns: freezeMorph ? 0 : _dragRotationTurns(context),
    );
    if (widget.elevation <= 0) {
      return indicator;
    }
    return DecoratedBox(
      decoration: ShapeDecoration(
        shape: theme.loadingIndicatorTheme.containerShape,
        shadows: M3EElevation.shadows(
          widget.elevation,
          shadowColor: theme.colorScheme.shadow,
        ),
      ),
      child: indicator,
    );
  }

  Widget _buildMaterialIndicator(BuildContext context, bool showIndeterminate) {
    return RefreshProgressIndicator(
      semanticsLabel:
          widget.semanticsLabel ??
          MaterialLocalizations.of(context).refreshIndicatorSemanticLabel,
      semanticsValue: widget.semanticsValue,
      value: showIndeterminate ? null : _value.value,
      valueColor: _valueColor,
      backgroundColor: widget.backgroundColor,
      strokeWidth: widget.strokeWidth,
      elevation: widget.elevation,
    );
  }

  Widget _buildAdaptiveIndicator(BuildContext context, bool showIndeterminate) {
    switch (M3ETheme.platformOf(context)) {
      case TargetPlatform.android:
      case TargetPlatform.fuchsia:
      case TargetPlatform.linux:
      case TargetPlatform.windows:
        return _buildMaterialIndicator(context, showIndeterminate);
      case TargetPlatform.iOS:
      case TargetPlatform.macOS:
        return CupertinoActivityIndicator(color: widget.color);
    }
  }
}
