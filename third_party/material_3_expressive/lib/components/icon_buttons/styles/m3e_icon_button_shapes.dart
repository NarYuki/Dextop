import 'package:material_ui/material_ui.dart';

import '../enums/m3e_icon_button_enums.dart';
import 'm3e_icon_button_theme.dart';

/// Shape resolution helpers: resting/pressed radii and toggle behavior.
class M3EIconButtonShapes {
  const M3EIconButtonShapes._();

  /// restVariant.

  static M3EIconButtonShapeVariant restVariant({
    required bool isToggle,
    required bool isSelected,
    required M3EIconButtonShapeVariant baseVariant,
  }) {
    if (isToggle && isSelected) {
      return baseVariant == M3EIconButtonShapeVariant.round
          ? M3EIconButtonShapeVariant.square
          : M3EIconButtonShapeVariant.round;
    }
    return baseVariant;
  }

  /// restingRadius.

  static double restingRadius({
    required M3EIconButtonTheme theme,
    required M3EIconButtonSize size,
    required M3EIconButtonShapeVariant variant,
  }) {
    return switch (variant) {
      M3EIconButtonShapeVariant.round => theme.radiusRestRound(size),
      M3EIconButtonShapeVariant.square => theme.radiusRestSquare(size),
    };
  }

  /// Effective corner radius for the given material states.
  ///
  /// Pressed uses [M3EIconButtonTheme.radiusPressed]; hover keeps resting
  /// (spec morphs only on press).
  static double effectiveRadius({
    required M3EIconButtonTheme theme,
    required M3EIconButtonSize size,
    required M3EIconButtonShapeVariant baseVariant,
    required bool isToggle,
    required bool isSelected,
    required Set<WidgetState> states,
  }) {
    final variant = restVariant(
      isToggle: isToggle,
      isSelected: isSelected,
      baseVariant: baseVariant,
    );

    if (states.contains(WidgetState.pressed)) {
      return theme.radiusPressed(size);
    }
    return restingRadius(theme: theme, size: size, variant: variant);
  }
}
