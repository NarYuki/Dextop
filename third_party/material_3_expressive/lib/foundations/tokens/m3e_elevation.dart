import 'package:flutter/widgets.dart';

/// Material 3 elevation tokens expressed in logical pixels (dp).
abstract final class M3EElevation {
  const M3EElevation._();

  /// Flat surface with no elevation shadow.
  static const double level0 = 0;

  /// Lowest raised surface (1dp).
  static const double level1 = 1;

  /// Low raised surface (3dp).
  static const double level2 = 3;

  /// Medium raised surface (6dp).
  static const double level3 = 6;

  /// High raised surface (8dp).
  static const double level4 = 8;

  /// Highest raised surface (12dp).
  static const double level5 = 12;

  /// Builds the layered key + ambient shadows Material uses for [elevation].
  ///
  /// Returns an empty list at level 0 so no shadow is painted.
  static List<BoxShadow> shadows(double elevation, {Color? shadowColor}) {
    if (elevation <= 0) {
      return const <BoxShadow>[];
    }
    final Color color = shadowColor ?? const Color(0xFF000000);
    return <BoxShadow>[
      BoxShadow(
        color: color.withValues(alpha: 0.3),
        offset: Offset(0, elevation * 0.5),
        blurRadius: elevation,
        spreadRadius: -elevation * 0.5,
      ),
      BoxShadow(
        color: color.withValues(alpha: 0.15),
        offset: Offset(0, elevation),
        blurRadius: elevation * 2,
      ),
    ];
  }
}
