import 'package:flutter/foundation.dart';
import 'package:flutter/scheduler.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../utils/m3e_toolbar_spring_motion.dart';

/// Tracks scroll / manual exit translation for a toolbar.
///
/// [offset] is ≤ 0 and clamped between [offsetLimit] and 0. Call [attach] from a
/// [TickerProvider] (usually the toolbar state) before [show] / [hide].
class M3EToolbarVisibilityController extends ChangeNotifier {
  /// M3EToolbarVisibilityController.
  M3EToolbarVisibilityController({
    this.motion = M3EMotion.expressiveSpatialDefault,
    double? exitExtent,
  }) : _exitExtent = exitExtent {
    if (exitExtent != null) {
      _offsetLimit = -exitExtent.abs();
    }
  }

  /// Spring used for [show] / [hide] / settle animations.
  final M3ESpring motion;

  double _offsetLimit = 0;
  double _offset = 0;
  double _contentOffset = 0;
  double? _exitExtent;

  TickerProvider? _vsync;
  SingleMotionController? _settle;
  double? _animatingTarget;

  /// Optional fixed exit distance. When null, the toolbar measures itself and
  /// sets [offsetLimit] to `-(extent + screenOffset)`.
  double? get exitExtent => _exitExtent;
  set exitExtent(double? value) {
    if (_exitExtent == value) {
      return;
    }
    _exitExtent = value;
    if (value != null) {
      offsetLimit = -value.abs();
    }
    notifyListeners();
  }

  /// Maximum negative translation (≤ 0).
  double get offsetLimit => _offsetLimit;
  set offsetLimit(double value) {
    if (_offsetLimit == value) {
      return;
    }
    _offsetLimit = value;
    offset = _offset;
    notifyListeners();
  }

  /// Current translation (≤ 0), clamped to [[offsetLimit], 0].
  double get offset => _offset;
  set offset(double value) {
    final double coerced = _offsetLimit <= 0
        ? value.clamp(_offsetLimit, 0.0)
        : value.clamp(0.0, _offsetLimit);
    if (_offset == coerced) {
      return;
    }
    _offset = coerced;
    notifyListeners();
  }

  /// Accumulated scroll delta consumed while dragging.
  double get contentOffset => _contentOffset;
  set contentOffset(double value) {
    if (_contentOffset == value) {
      return;
    }
    _contentOffset = value;
    notifyListeners();
  }

  /// 0 = fully visible, 1 = fully hidden.
  double get collapsedFraction {
    if (_offsetLimit == 0) {
      return 0;
    }
    return _offset / _offsetLimit;
  }

  /// Whether the toolbar is fully off-screen.
  bool get isHidden => collapsedFraction >= 1;

  /// Whether a settle / show / hide spring is currently running.
  bool get isAnimating => _settle != null;

  bool _collapseRequested = false;

  /// Scroll-collapse intent for `M3EToolbarScrollAction.collapse`, read by
  /// the toolbar to flip its own expand state (via its adjacent FAB /
  /// expand-trigger spring) the instant a scroll direction changes.
  ///
  /// Independent of [offset], which only animates for
  /// `M3EToolbarScrollAction.hide`'s slide-away.
  bool get collapseRequested => _collapseRequested;

  /// Requests the collapse-on-scroll target. No-op if already requested.
  void requestCollapse() {
    if (_collapseRequested) {
      return;
    }
    _collapseRequested = true;
    notifyListeners();
  }

  /// Reverses [requestCollapse]. No-op if not currently requested.
  void requestExpand() {
    if (!_collapseRequested) {
      return;
    }
    _collapseRequested = false;
    notifyListeners();
  }

  /// Binds a ticker for spring show/hide. Safe to call repeatedly.
  // ignore: use_setters_to_change_properties -- attach/detach pair; not a field setter.
  void attach(TickerProvider vsync) {
    _vsync = vsync;
  }

  /// Releases settle animation resources. Does not dispose this controller.
  void detach() {
    _settle?.dispose();
    _settle = null;
    _animatingTarget = null;
    _vsync = null;
  }

  /// Cancels an in-flight settle / show / hide spring.
  void cancelAnimation() {
    _settle?.dispose();
    _settle = null;
    _animatingTarget = null;
  }

  /// Animates to fully visible (`offset == 0`).
  void show() => _animateTo(0);

  /// Animates to fully hidden (`offset == offsetLimit`).
  void hide() => _animateTo(offsetLimit);

  /// Toggles based on [collapsedFraction] midpoint.
  void toggle() {
    if (collapsedFraction < 0.5) {
      hide();
    } else {
      show();
    }
  }

  /// Settles after a scroll fling using velocity threshold 150.
  void settle({required double velocity}) {
    if (offset == 0 || offset == offsetLimit) {
      return;
    }
    final double target;
    if (velocity.abs() > 150) {
      target = velocity > 0 ? offsetLimit : 0;
    } else {
      target = collapsedFraction < 0.5 ? 0 : offsetLimit;
    }
    _animateTo(target);
  }

  void _animateTo(double target) {
    // Already there and idle, or already mid-flight toward the same target:
    // restarting the spring on every scroll delta would read as a twitch.
    if ((offset == target && !isAnimating) ||
        (isAnimating && _animatingTarget == target)) {
      return;
    }
    final TickerProvider? vsync = _vsync;
    if (vsync == null) {
      cancelAnimation();
      offset = target;
      return;
    }
    cancelAnimation();
    _animatingTarget = target;
    _settle =
        SingleMotionController(
          motion: motion.toMotion(),
          vsync: vsync,
          initialValue: offset,
        )..addListener(() {
          offset = _settle!.value;
        });
    _settle!.animateTo(target);
  }

  @override
  void dispose() {
    cancelAnimation();
    super.dispose();
  }
}
