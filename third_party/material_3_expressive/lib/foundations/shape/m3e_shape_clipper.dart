import 'package:flutter/widgets.dart';

import 'm3e_material_new_shapes_bridge.dart';
import 'm3e_shape_kind.dart';

/// Clips a child to a catalog [M3EShapeKind] polygon, fitted to the clip size.
class M3EShapeClipper extends CustomClipper<Path> {
  /// Creates a clipper for [kind].
  const M3EShapeClipper(this.kind);

  /// Shape to clip to.
  final M3EShapeKind kind;

  @override
  Path getClip(Size size) {
    if (size.isEmpty) {
      return Path();
    }
    final source = kind.polygon.toPath();
    final bounds = source.getBounds();
    if (bounds.isEmpty) {
      return Path();
    }
    final matrix = Matrix4.identity()
      ..scaleByDouble(
        size.width / bounds.width,
        size.height / bounds.height,
        1,
        1,
      )
      ..translateByDouble(-bounds.left, -bounds.top, 0, 1);
    return source.transform(matrix.storage);
  }

  @override
  bool shouldReclip(covariant M3EShapeClipper oldClipper) {
    return oldClipper.kind != kind;
  }
}
