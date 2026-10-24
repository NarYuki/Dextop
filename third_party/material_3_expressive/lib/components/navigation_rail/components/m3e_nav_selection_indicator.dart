import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';

/// Per-destination selection indicator.
///
/// Width scales from 0.4 to 1 with [scaleSpring]. The selected pill is opaque
/// immediately so the destination change is not waiting on a fade-in.
/// Leaving a destination fades with [fadeSpring]. A resting unselected
/// indicator is fully hidden. The child is only the indicator shape; icons
/// and labels stay outside this widget.
class M3ESelectionIndicator extends StatefulWidget {
  /// M3ESelectionIndicator.
  const M3ESelectionIndicator({
    required this.selected,
    required this.child,
    this.scaleSpring = M3EMotion.expressiveSpatialDefault,
    this.fadeSpring = M3EMotion.effectsFast,
    super.key,
  });

  /// Whether this destination is selected.
  final bool selected;

  /// Indicator shape. It is scaled and faded; it is not the icon or label.
  final Widget child;

  /// Spatial spring for the width scale.
  final M3ESpring scaleSpring;

  /// Effects spring for the fade.
  final M3ESpring fadeSpring;

  @override
  State<M3ESelectionIndicator> createState() => _M3ESelectionIndicatorState();
}

class _M3ESelectionIndicatorState extends State<M3ESelectionIndicator>
    with TickerProviderStateMixin {
  static const double _restScale = 0.4;

  late final SingleMotionController _scale;
  late final SingleMotionController _fade;

  SpringMotion _scaleMotion(M3ESpring spring) =>
      const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: spring.stiffness,
        damping: spring.damping,
      );

  SpringMotion _fadeMotion(M3ESpring spring) =>
      const MaterialSpringMotion.expressiveEffectsFast().copyWith(
        stiffness: spring.stiffness,
        damping: spring.damping,
      );

  @override
  void initState() {
    super.initState();
    final bool selected = widget.selected;
    _scale = SingleMotionController(
      motion: _scaleMotion(widget.scaleSpring),
      vsync: this,
      initialValue: selected ? 1 : 0,
    );
    _fade = SingleMotionController(
      motion: _fadeMotion(widget.fadeSpring),
      vsync: this,
      initialValue: selected ? 1 : 0,
    );
    _scale.addStatusListener(_onSettled);
    _fade.addStatusListener(_onSettled);
  }

  @override
  void didUpdateWidget(covariant M3ESelectionIndicator oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.scaleSpring != widget.scaleSpring) {
      _scale.motion = _scaleMotion(widget.scaleSpring);
    }
    if (oldWidget.fadeSpring != widget.fadeSpring) {
      _fade.motion = _fadeMotion(widget.fadeSpring);
    }
    if (oldWidget.selected == widget.selected) {
      return;
    }
    if (widget.selected) {
      if (_scale.value < _restScale) {
        _scale.value = _restScale;
      }
      // Show the pill on this frame. Width still springs from the rest scale.
      _fade.value = 1;
      _scale.animateTo(1);
      return;
    }
    if (_scale.value <= 0) {
      _fade.value = 0;
      return;
    }
    _scale.animateTo(_restScale);
    _fade.animateTo(0);
  }

  void _onSettled(AnimationStatus status) {
    if (widget.selected || _scale.isAnimating || _fade.isAnimating) {
      return;
    }
    if (_fade.value > 0.001 || _scale.value == 0) {
      return;
    }
    _scale.value = 0;
  }

  @override
  void dispose() {
    _scale
      ..removeStatusListener(_onSettled)
      ..dispose();
    _fade
      ..removeStatusListener(_onSettled)
      ..dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge(<Listenable>[_scale, _fade]),
      builder: (BuildContext context, Widget? child) {
        final double scale = _scale.value <= 0 ? 0 : _scale.value;
        final double opacity = _fade.value.clamp(0, 1);
        return Opacity(
          opacity: opacity,
          child: Transform(
            alignment: Alignment.center,
            transform: Matrix4.diagonal3Values(scale, 1, 1),
            child: child,
          ),
        );
      },
      child: widget.child,
    );
  }
}
