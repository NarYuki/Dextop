import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_chip_type.dart';

/// Theme values for M3EChip.
@immutable
class M3EChipTheme extends M3EThemeExtension<M3EChipTheme> {
  /// Creates chip theme values.
  const M3EChipTheme({
    this.height = 32,
    this.iconSize = 18,
    this.cornerRadius = 8,
    this.labelStartPadding = 16,
    this.iconStartPadding = 8,
    this.endPadding = 16,
    this.trailingEndPadding = 8,
    this.inputStartPadding = 12,
    this.avatarStartPadding = 4,
    this.avatarSize = 24,
    this.avatarRadius = 12,
    this.iconLabelGap = 8,
    this.outlineWidth = 1,
    this.disabledContainerOpacity = 0.12,
    this.disabledContentOpacity = 0.38,
    this.hoverStateLayerOpacity = 0.08,
    this.focusStateLayerOpacity = 0.1,
    this.pressedStateLayerOpacity = 0.1,
    this.draggedStateLayerOpacity = 0.16,
    this.focusIndicatorThickness = 3,
    this.focusIndicatorOffset = 2,
    this.focusIndicatorColor,
    this.elevatedElevation = M3EElevation.level1,
    this.draggedElevation = M3EElevation.level4,
    this.removeTargetSize = 48,
    this.minWidth = 88,
  });

  /// Spec defaults.
  static const M3EChipTheme defaults = M3EChipTheme();

  /// Visual chip height.
  final double height;

  /// Leading, trailing, and remove icon size.
  final double iconSize;

  /// Corner radius of the chip container.
  final double cornerRadius;

  /// Start padding when the chip has no leading icon or avatar.
  final double labelStartPadding;

  /// Start padding on the leading-icon side for assist, filter, and suggestion.
  final double iconStartPadding;

  /// End padding on the side that has no trailing icon.
  final double endPadding;

  /// End padding on the trailing-icon side.
  final double trailingEndPadding;

  /// Input-chip start padding when there is no avatar.
  final double inputStartPadding;

  /// Input-chip start padding when an avatar is shown.
  final double avatarStartPadding;

  /// Avatar width and height.
  final double avatarSize;

  /// Avatar corner radius. 12 on a 24dp avatar is a circle.
  final double avatarRadius;

  /// Gap between an icon or avatar and the label.
  final double iconLabelGap;

  /// Outline width for flat, unselected chips.
  final double outlineWidth;

  /// Opacity for a disabled outline and a disabled filled container.
  final double disabledContainerOpacity;

  /// Opacity for a disabled label and icons.
  final double disabledContentOpacity;

  /// Hover state-layer opacity.
  final double hoverStateLayerOpacity;

  /// Focus state-layer opacity.
  final double focusStateLayerOpacity;

  /// Pressed state-layer opacity.
  final double pressedStateLayerOpacity;

  /// Dragged state-layer opacity.
  final double draggedStateLayerOpacity;

  /// Focus-ring stroke width.
  final double focusIndicatorThickness;

  /// Gap between the chip and the focus ring.
  final double focusIndicatorOffset;

  /// Focus-ring color. Null uses the secondary role.
  final Color? focusIndicatorColor;

  /// Elevation for an elevated chip.
  final double elevatedElevation;

  /// Elevation while the chip is dragged.
  final double draggedElevation;

  /// Hit target for the input remove icon when it is its own control.
  final double removeTargetSize;

  /// Minimum width when a chip has both a primary action and remove.
  final double minWidth;

  /// Rounded chip shape.
  BorderRadius get borderRadius => BorderRadius.circular(cornerRadius);

  /// Outline shape used to clip ink.
  ShapeBorder get shape => RoundedRectangleBorder(borderRadius: borderRadius);

  /// Start padding for this configuration.
  double startPadding({
    required M3EChipType type,
    required bool hasLeading,
    required bool hasAvatar,
  }) {
    if (type == M3EChipType.input) {
      return hasAvatar ? avatarStartPadding : inputStartPadding;
    }
    return hasLeading ? iconStartPadding : labelStartPadding;
  }

  /// End padding. The icon side is tighter than the text side.
  double endPaddingFor({required bool hasTrailing}) {
    return hasTrailing ? trailingEndPadding : endPadding;
  }

  /// Flat chips stay at 0. Dragging replaces the elevated level.
  double elevation({required bool elevated, required bool dragged}) {
    if (dragged) {
      return draggedElevation;
    }
    if (elevated) {
      return elevatedElevation;
    }
    return M3EElevation.level0;
  }

  /// Outline width. Elevated and selected filter/input chips have none.
  double resolvedOutlineWidth({
    required M3EChipType type,
    required bool selected,
    required bool elevated,
  }) {
    if (elevated || _selected(type, selected)) {
      return 0;
    }
    return outlineWidth;
  }

  /// Container color for the chip body.
  Color containerColor(
    M3EColorScheme scheme, {
    required bool enabled,
    required bool selected,
    required bool elevated,
    required M3EChipType type,
  }) {
    final bool filled = _selected(type, selected);
    if (!enabled) {
      if (filled || elevated) {
        return M3EColorUtils.withOpacity(
          scheme.onSurface,
          disabledContainerOpacity,
        );
      }
      return const Color(0x00000000);
    }
    if (filled) {
      return scheme.secondaryContainer;
    }
    if (elevated) {
      return scheme.surfaceContainerLow;
    }
    return const Color(0x00000000);
  }

  /// Outline color. Focus uses on-surface for assist and on-surface-variant
  /// for the other types.
  Color outlineColor(
    M3EColorScheme scheme, {
    required bool enabled,
    required bool focused,
    required M3EChipType type,
  }) {
    if (!enabled) {
      return M3EColorUtils.withOpacity(
        scheme.onSurface,
        disabledContainerOpacity,
      );
    }
    if (focused && type == M3EChipType.assist) {
      return scheme.onSurface;
    }
    if (focused) {
      return scheme.onSurfaceVariant;
    }
    return scheme.outlineVariant;
  }

  /// Label color.
  Color labelColor(
    M3EColorScheme scheme, {
    required bool enabled,
    required bool selected,
    required M3EChipType type,
  }) {
    if (type == M3EChipType.assist) {
      return _content(scheme, scheme.onSurface, enabled: enabled);
    }
    if (_selected(type, selected)) {
      return _content(scheme, scheme.onSecondaryContainer, enabled: enabled);
    }
    return _content(scheme, scheme.onSurfaceVariant, enabled: enabled);
  }

  /// Leading icon color. Assist and suggestion stay primary.
  Color leadingIconColor(
    M3EColorScheme scheme, {
    required bool enabled,
    required bool selected,
    required M3EChipType type,
  }) {
    if (type == M3EChipType.assist || type == M3EChipType.suggestion) {
      return _content(scheme, scheme.primary, enabled: enabled);
    }
    if (_selected(type, selected)) {
      return _content(scheme, scheme.onSecondaryContainer, enabled: enabled);
    }
    return _content(scheme, scheme.primary, enabled: enabled);
  }

  /// Trailing icon color, including the input remove icon.
  Color trailingIconColor(
    M3EColorScheme scheme, {
    required bool enabled,
    required bool selected,
    required M3EChipType type,
  }) {
    if (_selected(type, selected)) {
      return _content(scheme, scheme.onSecondaryContainer, enabled: enabled);
    }
    if (type == M3EChipType.assist) {
      return _content(scheme, scheme.onSurface, enabled: enabled);
    }
    return _content(scheme, scheme.onSurfaceVariant, enabled: enabled);
  }

  /// State-layer role. Hover, focus, press, and drag share it.
  Color stateLayerColor(
    M3EColorScheme scheme, {
    required bool selected,
    required M3EChipType type,
  }) {
    if (type == M3EChipType.assist) {
      return scheme.onSurface;
    }
    if (_selected(type, selected)) {
      return scheme.onSecondaryContainer;
    }
    return scheme.onSurfaceVariant;
  }

  /// Opacity for the highest-priority interaction, including keyboard focus.
  double stateLayerOpacity(M3EInteractionState state) {
    if (state.dragged) {
      return draggedStateLayerOpacity;
    }
    if (state.pressed) {
      return pressedStateLayerOpacity;
    }
    if (state.focused) {
      return focusStateLayerOpacity;
    }
    if (state.hovered) {
      return hoverStateLayerOpacity;
    }
    return 0;
  }

  /// Focus ring color.
  Color resolveFocusIndicatorColor(M3EColorScheme scheme) {
    return focusIndicatorColor ?? scheme.secondary;
  }

  bool _selected(M3EChipType type, bool selected) {
    return selected &&
        (type == M3EChipType.filter || type == M3EChipType.input);
  }

  Color _content(M3EColorScheme scheme, Color color, {required bool enabled}) {
    if (!enabled) {
      return M3EColorUtils.withOpacity(
        scheme.onSurface,
        disabledContentOpacity,
      );
    }
    return color;
  }

  @override
  M3EChipTheme copyWith({
    double? height,
    double? iconSize,
    double? cornerRadius,
    double? labelStartPadding,
    double? iconStartPadding,
    double? endPadding,
    double? trailingEndPadding,
    double? inputStartPadding,
    double? avatarStartPadding,
    double? avatarSize,
    double? avatarRadius,
    double? iconLabelGap,
    double? outlineWidth,
    double? disabledContainerOpacity,
    double? disabledContentOpacity,
    double? hoverStateLayerOpacity,
    double? focusStateLayerOpacity,
    double? pressedStateLayerOpacity,
    double? draggedStateLayerOpacity,
    double? focusIndicatorThickness,
    double? focusIndicatorOffset,
    Color? focusIndicatorColor,
    double? elevatedElevation,
    double? draggedElevation,
    double? removeTargetSize,
    double? minWidth,
  }) {
    return M3EChipTheme(
      height: height ?? this.height,
      iconSize: iconSize ?? this.iconSize,
      cornerRadius: cornerRadius ?? this.cornerRadius,
      labelStartPadding: labelStartPadding ?? this.labelStartPadding,
      iconStartPadding: iconStartPadding ?? this.iconStartPadding,
      endPadding: endPadding ?? this.endPadding,
      trailingEndPadding: trailingEndPadding ?? this.trailingEndPadding,
      inputStartPadding: inputStartPadding ?? this.inputStartPadding,
      avatarStartPadding: avatarStartPadding ?? this.avatarStartPadding,
      avatarSize: avatarSize ?? this.avatarSize,
      avatarRadius: avatarRadius ?? this.avatarRadius,
      iconLabelGap: iconLabelGap ?? this.iconLabelGap,
      outlineWidth: outlineWidth ?? this.outlineWidth,
      disabledContainerOpacity:
          disabledContainerOpacity ?? this.disabledContainerOpacity,
      disabledContentOpacity:
          disabledContentOpacity ?? this.disabledContentOpacity,
      hoverStateLayerOpacity:
          hoverStateLayerOpacity ?? this.hoverStateLayerOpacity,
      focusStateLayerOpacity:
          focusStateLayerOpacity ?? this.focusStateLayerOpacity,
      pressedStateLayerOpacity:
          pressedStateLayerOpacity ?? this.pressedStateLayerOpacity,
      draggedStateLayerOpacity:
          draggedStateLayerOpacity ?? this.draggedStateLayerOpacity,
      focusIndicatorThickness:
          focusIndicatorThickness ?? this.focusIndicatorThickness,
      focusIndicatorOffset: focusIndicatorOffset ?? this.focusIndicatorOffset,
      focusIndicatorColor: focusIndicatorColor ?? this.focusIndicatorColor,
      elevatedElevation: elevatedElevation ?? this.elevatedElevation,
      draggedElevation: draggedElevation ?? this.draggedElevation,
      removeTargetSize: removeTargetSize ?? this.removeTargetSize,
      minWidth: minWidth ?? this.minWidth,
    );
  }

  @override
  M3EChipTheme lerp(M3EChipTheme? other, double t) {
    if (other is! M3EChipTheme) {
      return this;
    }
    return _lerpChipLayout(other, t);
  }

  M3EChipTheme _lerpChipLayout(M3EChipTheme other, double t) {
    return _lerpChipFeedback(other, t).copyWith(
      height: _lerpDouble(height, other.height, t),
      iconSize: _lerpDouble(iconSize, other.iconSize, t),
      cornerRadius: _lerpDouble(cornerRadius, other.cornerRadius, t),
      labelStartPadding: _lerpDouble(
        labelStartPadding,
        other.labelStartPadding,
        t,
      ),
      iconStartPadding: _lerpDouble(
        iconStartPadding,
        other.iconStartPadding,
        t,
      ),
      endPadding: _lerpDouble(endPadding, other.endPadding, t),
      trailingEndPadding: _lerpDouble(
        trailingEndPadding,
        other.trailingEndPadding,
        t,
      ),
      inputStartPadding: _lerpDouble(
        inputStartPadding,
        other.inputStartPadding,
        t,
      ),
      avatarStartPadding: _lerpDouble(
        avatarStartPadding,
        other.avatarStartPadding,
        t,
      ),
      avatarSize: _lerpDouble(avatarSize, other.avatarSize, t),
      avatarRadius: _lerpDouble(avatarRadius, other.avatarRadius, t),
    );
  }

  M3EChipTheme _lerpChipFeedback(M3EChipTheme other, double t) {
    return copyWith(
      iconLabelGap: _lerpDouble(iconLabelGap, other.iconLabelGap, t),
      outlineWidth: _lerpDouble(outlineWidth, other.outlineWidth, t),
      disabledContainerOpacity: _lerpDouble(
        disabledContainerOpacity,
        other.disabledContainerOpacity,
        t,
      ),
      disabledContentOpacity: _lerpDouble(
        disabledContentOpacity,
        other.disabledContentOpacity,
        t,
      ),
      hoverStateLayerOpacity: _lerpDouble(
        hoverStateLayerOpacity,
        other.hoverStateLayerOpacity,
        t,
      ),
      focusStateLayerOpacity: _lerpDouble(
        focusStateLayerOpacity,
        other.focusStateLayerOpacity,
        t,
      ),
      pressedStateLayerOpacity: _lerpDouble(
        pressedStateLayerOpacity,
        other.pressedStateLayerOpacity,
        t,
      ),
      draggedStateLayerOpacity: _lerpDouble(
        draggedStateLayerOpacity,
        other.draggedStateLayerOpacity,
        t,
      ),
      focusIndicatorThickness: _lerpDouble(
        focusIndicatorThickness,
        other.focusIndicatorThickness,
        t,
      ),
      focusIndicatorOffset: _lerpDouble(
        focusIndicatorOffset,
        other.focusIndicatorOffset,
        t,
      ),
      focusIndicatorColor:
          Color.lerp(focusIndicatorColor, other.focusIndicatorColor, t) ??
          focusIndicatorColor ??
          other.focusIndicatorColor,
      elevatedElevation: _lerpDouble(
        elevatedElevation,
        other.elevatedElevation,
        t,
      ),
      draggedElevation: _lerpDouble(
        draggedElevation,
        other.draggedElevation,
        t,
      ),
      removeTargetSize: _lerpDouble(
        removeTargetSize,
        other.removeTargetSize,
        t,
      ),
      minWidth: _lerpDouble(minWidth, other.minWidth, t),
    );
  }

  double _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
