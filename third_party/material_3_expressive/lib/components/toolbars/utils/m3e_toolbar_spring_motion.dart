import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';

/// Converts [M3ESpring] to a motor [SpringMotion] for toolbar animations.
extension M3EToolbarSpringMotion on M3ESpring {
  /// Matches m3e_core floating toolbar: expressive spatial base + token values.
  SpringMotion toMotion() => const MaterialSpringMotion.expressiveSpatialFast()
      .copyWith(stiffness: stiffness, damping: damping);
}
