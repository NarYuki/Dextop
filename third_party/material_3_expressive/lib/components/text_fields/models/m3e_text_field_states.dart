import 'package:flutter/widgets.dart';

/// Interaction snapshot used to resolve `M3ETextField` tokens.
///
/// Priority follows the spec: disabled, error focus, error hover, error,
/// focus, hover, enabled.
@immutable
class M3ETextFieldStates {
  /// Creates a state snapshot.
  const M3ETextFieldStates({
    this.enabled = true,
    this.hovered = false,
    this.focused = false,
    this.error = false,
  });

  /// Whether the field accepts input.
  final bool enabled;

  /// Whether a pointer hovers the container.
  final bool hovered;

  /// Whether the field has input focus.
  final bool focused;

  /// Whether the field shows error text.
  final bool error;

  /// Hover only counts while enabled.
  bool get isHovered => enabled && hovered;

  /// Focus only counts while enabled.
  bool get isFocused => enabled && focused;

  /// Hover without focus, the trigger for the darker error hover tokens.
  bool get isHoverOnly => isHovered && !isFocused;

  /// Matching [WidgetState]s for state-property overrides.
  Set<WidgetState> toWidgetStates() {
    return <WidgetState>{
      if (!enabled) WidgetState.disabled,
      if (isHovered) WidgetState.hovered,
      if (isFocused) WidgetState.focused,
      if (error) WidgetState.error,
    };
  }

  @override
  bool operator ==(Object other) {
    return other is M3ETextFieldStates &&
        other.enabled == enabled &&
        other.hovered == hovered &&
        other.focused == focused &&
        other.error == error;
  }

  @override
  int get hashCode => Object.hash(enabled, hovered, focused, error);
}
