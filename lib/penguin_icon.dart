import 'package:flutter/material.dart';

/// A Tux-like penguin silhouette for the Linux destination.
///
/// Drawn on a 24-unit grid like Material icons. [filled] draws the solid
/// silhouette used for the selected destination; otherwise an outlined body
/// with a solid belly cut-out, matching the outlined/rounded icon pair the
/// navigation bar uses for its other destinations.
class PenguinIcon extends StatelessWidget {
  const PenguinIcon({super.key, this.filled = false, this.size, this.color});

  final bool filled;
  final double? size;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final iconTheme = IconTheme.of(context);
    final dimension = size ?? iconTheme.size ?? 24;
    return SizedBox.square(
      dimension: dimension,
      child: CustomPaint(
        painter: _PenguinPainter(
          color: color ?? iconTheme.color ?? Colors.black,
          filled: filled,
        ),
      ),
    );
  }
}

class _PenguinPainter extends CustomPainter {
  _PenguinPainter({required this.color, required this.filled});

  final Color color;
  final bool filled;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.scale(size.width / 24, size.height / 24);
    final body = Path()
      // Head and body: a rounded pear shape.
      ..moveTo(12, 2.2)
      ..cubicTo(9.4, 2.2, 8.2, 4.2, 8.2, 6.6)
      ..cubicTo(8.2, 8.1, 7.6, 9.2, 6.6, 10.6)
      ..cubicTo(5.4, 12.3, 4.6, 14.2, 4.6, 16.2)
      ..cubicTo(4.6, 18.4, 6.2, 20, 8.4, 20.4)
      ..lineTo(15.6, 20.4)
      ..cubicTo(17.8, 20, 19.4, 18.4, 19.4, 16.2)
      ..cubicTo(19.4, 14.2, 18.6, 12.3, 17.4, 10.6)
      ..cubicTo(16.4, 9.2, 15.8, 8.1, 15.8, 6.6)
      ..cubicTo(15.8, 4.2, 14.6, 2.2, 12, 2.2)
      ..close();
    final belly = Path()
      ..moveTo(12, 8.6)
      ..cubicTo(10, 8.6, 8.2, 11.8, 8.2, 14.8)
      ..cubicTo(8.2, 17, 9.8, 18.6, 12, 18.6)
      ..cubicTo(14.2, 18.6, 15.8, 17, 15.8, 14.8)
      ..cubicTo(15.8, 11.8, 14, 8.6, 12, 8.6)
      ..close();
    final feet = Path()
      ..addOval(const Rect.fromLTWH(5.2, 19, 5.6, 2.8))
      ..addOval(const Rect.fromLTWH(13.2, 19, 5.6, 2.8));
    final fill = Paint()
      ..color = color
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;

    if (filled) {
      // Solid silhouette with the belly, eyes and beak cut out.
      final cutouts = Path()
        ..addPath(belly, Offset.zero)
        ..addOval(Rect.fromCircle(center: const Offset(10.6, 5.6), radius: .85))
        ..addOval(Rect.fromCircle(center: const Offset(13.4, 5.6), radius: .85))
        ..addPath(_beak(), Offset.zero);
      canvas.drawPath(
        Path.combine(PathOperation.difference, body, cutouts),
        fill,
      );
      canvas.drawPath(feet, fill);
      return;
    }
    final stroke = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.6
      ..strokeJoin = StrokeJoin.round
      ..isAntiAlias = true;
    canvas.drawPath(body, stroke);
    canvas.drawCircle(const Offset(10.6, 5.6), .8, fill);
    canvas.drawCircle(const Offset(13.4, 5.6), .8, fill);
    canvas.drawPath(_beak(), fill);
    canvas.drawPath(feet, fill);
  }

  Path _beak() => Path()
    ..moveTo(10.8, 7.2)
    ..quadraticBezierTo(12, 6.5, 13.2, 7.2)
    ..quadraticBezierTo(12, 8.6, 10.8, 7.2)
    ..close();

  @override
  bool shouldRepaint(_PenguinPainter oldDelegate) =>
      oldDelegate.color != color || oldDelegate.filled != filled;
}
