import 'dart:math' as math;

import '../res/m3e_date_picker_constants.dart';

/// Date helpers shared by calendar, input, and dialog pickers.
abstract final class M3EDatePickerUtils {
  const M3EDatePickerUtils._();

  /// dateOnly.

  static DateTime dateOnly(DateTime date) {
    return DateTime(date.year, date.month, date.day);
  }

  /// isSameDay.

  static bool isSameDay(DateTime? a, DateTime? b) {
    if (a == null || b == null) {
      return false;
    }
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }

  /// isInRange.

  static bool isInRange(DateTime day, DateTime start, DateTime end) {
    final DateTime normalized = dateOnly(day);
    final DateTime rangeStart = dateOnly(start);
    final DateTime rangeEnd = dateOnly(end);
    return !normalized.isBefore(rangeStart) && !normalized.isAfter(rangeEnd);
  }

  /// clampDate.

  static DateTime clampDate(
    DateTime date,
    DateTime firstDate,
    DateTime lastDate,
  ) {
    if (date.isBefore(firstDate)) {
      return firstDate;
    }
    if (date.isAfter(lastDate)) {
      return lastDate;
    }
    return date;
  }

  /// getMonth.

  static DateTime getMonth(int year, int month) {
    return DateTime(year, month);
  }

  /// daysInMonth.

  static int daysInMonth(int year, int month) {
    return DateTime(year, month + 1, 0).day;
  }

  /// monthDelta.

  static int monthDelta(DateTime startMonth, DateTime endMonth) {
    return (endMonth.year - startMonth.year) * 12 +
        endMonth.month -
        startMonth.month;
  }

  /// addMonthsToMonthDate.

  static DateTime addMonthsToMonthDate(DateTime monthDate, int delta) {
    return DateTime(monthDate.year, monthDate.month + delta);
  }

  /// getDay.

  static DateTime getDay(int year, int month, int day) {
    return DateTime(year, month, day);
  }

  /// normalizeSelectedDay.

  static DateTime normalizeSelectedDay(DateTime selected, DateTime monthDate) {
    final int days = daysInMonth(monthDate.year, monthDate.month);
    final int preferredDay = math.min(selected.day, days);
    return DateTime(monthDate.year, monthDate.month, preferredDay);
  }

  /// isSelectable.

  static bool isSelectable(
    DateTime date,
    DateTime firstDate,
    DateTime lastDate, {
    bool Function(DateTime day)? predicate,
  }) {
    if (date.isBefore(firstDate) || date.isAfter(lastDate)) {
      return false;
    }
    return predicate?.call(date) ?? true;
  }

  /// Week rows required to display [month] (4–6).
  static int dayPickerRowCount(DateTime month, int firstDayOfWeekIndex) {
    final int year = month.year;
    final int monthIndex = month.month;
    final int days = daysInMonth(year, monthIndex);
    final int offset =
        (DateTime(year, monthIndex).weekday - firstDayOfWeekIndex) % 7;
    return ((days + offset) / 7).ceil();
  }

  /// Height of the day calendar view for [month].
  static double calendarDayViewHeight(DateTime month, int firstDayOfWeekIndex) {
    final int rows = dayPickerRowCount(month, firstDayOfWeekIndex);
    return M3EDatePickerConstants.subHeaderHeight +
        M3EDatePickerConstants.weekdayRowHeight +
        M3EDatePickerConstants.dayPickerRowHeight * rows +
        M3EDatePickerConstants.dayGridTopPadding;
  }

  /// Height of the year calendar view in a dialog.
  static double calendarYearViewHeight(
    DateTime firstDate,
    DateTime lastDate, {
    bool includeSubHeader = true,
  }) {
    final int yearCount = lastDate.year - firstDate.year + 1;
    var height = yearPickerGridHeight(yearCount);
    if (includeSubHeader) {
      height += M3EDatePickerConstants.subHeaderHeight;
    }
    return height;
  }

  /// Grid rows required for [yearCount] years.
  static int yearPickerRowCount(int yearCount) {
    if (yearCount <= 0) {
      return 0;
    }
    return (yearCount / M3EDatePickerConstants.yearPickerColumnCount).ceil();
  }

  /// Natural height of the year grid for [yearCount] years.
  static double yearPickerGridNaturalHeight(int yearCount) {
    if (yearCount <= 0) {
      return 0;
    }
    final int rows = yearPickerRowCount(yearCount);
    return M3EDatePickerConstants.yearPickerPadding * 2 +
        rows * M3EDatePickerConstants.yearPickerRowHeight +
        (rows - 1) * M3EDatePickerConstants.yearPickerRowSpacing;
  }

  /// Year grid height capped at [M3EDatePickerConstants.maxDayPickerHeight].
  static double yearPickerGridHeight(int yearCount) {
    return math.min(
      yearPickerGridNaturalHeight(yearCount),
      M3EDatePickerConstants.maxDayPickerHeight,
    );
  }
}
