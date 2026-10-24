import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Shared focus helpers for M3E input components.
abstract final class M3EFocus {
  const M3EFocus._();

  /// Default [EditableText.onTapOutside] handler that unfocuses [focusNode].
  static void unfocusOnTapOutside(FocusNode focusNode, PointerDownEvent event) {
    focusNode.unfocus();
  }

  /// Builds a [TapRegionCallback] that unfocuses [focusNode].
  static TapRegionCallback tapOutsideHandler(FocusNode focusNode) {
    return (PointerDownEvent event) => unfocusOnTapOutside(focusNode, event);
  }

  /// Moves focus on Tab / Shift+Tab for raw [EditableText] hosts.
  ///
  /// Material text fields wire this via editing shortcuts; M3E uses raw
  /// [EditableText], so Tab must be forwarded to focus traversal explicitly.
  ///
  /// Prefer [editableTabShortcuts] over wrapping [EditableText] in [Focus]
  /// with [Focus.onKeyEvent] — ancestor key handlers run before text entry and
  /// can interfere with typing even when they return [KeyEventResult.ignored].
  static KeyEventResult handleEditableTabTraversal(
    FocusNode focusNode,
    KeyEvent event,
  ) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    if (event.logicalKey != LogicalKeyboardKey.tab) {
      return KeyEventResult.ignored;
    }
    final bool moved = HardwareKeyboard.instance.isShiftPressed
        ? focusNode.previousFocus()
        : focusNode.nextFocus();
    return moved ? KeyEventResult.handled : KeyEventResult.ignored;
  }

  /// Tab / Shift+Tab bindings that advance focus without a [Focus] ancestor.
  static Map<ShortcutActivator, VoidCallback> editableTabShortcuts(
    FocusNode focusNode,
  ) {
    return <ShortcutActivator, VoidCallback>{
      const SingleActivator(LogicalKeyboardKey.tab): focusNode.nextFocus,
      const SingleActivator(LogicalKeyboardKey.tab, shift: true):
          focusNode.previousFocus,
    };
  }

  /// Escape unfocuses [focusNode] when it has primary focus.
  ///
  /// Does not clear keyboard-ring modality — the next Tab still shows rings.
  /// Overlay dismiss shortcuts (menus/dropdowns) should wrap above the field
  /// so Escape closes the overlay first.
  static Map<ShortcutActivator, VoidCallback> editableEscapeUnfocus(
    FocusNode focusNode,
  ) {
    return <ShortcutActivator, VoidCallback>{
      const SingleActivator(LogicalKeyboardKey.escape): () {
        if (focusNode.hasPrimaryFocus) {
          focusNode.unfocus();
        }
      },
    };
  }

  /// Tab + Escape bindings for raw [EditableText] hosts.
  static Map<ShortcutActivator, VoidCallback> editableInputShortcuts(
    FocusNode focusNode,
  ) {
    return <ShortcutActivator, VoidCallback>{
      ...editableTabShortcuts(focusNode),
      ...editableEscapeUnfocus(focusNode),
    };
  }
}
