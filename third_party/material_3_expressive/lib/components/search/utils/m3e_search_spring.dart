import 'package:flutter/animation.dart';
import 'package:flutter/physics.dart';

import '../../../foundations/foundations.dart';

const Tolerance _settleTolerance = Tolerance(velocity: 0.01);
const Duration _maxSettle = Duration(seconds: 2);

/// Spring simulation from [from] to [to] used by search motion.
SpringSimulation m3eSearchSpringSimulation(
  M3ESpring spring, {
  double from = 0,
  double to = 1,
  double velocity = 0,
}) {
  return SpringSimulation(
    spring.toDescription(),
    from,
    to,
    velocity,
    tolerance: _settleTolerance,
    snapToEnd: true,
  );
}

/// Time a 0→1 [spring] needs to settle (capped at 2s).
Duration m3eSearchSpringSettle(M3ESpring spring) {
  final SpringSimulation sim = m3eSearchSpringSimulation(spring);
  var ms = 0;
  while (ms < _maxSettle.inMilliseconds && !sim.isDone(ms / 1000)) {
    ms += 4;
  }
  return Duration(milliseconds: ms);
}

/// [spring] sampled as a [Curve] over its settle time.
Curve m3eSearchSpringCurve(M3ESpring spring) => _M3ESearchSpringCurve(spring);

class _M3ESearchSpringCurve extends Curve {
  _M3ESearchSpringCurve(M3ESpring spring)
    : _sim = m3eSearchSpringSimulation(spring),
      _seconds = m3eSearchSpringSettle(spring).inMicroseconds / 1e6;

  final SpringSimulation _sim;
  final double _seconds;

  @override
  double transformInternal(double t) => _sim.x(t * _seconds);
}
