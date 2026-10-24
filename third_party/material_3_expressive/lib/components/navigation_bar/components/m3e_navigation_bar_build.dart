part of '../m3e_navigation_bar.dart';

/// Resolved layout metrics for one destination band, computed once per
/// build and threaded through the destination slots.
typedef _NavBarBandMetrics = ({
  bool wide,
  TextStyle label,
  double slot,
  ({double top, double bottom}) pad,
  double indicatorHeight,
  double radius,
  double height,
  EdgeInsetsGeometry edgeInset,
  int maxLines,
  TextOverflow overflow,
});

/// Widget-building helpers for `_M3ENavigationBarState`, split out to keep
/// the state class under the component length guidelines.
extension _M3ENavigationBarBuild on _M3ENavigationBarState {
  M3ENavBarLayout _resolveLayout(double maxWidth, M3ENavigationBarTheme theme) {
    if (!widget.autoLayout) {
      return widget.layout;
    }
    final double breakpoint = widget.wideBreakpoint ?? theme.wideBreakpoint;
    return maxWidth >= breakpoint
        ? M3ENavBarLayout.wide
        : M3ENavBarLayout.compact;
  }

  MainAxisAlignment _wideMainAxisAlignment() {
    return switch (widget.alignment) {
      M3ENavBarAlignment.start => MainAxisAlignment.start,
      M3ENavBarAlignment.center => MainAxisAlignment.center,
      M3ENavBarAlignment.end => MainAxisAlignment.end,
    };
  }

  bool _showsIcon(M3ENavigationBarDestination destination, bool selected) {
    if (!destination.hasIcon) {
      return false;
    }
    return switch (widget.iconBehavior) {
      M3ENavBarIconBehavior.alwaysShow => true,
      M3ENavBarIconBehavior.onlySelected => selected,
      M3ENavBarIconBehavior.alwaysHide => false,
    };
  }

  bool _showsLabel(M3ENavigationBarDestination destination, bool selected) {
    if (!destination.hasLabel) {
      return false;
    }
    return switch (widget.labelBehavior) {
      M3ENavBarLabelBehavior.alwaysShow => true,
      M3ENavBarLabelBehavior.onlySelected => selected,
      M3ENavBarLabelBehavior.alwaysHide => false,
    };
  }

  double _textWidth(String text, TextStyle style, TextScaler scaler) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textScaler: scaler,
      maxLines: 1,
      textDirection: Directionality.of(context),
    )..layout();
    final double width = painter.width;
    painter.dispose();
    return width;
  }

  double _textHeight(
    String text,
    TextStyle style,
    TextScaler scaler,
    int maxLines,
    double maxWidth,
  ) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textScaler: scaler,
      maxLines: maxLines,
      textDirection: Directionality.of(context),
    )..layout(maxWidth: math.max(1, maxWidth));
    final double height = painter.height;
    painter.dispose();
    return height;
  }

  double _slotWidth(
    M3ENavigationBarTheme theme,
    TextStyle style,
    TextScaler scaler,
  ) {
    final double? forced = widget.wideDestinationWidth;
    if (forced != null) {
      return forced;
    }
    var widest = 0.0;
    for (final M3ENavigationBarDestination destination in widget.destinations) {
      double widthFor({required bool icon, required bool label}) {
        var width = theme.horizontalIndicatorInset * 2;
        if (icon) {
          width += theme.iconSize;
        }
        if (icon && label) {
          width += theme.horizontalIconLabelGap;
        }
        if (label) {
          width += _textWidth(destination.label!, style, scaler);
        }
        return width;
      }

      widest = math.max(
        widest,
        math.max(
          widthFor(
            icon: _showsIcon(destination, true),
            label: _showsLabel(destination, true),
          ),
          widthFor(
            icon: _showsIcon(destination, false),
            label: _showsLabel(destination, false),
          ),
        ),
      );
    }
    return widest;
  }

  double _labelBlock({
    required bool wide,
    required double cellWidth,
    required M3ENavigationBarTheme theme,
    required TextStyle style,
    required TextScaler scaler,
    required int maxLines,
  }) {
    var tallest = 0.0;
    for (final M3ENavigationBarDestination destination in widget.destinations) {
      if (!_showsLabel(destination, true) && !_showsLabel(destination, false)) {
        continue;
      }
      final bool icon = _showsIcon(destination, true);
      final double maxWidth = wide
          ? cellWidth -
                theme.horizontalIndicatorInset * 2 -
                (icon ? theme.iconSize + theme.horizontalIconLabelGap : 0)
          : cellWidth;
      tallest = math.max(
        tallest,
        _textHeight(destination.label!, style, scaler, maxLines, maxWidth),
      );
    }
    return tallest;
  }

  ({double top, double bottom}) _bandPadding(
    M3ENavigationBarTheme theme,
    bool wide,
  ) {
    final double extra = widget.size == M3ENavBarSize.medium
        ? theme.baselineExtraPadding
        : 0;
    var top =
        (wide ? theme.horizontalPadding : theme.verticalPaddingTop) + extra;
    var bottom =
        (wide ? theme.horizontalPadding : theme.verticalPaddingBottom) + extra;
    if (widget.density == M3ENavBarDensity.compact) {
      final double cut = theme.compactHeightReduction / 2;
      top = math.max(0, top - cut);
      bottom = math.max(0, bottom - cut);
    }
    return (top: top, bottom: bottom);
  }

  Widget _buildNavigationBar(BuildContext context) {
    final M3EThemeData m3e = M3ETheme.of(context);
    final M3ENavigationBarTheme theme = m3e.navigationBarTheme;
    final M3EColorScheme scheme = m3e.colorScheme;
    final M3ENavMetrics metrics = theme.metrics(widget.density, m3e.spacing);
    final Color background =
        widget.backgroundColor ?? theme.containerColor(scheme);
    final ShapeBorder shape = theme.containerShape(widget.shapeFamily);
    final double bottomInset = widget.safeArea
        ? M3ESafeArea.bottomOf(context)
        : 0.0;
    final Color indicator =
        widget.indicatorColor ?? theme.indicatorColor(scheme);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final bool shown = _shown(context);

    Widget nav = Material(
      color: background,
      elevation: widget.elevation ?? theme.elevation,
      shadowColor: theme.shadowColor(scheme),
      surfaceTintColor: const Color(0x00000000),
      shape: shape,
      child: Padding(
        padding: EdgeInsets.only(bottom: bottomInset),
        child: LayoutBuilder(
          builder: (BuildContext context, BoxConstraints constraints) {
            return _buildBand(
              context,
              m3e: m3e,
              theme: theme,
              scheme: scheme,
              metrics: metrics,
              indicator: indicator,
              maxWidth: constraints.maxWidth,
            );
          },
        ),
      ),
    );
    nav = Padding(padding: widget.padding ?? EdgeInsets.zero, child: nav);
    nav = CallbackShortcuts(
      bindings: <ShortcutActivator, VoidCallback>{
        const SingleActivator(LogicalKeyboardKey.arrowLeft): () =>
            _move(rtl ? 1 : -1),
        const SingleActivator(LogicalKeyboardKey.arrowRight): () =>
            _move(rtl ? -1 : 1),
      },
      child: ClipRect(
        child: AnimatedAlign(
          alignment: Alignment.bottomCenter,
          heightFactor: shown ? 1 : 0,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOutCubic,
          child: nav,
        ),
      ),
    );
    nav = ExcludeSemantics(excluding: !shown, child: nav);
    if (widget.semanticLabel != null) {
      nav = Semantics(container: true, label: widget.semanticLabel, child: nav);
    }
    return nav;
  }

  /// Resolves once-per-layout band metrics (wide/compact, label style,
  /// slot width, padding, indicator size, and outer height/inset).
  _NavBarBandMetrics _resolveBandMetrics(
    BuildContext context, {
    required M3EThemeData m3e,
    required M3ENavigationBarTheme theme,
    required double maxWidth,
  }) {
    final wide = _resolveLayout(maxWidth, theme) == M3ENavBarLayout.wide;
    final TextScaler scaler = MediaQuery.textScalerOf(context);
    final double scale = theme.labelFontSize == 0
        ? 1
        : scaler.scale(theme.labelFontSize) / theme.labelFontSize;
    final int maxLines = scale <= 1
        ? theme.restingLabelMaxLines
        : theme.scaledLabelMaxLines;
    final TextOverflow overflow = scale > theme.truncationTextScale
        ? TextOverflow.ellipsis
        : TextOverflow.clip;
    final TextStyle measure = theme.labelStyle(m3e.typeScale, selected: true);
    final TextStyle label = theme.labelStyle(m3e.typeScale);
    final double slot = _slotWidth(theme, measure, scaler);
    final double cell = wide
        ? slot
        : maxWidth / math.max(1, widget.destinations.length);
    final double labelHeight = _labelBlock(
      wide: wide,
      cellWidth: cell,
      theme: theme,
      style: measure,
      scaler: scaler,
      maxLines: maxLines,
    );
    final ({double top, double bottom}) pad = _bandPadding(theme, wide);
    final double indicatorHeight = wide
        ? math.max(theme.horizontalIndicatorHeight, labelHeight)
        : theme.verticalIndicatorHeight;
    final double natural = wide
        ? pad.top + indicatorHeight + pad.bottom
        : pad.top +
              theme.verticalIndicatorHeight +
              (labelHeight > 0 ? theme.verticalIconLabelGap : 0) +
              labelHeight +
              pad.bottom;
    var minHeight = widget.size == M3ENavBarSize.small
        ? theme.heightSmall
        : theme.heightMedium;
    if (widget.density == M3ENavBarDensity.compact) {
      minHeight -= theme.compactHeightReduction;
    }
    final double height = math.max(minHeight, natural);
    final double radius = wide
        ? indicatorHeight / 2
        : theme.verticalIndicatorRadius;
    final EdgeInsetsGeometry edgeInset = !wide
        ? EdgeInsets.zero
        : switch (widget.alignment) {
            M3ENavBarAlignment.start => EdgeInsetsDirectional.only(
              start: theme.wideEdgePadding,
            ),
            M3ENavBarAlignment.end => EdgeInsetsDirectional.only(
              end: theme.wideEdgePadding,
            ),
            M3ENavBarAlignment.center => EdgeInsets.zero,
          };
    return (
      wide: wide,
      label: label,
      slot: slot,
      pad: pad,
      indicatorHeight: indicatorHeight,
      radius: radius,
      height: height,
      edgeInset: edgeInset,
      maxLines: maxLines,
      overflow: overflow,
    );
  }

  Widget _buildBand(
    BuildContext context, {
    required M3EThemeData m3e,
    required M3ENavigationBarTheme theme,
    required M3EColorScheme scheme,
    required M3ENavMetrics metrics,
    required Color indicator,
    required double maxWidth,
  }) {
    final _NavBarBandMetrics band = _resolveBandMetrics(
      context,
      m3e: m3e,
      theme: theme,
      maxWidth: maxWidth,
    );
    return SizedBox(
      height: band.height,
      width: double.infinity,
      child: Padding(
        padding: band.edgeInset,
        child: Row(
          spacing: band.wide ? theme.wideItemGap : theme.itemGap,
          mainAxisAlignment: band.wide
              ? _wideMainAxisAlignment()
              : MainAxisAlignment.start,
          children: _destinationSlots(
            theme: theme,
            scheme: scheme,
            metrics: metrics,
            indicator: indicator,
            band: band,
          ),
        ),
      ),
    );
  }

  List<Widget> _destinationSlots({
    required M3ENavigationBarTheme theme,
    required M3EColorScheme scheme,
    required M3ENavMetrics metrics,
    required Color indicator,
    required _NavBarBandMetrics band,
  }) {
    return <Widget>[
      for (int i = 0; i < widget.destinations.length; i++)
        if (band.wide)
          SizedBox(
            width: band.slot,
            child: _button(
              index: i,
              theme: theme,
              scheme: scheme,
              metrics: metrics,
              indicator: indicator,
              label: band.label,
              wide: true,
              slot: band.slot,
              indicatorHeight: band.indicatorHeight,
              radius: band.radius,
              pad: band.pad,
              maxLines: band.maxLines,
              overflow: band.overflow,
            ),
          )
        else
          Expanded(
            child: _button(
              index: i,
              theme: theme,
              scheme: scheme,
              metrics: metrics,
              indicator: indicator,
              label: band.label,
              wide: false,
              slot: band.slot,
              indicatorHeight: band.indicatorHeight,
              radius: band.radius,
              pad: band.pad,
              maxLines: band.maxLines,
              overflow: band.overflow,
            ),
          ),
    ];
  }

  Widget _button({
    required int index,
    required M3ENavigationBarTheme theme,
    required M3EColorScheme scheme,
    required M3ENavMetrics metrics,
    required Color indicator,
    required TextStyle label,
    required bool wide,
    required double slot,
    required double indicatorHeight,
    required double radius,
    required ({double top, double bottom}) pad,
    required int maxLines,
    required TextOverflow overflow,
  }) {
    return M3ENavBarDestinationButton(
      destination: widget.destinations[index],
      selected: index == _selected,
      selectedColor: theme.selectedColor(scheme),
      activeLabelColor: theme.activeLabelColor(scheme),
      unselectedColor: theme.unselectedColor(scheme),
      labelStyle: label,
      iconSize: metrics.iconSize,
      labelBehavior: widget.labelBehavior,
      iconBehavior: widget.iconBehavior,
      layout: wide ? M3ENavBarLayout.wide : M3ENavBarLayout.compact,
      indicatorStyle: widget.indicatorStyle,
      indicatorWidth: theme.verticalIndicatorWidth,
      indicatorHeight: indicatorHeight,
      indicatorRadius: radius,
      contentPadding: EdgeInsets.only(top: pad.top, bottom: pad.bottom),
      iconLabelGap: wide
          ? theme.horizontalIconLabelGap
          : theme.verticalIconLabelGap,
      labelMaxLines: maxLines,
      labelOverflow: overflow,
      wideDestinationWidth: wide ? slot : null,
      horizontalInset: wide ? theme.horizontalIndicatorInset : 0,
      underlineThickness: metrics.indicatorThickness,
      underlineColor: indicator,
      indicatorColor: indicator,
      focusNode: _nodes[index],
      skipTraversal: !_tabStop(index),
      onTap: () => widget.onDestinationSelected?.call(index),
    );
  }
}
