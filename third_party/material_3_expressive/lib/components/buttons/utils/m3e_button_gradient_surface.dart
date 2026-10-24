import 'package:material_ui/material_ui.dart';

import 'm3e_button_gradient_fill.dart';
import 'm3e_button_gradient_outline.dart';
import 'm3e_button_gradient_overlay.dart';

/// Fill, overlay, and outline for Material backgroundBuilder.
///
/// Outline is painted on the material bounds (the morphing pill), not the
/// outer tap-target padding.
ButtonLayerBuilder? m3eGradientSurfaceBuilder({
  required BorderRadius clipRadius,
  WidgetStateProperty<Gradient?>? backgroundGradient,
  WidgetStateProperty<Gradient?>? overlayGradient,
  WidgetStateProperty<Gradient?>? outlineGradient,
  WidgetStateProperty<BorderSide?>? outlineSide,
  double outlineFallbackWidth = 1,
  ButtonLayerBuilder? explicitBuilder,
}) {
  if (explicitBuilder == null &&
      backgroundGradient == null &&
      overlayGradient == null &&
      outlineGradient == null &&
      outlineSide == null) {
    return null;
  }
  final ButtonLayerBuilder? fill = m3eGradientBackgroundBuilder(
    clipRadius: clipRadius,
    gradient: backgroundGradient,
    explicitBuilder: explicitBuilder,
  );
  return (BuildContext context, Set<WidgetState> states, Widget? child) {
    Widget result = child ?? const SizedBox.shrink();
    if (fill != null) {
      result = fill(context, states, result);
    }
    result = m3eResolveGradientOverlay(
      clipRadius: clipRadius,
      states: states,
      overlayGradient: overlayGradient,
      child: result,
    );
    final Gradient? outline = outlineGradient?.resolve(states);
    final BorderSide? side = outlineSide?.resolve(states);
    final double width = m3eOutlineWidth(side, fallback: outlineFallbackWidth);
    if (outline != null ||
        (side != null && side.style != BorderStyle.none && outline == null)) {
      result = m3eGradientOutlineLayer(
        clipRadius: clipRadius,
        gradient: outline,
        color: outline == null ? side?.color : null,
        width: width,
        child: result,
      );
    }
    return result;
  };
}
