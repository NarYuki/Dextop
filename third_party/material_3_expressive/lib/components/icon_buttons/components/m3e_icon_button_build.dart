part of '../m3e_icon_buttons.dart';

extension _M3EIconButtonBuild on _M3EIconButtonState {
  Widget _buildContent(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) =>
          _buildConstrainedContent(context, constraints),
    );
  }

  Widget _buildConstrainedContent(
    BuildContext context,
    BoxConstraints constraints,
  ) {
    final theme = M3ETheme.of(context);
    final iconButtonTheme = theme.iconButtonTheme;
    final scheme = theme.colorScheme;
    final ({Size visual, Size target}) sizes = _resolveLayoutSizes(
      iconButtonTheme,
      constraints,
    );
    final selected = widget.isSelected ?? false;
    // Toggle colors/shape only when [isSelected] is set (default vs toggle).
    final isToggle = widget.isSelected != null;
    final outlineW = iconButtonTheme.outlineWidthFor(widget.size);
    final ({Color bg, Color fg, BorderSide? side}) colors = _resolveColors(
      scheme,
      selected: selected,
      isToggle: isToggle,
      outlineWidth: outlineW,
    );
    final iconSize = iconButtonTheme.iconSize(widget.size);
    final iconTheme = IconThemeData(size: iconSize, color: colors.fg);
    final Widget themedIcon = IconTheme.merge(
      data: iconTheme,
      child: widget.icon,
    );
    final Widget? themedSelectedIcon = widget.selectedIcon == null
        ? null
        : IconTheme.merge(data: iconTheme, child: widget.selectedIcon!);
    Widget paintedButton = SizedBox(
      width: sizes.visual.width,
      height: sizes.visual.height,
      child: _wrapWithBadge(
        theme,
        scheme,
        _buildMorphingFace(
          iconButtonTheme: iconButtonTheme,
          visual: sizes.visual,
          colors: colors,
          isToggle: isToggle,
          selected: selected,
          iconSize: iconSize,
          icon: themedIcon,
          selectedIcon: themedSelectedIcon,
        ),
      ),
    );
    paintedButton = _wrapPointerTracking(paintedButton);
    final layout = widget.inflateHitTarget ? sizes.target : sizes.visual;
    Widget result = Semantics(
      button: true,
      selected: selected,
      label: widget.semanticLabel ?? widget.tooltip,
      child: SizedBox(
        width: layout.width,
        height: layout.height,
        child: Center(child: paintedButton),
      ),
    );
    final String? tooltip = widget.tooltip;
    if (tooltip != null) {
      result = M3ETooltip(
        message: tooltip,
        dismissDelay: Duration.zero,
        child: result,
      );
    }
    return result;
  }

  ({Size visual, Size target}) _resolveLayoutSizes(
    M3EIconButtonTheme iconButtonTheme,
    BoxConstraints constraints,
  ) {
    final Size themeVisual = iconButtonTheme.visual(widget.size, widget.width);
    final Size themeTarget = iconButtonTheme.target(widget.size, widget.width);
    var visual = widget.visualSize ?? themeVisual;
    // Fill the allocated slot when the parent drives width (connected equal
    // flex or standard neighbor-squish). Do not use unbounded max sizes from
    // loose parents (e.g. centered scaffolding).
    final bool fillWidth =
        constraints.hasBoundedWidth &&
        (widget.matchParentConstraints ||
            widget.isGroupConnected ||
            constraints.maxWidth < visual.width);
    final bool fillHeight =
        constraints.hasBoundedHeight &&
        (widget.matchParentConstraints ||
            widget.isGroupConnected ||
            constraints.maxHeight < visual.height);
    if (fillWidth) {
      visual = Size(math.max(0, constraints.maxWidth), visual.height);
    }
    if (fillHeight) {
      visual = Size(visual.width, math.max(0, constraints.maxHeight));
    }
    return (
      visual: visual,
      target: Size(
        math.max(themeTarget.width, visual.width),
        math.max(themeTarget.height, visual.height),
      ),
    );
  }

  Widget _buildMorphingFace({
    required M3EIconButtonTheme iconButtonTheme,
    required Size visual,
    required ({Color bg, Color fg, BorderSide? side}) colors,
    required bool isToggle,
    required bool selected,
    required double iconSize,
    required Widget icon,
    required Widget? selectedIcon,
  }) {
    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        _isPointerDownNotifier,
        _isHoveredNotifier,
        _isPressedNotifier,
        _showFocusRingNotifier,
      ]),
      builder: (BuildContext context, Widget? child) {
        final bool pressed =
            widget.onPressed != null &&
            (_isPointerDownNotifier.value || _isPressedNotifier.value);
        final morphStates = <WidgetState>{
          if (pressed) WidgetState.pressed,
          if (_isHoveredNotifier.value) WidgetState.hovered,
        };
        return _buildMorphButton(
          visual: visual,
          colors: colors,
          targetRadius: widget.isGroupConnected
              ? _connectedRadius(
                  visual: visual,
                  selected: selected,
                  pressed: pressed,
                )
              : BorderRadius.circular(
                  M3EIconButtonShapes.effectiveRadius(
                    theme: iconButtonTheme,
                    size: widget.size,
                    baseVariant: widget.shape,
                    isToggle: isToggle,
                    isSelected: selected,
                    states: morphStates,
                  ),
                ),
          iconSize: iconSize,
          icon: icon,
          selectedIcon: selectedIcon,
          morphStates: morphStates,
          showFocusRing: _showFocusRingNotifier.value,
        );
      },
    );
  }

  BorderRadius _connectedRadius({
    required Size visual,
    required bool selected,
    required bool pressed,
  }) {
    final groupTheme = M3ETheme.of(context).buttonGroupTheme;
    final buttonSize = switch (widget.size) {
      M3EIconButtonSize.xs => M3EButtonSize.xs,
      M3EIconButtonSize.sm => M3EButtonSize.sm,
      M3EIconButtonSize.md => M3EButtonSize.md,
      M3EIconButtonSize.lg => M3EButtonSize.lg,
      M3EIconButtonSize.xl => M3EButtonSize.xl,
    };
    if (selected) {
      return BorderRadius.circular(
        groupTheme.connectedSelectedInnerRadiusFor(visual.height),
      );
    }
    final outer = widget.shape == M3EIconButtonShapeVariant.round
        ? visual.height / 2
        : groupTheme.connectedOuterSquareRadiusFor(buttonSize);
    final inner = pressed
        ? groupTheme.connectedPressedInnerRadiusFor(buttonSize)
        : groupTheme.connectedInnerRadiusFor(buttonSize);
    return BorderRadiusDirectional.horizontal(
      start: Radius.circular(widget.isFirstInGroup ? outer : inner),
      end: Radius.circular(widget.isLastInGroup ? outer : inner),
    ).resolve(Directionality.of(context));
  }

  Widget _wrapPointerTracking(Widget child) {
    if (widget.onPressed == null) {
      return child;
    }
    return TapRegion(
      onTapOutside: _onTapOutside,
      child: Listener(
        behavior: HitTestBehavior.translucent,
        onPointerDown: _onPointerDown,
        onPointerUp: _onPointerUp,
        onPointerCancel: _onPointerCancel,
        child: child,
      ),
    );
  }

  void _onTapOutside(PointerDownEvent _) {
    M3EFocusInteraction.instance.notePointerInteraction();
    if (_focusNode.hasPrimaryFocus) {
      _focusNode.unfocus();
    }
  }

  void _onPointerDown(PointerDownEvent _) {
    M3EFocusInteraction.instance.notePointerInteraction();
    _setPointerDown(true);
  }

  void _onPointerUp(PointerUpEvent _) {
    _setPointerDown(false);
  }

  void _onPointerCancel(PointerCancelEvent _) {
    _setPointerDown(false);
  }

  ({Color bg, Color fg, BorderSide? side}) _resolveColors(
    M3EColorScheme scheme, {
    required bool selected,
    required bool isToggle,
    required double outlineWidth,
  }) {
    final ({Color bg, Color fg, BorderSide? side}) defaults = _variantColors(
      scheme,
      selected: selected,
      isToggle: isToggle,
      outlineWidth: outlineWidth,
    );
    final dec = widget.decoration;
    final disabled = widget.onPressed == null;
    final states = <WidgetState>{
      if (disabled) WidgetState.disabled,
      if (selected) WidgetState.selected,
    };
    final useFgGradient = dec?.foregroundGradient != null;
    final paintInnerOutline = dec?.outlineGradient != null || dec?.side != null;

    var bg = dec?.backgroundColor?.resolve(states) ?? defaults.bg;
    var fg = useFgGradient
        ? m3eGradientForegroundSourceColor
        : (dec?.foregroundColor?.resolve(states) ?? defaults.fg);
    var side = paintInnerOutline
        ? null
        : (dec?.side?.resolve(states) ?? defaults.side);

    if (disabled) {
      final onSurface = scheme.onSurface;
      final isTransparent =
          widget.variant == M3EIconButtonVariant.standard ||
          (widget.variant == M3EIconButtonVariant.outlined &&
              !(isToggle && selected));
      bg = isTransparent
          ? Colors.transparent
          : onSurface.withValues(
              alpha: M3EIconButtonTheme.disabledContainerAlpha,
            );
      if (!useFgGradient) {
        fg = onSurface.withValues(
          alpha: M3EIconButtonTheme.disabledForegroundAlpha,
        );
      }
      if (side != null && !paintInnerOutline) {
        side = BorderSide(
          color: onSurface.withValues(
            alpha: M3EIconButtonTheme.disabledContainerAlpha,
          ),
          width: side.width,
        );
      }
    }

    return (bg: bg, fg: fg, side: side);
  }

  ({Color bg, Color fg, BorderSide? side}) _variantColors(
    M3EColorScheme scheme, {
    required bool selected,
    required bool isToggle,
    required double outlineWidth,
  }) {
    switch (widget.variant) {
      case M3EIconButtonVariant.standard:
        return (
          bg: Colors.transparent,
          fg: (isToggle && selected) ? scheme.primary : scheme.onSurfaceVariant,
          side: null,
        );
      case M3EIconButtonVariant.filled:
        return _filledColors(scheme, isToggle: isToggle, selected: selected);
      case M3EIconButtonVariant.tonal:
        return _tonalColors(scheme, isToggle: isToggle, selected: selected);
      case M3EIconButtonVariant.outlined:
        return _outlinedColors(
          scheme,
          isToggle: isToggle,
          selected: selected,
          outlineWidth: outlineWidth,
        );
    }
  }

  ({Color bg, Color fg, BorderSide? side}) _filledColors(
    M3EColorScheme scheme, {
    required bool isToggle,
    required bool selected,
  }) {
    if (isToggle && !selected) {
      return (
        bg: scheme.surfaceContainer,
        fg: scheme.onSurfaceVariant,
        side: null,
      );
    }
    return (bg: scheme.primary, fg: scheme.onPrimary, side: null);
  }

  ({Color bg, Color fg, BorderSide? side}) _tonalColors(
    M3EColorScheme scheme, {
    required bool isToggle,
    required bool selected,
  }) {
    if (isToggle && selected) {
      return (bg: scheme.secondary, fg: scheme.onSecondary, side: null);
    }
    return (
      bg: scheme.secondaryContainer,
      fg: scheme.onSecondaryContainer,
      side: null,
    );
  }

  ({Color bg, Color fg, BorderSide? side}) _outlinedColors(
    M3EColorScheme scheme, {
    required bool isToggle,
    required bool selected,
    required double outlineWidth,
  }) {
    if (isToggle && selected) {
      return (
        bg: scheme.inverseSurface,
        fg: scheme.onInverseSurface,
        side: null,
      );
    }
    return (
      bg: Colors.transparent,
      fg: scheme.onSurfaceVariant,
      side: BorderSide(color: scheme.outlineVariant, width: outlineWidth),
    );
  }

  Widget _buildMorphButton({
    required Size visual,
    required ({Color bg, Color fg, BorderSide? side}) colors,
    required BorderRadius targetRadius,
    required double iconSize,
    required Widget icon,
    required Widget? selectedIcon,
    required Set<WidgetState> morphStates,
    required bool showFocusRing,
  }) {
    final morphSpring = M3ETheme.of(context).iconButtonTheme.morphSpring;
    return M3ERadiusAndPaddingMotion(
      motion: const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: morphSpring.stiffness,
        damping: morphSpring.damping,
      ),
      internalLeft: 0,
      internalRight: 0,
      internalTop: 0,
      internalBottom: 0,
      targetRadius: targetRadius,
      builder: (padding, animatedRadius) {
        final dec = widget.decoration;
        final fill =
            dec?.backgroundGradient?.resolve(morphStates) ??
            _themeGradientForVariant(M3ETheme.of(context).iconButtonTheme);
        final useGradient = fill != null;
        final gradientOverlay = m3eUsesGradientOverlay(dec?.overlayGradient);
        var displayIcon = icon;
        var displaySelectedIcon = selectedIcon;
        final fgGradient = dec?.foregroundGradient?.resolve(morphStates);
        if (fgGradient != null) {
          displayIcon = m3eGradientForegroundLayer(
            clipRadius: animatedRadius,
            gradient: fgGradient,
            child: displayIcon,
          );
          if (displaySelectedIcon != null) {
            displaySelectedIcon = m3eGradientForegroundLayer(
              clipRadius: animatedRadius,
              gradient: fgGradient,
              child: displaySelectedIcon,
            );
          }
        }
        return M3EFocusRing(
          focused: showFocusRing,
          radius: animatedRadius,
          child: M3EInkSplashTheme(
            color: colors.fg,
            child: IconButton(
              focusNode: _focusNode,
              autofocus: widget.autofocus,
              onPressed: widget.onPressed == null
                  ? null
                  : () {
                      M3EHaptics.trigger(widget.haptic);
                      widget.onPressed!();
                    },
              isSelected: widget.isSelected,
              iconSize: iconSize,
              selectedIcon: displaySelectedIcon,
              icon: displayIcon,
              enableFeedback: widget.haptic != M3EHapticFeedback.none
                  ? false
                  : widget.enableFeedback,
              statesController: _statesController,
              style: _morphButtonStyle(
                visual: visual,
                colors: colors,
                animatedRadius: animatedRadius,
                iconSize: iconSize,
                fill: fill,
                useGradient: useGradient,
                gradientOverlay: gradientOverlay,
              ),
            ),
          ),
        );
      },
    );
  }

  ButtonStyle _morphButtonStyle({
    required Size visual,
    required ({Color bg, Color fg, BorderSide? side}) colors,
    required BorderRadius animatedRadius,
    required double iconSize,
    required Gradient? fill,
    required bool useGradient,
    required bool gradientOverlay,
  }) {
    final dec = widget.decoration;
    final outlineFallback = M3ETheme.of(context).iconButtonTheme
        .outlineWidthFor(widget.size);
    return ButtonStyle(
      fixedSize: WidgetStateProperty.all(visual),
      padding: WidgetStateProperty.all(EdgeInsets.zero),
      iconSize: WidgetStateProperty.all(iconSize),
      shape: WidgetStateProperty.all(
        RoundedRectangleBorder(borderRadius: animatedRadius),
      ),
      backgroundColor: WidgetStateProperty.all(
        useGradient ? Colors.transparent : colors.bg,
      ),
      backgroundBuilder: m3eGradientSurfaceBuilder(
        clipRadius: animatedRadius,
        backgroundGradient: fill == null
            ? null
            : WidgetStatePropertyAll<Gradient?>(fill),
        overlayGradient: dec?.overlayGradient,
        outlineGradient: dec?.outlineGradient,
        outlineSide: dec?.side,
        outlineFallbackWidth: outlineFallback,
      ),
      foregroundColor: WidgetStateProperty.resolveWith((_) => colors.fg),
      side: WidgetStateProperty.resolveWith((_) => colors.side),
      splashFactory: widget.suppressInk || gradientOverlay
          ? NoSplash.splashFactory
          : InkSparkle.splashFactory,
      overlayColor: widget.suppressInk || gradientOverlay
          ? WidgetStateProperty.all(Colors.transparent)
          : (dec?.overlayColor ??
                WidgetStateProperty.resolveWith((Set<WidgetState> states) {
                  if (states.contains(WidgetState.disabled)) {
                    return null;
                  }
                  // Keyboard focus uses the outset ring only.
                  final overlayStates = Set<WidgetState>.of(states)
                    ..remove(WidgetState.focused);
                  return M3EStateLayer.resolveOverlayColor(
                    colors.fg,
                    overlayStates,
                  );
                })),
      mouseCursor: WidgetStateProperty.resolveWith((Set<WidgetState> states) {
        if (states.contains(WidgetState.disabled) || widget.onPressed == null) {
          return SystemMouseCursors.basic;
        }
        return SystemMouseCursors.click;
      }),
      animationDuration: Duration.zero,
      visualDensity: VisualDensity.standard,
    );
  }

  Gradient? _themeGradientForVariant(M3EIconButtonTheme theme) {
    return switch (widget.variant) {
      M3EIconButtonVariant.filled => theme.filledBackgroundGradient,
      M3EIconButtonVariant.tonal => theme.tonalBackgroundGradient,
      M3EIconButtonVariant.standard || M3EIconButtonVariant.outlined => null,
    };
  }

  Widget _wrapWithBadge(
    M3EThemeData theme,
    M3EColorScheme scheme,
    Widget button,
  ) {
    final Widget? badge = _buildBadge(theme, scheme);
    if (badge == null) {
      return button;
    }
    return Stack(
      clipBehavior: Clip.none,
      children: [
        button,
        PositionedDirectional(top: 0, end: 0, child: badge),
      ],
    );
  }

  Widget? _buildBadge(M3EThemeData theme, M3EColorScheme scheme) {
    final Object? v = widget.badgeValue;
    if (v == null) {
      return null;
    }
    if (v is num) {
      final int c = v.round().clamp(0, 999999);
      if (c == 0) {
        return Badge(
          smallSize: 8,
          backgroundColor: scheme.primary,
          textColor: scheme.onPrimary,
        );
      }
      return Badge.count(
        count: c,
        backgroundColor: scheme.primary,
        textColor: scheme.onPrimary,
      );
    }
    if (v is String) {
      if (v.isEmpty) {
        return null;
      }
      return Badge(
        label: Text(
          v,
          style: theme.typeScale.labelSmall.copyWith(color: scheme.onPrimary),
        ),
        backgroundColor: scheme.primary,
        textColor: scheme.onPrimary,
      );
    }
    assert(() {
      throw FlutterError(
        "M3EIconButton.badgeValue must be a String or num, but got '${v.runtimeType}'.",
      );
    }(), 'badgeValue must be String or num');
    return null;
  }
}
