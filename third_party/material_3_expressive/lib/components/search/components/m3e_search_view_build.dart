part of 'm3e_search_view.dart';

extension _M3ESearchViewContentBuild on _M3ESearchViewContentState {
  Widget _buildSearchView(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge(<Listenable>[_progress, _fade, _swap, _back]),
      builder: (BuildContext context, Widget? child) => _buildFrame(),
    );
  }

  Widget _buildFrame() {
    final theme = M3ETheme.of(context);
    final viewTheme = theme.searchViewTheme;
    final scheme = theme.colorScheme;
    final M3ESearchViewStyle style = _route.viewStyle ?? viewTheme.style;
    final _ViewGeometry g = _resolveGeometry(
      screen: MediaQuery.sizeOf(context),
      viewTheme: viewTheme,
      style: style,
    );
    final _ViewStyles s = _resolveViewStyles(
      theme: theme,
      viewTheme: viewTheme,
      scheme: scheme,
      style: style,
      fullScreen: g.fullScreen,
    );
    final Widget surface = g.fullScreen
        ? _buildFullScreen(g: g, s: s, style: style, viewTheme: viewTheme)
        : _buildDocked(g: g, s: s, style: style);
    return Stack(
      children: <Widget>[
        if (!g.fullScreen)
          Positioned.fill(
            child: _buildScrim(
              _route.scrimColor ?? viewTheme.scrimColor(scheme),
            ),
          ),
        Positioned.fromRect(
          rect: g.rect,
          child: Transform(
            transform: _backTransform(g.rect, viewTheme),
            child: CallbackShortcuts(
              bindings: <ShortcutActivator, VoidCallback>{
                const SingleActivator(LogicalKeyboardKey.escape): _close,
              },
              child: surface,
            ),
          ),
        ),
      ],
    );
  }

  /// Scrim behind the docked layout; a tap dismisses the view.
  Widget _buildScrim(Color color) {
    final double opacity = clampDouble(_fade.value, 0, 1);
    return ExcludeSemantics(
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: _close,
        child: ColoredBox(color: color.withValues(alpha: color.a * opacity)),
      ),
    );
  }

  Widget _buildFullScreen({
    required _ViewGeometry g,
    required _ViewStyles s,
    required M3ESearchViewStyle style,
    required M3ESearchViewTheme viewTheme,
  }) {
    final contained = style == M3ESearchViewStyle.contained;
    final double top = M3ESafeArea.topOf(context) * g.t;
    final double headerHeight = lerpDouble(
      g.begin.height,
      s.headerHeight,
      g.t,
    )!;
    final double start = contained ? viewTheme.containedLeadingMargin * g.t : 0;
    final double end = contained ? viewTheme.containedTrailingMargin * g.t : 0;
    // Contained: the 56 bar sits in a 72 header (8 above and below).
    final double vertical = contained
        ? viewTheme.containedFullScreenBarVerticalPadding * g.t
        : 0;
    final double inset = contained
        ? viewTheme.containedFullScreenResultsInset
        : viewTheme.dividedResultsInset;
    final double backRadius =
        viewTheme.cornerRadius * clampDouble(_back.value, 0, 1);
    final double radius =
        lerpDouble(g.begin.height / 2, s.radius, g.t)! + backRadius;
    final column = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        SizedBox(height: top),
        SafeArea(
          top: false,
          bottom: false,
          child: Padding(
            padding: EdgeInsetsDirectional.fromSTEB(
              start,
              vertical,
              end,
              vertical,
            ),
            child: _buildHeader(s: s, style: style, height: headerHeight),
          ),
        ),
        if (!contained) _buildDivider(s),
        Expanded(
          child: _fadeIn(
            _buildResults(
              padding: EdgeInsets.fromLTRB(
                inset,
                0,
                inset,
                M3ESafeArea.overlayBottomOf(context),
              ),
              shrinkWrap: false,
            ),
          ),
        ),
      ],
    );
    return _buildSurface(
      s: s,
      color: Color.lerp(s.barColor, s.background, g.t)!,
      elevation: s.elevation * g.t,
      radius: radius,
      child: _fitColumn(
        column: column,
        height: g.rect.height,
        needed: top + headerHeight + vertical * 2 + (contained ? 0 : 1),
      ),
    );
  }

  Widget _buildDocked({
    required _ViewGeometry g,
    required _ViewStyles s,
    required M3ESearchViewStyle style,
  }) {
    final M3ESearchViewTheme viewTheme = M3ETheme.of(context).searchViewTheme;
    final double headerHeight = lerpDouble(
      g.begin.height,
      s.headerHeight,
      g.t,
    )!;
    final Widget docked = style == M3ESearchViewStyle.contained
        ? _buildContainedDocked(
            g: g,
            s: s,
            viewTheme: viewTheme,
            headerHeight: headerHeight,
          )
        : _buildDividedDocked(
            g: g,
            s: s,
            viewTheme: viewTheme,
            headerHeight: headerHeight,
          );
    return Padding(padding: s.padding, child: docked);
  }

  Widget _buildContainedDocked({
    required _ViewGeometry g,
    required _ViewStyles s,
    required M3ESearchViewTheme viewTheme,
    required double headerHeight,
  }) {
    final double gap = viewTheme.dockedBarResultsGap * g.t;
    final Widget results = _buildSurface(
      s: s,
      color: s.background,
      elevation: s.elevation,
      radius: s.radius,
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          viewTheme.dockedResultsInset,
          0,
          viewTheme.dockedResultsInset,
          viewTheme.dockedResultsBottomPadding,
        ),
        child: _buildResults(
          padding: EdgeInsets.zero,
          shrinkWrap: s.shrinkWrap,
        ),
      ),
    );
    final column = Column(
      mainAxisSize: s.shrinkWrap ? MainAxisSize.min : MainAxisSize.max,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        _buildHeader(
          s: s,
          style: M3ESearchViewStyle.contained,
          height: headerHeight,
        ),
        SizedBox(height: gap),
        Flexible(
          fit: s.shrinkWrap ? FlexFit.loose : FlexFit.tight,
          child: _fadeIn(results),
        ),
      ],
    );
    return _fitColumn(
      column: column,
      height: g.rect.height,
      needed: headerHeight + gap,
      shrinkWrap: s.shrinkWrap,
    );
  }

  Widget _buildDividedDocked({
    required _ViewGeometry g,
    required _ViewStyles s,
    required M3ESearchViewTheme viewTheme,
    required double headerHeight,
  }) {
    final double pad = viewTheme.dividedDockedListPadding;
    final column = Column(
      mainAxisSize: s.shrinkWrap ? MainAxisSize.min : MainAxisSize.max,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        _buildHeader(
          s: s,
          style: M3ESearchViewStyle.divided,
          height: headerHeight,
        ),
        _buildDivider(s),
        Flexible(
          fit: s.shrinkWrap ? FlexFit.loose : FlexFit.tight,
          child: _fadeIn(
            _buildResults(
              padding: EdgeInsets.symmetric(
                horizontal: viewTheme.dividedResultsInset,
                vertical: pad,
              ),
              shrinkWrap: s.shrinkWrap,
            ),
          ),
        ),
      ],
    );
    return _buildSurface(
      s: s,
      color: Color.lerp(s.barColor, s.background, g.t)!,
      elevation: s.elevation * g.t,
      radius: lerpDouble(g.begin.height / 2, s.radius, g.t)!,
      child: _fitColumn(
        column: column,
        height: g.rect.height,
        needed: headerHeight + 1,
        shrinkWrap: s.shrinkWrap,
      ),
    );
  }

  /// Sizes [column] to the surface; grows it when the spring is mid-way.
  Widget _fitColumn({
    required Column column,
    required double height,
    required double needed,
    bool shrinkWrap = false,
  }) {
    final double max = math.max(height, needed);
    if (shrinkWrap) {
      return Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: math.min(needed, max),
            maxHeight: max,
          ),
          child: column,
        ),
      );
    }
    return ClipRect(
      child: OverflowBox(
        alignment: Alignment.topCenter,
        minHeight: max,
        maxHeight: max,
        child: column,
      ),
    );
  }

  Widget _buildSurface({
    required _ViewStyles s,
    required Color color,
    required double elevation,
    required double radius,
    required Widget child,
  }) {
    OutlinedBorder shape =
        s.shape ??
        RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(math.max(0, radius)),
        );
    if (s.side != null) {
      shape = shape.copyWith(side: s.side);
    }
    return Material(
      color: color,
      surfaceTintColor: s.surfaceTint,
      elevation: elevation,
      shape: shape,
      clipBehavior: Clip.antiAlias,
      child: child,
    );
  }

  Widget _fadeIn(Widget child) {
    return Opacity(opacity: clampDouble(_fade.value, 0, 1), child: child);
  }

  Widget _buildDivider(_ViewStyles s) {
    return _fadeIn(M3EDivider(color: s.dividerColor));
  }

  Widget _buildHeader({
    required _ViewStyles s,
    required M3ESearchViewStyle style,
    required double height,
  }) {
    final contained = style == M3ESearchViewStyle.contained;
    final EdgeInsetsGeometry? barPadding = _route.viewBarPadding;
    return CallbackShortcuts(
      bindings: <ShortcutActivator, VoidCallback>{
        const SingleActivator(LogicalKeyboardKey.arrowDown): _focusFirstResult,
      },
      child: M3ESearchBar(
        focusNode: _viewFocusNode,
        expandOnFocus: false,
        constraints: BoxConstraints.tightFor(height: height),
        padding: barPadding == null
            ? null
            : WidgetStatePropertyAll<EdgeInsetsGeometry>(barPadding),
        leading:
            _route.viewLeading ??
            M3EIconButton(
              variant: M3EIconButtonVariant.standard,
              icon: const Icon(M3EIcons.arrow_back),
              tooltip: M3ESearchConstants.backButtonTooltip,
              onPressed: _close,
            ),
        trailing: _route.viewTrailing,
        showClearButton: _route.showViewClearButton,
        hintText: _route.viewHintText,
        backgroundColor: WidgetStatePropertyAll<Color>(
          contained ? s.barColor : const Color(0x00000000),
        ),
        overlayColor: contained
            ? null
            : const WidgetStatePropertyAll<Color>(Color(0x00000000)),
        elevation: const WidgetStatePropertyAll<double>(0),
        textStyle: WidgetStatePropertyAll<TextStyle>(s.textStyle),
        hintStyle: WidgetStatePropertyAll<TextStyle>(s.hintStyle),
        controller: _route.searchController,
        onEscape: _close,
        onChanged: (String value) {
          _route.viewOnChanged?.call(value);
          _updateSuggestions();
        },
        onSubmitted: _route.viewOnSubmitted,
        textCapitalization: _route.textCapitalization,
        textInputAction: _route.textInputAction,
        keyboardType: _route.keyboardType,
        smartDashesType: _route.smartDashesType,
        smartQuotesType: _route.smartQuotesType,
      ),
    );
  }

  /// Results list; arrows move between items, up from the first returns to
  /// the field.
  Widget _buildResults({
    required EdgeInsets padding,
    required bool shrinkWrap,
  }) {
    final M3ESearchViewBuilder? builder = _route.viewBuilder;
    final Widget list = builder == null
        ? MediaQuery.removePadding(
            context: context,
            removeTop: true,
            child: ListView(
              padding: padding,
              shrinkWrap: shrinkWrap,
              children: _suggestions.toList(),
            ),
          )
        : builder(_suggestions);
    return Focus(
      focusNode: _resultsNode,
      child: CallbackShortcuts(
        bindings: <ShortcutActivator, VoidCallback>{
          const SingleActivator(LogicalKeyboardKey.arrowDown): () =>
              _moveResult(1),
          const SingleActivator(LogicalKeyboardKey.arrowUp): () =>
              _moveResult(-1),
        },
        child: FocusTraversalGroup(child: list),
      ),
    );
  }

  _ViewStyles _resolveViewStyles({
    required M3EThemeData theme,
    required M3ESearchViewTheme viewTheme,
    required M3EColorScheme scheme,
    required M3ESearchViewStyle style,
    required bool fullScreen,
  }) {
    final contained = style == M3ESearchViewStyle.contained;
    final Color background = switch ((contained, fullScreen)) {
      (true, true) => viewTheme.containedBackgroundColor(scheme),
      (true, false) => viewTheme.containedContainerColor(scheme),
      (false, true) => viewTheme.fullScreenBackgroundColor(scheme),
      (false, false) => viewTheme.backgroundColor(scheme),
    };
    final double elevation = contained
        ? viewTheme.containedElevation
        : (fullScreen ? M3EElevation.level0 : viewTheme.elevation);
    final double headerHeight = contained
        ? viewTheme.containedBarHeight
        : (fullScreen
              ? viewTheme.headerHeight
              : viewTheme.dividedDockedHeaderHeight);
    final double radius = fullScreen
        ? viewTheme.fullScreenRadius
        : (contained ? viewTheme.dockedResultsRadius : viewTheme.cornerRadius);
    return _ViewStyles(
      barColor: theme.searchBarTheme.backgroundColor(scheme),
      background: _route.viewBackgroundColor ?? background,
      surfaceTint:
          _route.viewSurfaceTintColor ?? viewTheme.surfaceTintColor(scheme),
      elevation: _route.viewElevation ?? elevation,
      shape: _route.viewShape,
      side: _route.viewSide,
      radius: radius,
      dividerColor: _route.dividerColor ?? viewTheme.dividerColor(scheme),
      headerHeight: _route.viewHeaderHeight ?? headerHeight,
      textStyle:
          _route.viewHeaderTextStyle ??
          viewTheme.headerTextStyle(theme.typeScale, scheme),
      hintStyle:
          _route.viewHeaderHintStyle ??
          _route.viewHeaderTextStyle ??
          viewTheme.headerHintStyle(theme.typeScale, scheme),
      padding: _route.viewPadding ?? EdgeInsets.zero,
      shrinkWrap: _route.shrinkWrap ?? viewTheme.shrinkWrap,
    );
  }
}

class _ViewStyles {
  const _ViewStyles({
    required this.barColor,
    required this.background,
    required this.surfaceTint,
    required this.elevation,
    required this.shape,
    required this.side,
    required this.radius,
    required this.dividerColor,
    required this.headerHeight,
    required this.textStyle,
    required this.hintStyle,
    required this.padding,
    required this.shrinkWrap,
  });

  final Color barColor;
  final Color background;
  final Color surfaceTint;
  final double elevation;
  final OutlinedBorder? shape;
  final BorderSide? side;
  final double radius;
  final Color dividerColor;
  final double headerHeight;
  final TextStyle textStyle;
  final TextStyle hintStyle;
  final EdgeInsetsGeometry padding;
  final bool shrinkWrap;
}
