import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart' show MaterialTapTargetSize;

import '../../foundations/foundations.dart';
import '../buttons/m3e_buttons.dart';
import '../icon_buttons/m3e_icon_buttons.dart';
import 'components/m3e_snackbar_host.dart';
import 'controllers/m3e_snackbar_controller.dart';
import 'styles/m3e_snackbar_theme.dart';

export 'components/m3e_snackbar_host.dart';
export 'controllers/m3e_snackbar_controller.dart';
export 'styles/m3e_snackbar_theme.dart';

/// A Material 3 Expressive snackbar.
///
/// Brief, low-emphasis feedback at the bottom of the screen with an optional
/// action and close affordance. Call [M3ESnackbar.show] to present one over the
/// nearest [Overlay] (one at a time via [defaultController]).
class M3ESnackbar extends StatelessWidget {
  /// M3ESnackbar.
  const M3ESnackbar({
    required this.message,
    this.actionLabel,
    this.onAction,
    this.showCloseButton = false,
    this.onClose,
    super.key,
  });

  /// Shared one-at-a-time controller used when [show] omits `controller`.
  static final M3ESnackbarController defaultController =
      M3ESnackbarController();

  /// Supporting text / message.
  final String message;

  /// Optional single action label (text button).
  final String? actionLabel;

  /// Called when the action is pressed (then the host dismisses if shown).
  final VoidCallback? onAction;

  /// Whether to show the optional close icon.
  final bool showCloseButton;

  /// Called when close is pressed (then the host dismisses if shown).
  final VoidCallback? onClose;

  /// Presents a snackbar over the overlay found from [context].
  ///
  /// Snackbars with an action or close button do not auto-dismiss unless
  /// [duration] is explicitly passed. Plain snackbars use
  /// [M3ESnackbarTheme.defaultDuration] (4s) by default.
  static void show(
    BuildContext context, {
    required String message,
    String? actionLabel,
    VoidCallback? onAction,
    bool showCloseButton = false,
    VoidCallback? onClose,
    Duration? duration,
    M3ESnackbarController? controller,
  }) {
    final M3EThemeData theme = M3ETheme.of(context);
    final bool actionable =
        (actionLabel != null && actionLabel.isNotEmpty) || showCloseButton;
    final Duration? resolvedDuration =
        duration ?? (actionable ? null : theme.snackBarTheme.defaultDuration);
    final Widget snackbar = M3EComponentTheme(
      builder: (BuildContext context) {
        return M3ESnackbar(
          message: message,
          actionLabel: actionLabel,
          onAction: onAction,
          showCloseButton: showCloseButton,
          onClose: onClose,
        );
      },
    );
    (controller ?? M3ESnackbar.defaultController).present(
      context,
      duration: resolvedDuration,
      child: snackbar,
    );
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(builder: _buildBar);
  }

  Widget _buildBar(BuildContext context) {
    final theme = M3ETheme.of(context);
    final scheme = theme.colorScheme;
    final snackTheme = theme.snackBarTheme;
    final TextDirection direction = Directionality.of(context);
    final bool hasAction =
        actionLabel != null && actionLabel!.trim().isNotEmpty;
    // Close icon carries 12dp on both sides, including the trailing inset.
    // Action-only bars use the 8dp end padding instead.
    final double endPadding = showCloseButton
        ? 0
        : (hasAction
              ? snackTheme.endPaddingWithTrailing
              : snackTheme.endPadding);
    final EdgeInsets padding = EdgeInsetsDirectional.only(
      start: snackTheme.startPadding,
      end: endPadding,
      top: snackTheme.verticalPadding,
      bottom: snackTheme.verticalPadding,
    ).resolve(direction);

    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        return _layoutSnackBar(
          context,
          constraints,
          theme: theme,
          scheme: scheme,
          snackTheme: snackTheme,
          direction: direction,
          hasAction: hasAction,
          padding: padding,
        );
      },
    );
  }

  Widget _layoutSnackBar(
    BuildContext context,
    BoxConstraints constraints, {
    required M3EThemeData theme,
    required M3EColorScheme scheme,
    required M3ESnackbarTheme snackTheme,
    required TextDirection direction,
    required bool hasAction,
    required EdgeInsets padding,
  }) {
    final double maxBarWidth = constraints.maxWidth.isFinite
        ? constraints.maxWidth.clamp(0, snackTheme.maxWidth)
        : snackTheme.maxWidth;
    final double inlineControlHeight =
        (snackTheme.singleLineMinHeight - snackTheme.verticalPadding * 2).clamp(
          0,
          snackTheme.singleLineMinHeight,
        );
    final double closeWidth =
        snackTheme.closePadding * 2 + snackTheme.closeIconSize;
    final TextStyle messageStyle = snackTheme.messageStyle(
      theme.typeScale,
      scheme,
    );
    final bool actionBelow = _actionBelow(
      snackTheme: snackTheme,
      messageStyle: messageStyle,
      direction: direction,
      maxBarWidth: maxBarWidth,
      padding: padding,
      hasAction: hasAction,
      closeWidth: closeWidth,
    );
    final bool multiLine =
        _messageLineCount(
          message: message,
          style: messageStyle,
          maxWidth: _textMaxWidth(
            snackTheme: snackTheme,
            maxBarWidth: maxBarWidth,
            padding: padding,
            hasAction: hasAction,
            actionBelow: false,
            closeVisualSize: showCloseButton ? closeWidth : 0,
          ),
          textDirection: direction,
        ) >
        1;
    final double minHeight = multiLine || actionBelow
        ? snackTheme.twoLineMinHeight
        : snackTheme.singleLineMinHeight;
    return _snackContainer(
      context,
      snackTheme: snackTheme,
      scheme: scheme,
      theme: theme,
      padding: padding,
      minHeight: minHeight,
      inlineControlHeight: inlineControlHeight,
      closeWidth: closeWidth,
      messageStyle: messageStyle,
      hasAction: hasAction,
      actionBelow: actionBelow,
    );
  }

  bool _actionBelow({
    required M3ESnackbarTheme snackTheme,
    required TextStyle messageStyle,
    required TextDirection direction,
    required double maxBarWidth,
    required EdgeInsets padding,
    required bool hasAction,
    required double closeWidth,
  }) {
    if (!hasAction) {
      return false;
    }
    final bool multiLine =
        _messageLineCount(
          message: message,
          style: messageStyle,
          maxWidth: _textMaxWidth(
            snackTheme: snackTheme,
            maxBarWidth: maxBarWidth,
            padding: padding,
            hasAction: hasAction,
            actionBelow: false,
            closeVisualSize: showCloseButton ? closeWidth : 0,
          ),
          textDirection: direction,
        ) >
        1;
    return multiLine ||
        actionLabel!.length > snackTheme.longActionLabelThreshold;
  }

  Widget _snackContainer(
    BuildContext context, {
    required M3EThemeData theme,
    required M3EColorScheme scheme,
    required M3ESnackbarTheme snackTheme,
    required EdgeInsets padding,
    required double minHeight,
    required double inlineControlHeight,
    required double closeWidth,
    required TextStyle messageStyle,
    required bool hasAction,
    required bool actionBelow,
  }) {
    final Widget messageText = Text(
      message,
      style: messageStyle,
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
    );
    final Widget? action = hasAction
        ? _buildAction(
            context,
            theme,
            snackTheme,
            scheme,
            height: actionBelow ? null : inlineControlHeight,
          )
        : null;
    final Widget? close = showCloseButton
        ? _buildClose(
            context,
            snackTheme,
            scheme,
            height: inlineControlHeight,
            width: closeWidth,
          )
        : null;
    final Widget body = actionBelow
        ? Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Row(
                children: <Widget>[
                  Expanded(child: messageText),
                  ?close,
                ],
              ),
              SizedBox(height: snackTheme.actionGap),
              Align(alignment: AlignmentDirectional.centerEnd, child: action),
            ],
          )
        : Row(
            children: <Widget>[
              Expanded(child: messageText),
              if (action != null) ...<Widget>[
                SizedBox(width: snackTheme.actionGap),
                action,
              ],
              ?close,
            ],
          );

    return Semantics(
      liveRegion: true,
      container: true,
      child: Container(
        constraints: BoxConstraints(
          minHeight: minHeight,
          maxWidth: snackTheme.maxWidth,
        ),
        padding: padding,
        decoration: BoxDecoration(
          color: snackTheme.containerColor(scheme),
          borderRadius: snackTheme.borderRadius,
          boxShadow: M3EElevation.shadows(
            snackTheme.elevation,
            shadowColor: scheme.shadow,
          ),
        ),
        child: body,
      ),
    );
  }

  double _textMaxWidth({
    required M3ESnackbarTheme snackTheme,
    required double maxBarWidth,
    required EdgeInsets padding,
    required bool hasAction,
    required bool actionBelow,
    required double closeVisualSize,
  }) {
    var width = maxBarWidth - padding.left - padding.right;
    if (closeVisualSize > 0) {
      width -= closeVisualSize;
    }
    if (hasAction && !actionBelow) {
      // Reserve approximate action width; layout still expands as needed.
      width -= snackTheme.actionGap + 48;
    }
    return width.clamp(0, maxBarWidth);
  }

  int _messageLineCount({
    required String message,
    required TextStyle style,
    required double maxWidth,
    required TextDirection textDirection,
  }) {
    final painter = TextPainter(
      text: TextSpan(text: message, style: style),
      textDirection: textDirection,
      maxLines: 2,
    )..layout(maxWidth: maxWidth);
    return painter.computeLineMetrics().length.clamp(1, 2);
  }

  Widget _buildAction(
    BuildContext context,
    M3EThemeData theme,
    M3ESnackbarTheme snackTheme,
    M3EColorScheme scheme, {
    double? height,
  }) {
    return M3EButton.text(
      size: M3EButtonSize.xs,
      semanticLabel: actionLabel,
      decoration: M3EButtonDecoration(
        foregroundColor: WidgetStateProperty.all(scheme.inversePrimary),
        textStyle: snackTheme.actionStyle(theme.typeScale, scheme),
        padding: snackTheme.actionPadding,
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        minimumSize: height == null ? null : Size(0, height),
        maximumSize: height == null ? null : Size(double.infinity, height),
      ),
      onPressed: () {
        onAction?.call();
        M3ESnackbarDismissScope.maybeOf(context)?.call();
      },
      child: Text(actionLabel!),
    );
  }

  Widget _buildClose(
    BuildContext context,
    M3ESnackbarTheme snackTheme,
    M3EColorScheme scheme, {
    required double height,
    required double width,
  }) {
    return M3EIconButton(
      variant: M3EIconButtonVariant.standard,
      visualSize: Size(width, height),
      inflateHitTarget: false,
      semanticLabel: 'Dismiss',
      decoration: M3EIconButtonDecoration(
        foregroundColor: WidgetStateProperty.all(
          snackTheme.closeIconColor(scheme),
        ),
        backgroundColor: const WidgetStatePropertyAll<Color?>(null),
      ),
      icon: Icon(M3EIcons.close, size: snackTheme.closeIconSize),
      onPressed: () {
        onClose?.call();
        M3ESnackbarDismissScope.maybeOf(context)?.call();
      },
    );
  }
}
