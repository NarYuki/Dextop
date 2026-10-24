import 'dart:math' as math;

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

import '../../foundations/foundations.dart';
import '../badges/m3e_badges.dart';
import 'controllers/m3e_tabs_controller.dart';
import 'enums/m3e_tabs_alignment.dart';
import 'enums/m3e_tabs_variant.dart';
import 'models/m3e_tab.dart';
import 'styles/m3e_tab_theme.dart';

export 'controllers/m3e_tabs_controller.dart';
export 'enums/m3e_tabs_alignment.dart';
export 'enums/m3e_tabs_variant.dart';
export 'm3e_tabs_view.dart';
export 'models/m3e_tab.dart';
export 'styles/m3e_tab_theme.dart';

part 'components/m3e_tabs_indicator.dart';
part 'components/m3e_tabs_sizing.dart';
part 'components/m3e_tabs_build.dart';
part 'components/m3e_tabs_content.dart';

/// A Material 3 tab bar.
///
/// Primary tabs stack an optional icon above the label. Secondary tabs place
/// an optional icon before the label. The active indicator springs to the
/// selected tab. Arrow keys move focus. Space or Enter selects.
class M3ETabs extends StatefulWidget {
  /// A fixed bar. Set [scrollable] to force or forbid horizontal scrolling.
  const M3ETabs({
    required this.tabs,
    required this.selectedIndex,
    required this.onTabSelected,
    this.variant = M3ETabsVariant.primary,
    this.scrollable,
    this.alignment = M3ETabsAlignment.fill,
    this.controller,
    super.key,
  }) : assert(tabs.length >= 2, 'A tab bar needs 2+ tabs.'),
       floating = false,
       _sliver = false;

  /// A bar inside a [CustomScrollView].
  ///
  /// When [floating] is true the bar scrolls away and returns on an upward
  /// scroll. When false it stays pinned at the top of the scroll view.
  const M3ETabs.sliver({
    required this.tabs,
    required this.selectedIndex,
    required this.onTabSelected,
    this.variant = M3ETabsVariant.primary,
    this.scrollable,
    this.alignment = M3ETabsAlignment.fill,
    this.controller,
    this.floating = true,
    super.key,
  }) : assert(tabs.length >= 2, 'A tab bar needs 2+ tabs.'),
       _sliver = true;

  /// Tabs, left to right in LTR.
  final List<M3ETab> tabs;

  /// Selected index.
  final int selectedIndex;

  /// Called when a tab is chosen.
  final ValueChanged<int> onTabSelected;

  /// Primary or secondary.
  final M3ETabsVariant variant;

  /// Forces scrollable tabs when true, and equal slots when false.
  ///
  /// Null scrolls only when a label does not fit its equal slot.
  final bool? scrollable;

  /// How fixed tabs share the width. Ignored while scrolling.
  final M3ETabsAlignment alignment;

  /// Optional selection controller.
  final M3ETabsController? controller;

  /// Whether a sliver bar scrolls away and returns.
  final bool floating;

  final bool _sliver;

  @override
  State<M3ETabs> createState() => _M3ETabsState();
}

class _M3ETabsState extends State<M3ETabs> with TickerProviderStateMixin {
  final GlobalKey _barKey = GlobalKey();
  final ScrollController _scroll = ScrollController();
  late final SingleMotionController _indicatorLeft;
  late final SingleMotionController _indicatorWidth;
  late final ValueChanged<int> _controllerSelect = _selectFromController;

  late List<GlobalKey> _slotKeys;
  late List<GlobalKey> _contentKeys;
  late List<FocusNode> _nodes;

  M3ETabsController? _bound;
  bool _placed = false;
  bool _inside = false;
  bool _redirecting = false;

  @override
  void initState() {
    super.initState();
    final spring = SpringMotion(M3EMotion.spatialDefault.toDescription());
    _indicatorLeft = SingleMotionController(motion: spring, vsync: this);
    _indicatorWidth = SingleMotionController(motion: spring, vsync: this);
    _alloc(widget.tabs.length);
    _scheduleMeasure();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _bindController();
  }

  @override
  void didUpdateWidget(covariant M3ETabs oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.tabs.length != widget.tabs.length) {
      _disposeNodes();
      _alloc(widget.tabs.length);
      _placed = false;
    }
    if (oldWidget.controller != widget.controller) {
      _bindController();
    } else {
      _bound?.updateIndex(_index);
    }
    _scheduleMeasure();
  }

  @override
  void dispose() {
    _bound?.detach(_controllerSelect);
    _disposeNodes();
    _indicatorLeft.dispose();
    _indicatorWidth.dispose();
    _scroll.dispose();
    super.dispose();
  }

  int get _index => widget.selectedIndex.clamp(0, widget.tabs.length - 1);

  @override
  Widget build(BuildContext context) => _build(context);
}
