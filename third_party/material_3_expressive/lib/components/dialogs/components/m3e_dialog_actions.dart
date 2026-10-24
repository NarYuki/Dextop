import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_dialog_theme.dart';
import '../utils/m3e_dialog_action_button.dart';

/// Trailing dialog actions that stack (confirm on top) when they overflow.
///
/// Text buttons inside pick up the dialog action tokens.
class M3EDialogActions extends StatelessWidget {
  /// M3EDialogActions.
  const M3EDialogActions({
    required this.actions,
    this.leadingAction,
    super.key,
  });

  /// Dismissive first, confirming last (closest to the trailing edge).
  final List<Widget> actions;

  /// Optional third action at the leading edge. Use with caution.
  final Widget? leadingAction;

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final appearance = theme.dialogTheme.appearance;
    return m3eDialogActionScope(
      color: appearance.resolveAction(theme.colorScheme),
      textStyle: appearance.resolveActionText(theme),
      overlay: appearance.actionOverlay,
      child: _buildRow(theme.dialogTheme),
    );
  }

  Widget _buildRow(M3EDialogTheme dialogTheme) {
    final bar = OverflowBar(
      spacing: dialogTheme.actionGap,
      overflowSpacing: dialogTheme.stackedActionGap,
      alignment: MainAxisAlignment.end,
      overflowAlignment: OverflowBarAlignment.end,
      overflowDirection: VerticalDirection.up,
      children: actions,
    );
    if (leadingAction == null) {
      return bar;
    }
    return Row(
      children: <Widget>[
        leadingAction!,
        SizedBox(width: dialogTheme.actionGap),
        Expanded(child: bar),
      ],
    );
  }
}
