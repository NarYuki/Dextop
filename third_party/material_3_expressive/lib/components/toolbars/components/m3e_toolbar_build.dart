part of '../m3e_toolbars.dart';

extension _M3EToolbarBuild on _M3EToolbarState {
  Widget _buildToolbar(BuildContext context) {
    final chrome = _resolveToolbarChrome(context);
    final ({Widget? title, Widget? subtitle, bool hasTitle}) titles =
        _resolveTitles(
          chrome.toolbarTheme,
          chrome.theme.typeScale,
          chrome.foreground,
        );
    final bool useExpanding = _usesTriggerExpand && !titles.hasTitle;
    final Widget body = _buildBody(
      toolbarTheme: chrome.toolbarTheme,
      theme: chrome.theme,
      metrics: chrome.metrics,
      foreground: chrome.foreground,
      titles: titles,
      actionsContent: _buildActionsContent(
        theme: chrome.theme,
        scheme: chrome.scheme,
        metrics: chrome.metrics,
        toolbarTheme: chrome.toolbarTheme,
        style: chrome.style,
        iconButtonSize: chrome.iconButtonSize,
        availableExtent: chrome.availableExtent,
        opticalInset: chrome.opticalInset,
        useExpanding: useExpanding,
        dockedIconsOnly: _isDockedIconsOnly(titles.hasTitle),
        actions: _resolvedActions,
      ),
      useExpanding: useExpanding,
    );
    return _composeBar(
      background: chrome.background,
      elev:
          widget.elevation ??
          (_hasFab
              ? chrome.metrics.elevationWithFab
              : chrome.metrics.elevation),
      shape: chrome.shape,
      contentBand: _buildContentBand(
        metrics: chrome.metrics,
        toolbarTheme: chrome.toolbarTheme,
        theme: chrome.theme,
        foreground: chrome.foreground,
        innerPadding: chrome.innerPadding,
        body: body,
        hasTitle: titles.hasTitle,
      ),
      style: chrome.style,
      hasTitle: titles.hasTitle,
    );
  }

  bool _isDockedIconsOnly(bool hasTitle) =>
      !_floating &&
      !hasTitle &&
      widget.leading == null &&
      widget.trailing == null &&
      widget.actions.isNotEmpty;

  ({
    M3EThemeData theme,
    M3EToolbarTheme toolbarTheme,
    M3EColorScheme scheme,
    M3EToolbarMetrics metrics,
    M3EToolbarColorStyle style,
    Color background,
    Color foreground,
    ShapeBorder shape,
    EdgeInsets innerPadding,
    double availableExtent,
    M3EIconButtonSize iconButtonSize,
    double opticalInset,
  })
  _resolveToolbarChrome(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3EToolbarTheme toolbarTheme = theme.toolbarTheme;
    final M3EColorScheme scheme = theme.colorScheme;
    final M3EToolbarMetrics metrics = toolbarTheme.metricsFor(widget.placement);
    final M3EToolbarColorStyle style = widget.variant != null
        ? toolbarTheme.colorStyleFromVariant(widget.variant!)
        : widget.colorStyle;
    final M3EToolbarColors colors = toolbarTheme.colors(scheme, style);
    final EdgeInsets contentPadding = metrics.contentPadding.resolve(
      Directionality.of(context),
    );
    final EdgeInsets innerPadding =
        widget.padding?.resolve(Directionality.of(context)) ??
        (_floating
            ? _axisAwareFloatingPadding(theme, toolbarTheme, contentPadding)
            : contentPadding);
    final M3EIconButtonSize iconButtonSize = toolbarTheme.iconButtonSize(
      widget.size,
    );
    return (
      theme: theme,
      toolbarTheme: toolbarTheme,
      scheme: scheme,
      metrics: metrics,
      style: style,
      background: widget.backgroundColor ?? colors.container,
      foreground: widget.foregroundColor ?? colors.content,
      shape: _floating
          ? toolbarTheme.floatingShape()
          : toolbarTheme.dockedShape(),
      innerPadding: innerPadding,
      availableExtent: M3EToolbarItemLayout.availableCrossExtent(
        crossAxisSize: metrics.crossAxisSize,
        padding: innerPadding,
        axis: widget.axis,
      ),
      iconButtonSize: iconButtonSize,
      opticalInset: _opticalInset(theme, iconButtonSize),
    );
  }

  Widget _buildContentBand({
    required M3EToolbarMetrics metrics,
    required M3EToolbarTheme toolbarTheme,
    required M3EThemeData theme,
    required Color foreground,
    required EdgeInsets innerPadding,
    required Widget body,
    required bool hasTitle,
  }) {
    return SizedBox(
      height: widget.axis == Axis.horizontal ? metrics.crossAxisSize : null,
      width: widget.axis == Axis.vertical
          ? metrics.crossAxisSize
          : (_floating && !hasTitle ? null : double.infinity),
      child: Padding(
        padding: innerPadding,
        child: M3ETheme(
          data: toolbarTheme.scopedTheme(theme, foreground),
          child: body,
        ),
      ),
    );
  }

  /// Main-axis floating inset matches cross-axis spacing to the pill edge
  /// (padding + half the icon-button target overhang).
  EdgeInsets _axisAwareFloatingPadding(
    M3EThemeData theme,
    M3EToolbarTheme toolbarTheme,
    EdgeInsets base,
  ) {
    final M3EIconButtonSize buttonSize = toolbarTheme.iconButtonSize(
      widget.size,
    );
    final Size target = theme.iconButtonTheme.target(
      buttonSize,
      M3EIconButtonWidth.defaultWidth,
    );
    final Size visual = theme.iconButtonTheme.visual(
      buttonSize,
      M3EIconButtonWidth.defaultWidth,
    );
    if (widget.axis == Axis.horizontal) {
      final double crossOptical = (target.height - visual.height) / 2;
      final double main = base.left + crossOptical;
      return EdgeInsets.fromLTRB(main, base.top, main, base.bottom);
    }
    final double crossOptical = (target.width - visual.width) / 2;
    final double main = base.top + crossOptical;
    return EdgeInsets.fromLTRB(base.left, main, base.right, main);
  }

  double _opticalInset(M3EThemeData theme, M3EIconButtonSize iconButtonSize) {
    final Size iconTarget = theme.iconButtonTheme.target(
      iconButtonSize,
      M3EIconButtonWidth.defaultWidth,
    );
    final Size iconVisual = theme.iconButtonTheme.visual(
      iconButtonSize,
      M3EIconButtonWidth.defaultWidth,
    );
    return widget.axis == Axis.horizontal
        ? (iconTarget.width - iconVisual.width) / 2
        : (iconTarget.height - iconVisual.height) / 2;
  }

  ({Widget? title, Widget? subtitle, bool hasTitle}) _resolveTitles(
    M3EToolbarTheme toolbarTheme,
    M3ETypeScale typeScale,
    Color foreground,
  ) {
    final Widget? resolvedTitle =
        widget.title ??
        (widget.titleText != null
            ? Text(
                widget.titleText!,
                style: toolbarTheme
                    .titleStyle(typeScale)
                    .copyWith(color: foreground),
                overflow: TextOverflow.ellipsis,
              )
            : null);
    final Widget? resolvedSubtitle =
        widget.subtitle ??
        (widget.subtitleText != null
            ? Text(
                widget.subtitleText!,
                style: toolbarTheme
                    .subtitleStyle(typeScale)
                    .copyWith(color: foreground.withValues(alpha: 0.8)),
                overflow: TextOverflow.ellipsis,
              )
            : null);
    return (
      title: resolvedTitle,
      subtitle: resolvedSubtitle,
      hasTitle: resolvedTitle != null || resolvedSubtitle != null,
    );
  }

  Widget _buildActionsContent({
    required M3EThemeData theme,
    required M3EColorScheme scheme,
    required M3EToolbarMetrics metrics,
    required M3EToolbarTheme toolbarTheme,
    required M3EToolbarColorStyle style,
    required M3EIconButtonSize iconButtonSize,
    required double availableExtent,
    required double opticalInset,
    required bool useExpanding,
    required bool dockedIconsOnly,
    required List<M3EToolbarItem> actions,
  }) {
    if (useExpanding) {
      return AnimatedBuilder(
        animation: _expandCtrl,
        builder: (BuildContext context, Widget? child) {
          return M3EToolbarExpandingActions(
            actions: actions,
            maxInline: widget.maxInlineActions,
            overflowIcon: widget.overflowIcon,
            iconButtonSize: iconButtonSize,
            overflowTextStyle: theme.typeScale.labelLarge.copyWith(
              color: scheme.onSurface,
            ),
            destructiveColor: scheme.error,
            axis: widget.axis,
            expandProgress: _expandCtrl.value,
            availableExtent: availableExtent,
            opticalInset: opticalInset,
            onTriggerPressed: () {
              final int triggerIndex = actions.indexWhere(
                (M3EToolbarItem item) =>
                    item is M3EToolbarAction && item.isExpandTrigger,
              );
              if (triggerIndex >= 0) {
                (actions[triggerIndex] as M3EToolbarAction).onPressed();
              }
            },
            leading: widget.leading,
            trailing: widget.trailing,
            gap: metrics.gap,
            pillActiveSpring: widget.pillActiveSpring,
            colorStyle: style,
          );
        },
      );
    }
    final M3EIconButtonShapeVariant shape = _floating
        ? M3EIconButtonShapeVariant.round
        : M3EIconButtonShapeVariant.square;
    final Size target = theme.iconButtonTheme.target(
      iconButtonSize,
      M3EIconButtonWidth.defaultWidth,
    );
    return M3EToolbarActionsRow(
      actions: actions,
      maxInline: widget.maxInlineActions,
      overflowIcon: widget.overflowIcon,
      iconButtonSize: iconButtonSize,
      overflowTextStyle: theme.typeScale.labelLarge.copyWith(
        color: scheme.onSurface,
      ),
      destructiveColor: scheme.error,
      axis: widget.axis,
      availableExtent: availableExtent,
      opticalInset: opticalInset,
      gap: metrics.gap,
      expand: dockedIconsOnly,
      mainAxisAlignment: dockedIconsOnly
          ? MainAxisAlignment.spaceBetween
          : MainAxisAlignment.start,
      pillActiveSpring: widget.pillActiveSpring,
      flexibleDockedGap: dockedIconsOnly,
      compact:
          MediaQuery.sizeOf(context).width < toolbarTheme.compactBreakpoint,
      contentAlignment: widget.contentAlignment,
      preferredGap: toolbarTheme.dockedPreferredGap,
      minGap: toolbarTheme.dockedMinGap,
      centeredGap: toolbarTheme.centeredGap,
      slotExtent: target.width,
      shape: shape,
      colorStyle: style,
    );
  }

  Widget _buildBody({
    required M3EToolbarTheme toolbarTheme,
    required M3EThemeData theme,
    required M3EToolbarMetrics metrics,
    required Color foreground,
    required ({Widget? title, Widget? subtitle, bool hasTitle}) titles,
    required Widget actionsContent,
    required bool useExpanding,
  }) {
    Widget? content;
    if (titles.hasTitle) {
      final double titleStartExtra = _floating
          ? _titleOpticalStartInset(toolbarTheme, theme.iconButtonTheme)
          : 0;
      content = Row(
        children: <Widget>[
          Expanded(
            child: Padding(
              padding: EdgeInsetsDirectional.only(start: titleStartExtra),
              child: M3EToolbarTitleBlock(
                title: titles.title,
                subtitle: titles.subtitle,
                center: widget.centerTitle,
                titleStyle: toolbarTheme
                    .titleStyle(theme.typeScale)
                    .copyWith(color: foreground),
                subtitleStyle: toolbarTheme
                    .subtitleStyle(theme.typeScale)
                    .copyWith(color: foreground.withValues(alpha: 0.8)),
              ),
            ),
          ),
          SizedBox(width: metrics.gap),
          actionsContent,
        ],
      );
    } else if (widget.actions.isNotEmpty || useExpanding) {
      content = actionsContent;
    }
    if (useExpanding) {
      return content ?? const SizedBox.shrink();
    }
    return M3EToolbarBody(
      axis: widget.axis,
      gap: metrics.gap,
      leading: widget.leading,
      trailing: widget.trailing,
      content: content,
      mainAxisSize: _floating && !titles.hasTitle
          ? MainAxisSize.min
          : MainAxisSize.max,
      mainAxisAlignment: _floating
          ? MainAxisAlignment.center
          : MainAxisAlignment.spaceBetween,
      expandContent: !_floating || titles.hasTitle,
    );
  }

  Widget _composeBar({
    required Color background,
    required double elev,
    required ShapeBorder shape,
    required Widget contentBand,
    required M3EToolbarColorStyle style,
    required bool hasTitle,
  }) {
    Widget bar = Material(
      color: background,
      elevation: elev,
      shape: shape,
      clipBehavior: widget.clipBehavior,
      child: _floating
          ? contentBand
          : Padding(padding: _edgeSafeAreaInset(context), child: contentBand),
    );
    if (_hasFab) {
      bar = _withFab(bar, style);
    }
    if (_floating) {
      bar = Padding(padding: _floatingOuterPadding(context), child: bar);
      bar = Align(alignment: widget.alignment, child: bar);
    }
    bar = _wrapVisibility(bar);
    bar = FocusTraversalGroup(child: bar);
    bar = Semantics(
      container: true,
      explicitChildNodes: true,
      label: widget.semanticLabel,
      child: bar,
    );
    return bar;
  }
}
