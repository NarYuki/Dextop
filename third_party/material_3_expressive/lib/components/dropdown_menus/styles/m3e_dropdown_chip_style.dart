import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';

/// M3EDropdownChipStyle.

@immutable
class M3EDropdownChipStyle with Diagnosticable {
  /// Chip background color.
  final Color? backgroundColor;

  /// Chip label text style.
  final TextStyle? labelStyle;

  /// Chip delete icon widget.
  final Widget? deleteIcon;

  /// Padding inside each chip.
  final EdgeInsetsGeometry padding;

  /// Border drawn around each chip.
  final BorderSide? border;

  /// Border radius of the chip.
  final BorderRadius borderRadius;

  /// Spacing between chips horizontally.
  final double spacing;

  /// Spacing between chip rows vertically.
  final double runSpacing;

  /// Whether to wrap chips or scroll them horizontally.
  final bool wrap;

  /// Maximum visible chips before showing "+N more".
  final int? maxDisplayCount;

  /// Spring motion for the chip entry (scale-in) animation.
  ///
  /// Defaults to [M3EMotion.expressiveSpatialDefault].
  final M3ESpring openMotion;

  /// Spring motion for the chip exit (scale-out / pop) animation.
  ///
  /// Defaults to [M3EMotion.expressiveSpatialDefault].
  final M3ESpring closeMotion;

  /// Mouse cursor when hovering over the chip.
  final MouseCursor? mouseCursor;

  /// Creates a [M3EDropdownChipStyle].
  const M3EDropdownChipStyle({
    this.backgroundColor,
    this.labelStyle,
    this.deleteIcon,
    this.padding = const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
    this.border,
    this.borderRadius = const BorderRadius.all(Radius.circular(20)),
    this.spacing = 6,
    this.runSpacing = 6,
    this.wrap = true,
    this.maxDisplayCount,
    this.openMotion = M3EMotion.expressiveSpatialDefault,
    this.closeMotion = M3EMotion.expressiveSpatialDefault,
    this.mouseCursor,
  });

  /// Creates a copy of this style with the given fields replaced.
  M3EDropdownChipStyle copyWith({
    Color? backgroundColor,
    TextStyle? labelStyle,
    Widget? deleteIcon,
    EdgeInsetsGeometry? padding,
    BorderSide? border,
    BorderRadius? borderRadius,
    double? spacing,
    double? runSpacing,
    bool? wrap,
    int? maxDisplayCount,
    M3ESpring? openMotion,
    M3ESpring? closeMotion,
    MouseCursor? mouseCursor,
  }) {
    return M3EDropdownChipStyle(
      backgroundColor: backgroundColor ?? this.backgroundColor,
      labelStyle: labelStyle ?? this.labelStyle,
      deleteIcon: deleteIcon ?? this.deleteIcon,
      padding: padding ?? this.padding,
      border: border ?? this.border,
      borderRadius: borderRadius ?? this.borderRadius,
      spacing: spacing ?? this.spacing,
      runSpacing: runSpacing ?? this.runSpacing,
      wrap: wrap ?? this.wrap,
      maxDisplayCount: maxDisplayCount ?? this.maxDisplayCount,
      openMotion: openMotion ?? this.openMotion,
      closeMotion: closeMotion ?? this.closeMotion,
      mouseCursor: mouseCursor ?? this.mouseCursor,
    );
  }

  /// Linearly interpolate between two chip styles.
  static M3EDropdownChipStyle lerp(
    M3EDropdownChipStyle? a,
    M3EDropdownChipStyle? b,
    double t,
  ) {
    if (a == null && b == null) {
      return const M3EDropdownChipStyle();
    }
    return M3EDropdownChipStyle(
      backgroundColor: Color.lerp(a?.backgroundColor, b?.backgroundColor, t),
      labelStyle: TextStyle.lerp(a?.labelStyle, b?.labelStyle, t),
      deleteIcon: t < 0.5 ? a?.deleteIcon : b?.deleteIcon,
      padding:
          EdgeInsetsGeometry.lerp(a?.padding, b?.padding, t) ??
          const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      border: BorderSide.lerp(
        a?.border ?? BorderSide.none,
        b?.border ?? BorderSide.none,
        t,
      ),
      borderRadius:
          BorderRadius.lerp(a?.borderRadius, b?.borderRadius, t) ??
          const BorderRadius.all(Radius.circular(20)),
      spacing: lerpDouble(a?.spacing, b?.spacing, t) ?? 6,
      runSpacing: lerpDouble(a?.runSpacing, b?.runSpacing, t) ?? 6,
      wrap: t < 0.5 ? (a?.wrap ?? true) : (b?.wrap ?? true),
      maxDisplayCount: t < 0.5 ? a?.maxDisplayCount : b?.maxDisplayCount,
      openMotion: t < 0.5
          ? (a?.openMotion ?? M3EMotion.expressiveSpatialDefault)
          : (b?.openMotion ?? M3EMotion.expressiveSpatialDefault),
      closeMotion: t < 0.5
          ? (a?.closeMotion ?? M3EMotion.expressiveSpatialDefault)
          : (b?.closeMotion ?? M3EMotion.expressiveSpatialDefault),
      mouseCursor: t < 0.5 ? a?.mouseCursor : b?.mouseCursor,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is M3EDropdownChipStyle &&
          backgroundColor == other.backgroundColor &&
          labelStyle == other.labelStyle &&
          deleteIcon == other.deleteIcon &&
          padding == other.padding &&
          border == other.border &&
          borderRadius == other.borderRadius &&
          spacing == other.spacing &&
          runSpacing == other.runSpacing &&
          wrap == other.wrap &&
          maxDisplayCount == other.maxDisplayCount &&
          openMotion == other.openMotion &&
          closeMotion == other.closeMotion &&
          mouseCursor == other.mouseCursor;

  @override
  int get hashCode => Object.hash(
    backgroundColor,
    labelStyle,
    deleteIcon,
    padding,
    border,
    borderRadius,
    spacing,
    runSpacing,
    wrap,
    maxDisplayCount,
    openMotion,
    closeMotion,
    mouseCursor,
  );

  @override
  void debugFillProperties(DiagnosticPropertiesBuilder properties) {
    super.debugFillProperties(properties);
    properties
      ..add(ColorProperty('backgroundColor', backgroundColor))
      ..add(DiagnosticsProperty<TextStyle>('labelStyle', labelStyle))
      ..add(DiagnosticsProperty<EdgeInsetsGeometry>('padding', padding))
      ..add(DiagnosticsProperty<BorderRadius>('borderRadius', borderRadius))
      ..add(DoubleProperty('spacing', spacing))
      ..add(IntProperty('maxDisplayCount', maxDisplayCount));
  }
}
