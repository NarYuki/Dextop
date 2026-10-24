import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/scheduler.dart';
import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

import '../../foundations/foundations.dart';
import '../buttons/m3e_buttons.dart';
import '../menus/m3e_menus.dart';
import 'components/m3e_button_group_item_scope.dart';
import 'components/m3e_button_group_provider.dart';
import 'components/m3e_button_group_scope.dart';
import 'controllers/m3e_button_group_overflow_controller.dart';
import 'enums/m3e_button_group_enums.dart';
import 'models/m3e_button_group_action.dart';
import 'models/m3e_button_group_overflow_paging_window.dart';
import 'styles/m3e_button_group_theme.dart';

export '../buttons/components/m3e_no_overflow_strategy.dart';
export '../buttons/components/m3e_overflow_strategy.dart';
export '../buttons/components/m3e_scroll_overflow_strategy.dart';
export 'components/m3e_button_group_item_scope.dart';
export 'components/m3e_button_group_provider.dart';
export 'components/m3e_button_group_scope.dart';
export 'controllers/m3e_button_group_overflow_controller.dart';
export 'enums/m3e_button_group_enums.dart';
export 'models/m3e_button_group_action.dart';
export 'models/m3e_button_group_overflow_paging_window.dart';
export 'styles/m3e_button_group_theme.dart';

part 'components/m3e_button_group_align.dart';
part 'components/m3e_button_group_parent_data.dart';
part 'components/m3e_button_group_collaborators.dart';
part 'components/m3e_button_group_overflow_presenter.dart';
part 'components/m3e_button_group_render.dart';
part 'components/m3e_button_group_measurement.dart';
part 'components/m3e_button_group_action_radii.dart';
part 'components/m3e_button_group_layout.dart';
part 'components/m3e_button_group_overflow_button.dart';
part 'components/m3e_button_group_scroll.dart';
part 'components/m3e_button_group_build.dart';

/// A row (or column) of selectable [M3EButton]s with standard or connected
/// layout, optional neighbour squish, and overflow handling.
///
/// All actions render as [M3EButton]. Use [M3EButtonGroupAction.minWidth] for
/// icon-only resting widths. Standard groups use size-token between-space;
/// connected groups use a 2dp gap and span their surface.
///
/// Selection:
/// - Single-select (default): [selectedIndex] + [onSelectedIndexChanged]
/// - Multi-select: set [multiSelect] to true and use [selectedIndices] +
///   [onSelectedIndicesChanged]
/// - [selectionRequired] prevents clearing the last selected action
class M3EButtonGroup extends StatefulWidget {
  /// Creates a button group.
  const M3EButtonGroup({
    super.key,
    required this.actions,
    this.type = M3EButtonGroupType.standard,
    this.shape = M3EButtonShape.round,
    this.size = M3EButtonSize.sm,
    this.style = M3EButtonStyle.filled,
    this.density = M3EButtonGroupDensity.regular,
    this.spacing,
    this.direction = Axis.horizontal,
    this.selectedIndex,
    this.selectedIndices,
    this.onSelectedIndexChanged,
    this.onSelectedIndicesChanged,
    this.multiSelect = false,
    this.selectionRequired = false,
    this.neighborSquish = true,
    this.expandedRatio = 0.15,
    this.haptic = M3EHapticFeedback.none,
    this.enableFeedback = true,
    this.decoration,
    this.semanticLabel,
    this.clipBehavior = Clip.none,
    this.overflow = M3EButtonGroupOverflow.scroll,
    this.overflowIcon,
    this.overflowPopupDecoration = const M3EOverflowPopupDecoration(),
    this.overflowBottomSheetDecoration =
        const M3EOverflowBottomSheetDecoration(),
    this.overflowMenuStyle = M3EButtonGroupOverflowMenuStyle.popup,
    this.overflowStrategy,
  });

  /// Actions rendered as group buttons.
  final List<M3EButtonGroupAction> actions;

  /// Standard (gapped) or connected (joined) layout.
  final M3EButtonGroupType type;

  /// Default corner strategy for actions (round or square).
  final M3EButtonShape shape;

  /// Size token applied uniformly to every action.
  final M3EButtonSize size;

  /// Visual style applied uniformly to every action.
  final M3EButtonStyle style;

  /// Density level (0…−3) that shrinks container height.
  final M3EButtonGroupDensity density;

  /// Optional between-space override; defaults to size / connection tokens.
  final double? spacing;

  /// Main layout axis.
  final Axis direction;

  /// Controlled single-select index; pair with [onSelectedIndexChanged].
  final int? selectedIndex;

  /// Controlled multi-select indices; pair with [onSelectedIndicesChanged].
  final Set<int>? selectedIndices;

  /// Called when single-select selection changes.
  final ValueChanged<int?>? onSelectedIndexChanged;

  /// Called when multi-select selection changes.
  final ValueChanged<Set<int>>? onSelectedIndicesChanged;

  /// When true, taps toggle membership in [selectedIndices] (multi-select).
  ///
  /// When false (default), selection is exclusive via [selectedIndex].
  final bool multiSelect;

  /// When true, the last selected action cannot be cleared.
  final bool selectionRequired;

  /// Whether pressing an action squishes neighbours (standard horizontal only).
  final bool neighborSquish;

  /// Pressed width growth ratio for neighbour squish (default 0.15).
  final double expandedRatio;

  /// Haptic feedback policy for presses.
  final M3EHapticFeedback haptic;

  /// Whether actions play platform feedback by default.
  final bool enableFeedback;

  /// Group-level decoration merged under each action's decoration.
  final M3EButtonDecoration? decoration;

  /// Semantics label for the group container (optional).
  final String? semanticLabel;

  /// Clip behavior for the group bounds.
  final Clip clipBehavior;

  /// Built-in overflow strategy when [overflowStrategy] is null.
  final M3EButtonGroupOverflow overflow;

  /// Icon for the overflow menu / paging trigger.
  final Widget? overflowIcon;

  /// Decoration for the overflow popup menu.
  final M3EOverflowPopupDecoration overflowPopupDecoration;

  /// Decoration for the overflow bottom sheet.
  final M3EOverflowBottomSheetDecoration overflowBottomSheetDecoration;

  /// Popup vs bottom sheet for [M3EButtonGroupOverflow.menu].
  final M3EButtonGroupOverflowMenuStyle overflowMenuStyle;

  /// Custom overflow strategy; overrides [overflow] when non-null.
  final M3EOverflowStrategy? overflowStrategy;

  bool get _connected => type == M3EButtonGroupType.connected;

  @override
  State<M3EButtonGroup> createState() => _M3EButtonGroupState();
}

class _M3EButtonGroupState extends State<M3EButtonGroup>
    with _ButtonGroupOverflowPresenterMixin {
  late List<WidgetStatesController> _controllers;
  late List<FocusNode?> _focusNodes;
  late int _layoutSignature;
  late int _focusNodeSignature;
  late final M3EButtonGroupOverflowController _overflowController;
  late final ScrollController _scrollOverflowController;
  late final _ButtonGroupPressCoordinator _pressCoordinator;
  late final _ButtonGroupMeasurementOrchestrator _measurement;
  int? _lastOverflowSelectionIndex;

  final ValueNotifier<int?> _focusedIndexNotifier = ValueNotifier<int?>(null);

  bool _isRtl = false;

  int get _measurementGeneration => _measurement.generation;
  set _measurementGeneration(int value) => _measurement.generation = value;

  List<GlobalKey> get _unselectedKeys => _measurement.unselectedKeys;
  List<GlobalKey> get _selectedKeys => _measurement.selectedKeys;

  List<double?> get _measuredUnselectedWidths =>
      _measurement.measuredUnselectedWidths;
  List<double?> get _measuredSelectedWidths =>
      _measurement.measuredSelectedWidths;

  bool get _hasAnyLabel => _measurement.hasAnyLabel;
  set _hasAnyLabel(bool value) => _measurement.hasAnyLabel = value;

  double _iconOnlyNaturalSizeCache = 40;

  bool get _supportsAnimatedSquish =>
      widget.direction == Axis.horizontal &&
      !widget._connected &&
      widget.neighborSquish;

  /// Multi-select when [M3EButtonGroup.multiSelect] is set or a multi-select
  /// callback is used.
  bool get _isMultiSelect =>
      widget.multiSelect || widget.onSelectedIndicesChanged != null;

  late List<M3EButtonDecoration> _cachedDecorations;
  bool _stateDisposed = false;

  @override
  void initState() {
    super.initState();
    _assertControlledSelection();
    _bootstrapState();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _updateDecorations();
    _updateIconOnlyNaturalSizeCache();
    _scheduleMeasurementIfNeeded();
  }

  @override
  void didUpdateWidget(covariant M3EButtonGroup old) {
    super.didUpdateWidget(old);
    _assertControlledSelection();
    _applyWidgetUpdate(old);
  }

  @override
  void dispose() {
    // Invalidate pending measurement / press frame callbacks before tearing
    // down notifiers (State.mounted stays true until dispose returns).
    _stateDisposed = true;
    _measurementGeneration++;
    _overflowController.stableAllOverflowMeasured.removeListener(
      _handleOverflowChange,
    );
    _pressCoordinator.dispose();
    _disposeControllers();
    _disposeFocusNodes();
    _disposeMeasurerControllers();
    _overflowController.dispose();
    _scrollOverflowController.dispose();
    _focusedIndexNotifier.dispose();
    super.dispose();
  }

  @override
  void debugFillProperties(DiagnosticPropertiesBuilder properties) {
    super.debugFillProperties(properties);
    properties
      ..add(EnumProperty<M3EButtonGroupType>('type', widget.type))
      ..add(EnumProperty<M3EButtonShape>('shape', widget.shape))
      ..add(DiagnosticsProperty<M3EButtonSize>('size', widget.size))
      ..add(IntProperty('actionCount', widget.actions.length))
      ..add(
        FlagProperty(
          'multiSelect',
          value: widget.multiSelect,
          ifTrue: 'multi-select',
        ),
      )
      ..add(
        FlagProperty(
          'selectionRequired',
          value: widget.selectionRequired,
          ifTrue: 'selection required',
        ),
      )
      ..add(EnumProperty<M3EButtonGroupOverflow>('overflow', widget.overflow))
      ..add(
        FlagProperty(
          'neighborSquish',
          value: widget.neighborSquish,
          ifTrue: 'squish',
        ),
      )
      ..add(FlagProperty('hasLabels', value: _hasAnyLabel, ifTrue: 'labeled'));
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(builder: _buildGroup);
  }

  @override
  void _handleOverflowActionSelection(int index) {
    _applyOverflowActionSelection(index);
  }

  @override
  bool _isActionSelected(int index) {
    return _resolveActionSelected(index);
  }
}
