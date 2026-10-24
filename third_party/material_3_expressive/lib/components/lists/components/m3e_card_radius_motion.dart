import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_list_theme.dart';

/// Springs a card's [BorderRadius] with the expressive spatial motion.
class M3ECardRadiusMotion extends StatefulWidget {
  /// M3ECardRadiusMotion.
  const M3ECardRadiusMotion({
    required this.radius,
    required this.builder,
    this.snap = false,
    this.motion,
    super.key,
  });

  /// Target corner radii.
  final BorderRadius radius;

  /// When true, the target is applied immediately (e.g. while dragging).
  final bool snap;

  /// Override spring; defaults to [M3EListCardListTheme.radiusSpring].
  final M3ESpring? motion;

  /// Builds the card with the animated radius.
  final Widget Function(BuildContext context, BorderRadius radius) builder;

  @override
  State<M3ECardRadiusMotion> createState() => _M3ECardRadiusMotionState();
}

class _M3ECardRadiusMotionState extends State<M3ECardRadiusMotion>
    with TickerProviderStateMixin {
  late final SingleMotionController _controller;
  late BorderRadius _from;
  late BorderRadius _to;

  SpringMotion _toMotion(M3ESpring spring) =>
      const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: spring.stiffness,
        damping: spring.damping,
      );

  SpringMotion get _motion {
    final spring =
        widget.motion ??
        (mounted
            ? M3ETheme.of(context).listTheme.cardList.radiusSpring
            : M3EListCardListTheme.defaults.radiusSpring);
    return _toMotion(spring);
  }

  @override
  void initState() {
    super.initState();
    _from = widget.radius;
    _to = widget.radius;
    _controller = SingleMotionController(
      motion: _toMotion(
        widget.motion ?? M3EListCardListTheme.defaults.radiusSpring,
      ),
      vsync: this,
      initialValue: 1,
    );
  }

  @override
  void didUpdateWidget(covariant M3ECardRadiusMotion oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.snap) {
      _from = widget.radius;
      _to = widget.radius;
      _controller.value = 1;
      return;
    }
    if (oldWidget.radius == widget.radius &&
        oldWidget.motion == widget.motion) {
      return;
    }
    _from = BorderRadius.lerp(_from, _to, _controller.value) ?? _to;
    _to = widget.radius;
    _controller
      ..motion = _motion
      ..value = 0
      ..animateTo(1);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.snap) {
      return widget.builder(context, widget.radius);
    }
    return AnimatedBuilder(
      animation: _controller,
      builder: (BuildContext context, Widget? _) {
        final BorderRadius radius =
            BorderRadius.lerp(_from, _to, _controller.value) ?? _to;
        return widget.builder(context, radius);
      },
    );
  }
}
