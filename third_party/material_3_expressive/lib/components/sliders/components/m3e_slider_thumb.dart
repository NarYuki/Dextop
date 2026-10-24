import 'dart:math' as math;

import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/sliders/m3e_sliders.dart'
    show M3ERangeSlider, M3ESlider;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ERangeSlider, M3ESlider;
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../res/m3e_slider_tokens.dart';
import '../styles/m3e_slider_theme.dart';

/// Expressive bar handle for [M3ESlider] / [M3ERangeSlider].
///
/// The short axis springs from 4 to 2 while [pressed] or [focused]. Hover
/// keeps the resting width and does not paint a fill.
class M3ESliderThumb extends StatefulWidget {
  /// M3ESliderThumb.
  const M3ESliderThumb({
    required this.color,
    required this.pressed,
    this.focused = false,
    this.hovered = false,
    this.overlap = false,
    this.overlapColor,
    this.axis = Axis.horizontal,
    this.width,
    this.height,
    this.pressedThickness,
    super.key,
  });

  /// color.

  final Color color;

  /// pressed.
  final bool pressed;

  /// Whether keyboard focus chrome is showing.
  final bool focused;

  /// Whether the pointer is over the slider.
  final bool hovered;

  /// Whether this range handle overlaps the other handle.
  final bool overlap;

  /// Outline color used when [overlap] is true.
  final Color? overlapColor;

  /// axis.
  final Axis axis;

  /// Resting thumb width (cross-axis for vertical). Defaults to token sizes.
  final double? width;

  /// Resting thumb height (main-axis for vertical). Defaults to token sizes.
  final double? height;

  /// Pressed and focused thickness along the short axis.
  final double? pressedThickness;

  @override
  State<M3ESliderThumb> createState() => _M3ESliderThumbState();
}

class _M3ESliderThumbState extends State<M3ESliderThumb>
    with SingleTickerProviderStateMixin {
  late final SingleMotionController _short;

  @override
  void initState() {
    super.initState();
    _short = SingleMotionController(motion: _motion(), vsync: this)
      ..value = _target;
    _short.addListener(_onTick);
  }

  @override
  void didUpdateWidget(M3ESliderThumb oldWidget) {
    super.didUpdateWidget(oldWidget);
    final next = _target;
    if ((_short.value - next).abs() < 0.01) {
      return;
    }
    _short.motion = _motion();
    _short.animateTo(next);
  }

  @override
  void dispose() {
    _short.removeListener(_onTick);
    _short.dispose();
    super.dispose();
  }

  void _onTick() {
    if (mounted) {
      setState(() {});
    }
  }

  SpringMotion _motion() {
    final spring = M3ESliderTheme.defaults.dockSpring;
    return const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
      stiffness: spring.stiffness,
      damping: spring.damping,
    );
  }

  double get _target {
    final vertical = widget.axis == Axis.vertical;
    final resting = vertical
        ? (widget.height ?? M3ESliderTokens.verticalHandleHeight)
        : (widget.width ?? M3ESliderTokens.handleWidth);
    if (widget.pressed || widget.focused) {
      return widget.pressedThickness ?? M3ESliderTokens.pressedHandleWidth;
    }
    return resting;
  }

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final sliderTheme = theme.sliderTheme;
    final vertical = widget.axis == Axis.vertical;
    final long = vertical
        ? (widget.width ?? M3ESliderTokens.verticalHandleWidth)
        : (widget.height ?? M3ESliderTokens.handleHeight);
    final short = _short.value;
    final w = vertical ? long : short;
    final h = vertical ? short : long;
    final radius = math.max(w, h) / 2;
    final outline = widget.overlap
        ? Border.all(
            color: widget.overlapColor ?? theme.colorScheme.onPrimary,
            width: sliderTheme.overlapOutlineWidth,
          )
        : null;
    return Container(
      width: w,
      height: h,
      decoration: BoxDecoration(
        color: widget.color,
        borderRadius: BorderRadius.circular(radius),
        border: outline,
      ),
    );
  }
}
