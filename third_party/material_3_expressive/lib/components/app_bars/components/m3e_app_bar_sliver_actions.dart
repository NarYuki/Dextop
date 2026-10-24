part of '../m3e_app_bars.dart';

/// Builds the [M3EAppBarHideMode.actions]-only variant of the sliver app bar,
/// where the action row stays pinned while the rest of the bar slides away.
extension _M3ESliverAppBarActions on _M3ESliverAppBarState {
  Widget _actionSliver(
    M3EThemeData theme,
    M3EAppBarTheme appBarTheme,
    M3EColorScheme scheme,
    M3EAppBarMetrics metrics,
    _M3EBarMotion motion,
    double top,
  ) {
    final double push = motion.band;
    final double paintContent = math.max(push, _collapsed);
    final double share = _follow ? 1.0 : motion.shown.clamp(0.0, 1.0);
    // Actions mode hides on scroll, so the bar keeps its resting look.
    final Color bg = _m3eBarColor(
      bar: bar,
      theme: appBarTheme,
      scheme: scheme,
      under: false,
    );
    final double elevation = metrics.elevation;
    final Widget? leading = _actionSliverLeading(appBarTheme, scheme);
    final Widget body = _actionSliverBody(
      theme,
      appBarTheme,
      scheme,
      metrics,
      motion,
      leading,
      bg,
    );
    return _M3EActionSliver(
      maxExtent: top + _expanded * share,
      minExtent: top + _collapsed,
      child: _M3EScrolledUnder(
        scrolledUnder: false,
        child: SizedBox(
          height: top + paintContent,
          child: Padding(
            padding: EdgeInsets.only(top: top),
            child: Stack(
              children: <Widget>[
                _actionSliverSurface(
                  bg: bg,
                  elevation: elevation,
                  appBarTheme: appBarTheme,
                  scheme: scheme,
                  push: push,
                  motion: motion,
                  body: body,
                ),
                _actionSliverRow(
                  appBarTheme: appBarTheme,
                  metrics: metrics,
                  scheme: scheme,
                  motion: motion,
                  leading: leading,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget? _actionSliverLeading(
    M3EAppBarTheme appBarTheme,
    M3EColorScheme scheme,
  ) {
    return bar.leading ??
        (bar.automaticallyImplyLeading
            ? _maybeBackButton(
                context,
                bar.foregroundColor ?? appBarTheme.leadingColor(scheme),
              )
            : null);
  }

  Widget _actionSliverBody(
    M3EThemeData theme,
    M3EAppBarTheme appBarTheme,
    M3EColorScheme scheme,
    M3EAppBarMetrics metrics,
    _M3EBarMotion motion,
    Widget? leading,
    Color containerColor,
  ) {
    return _M3EBarBody(
      expand: motion.titleExpand,
      topPadding: appBarTheme.flexibleTopPadding,
      actionRow: appBarTheme.actionRowHeight,
      bottomPadding: metrics.flexibleBottomPadding,
      titleInset: metrics.titleInset,
      contentPadding: metrics.contentPadding,
      centerTitle: bar.centerTitle,
      search: bar.title is _M3EAppBarSearchTitle,
      leading: leading,
      actions: bar.actions,
      title: _sliverTitle(theme, appBarTheme, scheme, motion.titleExpand),
      leadingColor: bar.foregroundColor ?? appBarTheme.leadingColor(scheme),
      trailingColor: appBarTheme.trailingColor(scheme),
      iconSize: metrics.iconSize,
      separateActions: true,
      containerColor: containerColor,
      shadowColor: scheme.shadow,
      shape: appBarTheme.shape(bar.shapeFamily),
    );
  }

  Widget _actionSliverSurface({
    required Color bg,
    required double elevation,
    required M3EAppBarTheme appBarTheme,
    required M3EColorScheme scheme,
    required double push,
    required _M3EBarMotion motion,
    required Widget body,
  }) {
    return Material(
      color: bg,
      elevation: elevation,
      shadowColor: scheme.shadow,
      surfaceTintColor: const Color(0x00000000),
      shape: appBarTheme.shape(bar.shapeFamily),
      child: _m3eClipSliding(
        push: push,
        visual: math.max(motion.painted, push),
        child: body,
      ),
    );
  }

  Widget _actionSliverRow({
    required M3EAppBarTheme appBarTheme,
    required M3EAppBarMetrics metrics,
    required M3EColorScheme scheme,
    required _M3EBarMotion motion,
    required Widget? leading,
  }) {
    return Positioned(
      top: appBarTheme.flexibleTopPadding,
      left: 0,
      right: 0,
      height: appBarTheme.actionRowHeight,
      child: _M3EActionRow(
        contentPadding: metrics.contentPadding,
        actionRow: appBarTheme.actionRowHeight,
        hide: motion.titleHide,
        tonal: appBarTheme.actionsTonalColor(scheme),
        leadingColor: bar.foregroundColor ?? appBarTheme.leadingColor(scheme),
        trailingColor: appBarTheme.trailingColor(scheme),
        iconSize: metrics.iconSize,
        leading: leading,
        actions: bar.actions,
      ),
    );
  }
}
