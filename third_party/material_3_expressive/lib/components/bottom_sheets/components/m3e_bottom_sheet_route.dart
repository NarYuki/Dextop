part of '../m3e_bottom_sheets.dart';

M3EThemeData _themeOf(BuildContext context) =>
    M3EThemeScope.resolveOf(context) ?? M3ETheme.of(context);

/// Pushes the modal sheet route. The host paints its own spring scrim.
Future<T?> _pushModalSheet<T>(
  BuildContext context, {
  required WidgetBuilder builder,
  required M3EBottomSheetTheme? theme,
  required M3EBottomSheetLabels labels,
  required bool useRootNavigator,
  required Widget Function(Animation<double>, M3EBottomSheetTheme) host,
}) {
  final M3EBottomSheetTheme sheetTheme =
      theme ?? _themeOf(context).bottomSheetTheme;
  final Duration enter = m3eBottomSheetSpringSettle(sheetTheme.enterSpring);
  final Duration scrim = m3eBottomSheetSpringSettle(sheetTheme.scrimSpring);
  return Navigator.of(context, rootNavigator: useRootNavigator).push<T>(
    RawDialogRoute<T>(
      barrierColor: null,
      barrierDismissible: false,
      transitionDuration: enter > scrim ? enter : scrim,
      traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
      pageBuilder: (BuildContext context, Animation<double> animation, _) {
        return M3EComponentTheme(builder: (_) => host(animation, sheetTheme));
      },
      transitionBuilder: (_, _, _, Widget child) => child,
    ),
  );
}
