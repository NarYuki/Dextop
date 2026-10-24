part of 'm3e_expandable_item.dart';

extension _M3EExpandableItemInteraction on _M3EExpandableItemState {
  Widget _buildInteractionWrapper(
    M3EExpandableStyle d, {
    required Widget child,
    required VoidCallback? onTap,
    bool isHeader = false,
    bool isIcon = false,
    String? semanticLabel,
    String? semanticHint,
    bool? isExpanded,
    String? tooltip,
    FocusNode? focusNode,
  }) {
    var result = child;
    if (tooltip != null) {
      result = M3ETooltip(message: tooltip, child: result);
    }
    if (onTap == null) {
      return Semantics(
        label: semanticLabel,
        expanded: isExpanded,
        child: result,
      );
    }
    final semantics = Semantics(
      label: semanticLabel,
      hint: semanticHint,
      expanded: isExpanded,
      button: true,
      onTap: onTap,
      child: result,
    );
    if (!d.useInkWell) {
      return _wrapWithGestureDetector(
        isHeader: isHeader,
        onTap: onTap,
        child: semantics,
      );
    }
    return _wrapWithInkWell(
      d,
      isHeader: isHeader,
      isIcon: isIcon,
      onTap: onTap,
      focusNode: focusNode,
      child: semantics,
    );
  }

  Widget _wrapWithGestureDetector({
    required bool isHeader,
    required VoidCallback onTap,
    required Widget child,
  }) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onTap: () {
          M3EFocusInteraction.instance.notePointerInteraction();
          onTap();
        },
        onTapDown: isHeader ? (_) => _handleTapDown() : null,
        onTapUp: isHeader ? (_) => _handleTapUp() : null,
        onTapCancel: isHeader ? () => _handleTapCancel() : null,
        child: child,
      ),
    );
  }

  Widget _wrapWithInkWell(
    M3EExpandableStyle d, {
    required bool isHeader,
    required bool isIcon,
    required VoidCallback onTap,
    required Widget child,
    FocusNode? focusNode,
  }) {
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    return M3ETappable(
      onTap: () {
        M3EFocusInteraction.instance.notePointerInteraction();
        onTap();
      },
      focusNode: focusNode,
      // Registered list toggles share the list's single Tab stop.
      skipTraversal: focusNode != null && focusNode == _toggleRegistration?.node
          ? !_toggleRegistration!.tabStop
          : null,
      mouseCursor: SystemMouseCursors.click,
      materialInk: true,
      onStateChanged: isHeader
          ? (M3EInteractionState state) =>
                _handleTappableStateChanged(state, focusNode)
          : null,
      builder: (BuildContext context, M3EInteractionState state) =>
          _buildTappableOverlay(
            context,
            state,
            scheme: scheme,
            isIcon: isIcon,
            child: child,
          ),
    );
  }

  void _handleTappableStateChanged(
    M3EInteractionState state,
    FocusNode? focusNode,
  ) {
    if (_isPressed != state.pressed) {
      setState(() => _isPressed = state.pressed);
    }
    if (focusNode != null) {
      _handleToggleFocusChanged();
    }
  }

  Widget _buildTappableOverlay(
    BuildContext context,
    M3EInteractionState state, {
    required M3EColorScheme scheme,
    required bool isIcon,
    required Widget child,
  }) {
    final bool active =
        _hovered ||
        _isPressed ||
        _focused ||
        M3EListDragProxyScope.maybeOf(context) != null;
    final M3EExpandableStyle style = widget.decoration;
    final BorderRadius inkRadius = active
        ? BorderRadius.circular(
            _isPressed ? style.pressedRadius : style.hoverRadius,
          )
        : _buildEffectiveRadius();
    final OutlinedBorder shape = isIcon
        ? const CircleBorder()
        : RoundedRectangleBorder(borderRadius: inkRadius);
    return M3EStateLayerOverlay(
      state: state,
      color: scheme.onSurface,
      shape: shape,
      child: child,
    );
  }
}
