import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../buttons/components/m3e_radius_and_padding_motion.dart';
import '../buttons/enums/m3e_button_enums.dart';
import '../buttons/utils/m3e_button_gradient_layer.dart';
import '../tooltips/m3e_tooltips.dart';
import 'enums/m3e_icon_button_enums.dart';
import 'styles/m3e_icon_button_decoration.dart';
import 'styles/m3e_icon_button_shapes.dart';
import 'styles/m3e_icon_button_theme.dart';

export 'enums/m3e_icon_button_enums.dart';
export 'styles/m3e_icon_button_decoration.dart';
export 'styles/m3e_icon_button_shapes.dart';
export 'styles/m3e_icon_button_theme.dart';

part 'components/m3e_icon_button_build.dart';

/// Material 3 Expressive Icon Button
///
/// - Visual sizes are defined by [M3EIconButtonTheme.visual] (per size × width)
/// - Tap target respects [M3EIconButtonTheme.target] with a minimum of 48×48 on XS/SM
/// - Variants: standard, filled, tonal, outlined
/// - Shapes: round (pill) or square (rounded rect). Toggle can flip shape when selected.
/// - Widths: default, narrow, wide
/// - Toggle: [isSelected] + [selectedIcon]
///  - Badge: [String] or [num]
class M3EIconButton extends StatefulWidget {
  /// M3EIconButton.
  const M3EIconButton({
    super.key,
    required this.icon,
    this.onPressed,
    this.tooltip,
    this.semanticLabel,
    this.variant = M3EIconButtonVariant.filled,
    this.size = M3EIconButtonSize.sm,
    this.shape = M3EIconButtonShapeVariant.round,
    this.width = M3EIconButtonWidth.defaultWidth,
    this.isSelected,
    this.selectedIcon,
    this.enableFeedback,
    this.haptic = M3EHapticFeedback.none,
    this.badgeValue,
    this.suppressInk = false,
    this.visualSize,
    this.decoration,
    this.isGroupConnected = false,
    this.isFirstInGroup = true,
    this.isLastInGroup = true,
    this.inflateHitTarget = true,
    this.matchParentConstraints = false,
    this.statesController,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
  });

  /// icon.

  final Widget icon;

  /// onPressed.
  final VoidCallback? onPressed;

  /// tooltip.
  final String? tooltip;

  /// semanticLabel.
  final String? semanticLabel;

  /// variant.
  final M3EIconButtonVariant variant;

  /// size.
  final M3EIconButtonSize size;

  /// shape.
  final M3EIconButtonShapeVariant shape;

  /// width.
  final M3EIconButtonWidth width;

  /// isSelected.
  final bool? isSelected;

  /// selectedIcon.
  final Widget? selectedIcon;

  /// enableFeedback.
  final bool? enableFeedback;

  /// Haptic intensity on press. Defaults to [M3EHapticFeedback.none].
  final M3EHapticFeedback haptic;

  /// badgeValue.
  final Object? badgeValue;

  /// When true, suppresses splash/hover ink effects.
  final bool suppressInk;

  /// Overrides theme visual size when non-null (e.g. sprung toolbar label width).
  ///
  /// Height should typically match [M3EIconButtonTheme.visual] height for
  /// [size]/[width]. The outer tap target uses at least the theme target,
  /// or this size when larger.
  final Size? visualSize;

  /// Optional decoration for solid and gradient surfaces.
  final M3EIconButtonDecoration? decoration;

  /// Whether this icon button belongs to a connected button group.
  final bool isGroupConnected;

  /// Whether this is the first visual item in its connected group.
  final bool isFirstInGroup;

  /// Whether this is the last visual item in its connected group.
  final bool isLastInGroup;

  /// When true (default), XS/S outer layout uses the 48dp target.
  ///
  /// Button groups set this to false so icon actions share the group's
  /// visual height with labeled button siblings.
  final bool inflateHitTarget;

  /// When true, visual width/height track the parent's max constraints.
  ///
  /// Button groups enable this so icon actions expand and shrink with
  /// neighbor-squish slot sizes (same as labeled button actions).
  final bool matchParentConstraints;

  /// Optional externally owned state controller.
  final WidgetStatesController? statesController;

  /// Optional externally owned focus node.
  final FocusNode? focusNode;

  /// Whether this icon button requests focus initially.
  final bool autofocus;

  /// Called when this icon button gains or loses focus.
  final ValueChanged<bool>? onFocusChange;

  @override
  State<M3EIconButton> createState() => _M3EIconButtonState();
}

class _M3EIconButtonState extends State<M3EIconButton> {
  late final WidgetStatesController _internalStatesController;
  late final ValueNotifier<bool> _isPointerDownNotifier;
  late final ValueNotifier<bool> _isHoveredNotifier;
  late final ValueNotifier<bool> _isPressedNotifier;
  late final ValueNotifier<bool> _showFocusRingNotifier;
  final FocusNode _internalFocusNode = FocusNode(debugLabel: 'M3EIconButton');

  WidgetStatesController get _statesController =>
      widget.statesController ?? _internalStatesController;

  FocusNode get _focusNode => widget.focusNode ?? _internalFocusNode;

  @override
  void initState() {
    super.initState();
    _internalStatesController = WidgetStatesController();
    _statesController.addListener(_onStatesChanged);
    _focusNode.addListener(_onFocusChanged);
    _isPointerDownNotifier = ValueNotifier(false);
    _isHoveredNotifier = ValueNotifier(false);
    _isPressedNotifier = ValueNotifier(false);
    _showFocusRingNotifier = ValueNotifier(false);
    FocusManager.instance.addHighlightModeListener(_onHighlightModeChanged);
    M3EFocusInteraction.instance.addListener(_onFocusInteractionChanged);
  }

  @override
  void didUpdateWidget(covariant M3EIconButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.statesController != widget.statesController) {
      (oldWidget.statesController ?? _internalStatesController).removeListener(
        _onStatesChanged,
      );
      _statesController.addListener(_onStatesChanged);
    }
    if (oldWidget.focusNode != widget.focusNode) {
      (oldWidget.focusNode ?? _internalFocusNode).removeListener(
        _onFocusChanged,
      );
      _focusNode.addListener(_onFocusChanged);
    }
  }

  @override
  void dispose() {
    FocusManager.instance.removeHighlightModeListener(_onHighlightModeChanged);
    M3EFocusInteraction.instance.removeListener(_onFocusInteractionChanged);
    _statesController.removeListener(_onStatesChanged);
    _focusNode.removeListener(_onFocusChanged);
    _internalStatesController.dispose();
    _isPointerDownNotifier.dispose();
    _isHoveredNotifier.dispose();
    _isPressedNotifier.dispose();
    _showFocusRingNotifier.dispose();
    _internalFocusNode.dispose();
    super.dispose();
  }

  void _onFocusInteractionChanged() {
    _syncFocusRing();
  }

  void _onFocusChanged() {
    _syncFocusRing();
    widget.onFocusChange?.call(_focusNode.hasFocus);
  }

  void _onStatesChanged() {
    if (!mounted) {
      return;
    }
    final Set<WidgetState> states = _statesController.value;
    _isHoveredNotifier.value = states.contains(WidgetState.hovered);
    _isPressedNotifier.value = states.contains(WidgetState.pressed);
    _syncFocusRing();
  }

  void _onHighlightModeChanged(FocusHighlightMode mode) {
    _syncFocusRing();
  }

  void _syncFocusRing() {
    if (!mounted) {
      return;
    }
    final show = M3EFocusRing.shouldShow(_focusNode, context);
    if (_showFocusRingNotifier.value != show) {
      _showFocusRingNotifier.value = show;
    }
  }

  void _setPointerDown(bool down) {
    if (_isPointerDownNotifier.value == down) {
      return;
    }
    _isPointerDownNotifier.value = down;
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(builder: _buildContent);
  }
}
