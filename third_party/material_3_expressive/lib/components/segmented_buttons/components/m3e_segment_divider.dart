import 'package:flutter/widgets.dart';

import '../../buttons/utils/m3e_button_gradient_layer.dart';

/// Vertical divider between two segments.
///
/// A [gradient] is sampled across the box of [hostKey] (the segment row) so
/// every divider lines up with the group's gradient instead of each one
/// stretching the full ramp over its own hairline width.
class M3ESegmentDivider extends StatelessWidget {
  /// M3ESegmentDivider.
  const M3ESegmentDivider({
    required this.hostKey,
    required this.width,
    this.color,
    this.gradient,
    super.key,
  });

  /// Segment row that defines the gradient's coordinate space.
  final GlobalKey hostKey;

  /// Divider thickness.
  final double width;

  /// Solid color. Used when [gradient] is null.
  final Color? color;

  /// Gradient sampled across the segment row.
  final Gradient? gradient;

  @override
  Widget build(BuildContext context) {
    // A childless Container stretches to the row height and stays safe under
    // unbounded constraints.
    if (gradient == null) {
      return Container(width: width, color: color);
    }
    return CustomPaint(
      painter: M3ESegmentDividerPainter(
        hostKey: hostKey,
        context: context,
        gradient: gradient!,
      ),
      child: Container(width: width),
    );
  }
}

/// Fills the divider with a gradient sampled in the segment row's space.
class M3ESegmentDividerPainter extends CustomPainter {
  /// M3ESegmentDividerPainter.
  const M3ESegmentDividerPainter({
    required this.hostKey,
    required this.context,
    required this.gradient,
  });

  /// Segment row that defines the gradient's coordinate space.
  final GlobalKey hostKey;

  /// Context of the painted box, used to map into the row.
  final BuildContext context;

  /// Divider gradient.
  final Gradient gradient;

  @override
  void paint(Canvas canvas, Size size) {
    final Rect local = Offset.zero & size;
    canvas.drawRect(
      local,
      Paint()
        ..shader = gradient.createShader(
          m3eGradientSpanRect(hostKey: hostKey, context: context, local: local),
        ),
    );
  }

  @override
  bool shouldRepaint(M3ESegmentDividerPainter oldDelegate) {
    return oldDelegate.gradient != gradient || oldDelegate.hostKey != hostKey;
  }
}
