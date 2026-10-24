part of '../m3e_split_buttons.dart';

/// Leading and trailing segment builders for [_M3ESplitButtonState].
extension _M3ESplitButtonSegments<T> on _M3ESplitButtonState<T> {
  Widget _buildLeadingSegment({
    required BuildContext context,
    required double height,
    required double minTap,
    required Color color,
    required Color onColor,
    required double? elevation,
    required BorderSide? outlineSide,
    required _CornerRadii radius,
    required M3EButtonSize? customSize,
    required bool focused,
    required bool hovered,
    required bool pressed,
    required bool enabled,
  }) {
    final size = widget.size;
    final targetRadius = radius.toBorderRadius(Directionality.of(context));
    final segmentStates = _segmentStates(
      focused: focused,
      hovered: hovered,
      pressed: pressed,
      enabled: enabled,
    );
    final hasBackgroundBuilder = _segmentHasFill(trailing: false);

    final animatedButton = M3ERadiusAndPaddingMotion(
      motion: springMotion,
      internalLeft: 0,
      internalRight: 0,
      internalTop: 0,
      internalBottom: 0,
      targetRadius: targetRadius,
      builder: (padding, animatedRadius) => _buildLeadingSegmentSurface(
        context: context,
        height: height,
        size: size,
        color: color,
        onColor: onColor,
        elevation: elevation,
        outlineSide: outlineSide,
        customSize: customSize,
        focused: focused,
        enabled: enabled,
        segmentStates: segmentStates,
        hasBackgroundBuilder: hasBackgroundBuilder,
        animatedRadius: animatedRadius,
      ),
    );

    return _wrapSegmentChrome(
      minTap: minTap,
      fixedWidth: customSize?.width,
      tooltip: widget.leadingTooltip,
      semanticLabel: widget.leadingTooltip ?? widget.label,
      child: animatedButton,
    );
  }

  Widget _buildLeadingSegmentSurface({
    required BuildContext context,
    required double height,
    required M3EButtonSize size,
    required Color color,
    required Color onColor,
    required double? elevation,
    required BorderSide? outlineSide,
    required M3EButtonSize? customSize,
    required bool focused,
    required bool enabled,
    required Set<WidgetState> segmentStates,
    required bool hasBackgroundBuilder,
    required BorderRadius animatedRadius,
  }) {
    return M3EFocusRing(
      focused: focused,
      radius: animatedRadius,
      child: Container(
        height: height,
        decoration: BoxDecoration(
          color: hasBackgroundBuilder ? Colors.transparent : color,
          borderRadius: animatedRadius,
          border:
              outlineSide != null &&
                  !_segmentHasOutlineGradient(trailing: false)
              ? Border.fromBorderSide(outlineSide)
              : null,
          boxShadow: _segmentShadows(
            shadowColor: Colors.black,
            elevation: elevation,
          ),
        ),
        child: Material(
          color: Colors.transparent,
          clipBehavior: Clip.antiAlias,
          borderRadius: animatedRadius,
          animationDuration: widget.decorationAnimationDuration,
          child: Focus(
            focusNode: effectiveFocusNode,
            autofocus: widget.autofocus,
            canRequestFocus: enabled,
            skipTraversal: !enabled,
            onFocusChange: widget.onFocusChange,
            onKeyEvent: (node, event) => _leadingKeyEvent(event, enabled),
            child: _buildLeadingInkWell(
              context: context,
              height: height,
              size: size,
              onColor: onColor,
              customSize: customSize,
              enabled: enabled,
              segmentStates: segmentStates,
              animatedRadius: animatedRadius,
            ),
          ),
        ),
      ),
    );
  }

  KeyEventResult _leadingKeyEvent(KeyEvent event, bool enabled) {
    final isActivate =
        event is KeyDownEvent &&
        (event.logicalKey == LogicalKeyboardKey.enter ||
            event.logicalKey == LogicalKeyboardKey.space ||
            event.logicalKey == LogicalKeyboardKey.numpadEnter);
    if (!enabled || !isActivate) {
      return KeyEventResult.ignored;
    }
    widget.onPressed?.call();
    return KeyEventResult.handled;
  }

  Widget _buildLeadingInkWell({
    required BuildContext context,
    required double height,
    required M3EButtonSize size,
    required Color onColor,
    required M3EButtonSize? customSize,
    required bool enabled,
    required Set<WidgetState> segmentStates,
    required BorderRadius animatedRadius,
  }) {
    return InkWell(
      onTap: enabled
          ? () {
              M3EFocusInteraction.instance.notePointerInteraction();
              effectiveFocusNode.requestFocus();
              widget.onPressed?.call();
              _triggerHaptic();
            }
          : null,
      onLongPress: enabled ? widget.onLongPress : null,
      onHover: enabled ? widget.onHover : null,
      onTapDown: enabled
          ? (_) {
              M3EFocusInteraction.instance.notePointerInteraction();
              setState(() => _leadingPressed = true);
            }
          : null,
      onTapUp: enabled ? (_) => setState(() => _leadingPressed = false) : null,
      onTapCancel: enabled
          ? () => setState(() => _leadingPressed = false)
          : null,
      statesController: statesController,
      canRequestFocus: false,
      mouseCursor: _segmentMouseCursor(
        enabled: enabled,
        segmentStates: segmentStates,
      ),
      enableFeedback: widget.enableFeedback,
      splashFactory: _segmentHasOverlayGradient(trailing: false)
          ? NoSplash.splashFactory
          : (widget.splashFactory ?? InkSparkle.splashFactory),
      splashColor: M3EStateLayer.splashColor(onColor),
      highlightColor: Colors.transparent,
      overlayColor: _segmentHasOverlayGradient(trailing: false)
          ? const WidgetStatePropertyAll<Color?>(Colors.transparent)
          : (widget.decorationOverlayColor ??
                M3EStateLayer.overlayColorHoverFocus(onColor)),
      child: _applyDecorationLayers(
        context: context,
        states: segmentStates,
        radius: animatedRadius,
        child: SizedBox(
          height: height,
          child: Center(
            child: _LeadingContent(
              size: size,
              icon: widget.leadingIcon,
              label: widget.label,
              color: _segmentHasForegroundGradient(trailing: false)
                  ? m3eGradientForegroundSourceColor
                  : onColor,
              customSize: customSize,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTrailingSegment({
    required BuildContext context,
    required double height,
    required double minTap,
    required double fixedWidth,
    required Color color,
    required Color onColor,
    required double? elevation,
    required BorderSide? outlineSide,
    required _CornerRadii radius,
    required double trailingLeftPad,
    required double trailingRightPad,
    required double chevronTargetTurns,
    required double chevronDxOffset,
    required M3EButtonSize? customSize,
    required bool focused,
    required bool hovered,
    required bool pressed,
    required bool enabled,
  }) {
    final targetRadius = radius.toBorderRadius(Directionality.of(context));
    final effectiveWidth = fixedWidth < minTap ? minTap : fixedWidth;
    final segmentStates = _segmentStates(
      focused: focused,
      hovered: hovered,
      pressed: pressed,
      selected: _menuOpen,
      enabled: enabled,
    );
    final hasBackgroundBuilder = _segmentHasFill(trailing: true);
    final chevron = _buildTrailingChevron(
      onColor: onColor,
      customSize: customSize,
      chevronTargetTurns: chevronTargetTurns,
      chevronDxOffset: chevronDxOffset,
    );

    final animatedButton = M3ERadiusAndPaddingMotion(
      motion: springMotion,
      internalLeft: 0,
      internalRight: 0,
      internalTop: 0,
      internalBottom: 0,
      targetRadius: targetRadius,
      builder: (padding, animatedRadius) => _buildTrailingSegmentSurface(
        context: context,
        height: height,
        effectiveWidth: effectiveWidth,
        color: color,
        onColor: onColor,
        elevation: elevation,
        outlineSide: outlineSide,
        trailingLeftPad: trailingLeftPad,
        trailingRightPad: trailingRightPad,
        focused: focused,
        enabled: enabled,
        segmentStates: segmentStates,
        hasBackgroundBuilder: hasBackgroundBuilder,
        animatedRadius: animatedRadius,
        chevron: chevron,
      ),
    );

    return _wrapSegmentChrome(
      key: _trailingKey,
      minTap: minTap,
      fixedWidth: customSize?.width,
      tooltip: widget.trailingTooltip,
      semanticLabel: widget.trailingTooltip ?? 'More options',
      expanded: _menuOpen,
      child: animatedButton,
    );
  }

  Widget _buildTrailingChevron({
    required Color onColor,
    required M3EButtonSize? customSize,
    required double chevronTargetTurns,
    required double chevronDxOffset,
  }) {
    return AnimatedRotation(
      duration: M3EMotion.short3,
      curve: M3EMotion.standard,
      turns: chevronTargetTurns,
      child: Transform.translate(
        offset: Offset(chevronDxOffset, 0),
        child: Icon(
          M3EIcons.keyboard_arrow_down,
          size:
              customSize?.iconSize ??
              _splitTheme.splitTrailingIconSize(widget.size),
          color: _segmentHasForegroundGradient(trailing: true)
              ? m3eGradientForegroundSourceColor
              : onColor,
        ),
      ),
    );
  }

  Widget _buildTrailingSegmentSurface({
    required BuildContext context,
    required double height,
    required double effectiveWidth,
    required Color color,
    required Color onColor,
    required double? elevation,
    required BorderSide? outlineSide,
    required double trailingLeftPad,
    required double trailingRightPad,
    required bool focused,
    required bool enabled,
    required Set<WidgetState> segmentStates,
    required bool hasBackgroundBuilder,
    required BorderRadius animatedRadius,
    required Widget chevron,
  }) {
    return M3EFocusRing(
      focused: focused,
      radius: animatedRadius,
      child: Container(
        width: effectiveWidth,
        height: height,
        decoration: BoxDecoration(
          color: hasBackgroundBuilder ? Colors.transparent : color,
          borderRadius: animatedRadius,
          border:
              outlineSide != null && !_segmentHasOutlineGradient(trailing: true)
              ? Border.fromBorderSide(outlineSide)
              : null,
          boxShadow: _segmentShadows(
            shadowColor: M3ETheme.of(context).colorScheme.shadow,
            elevation: elevation,
          ),
        ),
        child: Material(
          color: Colors.transparent,
          clipBehavior: Clip.antiAlias,
          borderRadius: animatedRadius,
          animationDuration: widget.decorationAnimationDuration,
          child: Focus(
            focusNode: _trailingFocusNode,
            canRequestFocus: enabled,
            skipTraversal: !enabled,
            onKeyEvent: (node, event) =>
                _trailingKeyEvent(event, enabled, context),
            child: _buildTrailingInkWell(
              context: context,
              onColor: onColor,
              trailingLeftPad: trailingLeftPad,
              trailingRightPad: trailingRightPad,
              enabled: enabled,
              segmentStates: segmentStates,
              animatedRadius: animatedRadius,
              chevron: chevron,
            ),
          ),
        ),
      ),
    );
  }

  KeyEventResult _trailingKeyEvent(
    KeyEvent event,
    bool enabled,
    BuildContext context,
  ) {
    final isActivate =
        event is KeyDownEvent &&
        (event.logicalKey == LogicalKeyboardKey.enter ||
            event.logicalKey == LogicalKeyboardKey.space ||
            event.logicalKey == LogicalKeyboardKey.numpadEnter);
    if (!enabled || !isActivate) {
      return KeyEventResult.ignored;
    }
    _openMenu(_trailingKey.currentContext ?? context);
    return KeyEventResult.handled;
  }

  Widget _buildTrailingInkWell({
    required BuildContext context,
    required Color onColor,
    required double trailingLeftPad,
    required double trailingRightPad,
    required bool enabled,
    required Set<WidgetState> segmentStates,
    required BorderRadius animatedRadius,
    required Widget chevron,
  }) {
    return InkWell(
      onTap: enabled
          ? () {
              M3EFocusInteraction.instance.notePointerInteraction();
              _trailingFocusNode.requestFocus();
              _openMenu(_trailingKey.currentContext ?? context);
              _triggerHaptic();
            }
          : null,
      mouseCursor: _segmentMouseCursor(
        enabled: enabled,
        segmentStates: segmentStates,
      ),
      onHover: enabled ? (value) => _onTrailingHover(value) : null,
      onTapDown: enabled
          ? (_) {
              M3EFocusInteraction.instance.notePointerInteraction();
              setState(() => _trailingPressed = true);
            }
          : null,
      onTapUp: enabled ? (_) => setState(() => _trailingPressed = false) : null,
      onTapCancel: enabled
          ? () => setState(() => _trailingPressed = false)
          : null,
      canRequestFocus: false,
      enableFeedback: widget.enableFeedback,
      splashFactory: _segmentHasOverlayGradient(trailing: true)
          ? NoSplash.splashFactory
          : (widget.splashFactory ?? InkSparkle.splashFactory),
      splashColor: M3EStateLayer.splashColor(onColor),
      highlightColor: Colors.transparent,
      overlayColor: _segmentHasOverlayGradient(trailing: true)
          ? const WidgetStatePropertyAll<Color?>(Colors.transparent)
          : (widget.decorationOverlayColor ??
                M3EStateLayer.overlayColorHoverFocus(onColor)),
      child: _applyDecorationLayers(
        context: context,
        states: segmentStates,
        radius: animatedRadius,
        trailing: true,
        child: Padding(
          padding: EdgeInsets.only(
            left: trailingLeftPad,
            right: trailingRightPad,
          ),
          child: Center(child: chevron),
        ),
      ),
    );
  }

  void _onTrailingHover(bool value) {
    if (_isTrailingHovered != value) {
      setState(() => _isTrailingHovered = value);
    }
    widget.onHover?.call(value);
  }
}
