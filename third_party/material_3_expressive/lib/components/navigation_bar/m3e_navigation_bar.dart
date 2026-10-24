import 'dart:math' as math;

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import 'components/m3e_nav_bar_destination_button.dart';
import 'controllers/m3e_navigation_bar_controller.dart';
import 'enums/m3e_nav_bar_enums.dart';
import 'models/m3e_nav_metrics.dart';
import 'models/m3e_navigation_bar_destination.dart';
import 'styles/m3e_navigation_bar_theme.dart';

export 'controllers/m3e_navigation_bar_controller.dart';
export 'enums/m3e_nav_bar_enums.dart';
export 'models/m3e_nav_metrics.dart';
export 'models/m3e_navigation_bar_destination.dart';
export 'res/m3e_nav_bar_constants.dart';
export 'styles/m3e_navigation_bar_theme.dart';

part 'components/m3e_navigation_bar_build.dart';

/// A Material 3 Expressive navigation bar.
///
/// The flexible size is 64. The baseline size is 80. Vertical items share the
/// width and keep the pill behind the icon. Horizontal items turn on in a
/// medium window, or when [layout] is [M3ENavBarLayout.wide]. Arrow keys move
/// focus. Space or Enter selects, including the active destination.
class M3ENavigationBar extends StatefulWidget {
  /// M3ENavigationBar.
  const M3ENavigationBar({
    super.key,
    required this.destinations,
    this.selectedIndex = 0,
    this.onDestinationSelected,
    this.labelBehavior = M3ENavBarLabelBehavior.alwaysShow,
    this.iconBehavior = M3ENavBarIconBehavior.alwaysShow,
    this.autoLayout = true,
    this.layout = M3ENavBarLayout.compact,
    this.alignment = M3ENavBarAlignment.center,
    this.wideBreakpoint,
    this.wideDestinationWidth,
    this.size = M3ENavBarSize.medium,
    this.shapeFamily = M3ENavBarShapeFamily.square,
    this.density = M3ENavBarDensity.regular,
    this.backgroundColor,
    this.elevation,
    this.indicatorStyle = M3ENavBarIndicatorStyle.pill,
    this.indicatorColor,
    this.padding,
    this.safeArea = true,
    this.semanticLabel,
    this.controller,
    this.hideOnScroll = false,
    this.scrollController,
  });

  /// destinations.
  final List<M3ENavigationBarDestination> destinations;

  /// selectedIndex.
  final int selectedIndex;

  /// Called when a destination is chosen, including the active one.
  final ValueChanged<int>? onDestinationSelected;

  /// labelBehavior.
  final M3ENavBarLabelBehavior labelBehavior;

  /// iconBehavior.
  final M3ENavBarIconBehavior iconBehavior;

  /// When true, pick compact vs wide from the bar’s own width.
  final bool autoLayout;

  /// Used only when [autoLayout] is false.
  final M3ENavBarLayout layout;

  /// Destination-group placement in wide layout (bar stays full width).
  final M3ENavBarAlignment alignment;

  /// Auto-layout width threshold. When null, uses the theme breakpoint
  /// (600).
  final double? wideBreakpoint;

  /// Width of each horizontal destination. When null, every item uses the
  /// widest icon-plus-label content.
  final double? wideDestinationWidth;

  /// Flexible [M3ENavBarSize.small] or baseline [M3ENavBarSize.medium].
  ///
  /// Defaults to the baseline bar.
  final M3ENavBarSize size;

  /// shapeFamily.
  final M3ENavBarShapeFamily shapeFamily;

  /// density.
  final M3ENavBarDensity density;

  /// backgroundColor.
  final Color? backgroundColor;

  /// elevation.
  final double? elevation;

  /// indicatorStyle.
  final M3ENavBarIndicatorStyle indicatorStyle;

  /// indicatorColor.
  final Color? indicatorColor;

  /// padding.
  final EdgeInsetsGeometry? padding;

  /// When true, the system navigation inset is padding inside the bar.
  final bool safeArea;

  /// semanticLabel.
  final String? semanticLabel;

  /// Optional selection and visibility controller.
  final M3ENavigationBarController? controller;

  /// When true, [scrollController] hides the bar on a downward scroll.
  final bool hideOnScroll;

  /// Body scroll position. A bottom bar is not inside that scroll view.
  final ScrollController? scrollController;

  @override
  State<M3ENavigationBar> createState() => _M3ENavigationBarState();
}

class _M3ENavigationBarState extends State<M3ENavigationBar> {
  final List<FocusNode> _nodes = <FocusNode>[];
  M3ENavigationBarController? _bound;
  ScrollController? _scroll;
  late final ValueChanged<int> _controllerSelect = _selectFromController;
  bool _inside = false;
  bool _redirecting = false;
  int _focusIndex = 0;
  bool _visible = true;

  int get _selected {
    if (widget.destinations.isEmpty) {
      return 0;
    }
    return widget.selectedIndex.clamp(0, widget.destinations.length - 1);
  }

  @override
  void initState() {
    super.initState();
    _focusIndex = widget.selectedIndex;
    _syncNodes();
    _bindScroll();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _bindController();
  }

  @override
  void didUpdateWidget(covariant M3ENavigationBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.destinations.length != widget.destinations.length) {
      _syncNodes();
    }
    if (oldWidget.controller != widget.controller) {
      _bindController();
    } else {
      _bound?.updateIndex(_selected);
    }
    if (oldWidget.scrollController != widget.scrollController ||
        oldWidget.hideOnScroll != widget.hideOnScroll) {
      _bindScroll();
    }
    if (!_inside) {
      _focusIndex = _selected;
    }
  }

  @override
  void dispose() {
    _bound?.removeListener(_onController);
    _bound?.detach(_controllerSelect);
    _scroll?.removeListener(_onScroll);
    for (final FocusNode node in _nodes) {
      node.dispose();
    }
    super.dispose();
  }

  void _syncNodes() {
    final int count = widget.destinations.length;
    while (_nodes.length < count) {
      _nodes.add(
        FocusNode(debugLabel: 'nav ${_nodes.length}')..addListener(_onFocus),
      );
    }
    while (_nodes.length > count) {
      _nodes.removeLast()
        ..removeListener(_onFocus)
        ..dispose();
    }
    if (_nodes.isEmpty) {
      _focusIndex = 0;
      return;
    }
    _focusIndex = _focusIndex.clamp(0, _nodes.length - 1);
  }

  void _bindController() {
    final M3ENavigationBarController? next = widget.controller;
    if (identical(_bound, next)) {
      _bound?.updateIndex(_selected);
      return;
    }
    _bound?.removeListener(_onController);
    _bound?.detach(_controllerSelect);
    _bound = next;
    next?.attach(_controllerSelect, _selected);
    next?.addListener(_onController);
  }

  void _bindScroll() {
    final ScrollController? next = widget.hideOnScroll
        ? widget.scrollController
        : null;
    if (identical(_scroll, next)) {
      return;
    }
    _scroll?.removeListener(_onScroll);
    _scroll = next;
    _scroll?.addListener(_onScroll);
  }

  void _selectFromController(int index) {
    widget.onDestinationSelected?.call(index);
  }

  void _onController() {
    if (!mounted) {
      return;
    }
    final bool visible = _bound?.visible ?? _visible;
    if (visible == _visible) {
      return;
    }
    setState(() => _visible = visible);
  }

  void _onScroll() {
    if (!mounted || !widget.hideOnScroll) {
      return;
    }
    final ScrollController? scroll = _scroll;
    if (scroll == null || !scroll.hasClients) {
      return;
    }
    if (MediaQuery.accessibleNavigationOf(context)) {
      _applyVisible(true);
      return;
    }
    final ScrollDirection direction = scroll.position.userScrollDirection;
    if (direction == ScrollDirection.reverse) {
      _applyVisible(false);
    } else if (direction == ScrollDirection.forward) {
      _applyVisible(true);
    }
  }

  void _applyVisible(bool visible) {
    final M3ENavigationBarController? bound = _bound;
    if (bound != null) {
      bound.updateVisible(visible: visible);
      return;
    }
    if (_visible == visible) {
      return;
    }
    setState(() => _visible = visible);
  }

  bool _shown(BuildContext context) {
    if (!widget.hideOnScroll) {
      return true;
    }
    if (MediaQuery.accessibleNavigationOf(context)) {
      return true;
    }
    return _bound?.visible ?? _visible;
  }

  void _onFocus() {
    if (!mounted || _redirecting || _nodes.isEmpty) {
      return;
    }
    final bool any = _nodes.any((FocusNode node) => node.hasFocus);
    if (!any) {
      if (!_inside) {
        return;
      }
      setState(() {
        _inside = false;
        _focusIndex = _selected;
      });
      return;
    }
    final int focused = _nodes.indexWhere((FocusNode node) => node.hasFocus);
    if (!_inside) {
      _inside = true;
      if (focused != _selected) {
        _redirecting = true;
        _focusIndex = _selected;
        _nodes[_selected].requestFocus();
        _redirecting = false;
        setState(() {});
        return;
      }
    }
    if (focused >= 0 && focused != _focusIndex) {
      setState(() => _focusIndex = focused);
    }
  }

  void _move(int delta) {
    final int current = _nodes.indexWhere((FocusNode node) => node.hasFocus);
    if (current < 0) {
      return;
    }
    final int next = current + delta;
    if (next < 0 || next >= _nodes.length) {
      return;
    }
    setState(() {
      _inside = true;
      _focusIndex = next;
    });
    _nodes[next].requestFocus();
  }

  bool _tabStop(int index) {
    if (_inside) {
      return index == _focusIndex;
    }
    return index == _selected;
  }

  @override
  Widget build(BuildContext context) {
    assert(widget.destinations.isNotEmpty, 'Provide at least one destination');
    return M3EComponentTheme(
      builder: (BuildContext context) => _buildNavigationBar(context),
    );
  }
}
