import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../buttons/components/m3e_base_button_state.dart';
import '../buttons/components/m3e_radius_and_padding_motion.dart';
import '../buttons/enums/m3e_button_enums.dart';
import '../buttons/res/m3e_button_constants.dart';
import '../buttons/styles/m3e_button_motion.dart';
import '../buttons/styles/m3e_button_theme.dart';
import '../buttons/utils/m3e_button_gradient_layer.dart';
import '../menus/m3e_menus.dart';
import '../tooltips/m3e_tooltips.dart';
import 'components/m3e_split_button_bottom_sheet.dart';
import 'enums/m3e_split_button_menu_style.dart';
import 'enums/m3e_split_button_selection_mode.dart';
import 'enums/m3e_split_button_trailing_alignment.dart';
import 'models/m3e_split_button_item.dart';
import 'styles/m3e_split_button_bottom_sheet_decoration.dart';
import 'styles/m3e_split_button_decoration.dart';
import 'styles/m3e_split_button_popup_decoration.dart';
import 'styles/m3e_split_button_theme.dart';

export '../menus/models/m3e_menu_node.dart';
export 'enums/m3e_split_button_menu_style.dart';
export 'enums/m3e_split_button_selection_mode.dart';
export 'enums/m3e_split_button_trailing_alignment.dart';
export 'models/m3e_split_button_item.dart';
export 'styles/m3e_split_button_bottom_sheet_decoration.dart';
export 'styles/m3e_split_button_checkbox_style.dart';
export 'styles/m3e_split_button_decoration.dart';
export 'styles/m3e_split_button_popup_decoration.dart';
export 'styles/m3e_split_button_theme.dart';

part 'components/m3e_split_button_widgets.dart';
part 'components/m3e_split_button_style.dart';
part 'components/m3e_split_button_content.dart';
part 'components/m3e_split_button_segments.dart';
part 'components/m3e_split_button_menu.dart';
part 'components/m3e_split_button_gradient.dart';

const bool _kDefaultEnableFeedback = true;

/// Material 3 Expressive Split Button.
class M3ESplitButton<T> extends StatefulWidget {
  /// const.
  const M3ESplitButton({
    super.key,
    required this.items,
    this.onSelected,
    this.onPressed,
    this.label,
    this.leadingIcon,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.style = M3EButtonStyle.filled,
    this.trailingAlignment = M3ESplitButtonTrailingAlignment.opticalCenter,
    this.leadingTooltip,
    this.trailingTooltip,
    this.enabled = true,
    this.menuBuilder,
    this.m3eMenuBuilder,
    this.decoration,
    this.mouseCursor,
    this.statesController,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.selectedValue,
    this.onMultiSelected,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  }) : assert(
         items != null || menuBuilder != null || m3eMenuBuilder != null,
         'Provide either `items`, `menuBuilder`, or `m3eMenuBuilder`.',
       ),
       assert(
         style != M3EButtonStyle.text,
         'M3ESplitButton does not support M3EButtonStyle.text.',
       ),
       assert(
         !enabled ||
             onPressed != null ||
             onSelected != null ||
             menuBuilder != null ||
             m3eMenuBuilder != null,
         'Provide either onPressed, onSelected, menuBuilder, or m3eMenuBuilder when the split button is enabled.',
       );

  /// A filled split button (highest emphasis).
  const M3ESplitButton.filled({
    super.key,
    required this.items,
    this.onSelected,
    this.onPressed,
    this.label,
    this.leadingIcon,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.trailingAlignment = M3ESplitButtonTrailingAlignment.opticalCenter,
    this.leadingTooltip,
    this.trailingTooltip,
    this.enabled = true,
    this.menuBuilder,
    this.m3eMenuBuilder,
    this.decoration,
    this.mouseCursor,
    this.statesController,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.selectedValue,
    this.onMultiSelected,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  }) : style = M3EButtonStyle.filled,
       assert(
         items != null || menuBuilder != null || m3eMenuBuilder != null,
         'Provide either `items`, `menuBuilder`, or `m3eMenuBuilder`.',
       );

  /// A tonal split button (medium emphasis).
  const M3ESplitButton.tonal({
    super.key,
    required this.items,
    this.onSelected,
    this.onPressed,
    this.label,
    this.leadingIcon,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.trailingAlignment = M3ESplitButtonTrailingAlignment.opticalCenter,
    this.leadingTooltip,
    this.trailingTooltip,
    this.enabled = true,
    this.menuBuilder,
    this.m3eMenuBuilder,
    this.decoration,
    this.mouseCursor,
    this.statesController,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.selectedValue,
    this.onMultiSelected,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  }) : style = M3EButtonStyle.tonal,
       assert(
         items != null || menuBuilder != null || m3eMenuBuilder != null,
         'Provide either `items`, `menuBuilder`, or `m3eMenuBuilder`.',
       );

  /// An elevated split button (medium emphasis with a shadow).
  const M3ESplitButton.elevated({
    super.key,
    required this.items,
    this.onSelected,
    this.onPressed,
    this.label,
    this.leadingIcon,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.trailingAlignment = M3ESplitButtonTrailingAlignment.opticalCenter,
    this.leadingTooltip,
    this.trailingTooltip,
    this.enabled = true,
    this.menuBuilder,
    this.m3eMenuBuilder,
    this.decoration,
    this.mouseCursor,
    this.statesController,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.selectedValue,
    this.onMultiSelected,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  }) : style = M3EButtonStyle.elevated,
       assert(
         items != null || menuBuilder != null || m3eMenuBuilder != null,
         'Provide either `items`, `menuBuilder`, or `m3eMenuBuilder`.',
       );

  /// An outlined split button (medium emphasis with a border).
  const M3ESplitButton.outlined({
    super.key,
    required this.items,
    this.onSelected,
    this.onPressed,
    this.label,
    this.leadingIcon,
    this.size = M3EButtonSize.sm,
    this.shape = M3EButtonShape.round,
    this.trailingAlignment = M3ESplitButtonTrailingAlignment.opticalCenter,
    this.leadingTooltip,
    this.trailingTooltip,
    this.enabled = true,
    this.menuBuilder,
    this.m3eMenuBuilder,
    this.decoration,
    this.mouseCursor,
    this.statesController,
    this.focusNode,
    this.autofocus = false,
    this.onFocusChange,
    this.selectedValue,
    this.onMultiSelected,
    this.onLongPress,
    this.onHover,
    this.enableFeedback = _kDefaultEnableFeedback,
    this.splashFactory,
  }) : style = M3EButtonStyle.outlined,
       assert(
         items != null || menuBuilder != null || m3eMenuBuilder != null,
         'Provide either `items`, `menuBuilder`, or `m3eMenuBuilder`.',
       );

  /// final.

  final List<M3ESplitButtonItem<T>>? items;

  /// final.
  final ValueChanged<T>? onSelected;

  /// final.
  final VoidCallback? onPressed;

  /// final.
  final String? label;

  /// final.
  final IconData? leadingIcon;

  /// final.
  final M3EButtonSize size;

  /// final.
  final M3EButtonShape shape;

  /// final.
  final M3EButtonStyle style;

  /// final.
  final M3ESplitButtonTrailingAlignment trailingAlignment;

  /// final.
  final String? leadingTooltip;

  /// final.
  final String? trailingTooltip;

  /// final.
  final bool enabled;

  /// Flutter [PopupMenuEntry] builder. Uses Material [showMenu] (not M3E).
  ///
  /// When set, takes priority over [m3eMenuBuilder] and [items].
  final List<PopupMenuEntry<T>> Function(BuildContext)? menuBuilder;

  /// Rich M3E menu tree. Respects [M3ESplitButtonDecoration.menuStyle] for
  /// popup / native paths. Ignored when [menuBuilder] is set.
  ///
  /// Bottom-sheet [M3ESplitButtonMenuStyle.bottomSheet] is not supported for
  /// custom trees — falls back to the spring popup.
  final List<M3EMenuNode> Function(BuildContext)? m3eMenuBuilder;

  /// final.
  final M3ESplitButtonDecoration? decoration;

  /// final.
  final MouseCursor? mouseCursor;

  /// final.
  final WidgetStatesController? statesController;

  /// final.
  final FocusNode? focusNode;

  /// final.
  final bool autofocus;

  /// final.
  final ValueChanged<bool>? onFocusChange;

  /// final.
  final T? selectedValue;

  /// final.
  final void Function(Set<T>)? onMultiSelected;

  /// final.
  final VoidCallback? onLongPress;

  /// final.
  final ValueChanged<bool>? onHover;

  /// final.
  final bool enableFeedback;

  /// final.
  final InteractiveInkFeatureFactory? splashFactory;

  /// WidgetStateProperty.

  WidgetStateProperty<Color?>? get decorationBackgroundColor =>
      decoration?.backgroundColor;

  /// WidgetStateProperty.
  WidgetStateProperty<Color?>? get decorationForegroundColor =>
      decoration?.foregroundColor;

  /// Color.
  Color? get decorationTrailingBackgroundColor =>
      decoration?.trailingBackgroundColor;

  /// Color.
  Color? get decorationTrailingForegroundColor =>
      decoration?.trailingForegroundColor;

  /// M3EButtonSize.
  M3EButtonSize? get decorationLeadingCustomSize =>
      decoration?.leadingCustomSize;

  /// M3EButtonSize.
  M3EButtonSize? get decorationTrailingCustomSize =>
      decoration?.trailingCustomSize;

  /// M3EButtonMotion.
  M3EButtonMotion? get decorationMotion => decoration?.motion;

  /// double.
  double? get decorationGap => decoration?.gap;

  /// Color.
  Color? get decorationMenuBackgroundColor => decoration?.menuBackgroundColor;

  /// Color.
  Color? get decorationMenuForegroundColor => decoration?.menuForegroundColor;

  /// WidgetStateProperty.
  WidgetStateProperty<BorderSide?>? get decorationBorderSide =>
      decoration?.side;

  /// M3EHapticFeedback.
  M3EHapticFeedback get decorationHaptic =>
      decoration?.haptic ?? M3EHapticFeedback.none;

  /// Duration. Zero keeps the segment clip on the spring, not Material's tween.
  Duration get decorationAnimationDuration =>
      decoration?.animationDuration ?? Duration.zero;

  /// double.
  double? get decorationBorderRadius => decoration?.borderRadius;

  /// WidgetStateProperty.
  WidgetStateProperty<Color?>? get decorationOverlayColor =>
      decoration?.overlayColor;

  /// WidgetStateProperty.
  WidgetStateProperty<Color?>? get decorationSurfaceTintColor =>
      decoration?.surfaceTintColor;

  @override
  State<M3ESplitButton<T>> createState() => _M3ESplitButtonState<T>();
}

class _M3ESplitButtonState<T> extends State<M3ESplitButton<T>>
    with M3EBaseButtonState<M3ESplitButton<T>> {
  bool _menuOpen = false;
  bool _leadingPressed = false;
  bool _trailingPressed = false;
  bool _isTrailingHovered = false;
  bool _isTrailingFocused = false;
  late FocusNode _trailingFocusNode;

  void _closeMenu() {
    if (!mounted) {
      return;
    }
    isPointerDownNotifier.value = false;
    setState(() {
      _menuOpen = false;
      _trailingPressed = false;
    });
  }

  Set<T>? _selectedValues;
  final GlobalKey _trailingKey = GlobalKey();
  final GlobalKey _splitGradientHostKey = GlobalKey();

  @override
  M3EButtonSize get buttonSize => widget.size;

  @override
  WidgetStatesController? get externalStatesController =>
      widget.statesController;

  @override
  FocusNode? get externalFocusNode => widget.focusNode;

  @override
  M3EButtonMotion? get effectiveMotion => widget.decorationMotion;

  M3EButtonTheme get _buttonTheme => M3ETheme.of(context).buttonTheme;

  M3ESplitButtonTheme get _splitTheme => M3ETheme.of(context).splitButtonTheme;

  M3EColorScheme get _scheme => M3ETheme.of(context).colorScheme;

  @override
  void initState() {
    super.initState();
    initBaseButtonState();
    _trailingFocusNode = FocusNode(debugLabel: 'M3ESplitButton.trailing');
    _trailingFocusNode.addListener(_onTrailingFocusChanged);
    M3EFocusInteraction.instance.addListener(_onTrailingFocusChanged);
  }

  void _onTrailingFocusChanged() {
    if (!mounted) {
      return;
    }
    final focused = M3EFocusRing.shouldShow(_trailingFocusNode, context);
    if (_isTrailingFocused != focused) {
      setState(() => _isTrailingFocused = focused);
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    updateSpringMotion();
  }

  @override
  void didUpdateWidget(covariant M3ESplitButton<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    handleStatesControllerUpdate(
      oldWidget.statesController,
      widget.statesController,
    );
    handleFocusNodeUpdate(oldWidget.focusNode, widget.focusNode);

    if (widget.decoration?.motion != oldWidget.decoration?.motion) {
      updateSpringMotion();
    }
  }

  @override
  void dispose() {
    M3EFocusInteraction.instance.removeListener(_onTrailingFocusChanged);
    _trailingFocusNode
      ..removeListener(_onTrailingFocusChanged)
      ..dispose();
    disposeBaseButtonState();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(
      builder: (context) => buildAnimatedContent(
        builder:
            (
              context, {
              required isPressed,
              required isHovered,
              required isFocused,
            }) {
              return _buildContent(context, isPressed, isHovered, isFocused);
            },
      ),
    );
  }
}
