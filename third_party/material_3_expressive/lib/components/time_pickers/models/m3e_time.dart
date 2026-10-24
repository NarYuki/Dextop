import 'package:flutter/foundation.dart';

/// An immutable wall-clock time in 24-hour form.
@immutable
class M3ETime {
  /// M3ETime.
  const M3ETime({required this.hour, required this.minute})
    : assert(hour >= 0 && hour < 24, 'hour must be 0..23'),
      assert(minute >= 0 && minute < 60, 'minute must be 0..59');

  /// hour.

  final int hour;

  /// minute.
  final int minute;

  /// Whether the time falls in the afternoon (12:00 onward).
  bool get isPm => hour >= 12;

  /// The hour in 12-hour form (1..12).
  int get hourOf12 {
    final int value = hour % 12;
    return value == 0 ? 12 : value;
  }

  /// copyWith.

  M3ETime copyWith({int? hour, int? minute}) {
    return M3ETime(hour: hour ?? this.hour, minute: minute ?? this.minute);
  }

  @override
  bool operator ==(Object other) {
    return other is M3ETime && other.hour == hour && other.minute == minute;
  }

  @override
  int get hashCode => Object.hash(hour, minute);

  /// A zero-padded "HH:MM" style label using the 12-hour hour.
  String get minuteLabel => minute.toString().padLeft(2, '0');
}
