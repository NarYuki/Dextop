part of '../m3e_navigation_rail.dart';

mixin _M3ENavigationRailChildrenMixin on State<M3ENavigationRail> {
  bool get _suppressInk;
  bool get _showExpandedItems;
  List<FocusNode> get _destinationNodes;

  Widget _buildMenuButton(BuildContext context);
  Widget? _buildFab(BuildContext context, {required bool showLabels});

  Widget? _buildTrailing(BuildContext context) {
    final Widget? trailing = widget.trailing;
    if (trailing == null) {
      return null;
    }
    return Padding(
      padding: const EdgeInsetsDirectional.symmetric(horizontal: 16),
      child: Align(
        alignment: _showExpandedItems
            ? AlignmentDirectional.centerStart
            : Alignment.center,
        child: trailing,
      ),
    );
  }

  List<Widget> _headerChildren(BuildContext context) {
    final theme = M3ETheme.of(context).navigationRailTheme;
    final children = <Widget>[SizedBox(height: theme.topSpace)];
    if (widget.leading != null) {
      children.add(
        Padding(
          padding: const EdgeInsetsDirectional.only(bottom: 8),
          child: widget.leading,
        ),
      );
    }
    children.add(_buildMenuButton(context));
    final Widget? fab = _buildFab(context, showLabels: _showExpandedItems);
    if (fab != null) {
      children.add(fab);
    }
    return children;
  }

  List<Widget> _destinationChildren(BuildContext context) {
    final M3ENavigationRailTheme theme = M3ETheme.of(context)
        .navigationRailTheme;
    final List<Widget> children = _showExpandedItems
        ? _buildExpandedDestinations(context, theme)
        : _buildCollapsedDestinations(theme);
    if (_showExpandedItems) {
      children.add(SizedBox(height: theme.expandedTrailingSpace));
    }
    if (widget.trailing != null && !widget.trailingAtBottom) {
      final Widget? trailing = _buildTrailing(context);
      if (trailing != null) {
        children.add(trailing);
      }
    }
    return children;
  }

  List<Widget> _buildExpandedDestinations(
    BuildContext context,
    M3ENavigationRailTheme theme,
  ) {
    final children = <Widget>[];
    var index = 0;
    for (final M3ENavigationRailSection section in widget.sections) {
      if (section.header != null) {
        children.add(_sectionHeader(context, theme, section.header!));
      }
      for (final M3ENavigationRailDestination dest in section.destinations) {
        final itemIndex = index;
        children.add(
          _paddedDestination(
            gap: theme.expandedItemGap,
            child: M3ERailItem(
              destination: dest,
              selected: itemIndex == widget.selectedIndex,
              onTap: () => widget.onDestinationSelected(itemIndex),
              expanded: true,
              labelBehavior: widget.labelBehavior,
              suppressInk: _suppressInk,
              focusNode: _destinationNodes[itemIndex],
            ),
          ),
        );
        index++;
      }
    }
    return children;
  }

  List<Widget> _buildCollapsedDestinations(M3ENavigationRailTheme theme) {
    final List<M3ENavigationRailDestination> all = widget.sections
        .expand((M3ENavigationRailSection s) => s.destinations)
        .toList();
    return <Widget>[
      for (var i = 0; i < all.length; i++)
        _paddedDestination(
          gap: theme.itemVerticalGap,
          child: M3ERailItem(
            destination: all[i],
            selected: i == widget.selectedIndex,
            onTap: () => widget.onDestinationSelected(i),
            expanded: false,
            labelBehavior: widget.labelBehavior,
            suppressInk: _suppressInk,
            focusNode: _destinationNodes[i],
          ),
        ),
    ];
  }

  Widget _sectionHeader(
    BuildContext context,
    M3ENavigationRailTheme theme,
    Widget header,
  ) {
    final M3EThemeData m3e = M3ETheme.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: theme.indicatorLeading,
        end: theme.indicatorTrailing,
        top: theme.sectionHeaderSpacingTop,
        bottom: theme.sectionHeaderSpacingBottom,
      ),
      child: DefaultTextStyle(
        style: m3e.typeScale.titleSmall.copyWith(
          color: m3e.colorScheme.onSurfaceVariant,
        ),
        child: header,
      ),
    );
  }

  Widget _paddedDestination({required double gap, required Widget child}) {
    return Padding(
      padding: EdgeInsets.only(bottom: gap),
      child: child,
    );
  }
}
