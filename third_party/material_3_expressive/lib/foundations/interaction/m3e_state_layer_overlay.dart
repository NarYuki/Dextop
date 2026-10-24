import 'package:material_ui/material_ui.dart';

import 'm3e_motion.dart';
import 'm3e_state_layer.dart';
import 'm3e_tappable_ink_scope.dart';

/// Paints a Material ink splash clipped to [shape] behind [child].
///
/// When wrapped in [M3ETappableInkScope], uses [InkWell] for the Material
/// ripple and state overlays. Size [child] to the desired bounds at the call
/// site so ink covers the surface.
class M3EStateLayerOverlay extends StatelessWidget {
  /// Creates a state-layer / ink overlay behind [child].
  const M3EStateLayerOverlay({
    required this.state,
    required this.color,
    required this.shape,
    required this.child,
    this.alignment = AlignmentDirectional.topStart,
    super.key,
  });

  /// The active interaction state driving fallback overlays when no ink scope
  /// is present.
  final M3EInteractionState state;

  /// The role color painted as the overlay.
  final Color color;

  /// The border used to clip the overlay to the component shape.
  final ShapeBorder shape;

  /// Content rendered above the state layer.
  final Widget child;

  /// Positions [child] when the parent supplies tight width and height bounds.
  final AlignmentGeometry alignment;

  @override
  /// Builds the ink well or fallback state-layer overlay.
  Widget build(BuildContext context) {
    final M3ETappableInkScope? ink = M3ETappableInkScope.maybeOf(context);
    if (ink != null && ink.isInteractive) {
      return Material(
        type: MaterialType.transparency,
        color: Colors.transparent,
        shape: shape,
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: ink.onTap,
          onLongPress: ink.onLongPress,
          mouseCursor: ink.mouseCursor ?? MouseCursor.defer,
          onTapDown: ink.onTapDown,
          onTapUp: ink.onTapUp,
          onTapCancel: ink.onTapCancel,
          onHover: ink.onHover,
          canRequestFocus: false,
          customBorder: shape,
          splashFactory: InkSparkle.splashFactory,
          splashColor: M3EStateLayer.splashColor(color),
          highlightColor: Colors.transparent,
          overlayColor: WidgetStateProperty.resolveWith((
            Set<WidgetState> states,
          ) {
            final resolved = Set<WidgetState>.of(states);
            if (!ink.trackHover) {
              resolved.remove(WidgetState.hovered);
            }
            if (!ink.focusOverlay) {
              resolved.remove(WidgetState.focused);
            }
            if (ink.trackHover && resolved.contains(WidgetState.pressed)) {
              return null;
            }
            return M3EStateLayer.resolveOverlayColor(color, resolved);
          }),
          child: child,
        ),
      );
    }

    if (state.opacity == 0) {
      return child;
    }

    return IgnorePointer(
      child: AnimatedContainer(
        duration: M3EMotion.short3,
        curve: M3EMotion.standard,
        alignment: alignment,
        decoration: ShapeDecoration(
          shape: shape,
          color: color.withValues(alpha: state.opacity),
        ),
        child: child,
      ),
    );
  }
}
