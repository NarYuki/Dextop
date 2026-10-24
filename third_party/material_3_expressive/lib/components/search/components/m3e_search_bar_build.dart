part of '../m3e_search_bar.dart';

extension _M3ESearchBarContentBuild on _M3ESearchBarState {
  Widget _buildBarContent({
    required M3EThemeData theme,
    required M3ESearchBarTheme barTheme,
    required M3EColorScheme scheme,
    required Set<WidgetState> states,
    required TextDirection textDirection,
  }) {
    final _BarResolvedStyles styles = _resolveBarStyles(
      theme: theme,
      barTheme: barTheme,
      scheme: scheme,
      states: states,
    );
    final M3EIconButtonTheme iconButtonTheme = theme.iconButtonTheme;
    final List<Widget> trailingSource = _trailingSource();
    assert(
      (widget.trailing?.length ?? 0) <= (widget.avatar == null ? 2 : 1),
      'M3ESearchBar: use at most two trailing icons, or one with an avatar.',
    );
    final double actionIconSize = _resolveActionIconSize(
      iconButtonTheme: iconButtonTheme,
      barTheme: barTheme,
      trailing: trailingSource,
      leading: widget.leading,
    );
    final bool hasTrailing = trailingSource.isNotEmpty || widget.avatar != null;
    return _buildBarShell(
      styles: styles,
      textDirection: textDirection,
      barTheme: barTheme,
      scheme: scheme,
      actionIconSize: actionIconSize,
      trailingSource: trailingSource,
      input: M3ESearchBarInput(
        controller: _controller,
        focusNode: _focusNode,
        hintText: widget.hintText,
        hintAlignment: widget.alignment,
        enabled: widget.enabled,
        readOnly: widget.readOnly,
        autoFocus: widget.autoFocus,
        onTap: _handleTap,
        onTapOutside:
            widget.onTapOutside ?? M3EFocus.tapOutsideHandler(_focusNode),
        onChanged: widget.onChanged,
        onSubmitted: widget.onSubmitted,
        onEscape: widget.onEscape,
        textStyle: styles.textStyle,
        hintStyle: styles.hintStyle,
        cursorColor: barTheme.cursorColor(scheme),
        selectionColor: barTheme.selectionColor(scheme),
        textCapitalization: styles.textCapitalization,
        textInputAction: widget.textInputAction,
        keyboardType: widget.keyboardType,
        scrollPadding: widget.scrollPadding,
        contextMenuBuilder: widget.contextMenuBuilder,
        smartDashesType: widget.smartDashesType,
        smartQuotesType: widget.smartQuotesType,
        contentPadding: barTheme.labelPadding(
          hasLeading: widget.leading != null,
          hasTrailing: hasTrailing,
        ),
      ),
      idleHintStyle: styles.hintStyle,
    );
  }

  bool get _clearVisible =>
      widget.showClearButton &&
      widget.enabled &&
      !widget.readOnly &&
      _controller.text.isNotEmpty;

  /// Trailing actions before the avatar; clear (X) leads while visible.
  List<Widget> _trailingSource() {
    return <Widget>[
      if (_clearVisible)
        M3EIconButton(
          variant: M3EIconButtonVariant.standard,
          icon: const Icon(M3EIcons.close),
          tooltip: M3ESearchConstants.clearButtonTooltip,
          onPressed: _controller.clear,
        ),
      ...?widget.trailing,
    ];
  }

  Widget _buildBarShell({
    required _BarResolvedStyles styles,
    required TextDirection textDirection,
    required M3ESearchBarTheme barTheme,
    required M3EColorScheme scheme,
    required double actionIconSize,
    required List<Widget> trailingSource,
    required Widget input,
    required TextStyle idleHintStyle,
  }) {
    final Widget? leading = _buildLeading(
      barTheme: barTheme,
      scheme: scheme,
      actionIconSize: actionIconSize,
      compact: false,
    );
    final List<Widget> trailing = _buildTrailing(
      barTheme: barTheme,
      scheme: scheme,
      source: trailingSource,
      actionIconSize: actionIconSize,
      compact: false,
    );

    // Keep [editingRow]/input) mounted in a stable slot. Swapping idle chrome for
    // the editing row on focus remounts [EditableText], drops its text-input
    // client, and can make Tab appear to skip the bar.
    final bool idleGrouped = _groupsIdleContent(textDirection);
    final Widget editingRow = _buildEditingRow(
      textDirection: textDirection,
      // While idle chrome covers the row, keep actions out of Tab order so the
      // only stop is the search field.
      leading: leading == null
          ? null
          : (idleGrouped ? ExcludeFocus(child: leading) : leading),
      trailing: idleGrouped
          ? trailing
                .map((Widget action) => ExcludeFocus(child: action))
                .toList()
          : trailing,
      input: input,
    );

    final Widget content = _buildBarStackContent(
      styles: styles,
      textDirection: textDirection,
      barTheme: barTheme,
      scheme: scheme,
      actionIconSize: actionIconSize,
      trailingSource: trailingSource,
      idleHintStyle: idleHintStyle,
      editingRow: editingRow,
      idleGrouped: idleGrouped,
    );

    // Ring hugs the bar itself, so it tracks the expand-on-focus inset.
    final Widget ring = M3EFocusRing(
      focused: _showFocusRing,
      radius: _barFocusRingRadius(styles.shape, barTheme),
      color: widget.focusIndicatorColor ?? barTheme.focusIndicatorColor(scheme),
      width: barTheme.focusIndicatorThickness,
      gap: barTheme.focusIndicatorOffset,
      child: _buildBarMaterial(
        styles: styles,
        barTheme: barTheme,
        content: content,
      ),
    );
    _surface = M3ESearchAnchorScope.maybeOf(context);
    if (_surface == null) {
      return ring;
    }
    return Builder(
      builder: (BuildContext surfaceContext) {
        _surfaceContext = surfaceContext;
        _surface!.context = surfaceContext;
        return ring;
      },
    );
  }

  Widget _buildBarStackContent({
    required _BarResolvedStyles styles,
    required TextDirection textDirection,
    required M3ESearchBarTheme barTheme,
    required M3EColorScheme scheme,
    required double actionIconSize,
    required List<Widget> trailingSource,
    required TextStyle idleHintStyle,
    required Widget editingRow,
    required bool idleGrouped,
  }) {
    return Stack(
      fit: StackFit.passthrough,
      alignment: Alignment.center,
      children: <Widget>[
        editingRow,
        if (idleGrouped)
          Positioned.fill(
            child: ExcludeFocus(
              child: IgnorePointer(
                child: ColoredBox(
                  color: styles.background,
                  child: _buildIdleGroupedContent(
                    textDirection: textDirection,
                    barTheme: barTheme,
                    scheme: scheme,
                    actionIconSize: actionIconSize,
                    trailingSource: trailingSource,
                    idleHintStyle: idleHintStyle,
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }

  MouseCursor _barCursor() {
    if (!widget.enabled) {
      return SystemMouseCursors.basic;
    }
    return widget.readOnly ? SystemMouseCursors.click : SystemMouseCursors.text;
  }

  Widget _buildBarMaterial({
    required _BarResolvedStyles styles,
    required M3ESearchBarTheme barTheme,
    required Widget content,
  }) {
    return Opacity(
      opacity: widget.enabled ? 1 : M3ESearchConstants.disabledOpacity,
      child: Material(
        elevation: styles.elevation,
        shadowColor: styles.shadow,
        color: styles.background,
        surfaceTintColor: styles.surfaceTint,
        shape: styles.shape.copyWith(side: styles.side),
        clipBehavior: Clip.antiAlias,
        child: IgnorePointer(
          ignoring: !widget.enabled,
          child: Material(
            type: MaterialType.transparency,
            child: InkWell(
              onTap: _handleTap,
              canRequestFocus: false,
              mouseCursor: _barCursor(),
              splashFactory: barTheme.splashFactory,
              overlayColor: styles.overlay == null
                  ? null
                  : WidgetStatePropertyAll<Color?>(styles.overlay),
              customBorder: styles.shape.copyWith(side: styles.side),
              statesController: _statesController,
              child: Padding(
                padding: styles.padding,
                child: Semantics(
                  textField: true,
                  label: widget.hintText,
                  child: content,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// Ring radius for the bar shape; stadium falls back to the pill radius.
  BorderRadius _barFocusRingRadius(
    OutlinedBorder shape,
    M3ESearchBarTheme barTheme,
  ) {
    if (shape is RoundedRectangleBorder) {
      return shape.borderRadius.resolve(Directionality.maybeOf(context));
    }
    final double height = barTheme
        .constraints(override: widget.constraints)
        .minHeight;
    return BorderRadius.circular(height / 2);
  }

  Widget _buildIdleGroupedContent({
    required TextDirection textDirection,
    required M3ESearchBarTheme barTheme,
    required M3EColorScheme scheme,
    required double actionIconSize,
    required List<Widget> trailingSource,
    required TextStyle idleHintStyle,
  }) {
    // Compact leading/trailing (no tap-target slot) so optical center matches
    // geometric center. Keep the row shrink-wrapped so Align can center it.
    final Widget? leading = _buildLeading(
      barTheme: barTheme,
      scheme: scheme,
      actionIconSize: actionIconSize,
      compact: true,
    );
    final List<Widget> trailing = _buildTrailing(
      barTheme: barTheme,
      scheme: scheme,
      source: trailingSource,
      actionIconSize: actionIconSize,
      compact: true,
    );
    // Same glyph → label distance as the slotted layout.
    final double slotInset = (barTheme.tapTargetSize - actionIconSize) / 2;
    final double leadingGap = leading != null
        ? slotInset + barTheme.iconLabelGap
        : 0;
    final double trailingGap = trailing.isNotEmpty
        ? slotInset + barTheme.trailingActionsLeadingSpace
        : 0;

    return Align(
      alignment: widget.alignment,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        textDirection: textDirection,
        children: <Widget>[
          ?leading,
          if (widget.hintText != null) ...<Widget>[
            if (leadingGap > 0) SizedBox(width: leadingGap),
            Flexible(
              child: Padding(
                padding: leading == null
                    ? EdgeInsetsDirectional.only(
                        start:
                            barTheme.noActionsLeadingSpace -
                            barTheme.leadingSpace,
                      )
                    : EdgeInsets.zero,
                child: Text(
                  widget.hintText!,
                  style: idleHintStyle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
            if (trailingGap > 0) SizedBox(width: trailingGap),
          ],
          ...trailing,
        ],
      ),
    );
  }

  Widget _buildEditingRow({
    required TextDirection textDirection,
    required Widget? leading,
    required List<Widget> trailing,
    required Widget input,
  }) {
    return Row(
      textDirection: textDirection,
      children: <Widget>[
        ?leading,
        Expanded(child: input),
        ...trailing,
      ],
    );
  }

  _BarResolvedStyles _resolveBarStyles({
    required M3EThemeData theme,
    required M3ESearchBarTheme barTheme,
    required M3EColorScheme scheme,
    required Set<WidgetState> states,
  }) {
    return _BarResolvedStyles(
      elevation: barTheme.resolveElevation(
        states: states,
        widgetValue: widget.elevation,
      ),
      background: barTheme.resolveBackground(
        scheme: scheme,
        states: states,
        widgetValue: widget.backgroundColor,
      ),
      shadow: barTheme.resolveShadowColor(
        scheme: scheme,
        states: states,
        widgetValue: widget.shadowColor,
      ),
      surfaceTint: barTheme.resolveSurfaceTint(
        scheme: scheme,
        states: states,
        widgetValue: widget.surfaceTintColor,
      ),
      overlay: barTheme.resolveOverlay(
        scheme: scheme,
        states: states,
        widgetValue: widget.overlayColor,
      ),
      padding: widget.padding?.resolve(states) ?? barTheme.padding(),
      shape:
          widget.shape?.resolve(states) ?? barTheme.shape() as OutlinedBorder,
      side: widget.side?.resolve(states),
      textStyle: barTheme.resolveTextStyle(
        theme: theme,
        states: states,
        widgetValue: widget.textStyle,
      ),
      hintStyle: barTheme.resolveHintStyle(
        theme: theme,
        states: states,
        widgetValue: widget.hintStyle,
        textStyleOverride: widget.textStyle,
      ),
      textCapitalization: widget.textCapitalization ?? TextCapitalization.none,
    );
  }

  Widget? _buildLeading({
    required M3ESearchBarTheme barTheme,
    required M3EColorScheme scheme,
    required double actionIconSize,
    required bool compact,
  }) {
    final Widget? source = widget.leading;
    if (source == null) {
      return null;
    }
    final Widget child = source is M3EIconButton
        ? source
        : IconTheme.merge(
            data: IconThemeData(
              color: barTheme.leadingIconColor(scheme),
              size: actionIconSize,
            ),
            // A plain icon is decorative (non-functional search icon).
            child: source is Icon ? ExcludeSemantics(child: source) : source,
          );
    if (compact) {
      return child;
    }
    return _wrapActionSlot(width: _slotWidth(source, barTheme), child: child);
  }

  List<Widget> _buildTrailing({
    required M3ESearchBarTheme barTheme,
    required M3EColorScheme scheme,
    required List<Widget> source,
    required double actionIconSize,
    required bool compact,
  }) {
    final actions = <Widget>[];
    for (final action in source) {
      if (actions.isNotEmpty && !compact && barTheme.trailingActionsGap > 0) {
        actions.add(SizedBox(width: barTheme.trailingActionsGap));
      }
      final Widget child = action is M3EIconButton
          ? action
          : IconTheme.merge(
              data: IconThemeData(
                color: barTheme.trailingIconColor(scheme),
                size: actionIconSize,
              ),
              child: action,
            );
      actions.add(
        compact
            ? child
            : _wrapActionSlot(
                width: _slotWidth(action, barTheme),
                child: child,
              ),
      );
    }
    final avatar = widget.avatar;
    if (avatar != null) {
      if (actions.isNotEmpty && !compact && barTheme.trailingActionsGap > 0) {
        actions.add(SizedBox(width: barTheme.trailingActionsGap));
      }
      final Widget clipped = SizedBox.square(
        dimension: barTheme.avatarSize,
        child: ClipPath(
          clipper: ShapeBorderClipper(
            shape: barTheme.avatarShape,
            textDirection: Directionality.maybeOf(context),
          ),
          child: avatar,
        ),
      );
      actions.add(
        compact
            ? clipped
            : _wrapActionSlot(width: barTheme.avatarTargetSize, child: clipped),
      );
    }
    return actions;
  }

  double _slotWidth(Widget action, M3ESearchBarTheme barTheme) {
    if (action is M3EIconButton) {
      return M3ETheme.of(context).iconButtonTheme
          .target(action.size, action.width)
          .width;
    }
    return barTheme.tapTargetSize;
  }
}

class _BarResolvedStyles {
  const _BarResolvedStyles({
    required this.elevation,
    required this.background,
    required this.shadow,
    required this.surfaceTint,
    required this.overlay,
    required this.padding,
    required this.shape,
    required this.side,
    required this.textStyle,
    required this.hintStyle,
    required this.textCapitalization,
  });

  final double elevation;
  final Color background;
  final Color shadow;
  final Color surfaceTint;
  final Color? overlay;
  final EdgeInsetsGeometry padding;
  final OutlinedBorder shape;
  final BorderSide? side;
  final TextStyle textStyle;
  final TextStyle hintStyle;
  final TextCapitalization textCapitalization;
}
