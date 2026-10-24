import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../buttons/m3e_buttons.dart';

/// Cancel and confirm actions for time picker dialogs.
class M3ETimePickerActions extends StatelessWidget {
  /// M3ETimePickerActions.
  const M3ETimePickerActions({
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

  /// Optional dial/input toggle at the far start (bottom-left).
  final Widget? entryModeButton;

  @override
  Widget build(BuildContext context) {
    final dialogTheme = M3ETheme.of(context).dialogTheme;

    return Padding(
      padding: EdgeInsetsDirectional.only(top: dialogTheme.actionGap),
      child: Row(
        children: <Widget>[
          ?entryModeButton,
          Expanded(
            child: Align(
              alignment: AlignmentDirectional.centerEnd,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  M3EButton(
                    style: M3EButtonStyle.text,
                    onPressed: onCancel,
                    child: Text(cancelText),
                  ),
                  SizedBox(width: dialogTheme.actionGap),
                  M3EButton(onPressed: onConfirm, child: Text(confirmText)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
