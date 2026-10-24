import 'package:flutter/widgets.dart';

/// Clips a carousel track on the scroll axis and leaves room for shadows or
/// a focus ring.
class M3ECarouselTrackClipper extends CustomClipper<Rect> {
  /// Creates a clipper for [axis].
  const M3ECarouselTrackClipper({
    required this.axis,
    this.crossAxisBleed = 16,
    this.mainAxisBleed = 0,
  });

  /// Scroll axis.
  final Axis axis;

  /// Extra space on the cross axis so the hover shadow is not cut off.
  final double crossAxisBleed;

  /// Extra space on the scroll axis. Item bodies use 0 so a settled item
  /// does not leave a sliver. Focus rings use a few pixels.
  final double mainAxisBleed;

  @override
  Rect getClip(Size size) {
    if (axis == Axis.horizontal) {
      return Rect.fromLTRB(
        -mainAxisBleed,
        -crossAxisBleed,
        size.width + mainAxisBleed,
        size.height + crossAxisBleed,
      );
    }
    return Rect.fromLTRB(
      -crossAxisBleed,
      -mainAxisBleed,
      size.width + crossAxisBleed,
      size.height + mainAxisBleed,
    );
  }

  @override
  bool shouldReclip(covariant M3ECarouselTrackClipper oldClipper) {
    return oldClipper.axis != axis ||
        oldClipper.crossAxisBleed != crossAxisBleed ||
        oldClipper.mainAxisBleed != mainAxisBleed;
  }
}
