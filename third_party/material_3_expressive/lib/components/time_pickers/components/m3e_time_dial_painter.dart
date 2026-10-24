import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../styles/m3e_time_picker_theme.dart';

/// Paints the clock dial: hour or minute labels, selection hand, and knob.
///
/// [handAngle] is continuous (radians, 0 = east, −π/2 = 12 o'clock) so minute
/// mode can land between the ×5 labels. [showSelectorDot] draws the Material
/// interstitial marker when the hand is not on a label.
class M3ETimeDialPainter extends CustomPainter {
  /// M3ETimeDialPainter.
  const M3ETimeDialPainter({
    required this.labels,
    required this.handAngle,
    required this.highlightedLabelIndex,
    required this.showSelectorDot,
    required this.dialColor,
    required this.accentColor,
    required this.onAccentColor,
    required this.labelColor,
    required this.labelStyle,
    required this.textDirection,
    required this.timeTheme,
  });

  /// Labels drawn evenly around the ring, starting at 12 o'clock.
  final List<String> labels;

  /// Continuous hand angle in radians (Flutter [Offset.fromDirection] space).
  final double handAngle;

  /// Label index under the knob when on a tick; null when between ticks.
  final int? highlightedLabelIndex;

  /// Whether to draw the inner selector dot (between minute labels).
  final bool showSelectorDot;

  /// dialColor.
  final Color dialColor;

  /// accentColor.
  final Color accentColor;

  /// onAccentColor.
  final Color onAccentColor;

  /// labelColor.
  final Color labelColor;

  /// labelStyle.
  final TextStyle labelStyle;

  /// textDirection.
  final TextDirection textDirection;

  /// timeTheme.
  final M3ETimePickerTheme timeTheme;

  @override
  void paint(Canvas canvas, Size size) {
    final Offset center = size.center(Offset.zero);
    final double radius = size.shortestSide / 2;
    final double knobRadius = timeTheme.dialKnobRadius;
    final double ringRadius = radius - knobRadius - timeTheme.dialRingInset;

    canvas.drawCircle(center, radius, Paint()..color = dialColor);

    final knob = center + Offset.fromDirection(handAngle, ringRadius);
    final accent = Paint()..color = accentColor;
    canvas
      ..drawLine(
        center,
        knob,
        Paint()
          ..color = accentColor
          ..strokeWidth = timeTheme.dialHandWidth,
      )
      ..drawCircle(center, timeTheme.dialCenterRadius, accent)
      ..drawCircle(knob, knobRadius, accent);

    if (showSelectorDot) {
      canvas.drawCircle(knob, 2, Paint()..color = onAccentColor);
    }

    for (var i = 0; i < labels.length; i++) {
      _paintLabel(canvas, center, ringRadius, i);
    }
  }

  void _paintLabel(Canvas canvas, Offset center, double ringRadius, int i) {
    final angle = _angleForIndex(i);
    final position = center + Offset.fromDirection(angle, ringRadius);
    final selected = highlightedLabelIndex == i;
    final painter = TextPainter(
      text: TextSpan(
        text: labels[i],
        style: labelStyle.copyWith(
          color: selected ? onAccentColor : labelColor,
          fontSize: timeTheme.dialLabelFontSize,
        ),
      ),
      textDirection: textDirection,
    )..layout();
    painter.paint(
      canvas,
      position - Offset(painter.width / 2, painter.height / 2),
    );
  }

  double _angleForIndex(int index) {
    final double step = 2 * math.pi / labels.length;
    return -math.pi / 2 + index * step;
  }

  @override
  bool shouldRepaint(M3ETimeDialPainter oldDelegate) {
    return oldDelegate.handAngle != handAngle ||
        oldDelegate.highlightedLabelIndex != highlightedLabelIndex ||
        oldDelegate.showSelectorDot != showSelectorDot ||
        oldDelegate.labels != labels ||
        oldDelegate.accentColor != accentColor ||
        oldDelegate.labelStyle != labelStyle ||
        oldDelegate.timeTheme != timeTheme;
  }
}
