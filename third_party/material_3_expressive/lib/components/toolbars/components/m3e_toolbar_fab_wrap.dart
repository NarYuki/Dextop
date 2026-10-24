part of '../m3e_toolbars.dart';

extension _M3EToolbarFabWrap on _M3EToolbarState {
  Widget _withFab(Widget toolbar, M3EToolbarColorStyle style) {
    final Widget expandIcon =
        widget.fabExpandIcon ?? widget.fabIcon ?? const Icon(M3EIcons.add);
    final Widget collapseIcon =
        widget.fabCollapseIcon ?? const Icon(M3EIcons.close);
    final bool fabExpands = widget.fabExpandsToolbar;
    final fabIcon = fabExpands && _expanded ? collapseIcon : expandIcon;
    final VoidCallback? fabOnPressed = widget.floatingActionButton == null
        ? (fabExpands ? _onFabPressed : widget.onFabPressed)
        : widget.onFabPressed;
    final M3EToolbarTheme toolbarTheme = M3ETheme.of(context).toolbarTheme;
    final double fabProgress = fabExpands ? _expandCtrl.value : 1;
    final M3EFabTheme fabTheme = M3ETheme.of(context).fabTheme;
    final Widget fab = M3EToolbarFabSlot(
      fab: widget.floatingActionButton,
      icon: fabIcon,
      onPressed: fabOnPressed,
      color: style == M3EToolbarColorStyle.vibrant
          ? M3EFabColor.tertiary
          : M3EFabColor.secondary,
      containerSize: fabExpands ? _fabSize : M3EToolbarTokens.fabBaseline,
      iconSize:
          toolbarTheme.fabCollapsedIcon +
          (toolbarTheme.fabExpandedIcon - toolbarTheme.fabCollapsedIcon) *
              fabProgress,
      cornerRadius:
          fabTheme.mediumRadius +
          (fabTheme.regularRadius - fabTheme.mediumRadius) * fabProgress,
    );

    final horizontal = widget.axis == Axis.horizontal;
    final isRtl = Directionality.of(context) == TextDirection.rtl;

    return AnimatedBuilder(
      animation: _expandCtrl,
      builder: (BuildContext context, Widget? child) {
        if (horizontal) {
          final M3EToolbarFabPosition pos =
              widget.fabPosition == M3EToolbarFabPosition.start ||
                  widget.fabPosition == M3EToolbarFabPosition.end
              ? widget.fabPosition
              : M3EToolbarFabPosition.end;
          return M3EToolbarHorizontalFabLayout(
            progress: _fabLayoutProgress,
            fabPosition: pos,
            isRtl: isRtl,
            toolbar: toolbar,
            fab: fab,
          );
        }
        final M3EToolbarFabPosition pos =
            widget.fabPosition == M3EToolbarFabPosition.top ||
                widget.fabPosition == M3EToolbarFabPosition.bottom
            ? widget.fabPosition
            : M3EToolbarFabPosition.bottom;
        return M3EToolbarVerticalFabLayout(
          progress: _fabLayoutProgress,
          fabPosition: pos,
          toolbar: toolbar,
          fab: fab,
        );
      },
    );
  }
}
