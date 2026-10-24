import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';

/// Converts [M3ESpring] to a motor [SpringMotion] for menu animations.
///
/// Identical to the dropdown menu conversion so expand/collapse feel matches.
extension M3EMenuSpringMotion on M3ESpring {
  /// toMotion.
  SpringMotion toMotion() =>
      const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: stiffness,
        damping: damping,
      );
}
