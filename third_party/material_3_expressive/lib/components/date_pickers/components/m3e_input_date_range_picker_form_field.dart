import 'package:material_ui/material_ui.dart';

import '../models/m3e_date_picker_models.dart';
import 'm3e_input_date_picker_form_field.dart';

/// Text fields for entering a date range in a picker dialog.
class M3EInputDateRangePickerFormField extends StatelessWidget {
  /// M3EInputDateRangePickerFormField.
  const M3EInputDateRangePickerFormField({
    required this.firstDate,
    required this.lastDate,
    this.initialStartDate,
    this.initialEndDate,
    this.onStartDateSaved,
    this.onEndDateSaved,
    this.selectableDayPredicate,
    this.errorFormatText,
    this.errorInvalidText,
    this.fieldStartHintText,
    this.fieldEndHintText,
    this.fieldStartLabelText,
    this.fieldEndLabelText,
    super.key,
  });

  /// firstDate.

  final DateTime firstDate;

  /// lastDate.
  final DateTime lastDate;

  /// initialStartDate.
  final DateTime? initialStartDate;

  /// initialEndDate.
  final DateTime? initialEndDate;

  /// onStartDateSaved.
  final ValueChanged<DateTime>? onStartDateSaved;

  /// onEndDateSaved.
  final ValueChanged<DateTime>? onEndDateSaved;

  /// selectableDayPredicate.
  final M3ESelectableDayForRangePredicate? selectableDayPredicate;

  /// errorFormatText.
  final String? errorFormatText;

  /// errorInvalidText.
  final String? errorInvalidText;

  /// fieldStartHintText.
  final String? fieldStartHintText;

  /// fieldEndHintText.
  final String? fieldEndHintText;

  /// fieldStartLabelText.
  final String? fieldStartLabelText;

  /// fieldEndLabelText.
  final String? fieldEndLabelText;

  @override
  Widget build(BuildContext context) {
    final MaterialLocalizations localizations = MaterialLocalizations.of(
      context,
    );
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Expanded(
          child: M3EInputDatePickerFormField(
            firstDate: firstDate,
            lastDate: lastDate,
            initialDate: initialStartDate,
            onDateSaved: onStartDateSaved,
            selectableDayPredicate: selectableDayPredicate,
            errorFormatText: errorFormatText,
            errorInvalidText: errorInvalidText,
            fieldHintText: fieldStartHintText,
            fieldLabelText:
                fieldStartLabelText ?? localizations.dateRangeStartLabel,
            autofocus: true,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: M3EInputDatePickerFormField(
            firstDate: firstDate,
            lastDate: lastDate,
            initialDate: initialEndDate,
            onDateSaved: onEndDateSaved,
            selectableDayPredicate: selectableDayPredicate,
            errorFormatText: errorFormatText,
            errorInvalidText: errorInvalidText,
            fieldHintText: fieldEndHintText,
            fieldLabelText:
                fieldEndLabelText ?? localizations.dateRangeEndLabel,
          ),
        ),
      ],
    );
  }
}
