part of '../m3e_cards.dart';

/// Structured-content layout for [_M3ECardState]: media, headline, subhead,
/// supporting text, actions, overflow, and their dividers.
extension _M3ECardContent on _M3ECardState {
  Widget _buildContent(
    BuildContext context,
    M3EThemeData theme,
    M3ECardTheme cardTheme,
    M3ECardGroupScope? group,
  ) {
    final bool structured = _hasStructuredContent;
    if (!structured) {
      return widget.child ?? const SizedBox.shrink();
    }

    final EdgeInsets pad = (widget.padding ?? cardTheme.contentPadding).resolve(
      Directionality.of(context),
    );
    final bool vertical =
        widget.vertical ?? group?.vertical ?? widget.media == null;
    return _arranged(theme, cardTheme, pad, vertical: vertical);
  }

  bool get _hasStructuredContent =>
      widget.media != null ||
      widget.headline != null ||
      widget.subhead != null ||
      widget.supportingText != null ||
      widget.actions != null ||
      widget.overflow != null ||
      widget.dividerAfterMedia ||
      widget.dividerAfterText;

  Widget _arranged(
    M3EThemeData theme,
    M3ECardTheme cardTheme,
    EdgeInsets pad, {
    required bool vertical,
  }) {
    if (widget.contentOnMedia && widget.media != null) {
      return _mediaOverlay(
        theme,
        cardTheme,
        pad,
        _contentColumn(theme, cardTheme),
      );
    }
    if (!vertical && widget.media != null) {
      return LayoutBuilder(
        builder: (BuildContext context, BoxConstraints constraints) {
          if (!constraints.hasBoundedWidth) {
            return _stacked(theme, cardTheme, pad);
          }
          return _besideMedia(theme, cardTheme, pad);
        },
      );
    }
    return _stacked(theme, cardTheme, pad);
  }

  Widget _stacked(M3EThemeData theme, M3ECardTheme cardTheme, EdgeInsets pad) {
    final bool edgeTextDivider =
        widget.dividerAfterText &&
        widget.textDividerSpan == M3ECardDividerSpan.edge;
    final double gap = cardTheme.resolveGap();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        if (widget.media != null) _mediaSlot(2),
        if (widget.dividerAfterMedia && widget.media != null)
          _divider(widget.mediaDividerSpan, pad),
        if (!edgeTextDivider)
          Padding(padding: pad, child: _contentColumn(theme, cardTheme)),
        if (edgeTextDivider) ...<Widget>[
          Padding(padding: pad, child: _header(theme, cardTheme)),
          const M3EDivider(),
          if (widget.actions != null)
            Padding(
              padding: EdgeInsets.fromLTRB(
                pad.left,
                gap,
                pad.right,
                pad.bottom,
              ),
              child: _sorted(4, _blockInner(widget.actions!, 4)),
            ),
        ],
      ],
    );
  }

  Widget _contentColumn(M3EThemeData theme, M3ECardTheme cardTheme) {
    final double gap = cardTheme.resolveGap();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        _header(theme, cardTheme),
        if (widget.dividerAfterText) ...<Widget>[
          SizedBox(height: gap),
          const M3EDivider(),
        ],
        if (widget.actions != null) ...<Widget>[
          SizedBox(height: gap),
          _sorted(4, _blockInner(widget.actions!, 4)),
        ],
        if (widget.overflow != null &&
            widget.overflowAlignment == M3ECardOverflowAlignment.bottomEnd)
          Align(
            alignment: AlignmentDirectional.centerEnd,
            child: _sorted(5, _blockInner(widget.overflow!, 5)),
          ),
      ],
    );
  }

  Widget _header(M3EThemeData theme, M3ECardTheme cardTheme) {
    final bool overflowAtEnd =
        widget.overflow != null &&
        widget.overflowAlignment == M3ECardOverflowAlignment.topEnd;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Expanded(child: _textLines(theme, cardTheme)),
        if (overflowAtEnd) _sorted(5, _blockInner(widget.overflow!, 5)),
      ],
    );
  }

  Widget _divider(M3ECardDividerSpan span, EdgeInsets pad) {
    if (span == M3ECardDividerSpan.edge) {
      return const M3EDivider();
    }
    return Padding(
      padding: EdgeInsets.only(left: pad.left, right: pad.right),
      child: const M3EDivider(),
    );
  }

  Widget _mediaSlot(double order) {
    final media = widget.media ?? const SizedBox.shrink();
    final Widget slot = widget.mediaDecorative
        ? ExcludeSemantics(child: media)
        : media;
    if (_actionable) {
      return slot;
    }
    return Semantics(sortKey: OrdinalSortKey(order), child: slot);
  }

  Widget _textLines(M3EThemeData theme, M3ECardTheme cardTheme) {
    final double gap = cardTheme.resolveGap();
    final lines = <Widget>[
      if (widget.headline != null)
        _sorted(
          1,
          Text(widget.headline!, style: cardTheme.headlineStyle(theme)),
        ),
      if (widget.subhead != null)
        _sorted(3, Text(widget.subhead!, style: cardTheme.subheadStyle(theme))),
      if (widget.supportingText != null)
        _sorted(
          3.1,
          Text(widget.supportingText!, style: cardTheme.supportingStyle(theme)),
        ),
      if (widget.child != null) _sorted(3.2, widget.child!),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        for (var i = 0; i < lines.length; i++) ...<Widget>[
          if (i > 0) SizedBox(height: gap),
          lines[i],
        ],
      ],
    );
  }

  Widget _besideMedia(
    M3EThemeData theme,
    M3ECardTheme cardTheme,
    EdgeInsets pad,
  ) {
    final bool rule = widget.dividerAfterMedia && widget.media != null;
    final edge = widget.mediaDividerSpan == M3ECardDividerSpan.edge;
    final Widget media = _mediaSlot(2);
    final Widget content = _contentColumn(theme, cardTheme);
    final Widget divider = const M3EDivider(axis: M3EDividerAxis.vertical);
    if (!rule) {
      return Padding(
        padding: pad,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            media,
            Expanded(child: content),
          ],
        ),
      );
    }
    if (edge) {
      return IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Padding(padding: pad, child: media),
            divider,
            Expanded(
              child: Padding(padding: pad, child: content),
            ),
          ],
        ),
      );
    }
    return Padding(
      padding: pad,
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            media,
            divider,
            Expanded(child: content),
          ],
        ),
      ),
    );
  }

  Widget _mediaOverlay(
    M3EThemeData theme,
    M3ECardTheme cardTheme,
    EdgeInsets pad,
    Widget text,
  ) {
    final bool plate =
        widget.contentOverlayPlate ?? cardTheme.contentOverlayPlate;
    final M3EColorScheme scheme = theme.colorScheme;
    final Widget backing = plate
        ? ColoredBox(color: cardTheme.resolveOverlayPlate(scheme), child: text)
        : text;
    final inset = widget.mediaDividerSpan == M3ECardDividerSpan.padding;
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final Widget media = constraints.hasBoundedWidth
            ? SizedBox(width: constraints.maxWidth, child: _mediaSlot(2))
            : _mediaSlot(2);
        return Stack(
          alignment: AlignmentDirectional.bottomStart,
          children: <Widget>[
            media,
            if (!plate)
              Positioned.fill(
                child: ColoredBox(color: cardTheme.resolveScrimColor(scheme)),
              ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: Padding(padding: pad, child: backing),
            ),
            if (widget.dividerAfterMedia && widget.media != null)
              Positioned(
                left: inset ? pad.left : 0,
                right: inset ? pad.right : 0,
                bottom: 0,
                child: const M3EDivider(),
              ),
          ],
        );
      },
    );
  }

  Widget _sorted(double order, Widget child) {
    if (_actionable) {
      return child;
    }
    return Semantics(sortKey: OrdinalSortKey(order), child: child);
  }

  Widget _blockInner(Widget child, double order) {
    return FocusTraversalOrder(order: NumericFocusOrder(order), child: child);
  }
}
