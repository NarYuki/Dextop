part of '../m3e_side_sheets.dart';

/// Pushes the modal side sheet route. The host paints its own spring scrim.
Future<T?> _pushSideSheet<T>(
  BuildContext context, {
  required M3ESideSheet sheet,
  required bool isDismissible,
  required Future<bool> Function()? onDismissRequest,
  required M3ESideSheetController? controller,
  required bool useRootNavigator,
}) {
  final M3EThemeData m3e =
      M3EThemeScope.resolveOf(context) ?? M3ETheme.of(context);
  final M3ESideSheetTheme theme = sheet.theme ?? m3e.sideSheetTheme;
  final Duration enter = m3eSideSheetSpringSettle(theme.motion.enterSpring);
  final Duration scrim = m3eSideSheetSpringSettle(theme.motion.scrimSpring);
  return Navigator.of(context, rootNavigator: useRootNavigator).push<T>(
    RawDialogRoute<T>(
      barrierColor: null,
      barrierDismissible: false,
      transitionDuration: enter > scrim ? enter : scrim,
      traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
      pageBuilder: (BuildContext context, Animation<double> animation, _) {
        return M3EComponentTheme(
          builder: (_) => _M3ESideSheetModalHost(
            animation: animation,
            sheet: sheet,
            theme: theme,
            isDismissible: isDismissible,
            onDismissRequest: onDismissRequest,
            controller: controller,
          ),
        );
      },
      transitionBuilder: (_, _, _, Widget child) => child,
    ),
  );
}
