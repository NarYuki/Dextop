part of '../m3e_navigation_drawer.dart';

/// Widget-building helpers for `_M3ENavigationDrawerState`, split out to
/// keep the state class under the component length guidelines.
extension _M3ENavigationDrawerBuild on _M3ENavigationDrawerState {
  Widget _buildStandard(BuildContext context) {
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final height = constraints.maxHeight.isFinite
            ? constraints.maxHeight
            : null;
        final panel = _sheet(context, height: height);
        if (!_canDismiss) {
          return _shortcuts(panel);
        }
        if (height == null) {
          return _reveal.value <= 0
              ? const SizedBox.shrink()
              : _shortcuts(panel);
        }
        final theme = M3ETheme.of(context).navigationDrawerTheme;
        final reveal = _reveal.value.clamp(0.0, 1.0);
        return _shortcuts(
          SizedBox(
            width: theme.width * reveal,
            height: height,
            child: ClipRect(
              child: OverflowBox(
                alignment: AlignmentDirectional.centerStart,
                minWidth: theme.width,
                maxWidth: theme.width,
                minHeight: height,
                maxHeight: height,
                child: panel,
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildOverlay(BuildContext context) {
    final data = M3ETheme.of(context);
    final theme = data.navigationDrawerTheme;
    final reveal = _reveal.value.clamp(0.0, 1.0);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    return M3EScrimSystemUi.wrap(
      _shortcuts(
        LayoutBuilder(
          builder: (BuildContext context, BoxConstraints constraints) {
            final height = constraints.maxHeight.isFinite
                ? constraints.maxHeight
                : null;
            return Stack(
              children: <Widget>[
                Positioned.fill(
                  child: Semantics(
                    button: true,
                    label: 'Close navigation drawer',
                    child: GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: () => _setOpen(false),
                      child: ColoredBox(
                        color: theme
                            .resolveScrim(data.colorScheme)
                            .withValues(alpha: theme.scrimOpacity * reveal),
                      ),
                    ),
                  ),
                ),
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: GestureDetector(
                    onHorizontalDragUpdate: _onDragUpdate,
                    onHorizontalDragEnd: _onDragEnd,
                    child: FractionalTranslation(
                      translation: Offset(rtl ? 1 - reveal : reveal - 1, 0),
                      child: _sheet(context, height: height),
                    ),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _shortcuts(Widget child) {
    return CallbackShortcuts(
      bindings: <ShortcutActivator, VoidCallback>{
        const SingleActivator(LogicalKeyboardKey.arrowDown): () => _move(1),
        const SingleActivator(LogicalKeyboardKey.arrowUp): () => _move(-1),
      },
      child: child,
    );
  }

  Widget _sheet(BuildContext context, {required double? height}) {
    final data = M3ETheme.of(context);
    final theme = data.navigationDrawerTheme;
    final scheme = data.colorScheme;
    final color = _isModal
        ? theme.resolveModalContainer(scheme)
        : theme.containerColor(scheme);
    final radius = BorderRadiusDirectional.only(
      topEnd: Radius.circular(theme.endCornerRadius),
      bottomEnd: Radius.circular(theme.endCornerRadius),
    ).resolve(Directionality.of(context));
    final rows = _rows(data);
    final body = height == null
        ? Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: rows)
        : ListView(padding: EdgeInsets.zero, children: rows);
    final material = Material(
      type: MaterialType.transparency,
      child: SafeArea(child: body),
    );
    return SizedBox(
      width: theme.width,
      height: height,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: color,
          borderRadius: theme.endCornerRadius > 0 ? radius : null,
          boxShadow: M3EElevation.shadows(
            theme.elevation,
            shadowColor: scheme.shadow,
          ),
        ),
        child: theme.endCornerRadius > 0
            ? ClipRRect(borderRadius: radius, child: material)
            : material,
      ),
    );
  }

  List<Widget> _rows(M3EThemeData data) {
    final theme = data.navigationDrawerTheme;
    final rows = <Widget>[];
    final headline = widget.headline;
    if (headline != null) {
      rows.add(
        Padding(
          padding: EdgeInsets.symmetric(
            horizontal: theme.headlineHorizontalPadding,
            vertical: theme.headlineVerticalPadding,
          ),
          child: Text(
            headline,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: data.typeScale.titleSmall.copyWith(
              color: theme.resolveHeadline(data.colorScheme),
            ),
          ),
        ),
      );
    }
    var index = _appendGroup(
      rows: rows,
      data: data,
      destinations: widget.destinations,
      start: 0,
      indent: 0,
      divided: false,
      header: null,
    );
    for (final section in widget.sections) {
      index = _appendGroup(
        rows: rows,
        data: data,
        destinations: section.destinations,
        start: index,
        indent: theme.sectionIndent,
        divided: true,
        header: section.header,
      );
    }
    return rows;
  }

  int _appendGroup({
    required List<Widget> rows,
    required M3EThemeData data,
    required List<M3ENavigationDestination> destinations,
    required int start,
    required double indent,
    required bool divided,
    required String? header,
  }) {
    final theme = data.navigationDrawerTheme;
    if (divided) {
      rows.add(
        Padding(
          padding: EdgeInsets.symmetric(
            horizontal: theme.dividerInset,
            vertical: theme.dividerSpacing,
          ),
          child: SizedBox(
            height: theme.dividerThickness,
            child: ColoredBox(color: theme.resolveDivider(data.colorScheme)),
          ),
        ),
      );
    }
    if (header != null && header.isNotEmpty) {
      rows.add(
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: theme.contentPadding,
            end: theme.contentPadding,
            bottom: theme.headlineVerticalPadding,
          ),
          child: Text(
            header,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: data.typeScale.titleSmall.copyWith(
              color: theme.resolveHeadline(data.colorScheme),
            ),
          ),
        ),
      );
    }
    for (var i = 0; i < destinations.length; i++) {
      final index = start + i;
      rows.add(
        CallbackShortcuts(
          bindings: <ShortcutActivator, VoidCallback>{
            const SingleActivator(LogicalKeyboardKey.arrowDown): () => _move(1),
            const SingleActivator(LogicalKeyboardKey.arrowUp): () => _move(-1),
          },
          child: M3EDrawerDestinationButton(
            destination: destinations[i],
            selected: index == widget.selectedIndex,
            focusNode: _nodes[index],
            indent: indent,
            onTap: () => _handleSelect(index),
          ),
        ),
      );
    }
    return start + destinations.length;
  }
}
