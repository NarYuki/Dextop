import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../../foundations/foundations.dart';
import '../divider/m3e_divider.dart';
import 'components/m3e_side_sheet_actions.dart';
import 'components/m3e_side_sheet_back_transform.dart';
import 'components/m3e_side_sheet_body.dart';
import 'components/m3e_side_sheet_header.dart';
import 'components/m3e_side_sheet_icon_action.dart';
import 'components/m3e_side_sheet_scrim.dart';
import 'components/m3e_side_sheet_surface.dart';
import 'controllers/m3e_side_sheet_controller.dart';
import 'enums/m3e_side_sheet_enums.dart';
import 'models/m3e_side_sheet_labels.dart';
import 'res/m3e_side_sheet_system_ui.dart';
import 'styles/m3e_side_sheet_theme.dart';
import 'utils/m3e_side_sheet_animator.dart';
import 'utils/m3e_side_sheet_spring.dart';

export 'controllers/m3e_side_sheet_controller.dart';
export 'enums/m3e_side_sheet_enums.dart';
export 'models/m3e_side_sheet_labels.dart';
export 'styles/m3e_side_sheet_theme.dart';

part 'components/m3e_side_sheet_layout.dart';
part 'components/m3e_side_sheet_layout_build.dart';
part 'components/m3e_side_sheet_modal_host.dart';
part 'components/m3e_side_sheet_panel.dart';
part 'components/m3e_side_sheet_route.dart';
part 'components/m3e_side_sheet_scope.dart';

/// A Material 3 Expressive side sheet.
///
/// Shows secondary content anchored to the end edge of the window (the left
/// edge in right-to-left languages). The unnamed constructor is the modal
/// sheet; use [M3ESideSheet.show] to present it over a scrim, or
/// `M3ESideSheetLayout` to place a [M3ESideSheet.standard] sheet next to
/// the main content and switch to modal on compact windows.
///
/// The sheet reads as a dialog named by [title]. It has a close icon
/// button by default, an optional back icon ([onBack]), a body that
/// scrolls on its own, and optional bottom [actions].
class M3ESideSheet extends StatelessWidget {
  /// A modal side sheet surface. Placed directly, the close button pops
  /// the current route unless [onClose] is set.
  const M3ESideSheet({
    required this.title,
    required this.body,
    this.actions = const <Widget>[],
    this.onClose,
    this.showCloseButton = true,
    this.onBack,
    this.showDivider,
    this.showEdgeDivider = false,
    this.detached = false,
    this.edge = M3ESideSheetEdge.end,
    this.width,
    this.scrollable = false,
    this.closeIcon,
    this.backIcon,
    this.theme,
    this.labels = const M3ESideSheetLabels(),
    super.key,
  }) : variant = M3ESideSheetVariant.modal;

  /// A standard side sheet that sits next to the main content.
  ///
  /// Placed directly, the close button shows only when [onClose] is set;
  /// inside `M3ESideSheetLayout` it closes the sheet.
  const M3ESideSheet.standard({
    required this.title,
    required this.body,
    this.actions = const <Widget>[],
    this.onClose,
    this.showCloseButton = true,
    this.onBack,
    this.showDivider,
    this.showEdgeDivider = false,
    this.detached = false,
    this.edge = M3ESideSheetEdge.end,
    this.width,
    this.scrollable = false,
    this.closeIcon,
    this.backIcon,
    this.theme,
    this.labels = const M3ESideSheetLabels(),
    super.key,
  }) : variant = M3ESideSheetVariant.standard;

  /// Headline; also the dialog's accessible name.
  final String title;

  /// Sheet content. It fills the space between the header and actions.
  final Widget body;

  /// Bottom action buttons, start-aligned, in focus order.
  final List<Widget> actions;

  /// Standard or modal.
  final M3ESideSheetVariant variant;

  /// Called when the close icon button is pressed.
  final VoidCallback? onClose;

  /// Whether to show the close icon button (recommended).
  final bool showCloseButton;

  /// Shows a back icon button that calls this.
  final VoidCallback? onBack;

  /// Divider above [actions]. Null shows it when there are actions.
  final bool? showDivider;

  /// Vertical divider on the edge facing the main content.
  final bool showEdgeDivider;

  /// Insets the sheet 16 from the window edges with every corner rounded.
  final bool detached;

  /// Window edge the sheet is anchored to.
  final M3ESideSheetEdge edge;

  /// Container width. Null uses the theme width (256); capped at 400.
  final double? width;

  /// Wraps [body] in a vertical scroll view. Leave false when [body] is
  /// already a scrollable such as a `ListView`.
  final bool scrollable;

  /// Close glyph. Null uses `M3EIcons.close`.
  final Widget? closeIcon;

  /// Back glyph. Null uses `M3EIcons.arrow_back`, mirrored in RTL.
  final Widget? backIcon;

  /// Per-sheet theme. Null uses `M3EThemeData.sideSheetTheme`.
  final M3ESideSheetTheme? theme;

  /// Accessibility strings.
  final M3ESideSheetLabels labels;

  /// Presents a modal side sheet and completes with the popped result.
  ///
  /// It springs in from the anchored edge over a scrim. Tapping the scrim,
  /// the close button, Escape, back and predictive back close it;
  /// [onDismissRequest] can veto those.
  static Future<T?> show<T>(
    BuildContext context, {
    required String title,
    required Widget body,
    List<Widget> actions = const <Widget>[],
    VoidCallback? onBack,
    bool showCloseButton = true,
    bool? showDivider,
    bool detached = false,
    M3ESideSheetEdge edge = M3ESideSheetEdge.end,
    double? width,
    bool scrollable = false,
    Widget? closeIcon,
    Widget? backIcon,
    bool isDismissible = true,
    Future<bool> Function()? onDismissRequest,
    M3ESideSheetController? controller,
    M3ESideSheetTheme? theme,
    M3ESideSheetLabels labels = const M3ESideSheetLabels(),
    bool useRootNavigator = true,
  }) {
    return _pushSideSheet<T>(
      context,
      sheet: M3ESideSheet(
        title: title,
        body: body,
        actions: actions,
        onBack: onBack,
        showCloseButton: showCloseButton,
        showDivider: showDivider,
        detached: detached,
        edge: edge,
        width: width,
        scrollable: scrollable,
        closeIcon: closeIcon,
        backIcon: backIcon,
        theme: theme,
        labels: labels,
      ),
      isDismissible: isDismissible,
      onDismissRequest: onDismissRequest,
      controller: controller,
      useRootNavigator: useRootNavigator,
    );
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(builder: _buildPanel);
  }
}
