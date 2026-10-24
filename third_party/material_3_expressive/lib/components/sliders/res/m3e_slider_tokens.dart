/// Numeric constants matching Compose Material 3 `SliderTokens`.
abstract final class M3ESliderTokens {
  const M3ESliderTokens._();

  /// activeHandleHeight.

  static const double activeHandleHeight = 44;

  /// activeHandleWidth.
  static const double activeHandleWidth = 4;

  /// handleHeight.
  static const double handleHeight = 44;

  /// handleWidth.
  static const double handleWidth = 4;

  /// pressedHandleWidth.
  static const double pressedHandleWidth = 2;

  /// focusHandleWidth.
  static const double focusHandleWidth = 2;

  /// hoverHandleWidth.
  static const double hoverHandleWidth = 4;

  /// activeTrackHeight.

  static const double activeTrackHeight = 16;

  /// inactiveTrackHeight.
  static const double inactiveTrackHeight = 16;

  /// Gap between the handle edge and the track segments.
  static const double thumbTrackGapSize = 6;

  /// Corner radius on track ends facing the handle gap.
  static const double trackInsideCornerSize = 2;

  /// Outer corner radius for active and inactive track segments.
  ///
  /// Fixed (not derived from track thickness). Half of the default 16px track.
  static const double trackCornerRadius = 8;

  /// stopIndicatorSize.

  static const double stopIndicatorSize = 4;

  /// Diameter of the inner discrete tick marks.
  ///
  /// Matches [stopIndicatorSize] so inner and end markers read as one set.
  static const double tickSize = stopIndicatorSize;

  /// Opacity of discrete tick marks on the track.
  ///
  /// Full strength so inner ticks match the end stop indicators.
  static const double tickOpacity = 1;

  /// stopIndicatorTrailingSpace.
  static const double stopIndicatorTrailingSpace = 4;

  /// Clear space between the track edge and the relocating icon's outer edge.
  ///
  /// Matches m3e_core's default resting inset (`trackCornerRadius`, which
  /// defaults to half of the 16px track height).
  static const double iconEdgeInset = 8;

  /// valueIndicatorActiveBottomSpace.

  static const double valueIndicatorActiveBottomSpace = 12;

  /// valueIndicatorWidth.
  static const double valueIndicatorWidth = 48;

  /// valueIndicatorHeight.
  static const double valueIndicatorHeight = 44;

  /// valueIndicatorRadius.
  static const double valueIndicatorRadius = 22;

  /// valueIndicatorFontSize.
  static const double valueIndicatorFontSize = 14;

  /// valueIndicatorLineHeight.
  static const double valueIndicatorLineHeight = 20;

  /// valueIndicatorLetterSpacing.
  static const double valueIndicatorLetterSpacing = 0.5;

  /// Diameter of the hover, focus, and press state layer.
  static const double stateLayerSize = 40;

  /// hoverStateOpacity.
  static const double hoverStateOpacity = 0.08;

  /// focusStateOpacity.
  static const double focusStateOpacity = 0.1;

  /// pressedStateOpacity.
  static const double pressedStateOpacity = 0.1;

  /// overlapOutlineWidth.
  static const double overlapOutlineWidth = 1;

  /// iconSize.
  static const double iconSize = 24;

  /// disabledHandleOpacity.

  static const double disabledHandleOpacity = 0.38;

  /// disabledActiveTrackOpacity.
  static const double disabledActiveTrackOpacity = 0.38;

  /// disabledInactiveTrackOpacity.
  static const double disabledInactiveTrackOpacity = 0.12;

  /// Vertical thumb is the horizontal size swapped (44×4).
  static const double verticalHandleWidth = handleHeight;

  /// verticalHandleHeight.
  static const double verticalHandleHeight = handleWidth;

  /// verticalPressedHandleHeight.
  static const double verticalPressedHandleHeight = pressedHandleWidth;

  /// Wavy active-track defaults (aligned with linear wavy progress).
  static const double waveAmplitude = 3;

  /// wavelength.
  static const double wavelength = 40;
}
