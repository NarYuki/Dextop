import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../foundations/foundations.dart';
import 'controllers/m3e_tooltip_controller.dart';
import 'enums/m3e_tooltip_placement.dart';
import 'styles/m3e_tooltip_theme.dart';
import 'utils/m3e_tooltip_position_delegate.dart';

export 'controllers/m3e_tooltip_controller.dart';
export 'enums/m3e_tooltip_placement.dart';
export 'styles/m3e_tooltip_theme.dart';

/// Ensures only one [M3ETooltip] overlay is visible at a time.
final Set<_M3ETooltipState> _openTooltips = <_M3ETooltipState>{};

/// A Material 3 Expressive tooltip.
///
/// **Plain** ([message]): hover, focus, or long-press; dismisses immediately
/// after leaving the target by default (themable). Default placement: above.
///
/// **Rich** ([richMessage]): optional [richTitle] and up to two [actions]
/// (prefer text buttons such as `M3EButton.text`). Transient rich uses the
/// same triggers as plain and dismisses **1.5s** after leave by default;
/// [persistent] rich shows on tap / [controller] only and stays until an
/// outside interaction. Default placement: bottom-end.
class M3ETooltip extends StatefulWidget {
  /// M3ETooltip.
  const M3ETooltip({
    required this.child,
    this.message,
    this.richTitle,
    this.richMessage,
    this.actions = const <Widget>[],
    this.persistent = false,
    this.preferredPlacement,
    this.dismissDelay,
    this.controller,
    super.key,
  }) : assert(
         message != null || richMessage != null,
         'Provide a plain message or a rich message.',
       ),
       assert(
         !persistent || richMessage != null,
         'persistent is only supported for rich tooltips.',
       );

  /// Anchor widget.
  final Widget child;

  /// Plain supporting text.
  final String? message;

  /// Rich subhead (optional).
  final String? richTitle;

  /// Rich supporting text. Presence selects rich mode.
  final String? richMessage;

  /// Rich action widgets (max 2). Prefer text buttons.
  final List<Widget> actions;

  /// When true (rich only), show on tap/controller — not hover/focus.
  final bool persistent;

  /// Overrides default placement (plain: above, rich: bottom-end).
  final M3ETooltipPlacement? preferredPlacement;

  /// Override for delay after leaving the target before hiding.
  ///
  /// When null, uses [M3ETooltipTheme.plainDismissDelay] for plain tooltips
  /// and [M3ETooltipTheme.richDismissDelay] for rich tooltips.
  final Duration? dismissDelay;

  /// Optional programmatic show/hide.
  final M3ETooltipController? controller;

  bool get _isRich => richMessage != null;

  @override
  State<M3ETooltip> createState() => _M3ETooltipState();
}

class _M3ETooltipState extends State<M3ETooltip>
    implements M3ETooltipControllerClient {
  final GlobalKey _targetKey = GlobalKey();
  final OverlayPortalController _portal = OverlayPortalController();
  Timer? _timer;
  bool _showing = false;

  bool get _isRich => widget._isRich;
  bool get _isPersistent => widget.persistent && _isRich;
  bool get _isTransient => !_isPersistent;

  @override
  void initState() {
    super.initState();
    widget.controller?.attachClient(this);
  }

  @override
  void didUpdateWidget(M3ETooltip oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.detachClient(this);
      widget.controller?.attachClient(this);
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _openTooltips.remove(this);
    widget.controller?.detachClient(this);
    super.dispose();
  }

  @override
  void onShowRequested() => _show();

  @override
  void onHideRequested() => _hide();

  void _show() {
    if (!mounted) {
      return;
    }
    _timer?.cancel();
    for (final other in List<_M3ETooltipState>.of(_openTooltips)) {
      if (!identical(other, this)) {
        other._hide();
      }
    }
    _openTooltips.add(this);
    if (!_portal.isShowing) {
      _portal.show();
    }
    if (!_showing) {
      _showing = true;
      widget.controller?.updateShowing(showing: true);
    }
  }

  void _hide() {
    _timer?.cancel();
    _openTooltips.remove(this);
    if (_portal.isShowing) {
      _portal.hide();
    }
    if (_showing) {
      _showing = false;
      widget.controller?.updateShowing(showing: false);
    }
  }

  void _scheduleHide() {
    if (!mounted || _isPersistent) {
      return;
    }
    final tooltipTheme = M3ETheme.of(context).tooltipTheme;
    final Duration delay =
        widget.dismissDelay ??
        (_isRich
            ? tooltipTheme.richDismissDelay
            : tooltipTheme.plainDismissDelay);
    _timer?.cancel();
    if (delay == Duration.zero) {
      _hide();
      return;
    }
    _timer = Timer(delay, () {
      if (mounted) {
        _hide();
      }
    });
  }

  void _cancelHide() {
    _timer?.cancel();
  }

  void _onChildFocusChange(bool focused) {
    if (!_isTransient) {
      return;
    }
    if (focused) {
      // Touch/tap focuses the child but must not open the tooltip; only
      // keyboard-driven focus (traditional highlight) should.
      if (FocusManager.instance.highlightMode ==
          FocusHighlightMode.traditional) {
        _show();
      }
    } else {
      _scheduleHide();
    }
  }

  M3ETooltipPlacement _resolvedPlacement() {
    if (widget.preferredPlacement != null) {
      return widget.preferredPlacement!;
    }
    return _isRich ? M3ETooltipPlacement.bottomEnd : M3ETooltipPlacement.above;
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(
      builder: (BuildContext context) {
        Widget target = KeyedSubtree(key: _targetKey, child: widget.child);
        if (_isTransient) {
          // Listen for descendant focus without adding a Tab stop. Using
          // FocusableActionDetector would insert an extra focusable node.
          target = Focus(
            canRequestFocus: false,
            skipTraversal: true,
            includeSemantics: false,
            onFocusChange: _onChildFocusChange,
            child: MouseRegion(
              onEnter: (_) => _show(),
              onExit: (_) => _scheduleHide(),
              child: GestureDetector(
                behavior: HitTestBehavior.translucent,
                onLongPress: _show,
                onLongPressEnd: (_) => _scheduleHide(),
                child: target,
              ),
            ),
          );
        } else {
          target = GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: _show,
            child: target,
          );
        }
        return OverlayPortal(
          controller: _portal,
          overlayChildBuilder: _buildOverlay,
          child: target,
        );
      },
    );
  }

  Widget _buildOverlay(BuildContext context) {
    final theme = M3ETheme.of(context);
    final tooltipTheme = theme.tooltipTheme;
    final targetBox =
        _targetKey.currentContext?.findRenderObject() as RenderBox?;
    final OverlayState? overlay = Overlay.maybeOf(context);
    final overlayBox = overlay?.context.findRenderObject() as RenderBox?;

    // Plain bubbles must not hit-test. Covering the icon steals the pointer
    // cursor, the button exits, and the tooltip hides and shows in a loop.
    final Widget bubble = _isRich
        ? MouseRegion(
            onEnter: (_) => _cancelHide(),
            onExit: (_) {
              if (_isTransient) {
                _scheduleHide();
              }
            },
            child: _buildRich(theme, tooltipTheme),
          )
        : IgnorePointer(child: _buildPlain(theme, tooltipTheme));

    if (targetBox == null ||
        overlayBox == null ||
        !targetBox.hasSize ||
        !overlayBox.hasSize) {
      return const SizedBox.shrink();
    }

    final Offset targetOrigin = targetBox.localToGlobal(
      Offset.zero,
      ancestor: overlayBox,
    );
    final Rect targetRect = targetOrigin & targetBox.size;

    return Stack(
      children: <Widget>[
        if (_isRich || _isPersistent)
          Positioned.fill(
            child: GestureDetector(
              onTap: _hide,
              behavior: HitTestBehavior.translucent,
            ),
          ),
        CustomSingleChildLayout(
          delegate: M3ETooltipPositionDelegate(
            target: targetRect,
            preferred: _resolvedPlacement(),
            gap: tooltipTheme.anchorOffset,
            step: tooltipTheme.placementStep,
            overlaySize: overlayBox.size,
            textDirection: Directionality.of(context),
          ),
          child: bubble,
        ),
      ],
    );
  }

  Widget _buildPlain(M3EThemeData theme, M3ETooltipTheme tooltipTheme) {
    final scheme = theme.colorScheme;
    final String message = widget.message!;
    return _fade(
      Semantics(
        container: true,
        tooltip: message,
        child: Container(
          constraints: BoxConstraints(
            maxWidth: tooltipTheme.plainMaxWidth,
            minHeight: tooltipTheme.plainMinHeight,
          ),
          padding: tooltipTheme.plainPadding,
          decoration: BoxDecoration(
            color: tooltipTheme.plainContainerColor(scheme),
            borderRadius: tooltipTheme.plainBorderRadius,
          ),
          // No alignment: under overlay max constraints, Align would expand
          // the bubble to the full viewport height.
          child: Text(
            message,
            textAlign: TextAlign.center,
            style: tooltipTheme.plainMessageStyle(theme.typeScale, scheme),
          ),
        ),
      ),
    );
  }

  Widget _buildRich(M3EThemeData theme, M3ETooltipTheme tooltipTheme) {
    final scheme = theme.colorScheme;
    final String body = widget.richMessage!;
    final String label = <String>[
      if (widget.richTitle != null) widget.richTitle!,
      body,
    ].join('. ');
    return _fade(
      Semantics(
        container: true,
        tooltip: label,
        child: Container(
          constraints: BoxConstraints(maxWidth: tooltipTheme.richMaxWidth),
          padding: tooltipTheme.richPadding,
          decoration: BoxDecoration(
            color: tooltipTheme.richContainerColor(scheme),
            borderRadius: tooltipTheme.richBorderRadius,
            boxShadow: M3EElevation.shadows(
              tooltipTheme.richElevation,
              shadowColor: scheme.shadow,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              if (widget.richTitle != null) ...<Widget>[
                Text(
                  widget.richTitle!,
                  style: tooltipTheme.richTitleStyle(theme.typeScale, scheme),
                ),
                SizedBox(height: tooltipTheme.richTitleGap),
              ],
              Text(
                body,
                style: tooltipTheme.richBodyStyle(theme.typeScale, scheme),
              ),
              if (widget.actions.isNotEmpty) ...<Widget>[
                SizedBox(height: tooltipTheme.richActionsGap),
                Row(children: widget.actions),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _fade(Widget child) {
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: M3EMotion.short3,
      curve: M3EMotion.standard,
      builder: (BuildContext context, double value, Widget? built) {
        return Opacity(opacity: value, child: built);
      },
      child: child,
    );
  }
}
