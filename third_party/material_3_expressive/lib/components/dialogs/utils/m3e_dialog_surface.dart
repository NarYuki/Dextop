import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// [color] with an optional elevation-scaled [tint] layer.
Color m3eDialogTinted(Color color, Color? tint, double elevation) {
  if (tint == null || elevation <= 0) {
    return color;
  }
  final double alpha = switch (elevation) {
    < M3EElevation.level2 => 0.05,
    < M3EElevation.level3 => 0.08,
    < M3EElevation.level4 => 0.11,
    < M3EElevation.level5 => 0.12,
    _ => 0.14,
  };
  return Color.alphaBlend(tint.withValues(alpha: alpha), color);
}
