import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';

/// Opacity for a gradient state layer from [states].
double m3eOverlayOpacityFor(Set<WidgetState> states) {
  if (states.contains(WidgetState.disabled)) {
    return 0;
  }
  if (states.contains(WidgetState.pressed)) {
    return M3EStateOpacity.pressed;
  }
  // Focus chrome is the outset ring (keyboard); no sticky focus fill.
  if (states.contains(WidgetState.hovered)) {
    return M3EStateOpacity.hover;
  }
  return 0;
}

/// Maps an [M3EInteractionState] onto [WidgetState]s.
Set<WidgetState> m3eStatesForInteraction(
  M3EInteractionState state, {
  bool enabled = true,
}) {
  return <WidgetState>{
    if (!enabled) WidgetState.disabled,
    if (state.hovered) WidgetState.hovered,
    if (state.focused) WidgetState.focused,
    if (state.pressed) WidgetState.pressed,
    if (state.dragged) WidgetState.dragged,
  };
}

/// Paints [gradient] as a clipped state layer over [child].
Widget m3eGradientOverlayLayer({
  required BorderRadius clipRadius,
  required Gradient gradient,
  required double opacity,
  required Widget child,
}) {
  if (opacity <= 0) {
    return child;
  }
  return ClipRRect(
    borderRadius: clipRadius,
    child: Stack(
      fit: StackFit.passthrough,
      children: <Widget>[
        child,
        Positioned.fill(
          child: IgnorePointer(
            child: Opacity(
              opacity: opacity,
              child: DecoratedBox(
                decoration: BoxDecoration(gradient: gradient),
              ),
            ),
          ),
        ),
      ],
    ),
  );
}

/// Resolves [overlayGradient] and paints it when the state layer is active.
Widget m3eResolveGradientOverlay({
  required BorderRadius clipRadius,
  required Set<WidgetState> states,
  required Widget child,
  WidgetStateProperty<Gradient?>? overlayGradient,
}) {
  if (overlayGradient == null) {
    return child;
  }
  final Gradient? resolved = overlayGradient.resolve(states);
  if (resolved == null) {
    return child;
  }
  return m3eGradientOverlayLayer(
    clipRadius: clipRadius,
    gradient: resolved,
    opacity: m3eOverlayOpacityFor(states),
    child: child,
  );
}

/// Whether [overlayGradient] should replace Material ink splash.
bool m3eUsesGradientOverlay(WidgetStateProperty<Gradient?>? overlayGradient) {
  return overlayGradient != null;
}
