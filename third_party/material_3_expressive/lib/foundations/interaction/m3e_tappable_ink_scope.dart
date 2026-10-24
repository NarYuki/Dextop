import 'package:flutter/widgets.dart';

/// Supplies ink interaction callbacks from the tappable primitive to overlays.
class M3ETappableInkScope extends InheritedWidget {
  /// Creates an ink-scope that forwards gesture callbacks to descendants.
  const M3ETappableInkScope({
    required super.child,
    this.onTap,
    this.onLongPress,
    this.mouseCursor,
    this.onTapDown,
    this.onTapUp,
    this.onTapCancel,
    this.onHover,
    this.trackHover = true,
    this.focusOverlay = true,
    super.key,
  });

  /// Called when the ink well is tapped.
  final VoidCallback? onTap;

  /// Called when the ink well is long-pressed.
  final VoidCallback? onLongPress;

  /// Cursor shown while interactive.
  final MouseCursor? mouseCursor;

  /// Called when a pointer contacts the ink well.
  final GestureTapDownCallback? onTapDown;

  /// Called when a pointer that contacted the ink well stops contacting it.
  final GestureTapUpCallback? onTapUp;

  /// Called when a tap gesture is canceled.
  final VoidCallback? onTapCancel;

  /// Called when the hover state changes.
  final ValueChanged<bool>? onHover;

  /// When false, the ink overlay does not paint hover.
  final bool trackHover;

  /// When false, the ink overlay does not paint the focused wash.
  final bool focusOverlay;

  /// Returns the nearest [M3ETappableInkScope], if any.
  static M3ETappableInkScope? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<M3ETappableInkScope>();
  }

  /// Whether this scope has a tap or long-press handler.
  bool get isInteractive => onTap != null || onLongPress != null;

  @override
  /// Whether dependents should rebuild when callbacks change.
  bool updateShouldNotify(M3ETappableInkScope oldWidget) {
    return onTap != oldWidget.onTap ||
        onLongPress != oldWidget.onLongPress ||
        mouseCursor != oldWidget.mouseCursor ||
        onTapDown != oldWidget.onTapDown ||
        onTapUp != oldWidget.onTapUp ||
        onTapCancel != oldWidget.onTapCancel ||
        onHover != oldWidget.onHover ||
        trackHover != oldWidget.trackHover ||
        focusOverlay != oldWidget.focusOverlay;
  }
}
