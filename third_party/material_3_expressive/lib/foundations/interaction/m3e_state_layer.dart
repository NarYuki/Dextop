import 'package:flutter/widgets.dart';

import 'm3e_focus_interaction.dart';
import 'm3e_ink_splash_theme.dart' show M3EInkSplashTheme;

/// Material 3 state layer opacity tokens.
///
/// State layers are translucent overlays of a role color painted on top of a
/// component to communicate hover, focus, pressed and dragged states.
abstract final class M3EStateOpacity {
  const M3EStateOpacity._();

  /// Hover state-layer opacity (8%).
  static const double hover = 0.08;

  /// Focus state-layer opacity (10%).
  static const double focus = 0.1;

  /// Pressed state-layer opacity (10%).
  static const double pressed = 0.1;

  /// Dragged state-layer opacity (16%).
  static const double dragged = 0.16;

  /// Opacity applied to content (icon/label) of a disabled component.
  static const double disabledContent = 0.38;

  /// Opacity applied to the container of a disabled component.
  static const double disabledContainer = 0.12;
}

/// The mutually reinforcing interaction states of a component.
///
/// Only the highest priority active state contributes a state layer at a time,
/// matching the Material specification (pressed over focus over hover).
@immutable
class M3EInteractionState {
  /// Creates an interaction state snapshot.
  const M3EInteractionState({
    this.hovered = false,
    this.focused = false,
    this.pressed = false,
    this.dragged = false,
  });

  /// Whether the pointer is hovering the component.
  final bool hovered;

  /// Whether the component is keyboard-focused.
  final bool focused;

  /// Whether the component is pressed.
  final bool pressed;

  /// Whether the component is being dragged.
  final bool dragged;

  /// Focus fill when rings are not the keyboard chrome.
  bool get showFocusFill =>
      focused && !M3EFocusInteraction.instance.ringsAllowed;

  /// Resolves the effective state layer opacity for the active state.
  double get opacity {
    if (dragged) {
      return M3EStateOpacity.dragged;
    }
    if (pressed) {
      return M3EStateOpacity.pressed;
    }
    if (showFocusFill) {
      return M3EStateOpacity.focus;
    }
    if (hovered) {
      return M3EStateOpacity.hover;
    }
    return 0;
  }

  /// Returns a copy with the given fields replaced.
  M3EInteractionState copyWith({
    bool? hovered,
    bool? focused,
    bool? pressed,
    bool? dragged,
  }) {
    return M3EInteractionState(
      hovered: hovered ?? this.hovered,
      focused: focused ?? this.focused,
      pressed: pressed ?? this.pressed,
      dragged: dragged ?? this.dragged,
    );
  }

  @override
  /// Equality based on public fields.
  bool operator ==(Object other) {
    return other is M3EInteractionState &&
        other.hovered == hovered &&
        other.focused == focused &&
        other.pressed == pressed &&
        other.dragged == dragged;
  }

  @override
  /// Hash code for this interaction state.
  int get hashCode => Object.hash(hovered, focused, pressed, dragged);
}

/// Resolves Material state-layer overlay colors using [M3EStateOpacity] tokens.
abstract final class M3EStateLayer {
  const M3EStateLayer._();

  /// Resolves an overlay color for the highest-priority active [states].
  ///
  /// When keyboard focus rings are active, [WidgetState.focused] does not
  /// contribute a fill overlay (ring-only chrome).
  static Color? resolveOverlayColor(Color color, Set<WidgetState> states) {
    if (states.contains(WidgetState.pressed)) {
      return color.withValues(alpha: M3EStateOpacity.pressed);
    }
    if (states.contains(WidgetState.hovered)) {
      return color.withValues(alpha: M3EStateOpacity.hover);
    }
    if (states.contains(WidgetState.focused) &&
        !M3EFocusInteraction.instance.ringsAllowed) {
      return color.withValues(alpha: M3EStateOpacity.focus);
    }
    return null;
  }

  /// Pressed ripple color. Use with splash on ink widgets or
  /// [M3EInkSplashTheme] for Material buttons.
  static Color splashColor(Color color) =>
      color.withValues(alpha: M3EStateOpacity.pressed);

  /// Hover/focus overlays only. Pressed feedback is splash-only.
  static WidgetStateProperty<Color?> overlayColorHoverFocus(Color color) {
    return WidgetStateProperty.resolveWith((Set<WidgetState> states) {
      if (states.contains(WidgetState.pressed)) {
        return null;
      }
      return resolveOverlayColor(color, states);
    });
  }

  /// Alias for [overlayColorHoverFocus].
  static WidgetStateProperty<Color?> overlayColor(Color color) {
    return overlayColorHoverFocus(color);
  }
}
