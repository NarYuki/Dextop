part of 'm3e_expandable_item.dart';

/// Body measurement / viewport helpers for [_M3EExpandableItemState].
extension _M3EExpandableItemBody on _M3EExpandableItemState {
  Widget _buildExpandableBody(
    M3EExpandableStyle d,
    double progress, {
    required bool isEntirelyTappable,
  }) {
    final expandableTheme = M3ETheme.of(context).listTheme.expandable;
    final effectivePadding = d.bodyPadding ?? expandableTheme.bodyPadding;
    final resolvedPadding = effectivePadding.resolve(
      Directionality.of(context),
    );
    final contentShift = math.min<double>(12, resolvedPadding.bottom * 0.6 + 4);
    final bodyHeight = _computeBodyHeight(effectivePadding, progress);
    final translationY = -(1.0 - progress.clamp(0.0, 1.0)) * contentShift;
    final needsMeasurement =
        _collapsedHeight == null || _expandedHeight == null;

    return Stack(
      children: [
        if (needsMeasurement) _buildMeasurementOverlay(effectivePadding),
        if (bodyHeight > 0)
          _buildBodyViewport(
            d,
            progress,
            effectivePadding: effectivePadding,
            bodyHeight: bodyHeight,
            translationY: translationY,
            isEntirelyTappable: isEntirelyTappable,
          )
        else
          const SizedBox.shrink(),
      ],
    );
  }

  double _computeBodyHeight(
    EdgeInsetsGeometry effectivePadding,
    double progress,
  ) {
    final contentCollapsed = _collapsedHeight ?? 0.0;
    final contentExpanded = _expandedHeight ?? 200.0;
    final paddingVertical = effectivePadding.vertical;
    final totalCollapsed = contentCollapsed > 0
        ? contentCollapsed + paddingVertical
        : 0.0;
    final totalExpanded = contentExpanded > 0
        ? contentExpanded + paddingVertical
        : 0.0;
    return math.max<double>(
      0,
      totalCollapsed + (totalExpanded - totalCollapsed) * progress,
    );
  }

  Widget _buildMeasurementOverlay(EdgeInsetsGeometry effectivePadding) {
    return Positioned(
      left: 0,
      right: 0,
      top: 0,
      child: ExcludeFocus(
        child: Offstage(
          child: Padding(
            padding: effectivePadding,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (_collapsedHeight == null)
                  M3EMeasureSize(
                    onChange: (size) =>
                        setState(() => _collapsedHeight = size.height),
                    child: widget.bodyBuilder(context, widget.index, 0),
                  ),
                if (_expandedHeight == null)
                  M3EMeasureSize(
                    onChange: (size) =>
                        setState(() => _expandedHeight = size.height),
                    child: widget.bodyBuilder(context, widget.index, 1),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBodyViewport(
    M3EExpandableStyle d,
    double progress, {
    required EdgeInsetsGeometry effectivePadding,
    required double bodyHeight,
    required double translationY,
    required bool isEntirelyTappable,
  }) {
    // Follow the target state, not the spring: the spring settles toward 1
    // slowly, which would keep the body out of focus traversal meanwhile.
    return ExcludeFocus(
      excluding: !widget.isExpanded,
      child: SizedBox(
        height: bodyHeight,
        child: SingleChildScrollView(
          physics: const NeverScrollableScrollPhysics(),
          padding: effectivePadding,
          child: SizedBox(
            width: double.infinity,
            child: Builder(
              builder: (context) => _buildBodyInteractiveContent(
                d,
                progress,
                translationY: translationY,
                isEntirelyTappable: isEntirelyTappable,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBodyInteractiveContent(
    M3EExpandableStyle d,
    double progress, {
    required double translationY,
    required bool isEntirelyTappable,
  }) {
    final isExpanded = progress > 0.5;
    final canTapBody =
        (isExpanded && d.tapBodyToCollapse) ||
        (!isExpanded && d.tapBodyToExpand);
    final tapCallback =
        (!isEntirelyTappable && canTapBody && !d.tapIconToToggle)
        ? _primaryToggle
        : null;
    final bodyTooltip = tapCallback != null
        ? (isExpanded ? d.collapseTooltip : d.expandTooltip)
        : null;

    return _buildInteractionWrapper(
      d,
      onTap: tapCallback,
      tooltip: bodyTooltip,
      child: Align(
        alignment: d.bodyAlignment,
        child: Transform.translate(
          offset: Offset(0, translationY),
          child: widget.bodyBuilder(context, widget.index, progress),
        ),
      ),
    );
  }
}

/// Header tap resolution and card assembly for [_M3EExpandableItemState].
extension _M3EExpandableItemHeader on _M3EExpandableItemState {
  VoidCallback get _primaryToggle {
    if (widget.expanded?.isTransform ?? false) {
      return widget.onTransform ?? widget.onToggle;
    }
    return widget.onToggle;
  }

  VoidCallback? _selectionDoubleTap() {
    final M3EListFeatureScope? features = M3EListFeatureScope.maybeOf(context);
    if (features == null ||
        !features.selectionEnabled ||
        features.selectionState.trigger != M3EListSelectionTrigger.doubleTap) {
      return null;
    }
    return () => features.onToggleSelection(widget.index);
  }

  bool _separateLeadingSelect(M3EListFeatureScope? features, bool selectionOn) {
    // Leading flip owns its own InkWell — keep expand off the card surface so
    // icon tap-to-select cannot also toggle expand/collapse.
    return selectionOn &&
        features!.selectionState.hasSelectedIcon &&
        features.selectionState.trigger == M3EListSelectionTrigger.icon;
  }

  bool _canTapBody(M3EExpandableStyle d) {
    return (widget.isExpanded && d.tapBodyToCollapse) ||
        (!widget.isExpanded && d.tapBodyToExpand);
  }

  VoidCallback? _rawHeaderOrOuterCallback({
    required bool selectionOn,
    required M3EListFeatureScope? features,
    required bool entireCardTappable,
    required bool expandOnHeader,
    required bool pressForSelection,
    required bool separateLeadingSelect,
  }) {
    if (!(entireCardTappable ||
        expandOnHeader ||
        pressForSelection ||
        separateLeadingSelect)) {
      return null;
    }
    return () {
      if (selectionOn && (features?.controller?.isSelectionMode ?? false)) {
        features!.onToggleSelection(widget.index);
        return;
      }
      if (entireCardTappable || expandOnHeader || separateLeadingSelect) {
        _primaryToggle();
      }
    };
  }

  _HeaderInteraction _assembleHeaderInteraction({
    required bool separateLeadingSelect,
    required bool entireCardTappable,
    required bool hasList,
    required VoidCallback? rawHeaderOrOuter,
    required M3EExpandableStyle d,
  }) {
    // Leading selection owns its InkWell. Expand/collapse + filled splash stay
    // on the card (list expansions) or the full header row (content bodies).
    final bool cardOwnsExpand =
        entireCardTappable || (separateLeadingSelect && hasList);
    final bool headerOwnsExpand =
        rawHeaderOrOuter != null && !cardOwnsExpand && !d.tapIconToToggle;
    return (
      separateLeadingSelect: separateLeadingSelect,
      entireCardTappable: entireCardTappable,
      rawHeaderOrOuter: rawHeaderOrOuter,
      outerTap: cardOwnsExpand ? rawHeaderOrOuter : null,
      headerTap: headerOwnsExpand ? rawHeaderOrOuter : null,
      outerTooltip: cardOwnsExpand
          ? (widget.isExpanded ? d.collapseTooltip : d.expandTooltip)
          : null,
      doubleTap: _selectionDoubleTap(),
    );
  }

  _HeaderInteraction _resolveHeaderInteraction({
    required M3EExpandableStyle d,
    required bool hasList,
  }) {
    final M3EListFeatureScope? features = M3EListFeatureScope.maybeOf(context);
    final selectionOn = features?.selectionEnabled ?? false;
    final separateLeadingSelect = _separateLeadingSelect(features, selectionOn);
    final canTapHeader = d.tapHeaderToToggle;
    final entireCardTappable =
        !separateLeadingSelect &&
        !d.tapIconToToggle &&
        canTapHeader &&
        _canTapBody(d) &&
        !hasList;
    final expandOnHeader =
        !entireCardTappable && canTapHeader && !d.tapIconToToggle;
    final pressForSelection = selectionOn && canTapHeader;
    final rawHeaderOrOuter = _rawHeaderOrOuterCallback(
      selectionOn: selectionOn,
      features: features,
      entireCardTappable: entireCardTappable,
      expandOnHeader: expandOnHeader,
      pressForSelection: pressForSelection,
      separateLeadingSelect: separateLeadingSelect,
    );
    return _assembleHeaderInteraction(
      separateLeadingSelect: separateLeadingSelect,
      entireCardTappable: entireCardTappable,
      hasList: hasList,
      rawHeaderOrOuter: rawHeaderOrOuter,
      d: d,
    );
  }

  Widget _buildHeaderCard({
    required M3EColorScheme scheme,
    required M3EExpandableStyle d,
    required bool hasList,
    required _HeaderInteraction interaction,
  }) {
    return M3EListTapBinder(
      onTap: interaction.outerTap ?? interaction.headerTap,
      onDoubleTap: interaction.doubleTap,
      builder: (BuildContext context, VoidCallback? onPressed) {
        return _buildAnimatedContainer(
          scheme,
          d,
          interaction.outerTap != null ? onPressed : null,
          interaction.headerTap != null ? onPressed : null,
          interaction.outerTooltip,
          bodyInsideCard: !hasList,
        );
      },
    );
  }

  Widget _buildListExpansionColumn({
    required Widget headerCard,
    required M3EExpandableStyle d,
    required bool isLast,
  }) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        headerCard,
        AnimatedBuilder(
          animation: _expandCtrl,
          builder: (BuildContext context, Widget? _) {
            return M3EExpandableSublist(
              progress: _expandCtrl.value,
              expanded: widget.isExpanded,
              style: d,
              topGap: widget.expanded!.topGap,
              child: M3EListReorderExclude(
                // Block parent list selection/reorder from leaking into
                // nested lists; nested FeatureHosts still override this.
                child: M3EListFeatureScope(
                  selectionEnabled: false,
                  reorderEnabled: false,
                  selectionState: M3EListSelectionState.defaults,
                  reorderState: M3EListReorderState.defaults,
                  controller: null,
                  itemCount: 0,
                  onToggleSelection: (_) {},
                  child: M3EExpandableNestScope(
                    closeBottom: isLast || d.roundSublistBottom,
                    outerRadius: d.outerRadius,
                    rowIndex: widget.index,
                    surfaceColor: _expandedStateFill(
                      d,
                      M3ETheme.of(context).colorScheme,
                    ),
                    variant: widget.nestVariant,
                    child: widget.expanded!.child,
                  ),
                ),
              ),
            );
          },
        ),
      ],
    );
  }

  Widget _buildAnimatedContainer(
    M3EColorScheme scheme,
    M3EExpandableStyle d,
    VoidCallback? outerTap,
    VoidCallback? headerTap,
    String? outerTooltip, {
    required bool bodyInsideCard,
  }) {
    // Card-level press matches [M3ECardList]: pointer cursor + hover state layer.
    // List expansions are header-only cards, so header taps live on the card.
    final VoidCallback? cardPress =
        outerTap ?? (!bodyInsideCard ? headerTap : null);
    final cardHandlesTap = cardPress != null;
    final entirelyTappable = outerTap != null || cardHandlesTap;

    Widget content = _buildAnimatedCardColumn(
      d,
      headerTap: cardHandlesTap ? null : headerTap,
      bodyInsideCard: bodyInsideCard,
      isEntirelyTappable: entirelyTappable,
    );
    content = _wrapAnimatedContainerContent(
      d,
      content: content,
      cardHandlesTap: cardHandlesTap,
      outerTap: outerTap,
      outerTooltip: outerTooltip,
    );

    return TweenAnimationBuilder<BorderRadius?>(
      duration: const Duration(milliseconds: 40),
      curve: Curves.easeOut,
      tween: BorderRadiusTween(
        begin: _buildEffectiveRadius(),
        end: _buildEffectiveRadius(),
      ),
      builder: (context, animatedRadius, child) {
        return _buildRadiusTweenCard(
          scheme,
          d,
          animatedRadius: animatedRadius,
          cardPress: cardPress,
          cardHandlesTap: cardHandlesTap,
          child: child!,
        );
      },
      child: content,
    );
  }

  Widget _buildAnimatedCardColumn(
    M3EExpandableStyle d, {
    required VoidCallback? headerTap,
    required bool bodyInsideCard,
    required bool isEntirelyTappable,
  }) {
    return AnimatedBuilder(
      animation: _expandCtrl,
      builder: (context, child) {
        final progress = _expandCtrl.value;
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _buildHeader(
              d,
              progress,
              headerTap,
              isEntirelyTappable: isEntirelyTappable,
            ),
            if (bodyInsideCard)
              _buildExpandableBody(
                d,
                progress,
                isEntirelyTappable: isEntirelyTappable,
              ),
          ],
        );
      },
    );
  }

  Widget _wrapAnimatedContainerContent(
    M3EExpandableStyle d, {
    required Widget content,
    required bool cardHandlesTap,
    required VoidCallback? outerTap,
    required String? outerTooltip,
  }) {
    if (!cardHandlesTap) {
      return _buildInteractionWrapper(
        d,
        onTap: outerTap,
        tooltip: outerTooltip,
        focusNode: outerTap != null ? _toggleFocusNode : null,
        child: content,
      );
    }
    if (outerTooltip != null) {
      return M3ETooltip(message: outerTooltip, child: content);
    }
    return content;
  }

  Widget _buildRadiusTweenCard(
    M3EColorScheme scheme,
    M3EExpandableStyle d, {
    required BorderRadius? animatedRadius,
    required VoidCallback? cardPress,
    required bool cardHandlesTap,
    required Widget child,
  }) {
    final BorderRadius radius = animatedRadius ?? _buildEffectiveRadius();
    final Color? selectionFill = m3eSelectionFill(context, widget.index);
    final Widget card = MouseRegion(
      onEnter: (_) => _setHovered(true),
      onExit: (_) => _setHovered(false),
      child: AnimatedBuilder(
        animation: _expandCtrl,
        builder: (BuildContext context, Widget? child) {
          return M3EListRowSurface(
            index: widget.index,
            radius: radius,
            color: selectionFill ?? _expandedStateFill(d, scheme),
            border: d.border,
            elevation: d.elevation,
            hovered: _hovered,
            pressed: _isPressed,
            focused: _focused,
            dragged: M3EListDragProxyScope.maybeOf(context) != null,
            onTap: cardPress,
            onStateChanged: cardHandlesTap ? _handleCardStateChanged : null,
            mouseCursor: cardHandlesTap ? SystemMouseCursors.click : null,
            child: child!,
          );
        },
        child: child,
      ),
    );
    if (cardHandlesTap) {
      return card;
    }
    return M3EListFocusRing(
      focused: _focused,
      radius: radius,
      color: scheme.secondary,
      child: card,
    );
  }

  /// Row / sublist fill: the rest color, blended toward the expanded-state
  /// color with expand progress when [M3EExpandableStyle.expandedStateFill].
  Color _expandedStateFill(M3EExpandableStyle d, M3EColorScheme scheme) {
    final Color rest = d.color ?? scheme.surfaceContainerHighest;
    if (!d.expandedStateFill) {
      return rest;
    }
    final Color expanded = d.expandedStateColor ?? scheme.surfaceContainerHigh;
    return Color.lerp(rest, expanded, _expandCtrl.value.clamp(0.0, 1.0))!;
  }

  Widget _buildHeader(
    M3EExpandableStyle d,
    double progress,
    VoidCallback? onTap, {
    required bool isEntirelyTappable,
  }) {
    final expandableTheme = M3ETheme.of(context).listTheme.expandable;
    final EdgeInsetsGeometry headerPadding =
        d.headerPadding ?? expandableTheme.headerPadding;
    final double iconGap = M3ETheme.of(context).listTheme.item.gap;

    final Widget icon = _buildIcon(d, progress, _primaryToggle);
    final iconOnTrailing = d.iconPlacement != M3EExpandableIconPlacement.left;
    final Widget headerChild = M3EListItemScope(
      child: M3EListTrailingOverride(
        trailing: iconOnTrailing ? icon : null,
        child: widget.headerBuilder(context, widget.index, progress),
      ),
    );
    final Widget headerContent = iconOnTrailing
        ? headerChild
        : Padding(
            padding: headerPadding,
            child: Row(
              children: <Widget>[
                icon,
                SizedBox(width: iconGap),
                Expanded(
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: headerChild,
                  ),
                ),
              ],
            ),
          );

    final String? headerTooltip = (d.tapHeaderToToggle && !isEntirelyTappable)
        ? (widget.isExpanded ? d.collapseTooltip : d.expandTooltip)
        : null;

    return _buildInteractionWrapper(
      d,
      onTap: onTap,
      isHeader: true,
      semanticLabel: 'Item ${widget.index + 1} of ${widget.totalCount}',
      semanticHint: widget.isExpanded ? 'Collapse' : 'Expand',
      isExpanded: widget.isExpanded,
      tooltip: headerTooltip,
      focusNode: onTap != null ? _toggleFocusNode : null,
      child: headerContent,
    );
  }

  Widget _buildIcon(
    M3EExpandableStyle d,
    double progress,
    VoidCallback onToggle,
  ) {
    if (d.expandIcon == null && d.collapseIcon == null) {
      return const SizedBox.shrink();
    }

    final isExpanded = progress >= 0.5;
    final Widget? icon = isExpanded ? d.collapseIcon : d.expandIcon;

    if (icon == null) {
      return const SizedBox.shrink();
    }

    final angle = d.iconRotationAngle * progress;
    final String? tooltip = d.tapIconToToggle
        ? (isExpanded ? d.collapseTooltip : d.expandTooltip)
        : null;

    final Widget rotated = Transform.rotate(angle: angle, child: icon);
    final Widget iconWidget;
    if (d.expandedIconBackgroundSize > 0) {
      // Narrow icon-button pill: no fill at rest, filled when expanded.
      final double width = d.expandedIconBackgroundSize;
      final double height = d.expandedIconBackgroundHeight;
      final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
      final Color expandedFill =
          d.expandedIconBackground ?? scheme.surfaceContainer;
      final Color fill = Color.lerp(
        expandedFill.withValues(alpha: 0),
        expandedFill,
        progress.clamp(0.0, 1.0),
      )!;
      iconWidget = SizedBox(
        width: width,
        height: height,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: fill,
            borderRadius: BorderRadius.circular(math.min(width, height) / 2),
          ),
          child: Center(child: rotated),
        ),
      );
    } else {
      iconWidget = rotated;
    }

    if (d.tapIconToToggle) {
      final Widget button = _buildInteractionWrapper(
        d,
        onTap: onToggle,
        isHeader: true,
        isIcon: true,
        semanticLabel: isExpanded ? 'Collapse button' : 'Expand button',
        isExpanded: isExpanded,
        tooltip: tooltip,
        child: iconWidget,
      );
      if (M3EListKeyboardGroup.maybeOf(context) == null) {
        return button;
      }
      // Arrow-reachable row action instead of its own Tab stop.
      return M3EListKeyTarget(
        index: widget.index,
        slot: 2,
        onActivate: onToggle,
        child: ExcludeFocus(child: button),
      );
    }
    return ExcludeSemantics(child: iconWidget);
  }
}
