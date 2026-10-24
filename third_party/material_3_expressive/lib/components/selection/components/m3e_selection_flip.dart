import 'dart:math' as math;

import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart'
    show CircleBorder, InkWell, Material, MaterialType;

/// Minimum layout size for an interactive selection flip.
///
/// The leading face is laid out at least this square; icon glyph size is
/// unchanged (centered). Callers may override via [M3ESelectionFlip.minTapSize].
const double kM3ESelectionFlipMinTapSize = 40;

/// Flips horizontally between [child] and [selectedChild] when [selected].
///
/// Used for list selection icons at leading or trailing placement.
class M3ESelectionFlip extends StatefulWidget {
  /// Creates a selection flip.
  const M3ESelectionFlip({
    required this.selected,
    required this.selectedChild,
    required this.child,
    this.duration = const Duration(milliseconds: 220),
    this.onTap,
    this.minTapSize = kM3ESelectionFlipMinTapSize,
    super.key,
  });

  /// Whether the selected face is shown.
  final bool selected;

  /// Unselected face.
  final Widget child;

  /// Selected face.
  final Widget selectedChild;

  /// Flip duration.
  final Duration duration;

  /// Optional tap handler on the flip target only.
  final VoidCallback? onTap;

  /// Minimum layout width and height.
  ///
  /// Icon faces stay their intrinsic size and are centered inside this box,
  /// which also defines the tappable region. Defaults to
  /// [kM3ESelectionFlipMinTapSize].
  final double minTapSize;

  @override
  State<M3ESelectionFlip> createState() => _M3ESelectionFlipState();
}

class _M3ESelectionFlipState extends State<M3ESelectionFlip>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: widget.duration,
      value: widget.selected ? 1 : 0,
    );
  }

  @override
  void didUpdateWidget(M3ESelectionFlip oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.duration != widget.duration) {
      _controller.duration = widget.duration;
    }
    if (oldWidget.selected != widget.selected) {
      if (widget.selected) {
        _controller.forward();
      } else {
        _controller.reverse();
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final Widget face = AnimatedBuilder(
      animation: _controller,
      builder: (BuildContext context, Widget? _) {
        final double angle = _controller.value * math.pi;
        final bool showSelected = angle > math.pi / 2;
        final double displayAngle = showSelected ? math.pi - angle : angle;
        return Transform(
          alignment: Alignment.center,
          transform: Matrix4.identity()
            ..setEntry(3, 2, 0.001)
            ..rotateY(displayAngle),
          child: showSelected ? widget.selectedChild : widget.child,
        );
      },
    );

    final Widget sized = _FlipSize(
      unselected: widget.child,
      selected: widget.selectedChild,
      child: face,
    );

    final Widget constrained = ConstrainedBox(
      constraints: BoxConstraints(
        minWidth: widget.minTapSize,
        minHeight: widget.minTapSize,
      ),
      child: Center(child: sized),
    );

    if (widget.onTap == null) {
      return constrained;
    }

    return Material(
      type: MaterialType.transparency,
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: widget.onTap,
        child: constrained,
      ),
    );
  }
}

/// Reserves the larger of both faces so the flip does not jump layout.
class _FlipSize extends StatelessWidget {
  const _FlipSize({
    required this.unselected,
    required this.selected,
    required this.child,
  });

  final Widget unselected;
  final Widget selected;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Stack(
      alignment: Alignment.center,
      children: <Widget>[
        IgnorePointer(child: Opacity(opacity: 0, child: unselected)),
        IgnorePointer(child: Opacity(opacity: 0, child: selected)),
        child,
      ],
    );
  }
}
