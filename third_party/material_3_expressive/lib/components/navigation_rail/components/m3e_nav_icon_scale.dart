import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_navigation_rail_theme.dart';

/// Subtle scale pop on the newly selected icon (≈1.0 → 1.08 → 1.0).
class M3ENavIconScale extends StatefulWidget {
  /// M3ENavIconScale.
  const M3ENavIconScale({
    required this.selected,
    required this.child,
    super.key,
  });

  /// selected.

  final bool selected;

  /// child.
  final Widget child;

  @override
  State<M3ENavIconScale> createState() => _M3ENavIconScaleState();
}

class _M3ENavIconScaleState extends State<M3ENavIconScale>
    with SingleTickerProviderStateMixin {
  late SingleMotionController _scale;

  SpringMotion _springMotion(M3ESpring spring) =>
      const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: spring.stiffness,
        damping: spring.damping,
      );

  SpringMotion get _motion =>
      _springMotion(M3ETheme.of(context).navigationRailTheme.iconScaleSpring);

  @override
  void initState() {
    super.initState();
    _scale = SingleMotionController(
      motion: _springMotion(M3ENavigationRailTheme.defaults.iconScaleSpring),
      vsync: this,
      initialValue: 1,
    );
  }

  @override
  void didUpdateWidget(covariant M3ENavIconScale oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!oldWidget.selected && widget.selected) {
      // Overshoot spring from slightly under 1 — no shrink-first dip.
      _scale
        ..motion = _motion
        ..value = 0.98
        ..animateTo(1);
    }
  }

  @override
  void dispose() {
    _scale.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _scale,
      builder: (BuildContext context, Widget? child) {
        return Transform.scale(scale: _scale.value, child: child);
      },
      child: widget.child,
    );
  }
}
