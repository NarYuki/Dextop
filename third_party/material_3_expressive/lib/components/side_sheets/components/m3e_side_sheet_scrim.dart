import 'package:flutter/widgets.dart';

/// Scrim behind a modal side sheet.
class M3ESideSheetScrim extends StatelessWidget {
  /// M3ESideSheetScrim.
  const M3ESideSheetScrim({
    required this.color,
    required this.progress,
    required this.label,
    this.onTap,
    super.key,
  });

  /// Full scrim color, opacity included.
  final Color color;

  /// Fade progress (0–1).
  final double progress;

  /// Semantics label when tappable.
  final String label;

  /// Dismisses the sheet. Null blocks taps without dismissing.
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final double t = progress.clamp(0, 1).toDouble();
    return Semantics(
      label: onTap != null ? label : null,
      onTap: onTap,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: ColoredBox(color: color.withValues(alpha: color.a * t)),
      ),
    );
  }
}
