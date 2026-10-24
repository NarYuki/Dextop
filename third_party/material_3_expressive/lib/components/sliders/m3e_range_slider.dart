import 'dart:math' as math;

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/sliders/m3e_sliders.dart'
    show M3ESlider;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ESlider;

import '../../foundations/foundations.dart';
import 'components/m3e_range_slider_track.dart';
import 'components/m3e_slider_dot_overlay.dart';
import 'components/m3e_slider_thumb.dart';
import 'components/m3e_slider_value_indicator.dart';
import 'enums/m3e_slider_enums.dart';
import 'models/m3e_slider_dot_builder.dart';
import 'models/m3e_slider_range.dart';
import 'models/m3e_slider_range_labels.dart';
import 'models/m3e_slider_track_icons.dart';
import 'styles/m3e_slider_theme.dart';
import 'utils/m3e_slider_math.dart';

part 'components/m3e_range_slider_build.dart';

/// Which thumb is being dragged on an [M3ERangeSlider].
enum _M3ERangeThumb { start, end }

/// A Material 3 Expressive range slider (Compose `RangeSlider`).
///
/// Horizontal only — Compose has no vertical range slider.
class M3ERangeSlider extends StatefulWidget {
  /// M3ERangeSlider.
  const M3ERangeSlider({
    required this.values,
    required this.onChanged,
    this.min = 0,
    this.max = 1,
    this.divisions,
    this.onChangeEnd,
    this.labels,
    this.semanticFormatterCallback,
    this.trackIcons,
    this.trackThickness,
    this.cornerRadius,
    this.thumbLength,
    this.dotSize,
    this.dotSpacing,
    this.dotBuilder,
    this.enabled = true,
    this.focusNode,
    this.autofocus = false,
    this.haptic = M3EHapticFeedback.none,
    this.size = M3ESliderSize.xs,
    this.semanticLabel,
    super.key,
  }) : wavy = false,
       amplitude = null,
       amplitudeForProgress = null,
       wavelength = null,
       waveSpeed = null,
       assert(max > min, 'max must be greater than min.');

  /// Range slider whose active span is a traveling sine wave.
  ///
  /// Inactive track, thumbs, gaps, and interaction match [M3ERangeSlider];
  /// only the active value span uses the linear-wavy progress recipe.
  const M3ERangeSlider.wavy({
    required this.values,
    required this.onChanged,
    this.min = 0,
    this.max = 1,
    this.divisions,
    this.onChangeEnd,
    this.labels,
    this.semanticFormatterCallback,
    this.trackIcons,
    this.trackThickness,
    this.cornerRadius,
    this.thumbLength,
    this.dotSize,
    this.dotSpacing,
    this.dotBuilder,
    this.amplitude,
    this.amplitudeForProgress,
    this.wavelength,
    this.waveSpeed,
    this.enabled = true,
    this.focusNode,
    this.autofocus = false,
    this.haptic = M3EHapticFeedback.none,
    this.size = M3ESliderSize.xs,
    this.semanticLabel,
    super.key,
  }) : wavy = true,
       assert(max > min, 'max must be greater than min.');

  /// Current start/end values within [min]..[max].
  final M3ESliderRange values;

  /// Called when either thumb moves. Null disables the slider.
  final ValueChanged<M3ESliderRange>? onChanged;

  /// min.

  final double min;

  /// max.
  final double max;

  /// divisions.
  final int? divisions;

  /// onChangeEnd.
  final ValueChanged<M3ESliderRange>? onChangeEnd;

  /// Optional start/end value-indicator labels.
  final M3ESliderRangeLabels? labels;

  /// Function.

  final String Function(M3ESliderRange values)? semanticFormatterCallback;

  /// Reserved for parity with [M3ESlider.trackIcons] (Compose sample pattern).
  final M3ESliderTrackIcons? trackIcons;

  /// When true, paints the active span as a traveling sine wave.
  final bool wavy;

  /// Fixed amplitude factor `0..1` for [wavy] tracks.
  final double? amplitude;

  /// Amplitude factor as a function of span progress for [wavy] tracks.
  final double Function(double progress)? amplitudeForProgress;

  /// Wave length in logical pixels ([wavy] only).
  final double? wavelength;

  /// Wave travel speed in logical pixels per second ([wavy] only).
  final double? waveSpeed;

  /// Thickness of both active and inactive tracks. Defaults to theme track height.
  final double? trackThickness;

  /// Outer corner radius for active and inactive track ends.
  ///
  /// Defaults to [M3ESliderTheme.trackCornerRadius] (fixed; not based on thickness).
  final double? cornerRadius;

  /// Length of each thumb along its long axis. Defaults to theme handle height.
  final double? thumbLength;

  /// Diameter of stop/tick markers. Defaults to theme stop indicator size.
  final double? dotSize;

  /// Clear space between each track end and the outer edge of the end dots.
  ///
  /// Defaults to theme [M3ESliderTheme.stopIndicatorTrailingSpace].
  final double? dotSpacing;

  /// Custom stop/tick markers. When null, default circular dots are painted.
  final M3ESliderDotBuilder? dotBuilder;

  /// Whether the slider responds to user interaction.
  ///
  /// The slider is also disabled when [onChanged] is null.
  final bool enabled;

  /// Focus node for keyboard/traditional focus.
  final FocusNode? focusNode;

  /// Whether this slider should be focused initially.
  final bool autofocus;

  /// Haptic feedback intensity fired on discrete value changes.
  final M3EHapticFeedback haptic;

  /// Spec size. XS uses the ambient [M3ESliderTheme] geometry.
  final M3ESliderSize size;

  /// Accessibility name. Matches the adjacent text label when set.
  final String? semanticLabel;

  @override
  State<M3ERangeSlider> createState() => _M3ERangeSliderState();
}

class _M3ERangeSliderState extends State<M3ERangeSlider>
    with TickerProviderStateMixin {
  _M3ERangeThumb? _activeThumb;
  bool _dragging = false;
  bool _hovered = false;
  bool _isFocusedFromPointer = false;
  bool _ownsFocusNode = false;
  late FocusNode _focusNode;
  late final AnimationController _waveController;
  final Stopwatch _hapticStopwatch = Stopwatch();

  bool get _enabled => widget.enabled && widget.onChanged != null;
  bool get _pressed => _activeThumb != null;

  /// Thumb keyboard input targets: the last active thumb, or
  /// [_M3ERangeThumb.end] by default.
  _M3ERangeThumb get _keyboardThumb => _activeThumb ?? _M3ERangeThumb.end;

  bool get _showFocusOutline {
    if (!M3ETheme.of(context).keyboardFocusIndicators) {
      return false;
    }
    if (!_focusNode.hasFocus) {
      return false;
    }
    if (!M3EFocusInteraction.instance.ringsAllowed) {
      return false;
    }
    if (_isFocusedFromPointer) {
      return false;
    }
    return FocusManager.instance.highlightMode ==
        FocusHighlightMode.traditional;
  }

  double get _startFraction =>
      M3ESliderMath.fraction(widget.values.start, widget.min, widget.max);
  double get _endFraction =>
      M3ESliderMath.fraction(widget.values.end, widget.min, widget.max);

  List<double> get _ticks => M3ESliderMath.tickFractions(widget.divisions);

  @override
  void initState() {
    super.initState();
    _attachFocusNode(widget.focusNode);
    M3EFocusInteraction.instance.addListener(_handleFocusInteraction);
    _waveController = AnimationController(
      vsync: this,
      duration: M3EMotion.extraLong2,
    );
    if (widget.wavy) {
      _waveController.repeat();
    }
  }

  void _attachFocusNode(FocusNode? external) {
    _focusNode = external ?? FocusNode();
    _ownsFocusNode = external == null;
    _focusNode.addListener(_handleFocusChange);
  }

  void _detachFocusNode() {
    _focusNode.removeListener(_handleFocusChange);
    if (_ownsFocusNode) {
      _focusNode.dispose();
    }
  }

  void _handleFocusChange() {
    if (!_focusNode.hasFocus) {
      _isFocusedFromPointer = false;
    }
    setState(() {});
  }

  void _handleFocusInteraction() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  void didUpdateWidget(M3ERangeSlider oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.focusNode != oldWidget.focusNode) {
      _detachFocusNode();
      _attachFocusNode(widget.focusNode);
    }
    if (widget.wavy) {
      if (!_waveController.isAnimating) {
        _waveController.repeat();
      }
    } else if (_waveController.isAnimating) {
      _waveController.stop();
    }
  }

  @override
  void dispose() {
    M3EFocusInteraction.instance.removeListener(_handleFocusInteraction);
    _detachFocusNode();
    _waveController.dispose();
    super.dispose();
  }

  double _phase(double wavelength, double waveSpeed) {
    final Duration elapsed =
        _waveController.lastElapsedDuration ?? Duration.zero;
    final double seconds = elapsed.inMicroseconds / 1e6;
    if (wavelength <= 0) {
      return 0;
    }
    return seconds * waveSpeed / wavelength * 2 * math.pi;
  }

  double _amplitudeFactor(M3ESliderTheme theme) {
    final double progress = (_endFraction - _startFraction).clamp(0.0, 1.0);
    if (widget.amplitudeForProgress != null) {
      return widget.amplitudeForProgress!(progress).clamp(0.0, 1.0);
    }
    if (widget.amplitude != null) {
      return widget.amplitude!.clamp(0.0, 1.0);
    }
    return theme.amplitudeForProgress(progress).clamp(0.0, 1.0);
  }

  @override
  Widget build(BuildContext context) {
    final _M3ERangeSliderResolved resolved = _resolve(context);
    return M3EComponentTheme(
      builder: (BuildContext context) {
        return Semantics(
          slider: true,
          label: widget.semanticLabel,
          enabled: _enabled,
          value:
              widget.semanticFormatterCallback?.call(widget.values) ??
              '${widget.values.start} – ${widget.values.end}',
          child: LayoutBuilder(
            builder: (BuildContext context, BoxConstraints constraints) {
              return _buildLayout(context, constraints, resolved);
            },
          ),
        );
      },
    );
  }

  String _indicatorLabel() {
    if (widget.labels != null) {
      return _activeThumb == _M3ERangeThumb.start
          ? widget.labels!.start
          : widget.labels!.end;
    }
    final double v = _activeThumb == _M3ERangeThumb.start
        ? widget.values.start
        : widget.values.end;
    return widget.divisions != null
        ? v.round().toString()
        : v.toStringAsFixed(2);
  }

  void _update(double dx, double width, bool rtl) {
    if (_activeThumb == null) {
      return;
    }
    final double next = M3ESliderMath.valueFromOffset(
      localPrimary: dx,
      extent: width,
      min: widget.min,
      max: widget.max,
      divisions: widget.divisions,
      reverse: rtl,
    );
    _setThumbValue(_activeThumb!, next);
  }

  void _setThumbValue(_M3ERangeThumb thumb, double raw) {
    if (!_enabled) {
      return;
    }
    final double clampedRaw = thumb == _M3ERangeThumb.start
        ? raw.clamp(widget.min, widget.values.end)
        : raw.clamp(widget.values.start, widget.max);
    final double next = widget.divisions != null
        ? M3ESliderMath.snap(
            clampedRaw,
            widget.min,
            widget.max,
            widget.divisions,
          )
        : clampedRaw;
    final updated = thumb == _M3ERangeThumb.start
        ? M3ESliderRange(next, widget.values.end)
        : M3ESliderRange(widget.values.start, next);
    if (updated == widget.values) {
      return;
    }
    if (widget.divisions != null) {
      if (widget.haptic != M3EHapticFeedback.none) {
        M3EHaptics.trigger(widget.haptic);
      }
    } else if (widget.haptic != M3EHapticFeedback.none && _dragging) {
      _maybeContinuousHaptic();
    }
    widget.onChanged!(updated);
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
      widget.onChangeEnd?.call(widget.values);
      return KeyEventResult.handled;
    }
    if (event is KeyDownEvent || event is KeyRepeatEvent) {
      final _M3ERangeThumb thumb = _keyboardThumb;
      if (_activeThumb == null) {
        setState(() => _activeThumb = thumb);
      }
      final coarse = HardwareKeyboard.instance.logicalKeysPressed.contains(
        LogicalKeyboardKey.space,
      );
      final double step = M3ESliderMath.keyboardStep(
        widget.min,
        widget.max,
        widget.divisions,
        coarse: coarse,
      );
      final double current = thumb == _M3ERangeThumb.start
          ? widget.values.start
          : widget.values.end;
      final double? next = _keyboardDelta(event.logicalKey, current, step);
      if (next != null) {
        _setThumbValue(thumb, next);
      }
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  double? _keyboardDelta(LogicalKeyboardKey key, double current, double step) {
    final reverse = Directionality.of(context) == TextDirection.rtl;
    final increase = reverse
        ? LogicalKeyboardKey.arrowLeft
        : LogicalKeyboardKey.arrowRight;
    final decrease = reverse
        ? LogicalKeyboardKey.arrowRight
        : LogicalKeyboardKey.arrowLeft;
    final page = M3ESliderMath.pageStep(step, widget.divisions);
    switch (key) {
      case LogicalKeyboardKey.arrowRight:
      case LogicalKeyboardKey.arrowLeft:
        if (key == increase) {
          return current + step;
        }
        if (key == decrease) {
          return current - step;
        }
        return null;
      case LogicalKeyboardKey.arrowUp:
        return current + step;
      case LogicalKeyboardKey.arrowDown:
        return current - step;
      case LogicalKeyboardKey.pageUp:
        return current + page;
      case LogicalKeyboardKey.pageDown:
        return current - page;
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
    if (_activeThumb != null) {
      setState(() => _activeThumb = null);
    }
    widget.onChangeEnd?.call(widget.values);
  }
}
