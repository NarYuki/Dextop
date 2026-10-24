import 'package:flutter/widgets.dart';

import '../../foundations/foundations.dart';
import '../side_sheets/m3e_side_sheets.dart';
import 'components/m3e_bottom_sheet_frame.dart';
import 'components/m3e_bottom_sheet_modal_host.dart';
import 'controllers/m3e_bottom_sheet_controller.dart';
import 'controllers/m3e_bottom_sheet_scroll_controller.dart';
import 'enums/m3e_bottom_sheet_enums.dart';
import 'models/m3e_bottom_sheet_labels.dart';
import 'styles/m3e_bottom_sheet_theme.dart';
import 'utils/m3e_bottom_sheet_spring.dart';

export 'controllers/m3e_bottom_sheet_controller.dart';
export 'controllers/m3e_bottom_sheet_scroll_controller.dart'
    show M3EBottomSheetDragTarget, M3EBottomSheetScrollController;
export 'enums/m3e_bottom_sheet_enums.dart';
export 'models/m3e_bottom_sheet_labels.dart';
export 'styles/m3e_bottom_sheet_theme.dart';

part 'components/m3e_bottom_sheet_route.dart';

/// A Material 3 Expressive bottom sheet.
///
/// Shows secondary content anchored to the bottom of the screen, up to 640dp
/// wide with 28dp top corners. Use [M3EBottomSheet.show] for a modal sheet
/// over a scrim and [M3EBottomSheet.standard] for a sheet that sits next to
/// the main UI. The optional drag handle resizes the sheet by drag, tap,
/// Space or Enter.
class M3EBottomSheet extends StatelessWidget {
  /// A modal sheet surface. [M3EBottomSheet.show] presents it in a route;
  /// placed directly, dragging it away pops the current route.
  const M3EBottomSheet({
    required this.child,
    this.showDragHandle = true,
    this.enableDrag = true,
    this.initialValue = M3EBottomSheetValue.collapsed,
    this.expandToFullScreen = false,
    this.fullScreenTitle,
    this.controller,
    this.onValueChanged,
    this.theme,
    this.labels = const M3EBottomSheetLabels(),
    super.key,
  }) : variant = M3EBottomSheetVariant.modal,
       isDismissible = true,
       previewHeight = null;

  /// A standard sheet that co-exists with the main UI (no scrim).
  ///
  /// Fills its parent and anchors to the bottom edge; place it in a
  /// `Stack` with `Positioned.fill`. Taps outside the sheet reach the UI
  /// behind it.
  const M3EBottomSheet.standard({
    required this.child,
    this.showDragHandle = true,
    this.enableDrag = true,
    this.isDismissible = false,
    this.initialValue = M3EBottomSheetValue.collapsed,
    this.previewHeight,
    this.expandToFullScreen = false,
    this.fullScreenTitle,
    this.controller,
    this.onValueChanged,
    this.theme,
    this.labels = const M3EBottomSheetLabels(),
    super.key,
  }) : variant = M3EBottomSheetVariant.standard;

  /// Sheet content. Vertical lists that use the primary scroll controller
  /// resize the sheet before they scroll.
  final Widget child;

  /// Whether to show the drag handle.
  final bool showDragHandle;

  /// Standard or modal.
  final M3EBottomSheetVariant variant;

  /// Whether dragging resizes the sheet.
  final bool enableDrag;

  /// Standard: whether dragging down (or the handle cycle) may hide it.
  final bool isDismissible;

  /// Height to rest at first. Collapsed is capped at half the screen.
  final M3EBottomSheetValue initialValue;

  /// Standard: optional peek height below the collapsed height.
  final double? previewHeight;

  /// Adds a full-screen height with a collapse (standard) or close (modal)
  /// header. The sheet then spans the full view width.
  final bool expandToFullScreen;

  /// Title in the full-screen header.
  final String? fullScreenTitle;

  /// Drives the sheet without dragging.
  final M3EBottomSheetController? controller;

  /// Called when the sheet rests at a new height.
  final ValueChanged<M3EBottomSheetValue>? onValueChanged;

  /// Per-sheet theme. Null uses `M3EThemeData.bottomSheetTheme`.
  final M3EBottomSheetTheme? theme;

  /// Accessibility strings.
  final M3EBottomSheetLabels labels;

  /// The scroll controller of the enclosing sheet, if any.
  static M3EBottomSheetScrollController? scrollControllerOf(
    BuildContext context,
  ) {
    final ScrollController? controller = PrimaryScrollController.maybeOf(
      context,
    );
    return controller is M3EBottomSheetScrollController ? controller : null;
  }

  /// Presents a modal bottom sheet and completes with the popped result.
  ///
  /// It opens at most half the screen tall and can be pulled up to its
  /// content height. Tapping the scrim, swiping down, Escape and back close
  /// it; [onDismissRequest] can veto those.
  static Future<T?> show<T>(
    BuildContext context, {
    required WidgetBuilder builder,
    bool showDragHandle = true,
    bool isDismissible = true,
    bool enableDrag = true,
    M3EBottomSheetValue initialValue = M3EBottomSheetValue.collapsed,
    bool expandToFullScreen = false,
    String? fullScreenTitle,
    M3EBottomSheetController? controller,
    ValueChanged<M3EBottomSheetValue>? onValueChanged,
    Future<bool> Function()? onDismissRequest,
    M3EBottomSheetTheme? theme,
    M3EBottomSheetLabels labels = const M3EBottomSheetLabels(),
    bool useRootNavigator = true,
  }) {
    return _pushModalSheet<T>(
      context,
      builder: builder,
      theme: theme,
      labels: labels,
      useRootNavigator: useRootNavigator,
      host: (Animation<double> animation, M3EBottomSheetTheme sheetTheme) {
        return M3EBottomSheetModalHost(
          animation: animation,
          theme: sheetTheme,
          labels: labels,
          showDragHandle: showDragHandle,
          isDismissible: isDismissible,
          enableDrag: enableDrag,
          initialValue: initialValue,
          expandToFullScreen: expandToFullScreen,
          fullScreenTitle: fullScreenTitle,
          controller: controller,
          onValueChanged: onValueChanged,
          onDismissRequest: onDismissRequest,
          child: Builder(builder: builder),
        );
      },
    );
  }

  /// Shows a modal bottom sheet on compact and medium windows, and an
  /// `M3ESideSheet` with [title] and [sideSheetActions] on expanded ones.
  static Future<T?> showAdaptive<T>(
    BuildContext context, {
    required String title,
    required WidgetBuilder builder,
    List<Widget> sideSheetActions = const <Widget>[],
    bool showDragHandle = true,
    bool isDismissible = true,
    bool enableDrag = true,
    M3EBottomSheetValue initialValue = M3EBottomSheetValue.collapsed,
    bool expandToFullScreen = false,
    M3EBottomSheetController? controller,
    ValueChanged<M3EBottomSheetValue>? onValueChanged,
    Future<bool> Function()? onDismissRequest,
    M3EBottomSheetTheme? theme,
    M3EBottomSheetLabels labels = const M3EBottomSheetLabels(),
  }) {
    final M3EBottomSheetTheme sheetTheme =
        theme ?? _themeOf(context).bottomSheetTheme;
    final double width = MediaQuery.sizeOf(context).width;
    if (width >= sheetTheme.adaptiveSideSheetBreakpoint) {
      return M3ESideSheet.show<T>(
        context,
        title: title,
        body: Builder(builder: builder),
        actions: sideSheetActions,
      );
    }
    return show<T>(
      context,
      builder: builder,
      showDragHandle: showDragHandle,
      isDismissible: isDismissible,
      enableDrag: enableDrag,
      initialValue: initialValue,
      expandToFullScreen: expandToFullScreen,
      fullScreenTitle: title,
      controller: controller,
      onValueChanged: onValueChanged,
      onDismissRequest: onDismissRequest,
      theme: theme,
      labels: labels,
    );
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(
      builder: (BuildContext context) {
        final modal = variant == M3EBottomSheetVariant.modal;
        return M3EBottomSheetFrame(
          variant: variant,
          theme: theme ?? M3ETheme.of(context).bottomSheetTheme,
          labels: labels,
          showDragHandle: showDragHandle,
          enableDrag: enableDrag,
          isDismissible: isDismissible,
          initialValue: initialValue,
          previewHeight: previewHeight,
          expandToFullScreen: expandToFullScreen,
          fullScreenTitle: fullScreenTitle,
          controller: controller,
          onValueChanged: onValueChanged,
          onDismiss: modal ? () => Navigator.maybePop(context) : null,
          onClose: modal ? (Object? r) => Navigator.maybePop(context, r) : null,
          child: child,
        );
      },
    );
  }
}
