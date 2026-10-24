import 'package:flutter/widgets.dart';

/// Color helpers shared across expressive components.
abstract final class M3EColorUtils {
  const M3EColorUtils._();

  /// Composites [foreground] over [background], flattening any transparency.
  ///
  /// Useful for tonal overlays where a translucent role color must be drawn on
  /// an opaque surface without relying on real compositing.
  static Color overlay(Color background, Color foreground) {
    final double fa = foreground.a;
    if (fa >= 1) {
      return foreground;
    }
    if (fa <= 0) {
      return background;
    }
    double blend(double b, double f) => f * fa + b * (1 - fa);
    return Color.from(
      alpha: 1,
      red: blend(background.r, foreground.r),
      green: blend(background.g, foreground.g),
      blue: blend(background.b, foreground.b),
    );
  }

  /// Applies [opacity] to [color] while preserving its existing alpha ratio.
  static Color withOpacity(Color color, double opacity) {
    return color.withValues(alpha: color.a * opacity);
  }
}
