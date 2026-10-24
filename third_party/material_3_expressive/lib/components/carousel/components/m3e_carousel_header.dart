part of '../m3e_carousel.dart';

/// Header, "Show all" button, and the shared full-item-list transition for
/// [_M3ECarouselState].
extension _M3ECarouselHeader on _M3ECarouselState {
  void _openList(M3ECarouselTheme theme) {
    final box = _trackKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null || !box.hasSize) {
      return;
    }
    final Rect origin = box.localToGlobal(Offset.zero) & box.size;
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    M3ECardContainerTransform.show<void>(
      context: context,
      origin: origin,
      originRadius: theme.radiusFor(widget.type),
      originColor: scheme.surface,
      builder: (BuildContext context) {
        return M3ECarouselShowAll(
          header: widget.header,
          children: widget.children,
        );
      },
    );
  }

  Widget _header(M3ECarouselTheme theme) {
    return SizedBox(
      height: theme.arrowSize,
      child: Padding(
        padding: EdgeInsetsDirectional.only(start: theme.headerInset)
            .resolve(Directionality.of(context)),
        child: Row(
          children: <Widget>[
            Expanded(child: widget.header!),
            M3EIconButton(
              variant: M3EIconButtonVariant.standard,
              icon: const Icon(M3EIcons.arrow_forward),
              tooltip: 'Show all',
              onPressed: () => _openList(theme),
            ),
          ],
        ),
      ),
    );
  }

  Widget _showAll(M3ECarouselTheme theme) {
    return Align(
      alignment: AlignmentDirectional.centerEnd,
      child: Padding(
        padding: EdgeInsets.only(top: theme.showAllGap),
        child: Padding(
          padding: EdgeInsets.all(theme.showAllPadding),
          child: M3EButton(
            style: M3EButtonStyle.text,
            onPressed: () => _openList(theme),
            child: const Text('Show all'),
          ),
        ),
      ),
    );
  }
}
