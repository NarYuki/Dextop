part of '../m3e_side_sheets.dart';

extension _M3ESideSheetLayoutBuild on _M3ESideSheetLayoutState {
  Widget _buildLayout(BuildContext context) {
    final bool blocking = _modalActive;
    return PopScope<Object?>(
      canPop: !blocking,
      onPopInvokedWithResult: _onPopInvoked,
      child: Actions(
        actions: _dismissActions(blocking: blocking),
        child: AnimatedBuilder(
          animation: Listenable.merge(<Listenable>[_open, _modal, _back]),
          builder: (BuildContext context, _) => LayoutBuilder(
            builder: (BuildContext context, BoxConstraints c) =>
                _buildStack(context, c, blocking: blocking),
          ),
        ),
      ),
    );
  }

  void _onPopInvoked(bool didPop, Object? result) {
    if (!didPop && _modalActive) {
      close();
    }
  }

  /// Escape closes the sheet only while it is modal.
  Map<Type, Action<Intent>> _dismissActions({required bool blocking}) {
    if (!blocking) {
      return const <Type, Action<Intent>>{};
    }
    return <Type, Action<Intent>>{
      DismissIntent: CallbackAction<DismissIntent>(
        onInvoke: (_) {
          close();
          return null;
        },
      ),
    };
  }

  Widget _buildStack(
    BuildContext context,
    BoxConstraints c, {
    required bool blocking,
  }) {
    final M3ESideSheet sheet = widget.sheet;
    final M3ESideSheetTheme t = _theme;
    final double open = _open.value;
    final double modal = _modal.value.clamp(0, 1).toDouble();
    final bool right = _onRight;
    final double margin = sheet.detached ? t.detachedMargin : 0;
    final double width = t.resolveWidth(sheet.width, c.maxWidth - margin * 2);
    // Never wider than the layout, so a narrow window can't overflow.
    final double inset = math.min(
      c.maxWidth,
      (width + margin + t.bodyTrailingMargin) *
          open.clamp(0, 1).toDouble() *
          (1 - modal),
    );
    final bool showSheet = _isOpen || open > 0;
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        Positioned(
          key: const ValueKey<String>('body'),
          top: 0,
          bottom: 0,
          left: right ? 0 : inset,
          right: right ? inset : 0,
          child: ExcludeFocus(
            excluding: blocking,
            child: ExcludeSemantics(excluding: blocking, child: widget.body),
          ),
        ),
        if (showSheet && modal > 0)
          KeyedSubtree(
            key: const ValueKey<String>('scrim'),
            child: M3EScrimSystemUi.wrap(
              M3ESideSheetScrim(
                color: t.scrimColor(scheme),
                progress: open * modal,
                label: sheet.labels.scrim,
                onTap: blocking && widget.isDismissible ? close : null,
              ),
            ),
          ),
        if (showSheet)
          Align(
            key: const ValueKey<String>('sheet'),
            alignment: right ? Alignment.centerRight : Alignment.centerLeft,
            child: FractionalTranslation(
              translation: Offset((right ? 1 : -1) * (1 - open), 0),
              child: _modalBars(
                context,
                modal: modal >= 0.5,
                child: Focus(
                  key: _sheetKey,
                  focusNode: _sheetFocus,
                  child: _M3ESideSheetScope(
                    modal: modal,
                    back: _back.value,
                    fromAnchoredEdge: _fromAnchor,
                    onClose: _onClosePressed,
                    autofocus: _keyboard && blocking,
                    routeScope: false,
                    child: sheet,
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }

  /// Modal only: dark bar icons where the light sheet sits under a bar.
  /// [_sheetKey] keeps the sheet's state when the wrapper changes.
  Widget _modalBars(
    BuildContext context, {
    required bool modal,
    required Widget child,
  }) {
    return modal ? _m3eSideSheetLightBars(context, child) : child;
  }
}
