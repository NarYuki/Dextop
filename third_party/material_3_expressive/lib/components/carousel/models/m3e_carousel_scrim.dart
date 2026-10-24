import 'package:flutter/widgets.dart';

/// Scrim drawn over a carousel image and under its text.
class M3ECarouselScrim {
  /// Creates a solid scrim.
  const M3ECarouselScrim({required this.color, this.opacity = 1})
    : gradient = null,
      _isGradient = false;

  /// Creates a gradient scrim.
  ///
  /// A null [gradient] fades from transparent at the top to a translucent
  /// `colorScheme.scrim` at the bottom, under the text.
  const M3ECarouselScrim.gradient({this.gradient, this.opacity = 1})
    : color = const Color(0x00000000),
      _isGradient = true;

  /// Bottom alpha of the default gradient.
  static const double defaultGradientEndOpacity = 0.6;

  /// Scrim color. [opacity] replaces the color's alpha.
  ///
  /// Transparent for a gradient scrim.
  final Color color;

  /// Gradient painted instead of [color]. Null uses the default fade.
  final Gradient? gradient;

  /// How solid the scrim is, from 0 to 1.
  ///
  /// For a gradient scrim, this fades the whole layer.
  final double opacity;

  final bool _isGradient;

  /// Whether this scrim paints a gradient.
  bool get isGradient => _isGradient;

  /// Color painted between the image and the text.
  Color get paintColor =>
      color.withValues(alpha: opacity.clamp(0, 1).toDouble());

  /// Gradient to paint: [gradient], or a fade into [scrim].
  Gradient resolveGradient(Color scrim) =>
      gradient ??
      LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: <Color>[
          scrim.withValues(alpha: 0),
          scrim.withValues(alpha: defaultGradientEndOpacity),
        ],
      );
}
