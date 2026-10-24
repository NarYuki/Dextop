import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

import '../../foundations/foundations.dart';
import '../tooltips/m3e_tooltips.dart';
import 'components/m3e_dropdown_chips.dart';
import 'components/m3e_dropdown_menu_item.dart';
import 'controllers/m3e_dropdown_controller.dart';
import 'enums/m3e_dropdown_expand_direction.dart';
import 'models/m3e_dropdown_item.dart';
import 'styles/m3e_dropdown_chip_style.dart';
import 'styles/m3e_dropdown_field_style.dart';
import 'styles/m3e_dropdown_item_style.dart';
import 'styles/m3e_dropdown_menu_theme.dart';
import 'styles/m3e_dropdown_panel_style.dart';
import 'styles/m3e_dropdown_search_style.dart';
import 'utils/m3e_dropdown_spring_motion.dart';

export 'controllers/m3e_dropdown_controller.dart';
export 'enums/m3e_dropdown_expand_direction.dart';
export 'models/m3e_dropdown_item.dart';
export 'styles/m3e_dropdown_chip_style.dart';
export 'styles/m3e_dropdown_field_style.dart';
export 'styles/m3e_dropdown_item_style.dart';
export 'styles/m3e_dropdown_menu_theme.dart';
export 'styles/m3e_dropdown_panel_style.dart';
export 'styles/m3e_dropdown_search_style.dart';

part 'components/m3e_dropdown_menu_lifecycle.dart';
part 'components/m3e_dropdown_menu_actions.dart';
part 'components/m3e_dropdown_menu_field.dart';
part 'components/m3e_dropdown_menu_chip_build.dart';
part 'components/m3e_dropdown_menu_panel.dart';

/// Signature for a function that asynchronously returns dropdown items.
typedef M3EDropdownFutureRequest<T> =
    Future<List<M3EDropdownItem<T>>> Function();

/// Signature for a custom item builder inside the dropdown list.
typedef M3EDropdownItemBuilder<T> = Widget Function(
  M3EDropdownItem<T> item, {
  required bool selected,
  required VoidCallback onTap,
});

/// A Material 3 Expressive dropdown menu.
///
/// Features M3E‑style outer / inner radius, spring animation powered by the
/// `motor` package, optional multi‑select, search, chip display, async data
/// loading, and customisable trailing icon with animated rotation.
///
/// ## Basic usage
///
/// ```dart
/// M3EDropdownMenu<String>(
///   items: [
///     M3EDropdownItem(label: 'Apple', value: 'apple'),
///     M3EDropdownItem(label: 'Banana', value: 'banana'),
///   ],
///   onSelectionChanged: (items) => print(items),
/// )
/// ```
///
/// ## Async data loading
///
/// ```dart
/// M3EDropdownMenu<int>.future(
///   future: () async {
///     final data = await fetchItems();
///     return data.map((e) => M3EDropdownItem(label: e.name, value: e.id)).toList();
///   },
/// )
/// ```
class M3EDropdownMenu<T> extends StatefulWidget {
  // ── Data ──

  /// The list of items. Ignored when using the [M3EDropdownMenu.future]
  /// constructor (items will be loaded asynchronously).
  final List<M3EDropdownItem<T>> items;

  /// Async item provider. When non-null the dropdown starts in a loading
  /// state and populates items once the future completes.
  final M3EDropdownFutureRequest<T>? future;

  // ── Behaviour ──

  /// When `true`, only a single item can be selected at a time.
  ///
  /// Defaults to `false` (multi-select).
  final bool singleSelect;

  /// Whether to show a search field inside the dropdown.
  final bool searchEnabled;

  /// Whether to show selected items as chips inside the field.
  /// Defaults to `true` when using the default [selectedItemBuilder] to allow the builder to take advantage of chip animations, but can be set to `false` to disable animations while keeping custom rendering.
  final bool showChipAnimation;

  /// Maximum number of selectable items. `0` means unlimited.
  final int maxSelections;

  /// Maximum number of selections when multi-select applies.
  ///
  /// `null` (default) means unlimited. When non-null, must be greater than
  /// zero. Takes precedence over [maxSelections] when set.
  final int? limit;

  /// Called whenever the selection changes.
  final ValueChanged<List<M3EDropdownItem<T>>>? onSelectionChanged;

  /// Called when the search text changes.
  final ValueChanged<String>? onSearchChanged;

  /// Optional programmatic controller.
  final M3EDropdownController<T>? controller;

  /// Whether the dropdown is enabled.
  final bool enabled;

  // ── Shape ──

  /// Radius applied to the dropdown panel container and (when no
  /// [M3EDropdownFieldStyle.borderRadius] is set) the field.
  ///
  /// Defaults to `28.0`.
  final double containerRadius;

  // ── Styling ──

  /// Field style.
  final M3EDropdownFieldStyle fieldStyle;

  /// Dropdown panel style.
  final M3EDropdownPanelStyle dropdownStyle;

  /// Chip style (only used when [showChipAnimation] is true).
  final M3EDropdownChipStyle chipStyle;

  /// Search field style (only used when [searchEnabled] is true).
  final M3EDropdownSearchStyle searchStyle;

  /// Item style.
  final M3EDropdownItemStyle itemStyle;

  /// Optional builder for each dropdown item – overrides default rendering.
  final M3EDropdownItemBuilder<T>? itemBuilder;

  /// Optional builder for the empty state when no items match the filter.
  final WidgetBuilder? emptyBuilder;

  /// Optional builder for each selected item in the field.
  ///
  /// If provided, replaces the default chip rendering. When using this,
  /// [showChipAnimation] should be `true` for the builder to take animations from chips.
  /// Defaut is `true` to allow the builder to take advantage of chip animations, but can be set to `false` to disable animations while keeping custom rendering.
  final Widget Function(M3EDropdownItem<T> item)? selectedItemBuilder;

  /// An optional widget placed between dropdown items.
  ///
  /// When non-null, overrides `itemGap` inside [M3EDropdownItemStyle.itemGap] and is used as the separator in
  /// the dropdown item list.
  final Widget? itemSeparator;

  // ── Form ──

  /// Optional validator for form integration.
  ///
  /// Return a non-null string to indicate a validation error.
  final String? Function(List<M3EDropdownItem<T>>? selectedOptions)? validator;

  /// The autovalidate mode for the dropdown when used inside a [Form].
  final AutovalidateMode autovalidateMode;

  // ── Focus ──

  /// An optional [FocusNode] for the dropdown field.
  final FocusNode? focusNode;

  /// Whether to close the dropdown when the system back button is pressed.
  ///
  /// Note: This requires the app to use a [Router] (e.g. `MaterialApp.router`).
  final bool closeOnBackButton;

  // ── Animation ──

  /// The spring motion for the expand animation.
  ///
  /// When null, uses [M3EDropdownMenuTheme.openSpring].
  final M3ESpring? openMotion;

  /// The spring motion for the collapse animation.
  ///
  /// When null, uses [M3EDropdownMenuTheme.closeSpring].
  final M3ESpring? closeMotion;

  // ── Splash ──

  /// The [InteractiveInkFeatureFactory] used for tap feedback on the field
  /// and dropdown items.
  ///
  /// Defaults to [NoSplash.splashFactory] (no ripple). Pass
  /// [InkSplash.splashFactory] or [InkRipple.splashFactory] to restore
  /// material splash feedback.
  final InteractiveInkFeatureFactory? splashFactory;

  // ── Haptics ──

  /// Haptic feedback level on tap.
  final M3EHapticFeedback haptic;

  /// Creates an [M3EDropdownMenu] with a static list of items.
  const M3EDropdownMenu({
    super.key,
    required this.items,
    this.singleSelect = false,
    this.searchEnabled = false,
    this.showChipAnimation = true,
    this.maxSelections = 0,
    this.limit,
    this.onSelectionChanged,
    this.onSearchChanged,
    this.controller,
    this.enabled = true,
    this.containerRadius = 28.0,
    this.fieldStyle = const M3EDropdownFieldStyle(),
    this.dropdownStyle = const M3EDropdownPanelStyle(),
    this.chipStyle = const M3EDropdownChipStyle(),
    this.searchStyle = const M3EDropdownSearchStyle(),
    this.itemStyle = const M3EDropdownItemStyle(),
    this.itemBuilder,
    this.emptyBuilder,
    this.selectedItemBuilder,
    this.itemSeparator,
    this.validator,
    this.autovalidateMode = AutovalidateMode.disabled,
    this.focusNode,
    this.closeOnBackButton = false,
    this.openMotion,
    this.closeMotion,
    this.splashFactory = NoSplash.splashFactory,
    this.haptic = M3EHapticFeedback.none,
  }) : future = null,
       assert(
         limit == null || limit > 0,
         'limit must be null or greater than 0',
       );

  /// Creates an [M3EDropdownMenu] that loads items asynchronously.
  const M3EDropdownMenu.future({
    super.key,
    required this.future,
    this.singleSelect = false,
    this.searchEnabled = false,
    this.showChipAnimation = false,
    this.maxSelections = 0,
    this.limit,
    this.onSelectionChanged,
    this.onSearchChanged,
    this.controller,
    this.enabled = true,
    this.containerRadius = 28.0,
    this.fieldStyle = const M3EDropdownFieldStyle(),
    this.dropdownStyle = const M3EDropdownPanelStyle(),
    this.chipStyle = const M3EDropdownChipStyle(),
    this.searchStyle = const M3EDropdownSearchStyle(),
    this.itemStyle = const M3EDropdownItemStyle(),
    this.itemBuilder,
    this.emptyBuilder,
    this.selectedItemBuilder,
    this.itemSeparator,
    this.validator,
    this.autovalidateMode = AutovalidateMode.disabled,
    this.focusNode,
    this.closeOnBackButton = false,
    this.openMotion,
    this.closeMotion,
    this.splashFactory = NoSplash.splashFactory,
    this.haptic = M3EHapticFeedback.none,
  }) : items = const [],
       assert(
         limit == null || limit > 0,
         'limit must be null or greater than 0',
       );

  @override
  State<M3EDropdownMenu<T>> createState() => _M3EDropdownMenuState<T>();
}

class _M3EDropdownMenuState<T> extends State<M3EDropdownMenu<T>>
    with TickerProviderStateMixin {
  late M3EDropdownController<T> _controller;
  bool _ownController = false;

  bool _isHoveredField = false;
  bool _isPressedField = false;
  bool _lastIsOpen = false;

  final Map<Object, SingleMotionController> _chipSlideControllers = {};
  final Map<Object, GlobalKey<M3ESpringChipState>> _chipKeys = {};
  final Set<Object> _removingChips = {};
  List<Object> _previousChipOrder = [];
  bool _isMoreChipsRemoving = false;
  int _moreChipsLastCount = 0;

  final LayerLink _layerLink = LayerLink();
  final OverlayPortalController _portalController = OverlayPortalController();
  M3EOverlayHistory? _overlayHistory;
  final GlobalKey<FormFieldState<List<M3EDropdownItem<T>>?>> _formFieldKey =
      GlobalKey();
  final GlobalKey<M3EMoreChipsIndicatorState> _moreKey = GlobalKey();

  late FocusNode _focusNode;
  final FocusScopeNode _focusTrapScope = FocusScopeNode(
    debugLabel: 'M3EDropdownMenu.trap',
  );
  bool? _openingShowOnTop;

  final TextEditingController _searchTextController = TextEditingController();
  Timer? _searchDebounce;

  bool _isLoading = false;
  String? _errorMessage;

  late final SingleMotionController _expandCtrl;
  late final SingleMotionController _arrowCtrl;
  late final ValueNotifier<bool> _loadingNotifier;

  /// Whether the field should paint the keyboard focus ring.
  late final ValueNotifier<bool> _focusRingNotifier;
  late final Listenable _listenable;

  M3ESpring get _resolvedOpenMotion =>
      widget.openMotion ?? M3ETheme.of(context).dropdownMenuTheme.openSpring;

  M3ESpring get _resolvedCloseMotion =>
      widget.closeMotion ?? M3ETheme.of(context).dropdownMenuTheme.closeSpring;

  @override
  void initState() {
    super.initState();
    _initControllers();
    _initFocusAndLoading();
    if (widget.future != null) {
      unawaited(_loadAsync());
    }
  }

  @override
  void didUpdateWidget(covariant M3EDropdownMenu<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncItemsFromWidget(oldWidget);
    _syncControllerFromWidget(oldWidget);
    _syncFocusNodeFromWidget(oldWidget);
    _syncMotionFromWidget(oldWidget);
  }

  @override
  void dispose() {
    _releaseOverlayHistory();
    _expandCtrl
      ..removeListener(_onExpandAnimationTick)
      ..dispose();
    _arrowCtrl.dispose();
    _searchDebounce?.cancel();
    _searchTextController.dispose();
    _loadingNotifier.dispose();
    _controller.removeListener(_onControllerChanged);
    if (_ownController) {
      _controller.dispose();
    }
    FocusManager.instance.removeHighlightModeListener(
      _onFocusHighlightModeChanged,
    );
    M3EFocusInteraction.instance.removeListener(_syncFieldFocusRing);
    _focusNode.removeListener(_syncFieldFocusRing);
    _focusRingNotifier.dispose();
    _focusTrapScope.dispose();
    if (widget.focusNode == null) {
      _focusNode.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(
      builder: (context) => FormField<List<M3EDropdownItem<T>>?>(
        key: _formFieldKey,
        validator: widget.validator ?? (_) => null,
        autovalidateMode: widget.autovalidateMode,
        initialValue: _controller.selectedItems,
        enabled: widget.enabled,
        builder: _buildFormField,
      ),
    );
  }
}
