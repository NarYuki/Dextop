/// Static English labels and helpers for the calendar grid.
abstract final class M3ECalendarLabels {
  const M3ECalendarLabels._();

  /// months.

  static const List<String> months = <String>[
    'January',
    'February',
    'March',
    'April',
    'May',
    'June',
    'July',
    'August',
    'September',
    'October',
    'November',
    'December',
  ];

  /// Weekday initials starting on Sunday.
  static const List<String> weekdayInitials = <String>[
    'S',
    'M',
    'T',
    'W',
    'T',
    'F',
    'S',
  ];

  /// The number of days in [month] of [year], accounting for leap years.
  static int daysInMonth(int year, int month) {
    return DateTime(year, month + 1, 0).day;
  }

  /// The weekday index (0 = Sunday) of the first day of the month.
  static int firstWeekday(int year, int month) {
    return DateTime(year, month).weekday % 7;
  }

  /// A "Month Year" label, for example "March 2026".
  static String monthYear(int year, int month) {
    return '${months[month - 1]} $year';
  }
}
