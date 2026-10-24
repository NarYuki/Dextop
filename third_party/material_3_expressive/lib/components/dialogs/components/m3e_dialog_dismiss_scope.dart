import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

import '../controllers/m3e_dialog_controller.dart';
import '../enums/m3e_dialog_enums.dart';
import '../models/m3e_dialog_discard_labels.dart';

/// Shows the discard confirmation; resolves true to discard.
typedef M3EDialogDiscardPrompt = Future<bool?> Function(
  BuildContext context,
  M3EDialogDiscardLabels labels,
);

/// Single dismiss path for dialog routes.
///
/// Escape, system back, barrier taps and close actions run
/// [onDismissRequest], then the discard confirmation when the [controller]
/// has unsaved changes. Also moves focus to the first interactive element.
class M3EDialogDismissScope extends StatefulWidget {
  /// M3EDialogDismissScope.
  const M3EDialogDismissScope({
    required this.child,
    required this.discardPrompt,
    this.controller,
    this.onDismissRequest,
    this.autofocusFirst = true,
    this.variant,
    super.key,
  });

  /// Layout reported to the [controller].
  final M3EDialogVariant? variant;

  /// Dialog surface.
  final Widget child;

  /// Builds the discard confirmation.
  final M3EDialogDiscardPrompt discardPrompt;

  /// Optional programmatic controller.
  final M3EDialogController? controller;

  /// Guard; resolve false to keep the dialog open.
  final Future<bool> Function()? onDismissRequest;

  /// Whether to focus the first interactive element on open.
  final bool autofocusFirst;

  @override
  State<M3EDialogDismissScope> createState() => _M3EDialogDismissScopeState();
}

class _M3EDialogDismissScopeState extends State<M3EDialogDismissScope>
    implements M3EDialogControllerClient {
  final FocusNode _host = FocusNode(
    debugLabel: 'M3EDialog',
    skipTraversal: true,
  );
  bool _dismissing = false;

  bool get _guarded =>
      widget.onDismissRequest != null ||
      (widget.controller?.hasUnsavedChanges ?? false);

  @override
  void initState() {
    super.initState();
    widget.controller
      ?..attach(this)
      ..updateVariant(widget.variant);
    WidgetsBinding.instance.addPostFrameCallback((_) => _focusFirst());
  }

  @override
  void didUpdateWidget(covariant M3EDialogDismissScope oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.detach(this);
      widget.controller?.attach(this);
    }
  }

  @override
  void dispose() {
    widget.controller?.detach(this);
    _host.dispose();
    super.dispose();
  }

  void _focusFirst() {
    if (!mounted) {
      return;
    }
    final FocusScopeNode scope = FocusScope.of(context);
    final FocusNode? current = scope.focusedChild;
    if (current != null && current != _host) {
      return;
    }
    // Only keyboard users land on the first element; pointer users get a
    // focused dialog with no highlighted control (Tab starts at the first).
    final bool keyboard = M3EFocusInteraction.instance.ringsAllowed;
    if (!widget.autofocusFirst || !keyboard || !_host.nextFocus()) {
      _host.requestFocus();
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

  @override
  void closeDialog([Object? result]) {
    if (mounted) {
      _pop(result);
    }
  }

  @override
  Future<bool> requestDismiss() async {
    if (_dismissing || !mounted) {
      return false;
    }
    _dismissing = true;
    try {
      final bool allowed = await _guardAllows() && await _discardAllows();
      if (allowed && mounted) {
        _pop();
        return true;
      }
      return false;
    } finally {
      _dismissing = false;
    }
  }

  Future<bool> _guardAllows() async {
    final Future<bool> Function()? guard = widget.onDismissRequest;
    return guard == null || await guard();
  }

  Future<bool> _discardAllows() async {
    final M3EDialogController? controller = widget.controller;
    if (controller == null || !controller.hasUnsavedChanges || !mounted) {
      return true;
    }
    final bool? discard = await widget.discardPrompt(
      context,
      controller.discardLabels,
    );
    return discard ?? false;
  }

  Widget _popScope(BuildContext context, Widget? child) {
    return PopScope<Object?>(
      canPop: !_guarded,
      onPopInvokedWithResult: (bool didPop, Object? result) {
        if (!didPop) {
          requestDismiss();
        }
      },
      child: child!,
    );
  }

  @override
  Widget build(BuildContext context) {
    final content = Actions(
      actions: <Type, Action<Intent>>{
        DismissIntent: CallbackAction<DismissIntent>(
          onInvoke: (_) {
            requestDismiss();
            return null;
          },
        ),
      },
      child: Focus(focusNode: _host, child: widget.child),
    );
    final M3EDialogController? controller = widget.controller;
    if (controller == null) {
      return _popScope(context, content);
    }
    return ListenableBuilder(
      listenable: controller,
      builder: _popScope,
      child: content,
    );
  }
}
