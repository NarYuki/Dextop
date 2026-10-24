import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import 'm3e_side_sheet_spring.dart';

/// Springs [controller] to [target], or jumps there when animations are off.
void m3eSideSheetAnimate(
  BuildContext context,
  AnimationController controller,
  M3ESpring spring,
  double target,
) {
  final bool reduce = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
  if (reduce) {
    controller.value = target;
    return;
  }
  controller.animateWith(
    m3eSideSheetSpring(
      spring,
      from: controller.value,
      to: target,
      velocity: controller.velocity,
    ),
  );
}
