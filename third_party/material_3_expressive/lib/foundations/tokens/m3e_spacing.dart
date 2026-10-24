import 'package:flutter/widgets.dart';

import 'm3e_dimensions.dart';

/// Material 3 Expressive spacing tokens.
///
/// Mirrors the spacing scale from the `m3e_design` package: a six-step scale
/// used for gaps, padding and run spacing across components.
@immutable
class M3ESpacing {
  /// Creates a custom spacing scale.
  const M3ESpacing({
    required this.xs,
    required this.sm,
    required this.md,
    required this.lg,
    required this.xl,
    required this.xxl,
  });

  /// The regular density spacing scale (4 / 8 / 12 / 16 / 24 / 32).
  const M3ESpacing.regular()
    : xs = M3EDimensions.spaceXs,
      sm = M3EDimensions.spaceSm,
      md = M3EDimensions.spaceMd,
      lg = M3EDimensions.spaceLg,
      xl = M3EDimensions.spaceXl,
      xxl = M3EDimensions.spaceXxl;

  /// 4dp.
  final double xs;

  /// 8dp.
  final double sm;

  /// 12dp.
  final double md;

  /// 16dp.
  final double lg;

  /// 24dp.
  final double xl;

  /// 32dp.
  final double xxl;

  /// Linearly interpolates between two spacing scales.
  static M3ESpacing lerp(M3ESpacing a, M3ESpacing b, double t) {
    double l(double x, double y) => x + (y - x) * t;
    return M3ESpacing(
      xs: l(a.xs, b.xs),
      sm: l(a.sm, b.sm),
      md: l(a.md, b.md),
      lg: l(a.lg, b.lg),
      xl: l(a.xl, b.xl),
      xxl: l(a.xxl, b.xxl),
    );
  }

  @override
  /// Equality based on public fields.
  bool operator ==(Object other) {
    return other is M3ESpacing &&
        other.xs == xs &&
        other.sm == sm &&
        other.md == md &&
        other.lg == lg &&
        other.xl == xl &&
        other.xxl == xxl;
  }

  @override
  /// Hash code for this spacing scale.
  int get hashCode => Object.hash(xs, sm, md, lg, xl, xxl);
}
