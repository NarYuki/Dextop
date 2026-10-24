import 'package:material_ui/material_ui.dart';

import '../../foundations/foundations.dart';
import '../dialogs/styles/m3e_dialog_theme.dart';
import 'enums/m3e_date_picker_enums.dart';
import 'm3e_date_picker_dialog.dart';
import 'm3e_date_range_picker_dialog.dart';
import 'models/m3e_date_picker_models.dart';
import 'res/m3e_date_picker_constants.dart';
import 'utils/m3e_date_picker_utils.dart';

export 'components/m3e_calendar_date_range_picker.dart';
export 'components/m3e_date_picker_actions.dart';
export 'components/m3e_date_picker_header.dart';
export 'components/m3e_day_picker.dart';
export 'components/m3e_input_date_picker_form_field.dart';
export 'components/m3e_input_date_range_picker_form_field.dart';
export 'components/m3e_month_picker.dart';
export 'components/m3e_year_picker.dart';
export 'enums/m3e_date_picker_enums.dart';
export 'm3e_calendar_date_picker.dart';
export 'm3e_date_picker_dialog.dart';
export 'm3e_date_range_picker_dialog.dart';
export 'models/m3e_calendar_labels.dart';
export 'models/m3e_date_picker_models.dart';
export 'res/m3e_date_picker_constants.dart';
export 'styles/m3e_date_picker_theme.dart';
export 'utils/m3e_date_picker_utils.dart';

/// Entry points for presenting M3E date picker dialogs.
abstract final class M3EDatePicker {
  const M3EDatePicker._();

  /// Shows a dialog to pick a single date.
  static Future<DateTime?> show(
    BuildContext context, {
    DateTime? initialDate,
    required DateTime firstDate,
    required DateTime lastDate,
    DateTime? currentDate,
    M3EDatePickerEntryMode initialEntryMode = M3EDatePickerEntryMode.calendar,
    M3ESelectableDayPredicate? selectableDayPredicate,
    String? helpText,
    String? cancelText,
    String? confirmText,
    M3EDatePickerMode initialCalendarMode = M3EDatePickerMode.day,
    String? errorFormatText,
    String? errorInvalidText,
    String? fieldHintText,
    String? fieldLabelText,
    TextInputType? keyboardType,
    bool barrierDismissible = true,
    String? restorationId,
    ValueChanged<M3EDatePickerEntryMode>? onDatePickerModeChange,
    EdgeInsets insetPadding = M3EDatePickerConstants.defaultInsetPadding,
    bool? resizeToAvoidBottomInset,
  }) {
    final DateTime? resolvedInitialDate = initialDate == null
        ? null
        : M3EDatePickerUtils.dateOnly(initialDate);
    final DateTime resolvedFirstDate = M3EDatePickerUtils.dateOnly(firstDate);
    final DateTime resolvedLastDate = M3EDatePickerUtils.dateOnly(lastDate);

    final M3EThemeData theme =
        M3EThemeScope.resolveOf(context) ?? M3ETheme.of(context);
    final M3EDialogTheme dialogTheme = theme.dialogTheme;

    return showGeneralDialog<DateTime>(
      context: context,
      barrierDismissible: barrierDismissible,
      barrierLabel: MaterialLocalizations.of(context).modalBarrierDismissLabel,
      barrierColor: dialogTheme.scrimColor(theme.colorScheme),
      transitionDuration: M3EMotion.medium2,
      pageBuilder: (BuildContext context, _, _) {
        return M3EScrimSystemUi.wrap(
          M3EComponentTheme(
            builder: (BuildContext context) => M3EDatePickerDialog(
              initialDate: resolvedInitialDate,
              firstDate: resolvedFirstDate,
              lastDate: resolvedLastDate,
              currentDate: currentDate,
              initialEntryMode: initialEntryMode,
              initialCalendarMode: initialCalendarMode,
              selectableDayPredicate: selectableDayPredicate,
              helpText: helpText,
              cancelText: cancelText,
              confirmText: confirmText,
              errorFormatText: errorFormatText,
              errorInvalidText: errorInvalidText,
              fieldHintText: fieldHintText,
              fieldLabelText: fieldLabelText,
              keyboardType: keyboardType,
              restorationId: restorationId,
              onDatePickerModeChange: onDatePickerModeChange,
              insetPadding: insetPadding,
              resizeToAvoidBottomInset: resizeToAvoidBottomInset,
            ),
          ),
        );
      },
      transitionBuilder: (context, animation, secondary, child) {
        final curved = CurvedAnimation(
          parent: animation,
          curve: M3EMotion.emphasizedDecelerate,
        );
        return FadeTransition(
          opacity: curved,
          child: ScaleTransition(
            scale: Tween<double>(
              begin: dialogTheme.entranceScale,
              end: 1,
            ).animate(curved),
            child: child,
          ),
        );
      },
    );
  }

  /// Shows a dialog to pick a date range.
  static Future<M3EDateRange?> showRange(
    BuildContext context, {
    DateTime? initialStartDate,
    DateTime? initialEndDate,
    required DateTime firstDate,
    required DateTime lastDate,
    DateTime? currentDate,
    M3EDatePickerEntryMode initialEntryMode = M3EDatePickerEntryMode.calendar,
    M3ESelectableDayForRangePredicate? selectableDayPredicate,
    String? helpText,
    String? cancelText,
    String? confirmText,
    String? errorFormatText,
    String? errorInvalidText,
    String? fieldStartHintText,
    String? fieldEndHintText,
    String? fieldStartLabelText,
    String? fieldEndLabelText,
    bool barrierDismissible = true,
    String? restorationId,
    ValueChanged<M3EDatePickerEntryMode>? onDatePickerModeChange,
    EdgeInsets insetPadding = M3EDatePickerConstants.defaultInsetPadding,
    bool? resizeToAvoidBottomInset,
  }) {
    final DateTime? resolvedInitialStartDate = initialStartDate == null
        ? null
        : M3EDatePickerUtils.dateOnly(initialStartDate);
    final DateTime? resolvedInitialEndDate = initialEndDate == null
        ? null
        : M3EDatePickerUtils.dateOnly(initialEndDate);
    final DateTime resolvedFirstDate = M3EDatePickerUtils.dateOnly(firstDate);
    final DateTime resolvedLastDate = M3EDatePickerUtils.dateOnly(lastDate);

    final M3EThemeData theme =
        M3EThemeScope.resolveOf(context) ?? M3ETheme.of(context);
    final M3EDialogTheme dialogTheme = theme.dialogTheme;

    return showGeneralDialog<M3EDateRange>(
      context: context,
      barrierDismissible: barrierDismissible,
      barrierLabel: MaterialLocalizations.of(context).modalBarrierDismissLabel,
      barrierColor: dialogTheme.scrimColor(theme.colorScheme),
      transitionDuration: M3EMotion.medium2,
      pageBuilder: (BuildContext context, _, _) {
        return M3EScrimSystemUi.wrap(
          M3EComponentTheme(
            builder: (BuildContext context) => M3EDateRangePickerDialog(
              initialStartDate: resolvedInitialStartDate,
              initialEndDate: resolvedInitialEndDate,
              firstDate: resolvedFirstDate,
              lastDate: resolvedLastDate,
              currentDate: currentDate,
              initialEntryMode: initialEntryMode,
              selectableDayPredicate: selectableDayPredicate,
              helpText: helpText,
              cancelText: cancelText,
              confirmText: confirmText,
              errorFormatText: errorFormatText,
              errorInvalidText: errorInvalidText,
              fieldStartHintText: fieldStartHintText,
              fieldEndHintText: fieldEndHintText,
              fieldStartLabelText: fieldStartLabelText,
              fieldEndLabelText: fieldEndLabelText,
              restorationId: restorationId,
              onDatePickerModeChange: onDatePickerModeChange,
              insetPadding: insetPadding,
              resizeToAvoidBottomInset: resizeToAvoidBottomInset,
            ),
          ),
        );
      },
      transitionBuilder: (context, animation, secondary, child) {
        final curved = CurvedAnimation(
          parent: animation,
          curve: M3EMotion.emphasizedDecelerate,
        );
        return FadeTransition(
          opacity: curved,
          child: ScaleTransition(
            scale: Tween<double>(
              begin: dialogTheme.entranceScale,
              end: 1,
            ).animate(curved),
            child: child,
          ),
        );
      },
    );
  }
}
