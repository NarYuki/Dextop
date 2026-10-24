import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../controllers/m3e_snackbar_controller.dart';

/// Provides [dismiss] to snackbar descendants (action / close).
class M3ESnackbarDismissScope extends InheritedWidget {
  /// Creates a dismiss scope.
  const M3ESnackbarDismissScope({
    required this.dismiss,
    required super.child,
    super.key,
  });

  /// Requests the host to dismiss the snackbar.
  final VoidCallback dismiss;

  /// The nearest dismiss callback, if any.
  static VoidCallback? maybeOf(BuildContext context) {
    return context
        .dependOnInheritedWidgetOfExactType<M3ESnackbarDismissScope>()
        ?.dismiss;
  }

  @override
  bool updateShouldNotify(M3ESnackbarDismissScope oldWidget) {
    return dismiss != oldWidget.dismiss;
  }
}

class _DismissSnackbarIntent extends Intent {
  const _DismissSnackbarIntent();
}

/// Animates a snackbar in from the bottom, holds it, then removes [entry].
class M3ESnackbarHost extends StatefulWidget {
  /// M3ESnackbarHost.
  const M3ESnackbarHost({
    required this.child,
    required this.entry,
    this.duration,
    this.controller,
    super.key,
  });

  /// Snackbar content.
  final Widget child;

  /// Auto-dismiss duration. Null means persistent until dismissed.
  final Duration? duration;

  /// Overlay entry to remove on dismiss.
  final OverlayEntry entry;

  /// Optional controller that owns this presentation.
  final M3ESnackbarController? controller;

  @override
  State<M3ESnackbarHost> createState() => M3ESnackbarHostState();
}

/// State for [M3ESnackbarHost].
class M3ESnackbarHostState extends State<M3ESnackbarHost>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<Offset> _offset;
  Timer? _timer;
  bool _dismissing = false;

  @override
  void initState() {
    super.initState();
    widget.controller?.attachHost(this);
    _controller = AnimationController(
      vsync: this,
      duration: M3EMotion.medium2,
      reverseDuration: M3EMotion.short4,
    );
    _offset = Tween<Offset>(begin: const Offset(0, 1.5), end: Offset.zero)
        .animate(
          CurvedAnimation(parent: _controller, curve: M3EMotion.emphasized),
        );
    _controller.forward();
    final Duration? duration = widget.duration;
    if (duration != null) {
      _timer = Timer(duration, dismiss);
    }
  }

  @override
  void didUpdateWidget(M3ESnackbarHost oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.detachHost(this);
      widget.controller?.attachHost(this);
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    widget.controller?.detachHost(this);
    _controller.dispose();
    super.dispose();
  }

  /// Plays the exit animation and removes the overlay entry.
  Future<void> dismiss() async {
    if (_dismissing) {
      return;
    }
    _dismissing = true;
    _timer?.cancel();
    if (!mounted) {
      _removeEntry();
      return;
    }
    await _controller.reverse();
    _removeEntry();
  }

  void _removeEntry() {
    widget.controller?.notifyClosed(widget.entry);
    try {
      widget.entry.remove();
    } catch (_) {
      // Already removed (e.g. controller.dispose cleared the overlay).
    }
  }

  @override
  Widget build(BuildContext context) {
    final snackTheme = M3ETheme.of(context).snackBarTheme;
    return Positioned(
      left: snackTheme.overlayHorizontalInset + M3ESafeArea.leftOf(context),
      right: snackTheme.overlayHorizontalInset + M3ESafeArea.rightOf(context),
      bottom:
          snackTheme.overlayBottomInset + M3ESafeArea.overlayBottomOf(context),
      child: Align(
        alignment: snackTheme.overlayAlignment,
        child: SlideTransition(
          position: _offset,
          child: FocusScope(
            child: Shortcuts(
              shortcuts: const <ShortcutActivator, Intent>{
                SingleActivator(LogicalKeyboardKey.escape):
                    _DismissSnackbarIntent(),
              },
              child: Actions(
                actions: <Type, Action<Intent>>{
                  _DismissSnackbarIntent:
                      CallbackAction<_DismissSnackbarIntent>(
                        onInvoke: (_DismissSnackbarIntent intent) {
                          dismiss();
                          return null;
                        },
                      ),
                },
                child: M3ESnackbarDismissScope(
                  dismiss: dismiss,
                  child: widget.child,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
