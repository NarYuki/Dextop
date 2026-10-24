part of '../m3e_app_bars.dart';

/// Leading, title, and surface pieces of [_M3EDockedAppBar], kept apart from
/// its state and scroll handling.
extension _M3EDockedAppBarParts on _M3EDockedAppBarState {
  /// The bar's [Material] sheet, with the floating action row on top of it
  /// in actions mode.
  Widget _dockedSurface({
    required Widget padded,
    required Color bg,
    required double elevation,
    required M3EColorScheme scheme,
    required M3EAppBarTheme appBarTheme,
    required M3EAppBarMetrics metrics,
    required _M3EBarMotion motion,
    required EdgeInsets safe,
    required Widget? leading,
  }) {
    final Widget sheet = Material(
      color: bg,
      elevation: elevation,
      shadowColor: scheme.shadow,
      surfaceTintColor: const Color(0x00000000),
      shape: appBarTheme.shape(bar.shapeFamily),
      clipBehavior: bar.clipBehavior,
      child: padded,
    );
    if (!motion.actions) {
      return sheet;
    }
    return _M3EActionOverlay(
      color: bg,
      inset: safe.top,
      topPadding: appBarTheme.flexibleTopPadding,
      actionRow: appBarTheme.actionRowHeight,
      contentPadding: metrics.contentPadding,
      hide: motion.titleHide,
      tonal: appBarTheme.actionsTonalColor(scheme),
      leadingColor: bar.foregroundColor ?? appBarTheme.leadingColor(scheme),
      trailingColor: appBarTheme.trailingColor(scheme),
      iconSize: metrics.iconSize,
      leading: leading,
      actions: bar.actions,
      child: sheet,
    );
  }

  Widget? _resolvedLeading(
    BuildContext context,
    M3EColorScheme scheme,
    M3EAppBarTheme appBarTheme,
  ) {
    return bar.leading ??
        (bar.automaticallyImplyLeading
            ? _maybeBackButton(
                context,
                bar.foregroundColor ?? appBarTheme.leadingColor(scheme),
              )
            : null);
  }

  Widget? _resolvedTitle(
    BuildContext context,
    M3EThemeData theme,
    M3EAppBarTheme appBarTheme,
    M3EColorScheme scheme,
    double expand,
  ) {
    if (bar.title is _M3EAppBarSearchTitle) {
      return bar.title;
    }
    final Color titleColor =
        bar.foregroundColor ?? appBarTheme.titleColor(scheme);
    final TextStyle collapsedStyle = appBarTheme
        .titleStyle(theme.typeScale, variant: bar.variant)
        .copyWith(color: titleColor);
    final TextStyle expandedStyle = appBarTheme
        .titleStyle(theme.typeScale, collapsed: false, variant: bar.variant)
        .copyWith(color: titleColor);
    final TextStyle subtitleStyle = appBarTheme
        .subtitleStyle(theme.typeScale, bar.variant)
        .copyWith(color: appBarTheme.subtitleColor(scheme));
    final bool wrap = expand > 0.5;
    final Widget? title = _collapsingHeadline(
      title: bar.title,
      titleText: bar.titleText,
      collapsedStyle: collapsedStyle,
      expandedStyle: expandedStyle,
      expand: expand,
      centerTitle: bar.centerTitle,
      wrap: wrap,
    );
    final Widget? subtitle = _supporting(
      subtitle: bar.subtitle,
      subtitleText: bar.subtitleText,
      style: subtitleStyle,
      centerTitle: bar.centerTitle,
      wrap: wrap,
    );
    if (title == null && subtitle == null) {
      return null;
    }
    return _M3ETitleBlock(
      title: title,
      subtitle: subtitle,
      centerTitle: bar.centerTitle,
    );
  }
}
