import 'm3e_material_new_shapes_bridge.dart';

/// Catalog of Material 3 Expressive morph polygons for clipping and fill.
enum M3EShapeKind {
  /// Circle.
  circle,

  /// Rounded square.
  square,

  /// Slanted square.
  slanted,

  /// Arch.
  arch,

  /// Semi-circle.
  semiCircle,

  /// Oval.
  oval,

  /// Pill.
  pill,

  /// Triangle.
  triangle,

  /// Arrow.
  arrow,

  /// Fan.
  fan,

  /// Diamond.
  diamond,

  /// Clam shell.
  clamShell,

  /// Pentagon.
  pentagon,

  /// Gem.
  gem,

  /// Sunny.
  sunny,

  /// Very sunny.
  verySunny,

  /// Four-sided cookie.
  cookie4Sided,

  /// Six-sided cookie.
  cookie6Sided,

  /// Seven-sided cookie.
  cookie7Sided,

  /// Nine-sided cookie.
  cookie9Sided,

  /// Twelve-sided cookie.
  cookie12Sided,

  /// Four-leaf clover.
  clover4Leaf,

  /// Eight-leaf clover.
  clover8Leaf,

  /// Burst.
  burst,

  /// Soft burst.
  softBurst,

  /// Boom.
  boom,

  /// Soft boom.
  softBoom,

  /// Flower.
  flower,

  /// Puffy.
  puffy,

  /// Puffy diamond.
  puffyDiamond,

  /// Ghostish.
  ghostish,

  /// Pixel circle.
  pixelCircle,

  /// Pixel triangle.
  pixelTriangle,

  /// Bun.
  bun,

  /// Heart.
  heart;

  /// Every catalog entry, in declaration order.
  static List<M3EShapeKind> get all => values;

  /// Morph polygon for this kind.
  RoundedPolygon get polygon {
    return switch (this) {
      M3EShapeKind.circle => M3EMaterialNewShapes.circle,
      M3EShapeKind.square => M3EMaterialNewShapes.square,
      M3EShapeKind.slanted => M3EMaterialNewShapes.slanted,
      M3EShapeKind.arch => M3EMaterialNewShapes.arch,
      M3EShapeKind.semiCircle => M3EMaterialNewShapes.semiCircle,
      M3EShapeKind.oval => M3EMaterialNewShapes.oval,
      M3EShapeKind.pill => M3EMaterialNewShapes.pill,
      M3EShapeKind.triangle => M3EMaterialNewShapes.triangle,
      M3EShapeKind.arrow => M3EMaterialNewShapes.arrow,
      M3EShapeKind.fan => M3EMaterialNewShapes.fan,
      M3EShapeKind.diamond => M3EMaterialNewShapes.diamond,
      M3EShapeKind.clamShell => M3EMaterialNewShapes.clamShell,
      M3EShapeKind.pentagon => M3EMaterialNewShapes.pentagon,
      M3EShapeKind.gem => M3EMaterialNewShapes.gem,
      M3EShapeKind.sunny => M3EMaterialNewShapes.sunny,
      M3EShapeKind.verySunny => M3EMaterialNewShapes.verySunny,
      M3EShapeKind.cookie4Sided => M3EMaterialNewShapes.cookie4Sided,
      M3EShapeKind.cookie6Sided => M3EMaterialNewShapes.cookie6Sided,
      M3EShapeKind.cookie7Sided => M3EMaterialNewShapes.cookie7Sided,
      M3EShapeKind.cookie9Sided => M3EMaterialNewShapes.cookie9Sided,
      M3EShapeKind.cookie12Sided => M3EMaterialNewShapes.cookie12Sided,
      M3EShapeKind.clover4Leaf => M3EMaterialNewShapes.clover4Leaf,
      M3EShapeKind.clover8Leaf => M3EMaterialNewShapes.clover8Leaf,
      M3EShapeKind.burst => M3EMaterialNewShapes.burst,
      M3EShapeKind.softBurst => M3EMaterialNewShapes.softBurst,
      M3EShapeKind.boom => M3EMaterialNewShapes.boom,
      M3EShapeKind.softBoom => M3EMaterialNewShapes.softBoom,
      M3EShapeKind.flower => M3EMaterialNewShapes.flower,
      M3EShapeKind.puffy => M3EMaterialNewShapes.puffy,
      M3EShapeKind.puffyDiamond => M3EMaterialNewShapes.puffyDiamond,
      M3EShapeKind.ghostish => M3EMaterialNewShapes.ghostish,
      M3EShapeKind.pixelCircle => M3EMaterialNewShapes.pixelCircle,
      M3EShapeKind.pixelTriangle => M3EMaterialNewShapes.pixelTriangle,
      M3EShapeKind.bun => M3EMaterialNewShapes.bun,
      M3EShapeKind.heart => M3EMaterialNewShapes.heart,
    };
  }
}
