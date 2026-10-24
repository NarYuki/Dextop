import 'package:flutter/widgets.dart';

/// Layout constants for M3E time picker dialogs and dials.
abstract final class M3ETimePickerConstants {
  const M3ETimePickerConstants._();

  /// Dial portrait dialog content size (material_ui `_kTimePickerPortraitSize`).
  static const Size dialPortraitDialogSize = Size(310, 468);

  /// Dial landscape dialog content size (material_ui `_kTimePickerLandscapeSize`).
  static const Size dialLandscapeDialogSize = Size(524, 342);

  /// Input dialog base size (material_ui `_kTimePickerInputSize`).
  static const Size inputDialogSize = Size(312, 252);

  /// Start inset before the dial in landscape.
  static const double dialLandscapeStartPadding = 64;

  /// Horizontal inset around the dial in portrait.
  static const double dialPortraitHorizontalPadding = 12;

  /// Top inset above the dial in portrait before interactive adjustment.
  static const double dialPortraitTopPadding = 36;

  /// M3 subtracts this from input base width for 12-hour layouts.
  static const double inputDialogWidthInset12Hour = 32;

  /// Gap between period control and fields when computing 24h input width.
  static const double inputDialogPeriodGap = 12;

  /// dialogSizeAnimationDuration.
  static const Duration dialogSizeAnimationDuration = Duration(
    milliseconds: 200,
  );

  /// Caps text scale used when sizing the dialog (material_ui uses 1.1).
  static const double maxTextScaleFactor = 1.1;

  /// fontSizeToScale.
  static const double fontSizeToScale = 14;

  /// defaultInsetPadding.
  static const EdgeInsets defaultInsetPadding = EdgeInsets.symmetric(
    horizontal: 16,
    vertical: 24,
  );
}
