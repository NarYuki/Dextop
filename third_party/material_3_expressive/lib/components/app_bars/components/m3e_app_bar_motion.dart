part of '../m3e_app_bars.dart';

/// Maps scroll offset and hide progress onto the content band.
class _M3EBarMotion {
  const _M3EBarMotion({
    required this.offset,
    required this.collapsed,
    required this.expanded,
    required this.shown,
    required this.mode,
    required this.manual,
    this.glued = false,
  });

  final double offset;
  final double collapsed;
  final double expanded;
  final double shown;
  final M3EAppBarHideMode mode;
  final bool manual;

  /// The page runs behind the bar and never moves with it, so the bar's
  /// bottom edge can travel with the content one-to-one. See [band].
  final bool glued;

  double get range => math.max(0, expanded - collapsed);

  double get expand {
    if (range <= 0.5) {
      return 0;
    }
    return (1 - (offset / range).clamp(0.0, 1.0)).clamp(0.0, 1.0);
  }

  bool get entire {
    if (mode == M3EAppBarHideMode.entire) {
      return true;
    }
    return mode == M3EAppBarHideMode.none && manual;
  }

  bool get actions => mode == M3EAppBarHideMode.actions;

  double get painted => lerpDouble(collapsed, expanded, expand) ?? collapsed;

  double get slot {
    final double open = painted;
    if (entire) {
      return open * shown.clamp(0.0, 1.0);
    }
    if (actions) {
      return lerpDouble(collapsed, open, shown.clamp(0.0, 1.0)) ?? collapsed;
    }
    return open;
  }

  /// Visible band height when [glued]. The page does not move with the bar,
  /// so subtracting the scroll [offset] keeps the bar's bottom edge exactly
  /// on the content: both travel at the scroll rate. [shown] only takes part
  /// while a controller drives the bar manually.
  double get band {
    final double share = manual ? shown.clamp(0.0, 1.0) : 1.0;
    final double travel = expanded * share - offset;
    if (entire || actions) {
      return math.max(0, travel);
    }
    return painted;
  }

  /// How much of the bar is still revealed, for title and action styling.
  double get reveal {
    if (!glued || manual) {
      return shown.clamp(0.0, 1.0);
    }
    if (expanded <= 0) {
      return 1;
    }
    return (band / expanded).clamp(0.0, 1.0);
  }

  double get titleExpand => actions ? expand * reveal : expand;

  double get titleHide => actions ? 1 - reveal : 0;
}

Color _m3eBarColor({
  required M3EAppBar bar,
  required M3EAppBarTheme theme,
  required M3EColorScheme scheme,
  required bool under,
}) {
  final Color rest = bar.backgroundColor ?? theme.backgroundColor(scheme);
  final Color scrolled =
      bar.backgroundColor ?? theme.scrolledBackgroundColor(scheme);
  return under ? scrolled : rest;
}

/// Starts a hide once. Repeating [AnimationController.reverse] every pixel
/// stops and restarts the travel, which reads as a twitch.
void _m3eSlideAway(AnimationController visibility) {
  if (visibility.status == AnimationStatus.reverse ||
      visibility.value <= 0.001) {
    return;
  }
  visibility.reverse();
}

/// Starts a show once. See [_m3eSlideAway].
void _m3eSlideBack(AnimationController visibility) {
  if (visibility.status == AnimationStatus.forward ||
      visibility.value >= 0.999) {
    return;
  }
  visibility.forward();
}

/// Slides [child] up out of a shrinking window.
Widget _m3eClipSliding({
  required double push,
  required double visual,
  required Widget child,
}) {
  final double open = math.max(visual, push);
  return SizedBox(
    height: math.max(0, push),
    child: ClipRect(
      child: OverflowBox(
        alignment: Alignment.bottomCenter,
        minHeight: open,
        maxHeight: open,
        child: SizedBox(height: open, child: child),
      ),
    ),
  );
}
