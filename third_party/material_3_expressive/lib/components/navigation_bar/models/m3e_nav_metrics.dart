import 'package:flutter/widgets.dart';

/// Resolved height/padding/icon metrics for the navigation bar.
@immutable
class M3ENavMetrics {
  /// heightSmall.
  final double heightSmall;

  /// heightMedium.
  final double heightMedium;

  /// iconSize.
  final double iconSize;

  /// padding.
  final EdgeInsetsGeometry padding;

  /// indicatorThickness.
  final double indicatorThickness; // for underline
  /// M3ENavMetrics.
  const M3ENavMetrics({
    required this.heightSmall,
    required this.heightMedium,
    required this.iconSize,
    required this.padding,
    required this.indicatorThickness,
  });
}
