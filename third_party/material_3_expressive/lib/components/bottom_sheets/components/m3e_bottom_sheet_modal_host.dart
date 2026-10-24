import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../controllers/m3e_bottom_sheet_controller.dart';
import '../enums/m3e_bottom_sheet_enums.dart';
import '../models/m3e_bottom_sheet_labels.dart';
import '../res/m3e_bottom_sheet_system_ui.dart';
import '../styles/m3e_bottom_sheet_theme.dart';
import '../utils/m3e_bottom_sheet_spring.dart';
import 'm3e_bottom_sheet_frame.dart';

/// Page of a modal bottom sheet route.
///
/// Paints the scrim, springs the sheet in and out with the route, and owns
/// the dismiss path: scrim tap, Escape, back, predictive back and drag.
class M3EBottomSheetModalHost extends StatefulWidget {
  /// M3EBottomSheetModalHost.
  const M3EBottomSheetModalHost({
    required this.animation,
    required this.theme,
    required this.labels,
    required this.child,
    this.showDragHandle = true,
    this.enableDrag = true,
    this.isDismissible = true,
    this.initialValue = M3EBottomSheetValue.collapsed,
    this.expandToFullScreen = false,
    this.fullScreenTitle,
    this.controller,
    this.onValueChanged,
    this.onDismissRequest,
    super.key,
  });

  /// Route animation; only its direction is used.
  final Animation<double> animation;

  /// Resolved sheet theme.
  final M3EBottomSheetTheme theme;

  /// Accessibility strings.
  final M3EBottomSheetLabels labels;

  /// Sheet content.
  final Widget child;

  /// Whether to show the drag handle.
  final bool showDragHandle;

  /// Whether dragging resizes or dismisses the sheet.
  final bool enableDrag;

  /// Whether tapping the scrim closes the sheet.
  final bool isDismissible;

  /// Height to open at.
  final M3EBottomSheetValue initialValue;

  /// Adds a full-screen height with a close header.
  final bool expandToFullScreen;

  /// Title in the full-screen header.
  final String? fullScreenTitle;

  /// Optional controller.
  final M3EBottomSheetController? controller;

  /// Called when the sheet rests at a new height.
  final ValueChanged<M3EBottomSheetValue>? onValueChanged;

  /// Guard; resolve false to keep the sheet open.
  final Future<bool> Function()? onDismissRequest;

  @override
  State<M3EBottomSheetModalHost> createState() =>
      _M3EBottomSheetModalHostState();
}

class _M3EBottomSheetModalHostState extends State<M3EBottomSheetModalHost>
    with TickerProviderStateMixin, WidgetsBindingObserver {
  late final AnimationController _entrance = AnimationController.unbounded(
    vsync: this,
  );
  late final AnimationController _scrim = AnimationController.unbounded(
    vsync: this,
  );
  late final AnimationController _back = AnimationController.unbounded(
    vsync: this,
  );
  final FocusNode _host = FocusNode(
    debugLabel: 'M3EBottomSheet',
    skipTraversal: true,
  );
  final FocusNode _handle = FocusNode(debugLabel: 'M3EBottomSheet handle');
  bool _dismissing = false;
  bool _started = false;

  M3EBottomSheetTheme get _theme => widget.theme;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.animation.addStatusListener(_onStatus);
    WidgetsBinding.instance.addPostFrameCallback((_) => _focusInitial());
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_started) {
      _started = true;
      _onStatus(widget.animation.status);
    }
  }

  @override
  void didUpdateWidget(covariant M3EBottomSheetModalHost oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.animation != widget.animation) {
      oldWidget.animation.removeStatusListener(_onStatus);
      widget.animation.addStatusListener(_onStatus);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.animation.removeStatusListener(_onStatus);
    _entrance.dispose();
    _scrim.dispose();
    _back.dispose();
    _host.dispose();
    _handle.dispose();
    super.dispose();
  }

  void _onStatus(AnimationStatus status) {
    final double target = status.isForwardOrCompleted ? 1 : 0;
    _spring(_entrance, _theme.enterSpring, target);
    _spring(_scrim, _theme.scrimSpring, target);
  }

  void _spring(AnimationController c, M3ESpring spring, double target) {
    final bool reduce = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    if (reduce) {
      c.value = target;
      return;
    }
    c.animateWith(
      m3eBottomSheetSpring(
        spring,
        from: c.value,
        to: target,
        velocity: c.velocity,
      ),
    );
  }

  /// Keyboard users land on the drag handle; others on the sheet itself.
  void _focusInitial() {
    if (!mounted) {
      return;
    }
    final bool keyboard = M3EFocusInteraction.instance.ringsAllowed;
    if (keyboard && widget.showDragHandle && _handle.canRequestFocus) {
      _handle.requestFocus();
      return;
    }
    _host.requestFocus();
  }

  bool get _isCurrent => ModalRoute.of(context)?.isCurrent ?? false;

  void _pop([Object? result]) {
    final ModalRoute<Object?>? route = ModalRoute.of(context);
    final NavigatorState navigator = Navigator.of(context);
    if (route == null || route.isCurrent) {
      navigator.pop(result);
    } else if (route.isActive) {
      navigator.removeRoute(route, result);
    }
  }

  void _close(Object? result) {
    if (mounted) {
      _pop(result);
    }
  }

  /// Runs the guard, then pops. True when the sheet closed.
  Future<bool> _requestDismiss() async {
    if (_dismissing || !mounted) {
      return false;
    }
    _dismissing = true;
    try {
      final Future<bool> Function()? guard = widget.onDismissRequest;
      final bool allowed = guard == null || await guard();
      if (allowed && mounted) {
        _pop();
        return true;
      }
      return false;
    } finally {
      _dismissing = false;
    }
  }

  // ── Predictive back ────────────────────────────────────────────────────

  @override
  bool handleStartBackGesture(PredictiveBackEvent backEvent) {
    if (!mounted || !_isCurrent) {
      return false;
    }
    _back
      ..stop()
      ..value = backEvent.progress;
    return true;
  }

  @override
  void handleUpdateBackGestureProgress(PredictiveBackEvent backEvent) {
    _back.value = backEvent.progress;
  }

  /// Commits through the guard, mirroring Compose's `settleToDismiss`: the
  /// guard decides whether the sheet closes, and a guard that keeps the sheet
  /// open (the "collapse one level" case in Compose, which animates its
  /// predictive back progress back to zero) settles the preview back to rest.
  /// When the guard allows, the preview stays put while the route exits.
  @override
  void handleCommitBackGesture() {
    if (widget.onDismissRequest == null) {
      _close(null);
      return;
    }
    unawaited(
      _requestDismiss().then((bool closed) {
        if (!closed && mounted) {
          _spring(_back, _theme.settleSpring, 0);
        }
      }),
    );
  }

  @override
  void handleCancelBackGesture() => _spring(_back, _theme.settleSpring, 0);

  // ── Build ──────────────────────────────────────────────────────────────

  Widget _buildScrim(BuildContext context) {
    final Color color = _theme.scrimColor(M3ETheme.of(context).colorScheme);
    final VoidCallback? onTap = widget.isDismissible ? _requestDismiss : null;
    return Semantics(
      label: widget.isDismissible ? widget.labels.scrim : null,
      onTap: onTap,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: AnimatedBuilder(
          animation: _scrim,
          builder: (BuildContext context, _) {
            final double t = _scrim.value.clamp(0, 1).toDouble();
            return ColoredBox(color: color.withValues(alpha: color.a * t));
          },
        ),
      ),
    );
  }

  Widget _buildSheet({required bool light}) {
    return M3EBottomSheetFrame(
      variant: M3EBottomSheetVariant.modal,
      theme: _theme,
      labels: widget.labels,
      showDragHandle: widget.showDragHandle,
      enableDrag: widget.enableDrag,
      initialValue: widget.initialValue,
      expandToFullScreen: widget.expandToFullScreen,
      fullScreenTitle: widget.fullScreenTitle,
      controller: widget.controller,
      onValueChanged: widget.onValueChanged,
      onDismiss: _requestDismiss,
      onClose: _close,
      entrance: _entrance,
      backProgress: _back,
      handleFocusNode: _handle,
      overlayStyle: light
          ? M3EBottomSheetSystemUi.lightSurface
          : M3EScrimSystemUi.bottomSheetOverlayStyle,
      child: widget.child,
    );
  }

  /// Light theme: icons follow what sits under each bar (dark scrim or light
  /// sheet). Dark theme: light status icons, navigation bar left as is.
  Widget _systemUi(Widget child, {required bool light}) {
    return light
        ? M3EScrimSystemUi.wrap(child)
        : M3EScrimSystemUi.wrapBottomSheet(child);
  }

  @override
  Widget build(BuildContext context) {
    final light = M3ETheme.of(context).brightness == Brightness.light;
    return _systemUi(_popScope(light: light), light: light);
  }

  Widget _popScope({required bool light}) {
    return PopScope<Object?>(
      canPop: widget.onDismissRequest == null,
      onPopInvokedWithResult: (bool didPop, Object? result) {
        if (!didPop) {
          _requestDismiss();
        }
      },
      child: Actions(
        actions: <Type, Action<Intent>>{
          DismissIntent: CallbackAction<DismissIntent>(
            onInvoke: (_) {
              _requestDismiss();
              return null;
            },
          ),
        },
        child: Focus(
          focusNode: _host,
          child: Stack(
            fit: StackFit.expand,
            children: <Widget>[
              _buildScrim(context),
              _buildSheet(light: light),
            ],
          ),
        ),
      ),
    );
  }
}
