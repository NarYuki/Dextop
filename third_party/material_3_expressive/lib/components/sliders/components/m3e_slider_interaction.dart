part of '../m3e_sliders.dart';

/// Value updates, keyboard navigation, and interaction-end handling for
/// [M3ESlider].
extension _M3ESliderInteraction on _M3ESliderState {
  void _update(double primary, double extent, bool reverse) {
    final double next = M3ESliderMath.valueFromOffset(
      localPrimary: primary,
      extent: extent,
      min: widget.min,
      max: widget.max,
      divisions: widget.divisions,
      reverse: reverse,
    );
    _setValue(next);
  }

  void _setValue(double raw) {
    if (!_enabled) {
      return;
    }
    final double next = widget.divisions != null
        ? M3ESliderMath.snap(
            raw.clamp(widget.min, widget.max),
            widget.min,
            widget.max,
            widget.divisions,
          )
        : raw.clamp(widget.min, widget.max);
    if (next == widget.value) {
      return;
    }
    if (widget.divisions != null) {
      if (widget.haptic != M3EHapticFeedback.none) {
        M3EHaptics.trigger(widget.haptic);
      }
    } else if (widget.haptic != M3EHapticFeedback.none && _dragging) {
      _maybeContinuousHaptic();
    }
    widget.onChanged!(next);
  }

  void _maybeContinuousHaptic() {
    if (!_hapticStopwatch.isRunning ||
        _hapticStopwatch.elapsedMilliseconds >= 60) {
      M3EHaptics.selection();
      _hapticStopwatch
        ..reset()
        ..start();
    }
  }

  KeyEventResult _handleKeyEvent(FocusNode node, KeyEvent event) {
    if (!_enabled || !M3ESliderMath.isNavigationKey(event.logicalKey)) {
      return KeyEventResult.ignored;
    }
    if (event is KeyUpEvent) {
      widget.onChangeEnd?.call(widget.value);
      return KeyEventResult.handled;
    }
    if (event is KeyDownEvent || event is KeyRepeatEvent) {
      final coarse = HardwareKeyboard.instance.logicalKeysPressed.contains(
        LogicalKeyboardKey.space,
      );
      final double step = M3ESliderMath.keyboardStep(
        widget.min,
        widget.max,
        widget.divisions,
        coarse: coarse,
      );
      final double? next = _keyboardDelta(event.logicalKey, step);
      if (next != null) {
        _setValue(next);
      }
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  double? _keyboardDelta(LogicalKeyboardKey key, double step) {
    final rtl = !_vertical && Directionality.of(context) == TextDirection.rtl;
    final page = M3ESliderMath.pageStep(step, widget.divisions);
    final verticalDownIncreases = _vertical && widget.topToBottom;
    switch (key) {
      case LogicalKeyboardKey.arrowRight:
        return widget.value + (rtl ? -step : step);
      case LogicalKeyboardKey.arrowLeft:
        return widget.value + (rtl ? step : -step);
      case LogicalKeyboardKey.arrowUp:
        return widget.value + (verticalDownIncreases ? -step : step);
      case LogicalKeyboardKey.arrowDown:
        return widget.value + (verticalDownIncreases ? step : -step);
      case LogicalKeyboardKey.pageUp:
        return widget.value + page;
      case LogicalKeyboardKey.pageDown:
        return widget.value - page;
      case LogicalKeyboardKey.home:
        return widget.min;
      case LogicalKeyboardKey.end:
        return widget.max;
      default:
        return null;
    }
  }

  void _endInteraction() {
    _dragging = false;
    if (_pressed) {
      setState(() => _pressed = false);
    }
    widget.onChangeEnd?.call(widget.value);
  }
}
