part of 'm3e_card_theme.dart';

/// Color, elevation, and type-style resolution for [M3ECardTheme].
///
/// None of these override an inherited member, so they live in this
/// extension instead of the main class body. Public (not `_`-prefixed)
/// because these were public instance methods on [M3ECardTheme] before this
/// split, and stay reachable the same way for existing callers.
extension M3ECardThemeResolve on M3ECardTheme {
  /// Resolved outer radius.
  BorderRadius get borderRadius => BorderRadius.circular(radius);

  /// Collection gap clamped to [maxGap].
  double resolveGap([double? override]) {
    final double value = override ?? gap;
    if (value > maxGap) {
      return maxGap;
    }
    return value;
  }

  /// Enabled container color for [variant].
  Color backgroundColor(M3EColorScheme scheme, M3ECardVariant variant) {
    final Color? shared = containerColor;
    if (shared != null) {
      return shared;
    }
    switch (variant) {
      case M3ECardVariant.elevated:
        return elevatedContainerColor ?? scheme.surfaceContainerLow;
      case M3ECardVariant.filled:
        return filledContainerColor ?? scheme.surfaceContainerHighest;
      case M3ECardVariant.outlined:
        return outlinedContainerColor ?? scheme.surface;
    }
  }

  /// Container color, including the disabled roles.
  Color containerColorFor(
    M3EColorScheme scheme,
    M3ECardVariant variant, {
    required bool enabled,
  }) {
    if (enabled) {
      return backgroundColor(scheme, variant);
    }
    switch (variant) {
      case M3ECardVariant.elevated:
        return disabledElevatedColor ?? scheme.surface;
      case M3ECardVariant.filled:
        return disabledFilledColor ?? scheme.onSurface;
      case M3ECardVariant.outlined:
        return backgroundColor(scheme, variant);
    }
  }

  /// Enabled outline color.
  Color outlineColor(M3EColorScheme scheme) =>
      outlineColorOverride ?? scheme.outlineVariant;

  /// Outline color for the current enabled and focus state.
  Color outlineColorFor(
    M3EColorScheme scheme, {
    required bool enabled,
    required bool focused,
  }) {
    if (!enabled) {
      return disabledOutlineColor ??
          scheme.outline.withValues(alpha: disabledOutlineOpacity);
    }
    if (focused) {
      return focusedOutlineColor ?? scheme.onSurface;
    }
    return outlineColor(scheme);
  }

  /// Icon color.
  Color resolveIconColor(M3EColorScheme scheme) => iconColor ?? scheme.primary;

  /// Shadow color.
  Color resolveShadowColor(M3EColorScheme scheme) =>
      shadowColor ?? scheme.shadow;

  /// Surface tint role. Not painted on the container.
  Color resolveSurfaceTint(M3EColorScheme scheme) =>
      surfaceTint ?? scheme.primary;

  /// Focus ring color.
  Color resolveFocusColor(M3EColorScheme scheme) =>
      focusColor ?? scheme.secondary;

  /// State-layer color.
  Color resolveStateLayerColor(M3EColorScheme scheme) =>
      stateLayerColor ?? scheme.onSurface;

  /// Scrim behind text on media.
  Color resolveScrimColor(M3EColorScheme scheme) =>
      (scrimColor ?? scheme.scrim).withValues(alpha: scrimOpacity);

  /// Container-transform scrim.
  Color resolveTransformScrim(M3EColorScheme scheme) =>
      (transformScrimColor ?? scheme.scrim).withValues(alpha: scrimOpacity);

  /// Open container-transform fill.
  Color resolveTransformOpenColor(M3EColorScheme scheme) =>
      transformOpenColor ?? scheme.surface;

  /// Plate behind text on media.
  Color resolveOverlayPlate(M3EColorScheme scheme) =>
      overlayPlateColor ?? scheme.surfaceContainerHigh;

  /// Elevation for [variant] and the active interaction.
  ///
  /// Disabled wins, then dragged, pressed, focused, hovered, then resting.
  double elevation(
    M3ECardVariant variant, {
    required bool hovered,
    bool focused = false,
    bool pressed = false,
    bool dragged = false,
    bool enabled = true,
  }) {
    if (!enabled) {
      return M3EElevation.level0;
    }
    if (dragged) {
      return _draggedElevation(variant);
    }
    if (pressed) {
      return _pressedElevation(variant);
    }
    if (focused) {
      return _focusedElevation(variant);
    }
    if (hovered) {
      return _hoverElevation(variant);
    }
    return _restingElevation(variant);
  }

  double _restingElevation(M3ECardVariant variant) {
    switch (variant) {
      case M3ECardVariant.elevated:
        return elevatedElevation;
      case M3ECardVariant.filled:
        return filledElevation;
      case M3ECardVariant.outlined:
        return outlinedElevation;
    }
  }

  double _hoverElevation(M3ECardVariant variant) {
    switch (variant) {
      case M3ECardVariant.elevated:
        return elevatedHoverElevation;
      case M3ECardVariant.filled:
        return filledHoverElevation;
      case M3ECardVariant.outlined:
        return outlinedHoverElevation;
    }
  }

  double _focusedElevation(M3ECardVariant variant) {
    switch (variant) {
      case M3ECardVariant.elevated:
        return elevatedFocusElevation;
      case M3ECardVariant.filled:
        return filledFocusElevation;
      case M3ECardVariant.outlined:
        return outlinedFocusElevation;
    }
  }

  double _pressedElevation(M3ECardVariant variant) {
    switch (variant) {
      case M3ECardVariant.elevated:
        return elevatedPressedElevation;
      case M3ECardVariant.filled:
        return filledPressedElevation;
      case M3ECardVariant.outlined:
        return outlinedPressedElevation;
    }
  }

  double _draggedElevation(M3ECardVariant variant) {
    switch (variant) {
      case M3ECardVariant.elevated:
        return elevatedDraggedElevation;
      case M3ECardVariant.filled:
        return filledDraggedElevation;
      case M3ECardVariant.outlined:
        return outlinedDraggedElevation;
    }
  }

  /// Headline style.
  TextStyle headlineStyle(M3EThemeData theme) {
    return theme.typeScale.titleMedium.copyWith(
      color: headlineColor ?? theme.colorScheme.onSurface,
    );
  }

  /// Subhead style.
  TextStyle subheadStyle(M3EThemeData theme) {
    return theme.typeScale.titleSmall.copyWith(
      color: subheadColor ?? theme.colorScheme.onSurfaceVariant,
    );
  }

  /// Supporting text style.
  TextStyle supportingStyle(M3EThemeData theme) {
    return theme.typeScale.bodyMedium.copyWith(
      color: supportingColor ?? theme.colorScheme.onSurfaceVariant,
    );
  }
}
