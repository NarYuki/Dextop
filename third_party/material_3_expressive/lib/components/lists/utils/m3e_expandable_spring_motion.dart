import 'package:material_3_expressive/components/dropdown_menus/utils/m3e_dropdown_spring_motion.dart'
    show M3EDropdownSpringMotion;
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';

/// Converts [M3ESpring] to a motor [SpringMotion] for expandable animations.
///
/// Matches [M3EDropdownSpringMotion] so expand/collapse feels the same as the
/// dropdown menu panel.
extension M3EExpandableSpringMotion on M3ESpring {
  /// toMotion.
  SpringMotion toMotion() =>
      const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: stiffness,
        damping: damping,
      );
}
