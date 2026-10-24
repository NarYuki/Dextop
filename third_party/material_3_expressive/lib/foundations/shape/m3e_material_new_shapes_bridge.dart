import 'dart:collection';

import 'package:material_new_shapes/material_new_shapes.dart';

export 'package:material_new_shapes/material_new_shapes.dart'
    show
        CornerRounding,
        Matrix4PointTransformer,
        Morph,
        MorphToPathExtension,
        RoundedPolygon,
        RoundedPolygonToPathExtension,
        pathFromCubics;

/// Foundation bridge to `material_new_shapes` expressive morph polygons.
abstract final class M3EMaterialNewShapes {
  const M3EMaterialNewShapes._();

  /// Circle morph polygon.
  static RoundedPolygon get circle => MaterialShapes.circle;

  /// Square morph polygon.
  static RoundedPolygon get square => MaterialShapes.square;

  /// Slanted morph polygon.
  static RoundedPolygon get slanted => MaterialShapes.slanted;

  /// Arch morph polygon.
  static RoundedPolygon get arch => MaterialShapes.arch;

  /// Semi-circle morph polygon.
  static RoundedPolygon get semiCircle => MaterialShapes.semiCircle;

  /// Oval morph polygon.
  static RoundedPolygon get oval => MaterialShapes.oval;

  /// Pill morph polygon.
  static RoundedPolygon get pill => MaterialShapes.pill;

  /// Triangle morph polygon.
  static RoundedPolygon get triangle => MaterialShapes.triangle;

  /// Arrow morph polygon.
  static RoundedPolygon get arrow => MaterialShapes.arrow;

  /// Fan morph polygon.
  static RoundedPolygon get fan => MaterialShapes.fan;

  /// Diamond morph polygon.
  static RoundedPolygon get diamond => MaterialShapes.diamond;

  /// Clam-shell morph polygon.
  static RoundedPolygon get clamShell => MaterialShapes.clamShell;

  /// Pentagon morph polygon.
  static RoundedPolygon get pentagon => MaterialShapes.pentagon;

  /// Gem morph polygon.
  static RoundedPolygon get gem => MaterialShapes.gem;

  /// Sunny morph polygon.
  static RoundedPolygon get sunny => MaterialShapes.sunny;

  /// Very-sunny morph polygon.
  static RoundedPolygon get verySunny => MaterialShapes.verySunny;

  /// Four-sided cookie morph polygon.
  static RoundedPolygon get cookie4Sided => MaterialShapes.cookie4Sided;

  /// Six-sided cookie morph polygon.
  static RoundedPolygon get cookie6Sided => MaterialShapes.cookie6Sided;

  /// Seven-sided cookie morph polygon.
  static RoundedPolygon get cookie7Sided => MaterialShapes.cookie7Sided;

  /// Nine-sided cookie morph polygon.
  static RoundedPolygon get cookie9Sided => MaterialShapes.cookie9Sided;

  /// Twelve-sided cookie morph polygon.
  static RoundedPolygon get cookie12Sided => MaterialShapes.cookie12Sided;

  /// Four-leaf clover morph polygon.
  static RoundedPolygon get clover4Leaf => MaterialShapes.clover4Leaf;

  /// Eight-leaf clover morph polygon.
  static RoundedPolygon get clover8Leaf => MaterialShapes.clover8Leaf;

  /// Burst morph polygon.
  static RoundedPolygon get burst => MaterialShapes.burst;

  /// Soft burst morph polygon.
  static RoundedPolygon get softBurst => MaterialShapes.softBurst;

  /// Boom morph polygon.
  static RoundedPolygon get boom => MaterialShapes.boom;

  /// Soft boom morph polygon.
  static RoundedPolygon get softBoom => MaterialShapes.softBoom;

  /// Flower morph polygon.
  static RoundedPolygon get flower => MaterialShapes.flower;

  /// Puffy morph polygon.
  static RoundedPolygon get puffy => MaterialShapes.puffy;

  /// Puffy diamond morph polygon.
  static RoundedPolygon get puffyDiamond => MaterialShapes.puffyDiamond;

  /// Ghostish morph polygon.
  static RoundedPolygon get ghostish => MaterialShapes.ghostish;

  /// Pixel circle morph polygon.
  static RoundedPolygon get pixelCircle => MaterialShapes.pixelCircle;

  /// Pixel triangle morph polygon.
  static RoundedPolygon get pixelTriangle => MaterialShapes.pixelTriangle;

  /// Bun morph polygon.
  static RoundedPolygon get bun => MaterialShapes.bun;

  /// Heart morph polygon.
  static RoundedPolygon get heart => MaterialShapes.heart;

  /// All built-in morph polygons from `material_new_shapes`.
  static UnmodifiableListView<RoundedPolygon> get all => MaterialShapes.all;
}
