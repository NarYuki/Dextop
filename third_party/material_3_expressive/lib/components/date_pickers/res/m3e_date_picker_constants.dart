import 'package:flutter/widgets.dart';

/// Layout constants for M3E date picker dialogs and calendars.
abstract final class M3EDatePickerConstants {
  const M3EDatePickerConstants._();

  /// calendarPortraitDialogSize.

  static const Size calendarPortraitDialogSize = Size(360, 568);

  /// calendarLandscapeDialogSize.
  static const Size calendarLandscapeDialogSize = Size(496, 346);

  /// inputPortraitDialogSize.
  static const Size inputPortraitDialogSize = Size(328, 270);

  /// inputLandscapeDialogSize.
  static const Size inputLandscapeDialogSize = Size(496, 160);

  /// inputRangeLandscapeDialogSize.
  static const Size inputRangeLandscapeDialogSize = Size(496, 160);

  /// dialogSizeAnimationDuration.

  static const Duration dialogSizeAnimationDuration = Duration(
    milliseconds: 200,
  );

  /// monthScrollDuration.
  static const Duration monthScrollDuration = Duration(milliseconds: 200);

  /// inputFormPortraitHeight.

  static const double inputFormPortraitHeight = 98;

  /// inputFormLandscapeHeight.
  static const double inputFormLandscapeHeight = 108;

  /// maxTextScaleFactor.

  static const double maxTextScaleFactor = 3;

  /// maxRangeTextScaleFactor.
  static const double maxRangeTextScaleFactor = 1.3;

  /// maxHeaderTextScaleFactor.
  static const double maxHeaderTextScaleFactor = 1.6;

  /// maxHeaderWithEntryTextScaleFactor.
  static const double maxHeaderWithEntryTextScaleFactor = 1.4;

  /// maxHelpPortraitTextScaleFactor.
  static const double maxHelpPortraitTextScaleFactor = 1.6;

  /// maxHelpLandscapeTextScaleFactor.
  static const double maxHelpLandscapeTextScaleFactor = 1.4;

  /// fontSizeToScale.
  static const double fontSizeToScale = 14;

  /// dayPickerRowHeight.

  static const double dayPickerRowHeight = 48;

  /// maxDayPickerRowCount.
  static const int maxDayPickerRowCount = 6;

  /// maxDayPickerHeight.
  static const double maxDayPickerHeight =
      dayPickerRowHeight * (maxDayPickerRowCount + 1);

  /// monthPickerHorizontalPaddingPortrait.

  static const double monthPickerHorizontalPaddingPortrait = 12;

  /// monthPickerHorizontalPaddingOther.
  static const double monthPickerHorizontalPaddingOther = 8;

  /// monthNavButtonsWidth.
  static const double monthNavButtonsWidth = 108;

  /// yearPickerColumnCount.

  static const int yearPickerColumnCount = 3;

  /// yearPickerPadding.
  static const double yearPickerPadding = 16;

  /// yearPickerRowHeight.
  static const double yearPickerRowHeight = 52;

  /// yearPickerRowSpacing.
  static const double yearPickerRowSpacing = 8;

  /// subHeaderHeight.

  static const double subHeaderHeight = 52;

  /// headerPortraitHeight.
  static const double headerPortraitHeight = 120;

  /// headerLandscapeWidth.
  static const double headerLandscapeWidth = 152;

  /// headerPaddingLandscape.
  static const double headerPaddingLandscape = 16;

  /// actionsMinHeight.
  static const double actionsMinHeight = 52;

  /// weekdayRowHeight.
  static const double weekdayRowHeight = 24;

  /// dayGridTopPadding.
  static const double dayGridTopPadding = 4;

  /// Calendar body height inside portrait dialogs at minimum header height.
  static const double dialogPickerBodyHeight = 395;

  /// defaultInsetPadding.

  static const EdgeInsets defaultInsetPadding = EdgeInsets.symmetric(
    horizontal: 16,
    vertical: 24,
  );

  /// modeToggleButtonMaxScaleFactor.

  static const double modeToggleButtonMaxScaleFactor = 2;

  /// dayPickerGridPortraitMaxScaleFactor.
  static const double dayPickerGridPortraitMaxScaleFactor = 2;

  /// dayPickerGridLandscapeMaxScaleFactor.
  static const double dayPickerGridLandscapeMaxScaleFactor = 1.5;
}
