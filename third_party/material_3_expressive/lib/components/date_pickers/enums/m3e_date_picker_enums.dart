import 'package:material_3_expressive/components/date_pickers/m3e_date_pickers.dart'
    show M3ECalendarDatePicker, M3EDatePickerDialog, M3EDateRangePickerDialog;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ECalendarDatePicker, M3EDatePickerDialog, M3EDateRangePickerDialog;

/// Entry mode for [M3EDatePickerDialog] and [M3EDateRangePickerDialog].
enum M3EDatePickerEntryMode {
  /// Calendar only, with an icon button to switch to input mode.
  calendar,

  /// Text input only, with an icon button to switch to calendar mode.
  input,

  /// Calendar only with no way to switch to input mode.
  calendarOnly,

  /// Text input only with no way to switch to calendar mode.
  inputOnly,
}

/// Calendar sub-view for [M3ECalendarDatePicker].
enum M3EDatePickerMode {
  /// Day grid for the displayed month.
  day,

  /// Scrollable year grid.
  year,
}
