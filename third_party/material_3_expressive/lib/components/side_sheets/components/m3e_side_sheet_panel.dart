part of '../m3e_side_sheets.dart';

/// Whether a sheet on [edge] sits on the right window edge.
bool _m3eSideSheetOnRight(BuildContext context, M3ESideSheetEdge edge) =>
    (edge == M3ESideSheetEdge.end) ==
    (Directionality.of(context) == TextDirection.ltr);

/// Light theme: dark system bar icons where a modal sheet sits under a bar.
/// Dark theme keeps the light icons of the scrim.
Widget _m3eSideSheetLightBars(BuildContext context, Widget sheet) {
  if (M3ETheme.of(context).brightness != Brightness.light) {
    return sheet;
  }
  return AnnotatedRegion<SystemUiOverlayStyle>(
    value: M3ESideSheetSystemUi.lightSurface,
    child: sheet,
  );
}

extension _M3ESideSheetPanel on M3ESideSheet {
  Widget _buildPanel(BuildContext context) {
    final M3EThemeData m3e = M3ETheme.of(context);
    final M3ESideSheetTheme t = theme ?? m3e.sideSheetTheme;
    final _M3ESideSheetScope? scope = _M3ESideSheetScope.maybeOf(context);
    final double modal =
        scope?.modal ?? (variant == M3ESideSheetVariant.modal ? 1 : 0);
    final double back = scope?.back ?? 0;
    final bool right = _m3eSideSheetOnRight(context, edge);
    final EdgeInsets outer = detached
        ? _systemInsets(context, scope) + EdgeInsets.all(t.detachedMargin)
        : EdgeInsets.zero;
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints c) {
        final double available = c.maxWidth.isFinite
            ? c.maxWidth - outer.horizontal
            : double.infinity;
        Widget sheet = M3ESideSheetSurface(
          theme: t,
          modal: modal,
          borderRadius: _radius(t, modal: modal, back: back, right: right),
          child: _withEdgeDivider(
            context,
            t,
            right: right,
            child: _content(context, t, scope),
          ),
        );
        sheet = SizedBox(
          width: t.resolveWidth(width, available),
          height: double.infinity,
          child: M3ESideSheetBackTransform(
            motion: t.motion,
            progress: back,
            anchoredRight: right,
            fromAnchoredEdge: scope?.fromAnchoredEdge ?? false,
            child: sheet,
          ),
        );
        if (detached) {
          sheet = Padding(padding: outer, child: sheet);
        }
        return _semantics(scope, _focusRingScope(m3e, t, sheet));
      },
    );
  }

  Widget _semantics(_M3ESideSheetScope? scope, Widget child) {
    final bool route = scope?.routeScope ?? false;
    return Semantics(
      container: true,
      explicitChildNodes: true,
      scopesRoute: route ? true : null,
      namesRoute: route ? true : null,
      role: SemanticsRole.dialog,
      label: title,
      child: child,
    );
  }

  /// Sheet focus indicator: secondary, 3 thick, 2 from the target.
  Widget _focusRingScope(M3EThemeData m3e, M3ESideSheetTheme t, Widget c) {
    final M3ESideSheetActionStyle a = t.action;
    return M3ETheme(
      data: m3e.copyWith(
        focusRingTheme: m3e.focusRingTheme.copyWith(
          color: a.focusIndicatorColor ?? m3e.colorScheme.secondary,
          width: a.focusIndicatorThickness,
          gap: a.focusIndicatorOffset,
        ),
      ),
      child: c,
    );
  }

  BorderRadius _radius(
    M3ESideSheetTheme t, {
    required double modal,
    required double back,
    required bool right,
  }) {
    final double m = modal.clamp(0, 1).toDouble();
    final double p = back.clamp(0, 1).toDouble();
    final double docked =
        t.standardCornerRadius +
        (t.modalCornerRadius - t.standardCornerRadius) * m;
    final double innerBase = detached ? t.detachedCornerRadius : docked;
    final double outerBase = detached ? t.detachedCornerRadius : 0;
    final double target = t.motion.predictiveBackCornerRadius;
    final inner = Radius.circular(innerBase + (target - innerBase) * p);
    final outer = Radius.circular(outerBase + (target - outerBase) * p);
    return right
        ? BorderRadius.horizontal(left: inner, right: outer)
        : BorderRadius.horizontal(left: outer, right: inner);
  }

  Widget _withEdgeDivider(
    BuildContext context,
    M3ESideSheetTheme t, {
    required bool right,
    required Widget child,
  }) {
    if (!showEdgeDivider) {
      return child;
    }
    final Widget divider = M3EDivider(
      axis: M3EDividerAxis.vertical,
      color: t.dividerColor(M3ETheme.of(context).colorScheme),
      thickness: t.dividerThickness,
    );
    return Row(
      textDirection: TextDirection.ltr,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: right
          ? <Widget>[divider, Expanded(child: child)]
          : <Widget>[Expanded(child: child), divider],
    );
  }

  /// System bar insets inside the sheet, matching the app bar's.
  ///
  /// A route sheet covers the window, so it reads the view insets. Inline
  /// sheets take the top and sides from [MediaQuery] (a `Scaffold` removes
  /// the top under its app bar) and the bottom from the view, which
  /// edge-to-edge apps strip from [MediaQuery].
  EdgeInsets _systemInsets(BuildContext context, _M3ESideSheetScope? scope) {
    final EdgeInsets view = M3ESafeArea.paddingOf(context);
    if (scope?.routeScope ?? false) {
      return view;
    }
    final EdgeInsets media = MediaQuery.paddingOf(context);
    return media.copyWith(
      bottom: media.bottom > view.bottom ? media.bottom : view.bottom,
    );
  }

  /// Bare modal sheets pop the route; bare standard ones need [onClose].
  VoidCallback? _closeHandler(BuildContext context, _M3ESideSheetScope? s) {
    if (!showCloseButton) {
      return null;
    }
    if (s != null) {
      return s.onClose;
    }
    if (onClose != null || variant == M3ESideSheetVariant.standard) {
      return onClose;
    }
    return () => Navigator.maybePop(context);
  }

  Widget _content(
    BuildContext context,
    M3ESideSheetTheme t,
    _M3ESideSheetScope? scope,
  ) {
    final bool autofocus = scope?.autofocus ?? false;
    final VoidCallback? close = _closeHandler(context, scope);
    final VoidCallback? back = onBack;
    final bool right = _m3eSideSheetOnRight(context, edge);
    final Widget header = M3ESideSheetHeader(
      theme: t,
      title: title,
      back: back == null
          ? null
          : M3ESideSheetIconAction(
              theme: t,
              icon: backIcon ?? const Icon(M3EIcons.arrow_back),
              label: labels.back,
              onPressed: back,
              autofocus: autofocus,
            ),
      close: close == null
          ? null
          : M3ESideSheetIconAction(
              theme: t,
              icon: closeIcon ?? const Icon(M3EIcons.close),
              label: labels.close,
              onPressed: close,
              autofocus: autofocus && back == null,
            ),
    );
    final EdgeInsets bars = detached
        ? EdgeInsets.zero
        : _systemInsets(context, scope);
    return Padding(
      padding: EdgeInsets.only(
        left: right ? 0 : bars.left,
        top: bars.top,
        right: right ? bars.right : 0,
        bottom: bars.bottom,
      ),
      child: FocusTraversalGroup(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            header,
            Expanded(
              child: _ordered(
                3,
                M3ESideSheetBody(scrollable: scrollable, child: body),
              ),
            ),
            if (actions.isNotEmpty)
              _ordered(
                4,
                M3ESideSheetActions(
                  theme: t,
                  actions: actions,
                  showDivider: showDivider ?? true,
                ),
              ),
          ],
        ),
      ),
    );
  }

  static Widget _ordered(double order, Widget child) =>
      Semantics(container: true, sortKey: OrdinalSortKey(order), child: child);
}
