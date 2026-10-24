import 'package:flutter/widgets.dart';

import '../../buttons/m3e_buttons.dart';

/// Text button for dialog-built actions.
///
/// Styling comes from the action [m3eDialogActionScope], the same as for
/// buttons callers pass in, so every dialog action matches.
M3EButton m3eDialogTextAction({
  required String label,
  required VoidCallback? onPressed,
}) {
  return M3EButton(
    style: M3EButtonStyle.text,
    onPressed: onPressed,
    child: Text(label),
  );
}

/// Applies the dialog action tokens to text buttons in [child].
///
/// [color] drives the label and hover layer; [overlay] maps states to it.
Widget m3eDialogActionScope({
  required Color color,
  required TextStyle textStyle,
  required Color? Function(Color color, Set<WidgetState> states) overlay,
  required Widget child,
}) {
  return M3EButtonDecorationScope(
    styles: const <M3EButtonStyle>{M3EButtonStyle.text},
    decoration: M3EButtonDecoration(
      foregroundColor: WidgetStateProperty.resolveWith(
        (Set<WidgetState> states) =>
            states.contains(WidgetState.disabled) ? null : color,
      ),
      overlayColor: WidgetStateProperty.resolveWith(
        (Set<WidgetState> states) => overlay(color, states),
      ),
      textStyle: textStyle,
    ),
    child: child,
  );
}
