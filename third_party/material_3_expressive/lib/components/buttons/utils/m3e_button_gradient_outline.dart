import 'package:flutter/widgets.dart';

/// Paints a stroke around [child] using [clipRadius].
Widget m3eGradientOutlineLayer({
  required BorderRadius clipRadius,
  required double width,
  required Widget child,
  Gradient? gradient,
  Color? color,
}) {
  if (width <= 0 || (gradient == null && color == null)) {
    return child;
  }
  return CustomPaint(
    foregroundPainter: M3EGradientOutlinePainter(
      gradient: gradient,
      color: color,
      radius: clipRadius,
      width: width,
    ),
    child: child,
  );
}

/// Stroke painter for a rounded-rect outline (solid or gradient).
class M3EGradientOutlinePainter extends CustomPainter {
  /// Creates an outline painter.
  const M3EGradientOutlinePainter({
    required this.radius,
    required this.width,
    this.gradient,
    this.color,
  });

  /// Stroke shader. Wins over [color] when both are set.
  final Gradient? gradient;

  /// Solid stroke color.
  final Color? color;

  /// Corner radii matching the morphing surface.
  final BorderRadius radius;

  /// Stroke width.
  final double width;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final rrect = radius.toRRect(rect).deflate(width / 2);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = width;
    if (gradient != null) {
      paint.shader = gradient!.createShader(rect);
    } else if (color != null) {
      paint.color = color!;
    } else {
      return;
    }
    canvas.drawRRect(rrect, paint);
  }

  @override
  bool shouldRepaint(M3EGradientOutlinePainter oldDelegate) {
    return oldDelegate.gradient != gradient ||
        oldDelegate.color != color ||
        oldDelegate.radius != radius ||
        oldDelegate.width != width;
  }
}

/// Width from [side], or [fallback] when [side] is null or none.
double m3eOutlineWidth(BorderSide? side, {double fallback = 1}) {
  if (side == null || side.style == BorderStyle.none) {
    return fallback;
  }
  return side.width;
}
