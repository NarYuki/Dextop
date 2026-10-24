import 'package:flutter/physics.dart';

import '../../../foundations/foundations.dart';

const Tolerance _settleTolerance = Tolerance(velocity: 0.01);
const Duration _maxSettle = Duration(seconds: 2);

/// Spring simulation from [from] to [to] for side sheet motion.
SpringSimulation m3eSideSheetSpring(
  M3ESpring spring, {
  required double from,
  required double to,
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
Duration m3eSideSheetSpringSettle(M3ESpring spring) {
  final SpringSimulation sim = m3eSideSheetSpring(spring, from: 0, to: 1);
  var ms = 0;
  while (ms < _maxSettle.inMilliseconds && !sim.isDone(ms / 1000)) {
    ms += 4;
  }
  return Duration(milliseconds: ms);
}
