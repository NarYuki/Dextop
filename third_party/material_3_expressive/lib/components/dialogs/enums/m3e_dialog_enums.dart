import 'package:flutter/painting.dart';

/// Dialog layout variant.
enum M3EDialogVariant {
  /// Floating modal over a scrim.
  basic,

  /// Fills the screen. For compact widths only.
  fullScreen,
}

/// Where a basic dialog sits on screen.
///
/// Every position stays inside the system bars and above the soft keyboard.
/// Off-centre positions keep the theme's 56dp margin on medium and wider
/// screens.
enum M3EDialogPosition {
  /// Centred (default).
  center(Alignment.center),

  /// Vertically centred against the left edge.
  left(Alignment.centerLeft),

  /// Vertically centred against the right edge.
  right(Alignment.centerRight),

  /// Horizontally centred below the status bar.
  top(Alignment.topCenter),

  /// Horizontally centred above the navigation bar or keyboard.
  bottom(Alignment.bottomCenter);

  const M3EDialogPosition(this.alignment);

  /// Alignment inside the padded screen area.
  final Alignment alignment;
}
