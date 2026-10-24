import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// Inset keyboard focus ring for a list row.
///
/// The stroke sits inside the row. [inset] is the spec's negative offset
/// expressed as a positive distance from the outer edge to the stroke.
class M3EListFocusRing extends StatelessWidget {
  /// Creates an inset list focus ring.
  const M3EListFocusRing({
    required this.radius,
    required this.child,
    required this.color,
    this.focused = false,
    this.thickness = 3,
    this.inset = 3,
    super.key,
  });

  /// Corner radius of the row.
  final BorderRadius radius;

  /// Row content.
  final Widget child;

  /// Ring color.
  final Color color;

  /// Whether the ring is visible.
  final bool focused;

  /// Stroke width.
  final double thickness;

  /// Distance from the outer edge to the outer edge of the stroke.
  final double inset;

  BorderRadius _insetRadius(double shrink) {
    Radius corner(Radius value) =>
        Radius.circular((value.x - shrink).clamp(0, double.infinity));
    return BorderRadius.only(
      topLeft: corner(radius.topLeft),
      topRight: corner(radius.topRight),
      bottomLeft: corner(radius.bottomLeft),
      bottomRight: corner(radius.bottomRight),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: M3EFocusInteraction.instance,
      builder: (BuildContext context, Widget? _) {
        final bool show = focused && M3EFocusInteraction.instance.ringsAllowed;
        final double pathInset = inset + thickness / 2;
        return Stack(
          fit: StackFit.passthrough,
          children: <Widget>[
            child,
            if (show)
              Positioned.fill(
                child: IgnorePointer(
                  child: Padding(
                    padding: EdgeInsets.all(pathInset),
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        borderRadius: _insetRadius(pathInset),
                        border: Border.all(color: color, width: thickness),
                      ),
                    ),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}
