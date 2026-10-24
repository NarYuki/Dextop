part of 'm3e_list_item_theme.dart';

/// Field surface [_M3EListItemThemeColors] and [_M3EListItemThemeTextStyles]
/// resolve against.
///
/// Splits [M3EListItemTheme]'s color- and text-style-resolution methods into
/// this file (via mixin application, so they stay real instance members —
/// not lexically-scoped extension methods) without inflating the class body.
abstract class _M3EListItemThemeFields {
  const _M3EListItemThemeFields();

  /// See [M3EListItemTheme.containerColor].
  Color? get containerColor;

  /// See [M3EListItemTheme.selectedContainerColor].
  Color? get selectedContainerColor;

  /// See [M3EListItemTheme.labelColor].
  Color? get labelColor;

  /// See [M3EListItemTheme.supportingColor].
  Color? get supportingColor;

  /// See [M3EListItemTheme.iconColorOverride].
  Color? get iconColorOverride;

  /// See [M3EListItemTheme.selectedContentColor].
  Color? get selectedContentColor;

  /// See [M3EListItemTheme.selectedStateIconColor].
  Color? get selectedStateIconColor;

  /// See [M3EListItemTheme.avatarColor].
  Color? get avatarColor;

  /// See [M3EListItemTheme.avatarLabelColor].
  Color? get avatarLabelColor;

  /// See [M3EListItemTheme.dividerColor].
  Color? get dividerColor;

  /// See [M3EListItemTheme.focusIndicatorColor].
  Color? get focusIndicatorColor;

  /// See [M3EListItemTheme.stateLayerColor].
  Color? get stateLayerColor;

  /// See [M3EListItemTheme.hoverOpacity].
  double get hoverOpacity;

  /// See [M3EListItemTheme.focusOpacity].
  double get focusOpacity;

  /// See [M3EListItemTheme.pressedOpacity].
  double get pressedOpacity;

  /// See [M3EListItemTheme.disabledStateOpacity].
  double get disabledStateOpacity;

  /// See [M3EListItemTheme.draggedStateOpacity].
  double get draggedStateOpacity;

  /// See [M3EListItemTheme.disabledContentOpacity].
  double get disabledContentOpacity;
}

/// Color-role resolution for [M3EListItemTheme].
mixin _M3EListItemThemeColors on _M3EListItemThemeFields {
  /// Resting container color.
  Color resolveContainer(M3EColorScheme scheme) =>
      containerColor ?? scheme.surface;

  /// selectedColor.
  Color selectedColor(M3EColorScheme scheme) =>
      selectedContainerColor ?? scheme.secondaryContainer;

  /// Disabled selected container.
  Color disabledSelectedColor(M3EColorScheme scheme) =>
      scheme.onSurface.withValues(alpha: disabledContentOpacity);

  /// iconColor.
  Color iconColor(
    M3EColorScheme scheme, {
    bool selected = false,
    bool stateIcon = false,
    bool trailing = false,
  }) {
    if (selected && stateIcon) {
      return selectedStateIconColor ?? scheme.onSurface;
    }
    if (selected) {
      return selectedContentColor ?? scheme.onSecondaryContainer;
    }
    if (trailing) {
      return iconColorOverride ?? scheme.onSurface;
    }
    return iconColorOverride ?? scheme.onSurfaceVariant;
  }

  /// State-layer role color.
  Color resolveStateLayer(M3EColorScheme scheme) =>
      stateLayerColor ?? scheme.onSurface;

  /// Opacity for the active interaction, including disabled.
  double stateOpacity({
    required bool enabled,
    required bool hovered,
    required bool focused,
    required bool pressed,
    required bool dragged,
  }) {
    if (!enabled) {
      return disabledStateOpacity;
    }
    if (dragged) {
      return draggedStateOpacity;
    }
    if (pressed) {
      return pressedOpacity;
    }
    if (focused) {
      return focusOpacity;
    }
    if (hovered) {
      return hoverOpacity;
    }
    return 0;
  }

  /// Avatar container color.
  Color resolveAvatar(M3EColorScheme scheme) =>
      avatarColor ?? scheme.primaryContainer;

  /// Avatar label color.
  Color resolveAvatarLabel(M3EColorScheme scheme) =>
      avatarLabelColor ?? scheme.onPrimaryContainer;

  /// Divider color.
  Color resolveDivider(M3EColorScheme scheme) => dividerColor ?? scheme.outline;

  /// Focus ring color.
  Color resolveFocusIndicator(M3EColorScheme scheme) =>
      focusIndicatorColor ?? scheme.secondary;
}

/// Text-style resolution for [M3EListItemTheme].
mixin _M3EListItemThemeTextStyles
    on _M3EListItemThemeFields, _M3EListItemThemeColors {
  /// overlineStyle.
  TextStyle overlineStyle(
    M3ETypeScale type,
    M3EColorScheme scheme, {
    bool selected = false,
  }) => type.labelSmall.copyWith(
    color: selected
        ? (selectedContentColor ?? scheme.onSecondaryContainer)
        : (supportingColor ?? scheme.onSurfaceVariant),
  );

  /// headlineStyle.
  TextStyle headlineStyle(
    M3ETypeScale type,
    M3EColorScheme scheme, {
    bool selected = false,
  }) => type.bodyLarge.copyWith(
    color: selected
        ? (selectedContentColor ?? scheme.onSecondaryContainer)
        : (labelColor ?? scheme.onSurface),
  );

  /// supportingStyle.
  TextStyle supportingStyle(
    M3ETypeScale type,
    M3EColorScheme scheme, {
    bool selected = false,
  }) => type.bodyMedium.copyWith(
    color: selected
        ? (selectedContentColor ?? scheme.onSecondaryContainer)
        : (supportingColor ?? scheme.onSurfaceVariant),
  );

  /// Trailing meta text.
  TextStyle trailingStyle(
    M3ETypeScale type,
    M3EColorScheme scheme, {
    bool selected = false,
  }) => type.labelSmall.copyWith(
    color: selected
        ? (selectedContentColor ?? scheme.onSecondaryContainer)
        : (supportingColor ?? scheme.onSurfaceVariant),
  );

  /// Avatar initial.
  TextStyle avatarLabelStyle(M3ETypeScale type, M3EColorScheme scheme) =>
      type.titleMedium.copyWith(color: resolveAvatarLabel(scheme));
}
