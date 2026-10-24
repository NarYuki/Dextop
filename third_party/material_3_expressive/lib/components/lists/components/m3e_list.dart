part of '../m3e_lists.dart';

enum _M3EListLayout { column, scrollable, sliver }

/// One list for selection, variant, reorder, swipe, and expand.
///
/// List-level fields apply to every row. A row opts into swipe with
/// [M3EListItem.swipe] and into expand with [M3EListItem.expanded].
/// A sub-list expansion is its own nested [M3EList].
class M3EList extends StatefulWidget {
  /// Non-scrollable column.
  const M3EList({
    super.key,
    required this.itemCount,
    required this.itemBuilder,
    this.outerRadius = M3EListCardListTheme.defaultOuterRadius,
    this.innerRadius = M3EListCardListTheme.defaultInnerRadius,
    this.gap = M3EListCardListTheme.defaultGap,
    this.color,
    this.colorBuilder,
    this.borderRadiusBuilder,
    this.padding,
    this.margin,
    this.onTap,
    this.onLongPress,
    this.semanticLabelBuilder,
    this.mouseCursor,
    this.haptic = M3EHapticFeedback.none,
    this.variant,
    this.border,
    this.emptyBuilder,
    this.selection = false,
    this.reorder = false,
    this.selectionController,
    this.onSelectionChanged,
    this.onReorder,
    this.selectionState,
    this.reorderState,
    this.embedded = false,
    this.semanticsLabel,
    this.dismissController,
    this.dismissStyle = const M3EDismissibleListStyle(),
    this.allowMultipleExpanded,
    this.initiallyExpanded = const <int>{},
    this.expandStyle,
    this.expandMotion,
    this.collapseMotion,
    this.onExpansionChanged,
    this.expandController,
  }) : assert(
         !reorder || onReorder != null,
         'onReorder is required when reorder is true',
       ),
       _layout = _M3EListLayout.column,
       controller = null,
       physics = null,
       shrinkWrap = false,
       listPadding = null,
       clipBehavior = Clip.hardEdge;

  /// Scrollable list.
  const M3EList.scrollable({
    super.key,
    required this.itemCount,
    required this.itemBuilder,
    this.outerRadius = M3EListCardListTheme.defaultOuterRadius,
    this.innerRadius = M3EListCardListTheme.defaultInnerRadius,
    this.gap = M3EListCardListTheme.defaultGap,
    this.color,
    this.colorBuilder,
    this.borderRadiusBuilder,
    this.padding,
    this.margin,
    this.onTap,
    this.onLongPress,
    this.semanticLabelBuilder,
    this.mouseCursor,
    this.haptic = M3EHapticFeedback.none,
    this.variant,
    this.border,
    this.emptyBuilder,
    this.controller,
    this.physics,
    this.shrinkWrap = false,
    this.listPadding,
    this.clipBehavior = Clip.hardEdge,
    this.selection = false,
    this.reorder = false,
    this.selectionController,
    this.onSelectionChanged,
    this.onReorder,
    this.selectionState,
    this.reorderState,
    this.embedded = false,
    this.semanticsLabel,
    this.dismissController,
    this.dismissStyle = const M3EDismissibleListStyle(),
    this.allowMultipleExpanded,
    this.initiallyExpanded = const <int>{},
    this.expandStyle,
    this.expandMotion,
    this.collapseMotion,
    this.onExpansionChanged,
    this.expandController,
  }) : assert(
         !reorder || onReorder != null,
         'onReorder is required when reorder is true',
       ),
       _layout = _M3EListLayout.scrollable;

  /// Sliver list. Selection stays; reorder is not supported.
  const M3EList.sliver({
    super.key,
    required this.itemCount,
    required this.itemBuilder,
    this.outerRadius = M3EListCardListTheme.defaultOuterRadius,
    this.innerRadius = M3EListCardListTheme.defaultInnerRadius,
    this.gap = M3EListCardListTheme.defaultGap,
    this.color,
    this.colorBuilder,
    this.borderRadiusBuilder,
    this.padding,
    this.margin,
    this.onTap,
    this.onLongPress,
    this.semanticLabelBuilder,
    this.mouseCursor,
    this.haptic = M3EHapticFeedback.none,
    this.variant,
    this.border,
    this.emptyBuilder,
    this.selection = false,
    this.selectionController,
    this.onSelectionChanged,
    this.selectionState,
    this.embedded = false,
    this.semanticsLabel,
    this.dismissController,
    this.dismissStyle = const M3EDismissibleListStyle(),
    this.allowMultipleExpanded,
    this.initiallyExpanded = const <int>{},
    this.expandStyle,
    this.expandMotion,
    this.collapseMotion,
    this.onExpansionChanged,
    this.expandController,
  }) : _layout = _M3EListLayout.sliver,
       reorder = false,
       onReorder = null,
       reorderState = null,
       controller = null,
       physics = null,
       shrinkWrap = false,
       listPadding = null,
       clipBehavior = Clip.hardEdge;

  final _M3EListLayout _layout;

  /// Number of rows.
  final int itemCount;

  /// Builds the row at the given index. A list item can set swipe and expanded.
  final IndexedWidgetBuilder itemBuilder;

  /// Outer corner radius.
  final double outerRadius;

  /// Inner corner radius.
  final double innerRadius;

  /// Gap between rows.
  final double gap;

  /// Shared row fill. Null lets the variant or a parent nest choose.
  final Color? color;

  /// Per-index fill. Non-null wins over [color].
  final Color? Function(int index)? colorBuilder;

  /// Per-index radius. Non-null wins over position radii.
  final BorderRadius? Function(int index, M3ECardPosition position)?
  borderRadiusBuilder;

  /// Padding inside each non-expanding row.
  final EdgeInsetsGeometry? padding;

  /// Margin around the whole list.
  final EdgeInsetsGeometry? margin;

  /// Called when a non-expanding row is tapped.
  final void Function(int index)? onTap;

  /// Called when a non-expanding row is long-pressed.
  final void Function(int index)? onLongPress;

  /// Semantic label for a row.
  final String Function(int index)? semanticLabelBuilder;

  /// Cursor for a row.
  final MouseCursor? mouseCursor;

  /// Haptic on a non-expanding row tap.
  final M3EHapticFeedback haptic;

  /// Filled, outlined, or elevated. A nested list inherits the parent variant.
  final M3ECardVariant? variant;

  /// Outline override.
  final BorderSide? border;

  /// Shown when [itemCount] is 0.
  final Widget? emptyBuilder;

  /// Scroll controller for [M3EList.scrollable].
  final ScrollController? controller;

  /// Scroll physics for [M3EList.scrollable].
  final ScrollPhysics? physics;

  /// Sizes the scroll view to its children.
  final bool shrinkWrap;

  /// Padding around the scrollable list.
  final EdgeInsetsGeometry? listPadding;

  /// Clip for [M3EList.scrollable].
  final Clip clipBehavior;

  /// Enables selection.
  final bool selection;

  /// Enables long-press reorder. Requires [onReorder].
  final bool reorder;

  /// Optional selection controller. An ancestor scope wins.
  final M3ESelectionController? selectionController;

  /// Called when selection changes.
  final ValueChanged<Set<int>>? onSelectionChanged;

  /// Called after a reorder drop.
  final ReorderCallback? onReorder;

  /// Selection state override.
  final M3EListSelectionState? selectionState;

  /// Reorder state override.
  final M3EListReorderState? reorderState;

  /// Forces nested corner radii even when this list is not under a parent.
  final bool embedded;

  /// List-box description.
  final String? semanticsLabel;

  /// Reveals or dismisses a swiping row by index.
  final M3EDismissibleListController? dismissController;

  /// Shared swipe chrome (springs, action metrics, default background).
  final M3EDismissibleListStyle dismissStyle;

  /// When false, opening one row closes the others.
  final bool? allowMultipleExpanded;

  /// Rows that start expanded.
  final Set<int> initiallyExpanded;

  /// Expandable row decoration override.
  final M3EExpandableStyle? expandStyle;

  /// Spring used while expanding.
  final M3ESpring? expandMotion;

  /// Spring used while collapsing.
  final M3ESpring? collapseMotion;

  /// Called when a row expands or collapses.
  final void Function(int index, {required bool isExpanded})?
  onExpansionChanged;

  /// Opens a row container transform by index.
  final M3EExpandableListController? expandController;

  @override
  State<M3EList> createState() => _M3EListState();
}

class _M3EListState extends State<M3EList>
    with
        TickerProviderStateMixin,
        M3EDismissibleCardMixin,
        M3EDismissibleCardDragMixin,
        M3EDismissibleCardBuildMixin {
  final List<Widget> _built = <Widget>[];
  final List<M3EListItemSwipe?> _swipes = <M3EListItemSwipe?>[];
  final List<M3EExpandableExpanded?> _expanded = <M3EExpandableExpanded?>[];
  final List<Widget?> _transforms = <Widget?>[];

  late Set<int> _expandedIndices;
  final Map<int, BuildContext> _transformAnchors = <int, BuildContext>{};
  M3ECardContainerTransformHandle<void>? _transformHandle;
  int? _collapsedForReorder;
  bool _snapCollapse = false;
  bool _embeddedActive = false;
  int? _styleDataIndex;

  @override
  int get swipeItemCount => widget.itemCount;

  @override
  M3EDismissibleListStyle get style {
    final M3EDismissibleListStyle base = widget.dismissStyle;
    final int? index = _styleDataIndex;
    if (index == null) {
      return base;
    }
    final M3EListItemSwipe? swipe = _swipeAt(index);
    if (swipe == null ||
        (swipe.background == null && swipe.secondaryBackground == null)) {
      return base;
    }
    return base.copyWith(
      background: swipe.background,
      secondaryBackground: swipe.secondaryBackground,
    );
  }

  @override
  bool get embedded => _embeddedActive;

  @override
  bool get listReorderEnabled => widget.reorder;

  @override
  Future<bool> Function(int, DismissDirection)? get onDismissCallback =>
      _dismissAt;

  @override
  void Function(int)? get onTapCallback => widget.onTap;

  @override
  void Function(int)? get onLongPressCallback => widget.onLongPress;

  @override
  Color? Function(int index)? get colorBuilder => widget.colorBuilder;

  @override
  BorderRadius? Function(int index, M3ECardPosition position)?
  get borderRadiusBuilder => widget.borderRadiusBuilder;

  @override
  List<M3EListSwipeAction> Function(int index)? get leadingActionsBuilder =>
      (int index) => _swipeAt(index)?.leading ?? const <M3EListSwipeAction>[];

  @override
  List<M3EListSwipeAction> Function(int index)? get trailingActionsBuilder =>
      (int index) => _swipeAt(index)?.trailing ?? const <M3EListSwipeAction>[];

  @override
  M3EListSwipeMode get swipeMode =>
      _swipeAt(activeSwipeIndex ?? -1)?.mode ?? M3EListSwipeMode.both;

  @override
  M3EListSwipeEdge get dismissEdge =>
      _swipeAt(activeSwipeIndex ?? -1)?.edge ?? M3EListSwipeEdge.both;

  @override
  bool swipeItemPaintsSurface(int dataIndex) => _expandedAt(dataIndex) != null;

  @override
  Widget swipeItemBuilder(BuildContext context, int dataIndex) {
    return M3EListItemIndex(
      index: dataIndex,
      child: _childAt(context, dataIndex),
    );
  }

  @override
  void initState() {
    super.initState();
    _expandedIndices = Set<int>.from(widget.initiallyExpanded);
    initSlots();
    bindSwipeController(widget.dismissController);
    _bindExpandController(widget.expandController);
  }

  @override
  void didUpdateWidget(M3EList oldWidget) {
    super.didUpdateWidget(oldWidget);
    syncSlotsIfNeeded(oldWidget.itemCount);
    bindSwipeController(widget.dismissController);
    if (oldWidget.expandController != widget.expandController) {
      oldWidget.expandController?.detach();
      _bindExpandController(widget.expandController);
    }
  }

  @override
  void dispose() {
    widget.expandController?.detach();
    unbindSwipeController();
    disposeSlots();
    super.dispose();
  }

  Future<bool> _dismissAt(int index, DismissDirection direction) {
    final Future<bool> Function(DismissDirection direction)? dismiss = _swipeAt(
      index,
    )?.onDismiss;
    if (dismiss == null) {
      return Future<bool>.value(true);
    }
    return dismiss(direction);
  }

  M3EListItemSwipe? _swipeAt(int index) {
    if (index < 0 || index >= _swipes.length) {
      return null;
    }
    return _swipes[index];
  }

  M3EExpandableExpanded? _expandedAt(int index) {
    if (index < 0 || index >= _expanded.length) {
      return null;
    }
    return _expanded[index];
  }

  bool _enabledAt(int index) {
    if (index < 0 || index >= _built.length) {
      return true;
    }
    final Widget child = _built[index];
    return child is! M3EListItem || child.enabled;
  }

  @override
  Widget buildSlot(
    BuildContext context,
    int slotIndex, {
    List<int>? visible,
    bool suppressOwnGap = false,
  }) {
    final List<int> shown = visible ?? computeVisibleIndices();
    final int dataIndex = shown.indexOf(slotIndex);
    _styleDataIndex = dataIndex < 0 ? null : dataIndex;
    final Widget child = super.buildSlot(
      context,
      slotIndex,
      visible: shown,
      suppressOwnGap: suppressOwnGap,
    );
    _styleDataIndex = null;
    return child;
  }

  @override
  Widget build(BuildContext context) {
    assert(
      widget._layout != _M3EListLayout.sliver || !widget.reorder,
      'M3EList.sliver does not support reorder.',
    );
    return M3EComponentTheme(builder: _buildList);
  }
}

int _remapListIndex(int index, int from, int to) {
  if (index == from) {
    return to;
  }
  if (from < to && index > from && index <= to) {
    return index - 1;
  }
  if (from > to && index >= to && index < from) {
    return index + 1;
  }
  return index;
}
