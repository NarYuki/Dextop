import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' show FlutterView, lerpDouble;

import 'package:flutter/foundation.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/search/m3e_search.dart'
    show M3ESearchAnchor;
import 'package:material_3_expressive/components/search/m3e_search_anchor.dart'
    show M3ESearchAnchor;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ESearchAnchor;
import 'package:material_ui/material_ui.dart'
    show Color, Material, WidgetStatePropertyAll;

import '../../../foundations/foundations.dart';
import '../../divider/m3e_divider.dart';
import '../../icon_buttons/m3e_icon_buttons.dart';
import '../controllers/m3e_search_controller.dart';
import '../enums/m3e_search_enums.dart';
import '../m3e_search_bar.dart';
import '../models/m3e_search_anchor_surface.dart';
import '../res/m3e_search_constants.dart';
import '../styles/m3e_search_view_theme.dart';
import '../utils/m3e_search_spring.dart';

part 'm3e_search_view_route.dart';
part 'm3e_search_view_build.dart';
part 'm3e_search_view_transition.dart';

/// Focused search surface shown by [M3ESearchAnchor].
///
/// Springs from the anchor bar into a full-screen or docked view, in the
/// contained or divided style.
class M3ESearchViewContent extends StatefulWidget {
  /// M3ESearchViewContent.
  const M3ESearchViewContent({
    required this.route,
    required this.animation,
    super.key,
  });

  /// Route that owns this view.
  final M3ESearchViewRoute route;

  /// Route animation; its direction drives the spring transform.
  final Animation<double> animation;

  @override
  State<M3ESearchViewContent> createState() => _M3ESearchViewContentState();
}

class _M3ESearchViewContentState extends State<M3ESearchViewContent>
    with TickerProviderStateMixin, WidgetsBindingObserver {
  late final AnimationController _progress = AnimationController.unbounded(
    vsync: this,
  );
  late final AnimationController _fade = AnimationController.unbounded(
    vsync: this,
  );
  late final AnimationController _swap = AnimationController.unbounded(
    vsync: this,
    value: 1,
  );
  late final AnimationController _back = AnimationController.unbounded(
    vsync: this,
  );
  final FocusNode _viewFocusNode = FocusNode(debugLabel: 'M3ESearchView');
  final FocusNode _resultsNode = FocusNode(
    debugLabel: 'M3ESearchViewResults',
    skipTraversal: true,
    canRequestFocus: false,
  );

  Iterable<Widget> _suggestions = const <Widget>[];
  String? _searchValue;
  Timer? _timer;
  bool _viewFocusRequested = false;
  bool _opening = true;
  String? _lastAnnouncement;

  // Layout memory for live full-screen ↔ docked swaps.
  bool? _lastFullScreen;
  Rect? _lastRect;
  Rect? _swapFrom;
  Rect? _anchorRect;
  Rect? _paneRect;

  // Predictive back gesture.
  SwipeEdge _backEdge = SwipeEdge.left;
  double? _backStartY;
  double _backDy = 0;

  M3ESearchViewRoute get _route => widget.route;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _route.searchController.addListener(_scheduleSuggestions);
    _route.searchController.addListener(_handleControllerChanged);
    widget.animation.addStatusListener(_handleRouteStatus);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      _runOpen(open: true);
      unawaited(_updateSuggestions());
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncLayoutSwap();
  }

  @override
  void didUpdateWidget(covariant M3ESearchViewContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.animation != oldWidget.animation) {
      oldWidget.animation.removeStatusListener(_handleRouteStatus);
      widget.animation.addStatusListener(_handleRouteStatus);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.animation.removeStatusListener(_handleRouteStatus);
    _route.searchController.removeListener(_scheduleSuggestions);
    _route.searchController.removeListener(_handleControllerChanged);
    _timer?.cancel();
    _progress.dispose();
    _fade.dispose();
    _swap.dispose();
    _back.dispose();
    _viewFocusNode.dispose();
    _resultsNode.dispose();
    super.dispose();
  }

  M3ESearchViewTheme get _viewTheme => M3ETheme.of(context).searchViewTheme;

  void _handleRouteStatus(AnimationStatus status) {
    final bool open =
        status == AnimationStatus.forward ||
        status == AnimationStatus.completed;
    if (open != _opening) {
      _runOpen(open: open);
    }
  }

  /// Springs the transform and fades toward open (1) or closed (0).
  void _runOpen({required bool open}) {
    _opening = open;
    final M3ESearchViewTheme viewTheme = _viewTheme;
    final double target = open ? 1 : 0;
    _fade.animateWith(
      m3eSearchSpringSimulation(
        viewTheme.fadeSpring,
        from: _fade.value,
        to: target,
        velocity: _fade.velocity,
      ),
    );
    _progress.animateWith(
      m3eSearchSpringSimulation(
        viewTheme.containerTransformSpring,
        from: _progress.value,
        to: target,
        velocity: _progress.velocity,
      ),
    );
    if (open) {
      _tryFocusViewField();
    }
  }

  /// Focuses the field on the first expansion, like Compose's `SearchBar`.
  void _tryFocusViewField() {
    if (_viewFocusRequested || !mounted || !_opening) {
      return;
    }
    _viewFocusRequested = true;
    _viewFocusNode.requestFocus();
  }

  void _handleControllerChanged() => setState(() {});

  void _scheduleSuggestions() {
    if (_searchValue == _route.searchController.text) {
      return;
    }
    _timer?.cancel();
    _timer = Timer(Duration.zero, _updateSuggestions);
  }

  Future<void> _updateSuggestions() async {
    _searchValue = _route.searchController.text;
    final Iterable<Widget> suggestions = await _route.suggestionsBuilder(
      context,
      _route.searchController,
    );
    if (!mounted) {
      return;
    }
    setState(() => _suggestions = suggestions);
    _announce(suggestions.length);
  }

  /// Tells screen readers that suggestions or results changed.
  void _announce(int count) {
    final String message =
        (_route.announcementBuilder ?? m3eSearchResultsAnnouncement)(count);
    final key = '${_route.searchController.text}\u0000$message';
    if (key == _lastAnnouncement) {
      return;
    }
    _lastAnnouncement = key;
    final FlutterView? view = View.maybeOf(context);
    if (view == null) {
      return;
    }
    unawaited(
      SemanticsService.sendAnnouncement(
        view,
        message,
        Directionality.of(context),
      ),
    );
  }

  void _close() => Navigator.of(context).maybePop();

  @override
  bool handleStartBackGesture(PredictiveBackEvent backEvent) =>
      _startBack(backEvent);

  @override
  void handleUpdateBackGestureProgress(PredictiveBackEvent backEvent) =>
      _updateBack(backEvent);

  @override
  void handleCommitBackGesture() {
    _close();
    _settleBack();
  }

  @override
  void handleCancelBackGesture() => _settleBack();

  @override
  Widget build(BuildContext context) => _buildSearchView(context);
}
