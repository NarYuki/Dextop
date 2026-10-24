import 'package:material_3_expressive/components/sliders/m3e_sliders.dart'
    show M3ESlider;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ESlider;

/// Which track geometry an [M3ESlider] paints.
enum M3ESliderTrackKind {
  /// Active track from the start edge to the thumb.
  standard,

  /// Active track grows from the midpoint toward the thumb.
  centered,
}

/// How the track interprets active fill extents.
enum M3ESliderPaintMode {
  /// Single thumb; active from start → thumb (or centered).
  single,

  /// Dual thumbs; active between start and end.
  range,
}

/// Spec size of an [M3ESlider] track and handle.
enum M3ESliderSize {
  /// Track 16, handle 44, corner 8.
  xs(
    trackHeight: 16,
    cornerRadius: 8,
    handleHeight: 44,
    iconSize: 24,
    iconPadding: 8,
  ),

  /// Track 24, handle 44, corner 8.
  s(
    trackHeight: 24,
    cornerRadius: 8,
    handleHeight: 44,
    iconSize: 24,
    iconPadding: 8,
  ),

  /// Track 40, handle 52, corner 12. Inset icon 24 with 6 padding.
  m(
    trackHeight: 40,
    cornerRadius: 12,
    handleHeight: 52,
    iconSize: 24,
    iconPadding: 6,
  ),

  /// Track 56, handle 68, corner 16. Inset icon 24 with 6 padding.
  l(
    trackHeight: 56,
    cornerRadius: 16,
    handleHeight: 68,
    iconSize: 24,
    iconPadding: 6,
  ),

  /// Track 96, handle 108, corner 28. Inset icon 32 with 8 padding.
  xl(
    trackHeight: 96,
    cornerRadius: 28,
    handleHeight: 108,
    iconSize: 32,
    iconPadding: 8,
  );

  /// M3ESliderSize.
  const M3ESliderSize({
    required this.trackHeight,
    required this.cornerRadius,
    required this.handleHeight,
    required this.iconSize,
    required this.iconPadding,
  });

  /// Active and inactive track thickness.
  final double trackHeight;

  /// Outer corner radius of the track ends.
  final double cornerRadius;

  /// Long-axis length of the handle.
  final double handleHeight;

  /// Inset icon extent. XS and S still report a size so an icon can be passed.
  final double iconSize;

  /// Clear space between the track edge and the inset icon.
  final double iconPadding;
}

/// Axis-relative resting edge for the relocating track [M3ESlider.icon].
enum M3ESliderIconPosition {
  /// Leading / bottom (depending on orientation and [M3ESlider.topToBottom]).
  start,

  /// Trailing / top (depending on orientation and [M3ESlider.topToBottom]).
  end,
}
