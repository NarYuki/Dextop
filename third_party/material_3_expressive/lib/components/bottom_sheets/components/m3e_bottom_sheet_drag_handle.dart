import 'package:flutter/semantics.dart';
import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart'
    show InkSparkle, InkWell, Material, MaterialType, WidgetState;

import '../../../foundations/foundations.dart';
import '../styles/m3e_bottom_sheet_theme.dart';

/// Drag handle button of a bottom sheet.
///
/// A 48dp target around the 32×4 handle. Tab focuses it; tap, Space and
/// Enter call [onActivate]. Only this element is labelled (role button).
class M3EBottomSheetDragHandle extends StatelessWidget {
  /// M3EBottomSheetDragHandle.
  const M3EBottomSheetDragHandle({
    required this.theme,
    required this.label,
    required this.value,
    required this.onActivate,
    this.actions = const <CustomSemanticsAction, VoidCallback>{},
    this.focusNode,
    super.key,
  });

  /// Resolved sheet theme.
  final M3EBottomSheetTheme theme;

  /// Accessibility label.
  final String label;

  /// Spoken current height.
  final String value;

  /// Cycles the preset heights.
  final VoidCallback? onActivate;

  /// Expand / collapse / dismiss semantic actions.
  final Map<CustomSemanticsAction, VoidCallback> actions;

  /// Focus node of the handle.
  final FocusNode? focusNode;

  @override
  Widget build(BuildContext context) {
    final M3EBottomSheetDragHandleStyle style = theme.dragHandle;
    return M3ETappable(
      materialInk: true,
      onTap: onActivate,
      focusNode: focusNode,
      focusOverlay: false,
      mouseCursor: style.mouseCursor,
      semanticLabel: label,
      builder: (BuildContext context, M3EInteractionState state) {
        return Semantics(
          value: value,
          customSemanticsActions: actions,
          child: SizedBox.square(
            dimension: style.targetSize,
            child: Builder(
              builder: (BuildContext context) =>
                  _ink(context, state, Center(child: _pill(context, state))),
            ),
          ),
        );
      },
    );
  }

  Widget _pill(BuildContext context, M3EInteractionState state) {
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    final radius = BorderRadius.circular(theme.handleCornerRadius);
    return M3EFocusRing(
      focused: state.focused,
      radius: radius,
      color: theme.focusRingColor(scheme),
      width: theme.dragHandle.focusRingWidth,
      gap: theme.dragHandle.focusRingGap,
      child: Container(
        width: theme.handleWidth,
        height: theme.handleHeight,
        decoration: BoxDecoration(
          color: theme.handleColor(scheme),
          borderRadius: radius,
        ),
      ),
    );
  }

  Widget _ink(BuildContext context, M3EInteractionState state, Widget child) {
    final M3ETappableInkScope? ink = M3ETappableInkScope.maybeOf(context);
    if (ink == null || !ink.isInteractive) {
      return child;
    }
    final M3EBottomSheetDragHandleStyle style = theme.dragHandle;
    final Color color = theme.handleStateColor(
      M3ETheme.of(context).colorScheme,
    );
    const shape = CircleBorder();
    return Material(
      type: MaterialType.transparency,
      shape: shape,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: ink.onTap,
        onTapDown: ink.onTapDown,
        onTapUp: ink.onTapUp,
        onTapCancel: ink.onTapCancel,
        onHover: ink.onHover,
        mouseCursor: ink.mouseCursor ?? MouseCursor.defer,
        canRequestFocus: false,
        customBorder: shape,
        splashFactory: InkSparkle.splashFactory,
        splashColor: color.withValues(alpha: style.pressedOpacity),
        highlightColor: const Color(0x00000000),
        overlayColor: WidgetStateProperty.resolveWith((Set<WidgetState> s) {
          if (s.contains(WidgetState.pressed)) {
            return null;
          }
          if (s.contains(WidgetState.hovered)) {
            return color.withValues(alpha: style.hoverOpacity);
          }
          return state.showFocusFill
              ? color.withValues(alpha: style.focusOpacity)
              : null;
        }),
        child: child,
      ),
    );
  }
}
