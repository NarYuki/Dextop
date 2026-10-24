import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../m3e_dialogs.dart';
import '../res/m3e_dialog_strings.dart';
import '../utils/m3e_dialog_action_button.dart';
import '../utils/m3e_dialog_spring.dart';

part 'm3e_adaptive_dialog_morph.dart';

/// Dialog that swaps variant at the compact breakpoint.
///
/// Below `M3EDialogTheme.compactBreakpoint` it is a full-screen dialog (close
/// X and [confirmLabel] in the header). Wider, it is a basic dialog with
/// [dismissLabel] and [confirmLabel] actions. Crossing the breakpoint morphs
/// the surface on the spatial spring.
class M3EAdaptiveDialog extends StatefulWidget {
  /// M3EAdaptiveDialog.
  const M3EAdaptiveDialog({
    required this.title,
    required this.content,
    required this.confirmLabel,
    required this.onConfirm,
    this.dismissLabel = M3EDialogStrings.cancel,
    this.icon,
    this.position = M3EDialogPosition.center,
    this.controller,
    this.semanticLabel,
    super.key,
  });

  /// Headline.
  final String title;

  /// Body. Scrolls in both variants.
  final Widget content;

  /// Confirming action label (e.g. Save).
  final String confirmLabel;

  /// Confirming action. Null disables it.
  final VoidCallback? onConfirm;

  /// Dismissive action label in the basic variant.
  final String dismissLabel;

  /// Optional hero icon in the basic variant.
  final Widget? icon;

  /// Basic dialog position.
  final M3EDialogPosition position;

  /// Receives the active variant.
  final M3EDialogController? controller;

  /// Null uses [title].
  final String? semanticLabel;

  @override
  State<M3EAdaptiveDialog> createState() => _M3EAdaptiveDialogState();
}

class _M3EAdaptiveDialogState extends State<M3EAdaptiveDialog>
    with SingleTickerProviderStateMixin {
  final GlobalKey _surfaceKey = GlobalKey();
  SingleMotionController? _morph;
  M3EDialogVariant? _variant;
  Rect? _lastRect;
  Rect? _from;
  Rect? _to;
  bool _pendingSwap = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _morph ??= SingleMotionController(
      motion: m3eDialogSpringMotion(
        M3ETheme.of(context).dialogTheme.enterSpring,
      ),
      vsync: this,
    )..value = 1;
  }

  @override
  void dispose() {
    _morph?.dispose();
    super.dispose();
  }

  void _refresh() => setState(() {});

  void _dismiss() => Navigator.maybePop(context);

  Widget _basic(M3EThemeData theme) {
    return M3EDialogInset(
      padding: theme.dialogTheme.screenMargin,
      alignment: widget.position.alignment,
      child: KeyedSubtree(
        key: _surfaceKey,
        child: M3EDialog(
          title: widget.title,
          icon: widget.icon,
          content: widget.content,
          semanticLabel: widget.semanticLabel,
          actions: <Widget>[
            m3eDialogTextAction(
              label: widget.dismissLabel,
              onPressed: _dismiss,
            ),
            m3eDialogTextAction(
              label: widget.confirmLabel,
              onPressed: widget.onConfirm,
            ),
          ],
        ),
      ),
    );
  }

  Widget _fullScreen() {
    return KeyedSubtree(
      key: _surfaceKey,
      child: M3EFullScreenDialog(
        title: widget.title,
        body: SingleChildScrollView(child: widget.content),
        confirmLabel: widget.confirmLabel,
        onConfirm: widget.onConfirm,
        semanticLabel: widget.semanticLabel,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final variant =
            constraints.maxWidth < theme.dialogTheme.compactBreakpoint
            ? M3EDialogVariant.fullScreen
            : M3EDialogVariant.basic;
        _onVariant(variant);
        final Widget layout = variant == M3EDialogVariant.fullScreen
            ? _fullScreen()
            : _basic(theme);
        return _morphed(layout);
      },
    );
  }
}
