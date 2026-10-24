import 'package:flutter/widgets.dart';

/// Static layout constants for the navigation rail.
abstract final class M3ENavigationRailLayout {
  /// Delay before committing a deferred selection/expansion change.
  static const Duration selectionDelay = Duration(milliseconds: 320);

  /// Duration of the rail's width/expansion animation.
  static const Duration expandDuration = Duration(milliseconds: 280);

  /// Horizontal + bottom insets around section headers.
  static const EdgeInsetsDirectional sectionPadding =
      EdgeInsetsDirectional.only(start: 16, end: 16, bottom: 12);

  /// Horizontal inset for rail content.
  static const double horizontalInset = 16;

  /// Vertical gap inserted above the first section.
  static const double topGap = 36;
}
