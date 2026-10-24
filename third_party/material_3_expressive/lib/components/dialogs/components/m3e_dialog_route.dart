part of '../m3e_dialogs.dart';

M3EThemeData _themeOf(BuildContext context) =>
    M3EThemeScope.resolveOf(context) ?? M3ETheme.of(context);

Future<bool?> _discardPrompt(
  BuildContext context,
  M3EDialogDiscardLabels labels,
) => M3EDialog.showDiscardConfirmation(context, labels: labels);

/// Pushes a spring-animated dialog route with the shared dismiss path.
Future<T?> _pushDialog<T>(
  BuildContext context, {
  required M3EDialogVariant variant,
  required bool barrierDismissible,
  required WidgetBuilder builder,
  required M3EDialogController? controller,
  required Future<bool> Function()? onDismissRequest,
  required bool autofocusFirst,
}) {
  final M3EThemeData theme = _themeOf(context);
  final M3EDialogTheme dialogTheme = theme.dialogTheme;
  return Navigator.of(context, rootNavigator: true).push<T>(
    RawDialogRoute<T>(
      barrierDismissible: barrierDismissible,
      barrierLabel: variant == M3EDialogVariant.basic
          ? M3EDialogStrings.dismiss
          : M3EDialogStrings.fullScreen,
      barrierColor: dialogTheme.scrimColor(theme.colorScheme),
      transitionDuration: M3EDialogTransition.durationFor(dialogTheme, variant),
      traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
      pageBuilder: (BuildContext context, _, _) {
        return M3EComponentTheme(
          builder: (BuildContext context) => M3EDialogDismissScope(
            controller: controller,
            onDismissRequest: onDismissRequest,
            autofocusFirst: autofocusFirst,
            variant: variant,
            discardPrompt: _discardPrompt,
            child: builder(context),
          ),
        );
      },
      transitionBuilder: (context, animation, _, child) => M3EDialogTransition(
        animation: animation,
        variant: variant,
        dialogTheme: dialogTheme,
        child: child,
      ),
    ),
  );
}

/// Basic dialog that confirms discarding unsaved changes.
Widget _discardDialog(BuildContext context, M3EDialogDiscardLabels labels) {
  final NavigatorState navigator = Navigator.of(context);
  final String? supporting = labels.supportingText;
  return M3EDialog(
    title: labels.title,
    content: supporting == null ? null : Text(supporting),
    actions: <Widget>[
      m3eDialogTextAction(
        label: labels.keepLabel,
        onPressed: () => navigator.pop(false),
      ),
      m3eDialogTextAction(
        label: labels.discardLabel,
        onPressed: () => navigator.pop(true),
      ),
    ],
  );
}
