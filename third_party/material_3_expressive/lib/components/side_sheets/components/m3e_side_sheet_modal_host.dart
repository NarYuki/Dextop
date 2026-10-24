part of '../m3e_side_sheets.dart';

/// Page of a modal side sheet route.
///
/// Paints the scrim, springs the sheet in from its edge and out again, and
/// owns the dismiss path: scrim tap, close, Escape, back and predictive back.
class _M3ESideSheetModalHost extends StatefulWidget {
  const _M3ESideSheetModalHost({
    required this.animation,
    required this.sheet,
    required this.theme,
    required this.isDismissible,
    this.onDismissRequest,
    this.controller,
  });

  /// Route animation; only its direction is used.
  final Animation<double> animation;

  /// The sheet.
  final M3ESideSheet sheet;

  /// Resolved sheet theme.
  final M3ESideSheetTheme theme;

  /// Whether tapping the scrim closes the sheet.
  final bool isDismissible;

  /// Guard; resolve false to keep the sheet open.
  final Future<bool> Function()? onDismissRequest;

  /// Optional controller.
  final M3ESideSheetController? controller;

  @override
  State<_M3ESideSheetModalHost> createState() => _M3ESideSheetModalHostState();
}

class _M3ESideSheetModalHostState extends State<_M3ESideSheetModalHost>
    with TickerProviderStateMixin, WidgetsBindingObserver
    implements M3ESideSheetControllerClient {
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
    debugLabel: 'M3ESideSheet',
    skipTraversal: true,
  );

  /// Keyboard users land on the first icon button.
  final bool _keyboard = M3EFocusInteraction.instance.ringsAllowed;
  bool _dismissing = false;
  bool _started = false;
  bool _fromAnchor = false;

  M3ESideSheetMotion get _motion => widget.theme.motion;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.animation.addStatusListener(_onStatus);
    widget.controller?.attach(this);
    if (!_keyboard) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _host.requestFocus();
        }
      });
    }
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
  void didUpdateWidget(covariant _M3ESideSheetModalHost oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.animation != widget.animation) {
      oldWidget.animation.removeStatusListener(_onStatus);
      widget.animation.addStatusListener(_onStatus);
    }
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.detach(this);
      widget.controller?.attach(this);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.animation.removeStatusListener(_onStatus);
    widget.controller?.detach(this);
    _entrance.dispose();
    _scrim.dispose();
    _back.dispose();
    _host.dispose();
    super.dispose();
  }

  void _onStatus(AnimationStatus status) {
    final bool open = status.isForwardOrCompleted;
    m3eSideSheetAnimate(context, _entrance, _motion.enterSpring, open ? 1 : 0);
    m3eSideSheetAnimate(context, _scrim, _motion.scrimSpring, open ? 1 : 0);
    widget.controller?.updateOpen(isOpen: open);
  }

  @override
  bool get isOpen => widget.animation.status.isForwardOrCompleted;

  @override
  void open() {}

  @override
  void close([Object? result]) {
    if (mounted) {
      _pop(result);
    }
  }

  void _pop([Object? result]) {
    final ModalRoute<Object?>? route = ModalRoute.of(context);
    final NavigatorState navigator = Navigator.of(context);
    if (route == null || route.isCurrent) {
      navigator.pop(result);
    } else if (route.isActive) {
      navigator.removeRoute(route, result);
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

  void _onClosePressed() {
    widget.sheet.onClose?.call();
    _requestDismiss();
  }

  bool get _onRight => _m3eSideSheetOnRight(context, widget.sheet.edge);

  // ── Predictive back ────────────────────────────────────────────────────

  @override
  bool handleStartBackGesture(PredictiveBackEvent backEvent) {
    final bool current = ModalRoute.of(context)?.isCurrent ?? false;
    if (!mounted || !current) {
      return false;
    }
    _fromAnchor = (backEvent.swipeEdge == SwipeEdge.right) == _onRight;
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
  /// open settles the preview back to rest. When the guard allows, the
  /// preview stays put while the route exits.
  @override
  void handleCommitBackGesture() {
    if (widget.onDismissRequest == null) {
      close();
      return;
    }
    unawaited(
      _requestDismiss().then((bool closed) {
        if (!closed && mounted) {
          m3eSideSheetAnimate(context, _back, _motion.settleSpring, 0);
        }
      }),
    );
  }

  @override
  void handleCancelBackGesture() =>
      m3eSideSheetAnimate(context, _back, _motion.settleSpring, 0);

  // ── Build ──────────────────────────────────────────────────────────────

  /// Light status and navigation bar icons over the scrim; the sheet sets
  /// its own where it sits under a bar (see [_m3eSideSheetLightBars]).
  @override
  Widget build(BuildContext context) {
    return M3EScrimSystemUi.wrap(
      PopScope<Object?>(
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
            child: AnimatedBuilder(
              animation: Listenable.merge(<Listenable>[
                _entrance,
                _scrim,
                _back,
              ]),
              builder: (BuildContext context, _) => _buildStack(context),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStack(BuildContext context) {
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    final bool right = _onRight;
    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        M3ESideSheetScrim(
          color: widget.theme.scrimColor(scheme),
          progress: _scrim.value,
          label: widget.sheet.labels.scrim,
          onTap: widget.isDismissible ? _requestDismiss : null,
        ),
        Align(
          alignment: right ? Alignment.centerRight : Alignment.centerLeft,
          child: FractionalTranslation(
            translation: Offset((right ? 1 : -1) * (1 - _entrance.value), 0),
            child: _m3eSideSheetLightBars(
              context,
              _M3ESideSheetScope(
                modal: 1,
                back: _back.value,
                fromAnchoredEdge: _fromAnchor,
                onClose: _onClosePressed,
                autofocus: _keyboard,
                routeScope: true,
                child: widget.sheet,
              ),
            ),
          ),
        ),
      ],
    );
  }
}
