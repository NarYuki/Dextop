import 'package:flutter/widgets.dart';

import 'm3e_shape_clipper.dart';
import 'm3e_shape_kind.dart';

/// A box clipped to an [M3EShapeKind] polygon.
class M3EShapeContainer extends StatelessWidget {
  /// Creates a shaped container.
  const M3EShapeContainer({
    super.key,
    required this.kind,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  });

  /// Circle.
  const M3EShapeContainer.circle({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.circle;

  /// Rounded square.
  const M3EShapeContainer.square({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.square;

  /// Slanted square.
  const M3EShapeContainer.slanted({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.slanted;

  /// Arch.
  const M3EShapeContainer.arch({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.arch;

  /// Semi-circle.
  const M3EShapeContainer.semiCircle({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.semiCircle;

  /// Oval.
  const M3EShapeContainer.oval({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.oval;

  /// Pill.
  const M3EShapeContainer.pill({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.pill;

  /// Triangle.
  const M3EShapeContainer.triangle({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.triangle;

  /// Arrow.
  const M3EShapeContainer.arrow({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.arrow;

  /// Fan.
  const M3EShapeContainer.fan({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.fan;

  /// Diamond.
  const M3EShapeContainer.diamond({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.diamond;

  /// Clam shell.
  const M3EShapeContainer.clamShell({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.clamShell;

  /// Pentagon.
  const M3EShapeContainer.pentagon({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.pentagon;

  /// Gem.
  const M3EShapeContainer.gem({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.gem;

  /// Sunny.
  const M3EShapeContainer.sunny({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.sunny;

  /// Very sunny.
  const M3EShapeContainer.verySunny({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.verySunny;

  /// Four-sided cookie.
  const M3EShapeContainer.cookie4Sided({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.cookie4Sided;

  /// Six-sided cookie.
  const M3EShapeContainer.cookie6Sided({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.cookie6Sided;

  /// Seven-sided cookie.
  const M3EShapeContainer.cookie7Sided({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.cookie7Sided;

  /// Nine-sided cookie.
  const M3EShapeContainer.cookie9Sided({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.cookie9Sided;

  /// Twelve-sided cookie.
  const M3EShapeContainer.cookie12Sided({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.cookie12Sided;

  /// Four-leaf clover.
  const M3EShapeContainer.clover4Leaf({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.clover4Leaf;

  /// Eight-leaf clover.
  const M3EShapeContainer.clover8Leaf({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.clover8Leaf;

  /// Burst.
  const M3EShapeContainer.burst({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.burst;

  /// Soft burst.
  const M3EShapeContainer.softBurst({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.softBurst;

  /// Boom.
  const M3EShapeContainer.boom({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.boom;

  /// Soft boom.
  const M3EShapeContainer.softBoom({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.softBoom;

  /// Flower.
  const M3EShapeContainer.flower({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.flower;

  /// Puffy.
  const M3EShapeContainer.puffy({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.puffy;

  /// Puffy diamond.
  const M3EShapeContainer.puffyDiamond({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.puffyDiamond;

  /// Ghostish.
  const M3EShapeContainer.ghostish({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.ghostish;

  /// Pixel circle.
  const M3EShapeContainer.pixelCircle({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.pixelCircle;

  /// Pixel triangle.
  const M3EShapeContainer.pixelTriangle({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.pixelTriangle;

  /// Bun.
  const M3EShapeContainer.bun({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.bun;

  /// Heart.
  const M3EShapeContainer.heart({
    super.key,
    this.child,
    this.width,
    this.height,
    this.color,
    this.gradient,
    this.clipBehavior = Clip.antiAlias,
  }) : kind = M3EShapeKind.heart;

  /// Polygon kind.
  final M3EShapeKind kind;

  /// Optional content inside the clip.
  final Widget? child;

  /// Explicit width.
  final double? width;

  /// Explicit height.
  final double? height;

  /// Fill color (ignored when [gradient] is set, except as a fallback).
  final Color? color;

  /// Fill gradient. Overrides [color] when both are set.
  final Gradient? gradient;

  /// Clip quality.
  final Clip clipBehavior;

  @override
  Widget build(BuildContext context) {
    Widget content = child ?? const SizedBox.expand();
    if (gradient != null) {
      content = DecoratedBox(
        decoration: BoxDecoration(color: color, gradient: gradient),
        child: content,
      );
    } else if (color != null) {
      content = ColoredBox(color: color!, child: content);
    }
    if (width != null || height != null) {
      content = SizedBox(width: width, height: height, child: content);
    }
    return ClipPath(
      clipper: M3EShapeClipper(kind),
      clipBehavior: clipBehavior,
      child: content,
    );
  }
}
