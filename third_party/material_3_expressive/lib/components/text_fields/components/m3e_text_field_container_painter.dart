import 'package:flutter/widgets.dart';

/// Paints the `M3ETextField` container, hover layer and stroke.
///
/// Filled draws a bottom active indicator. Outlined draws a full outline with
/// a top-edge notch for the floating label. Strokes paint inside the bounds, so
/// focus never changes layout.
class M3ETextFieldContainerPainter extends CustomPainter {
  /// Creates the container painter.
  const M3ETextFieldContainerPainter({
    required this.outlined,
    required this.shape,
    required this.containerColor,
    required this.stateLayerColor,
    required this.strokeColor,
    required this.strokeWidth,
    required this.textDirection,
    this.notchStart = 0,
    this.notchWidth = 0,
  });

  /// Whether to draw the outlined variant.
  final bool outlined;

  /// Container corners.
  final BorderRadius shape;

  /// Container fill.
  final Color containerColor;

  /// Hover state layer.
  final Color stateLayerColor;

  /// Indicator or outline color.
  final Color strokeColor;

  /// Indicator height or outline width.
  final double strokeWidth;

  /// Resolves [notchStart] for RTL.
  final TextDirection textDirection;

  /// Notch start from the leading edge.
  final double notchStart;

  /// Current notch width; it opens from its center.
  final double notchWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final RRect rrect = shape.toRRect(Offset.zero & size);
    if (containerColor.a > 0) {
      canvas.drawRRect(rrect, Paint()..color = containerColor);
    }
    if (stateLayerColor.a > 0) {
      canvas.drawRRect(rrect, Paint()..color = stateLayerColor);
    }
    if (strokeWidth <= 0) {
      return;
    }
    if (outlined) {
      _paintOutline(canvas, size);
    } else {
      canvas.drawRect(
        Rect.fromLTRB(0, size.height - strokeWidth, size.width, size.height),
        Paint()..color = strokeColor,
      );
    }
  }

  void _paintOutline(Canvas canvas, Size size) {
    final Rect bounds = Offset.zero & size;
    final stroke = Paint()
      ..color = strokeColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth;
    final RRect outline = shape.toRRect(bounds.deflate(strokeWidth / 2));
    if (notchWidth <= 0) {
      canvas.drawRRect(outline, stroke);
      return;
    }
    canvas
      ..saveLayer(bounds.inflate(strokeWidth), Paint())
      ..drawRRect(outline, stroke)
      ..drawRect(_notchRect(size), Paint()..blendMode = BlendMode.clear)
      ..restore();
  }

  Rect _notchRect(Size size) {
    final double fullStart = textDirection == TextDirection.rtl
        ? size.width - notchStart - notchWidth
        : notchStart;
    return Rect.fromLTWH(fullStart, -1, notchWidth, strokeWidth + 2);
  }

  @override
  bool shouldRepaint(M3ETextFieldContainerPainter oldDelegate) {
    return oldDelegate.outlined != outlined ||
        oldDelegate.shape != shape ||
        oldDelegate.containerColor != containerColor ||
        oldDelegate.stateLayerColor != stateLayerColor ||
        oldDelegate.strokeColor != strokeColor ||
        oldDelegate.strokeWidth != strokeWidth ||
        oldDelegate.textDirection != textDirection ||
        oldDelegate.notchStart != notchStart ||
        oldDelegate.notchWidth != notchWidth;
  }
}
