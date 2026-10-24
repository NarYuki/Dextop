import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import 'm3e_list_theme.dart';

/// Visual and motion defaults for list reorder.
@immutable
class M3EListReorderState {
  /// Creates list reorder state.
  const M3EListReorderState({
    this.showDragHandle = true,
    this.dragElevation = M3EElevation.level4,
    this.dragScale = 1.02,
    this.dragRadius,
    this.dragColor,
    this.placeholderColor,
    this.placeholderBorder,
    this.placeholderRadius,
    this.displaceMotion = M3EMotion.expressiveSpatialPress,
    this.settleMotion = M3EMotion.expressiveSpatialDefault,
  });

  /// Theme defaults.
  static const M3EListReorderState defaults = M3EListReorderState();

  /// Whether to show a trailing drag-handle cue.
  final bool showDragHandle;

  /// Elevation while dragging.
  final double dragElevation;

  /// Scale while dragging.
  final double dragScale;

  /// Corner radius while dragging; defaults to card-list outer radius.
  final double? dragRadius;

  /// Fill while dragging; defaults to surface container high.
  final Color? dragColor;

  /// Placeholder slot fill; defaults to surface container low.
  final Color? placeholderColor;

  /// Placeholder slot outline.
  final BorderSide? placeholderBorder;

  /// Placeholder corner radius; defaults to card-list outer radius.
  final double? placeholderRadius;

  /// Neighbor displacement spring (noticeable overshoot).
  final M3ESpring displaceMotion;

  /// Spring that eases the lifted row into its slot before the order changes.
  final M3ESpring settleMotion;

  /// Resolved drag radius.
  double resolvedDragRadius(M3EListTheme listTheme) =>
      dragRadius ?? listTheme.cardList.outerRadius;

  /// Resolved placeholder radius.
  double resolvedPlaceholderRadius(M3EListTheme listTheme) =>
      placeholderRadius ?? listTheme.cardList.outerRadius;

  /// Drag fill.
  Color resolvedDragColor(M3EColorScheme scheme) =>
      dragColor ?? scheme.surfaceContainerHigh;

  /// Placeholder fill.
  Color resolvedPlaceholderColor(M3EColorScheme scheme) =>
      placeholderColor ?? scheme.surfaceContainerLow;

  /// copyWith.
  M3EListReorderState copyWith({
    bool? showDragHandle,
    double? dragElevation,
    double? dragScale,
    double? dragRadius,
    Color? dragColor,
    Color? placeholderColor,
    BorderSide? placeholderBorder,
    double? placeholderRadius,
    M3ESpring? displaceMotion,
    M3ESpring? settleMotion,
  }) {
    return M3EListReorderState(
      showDragHandle: showDragHandle ?? this.showDragHandle,
      dragElevation: dragElevation ?? this.dragElevation,
      dragScale: dragScale ?? this.dragScale,
      dragRadius: dragRadius ?? this.dragRadius,
      dragColor: dragColor ?? this.dragColor,
      placeholderColor: placeholderColor ?? this.placeholderColor,
      placeholderBorder: placeholderBorder ?? this.placeholderBorder,
      placeholderRadius: placeholderRadius ?? this.placeholderRadius,
      displaceMotion: displaceMotion ?? this.displaceMotion,
      settleMotion: settleMotion ?? this.settleMotion,
    );
  }
}
