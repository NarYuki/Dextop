import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';

/// Converts [M3ESpring] to a motor [SpringMotion] for dropdown animations.
extension M3EDropdownSpringMotion on M3ESpring {
  /// toMotion.
  SpringMotion toMotion() =>
      const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: stiffness,
        damping: damping,
      );
}
