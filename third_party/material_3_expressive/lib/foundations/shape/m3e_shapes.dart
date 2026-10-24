import 'package:flutter/widgets.dart';

import '../tokens/m3e_dimensions.dart';

/// Material 3 Expressive shape (corner radius) tokens.
///
/// The expressive shape scale extends the baseline scale with the
/// `largeIncreased`, `extraLargeIncreased` and `extraExtraLarge` steps used by
/// the new large components (FAB menu, toolbars, expressive buttons).
abstract final class M3EShapes {
  const M3EShapes._();

  /// 0dp — sharp corners.
  static const double none = M3EDimensions.radiusNone;

  /// 4dp corner radius.
  static const double extraSmall = M3EDimensions.radiusExtraSmall;

  /// 8dp corner radius.
  static const double small = M3EDimensions.radiusSmall;

  /// 12dp corner radius.
  static const double medium = M3EDimensions.radiusMedium;

  /// 16dp corner radius.
  static const double large = M3EDimensions.radiusLarge;

  /// 20dp corner radius (expressive large+).
  static const double largeIncreased = M3EDimensions.radiusLargeIncreased;

  /// 28dp corner radius.
  static const double extraLarge = M3EDimensions.radiusExtraLarge;

  /// 32dp corner radius (expressive XL+).
  static const double extraLargeIncreased =
      M3EDimensions.radiusExtraLargeIncreased;

  /// 48dp corner radius.
  static const double extraExtraLarge = M3EDimensions.radiusExtraExtraLarge;

  /// Sentinel radius that resolves to a fully rounded (stadium) shape.
  static const double full = M3EDimensions.radiusFull;

  /// [BorderRadius] for [none].
  static const BorderRadius radiusNone = M3EDimensions.borderRadiusNone;

  /// [BorderRadius] for [extraSmall].
  static const BorderRadius radiusExtraSmall =
      M3EDimensions.borderRadiusExtraSmall;

  /// [BorderRadius] for [small].
  static const BorderRadius radiusSmall = M3EDimensions.borderRadiusSmall;

  /// [BorderRadius] for [medium].
  static const BorderRadius radiusMedium = M3EDimensions.borderRadiusMedium;

  /// [BorderRadius] for [large].
  static const BorderRadius radiusLarge = M3EDimensions.borderRadiusLarge;

  /// [BorderRadius] for [largeIncreased].
  static const BorderRadius radiusLargeIncreased =
      M3EDimensions.borderRadiusLargeIncreased;

  /// [BorderRadius] for [extraLarge].
  static const BorderRadius radiusExtraLarge =
      M3EDimensions.borderRadiusExtraLarge;

  /// [BorderRadius] for [extraLargeIncreased].
  static const BorderRadius radiusExtraLargeIncreased =
      M3EDimensions.borderRadiusExtraLargeIncreased;

  /// [BorderRadius] for [extraExtraLarge].
  static const BorderRadius radiusExtraExtraLarge =
      M3EDimensions.borderRadiusExtraExtraLarge;

  /// Fully rounded stadium shape border.
  static const StadiumBorder stadium = StadiumBorder();

  /// Resolves a corner radius token to a [BorderRadius].
  ///
  /// The [full] sentinel produces a very large radius that reads as a stadium
  /// for any realistic component height.
  static BorderRadius resolve(double token) {
    return M3EDimensions.resolveRadius(token);
  }

  /// The expressive *round* shape set (mirrors `m3e_design`'s round family).
  static const M3EShapeSet roundSet = M3EShapeSet(
    xs: BorderRadius.all(Radius.circular(999)),
    sm: BorderRadius.all(Radius.circular(20)),
    md: BorderRadius.all(Radius.circular(28)),
    lg: BorderRadius.all(Radius.circular(44)),
    xl: BorderRadius.all(Radius.circular(64)),
  );

  /// The expressive *square* shape set (mirrors `m3e_design`'s square family).
  static const M3EShapeSet squareSet = M3EShapeSet(
    xs: BorderRadius.all(Radius.circular(6)),
    sm: BorderRadius.all(Radius.circular(8)),
    md: BorderRadius.all(Radius.circular(12)),
    lg: BorderRadius.all(Radius.circular(16)),
    xl: BorderRadius.all(Radius.circular(20)),
  );
}

/// A five-step [BorderRadius] scale for a single shape family.
///
/// Mirrors the `M3EShapeSet` from the `m3e_design` package.
@immutable
class M3EShapeSet {
  /// Creates a five-step shape set.
  const M3EShapeSet({
    required this.xs,
    required this.sm,
    required this.md,
    required this.lg,
    required this.xl,
  });

  /// Extra-small corner radius in this family.
  final BorderRadius xs;

  /// Small corner radius in this family.
  final BorderRadius sm;

  /// Medium corner radius in this family.
  final BorderRadius md;

  /// Large corner radius in this family.
  final BorderRadius lg;

  /// Extra-large corner radius in this family.
  final BorderRadius xl;
}
