part of '../m3e_split_buttons.dart';

/// Fill and/or outline using the split row as the gradient's coordinate space.
class _M3ESplitSharedGradientLayer extends StatelessWidget {
  const _M3ESplitSharedGradientLayer({
    required this.hostKey,
    required this.radius,
    required this.child,
    this.fill,
    this.outline,
    this.outlineWidth = 1,
    this.disabled = false,
  });

  final GlobalKey hostKey;
  final BorderRadius radius;
  final Gradient? fill;
  final Gradient? outline;
  final double outlineWidth;
  final bool disabled;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (fill == null && outline == null) {
      return child;
    }
    return CustomPaint(
      painter: fill == null
          ? null
          : _M3ESplitSharedGradientPainter(
              hostKey: hostKey,
              context: context,
              radius: radius,
              fill: fill,
              disabled: disabled,
            ),
      foregroundPainter: outline == null || outlineWidth <= 0
          ? null
          : _M3ESplitSharedGradientPainter(
              hostKey: hostKey,
              context: context,
              radius: radius,
              outline: outline,
              outlineWidth: outlineWidth,
            ),
      child: child,
    );
  }
}

class _M3ESplitSharedGradientPainter extends CustomPainter {
  const _M3ESplitSharedGradientPainter({
    required this.hostKey,
    required this.context,
    required this.radius,
    this.fill,
    this.outline,
    this.outlineWidth = 1,
    this.disabled = false,
  });

  final GlobalKey hostKey;
  final BuildContext context;
  final BorderRadius radius;
  final Gradient? fill;
  final Gradient? outline;
  final double outlineWidth;
  final bool disabled;

  @override
  void paint(Canvas canvas, Size size) {
    final Rect local = Offset.zero & size;
    final Rect shaderRect = m3eGradientSpanRect(
      hostKey: hostKey,
      context: context,
      local: local,
    );
    final RRect rrect = radius.toRRect(local);
    if (fill != null) {
      final paint = Paint()..shader = fill!.createShader(shaderRect);
      if (disabled) {
        canvas.saveLayer(
          local,
          Paint()
            ..color = const Color.fromRGBO(
              255,
              255,
              255,
              M3EButtonConstants.kDisabledBackgroundAlpha,
            ),
        );
      }
      canvas.drawRRect(rrect, paint);
      if (disabled) {
        canvas.restore();
      }
    }
    if (outline != null && outlineWidth > 0) {
      canvas.drawRRect(
        rrect.deflate(outlineWidth / 2),
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = outlineWidth
          ..shader = outline!.createShader(shaderRect),
      );
    }
  }

  @override
  bool shouldRepaint(_M3ESplitSharedGradientPainter oldDelegate) {
    return oldDelegate.radius != radius ||
        oldDelegate.fill != fill ||
        oldDelegate.outline != outline ||
        oldDelegate.outlineWidth != outlineWidth ||
        oldDelegate.disabled != disabled;
  }
}

/// Foreground [ShaderMask] sampled in the split row's coordinate space.
class _M3ESplitSharedForegroundLayer extends StatelessWidget {
  const _M3ESplitSharedForegroundLayer({
    required this.hostKey,
    required this.gradient,
    required this.child,
  });

  final GlobalKey hostKey;
  final Gradient gradient;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return ShaderMask(
      blendMode: BlendMode.srcIn,
      shaderCallback: (Rect bounds) {
        return gradient.createShader(
          m3eGradientSpanRect(
            hostKey: hostKey,
            context: context,
            local: bounds,
          ),
        );
      },
      child: child,
    );
  }
}
