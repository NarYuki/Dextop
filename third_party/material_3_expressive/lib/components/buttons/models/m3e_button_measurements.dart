import 'package:flutter/foundation.dart';

import '../enums/m3e_button_enums.dart';

/// Resolved layout measurements for a button size variant.
@immutable
class M3EButtonMeasurements {
  /// M3EButtonMeasurements.
  const M3EButtonMeasurements({
    required this.height,
    required this.hPadding,
    required this.iconSize,
    required this.iconGap,
    this.outlineWidth = 1,
  });

  /// height.
  final double height;

  /// hPadding.
  final double hPadding;

  /// iconSize.
  final double iconSize;

  /// iconGap.
  final double iconGap;

  /// Outline stroke width for outlined buttons (dp).
  final double outlineWidth;

  /// applyCustomSize.

  M3EButtonMeasurements applyCustomSize(M3EButtonSize? custom) {
    if (custom == null) {
      return this;
    }
    return M3EButtonMeasurements(
      height: custom.height ?? height,
      hPadding: custom.hPadding ?? hPadding,
      iconSize: custom.iconSize ?? iconSize,
      iconGap: custom.iconGap ?? iconGap,
      outlineWidth: custom.outlineWidth ?? outlineWidth,
    );
  }
}
