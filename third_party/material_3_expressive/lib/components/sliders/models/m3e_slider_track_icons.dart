import 'package:flutter/widgets.dart';

/// Optional icons inset into the active / inactive track segments.
@immutable
class M3ESliderTrackIcons {
  /// M3ESliderTrackIcons.
  const M3ESliderTrackIcons({
    this.activeStart,
    this.activeEnd,
    this.inactiveStart,
    this.inactiveEnd,
    this.size = 16,
  });

  /// activeStart.

  final Widget? activeStart;

  /// activeEnd.
  final Widget? activeEnd;

  /// inactiveStart.
  final Widget? inactiveStart;

  /// inactiveEnd.
  final Widget? inactiveEnd;

  /// size.
  final double size;

  /// The hasAny.

  bool get hasAny =>
      activeStart != null ||
      activeEnd != null ||
      inactiveStart != null ||
      inactiveEnd != null;
}
