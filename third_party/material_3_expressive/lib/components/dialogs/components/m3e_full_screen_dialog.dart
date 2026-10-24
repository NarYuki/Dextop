import 'dart:ui' show SemanticsRole, lerpDouble;

import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../../divider/m3e_divider.dart';
import '../../floating_action_buttons/m3e_floating_action_buttons.dart';
import '../../icon_buttons/m3e_icon_buttons.dart';
import '../res/m3e_dialog_strings.dart';
import '../styles/m3e_dialog_theme.dart';
import '../styles/m3e_full_screen_dialog_theme.dart';
import '../utils/m3e_dialog_action_button.dart';
import '../utils/m3e_dialog_spring.dart';
import '../utils/m3e_dialog_surface.dart';

part 'm3e_full_screen_dialog_parts.dart';

/// A Material 3 full-screen dialog surface.
///
/// Header: close (X), headline and a trailing confirm action. Content scrolls
/// under the header and optional bottom action bar, which tint and lift on
/// scroll. Use it with `M3EDialog.showFullScreen`, or as the destination of
/// a FAB container transform (`M3EFab.openBuilder`).
class M3EFullScreenDialog extends StatefulWidget {
  /// M3EFullScreenDialog.
  const M3EFullScreenDialog({
    required this.title,
    required this.body,
    this.action,
    this.confirmLabel,
    this.onConfirm,
    this.bottomActions = const <Widget>[],
    this.contentHeadline,
    this.contentPadding,
    this.showDivider,
    this.closeIcon,
    this.closeLabel = M3EDialogStrings.close,
    this.onClose,
    this.semanticLabel,
    super.key,
  });

  /// Header headline. Keep it short; long copy goes in [contentHeadline].
  final String title;

  /// Dialog content.
  final Widget body;

  /// Trailing header action (e.g. a text button labelled Save).
  final Widget? action;

  /// Label for a token-styled header action, used when [action] is null.
  final String? confirmLabel;

  /// Called by the [confirmLabel] action.
  final VoidCallback? onConfirm;

  /// Bottom action bar actions. Empty hides the bar.
  final List<Widget> bottomActions;

  /// Long headline placed in the content area instead of the header.
  final String? contentHeadline;

  /// Null uses the theme (24 top / left / right).
  final EdgeInsetsGeometry? contentPadding;

  /// Null uses the theme.
  final bool? showDivider;

  /// Null uses the close glyph.
  final Widget? closeIcon;

  /// Close button label and tooltip.
  final String closeLabel;

  /// Null closes the FAB transform or pops through dismiss guards.
  final VoidCallback? onClose;

  /// Null uses [title].
  final String? semanticLabel;

  @override
  State<M3EFullScreenDialog> createState() => _M3EFullScreenDialogState();
}

class _M3EFullScreenDialogState extends State<M3EFullScreenDialog>
    with TickerProviderStateMixin {
  SingleMotionController? _header;
  SingleMotionController? _bar;
  bool _headerScrolled = false;
  bool _barScrolled = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_header != null) {
      return;
    }
    final M3ESpring spring = M3ETheme.of(context)
        .dialogTheme
        .fullScreen
        .scrollSpring;
    _header = SingleMotionController(
      motion: m3eDialogSpringMotion(spring),
      vsync: this,
    );
    _bar = SingleMotionController(
      motion: m3eDialogSpringMotion(spring),
      vsync: this,
    );
  }

  @override
  void dispose() {
    _header?.dispose();
    _bar?.dispose();
    super.dispose();
  }

  void _close() {
    final VoidCallback? onClose = widget.onClose;
    if (onClose != null) {
      onClose();
      return;
    }
    final M3EFabContainerTransformScope? transform =
        M3EFabContainerTransformScope.maybeOf(context);
    if (transform != null) {
      transform.close();
      return;
    }
    Navigator.maybePop(context);
  }

  void _onMetrics(ScrollMetrics metrics) {
    if (metrics.axis != Axis.vertical) {
      return;
    }
    final bool header = metrics.pixels > metrics.minScrollExtent;
    final bool bar = metrics.extentAfter > 0;
    if (header != _headerScrolled) {
      _headerScrolled = header;
      _header!.animateTo(header ? 1 : 0);
    }
    if (bar != _barScrolled) {
      _barScrolled = bar;
      _bar!.animateTo(bar ? 1 : 0);
    }
  }

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3EFullScreenDialogTheme fs = theme.dialogTheme.fullScreen;
    final Widget column = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        _buildHeader(theme),
        if (widget.showDivider ?? fs.showDivider)
          M3EDivider(
            color: fs.resolveDivider(theme.colorScheme),
            thickness: fs.dividerThickness,
          ),
        Expanded(child: _buildContent(theme)),
        if (widget.bottomActions.isNotEmpty) _buildActionBar(theme),
      ],
    );
    return _wrapSurface(theme, column);
  }
}
