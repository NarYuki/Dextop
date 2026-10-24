import 'dart:math' as math;
import 'dart:ui' show lerpDouble;

import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_dialog_enums.dart';
import '../styles/m3e_dialog_theme.dart';
import '../utils/m3e_dialog_spring.dart';

/// Spring-driven enter / exit for dialog routes.
///
/// Follows the route [animation] direction. Basic dialogs fade and scale up;
/// full-screen dialogs slide up from the bottom edge.
class M3EDialogTransition extends StatefulWidget {
  /// M3EDialogTransition.
  const M3EDialogTransition({
    required this.animation,
    required this.variant,
    required this.dialogTheme,
    required this.child,
    super.key,
  });

  /// Theme captured from the opening context.
  final M3EDialogTheme dialogTheme;

  /// Route animation; only its direction is used.
  final Animation<double> animation;

  /// Which motion to play.
  final M3EDialogVariant variant;

  /// Dialog surface.
  final Widget child;

  /// Route duration that covers the slowest spring for [variant].
  static Duration durationFor(M3EDialogTheme theme, M3EDialogVariant variant) {
    if (variant == M3EDialogVariant.fullScreen) {
      return m3eDialogSpringSettle(theme.fullScreen.enterSpring);
    }
    final Duration spatial = m3eDialogSpringSettle(theme.enterSpring);
    final Duration fade = m3eDialogSpringSettle(theme.fadeSpring);
    return spatial > fade ? spatial : fade;
  }

  @override
  State<M3EDialogTransition> createState() => _M3EDialogTransitionState();
}

class _M3EDialogTransitionState extends State<M3EDialogTransition>
    with TickerProviderStateMixin {
  SingleMotionController? _spatial;
  SingleMotionController? _fade;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_spatial != null) {
      return;
    }
    final M3EDialogTheme dialogTheme = widget.dialogTheme;
    final M3ESpring spatial = widget.variant == M3EDialogVariant.fullScreen
        ? dialogTheme.fullScreen.enterSpring
        : dialogTheme.enterSpring;
    _spatial = SingleMotionController(
      motion: m3eDialogSpringMotion(spatial),
      vsync: this,
    );
    _fade = SingleMotionController(
      motion: m3eDialogSpringMotion(dialogTheme.fadeSpring),
      vsync: this,
    );
    widget.animation.addStatusListener(_onStatus);
    _onStatus(widget.animation.status);
  }

  @override
  void didUpdateWidget(covariant M3EDialogTransition oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.animation != widget.animation) {
      oldWidget.animation.removeStatusListener(_onStatus);
      widget.animation.addStatusListener(_onStatus);
    }
  }

  void _onStatus(AnimationStatus status) {
    final double target = status.isForwardOrCompleted ? 1 : 0;
    final bool reduce = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    if (status == AnimationStatus.completed && _spatial!.value == 0 || reduce) {
      _spatial!.value = target;
      _fade!.value = target;
      return;
    }
    _spatial!.animateTo(target);
    _fade!.animateTo(target);
  }

  @override
  void dispose() {
    widget.animation.removeStatusListener(_onStatus);
    _spatial?.dispose();
    _fade?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final M3EDialogTheme dialogTheme = widget.dialogTheme;
    return AnimatedBuilder(
      animation: Listenable.merge(<Listenable>[_spatial!, _fade!]),
      child: widget.child,
      builder: (BuildContext context, Widget? child) {
        final double p = m3eDialogSnap(_spatial!.value);
        if (widget.variant == M3EDialogVariant.fullScreen) {
          return FractionalTranslation(
            translation: Offset(0, math.max(0, 1 - p)),
            child: child,
          );
        }
        return Opacity(
          opacity: m3eDialogSnap(_fade!.value).clamp(0, 1),
          child: Transform.scale(
            scale: lerpDouble(dialogTheme.entranceScale, 1, p),
            child: child,
          ),
        );
      },
    );
  }
}
