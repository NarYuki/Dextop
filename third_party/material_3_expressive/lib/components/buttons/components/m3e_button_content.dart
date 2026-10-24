// Button build / core widget assembly for function_length.
part of '../m3e_buttons.dart';

extension _M3EButtonContent on _M3EButtonState {
  Widget _buildContent(BuildContext context) {
    final m = _measurements;
    final baseInternalPadding = EdgeInsets.symmetric(
      horizontal: _usesSelection && !_hasSelectionLabel
          ? m.hPadding / 2
          : m.hPadding,
    );
    final shapes = _usesSelection
        ? _resolveSelectionShapes(m)
        : _resolveShapes(m);
    final baseStyle = _buildBaseStyle();

    return wrapWithPointerPressTracking(
      enabled: widget.enabled && widget.onPressed != null,
      child: buildAnimatedContent(
        builder:
            (
              context, {
              required isPressed,
              required isHovered,
              required isFocused,
            }) {
              return _buildAnimatedCore(
                m: m,
                baseStyle: baseStyle,
                baseInternalPadding: baseInternalPadding,
                shapes: shapes,
                isPressed: isPressed,
                isHovered: isHovered,
                isFocused: isFocused,
              );
            },
      ),
    );
  }

  ({
    BorderRadius defaultShape,
    BorderRadius pressedShape,
    BorderRadius hoveredShape,
    bool freezeLeft,
    bool freezeRight,
  })
  _resolveShapes(M3EButtonMeasurements m) {
    final fullyRound = BorderRadius.circular(m.height / 2);
    final explicitBorderRadius = _decoration?.borderRadius;
    final tokenPressed = _buttonTheme.pressedRadius(widget.size);
    final defaultShape = explicitBorderRadius != null
        ? BorderRadius.circular(explicitBorderRadius)
        : widget.shape == M3EButtonShape.round
        ? fullyRound
        : BorderRadius.circular(_buttonTheme.squareRadius(widget.size));

    final explicitPressed = _decoration?.pressedRadius;
    final pressedShape = explicitPressed != null
        ? BorderRadius.circular(explicitPressed)
        : explicitBorderRadius != null
        ? BorderRadius.circular(explicitBorderRadius)
        : BorderRadius.circular(tokenPressed);

    // Spec: hover keeps resting shape; only press morphs unless overridden.
    final explicitHovered = _decoration?.hoveredRadius;
    final hoveredShape = explicitHovered != null
        ? BorderRadius.circular(explicitHovered)
        : defaultShape;

    return (
      defaultShape: defaultShape,
      pressedShape: pressedShape,
      hoveredShape: hoveredShape,
      freezeLeft: false,
      freezeRight: false,
    );
  }

  BorderRadius _targetShape({
    required bool effectivelyEnabled,
    required bool isPressed,
    required bool isHovered,
    required ({
      BorderRadius defaultShape,
      BorderRadius pressedShape,
      BorderRadius hoveredShape,
      bool freezeLeft,
      bool freezeRight,
    })
    shapes,
  }) {
    if (effectivelyEnabled && isPressed) {
      return shapes.pressedShape;
    }
    if (effectivelyEnabled && isHovered) {
      return shapes.hoveredShape;
    }
    return shapes.defaultShape;
  }

  Widget _buildAnimatedCore({
    required M3EButtonMeasurements m,
    required ButtonStyle baseStyle,
    required EdgeInsets baseInternalPadding,
    required ({
      BorderRadius defaultShape,
      BorderRadius pressedShape,
      BorderRadius hoveredShape,
      bool freezeLeft,
      bool freezeRight,
    })
    shapes,
    required bool isPressed,
    required bool isHovered,
    required bool isFocused,
  }) {
    final effectivelyEnabled = widget.enabled && widget.onPressed != null;
    final targetRadius = _targetShape(
      effectivelyEnabled: effectivelyEnabled,
      isPressed: isPressed,
      isHovered: isHovered,
      shapes: shapes,
    );

    Widget core = RepaintBoundary(
      child: M3ERadiusAndPaddingMotion(
        motion: springMotion,
        internalLeft: baseInternalPadding.left,
        internalRight: baseInternalPadding.right,
        internalTop: baseInternalPadding.top,
        internalBottom: baseInternalPadding.bottom,
        targetRadius: targetRadius,
        freezeTopLeft: widget.isGroupConnected && shapes.freezeLeft,
        freezeBottomLeft: widget.isGroupConnected && shapes.freezeLeft,
        freezeTopRight: widget.isGroupConnected && shapes.freezeRight,
        freezeBottomRight: widget.isGroupConnected && shapes.freezeRight,
        builder: (animatedInternal, animatedRadius) {
          final buttonCore = _buildButtonCore(
            m,
            baseStyle,
            animatedInternal,
            animatedRadius,
          );
          return M3EFocusRing(
            focused: isFocused,
            radius: animatedRadius,
            child: buttonCore,
          );
        },
      ),
    );

    final dec = _decoration;
    final hasDecorationSize =
        dec?.fixedSize != null ||
        dec?.minimumSize != null ||
        dec?.maximumSize != null;
    final fixedWidth = hasDecorationSize ? null : widget.size.width;
    if (fixedWidth != null) {
      core = SizedBox(width: fixedWidth, child: core);
    }
    return core;
  }

  Widget _buildButtonCore(
    M3EButtonMeasurements m,
    ButtonStyle baseStyle,
    EdgeInsets internalPadding,
    BorderRadius animatedRadius,
  ) {
    Widget child = _usesSelection
        ? _buildSelectionContent(m)
        : widget.icon != null || widget.label != null
        ? _M3EButtonIconLayout(
            icon: widget.icon,
            label: widget.label,
            size: widget.size,
            iconAlignment: _decoration?.iconAlignment ?? IconAlignment.start,
          )
        : widget.child ?? const SizedBox.shrink();
    if (widget.semanticLabel != null) {
      child = ExcludeSemantics(child: child);
    }

    final style = baseStyle.copyWith(
      padding: _decoration?.padding != null
          ? WidgetStateProperty.all<EdgeInsetsGeometry>(_decoration!.padding!)
          : WidgetStateProperty.all<EdgeInsetsGeometry>(internalPadding),
      shape: WidgetStateProperty.all<OutlinedBorder>(
        RoundedRectangleBorder(borderRadius: animatedRadius),
      ),
      backgroundBuilder: _withStateLayer(
        animatedRadius,
        m3eGradientSurfaceBuilder(
          clipRadius: animatedRadius,
          backgroundGradient: _decoration?.backgroundGradient,
          overlayGradient: _decoration?.overlayGradient,
          outlineGradient: _decoration?.outlineGradient,
          outlineSide: _decoration?.side,
          explicitBuilder: _decoration?.backgroundBuilder,
        ),
      ),
      foregroundBuilder: m3eGradientForegroundBuilder(
        clipRadius: animatedRadius,
        gradient: _decoration?.foregroundGradient,
        explicitBuilder: _decoration?.foregroundBuilder,
      ),
    );

    final button = _createMaterialButton(
      style: style,
      onPressed: _effectiveOnPressed,
      onLongPress: widget.enabled ? widget.onLongPress : null,
      child: child,
    );

    return _wrapButtonChrome(button);
  }

  VoidCallback? get _effectiveOnPressed {
    if (!widget.enabled || widget.onPressed == null) {
      return null;
    }
    return () {
      M3EHaptics.trigger(_decoration?.haptic ?? M3EHapticFeedback.none);
      // Pointer taps hide rings in the press listener. Take focus here so the
      // next Tab continues from this button without painting a ring.
      effectiveFocusNode.requestFocus();
      widget.onPressed?.call();
    };
  }

  Widget _createMaterialButton({
    required ButtonStyle style,
    required VoidCallback? onPressed,
    required VoidCallback? onLongPress,
    required Widget child,
  }) {
    switch (widget.style) {
      case M3EButtonStyle.filled:
        return FilledButton(
          style: style,
          onPressed: onPressed,
          onLongPress: onLongPress,
          onHover: widget.onHover,
          statesController: statesController,
          focusNode: effectiveFocusNode,
          autofocus: widget.autofocus,
          onFocusChange: widget.onFocusChange,
          child: child,
        );
      case M3EButtonStyle.tonal:
        return FilledButton.tonal(
          style: style,
          onPressed: onPressed,
          onLongPress: onLongPress,
          onHover: widget.onHover,
          statesController: statesController,
          focusNode: effectiveFocusNode,
          autofocus: widget.autofocus,
          onFocusChange: widget.onFocusChange,
          child: child,
        );
      case M3EButtonStyle.elevated:
        return ElevatedButton(
          style: style,
          onPressed: onPressed,
          onLongPress: onLongPress,
          onHover: widget.onHover,
          statesController: statesController,
          focusNode: effectiveFocusNode,
          autofocus: widget.autofocus,
          onFocusChange: widget.onFocusChange,
          child: child,
        );
      case M3EButtonStyle.outlined:
        return OutlinedButton(
          style: style,
          onPressed: onPressed,
          onLongPress: onLongPress,
          onHover: widget.onHover,
          statesController: statesController,
          focusNode: effectiveFocusNode,
          autofocus: widget.autofocus,
          onFocusChange: widget.onFocusChange,
          child: child,
        );
      case M3EButtonStyle.text:
        return TextButton(
          style: style,
          onPressed: onPressed,
          onLongPress: onLongPress,
          onHover: widget.onHover,
          statesController: statesController,
          focusNode: effectiveFocusNode,
          autofocus: widget.autofocus,
          onFocusChange: widget.onFocusChange,
          child: child,
        );
    }
  }

  Widget _wrapButtonChrome(Widget button) {
    final dec = _decoration;
    Color inkSplashColor = _selectionForegroundColor();
    if (dec?.foregroundColor != null) {
      inkSplashColor =
          dec!.foregroundColor!.resolve(
            _isSelected
                ? const <WidgetState>{WidgetState.selected}
                : const <WidgetState>{},
          ) ??
          inkSplashColor;
    }

    Widget result = M3EInkSplashTheme(color: inkSplashColor, child: button);
    if (widget.tooltip != null) {
      result = M3ETooltip(message: widget.tooltip, child: result);
    }
    if (widget.semanticLabel != null) {
      result = Semantics(label: widget.semanticLabel, child: result);
    }
    if (_usesSelection) {
      result = Semantics(selected: _isSelected, child: result);
    }
    return result;
  }
}
