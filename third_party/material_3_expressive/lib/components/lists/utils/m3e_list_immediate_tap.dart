import 'package:flutter/widgets.dart';

/// Binds immediate [onTap] and optional [onDoubleTap] without delaying the
/// first tap (unlike [GestureDetector.onDoubleTap]).
class M3EListTapBinder extends StatefulWidget {
  /// Creates a tap binder.
  const M3EListTapBinder({
    required this.builder,
    this.onTap,
    this.onDoubleTap,
    this.doubleTapInterval = const Duration(milliseconds: 280),
    super.key,
  });

  /// Builds the child with a composed onPressed callback.
  final Widget Function(BuildContext context, VoidCallback? onPressed) builder;

  /// Immediate single-tap handler.
  final VoidCallback? onTap;

  /// Second-tap handler within [doubleTapInterval] (replaces a second [onTap]).
  final VoidCallback? onDoubleTap;

  /// Window for recognizing a double tap.
  final Duration doubleTapInterval;

  @override
  State<M3EListTapBinder> createState() => _M3EListTapBinderState();
}

class _M3EListTapBinderState extends State<M3EListTapBinder> {
  DateTime? _lastTapAt;

  VoidCallback? get _onPressed {
    if (widget.onTap == null && widget.onDoubleTap == null) {
      return null;
    }
    return _handleTap;
  }

  void _handleTap() {
    final now = DateTime.now();
    final DateTime? last = _lastTapAt;
    if (widget.onDoubleTap != null &&
        last != null &&
        now.difference(last) <= widget.doubleTapInterval) {
      _lastTapAt = null;
      widget.onDoubleTap!();
      return;
    }
    _lastTapAt = now;
    widget.onTap?.call();
  }

  @override
  Widget build(BuildContext context) => widget.builder(context, _onPressed);
}
