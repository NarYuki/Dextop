part of '../m3e_side_sheets.dart';

/// Places a side sheet next to (standard) or over (modal) [body].
///
/// When a standard sheet opens, [body] shrinks to make room for it and
/// keeps a 24 margin on its trailing edge. In
/// [M3ESideSheetLayoutMode.adaptive] the sheet is modal on compact windows
/// (below 600) and standard on medium and wider ones; crossing the breakpoint
/// while open morphs between the two with a spatial spring.
class M3ESideSheetLayout extends StatefulWidget {
  /// M3ESideSheetLayout.
  const M3ESideSheetLayout({
    required this.body,
    required this.sheet,
    this.controller,
    this.mode = M3ESideSheetLayoutMode.adaptive,
    this.initiallyOpen = false,
    this.isDismissible = true,
    this.onOpenChanged,
    super.key,
  });

  /// Main content.
  final Widget body;

  /// The side sheet. Its variant is chosen by [mode].
  final M3ESideSheet sheet;

  /// Opens and closes the sheet.
  final M3ESideSheetController? controller;

  /// Standard, modal, or chosen by window width.
  final M3ESideSheetLayoutMode mode;

  /// Whether the sheet starts open.
  final bool initiallyOpen;

  /// Whether tapping the scrim closes a modal sheet.
  final bool isDismissible;

  /// Called when the sheet opens or closes.
  final ValueChanged<bool>? onOpenChanged;

  @override
  State<M3ESideSheetLayout> createState() => _M3ESideSheetLayoutState();
}

class _M3ESideSheetLayoutState extends State<M3ESideSheetLayout>
    with TickerProviderStateMixin, WidgetsBindingObserver
    implements M3ESideSheetControllerClient {
  late final AnimationController _open = AnimationController.unbounded(
    vsync: this,
    value: widget.initiallyOpen ? 1 : 0,
  );
  late final AnimationController _modal = AnimationController.unbounded(
    vsync: this,
  );
  late final AnimationController _back = AnimationController.unbounded(
    vsync: this,
  );
  final FocusNode _sheetFocus = FocusNode(
    debugLabel: 'M3ESideSheetLayout sheet',
    skipTraversal: true,
  );
  final GlobalKey _sheetKey = GlobalKey(debugLabel: 'M3ESideSheetLayout');
  late bool _isOpen = widget.initiallyOpen;
  bool? _isModal;
  bool _keyboard = false;
  bool _fromAnchor = false;

  M3ESideSheetTheme get _theme =>
      widget.sheet.theme ?? M3ETheme.of(context).sideSheetTheme;

  bool get _modalActive => _isOpen && (_isModal ?? false);

  bool get _onRight => _m3eSideSheetOnRight(context, widget.sheet.edge);

  @override
  bool get isOpen => _isOpen;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.controller?.attach(this);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncMode();
  }

  @override
  void didUpdateWidget(covariant M3ESideSheetLayout oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.detach(this);
      widget.controller?.attach(this);
    }
    _syncMode();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.controller?.detach(this);
    _open.dispose();
    _modal.dispose();
    _back.dispose();
    _sheetFocus.dispose();
    super.dispose();
  }

  void _syncMode() {
    final bool modal = switch (widget.mode) {
      M3ESideSheetLayoutMode.modal => true,
      M3ESideSheetLayoutMode.standard => false,
      M3ESideSheetLayoutMode.adaptive =>
        MediaQuery.sizeOf(context).width < _theme.compactBreakpoint,
    };
    if (_isModal == null) {
      _isModal = modal;
      _modal.value = modal ? 1 : 0;
      return;
    }
    if (modal == _isModal) {
      return;
    }
    _isModal = modal;
    m3eSideSheetAnimate(
      context,
      _modal,
      _theme.motion.layoutSpring,
      modal ? 1 : 0,
    );
  }

  @override
  void open() {
    if (_isOpen) {
      return;
    }
    _keyboard = M3EFocusInteraction.instance.ringsAllowed;
    _back.value = 0;
    setState(() => _isOpen = true);
    m3eSideSheetAnimate(context, _open, _theme.motion.enterSpring, 1);
    _notify();
    if ((_isModal ?? false) && !_keyboard) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _isOpen) {
          _sheetFocus.requestFocus();
        }
      });
    }
  }

  @override
  void close([Object? result]) {
    if (!_isOpen) {
      return;
    }
    setState(() => _isOpen = false);
    m3eSideSheetAnimate(context, _open, _theme.motion.enterSpring, 0);
    _notify();
  }

  void _notify() {
    widget.controller?.updateOpen(isOpen: _isOpen);
    widget.onOpenChanged?.call(_isOpen);
  }

  void _onClosePressed() {
    widget.sheet.onClose?.call();
    close();
  }

  // ── Predictive back (modal only) ───────────────────────────────────────

  @override
  bool handleStartBackGesture(PredictiveBackEvent backEvent) {
    final bool current = ModalRoute.of(context)?.isCurrent ?? true;
    if (!mounted || !_modalActive || !current) {
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

  @override
  void handleCommitBackGesture() => close();

  @override
  void handleCancelBackGesture() =>
      m3eSideSheetAnimate(context, _back, _theme.motion.settleSpring, 0);

  @override
  Widget build(BuildContext context) => _buildLayout(context);
}
