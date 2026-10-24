import 'package:flutter/gestures.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../theme/m3e_theme.dart';

/// Tracks whether keyboard focus rings are allowed after the last interaction.
///
/// Any pointer interaction (hover, press, scroll, or pan) clears rings. Only
/// keyboard focus-navigation keys enable them again. Focus on nodes is
/// unchanged — only ring visibility is gated.
class M3EFocusInteraction extends ChangeNotifier {
  M3EFocusInteraction._() {
    FocusManager.instance.addHighlightModeListener(_onHighlightModeChanged);
    HardwareKeyboard.instance.addHandler(_handleKeyEvent);
    _bindPointerRoute();
    _syncFromHighlightMode(FocusManager.instance.highlightMode);
  }

  /// Shared process-wide interaction modality for focus rings.
  static final M3EFocusInteraction instance = M3EFocusInteraction._();

  bool _ringsAllowed = false;
  bool _notifyPointerScheduled = false;

  /// Whether keyboard focus rings may paint.
  bool get ringsAllowed => _ringsAllowed;

  /// Listenable for rebuilds when [ringsAllowed] changes.
  Listenable get listenable => this;

  /// Call when a pointer interaction starts so rings hide until keyboard nav.
  ///
  /// [immediate] notifies listeners in this event. Press uses a deferred
  /// notification so a rebuild cannot cancel an in-progress tap.
  void notePointerInteraction({bool immediate = false}) {
    if (!_ringsAllowed) {
      return;
    }
    _ringsAllowed = false;
    if (immediate) {
      notifyListeners();
      return;
    }
    if (_notifyPointerScheduled) {
      return;
    }
    _notifyPointerScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _notifyPointerScheduled = false;
      if (!_ringsAllowed) {
        notifyListeners();
      }
    });
  }

  GestureBinding? _boundPointerBinding;

  void _bindPointerRoute() {
    final GestureBinding binding = GestureBinding.instance;
    if (identical(_boundPointerBinding, binding)) {
      return;
    }
    _boundPointerBinding = binding;
    binding.pointerRouter.addGlobalRoute(_handlePointer);
  }

  void _handlePointer(PointerEvent event) {
    final press = event is PointerDownEvent;
    final pointer =
        press ||
        event is PointerHoverEvent ||
        event is PointerScrollEvent ||
        event is PointerPanZoomStartEvent;
    if (!pointer) {
      return;
    }
    notePointerInteraction(immediate: !press);
  }

  /// Call when keyboard focus navigation becomes active (Tab / arrows).
  void noteKeyboardHighlight() {
    _setRingsAllowed(true);
  }

  void _setRingsAllowed(bool value) {
    if (_ringsAllowed == value) {
      return;
    }
    _ringsAllowed = value;
    notifyListeners();
  }

  void _onHighlightModeChanged(FocusHighlightMode mode) {
    _syncFromHighlightMode(mode);
  }

  void _syncFromHighlightMode(FocusHighlightMode mode) {
    switch (mode) {
      case FocusHighlightMode.touch:
        notePointerInteraction();
      case FocusHighlightMode.traditional:
        break;
    }
  }

  bool _handleKeyEvent(KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return false;
    }
    if (_isFocusNavigationKey(event.logicalKey)) {
      noteKeyboardHighlight();
    }
    return false;
  }

  static bool _isFocusNavigationKey(LogicalKeyboardKey key) {
    return key == LogicalKeyboardKey.tab ||
        key == LogicalKeyboardKey.arrowUp ||
        key == LogicalKeyboardKey.arrowDown ||
        key == LogicalKeyboardKey.arrowLeft ||
        key == LogicalKeyboardKey.arrowRight ||
        key == LogicalKeyboardKey.home ||
        key == LogicalKeyboardKey.end ||
        key == LogicalKeyboardKey.pageUp ||
        key == LogicalKeyboardKey.pageDown;
  }

  /// Ensures the focused widget is visible when keyboard rings are active.
  static void ensureVisibleIfKeyboard(
    BuildContext context, {
    double alignment = 0.5,
    Duration duration = const Duration(milliseconds: 120),
    Curve curve = Curves.easeOut,
  }) {
    if (!instance.ringsAllowed) {
      return;
    }
    if (M3ETheme.maybeOf(context)?.keyboardFocusIndicators == false) {
      return;
    }
    if (FocusManager.instance.highlightMode != FocusHighlightMode.traditional) {
      return;
    }
    final RenderObject? renderObject = context.findRenderObject();
    if (renderObject == null || !renderObject.attached) {
      return;
    }
    Scrollable.ensureVisible(
      context,
      alignment: alignment,
      duration: duration,
      curve: curve,
      alignmentPolicy: ScrollPositionAlignmentPolicy.keepVisibleAtEnd,
    );
  }

  /// Resets singleton state for widget tests. Call from `addTearDown`.
  @visibleForTesting
  static void resetForTest() {
    instance._notifyPointerScheduled = false;
    instance._ringsAllowed = false;
    // Re-bind key handler in case the test binding cleared handlers.
    HardwareKeyboard.instance.removeHandler(instance._handleKeyEvent);
    HardwareKeyboard.instance.addHandler(instance._handleKeyEvent);
    instance._bindPointerRoute();
    instance.notifyListeners();
  }
}
