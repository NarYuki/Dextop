import 'package:flutter/widgets.dart';

/// Standardized spacing and corner-radius tokens for Material 3 Expressive.
///
/// Spacing follows a 4dp grid. Radius steps match `M3EShapes`. Theme spacing
/// (`M3ESpacing.regular`) and `M3EShapes` radius fields forward here.
abstract final class M3EDimensions {
  const M3EDimensions._();

  /// 0dp.
  static const double space0 = 0;

  /// 4dp.
  static const double space4 = 4;

  /// 8dp.
  static const double space8 = 8;

  /// 12dp.
  static const double space12 = 12;

  /// 16dp.
  static const double space16 = 16;

  /// 20dp.
  static const double space20 = 20;

  /// 24dp.
  static const double space24 = 24;

  /// 28dp.
  static const double space28 = 28;

  /// 32dp.
  static const double space32 = 32;

  /// 40dp.
  static const double space40 = 40;

  /// 48dp.
  static const double space48 = 48;

  /// Theme extra-small gap (`M3ESpacing.xs`).
  static const double spaceXs = space4;

  /// Theme small gap (`M3ESpacing.sm`).
  static const double spaceSm = space8;

  /// Theme medium gap (`M3ESpacing.md`).
  static const double spaceMd = space12;

  /// Theme large gap (`M3ESpacing.lg`).
  static const double spaceLg = space16;

  /// Theme extra-large gap (`M3ESpacing.xl`).
  static const double spaceXl = space24;

  /// Theme extra-extra-large gap (`M3ESpacing.xxl`).
  static const double spaceXxl = space32;

  /// 0dp — sharp corners.
  static const double radiusNone = 0;

  /// 4dp corner radius.
  static const double radiusExtraSmall = 4;

  /// 8dp corner radius.
  static const double radiusSmall = 8;

  /// 12dp corner radius.
  static const double radiusMedium = 12;

  /// 16dp corner radius.
  static const double radiusLarge = 16;

  /// 20dp corner radius (expressive large+).
  static const double radiusLargeIncreased = 20;

  /// 28dp corner radius.
  static const double radiusExtraLarge = 28;

  /// 32dp corner radius (expressive XL+).
  static const double radiusExtraLargeIncreased = 32;

  /// 48dp corner radius.
  static const double radiusExtraExtraLarge = 48;

  /// Sentinel radius that reads as a stadium for typical heights.
  static const double radiusFull = 9999;

  /// [BorderRadius] for [radiusNone].
  static const BorderRadius borderRadiusNone = BorderRadius.zero;

  /// [BorderRadius] for [radiusExtraSmall].
  static const BorderRadius borderRadiusExtraSmall = BorderRadius.all(
    Radius.circular(radiusExtraSmall),
  );

  /// [BorderRadius] for [radiusSmall].
  static const BorderRadius borderRadiusSmall = BorderRadius.all(
    Radius.circular(radiusSmall),
  );

  /// [BorderRadius] for [radiusMedium].
  static const BorderRadius borderRadiusMedium = BorderRadius.all(
    Radius.circular(radiusMedium),
  );

  /// [BorderRadius] for [radiusLarge].
  static const BorderRadius borderRadiusLarge = BorderRadius.all(
    Radius.circular(radiusLarge),
  );

  /// [BorderRadius] for [radiusLargeIncreased].
  static const BorderRadius borderRadiusLargeIncreased = BorderRadius.all(
    Radius.circular(radiusLargeIncreased),
  );

  /// [BorderRadius] for [radiusExtraLarge].
  static const BorderRadius borderRadiusExtraLarge = BorderRadius.all(
    Radius.circular(radiusExtraLarge),
  );

  /// [BorderRadius] for [radiusExtraLargeIncreased].
  static const BorderRadius borderRadiusExtraLargeIncreased = BorderRadius.all(
    Radius.circular(radiusExtraLargeIncreased),
  );

  /// [BorderRadius] for [radiusExtraExtraLarge].
  static const BorderRadius borderRadiusExtraExtraLarge = BorderRadius.all(
    Radius.circular(radiusExtraExtraLarge),
  );

  /// Resolves a corner radius token to a [BorderRadius].
  static BorderRadius resolveRadius(double token) {
    return BorderRadius.all(Radius.circular(token));
  }
}
