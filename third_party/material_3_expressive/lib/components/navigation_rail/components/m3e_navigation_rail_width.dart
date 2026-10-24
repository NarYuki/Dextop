part of '../m3e_navigation_rail.dart';

/// Width-target and layout-inset calculations for
/// `_M3ENavigationRailState`, split out to keep the state class under the
/// component length guidelines.
extension _M3ENavigationRailWidth on _M3ENavigationRailState {
  double _collapsedInset(M3ENavigationRailTheme theme) {
    final bool narrow =
        (theme.collapsedWidth - theme.narrowCollapsedWidth).abs() < 0.5;
    return narrow
        ? theme.narrowHorizontalPadding
        : theme.collapsedHorizontalPadding;
  }

  /// Leading edge of the destination icon for the current layout.
  double _iconOrigin(M3ENavigationRailTheme theme, {required bool expanded}) {
    if (expanded) {
      return theme.expandedItemInset + theme.indicatorLeading;
    }
    return _collapsedInset(theme) +
        (theme.verticalIndicatorWidth - theme.iconSize) / 2;
  }

  double _expandedTarget(BuildContext context) {
    final M3ENavigationRailTheme theme = M3ETheme.of(context)
        .navigationRailTheme;
    return (widget.expandedWidth ?? theme.expandedMinWidth).clamp(
      theme.expandedMinWidth,
      theme.expandedMaxWidth,
    );
  }

  double _targetWidth(BuildContext context) {
    final M3ENavigationRailTheme theme = M3ETheme.of(context)
        .navigationRailTheme;
    if (_isModal) {
      return _modalShown && _isExpanded ? _expandedTarget(context) : 0;
    }
    if (!_isExpanded && widget.hideWhenCollapsed) {
      return 0;
    }
    return _isExpanded ? _expandedTarget(context) : theme.collapsedWidth;
  }

  void _animateWidth() {
    final M3ENavigationRailTheme theme = M3ETheme.of(context)
        .navigationRailTheme;
    final double target = _targetWidth(context);
    _width.motion = const MaterialSpringMotion.expressiveSpatialDefault()
        .copyWith(
          stiffness: theme.widthSpring.stiffness,
          damping: theme.widthSpring.damping,
        );
    if (!_widthSeeded) {
      _width.value = target;
      _widthSeeded = true;
      return;
    }
    if ((_width.value - target).abs() < 0.5) {
      return;
    }
    _width.animateTo(target);
  }
}
