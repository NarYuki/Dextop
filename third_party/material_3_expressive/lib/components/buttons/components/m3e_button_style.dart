// ButtonStyle assembly extracted for function_length / cognitive_complexity.
part of '../m3e_buttons.dart';

extension _M3EButtonStyle on _M3EButtonState {
  ButtonStyle _buildBaseStyle() {
    final dec = _decoration;
    final effectivePadding = dec?.padding != null
        ? WidgetStateProperty.all<EdgeInsetsGeometry>(dec!.padding!)
        : null;
    final defaultMinSize = Size(
      _buttonTheme.minWidthFloor,
      _measurements.height,
    );
    final effectiveMinSize = dec?.minimumSize != null
        ? WidgetStateProperty.all(dec!.minimumSize!)
        : WidgetStateProperty.all(defaultMinSize);

    return ButtonStyle(
      alignment: dec?.alignment ?? _kAlignmentCenter,
      padding: effectivePadding,
      textStyle: WidgetStateProperty.all(dec?.textStyle ?? labelStyle),
      minimumSize: effectiveMinSize,
      fixedSize: dec?.fixedSize != null
          ? WidgetStateProperty.all(dec!.fixedSize)
          : null,
      maximumSize: dec?.maximumSize != null
          ? WidgetStateProperty.all(dec!.maximumSize)
          : null,
      iconSize: dec?.iconSize != null
          ? WidgetStateProperty.all(dec!.iconSize)
          : null,
      iconAlignment: dec?.iconAlignment,
      shadowColor: dec?.shadowColor,
      visualDensity: dec?.visualDensity ?? _kVisualDensityStandard,
      tapTargetSize: dec?.tapTargetSize,
      animationDuration: dec?.animationDuration ?? _kDurationZero,
      splashFactory: m3eUsesGradientOverlay(dec?.overlayGradient)
          ? NoSplash.splashFactory
          : (dec?.splashFactory ??
                widget.splashFactory ??
                InkSparkle.splashFactory),
      foregroundColor: WidgetStateProperty.resolveWith(_resolveForegroundColor),
      backgroundColor: WidgetStateProperty.resolveWith(_resolveBackgroundColor),
      elevation: WidgetStateProperty.resolveWith(_resolveElevation),
      side: WidgetStateProperty.resolveWith(_resolveSide),
      mouseCursor: WidgetStateProperty.resolveWith(_resolveMouseCursor),
      // Ink highlights resolve hover and focus with the same state set and
      // keep their first color, so the state layer is painted by
      // [_withStateLayer] from live states instead.
      overlayColor: const WidgetStatePropertyAll<Color?>(Colors.transparent),
      surfaceTintColor: dec?.surfaceTintColor,
      enableFeedback:
          (dec?.haptic ?? M3EHapticFeedback.none) == M3EHapticFeedback.none &&
          (dec?.enableFeedback ?? widget.enableFeedback),
    );
  }

  Color? _resolveForegroundColor(Set<WidgetState> states) {
    final dec = _decoration;
    final activeStates = _selectionStates(states);
    if (dec?.foregroundGradient?.resolve(activeStates) != null) {
      return m3eGradientForegroundSourceColor;
    }
    if (dec?.foregroundColor != null) {
      final color = dec!.foregroundColor!.resolve(activeStates);
      if (color != null) {
        return color;
      }
    }
    if (states.contains(WidgetState.disabled)) {
      return _scheme.onSurface.withValues(
        alpha: M3EButtonConstants.kDisabledForegroundAlpha,
      );
    }
    return _selectionForegroundColor();
  }

  Color? _resolveBackgroundColor(Set<WidgetState> states) {
    final dec = _decoration;
    final activeStates = _selectionStates(states);
    if (dec?.backgroundColor != null) {
      final color = dec!.backgroundColor!.resolve(activeStates);
      if (color != null) {
        return color;
      }
    }
    final isTransparent =
        widget.style == M3EButtonStyle.text ||
        (widget.style == M3EButtonStyle.outlined &&
            !(_usesSelection && _isSelected));

    if (states.contains(WidgetState.disabled)) {
      return isTransparent
          ? Colors.transparent
          : _scheme.onSurface.withValues(
              alpha: M3EButtonConstants.kDisabledBackgroundAlpha,
            );
    }
    return (dec?.backgroundBuilder != null ||
            dec?.backgroundGradient != null ||
            isTransparent)
        ? Colors.transparent
        : _selectionBackgroundColor();
  }

  double? _resolveElevation(Set<WidgetState> states) {
    final dec = _decoration;
    if (dec?.elevation != null) {
      final e = dec!.elevation!.resolve(states);
      if (e != null) {
        return e;
      }
    }
    return _buttonTheme.elevation(widget.style, states);
  }

  BorderSide? _resolveSide(Set<WidgetState> states) {
    final dec = _decoration;
    final activeStates = _selectionStates(states);
    if (dec?.outlineGradient?.resolve(activeStates) != null) {
      return BorderSide.none;
    }
    if (dec?.side != null) {
      final s = dec!.side!.resolve(activeStates);
      if (s != null) {
        return s;
      }
    }
    if (widget.style != M3EButtonStyle.outlined) {
      return BorderSide.none;
    }
    if (_usesSelection && _isSelected) {
      return BorderSide.none;
    }
    final width = _measurements.outlineWidth;
    if (states.contains(WidgetState.disabled)) {
      return BorderSide(
        color: _scheme.onSurface.withValues(
          alpha: M3EButtonConstants.kDisabledOutlineAlpha,
        ),
        width: width,
      );
    }
    return BorderSide(color: _buttonTheme.outline(_scheme), width: width);
  }

  MouseCursor? _resolveMouseCursor(Set<WidgetState> states) {
    final dec = _decoration;
    if (dec?.mouseCursor != null) {
      final cursor = dec!.mouseCursor!.resolve(states);
      if (cursor != null) {
        return cursor;
      }
    }
    if (states.contains(WidgetState.disabled)) {
      return SystemMouseCursors.basic;
    }
    return widget.mouseCursor;
  }

  /// State layer color for live [states] (pressed over focus over hover).
  ///
  /// Press shows only while held. Focus shows only for keyboard focus (the
  /// same rule as the ring), so a click that focuses the button leaves no
  /// fill. A decoration `overlayColor` gets the same gated states.
  Color? _stateLayerColor(Set<WidgetState> states) {
    final M3EButtonDecoration? dec = _decoration;
    if (states.contains(WidgetState.disabled) ||
        m3eUsesGradientOverlay(dec?.overlayGradient)) {
      return null;
    }
    final live = <WidgetState>{
      ...states.difference(const <WidgetState>{WidgetState.focused}),
      if (isFocused) WidgetState.focused,
    };
    final WidgetStateProperty<Color?>? custom = dec?.overlayColor;
    if (custom != null) {
      return custom.resolve(live);
    }
    final Color foreground =
        dec?.foregroundColor?.resolve(_selectionStates(states)) ??
        _selectionForegroundColor();
    final double opacity = live.contains(WidgetState.pressed)
        ? M3EStateOpacity.pressed
        : live.contains(WidgetState.focused)
        ? M3EStateOpacity.focus
        : live.contains(WidgetState.hovered)
        ? M3EStateOpacity.hover
        : 0;
    return opacity == 0 ? null : foreground.withValues(alpha: opacity);
  }

  /// Paints the state layer under the button content, clipped to [radius].
  ButtonLayerBuilder _withStateLayer(
    BorderRadius radius,
    ButtonLayerBuilder? surface,
  ) {
    return (BuildContext context, Set<WidgetState> states, Widget? child) {
      final Widget inner = surface?.call(context, states, child) ?? child!;
      final Color color = _stateLayerColor(states) ?? Colors.transparent;
      return Stack(
        fit: StackFit.passthrough,
        children: <Widget>[
          Positioned.fill(
            child: IgnorePointer(
              child: ClipRRect(
                borderRadius: radius,
                child: TweenAnimationBuilder<Color?>(
                  tween: ColorTween(end: color),
                  duration: M3EMotion.short3,
                  curve: M3EMotion.standard,
                  builder: (BuildContext context, Color? value, _) =>
                      ColoredBox(color: value ?? Colors.transparent),
                ),
              ),
            ),
          ),
          inner,
        ],
      );
    };
  }

  Set<WidgetState> _selectionStates(Set<WidgetState> states) {
    if (!_usesSelection || !_isSelected) {
      return states;
    }
    return {...states, WidgetState.selected};
  }

  Color _selectionForegroundColor() {
    if (!_usesSelection) {
      return _buttonTheme.foreground(_scheme, widget.style);
    }
    return switch (widget.style) {
      M3EButtonStyle.elevated =>
        _isSelected ? _scheme.onPrimary : _scheme.primary,
      M3EButtonStyle.filled =>
        _isSelected ? _scheme.onPrimary : _scheme.onSurfaceVariant,
      M3EButtonStyle.tonal =>
        _isSelected ? _scheme.onSecondary : _scheme.onSecondaryContainer,
      M3EButtonStyle.outlined =>
        _isSelected ? _scheme.onInverseSurface : _scheme.onSurfaceVariant,
      M3EButtonStyle.text => _scheme.primary,
    };
  }

  Color _selectionBackgroundColor() {
    if (!_usesSelection) {
      return _buttonTheme.container(_scheme, widget.style);
    }
    return switch (widget.style) {
      M3EButtonStyle.elevated =>
        _isSelected ? _scheme.primary : _scheme.surfaceContainerLow,
      M3EButtonStyle.filled =>
        _isSelected ? _scheme.primary : _scheme.surfaceContainer,
      M3EButtonStyle.tonal =>
        _isSelected ? _scheme.secondary : _scheme.secondaryContainer,
      M3EButtonStyle.outlined =>
        _isSelected ? _scheme.inverseSurface : Colors.transparent,
      M3EButtonStyle.text => Colors.transparent,
    };
  }
}
