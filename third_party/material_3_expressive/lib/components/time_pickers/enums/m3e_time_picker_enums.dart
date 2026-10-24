import 'package:material_3_expressive/components/time_pickers/m3e_time_picker_dialog.dart'
    show M3ETimePickerDialog;
import 'package:material_3_expressive/components/time_pickers/m3e_time_pickers.dart'
    show M3ETimePickerDialog;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ETimePickerDialog;

/// Entry mode for [M3ETimePickerDialog].
enum M3ETimePickerEntryMode {
  /// Dial UI; user can switch to input.
  dial,

  /// Text input UI; user can switch to dial.
  input,

  /// Dial UI only (no mode toggle).
  dialOnly,

  /// Text input UI only (no mode toggle).
  inputOnly,
}

/// Which unit the dial is currently editing.
enum M3ETimePickerMode {
  /// Editing the hour hand / field.
  hour,

  /// Editing the minute hand / field.
  minute,
}
