import 'package:flutter/widgets.dart';

import '../theme/m3e_theme.dart';
import 'm3e_focus_interaction.dart';
import 'm3e_focus_ring_theme.dart';

/// Draws the Material 3 Expressive keyboard focus ring around [child].
///
/// When [focused] is true, the ring is outset around [child]. A transparent
/// [PhysicalModel] with elevation lifts the ring above opaque siblings (e.g.
/// dropdown / list rows) without changing layout spacing between items.
///
/// Always uses a stable [Stack] so toggling [focused] does not remount
/// [child] (important for [EditableText] text-input clients).
class M3EFocusRing extends StatelessWidget {
  /// Creates a focus ring decorator.
  const M3EFocusRing({
    required this.radius,
    required this.child,
    this.focused = false,
    this.animationDuration = Duration.zero,
    this.color,
    this.width,
    this.gap,
    super.key,
  });

  /// Outer border radius of the focused surface.
  final BorderRadius radius;

  /// Content to wrap.
  final Widget child;

  /// Whether the keyboard focus ring should be painted.
  final bool focused;

  /// Optional animation for ring appearance.
  final Duration animationDuration;

  /// Optional color override; defaults to [M3EFocusRingTheme.resolveColor].
  final Color? color;

  /// Optional stroke width override.
  final double? width;

  /// Optional gap override between surface and ring.
  final double? gap;

  /// Theme outset (`gap + width`) for layout clearance.
  static double outsetOf(BuildContext context) {
    return M3ETheme.of(context).focusRingTheme.outset;
  }

  /// Theme tokens for the ambient [M3ETheme].
  static M3EFocusRingTheme themeOf(BuildContext context) {
    return M3ETheme.of(context).focusRingTheme;
  }

  /// Whether the ambient theme allows keyboard focus ring / state chrome.
  static bool indicatorsEnabledOf(BuildContext context) {
    return M3ETheme.of(context).keyboardFocusIndicators;
  }

  /// Whether [node] should show a keyboard focus ring.
  ///
  /// Pass [context] so [M3EThemeData.keyboardFocusIndicators] is respected.
  static bool shouldShow(FocusNode node, [BuildContext? context]) {
    if (context != null) {
      final bool enabled =
          M3ETheme.maybeOf(context)?.keyboardFocusIndicators ?? true;
      if (!enabled) {
        return false;
      }
    }
    if (!node.hasPrimaryFocus) {
      return false;
    }
    if (!M3EFocusInteraction.instance.ringsAllowed) {
      return false;
    }
    return FocusManager.instance.highlightMode ==
        FocusHighlightMode.traditional;
  }

  /// Elevation used while focused so the outset ring composites above
  /// neighboring opaque surfaces without adding layout gaps.
  static const double _focusedElevation = 6;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: M3EFocusInteraction.instance,
      builder: (BuildContext context, Widget? _) => _paint(context),
    );
  }

  Widget _paint(BuildContext context) {
    final theme = M3ETheme.of(context);
    final ringTheme = theme.focusRingTheme;
    final bool show =
        focused &&
        theme.keyboardFocusIndicators &&
        M3EFocusInteraction.instance.ringsAllowed;
    final resolvedColor = color ?? ringTheme.resolveColor(theme.colorScheme);
    final resolvedGap = gap ?? ringTheme.gap;
    final resolvedWidth = width ?? ringTheme.width;
    final outset = resolvedGap + resolvedWidth;

    final adjustedRadius = BorderRadius.only(
      topLeft: Radius.circular(radius.topLeft.x + outset),
      topRight: Radius.circular(radius.topRight.x + outset),
      bottomLeft: Radius.circular(radius.bottomLeft.x + outset),
      bottomRight: Radius.circular(radius.bottomRight.x + outset),
    );

    return PhysicalModel(
      elevation: show ? _focusedElevation : 0,
      color: const Color(0x00000000),
      shadowColor: const Color(0x00000000),
      child: Stack(
        fit: StackFit.passthrough,
        clipBehavior: Clip.none,
        children: [
          child,
          if (show)
            Positioned(
              top: -outset,
              bottom: -outset,
              left: -outset,
              right: -outset,
              child: IgnorePointer(
                child: AnimatedContainer(
                  duration: animationDuration,
                  curve: Curves.easeOutCubic,
                  decoration: BoxDecoration(
                    border: Border.all(
                      color: resolvedColor,
                      width: resolvedWidth,
                    ),
                    borderRadius: adjustedRadius,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
