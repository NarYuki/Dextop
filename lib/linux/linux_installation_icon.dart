import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_installations.dart';

/// Keeps the existing rounded square, with a contour-following Expressive track.
class LinuxInstallationIcon extends StatelessWidget {
  const LinuxInstallationIcon({
    super.key,
    required this.installation,
    required this.child,
  });
  final AppInstallation? installation;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final reduced = MediaQuery.disableAnimationsOf(context);
    return SizedBox.square(
      dimension: 56,
      child: TweenAnimationBuilder<double>(
        tween: Tween(end: installation == null ? 0 : 1),
        duration: reduced ? Duration.zero : const Duration(milliseconds: 500),
        curve: M3EMotion.emphasized,
        child: child,
        builder: (context, amount, child) {
          final saturation = 1 - amount;
          final r = .2126 * amount, g = .7152 * amount, b = .0722 * amount;
          return Stack(
            alignment: Alignment.center,
            children: [
              Transform.scale(
                scale: 1 - .22 * amount,
                child: ColorFiltered(
                  colorFilter: ColorFilter.matrix([
                    r + saturation,
                    g,
                    b,
                    0,
                    0,
                    r,
                    g + saturation,
                    b,
                    0,
                    0,
                    r,
                    g,
                    b + saturation,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                  ]),
                  child: child,
                ),
              ),
              if (amount > 0)
                Positioned.fill(
                  child: IgnorePointer(
                    child: TweenAnimationBuilder<double>(
                      tween: Tween(
                        end: installation == null
                            ? 1
                            : installation!.percent.clamp(0, 100) / 100,
                      ),
                      duration: reduced
                          ? Duration.zero
                          : const Duration(milliseconds: 350),
                      curve: M3EMotion.emphasizedDecelerate,
                      builder: (context, progress, _) => CustomPaint(
                        painter: _InstallContour(
                          opacity: amount,
                          queued: installation?.queued ?? false,
                          progress: progress,
                          color: scheme.primary,
                          track: scheme.outlineVariant,
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _InstallContour extends CustomPainter {
  _InstallContour({
    required this.opacity,
    required this.queued,
    required this.progress,
    required this.color,
    required this.track,
  });
  final double opacity;
  final bool queued;
  final double progress;
  final Color color, track;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).deflate(3);
    final base = Path()
      ..addRRect(
        RRect.fromRectAndRadius(rect, Radius.circular(size.width * .3)),
      );
    final path = base;
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.5
      ..strokeCap = StrokeCap.round
      ..color = track.withValues(alpha: opacity * .55);
    canvas.drawPath(path, paint);
    if (queued) return;
    paint.color = color.withValues(alpha: opacity);
    final outline = path.computeMetrics().first;
    canvas.drawPath(outline.extractPath(0, progress * outline.length), paint);
  }

  @override
  bool shouldRepaint(covariant _InstallContour old) =>
      old.opacity != opacity ||
      old.queued != queued ||
      old.progress != progress ||
      old.color != color ||
      old.track != track;
}
