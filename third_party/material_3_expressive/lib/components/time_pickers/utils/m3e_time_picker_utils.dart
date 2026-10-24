import 'package:material_ui/material_ui.dart';

import '../models/m3e_time.dart';

/// Time helpers shared by dial, input, and dialog pickers.
abstract final class M3ETimePickerUtils {
  const M3ETimePickerUtils._();

  /// clampRaw.

  static M3ETime clampRaw({required int hour, required int minute}) {
    return M3ETime(hour: hour.clamp(0, 23), minute: minute.clamp(0, 59));
  }

  /// clampTime.

  static M3ETime clampTime(M3ETime time) {
    return clampRaw(hour: time.hour, minute: time.minute);
  }

  /// use24HourFormat.

  static bool use24HourFormat(
    BuildContext context, {
    bool? alwaysUse24HourFormat,
  }) {
    if (alwaysUse24HourFormat != null) {
      return alwaysUse24HourFormat;
    }
    return MediaQuery.alwaysUse24HourFormatOf(context);
  }

  /// toTimeOfDay.

  static TimeOfDay toTimeOfDay(M3ETime time) {
    return TimeOfDay(hour: time.hour, minute: time.minute);
  }

  /// fromTimeOfDay.

  static M3ETime fromTimeOfDay(TimeOfDay time) {
    return M3ETime(hour: time.hour, minute: time.minute);
  }

  /// formatTime.

  static String formatTime(
    BuildContext context,
    M3ETime time, {
    bool? alwaysUse24HourFormat,
  }) {
    final MaterialLocalizations localizations = MaterialLocalizations.of(
      context,
    );
    return localizations.formatTimeOfDay(
      toTimeOfDay(time),
      alwaysUse24HourFormat: use24HourFormat(
        context,
        alwaysUse24HourFormat: alwaysUse24HourFormat,
      ),
    );
  }

  /// to24Hour.

  static int to24Hour(int hour12, {required bool pm}) {
    final int base = hour12 % 12;
    return pm ? base + 12 : base;
  }

  /// hour12From24.

  static int hour12From24(int hour24) {
    final int value = hour24 % 12;
    return value == 0 ? 12 : value;
  }

  /// isValidHourText.

  static bool isValidHourText(String text, {required bool use24HourFormat}) {
    final int? value = int.tryParse(text);
    if (value == null) {
      return false;
    }
    if (use24HourFormat) {
      return value >= 0 && value <= 23;
    }
    return value >= 1 && value <= 12;
  }

  /// isValidMinuteText.

  static bool isValidMinuteText(String text) {
    final int? value = int.tryParse(text);
    return value != null && value >= 0 && value <= 59;
  }

  /// parseInputTime.

  static M3ETime? parseInputTime({
    required String hourText,
    required String minuteText,
    required bool use24HourFormat,
    bool? isPm,
  }) {
    if (!isValidHourText(hourText, use24HourFormat: use24HourFormat) ||
        !isValidMinuteText(minuteText)) {
      return null;
    }
    final int minute = int.parse(minuteText);
    final int hourParsed = int.parse(hourText);
    final int hour = use24HourFormat
        ? hourParsed
        : to24Hour(hourParsed, pm: isPm ?? false);
    return clampTime(M3ETime(hour: hour, minute: minute));
  }
}
