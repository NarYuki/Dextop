import 'package:flutter/cupertino.dart' show Axis;
import 'package:flutter/painting.dart' show Axis;
import 'package:flutter/rendering.dart' show Axis;
import 'package:flutter/widgets.dart' show Axis;
import 'package:material_ui/material_ui.dart' show Axis;

/// Defines the visual presentation and layout style of an `M3ECarousel`.
enum M3ECarouselType {
  /// A large, prominent carousel that spotlights one big item with small
  /// peeking neighbours. Shows 2 - 3 visible items depending on
  /// [M3ECarouselHeroAlignment].
  hero,

  /// A standard carousel where items stay visually contained within the
  /// content area. Shows 3 - 4 visible items depending on whether the layout
  /// is extended.
  contained,

  /// A carousel whose items scroll to the edge of the container, all sharing a
  /// uniform extent.
  uncontained,

  /// Uncontained items with their own widths, from 9:16 to 16:9.
  ///
  /// Items keep those widths while scrolling. They do not grow or shrink
  /// into a shared slot.
  uncontainedMultiAspect,

  /// One edge-to-edge item that fills the carousel and snaps vertically.
  fullScreen,
}

/// The alignment of the focal item in a [M3ECarouselType.hero] carousel.
///
/// For [Axis.horizontal]: leading / center / trailing along the row.
/// For [Axis.vertical]: [left] is top (start), [right] is bottom (end).
enum M3ECarouselHeroAlignment {
  /// Focal item at the leading edge (left when horizontal, top when vertical),
  /// with one small peek after.
  left,

  /// The focal item is centered, with a small peek on either side.
  center,

  /// Focal item at the trailing edge (right when horizontal, bottom when
  /// vertical), with one small peek before.
  right,
}
