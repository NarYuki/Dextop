import 'package:flutter/gestures.dart';
import 'package:flutter/physics.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../theme/m3e_theme.dart';
import 'm3e_focus_interaction.dart';
import 'm3e_haptics.dart';
import 'm3e_motion.dart';
import 'm3e_state_layer.dart';
import 'm3e_tappable_ink_scope.dart';

/// Builds the visual for a tappable surface given its interaction [state].
typedef M3EStateWidgetBuilder = Widget Function(
  BuildContext context,
  M3EInteractionState state,
);

/// A reusable interaction primitive powering expressive components.
///
/// It tracks hover, focus and press states, exposes them to [builder], drives
/// an optional spring based press scale, and wires up keyboard activation and
/// semantics so components stay focused on their own visuals.
class M3ETappable extends StatefulWidget {
  /// Creates a tappable interaction surface.
  const M3ETappable({
    required this.builder,
    this.onTap,
    this.onLongPress,
    this.enabled = true,
    this.focusable = true,
    this.skipTraversal,
    this.trackHover = true,
    this.focusOverlay = true,
    this.focusNode,
    this.autofocus = false,
    this.mouseCursor,
    this.semanticLabel,
    this.semanticButton = true,
    this.semanticChecked,
    this.semanticMixed = false,
    this.semanticToggled,
    this.semanticInMutuallyExclusiveGroup = false,
    this.excludeSemantics = false,
    this.pressedScale = 1,
    this.spring = M3EMotion.expressiveSpatialPress,
    this.onStateChanged,
    this.materialInk = false,
    this.haptic = M3EHapticFeedback.none,
    super.key,
  });

  /// Builds the visual for the current interaction [M3EInteractionState].
  final M3EStateWidgetBuilder builder;

  /// Called when the surface is tapped.
  final VoidCallback? onTap;

  /// Called when the surface is long-pressed.
  final VoidCallback? onLongPress;

  /// Whether the surface accepts interaction.
  final bool enabled;

  /// Whether this surface is a keyboard Tab stop.
  ///
  /// When false, pointer activation still works but Tab skips the surface
  /// (use for controls embedded in a focusable parent row).
  final bool focusable;

  /// Tab-order override. Null skips traversal only when [focusable] is false.
  final bool? skipTraversal;

  /// When false, pointer motion does not paint the hover state layer.
  final bool trackHover;

  /// When false, a focused node does not paint the ink focus wash.
  ///
  /// List rows use the inset ring instead, including after a pointer tap.
  final bool focusOverlay;

  /// Optional focus node; one is created internally when null.
  final FocusNode? focusNode;

  /// Whether this surface should request focus when first shown.
  final bool autofocus;

  /// Cursor shown while interactive.
  final MouseCursor? mouseCursor;

  /// Accessibility label for the surface.
  final String? semanticLabel;

  /// Whether semantics treat this as a button.
  final bool semanticButton;

  /// Checked state for checkbox semantics. Null leaves the node unchecked-role
  /// free so other components stay buttons.
  final bool? semanticChecked;

  /// Tristate mixed flag. Used with [semanticChecked] for an indeterminate
  /// checkbox.
  final bool semanticMixed;

  /// On/off state for switch semantics. Null leaves the node free of the
  /// toggle role so other components stay buttons or checkboxes.
  final bool? semanticToggled;

  /// Marks this control as one option in a radio group.
  final bool semanticInMutuallyExclusiveGroup;

  /// Whether to exclude child semantics.
  final bool excludeSemantics;

  /// Scale applied while pressed. `1.0` disables the press scale animation.
  final double pressedScale;

  /// Spring used to animate the press scale in and out.
  final M3ESpring spring;

  /// Notified whenever the resolved interaction state changes.
  final ValueChanged<M3EInteractionState>? onStateChanged;

  /// When true, gestures are handled by the overlay ink well.
  final bool materialInk;

  /// Haptic intensity for tap and long-press. [M3EHapticFeedback.none] disables.
  final M3EHapticFeedback haptic;

  bool get _isInteractive => enabled && (onTap != null || onLongPress != null);

  @override
  /// Creates the mutable state for this widget.
  State<M3ETappable> createState() => _M3ETappableState();
}

class _M3ETappableState extends State<M3ETappable>
    with SingleTickerProviderStateMixin {
  M3EInteractionState _state = const M3EInteractionState();
  late final AnimationController _scaleController;
  FocusNode? _internalFocusNode;
  int? _activePointer;
  final List<ValueNotifier<bool>> _scrolling = <ValueNotifier<bool>>[];
  bool _pointerInside = false;

  /// Raw focus-highlight from [FocusableActionDetector], before modality gate.
  bool _focusHighlight = false;

  FocusNode get _effectiveFocusNode =>
      widget.focusNode ??
      (_internalFocusNode ??= FocusNode(
        canRequestFocus: widget.focusable,
        skipTraversal: !widget.focusable,
      ));

  @override
  void initState() {
    super.initState();
    _scaleController = AnimationController.unbounded(vsync: this, value: 1);
    M3EFocusInteraction.instance.addListener(_onFocusInteractionChanged);
    _applyFocusableToNode();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _bindScrolling();
  }

  @override
  void didUpdateWidget(covariant M3ETappable oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.focusable != widget.focusable ||
        oldWidget.skipTraversal != widget.skipTraversal ||
        oldWidget.focusNode != widget.focusNode) {
      _applyFocusableToNode();
    }
    final bool wasInteractive =
        oldWidget.enabled &&
        (oldWidget.onTap != null || oldWidget.onLongPress != null);
    final bool isInteractive = widget._isInteractive;
    if (wasInteractive && !isInteractive) {
      _update(_state.copyWith(hovered: false, pressed: false));
      _animateScale(1);
    }
    if (!widget.trackHover && _state.hovered) {
      _update(_state.copyWith(hovered: false));
    }
  }

  void _applyFocusableToNode() {
    _effectiveFocusNode
      ..canRequestFocus = widget.focusable
      ..skipTraversal = widget.skipTraversal ?? !widget.focusable;
  }

  @override
  void dispose() {
    _unbindScrolling();
    M3EFocusInteraction.instance.removeListener(_onFocusInteractionChanged);
    _clearPointerRoute();
    _internalFocusNode?.dispose();
    _scaleController.dispose();
    super.dispose();
  }

  void _bindScrolling() {
    final List<ValueNotifier<bool>> next = _collectScrollingNotifiers();
    if (_sameScrollingNotifiers(next)) {
      return;
    }
    _unbindScrolling();
    _scrolling.addAll(next);
    for (final ValueNotifier<bool> notifier in _scrolling) {
      notifier.addListener(_onAncestorScroll);
    }
  }

  /// Ancestor scrollables' `isScrollingNotifier`s, nearest-first.
  List<ValueNotifier<bool>> _collectScrollingNotifiers() {
    final next = <ValueNotifier<bool>>[];
    context.visitAncestorElements((Element element) {
      if (element is StatefulElement && element.state is ScrollableState) {
        next.add(
          (element.state as ScrollableState).position.isScrollingNotifier,
        );
      }
      return true;
    });
    return next;
  }

  bool _sameScrollingNotifiers(List<ValueNotifier<bool>> next) {
    if (next.length != _scrolling.length) {
      return false;
    }
    for (var i = 0; i < next.length; i++) {
      if (!identical(next[i], _scrolling[i])) {
        return false;
      }
    }
    return true;
  }

  void _unbindScrolling() {
    for (final ValueNotifier<bool> notifier in _scrolling) {
      notifier.removeListener(_onAncestorScroll);
    }
    _scrolling.clear();
  }

  bool get _ancestorScrolling {
    for (final ValueNotifier<bool> notifier in _scrolling) {
      if (notifier.value) {
        return true;
      }
    }
    return false;
  }

  void _onAncestorScroll() {
    if (!mounted || !widget.trackHover) {
      return;
    }
    if (_ancestorScrolling) {
      if (_state.hovered) {
        _update(_state.copyWith(hovered: false));
      }
      return;
    }
    if (_pointerInside && !_state.hovered) {
      _update(_state.copyWith(hovered: true));
    }
  }

  void _setHovered(bool hovered) {
    _pointerInside = hovered;
    if (!widget.trackHover) {
      return;
    }
    if (_ancestorScrolling) {
      if (_state.hovered) {
        _update(_state.copyWith(hovered: false));
      }
      return;
    }
    _update(_state.copyWith(hovered: hovered));
  }

  void _onFocusInteractionChanged() {
    if (!mounted) {
      return;
    }
    _syncFocusedVisual();
  }

  void _update(M3EInteractionState next) {
    if (next == _state) {
      return;
    }
    setState(() => _state = next);
    widget.onStateChanged?.call(next);
  }

  void _syncFocusedVisual() {
    if (!mounted) {
      return;
    }
    final bool indicatorsEnabled =
        M3ETheme.maybeOf(context)?.keyboardFocusIndicators ?? true;
    final bool showRing =
        indicatorsEnabled &&
        _focusHighlight &&
        M3EFocusInteraction.instance.ringsAllowed;
    _update(_state.copyWith(focused: showRing));
    if (showRing) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) {
          return;
        }
        M3EFocusInteraction.ensureVisibleIfKeyboard(context);
      });
    }
  }

  void _animateScale(double target) {
    if (widget.pressedScale == 1) {
      return;
    }
    _scaleController.animateWith(
      SpringSimulation(
        widget.spring.toDescription(),
        _scaleController.value,
        target,
        _scaleController.velocity,
      ),
    );
  }

  void _clearPointerRoute() {
    if (_activePointer == null) {
      return;
    }
    GestureBinding.instance.pointerRouter.removeGlobalRoute(
      _handleGlobalPointerEvent,
    );
    _activePointer = null;
  }

  void _handleGlobalPointerEvent(PointerEvent event) {
    if (event.pointer != _activePointer) {
      return;
    }
    if (event is PointerUpEvent || event is PointerCancelEvent) {
      _clearPointerRoute();
      _releasePress();
    }
  }

  void _handlePointerDown(PointerDownEvent event) {
    _clearPointerRoute();
    _activePointer = event.pointer;
    GestureBinding.instance.pointerRouter.addGlobalRoute(
      _handleGlobalPointerEvent,
    );
    if (widget._isInteractive) {
      // Clear rings without requestFocus here — focus on tap so the gesture
      // is not cancelled by a mid-press focus/rebuild.
      M3EFocusInteraction.instance.notePointerInteraction();
      _syncFocusedVisual();
    }
    _update(_state.copyWith(pressed: true));
    _animateScale(widget.pressedScale);
  }

  void _handleShowFocusHighlight(bool value) {
    _focusHighlight = value;
    _syncFocusedVisual();
  }

  void _handlePointerUp(PointerUpEvent event) {
    if (event.pointer != _activePointer) {
      return;
    }
    _clearPointerRoute();
    _releasePress();
  }

  void _handlePointerCancel(PointerCancelEvent event) {
    if (_activePointer != null && event.pointer != _activePointer) {
      return;
    }
    _clearPointerRoute();
    _releasePress();
  }

  void _releasePress() {
    if (!_state.pressed) {
      return;
    }
    _update(_state.copyWith(pressed: false));
    _animateScale(1);
  }

  void _fireHaptic() {
    M3EHaptics.trigger(widget.haptic);
  }

  VoidCallback? _wrapTap(VoidCallback? callback) {
    if (callback == null) {
      return null;
    }
    return () {
      M3EFocusInteraction.instance.notePointerInteraction();
      _effectiveFocusNode.requestFocus();
      _fireHaptic();
      callback();
    };
  }

  VoidCallback? _wrapLongPress(VoidCallback? callback) {
    if (callback == null) {
      return null;
    }
    return () {
      M3EFocusInteraction.instance.notePointerInteraction();
      _effectiveFocusNode.requestFocus();
      _fireHaptic();
      callback();
    };
  }

  @override
  Widget build(BuildContext context) {
    final interactive = widget._isInteractive;
    final VoidCallback? onTap = interactive ? _wrapTap(widget.onTap) : null;
    final VoidCallback? onLongPress = interactive
        ? _wrapLongPress(widget.onLongPress)
        : null;

    Widget content = widget.builder(context, _state);
    if (widget.materialInk) {
      // InkWell owns splash/tap; press scale is driven by [Listener] below so
      // setState rebuilds cannot cancel the press gesture mid-spring.
      content = M3ETappableInkScope(
        onTap: onTap,
        onLongPress: onLongPress,
        mouseCursor: _resolveCursor(interactive),
        trackHover: widget.trackHover,
        focusOverlay: widget.focusOverlay,
        onHover: interactive && widget.trackHover ? _setHovered : null,
        child: content,
      );
    }
    content = _wrapScale(content);
    final Widget pointer = _wrapPointer(
      content,
      interactive,
      onTap,
      onLongPress,
    );
    return _wrapSemantics(_wrapFocus(pointer, interactive));
  }

  Widget _wrapScale(Widget child) {
    if (widget.pressedScale == 1) {
      return child;
    }
    return AnimatedBuilder(
      animation: _scaleController,
      builder: (BuildContext context, Widget? built) {
        return Transform.scale(scale: _scaleController.value, child: built);
      },
      child: child,
    );
  }

  Widget _wrapPointer(
    Widget child,
    bool interactive,
    VoidCallback? onTap,
    VoidCallback? onLongPress,
  ) {
    var wrapped = child;

    if (!widget.materialInk) {
      wrapped = MouseRegion(
        cursor: _resolveCursor(interactive),
        onEnter: widget.trackHover ? (_) => _setHovered(true) : null,
        onExit: widget.trackHover ? (_) => _setHovered(false) : null,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onTap,
          onLongPress: onLongPress,
          child: wrapped,
        ),
      );
    }

    if (!interactive) {
      return wrapped;
    }

    // Always track press with Listener so scale springs in both directions,
    // including when Material ink handles the actual tap.
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: _handlePointerDown,
      onPointerUp: _handlePointerUp,
      onPointerCancel: _handlePointerCancel,
      child: wrapped,
    );
  }

  Widget _wrapFocus(Widget child, bool interactive) {
    // Keyboard activation mirrors a short pointer press so scale / state-layer
    // animations run (Material buttons set WidgetState.pressed the same way).
    Object? activate(Intent intent) {
      if (!interactive || widget.onTap == null) {
        return null;
      }
      _activateFromKeyboard();
      return null;
    }

    Widget focused = FocusableActionDetector(
      enabled: interactive && widget.focusable,
      focusNode: _effectiveFocusNode,
      autofocus: widget.autofocus && widget.focusable,
      onShowFocusHighlight: _handleShowFocusHighlight,
      actions: <Type, Action<Intent>>{
        // Desktop/mobile: Enter → ActivateIntent. Web: Enter → ButtonActivateIntent.
        ActivateIntent: CallbackAction<ActivateIntent>(onInvoke: activate),
        ButtonActivateIntent: CallbackAction<ButtonActivateIntent>(
          onInvoke: activate,
        ),
      },
      child: child,
    );

    // Local shortcuts so FABs and other tappables activate on Enter even when
    // an ancestor remaps keys (or omits ActivateIntent).
    if (!interactive || !widget.focusable) {
      return focused;
    }
    return TapRegion(
      onTapOutside: _onTapOutside,
      child: Shortcuts(
        shortcuts: const <ShortcutActivator, Intent>{
          SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.numpadEnter): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        },
        child: focused,
      ),
    );
  }

  void _onTapOutside(PointerDownEvent event) {
    M3EFocusInteraction.instance.notePointerInteraction();
    if (_effectiveFocusNode.hasPrimaryFocus) {
      _effectiveFocusNode.unfocus();
    }
  }

  void _activateFromKeyboard() {
    final VoidCallback? onTap = widget.onTap;
    if (onTap == null) {
      return;
    }
    _update(_state.copyWith(pressed: true));
    _animateScale(widget.pressedScale);
    _fireHaptic();
    onTap();
    // Hold the pressed visual briefly so the spring is visible, then release.
    Future<void>.delayed(const Duration(milliseconds: 100), () {
      if (!mounted) {
        return;
      }
      _releasePress();
    });
  }

  Widget _wrapSemantics(Widget child) {
    return Semantics(
      container: true,
      button: widget.semanticButton,
      checked: widget.semanticChecked,
      mixed: widget.semanticMixed,
      toggled: widget.semanticToggled,
      inMutuallyExclusiveGroup: widget.semanticInMutuallyExclusiveGroup,
      enabled: widget.enabled,
      label: widget.semanticLabel,
      excludeSemantics: widget.excludeSemantics,
      child: child,
    );
  }

  MouseCursor _resolveCursor(bool interactive) {
    if (!interactive) {
      return SystemMouseCursors.basic;
    }
    return widget.mouseCursor ?? SystemMouseCursors.click;
  }
}
