import 'package:material_ui/material_ui.dart';

import '../../foundations/foundations.dart';
import 'enums/m3e_time_picker_enums.dart';
import 'm3e_time_picker_dialog.dart';
import 'models/m3e_time.dart';
import 'res/m3e_time_picker_constants.dart';
import 'utils/m3e_time_picker_utils.dart';

export 'components/m3e_day_period_control.dart';
export 'components/m3e_dial_time_picker.dart';
export 'components/m3e_hour_minute_text_field.dart';
export 'components/m3e_input_time_picker_form_field.dart';
export 'components/m3e_time_picker_actions.dart';
export 'components/m3e_time_picker_dialog_content.dart';
export 'components/m3e_time_picker_header.dart';
export 'enums/m3e_time_picker_enums.dart';
export 'm3e_time_picker_dialog.dart';
export 'models/m3e_time.dart';
export 'res/m3e_time_picker_constants.dart';
export 'styles/m3e_time_picker_theme.dart';
export 'utils/m3e_time_picker_utils.dart';

/// Entry points for presenting M3E time picker dialogs.
abstract final class M3ETimePicker {
  const M3ETimePicker._();

  /// Shows a dialog to pick a single time.
  static Future<M3ETime?> show(
    BuildContext context, {
    required M3ETime initialTime,
    M3ETimePickerEntryMode initialEntryMode = M3ETimePickerEntryMode.dial,
    String? helpText,
    String? cancelText,
    String? confirmText,
    String? errorInvalidText,
    String? hourLabelText,
    String? minuteLabelText,
    Orientation? orientation,
    bool? alwaysUse24HourFormat,
    bool emptyInitialInput = false,
    bool barrierDismissible = true,
    String? restorationId,
    ValueChanged<M3ETimePickerEntryMode>? onTimePickerModeChange,
    EdgeInsets insetPadding = M3ETimePickerConstants.defaultInsetPadding,
    bool? resizeToAvoidBottomInset,
  }) {
    final M3ETime normalized = M3ETimePickerUtils.clampTime(initialTime);

    final M3EThemeData theme =
        M3EThemeScope.resolveOf(context) ?? M3ETheme.of(context);
    final dialogTheme = theme.dialogTheme;

    return showGeneralDialog<M3ETime>(
      context: context,
      barrierDismissible: barrierDismissible,
      barrierLabel: MaterialLocalizations.of(context).modalBarrierDismissLabel,
      barrierColor: dialogTheme.scrimColor(theme.colorScheme),
      transitionDuration: M3EMotion.medium2,
      pageBuilder: (BuildContext context, _, _) {
        return M3EScrimSystemUi.wrap(
          M3EComponentTheme(
            builder: (BuildContext context) => M3ETimePickerDialog(
              initialTime: normalized,
              initialEntryMode: initialEntryMode,
              helpText: helpText,
              cancelText: cancelText,
              confirmText: confirmText,
              errorInvalidText: errorInvalidText,
              hourLabelText: hourLabelText,
              minuteLabelText: minuteLabelText,
              orientation: orientation,
              alwaysUse24HourFormat: alwaysUse24HourFormat,
              emptyInitialInput: emptyInitialInput,
              restorationId: restorationId,
              onTimePickerModeChange: onTimePickerModeChange,
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
