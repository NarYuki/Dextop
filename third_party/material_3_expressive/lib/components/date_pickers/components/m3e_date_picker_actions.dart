import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../buttons/m3e_buttons.dart';
import '../res/m3e_date_picker_constants.dart';

/// Cancel and confirm actions for date picker dialogs.
class M3EDatePickerActions extends StatelessWidget {
  /// M3EDatePickerActions.
  const M3EDatePickerActions({
    required this.onCancel,
    required this.onConfirm,
    required this.cancelText,
    required this.confirmText,
    this.entryModeButton,
    super.key,
  });

  /// onCancel.
  final VoidCallback onCancel;

  /// onConfirm.
  final VoidCallback onConfirm;

  /// cancelText.
  final String cancelText;

  /// confirmText.
  final String confirmText;

  /// Kept for API compatibility; entry mode lives in the date picker header.
  final Widget? entryModeButton;

  @override
  Widget build(BuildContext context) {
    final dialogTheme = M3ETheme.of(context).dialogTheme;

    // Outer content padding is applied by the dialog; keep action bar compact.
    return ConstrainedBox(
      constraints: const BoxConstraints(
        minHeight: M3EDatePickerConstants.actionsMinHeight,
      ),
      child: Align(
        alignment: AlignmentDirectional.centerEnd,
        child: OverflowBar(
          spacing: dialogTheme.actionGap,
          children: <Widget>[
            M3EButton(
              style: M3EButtonStyle.text,
              onPressed: onCancel,
              child: Text(cancelText),
            ),
            M3EButton(onPressed: onConfirm, child: Text(confirmText)),
          ],
        ),
      ),
    );
  }
}
