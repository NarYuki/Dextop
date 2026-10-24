import 'package:flutter/widgets.dart';

/// Color overrides for a bottom sheet.
///
/// Null values resolve to the spec roles.
@immutable
class M3EBottomSheetColors {
  /// M3EBottomSheetColors.
  const M3EBottomSheetColors({
    this.containerColor,
    this.surfaceTintColor,
    this.handleColor,
    this.focusRingColor,
    this.scrimColor,
  });

  /// Container. Null uses `surfaceContainerLow`.
  final Color? containerColor;

  /// Surface tint layer blended at the elevation. Null paints no tint.
  final Color? surfaceTintColor;

  /// Drag handle, before its opacity. Null uses `onSurfaceVariant`.
  final Color? handleColor;

  /// Drag handle focus ring. Null uses `secondary`.
  final Color? focusRingColor;

  /// Scrim, before its opacity. Null uses `scrim`.
  final Color? scrimColor;

  /// Returns a copy with the given fields replaced.
  M3EBottomSheetColors copyWith({
    Color? containerColor,
    Color? surfaceTintColor,
    Color? handleColor,
    Color? focusRingColor,
    Color? scrimColor,
  }) {
    return M3EBottomSheetColors(
      containerColor: containerColor ?? this.containerColor,
      surfaceTintColor: surfaceTintColor ?? this.surfaceTintColor,
      handleColor: handleColor ?? this.handleColor,
      focusRingColor: focusRingColor ?? this.focusRingColor,
      scrimColor: scrimColor ?? this.scrimColor,
    );
  }

  /// Interpolates between this and [other].
  M3EBottomSheetColors lerp(M3EBottomSheetColors other, double t) {
    return M3EBottomSheetColors(
      containerColor: Color.lerp(containerColor, other.containerColor, t),
      surfaceTintColor: Color.lerp(surfaceTintColor, other.surfaceTintColor, t),
      handleColor: Color.lerp(handleColor, other.handleColor, t),
      focusRingColor: Color.lerp(focusRingColor, other.focusRingColor, t),
      scrimColor: Color.lerp(scrimColor, other.scrimColor, t),
    );
  }
}
