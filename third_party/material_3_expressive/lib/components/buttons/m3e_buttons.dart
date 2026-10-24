import 'package:flutter/foundation.dart';
import 'package:material_3_expressive/components/buttons/models/m3e_button_measurements.dart';
import 'package:material_3_expressive/components/buttons/res/m3e_button_constants.dart';
import 'package:material_3_expressive/components/buttons/styles/m3e_button_decoration.dart';
import 'package:material_3_expressive/components/buttons/styles/m3e_button_decoration_scope.dart';
import 'package:material_3_expressive/components/buttons/styles/m3e_button_motion.dart';
import 'package:material_3_expressive/components/buttons/styles/m3e_button_theme.dart';
import 'package:material_3_expressive/foundations/foundations.dart';
import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

import '../tooltips/m3e_tooltips.dart';
import 'components/m3e_base_button_state.dart';
import 'components/m3e_radius_and_padding_motion.dart';
import 'enums/m3e_button_enums.dart';
import 'utils/m3e_button_gradient_layer.dart';

export 'components/m3e_no_overflow_strategy.dart';
export 'components/m3e_overflow_strategy.dart';
export 'components/m3e_scroll_overflow_strategy.dart';
export 'enums/m3e_button_enums.dart';
export 'models/m3e_button_measurements.dart';
export 'res/m3e_button_constants.dart';
export 'styles/m3e_button_decoration.dart';
export 'styles/m3e_button_decoration_scope.dart';
export 'styles/m3e_button_motion.dart';
export 'styles/m3e_button_theme.dart';
export 'styles/m3e_overflow_bottom_sheet_decoration.dart';
export 'styles/m3e_overflow_popup_decoration.dart';
export 'utils/m3e_button_gradient_layer.dart';

part 'components/m3e_button_state.dart';
part 'components/m3e_button_style.dart';
part 'components/m3e_button_content.dart';
part 'components/m3e_button_selection_content.dart';
part 'components/m3e_button_selection_shape.dart';

const Alignment _kAlignmentCenter = Alignment.center;
const VisualDensity _kVisualDensityStandard = VisualDensity.standard;
const Duration _kDurationZero = Duration.zero;
const bool _kDefaultEnableFeedback = true;

/// M3EButton.

class M3EButton extends StatefulWidget {
  /// M3EButton.
  const M3EButton({
    super.key,
    required this.onPressed,
    this.child,
    this.icon,
    this.label,
    this.selectedIcon,
    this.selectedLabel,
    this.isSelected,
    this.style = M3EButtonStyle.filled,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.enabled = true,
    this.isGroupConnected = false,
    this.isFirstInGroup = true,
    this.isLastInGroup = true,
    this.statesController,
    this.decoration,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.semanticLabel,
    this.tooltip,
    this.mouseCursor = SystemMouseCursors.click,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  });

  /// Factory for a button with an icon and label.
  factory M3EButton.icon({
    Key? key,
    required VoidCallback? onPressed,
    required Widget icon,
    required Widget label,
    Widget? selectedIcon,
    Widget? selectedLabel,
    bool? isSelected,
    M3EButtonStyle style = M3EButtonStyle.filled,
    M3EButtonSize size = M3EButtonSize.sm,
    M3EButtonShape shape = M3EButtonShape.round,
    bool enabled = true,
    bool isGroupConnected = false,
    bool isFirstInGroup = true,
    bool isLastInGroup = true,
    WidgetStatesController? statesController,
    M3EButtonDecoration? decoration,
    FocusNode? focusNode,
    bool autofocus = false,
    ValueChanged<bool>? onFocusChange,
    String? semanticLabel,
    String? tooltip,
    MouseCursor mouseCursor = SystemMouseCursors.click,
    VoidCallback? onLongPress,
    ValueChanged<bool>? onHover,
    bool enableFeedback = _kDefaultEnableFeedback,
    InteractiveInkFeatureFactory? splashFactory,
  }) {
    return M3EButton(
      key: key,
      onPressed: onPressed,
      style: style,
      size: size,
      shape: shape,
      enabled: enabled,
      isGroupConnected: isGroupConnected,
      isFirstInGroup: isFirstInGroup,
      isLastInGroup: isLastInGroup,
      statesController: statesController,
      decoration: decoration,
      focusNode: focusNode,
      autofocus: autofocus,
      onFocusChange: onFocusChange,
      semanticLabel: semanticLabel,
      tooltip: tooltip,
      mouseCursor: mouseCursor,
      onLongPress: onLongPress,
      onHover: onHover,
      enableFeedback: enableFeedback,
      splashFactory: splashFactory,
      icon: icon,
      label: label,
      selectedIcon: selectedIcon,
      selectedLabel: selectedLabel,
      isSelected: isSelected,
    );
  }

  /// A filled button (highest emphasis).
  const M3EButton.filled({
    super.key,
    required this.onPressed,
    this.child,
    this.icon,
    this.label,
    this.selectedIcon,
    this.selectedLabel,
    this.isSelected,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.enabled = true,
    this.isGroupConnected = false,
    this.isFirstInGroup = true,
    this.isLastInGroup = true,
    this.statesController,
    this.decoration,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.semanticLabel,
    this.tooltip,
    this.mouseCursor = SystemMouseCursors.click,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  }) : style = M3EButtonStyle.filled;

  /// A tonal button (medium emphasis).
  const M3EButton.tonal({
    super.key,
    required this.onPressed,
    this.child,
    this.icon,
    this.label,
    this.selectedIcon,
    this.selectedLabel,
    this.isSelected,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.enabled = true,
    this.isGroupConnected = false,
    this.isFirstInGroup = true,
    this.isLastInGroup = true,
    this.statesController,
    this.decoration,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.semanticLabel,
    this.tooltip,
    this.mouseCursor = SystemMouseCursors.click,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  }) : style = M3EButtonStyle.tonal;

  /// An elevated button (medium emphasis with a shadow).
  const M3EButton.elevated({
    super.key,
    required this.onPressed,
    this.child,
    this.icon,
    this.label,
    this.selectedIcon,
    this.selectedLabel,
    this.isSelected,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.enabled = true,
    this.isGroupConnected = false,
    this.isFirstInGroup = true,
    this.isLastInGroup = true,
    this.statesController,
    this.decoration,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.semanticLabel,
    this.tooltip,
    this.mouseCursor = SystemMouseCursors.click,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  }) : style = M3EButtonStyle.elevated;

  /// An outlined button (medium emphasis with a border).
  const M3EButton.outlined({
    super.key,
    required this.onPressed,
    this.child,
    this.icon,
    this.label,
    this.selectedIcon,
    this.selectedLabel,
    this.isSelected,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.enabled = true,
    this.isGroupConnected = false,
    this.isFirstInGroup = true,
    this.isLastInGroup = true,
    this.statesController,
    this.decoration,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.semanticLabel,
    this.tooltip,
    this.mouseCursor = SystemMouseCursors.click,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  }) : style = M3EButtonStyle.outlined;

  /// A text button (lowest emphasis).
  const M3EButton.text({
    super.key,
    required this.onPressed,
    this.child,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.enabled = true,
    this.statesController,
    this.decoration,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.semanticLabel,
    this.tooltip,
    this.mouseCursor = SystemMouseCursors.click,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  }) : icon = null,
       label = null,
       selectedIcon = null,
       selectedLabel = null,
       isSelected = null,
       isGroupConnected = false,
       isFirstInGroup = true,
       isLastInGroup = true,
       style = M3EButtonStyle.text;

  /// Callback invoked when the button is pressed. Null disables the button.
  final VoidCallback? onPressed;

  /// The child content of the button.
  final Widget? child;

  /// Icon shown by the button; pass null to omit it.
  final Widget? icon;

  /// Label shown by the button; pass null to omit it.
  final Widget? label;

  /// Icon displayed when [isSelected] is true. Falls back to [icon].
  final Widget? selectedIcon;

  /// Label displayed when [isSelected] is true. Falls back to [label].
  final Widget? selectedLabel;

  /// Selection state. Null keeps default, non-toggle button behavior.
  final bool? isSelected;

  /// Visual style of the button.
  ///
  /// See [M3EButtonStyle] for available styles (filled, outlined, tonal, etc.).
  final M3EButtonStyle style;

  /// Size variant of the button.
  ///
  /// See [M3EButtonSize] for available sizes (xs, sm, md, lg, xl).
  final M3EButtonSize size;

  /// Corner radius strategy for the button.
  ///
  /// See [M3EButtonShape] for available shapes (round, square).
  final M3EButtonShape shape;

  /// Whether the button is enabled. Defaults to true.
  final bool enabled;

  /// Whether this button is part of a connected button group.
  final bool isGroupConnected;

  /// Whether this is the first button in a connected group.
  final bool isFirstInGroup;

  /// Whether this is the last button in a connected group.
  final bool isLastInGroup;

  /// Optional controller for managing widget states externally.
  ///
  /// Allows programmatic control of pressed, hovered, focused states.
  final WidgetStatesController? statesController;

  /// Optional decoration that bundles styling properties together.
  ///
  /// When provided, decoration values take precedence over individual flat
  /// parameters (e.g. `backgroundColor`, `foregroundColor`, etc.).
  final M3EButtonDecoration? decoration;

  /// External focus node for keyboard navigation.
  final FocusNode? focusNode;

  /// Whether this button should focus itself on mount.
  final bool autofocus;

  /// Callback fired when focus state changes.
  final ValueChanged<bool>? onFocusChange;

  /// Accessibility label. Merged on top of the button's own semantics.
  final String? semanticLabel;

  /// Tooltip text.
  final String? tooltip;

  /// Custom mouse cursor.
  final MouseCursor mouseCursor;

  /// Callback invoked when the button is long-pressed.
  final VoidCallback? onLongPress;

  /// Callback invoked when the hover state changes.
  final ValueChanged<bool>? onHover;

  /// Whether to show a ripple/splash effect and haptic feedback on press.
  ///
  /// Defaults to true.
  final bool enableFeedback;

  /// The splash factory for the ink ripple effect.
  ///
  /// See [InteractiveInkFeatureFactory] for available options.
  final InteractiveInkFeatureFactory? splashFactory;

  /// decorationBackgroundColor.

  // ── Decoration property helpers ───────────────────────────────────────────

  WidgetStateProperty<Color?>? get decorationBackgroundColor =>
      decoration?.backgroundColor;

  /// decorationForegroundColor.
  WidgetStateProperty<Color?>? get decorationForegroundColor =>
      decoration?.foregroundColor;

  /// decorationBorderSide.
  WidgetStateProperty<BorderSide?>? get decorationBorderSide =>
      decoration?.side;

  /// decorationMotion.
  M3EButtonMotion? get decorationMotion => decoration?.motion;

  /// decorationHaptic.
  M3EHapticFeedback get decorationHaptic =>
      decoration?.haptic ?? M3EHapticFeedback.none;

  /// decorationMouseCursor.
  WidgetStateProperty<MouseCursor?>? get decorationMouseCursor =>
      decoration?.mouseCursor;

  /// decorationPressedRadius.
  double? get decorationPressedRadius => decoration?.pressedRadius;

  /// decorationBorderRadius.
  double? get decorationBorderRadius => decoration?.borderRadius;

  /// Radius used by the selected resting shape.
  double? get decorationSelectedRadius => decoration?.selectedRadius;

  /// Radius used by the unselected resting shape.
  double? get decorationUnselectedRadius => decoration?.unselectedRadius;

  /// Radius used on connected inner corners.
  double? get decorationConnectedInnerRadius =>
      decoration?.connectedInnerRadius;

  /// decorationOverlayColor.
  WidgetStateProperty<Color?>? get decorationOverlayColor =>
      decoration?.overlayColor;

  /// decorationSurfaceTintColor.
  WidgetStateProperty<Color?>? get decorationSurfaceTintColor =>
      decoration?.surfaceTintColor;

  @override
  State<M3EButton> createState() => _M3EButtonState();
}
