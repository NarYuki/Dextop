import 'dart:ui' show lerpDouble;

import 'package:flutter/widgets.dart';

/// Interaction tokens for the bottom sheet drag handle.
///
/// The handle size, padding and opacity live on `M3EBottomSheetTheme`.
@immutable
class M3EBottomSheetDragHandleStyle {
  /// M3EBottomSheetDragHandleStyle.
  const M3EBottomSheetDragHandleStyle({
    this.targetSize = 48,
    this.dragRegionHeight = 48,
    this.hoverOpacity = 0.08,
    this.focusOpacity = 0.1,
    this.pressedOpacity = 0.1,
    this.focusRingWidth = 3,
    this.focusRingGap = 2,
    this.mouseCursor = SystemMouseCursors.click,
    this.dragCursor = SystemMouseCursors.grab,
    this.draggingCursor = SystemMouseCursors.grabbing,
  });

  /// Square hit target of the handle button (48).
  final double targetSize;

  /// Height of the draggable top strip (48).
  final double dragRegionHeight;

  /// Hover state layer opacity (0.08).
  final double hoverOpacity;

  /// Focus state layer opacity (0.1), when focus rings are off.
  final double focusOpacity;

  /// Pressed ripple opacity (0.1).
  final double pressedOpacity;

  /// Focus ring thickness (3).
  final double focusRingWidth;

  /// Focus ring offset from the handle (2).
  final double focusRingGap;

  /// Cursor over the handle button.
  final MouseCursor mouseCursor;

  /// Cursor over the drag strip.
  final MouseCursor dragCursor;

  /// Cursor while the sheet is dragged.
  final MouseCursor draggingCursor;

  /// Returns a copy with the given fields replaced.
  M3EBottomSheetDragHandleStyle copyWith({
    double? targetSize,
    double? dragRegionHeight,
    double? hoverOpacity,
    double? focusOpacity,
    double? pressedOpacity,
    double? focusRingWidth,
    double? focusRingGap,
    MouseCursor? mouseCursor,
    MouseCursor? dragCursor,
    MouseCursor? draggingCursor,
  }) {
    return M3EBottomSheetDragHandleStyle(
      targetSize: targetSize ?? this.targetSize,
      dragRegionHeight: dragRegionHeight ?? this.dragRegionHeight,
      hoverOpacity: hoverOpacity ?? this.hoverOpacity,
      focusOpacity: focusOpacity ?? this.focusOpacity,
      pressedOpacity: pressedOpacity ?? this.pressedOpacity,
      focusRingWidth: focusRingWidth ?? this.focusRingWidth,
      focusRingGap: focusRingGap ?? this.focusRingGap,
      mouseCursor: mouseCursor ?? this.mouseCursor,
      dragCursor: dragCursor ?? this.dragCursor,
      draggingCursor: draggingCursor ?? this.draggingCursor,
    );
  }

  /// Interpolates between this and [other].
  M3EBottomSheetDragHandleStyle lerp(
    M3EBottomSheetDragHandleStyle other,
    double t,
  ) {
    final end = t < 0.5 ? this : other;
    return end.copyWith(
      targetSize: lerpDouble(targetSize, other.targetSize, t),
      dragRegionHeight: lerpDouble(dragRegionHeight, other.dragRegionHeight, t),
      hoverOpacity: lerpDouble(hoverOpacity, other.hoverOpacity, t),
      focusOpacity: lerpDouble(focusOpacity, other.focusOpacity, t),
      pressedOpacity: lerpDouble(pressedOpacity, other.pressedOpacity, t),
      focusRingWidth: lerpDouble(focusRingWidth, other.focusRingWidth, t),
      focusRingGap: lerpDouble(focusRingGap, other.focusRingGap, t),
    );
  }
}
