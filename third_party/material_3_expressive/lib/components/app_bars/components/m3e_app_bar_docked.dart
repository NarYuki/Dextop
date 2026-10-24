part of '../m3e_app_bars.dart';

/// Fixed top bar. Scroll-under follows the body scroll offset.
class _M3EDockedAppBar extends StatefulWidget {
  const _M3EDockedAppBar({required this.bar});

  final M3EAppBar bar;

  @override
  State<_M3EDockedAppBar> createState() => _M3EDockedAppBarState();
}

class _M3EDockedAppBarState extends State<_M3EDockedAppBar>
    with SingleTickerProviderStateMixin {
  ScrollNotificationObserverState? _observer;
  ScrollPosition? _position;
  final _M3EPageScroll _pageScroll = _M3EPageScroll();
  M3EAppBarController? _bound;
  late final AnimationController _visibility;
  bool _under = false;
  bool _follow = true;
  double _offset = 0;

  /// [_offset] as of the last build. Compared against instead of the previous
  /// notification so slow drags (many sub-pixel steps) still move the bar
  /// every frame; several notifications in one frame share one build.
  double _builtOffset = 0;
  double _collapsed = 0;
  double _expanded = 0;

  M3EAppBar get bar => widget.bar;

  /// The page runs behind the bar ([Scaffold.extendBodyBehindAppBar]), so
  /// the bar can keep a fixed slot and move its surface with the content.
  /// Otherwise the body starts at the bar's laid-out height and moves with
  /// every change to it, which can never stay in step with the scroll.
  bool get _glued {
    return Scaffold.maybeOf(context)?.widget.extendBodyBehindAppBar ?? false;
  }

  @override
  void initState() {
    super.initState();
    _visibility = AnimationController(
      vsync: this,
      value: 1,
      duration: _kAppBarTravel,
    )..addListener(_onVisibility);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _bindObserver();
    _bindController();
    _seedPrimary();
  }

  @override
  void didUpdateWidget(_M3EDockedAppBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    final double? live = _m3eAppBarHeightOf(oldWidget.bar);
    if (live != null) {
      _m3eAppBarWriteHeight(bar, live);
    }
    if (oldWidget.bar.controller != bar.controller) {
      _bindController();
    }
  }

  @override
  void dispose() {
    _observer?.removeListener(_onNotification);
    if (_bound == bar.controller) {
      bar.controller?.detach();
    }
    _m3eAppBarClearHeight(bar);
    _visibility
      ..removeListener(_onVisibility)
      ..dispose();
    super.dispose();
  }

  void _bindObserver() {
    final ScrollNotificationObserverState? next =
        ScrollNotificationObserver.maybeOf(context);
    if (next == _observer) {
      return;
    }
    _observer?.removeListener(_onNotification);
    _observer = next;
    _observer?.addListener(_onNotification);
  }

  /// Re-derives on-scroll state from the live scroll position whenever a
  /// dependency changes (e.g. a theme rebuild). Notifications are the normal
  /// source of truth, but nothing else corrects [_offset]/[_under] if a
  /// dependency-driven rebuild ever leaves them stale, so this resyncs them
  /// unconditionally rather than only when [_offset] happens to be zero.
  void _seedPrimary() {
    final ScrollPosition? primary = _m3eSinglePrimaryPosition(context);
    if (primary == null) {
      return;
    }
    _position = primary;
    if (primary.pixels != _offset) {
      _applyOffset(primary.pixels);
    }
  }

  void _onNotification(ScrollNotification notification) {
    if (!mounted || !_pageScroll.accepts(notification, context)) {
      return;
    }
    final BuildContext? target = notification.context;
    if (target != null && target.mounted) {
      _position = Scrollable.maybeOf(target)?.position;
    }
    _applyOffset(notification.metrics.extentBefore);
  }

  void _onVisibility() {
    if (mounted) {
      setState(() {});
    }
    _publish();
  }

  void _bindController() {
    final M3EAppBarController? next = bar.controller;
    if (_bound == next) {
      _publish();
      return;
    }
    _bound?.detach();
    _bound = next;
    next?.attach(
      collapsed: _collapsedNow,
      visible: _visibleNow,
      expand: _expand,
      collapse: _collapse,
      show: _show,
      hide: _hide,
      followScroll: _resumeScroll,
    );
  }

  bool get _visibleNow {
    final M3EAppBarHideMode mode = bar._effectiveHideMode;
    if (mode == M3EAppBarHideMode.actions) {
      return true;
    }
    if (_follow && _glued) {
      return mode == M3EAppBarHideMode.none || _offset < _expanded - 0.5;
    }
    return _visibility.value > 0.01;
  }

  bool get _collapsedNow {
    if (_expanded <= _collapsed + 0.5) {
      return true;
    }
    return _offset >= _expanded - _collapsed - 0.5;
  }

  void _publish() {
    _bound?.update(collapsed: _collapsedNow, visible: _visibleNow);
  }

  void _applyOffset(double offset) {
    _offset = offset;
    final under = offset > 0;
    final changed = under != _under;
    _under = under;
    _maybeSlideForScroll();
    _publish();
    // A small bar only changes color at the top. Rebuilding it on every
    // pixel restarts hover under the pointer and the list appears to flicker.
    _maybeRebuild(changed: changed, offset: offset);
  }

  /// Starts or stops the hide-on-scroll travel. Follows the scrollable's own
  /// [ScrollPosition.userScrollDirection] so a hide/show starts the instant a
  /// drag changes direction, at any scroll position and at any drag speed.
  void _maybeSlideForScroll() {
    if (!_follow ||
        bar._effectiveHideMode == M3EAppBarHideMode.none ||
        _glued) {
      return;
    }
    switch (_position?.userScrollDirection) {
      case ScrollDirection.reverse:
        _m3eSlideAway(_visibility);
      case ScrollDirection.forward:
        _m3eSlideBack(_visibility);
      case ScrollDirection.idle:
      case null:
        break;
    }
  }

  void _maybeRebuild({required bool changed, required double offset}) {
    // A glued bar moves its surface with every scrolled pixel.
    final bool flexible =
        _expanded > _collapsed + 0.5 ||
        (_glued && bar._effectiveHideMode != M3EAppBarHideMode.none);
    final bool shouldRebuild = changed || (flexible && offset != _builtOffset);
    if (mounted && shouldRebuild) {
      setState(() {});
    }
  }

  void _commitExtent(double height) {
    if (bar.toolbarHeight != null || _collapsed <= 0) {
      return;
    }
    final double? previous = _m3eAppBarHeightOf(bar);
    // preferredSize is only the scaffold's max: the body starts where the
    // bar's laid-out height ends. It is always the expanded height, so the
    // bar can shrink and grow inside it in the same frame as the scroll. It
    // only changes with the theme or variant, never per scroll frame.
    if (previous != null && (height - previous).abs() <= 0.5) {
      return;
    }
    _m3eAppBarWriteHeight(bar, height);
    final ScaffoldState? scaffold = Scaffold.maybeOf(context);
    if (scaffold == null) {
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (scaffold.mounted) {
        scaffold.setState(() {});
      }
    });
  }

  ScrollPosition? _activePosition() {
    final ScrollPosition? current = _position;
    if (current != null && current.hasPixels) {
      return current;
    }
    return _m3eSinglePrimaryPosition(context) ?? current;
  }

  Future<void> _expand() {
    final ScrollPosition? position = _activePosition();
    if (position == null || !position.hasPixels) {
      return Future<void>.value();
    }
    return position.animateTo(
      0,
      duration: _kAppBarTravel,
      curve: Curves.easeOut,
    );
  }

  Future<void> _collapse() {
    final ScrollPosition? position = _activePosition();
    if (position == null || !position.hasPixels) {
      return Future<void>.value();
    }
    final double target = (_expanded - _collapsed).clamp(
      0.0,
      position.maxScrollExtent,
    );
    return position.animateTo(
      target,
      duration: _kAppBarTravel,
      curve: Curves.easeOut,
    );
  }

  Future<void> _show() {
    _follow = false;
    return _visibility.forward();
  }

  Future<void> _hide() {
    _follow = false;
    return _visibility.reverse();
  }

  Future<void> _resumeScroll() {
    _follow = true;
    if (_glued) {
      return _visibility.forward();
    }
    if (_offset <= math.max(0, _expanded - _collapsed) + 0.5) {
      return _visibility.forward();
    }
    return Future<void>.value();
  }

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final appBarTheme = theme.appBarTheme;
    final scheme = theme.colorScheme;
    final metrics = appBarTheme.metrics(bar.density);
    final hasSubtitle = bar.subtitle != null || bar.subtitleText != null;
    _collapsed = metrics.collapsedHeight;
    _expanded = bar.toolbarHeight != null
        ? bar.toolbarHeight!
        : metrics.expandedHeight(bar.variant, hasSubtitle: hasSubtitle);
    final bool glued = _glued;
    _builtOffset = _offset;
    final motion = _M3EBarMotion(
      offset: _offset,
      collapsed: _collapsed,
      expanded: _expanded,
      shown: _visibility.value,
      mode: bar._effectiveHideMode,
      manual: !_follow,
      glued: glued,
    );
    final double slot = bar.toolbarHeight ?? (glued ? _expanded : motion.slot);
    if (bar.toolbarHeight == null) {
      _commitExtent(_expanded);
    }

    // A bar that hides on scroll keeps its resting color, elevation, and
    // search field; only a bar that stays shows the scrolled-under state.
    final bool under =
        _under && bar._effectiveHideMode == M3EAppBarHideMode.none;
    final Color bg = _m3eBarColor(
      bar: bar,
      theme: appBarTheme,
      scheme: scheme,
      under: under,
    );
    final double elevation =
        bar.elevation ??
        (under ? metrics.scrolledElevation : metrics.elevation);
    final Widget body = _M3EBarBody(
      expand: motion.titleExpand,
      topPadding: appBarTheme.flexibleTopPadding,
      actionRow: appBarTheme.actionRowHeight,
      bottomPadding: metrics.flexibleBottomPadding,
      titleInset: metrics.titleInset,
      contentPadding: metrics.contentPadding,
      centerTitle: bar.centerTitle,
      search: bar.title is _M3EAppBarSearchTitle,
      leading: _resolvedLeading(context, scheme, appBarTheme),
      actions: bar.actions,
      title: _resolvedTitle(
        context,
        theme,
        appBarTheme,
        scheme,
        motion.titleExpand,
      ),
      leadingColor: bar.foregroundColor ?? appBarTheme.leadingColor(scheme),
      trailingColor: appBarTheme.trailingColor(scheme),
      iconSize: metrics.iconSize,
      separateActions: motion.actions,
      containerColor: bg,
      shadowColor: scheme.shadow,
      shape: appBarTheme.shape(bar.shapeFamily),
    );
    final Widget clipped = motion.entire
        ? ClipRect(
            child: OverflowBox(
              alignment: Alignment.bottomCenter,
              minHeight: motion.painted,
              maxHeight: motion.painted,
              child: SizedBox(height: motion.painted, child: body),
            ),
          )
        : body;
    final EdgeInsets safe = bar._edgeSafeAreaInset(context);
    final Widget? leading = _resolvedLeading(context, scheme, appBarTheme);
    Widget surface(Widget padded) => _dockedSurface(
      padded: padded,
      bg: bg,
      elevation: elevation,
      scheme: scheme,
      appBarTheme: appBarTheme,
      metrics: metrics,
      motion: motion,
      safe: safe,
      leading: leading,
    );

    final Widget material = LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final double inset = safe.top + safe.bottom;
        final double available = constraints.maxHeight.isFinite
            ? math.max(0, constraints.maxHeight - inset)
            : slot;
        final double height = math.min(math.max(0, slot), available);
        final double shown = motion.shown.clamp(0.0, 1.0);
        // Glued, the slot stays fixed and only the painted band shrinks, by
        // exactly the scrolled distance, so it rides on the content.
        final double band = math.min(motion.band, height);
        final Widget padded = Padding(
          padding: safe,
          child: motion.actions
              ? _m3eClipSliding(
                  push: glued
                      ? band
                      : math.min(motion.painted * shown, available),
                  visual: motion.painted,
                  child: body,
                )
              : SizedBox(height: glued ? band : height, child: clipped),
        );
        if (!glued) {
          return surface(padded);
        }
        // The empty part of the slot paints and hit-tests nothing, so the
        // page behind it shows through and stays interactive.
        return SizedBox(
          height: height + inset,
          child: Stack(
            clipBehavior: Clip.none,
            children: <Widget>[
              Positioned(top: 0, left: 0, right: 0, child: surface(padded)),
            ],
          ),
        );
      },
    );
    final Widget scoped = _M3EScrolledUnder(
      scrolledUnder: under,
      child: material,
    );
    if (bar.semanticLabel == null) {
      return scoped;
    }
    return Semantics(container: true, label: bar.semanticLabel, child: scoped);
  }
}
