import 'package:material_ui/material_ui.dart';

/// Styling overrides for M3EFab and M3EExtendedFab.
@immutable
class M3EFabDecoration {
  /// Creates FAB decoration.
  const M3EFabDecoration({
    this.backgroundColor,
    this.foregroundColor,
    this.overlayColor,
    this.side,
    this.backgroundGradient,
    this.foregroundGradient,
    this.overlayGradient,
    this.outlineGradient,
  });

  /// Solid container color.
  final WidgetStateProperty<Color?>? backgroundColor;

  /// Solid icon / label color.
  final WidgetStateProperty<Color?>? foregroundColor;

  /// Solid hover / focus overlay.
  final WidgetStateProperty<Color?>? overlayColor;

  /// Solid outline. Width is reused when [outlineGradient] is set.
  final WidgetStateProperty<BorderSide?>? side;

  /// Gradient fill.
  final WidgetStateProperty<Gradient?>? backgroundGradient;

  /// Gradient icon / label tint.
  final WidgetStateProperty<Gradient?>? foregroundGradient;

  /// Gradient state layer.
  final WidgetStateProperty<Gradient?>? overlayGradient;

  /// Gradient outline.
  final WidgetStateProperty<Gradient?>? outlineGradient;

  /// copyWith.
  M3EFabDecoration copyWith({
    WidgetStateProperty<Color?>? backgroundColor,
    WidgetStateProperty<Color?>? foregroundColor,
    WidgetStateProperty<Color?>? overlayColor,
    WidgetStateProperty<BorderSide?>? side,
    WidgetStateProperty<Gradient?>? backgroundGradient,
    WidgetStateProperty<Gradient?>? foregroundGradient,
    WidgetStateProperty<Gradient?>? overlayGradient,
    WidgetStateProperty<Gradient?>? outlineGradient,
  }) {
    return M3EFabDecoration(
      backgroundColor: backgroundColor ?? this.backgroundColor,
      foregroundColor: foregroundColor ?? this.foregroundColor,
      overlayColor: overlayColor ?? this.overlayColor,
      side: side ?? this.side,
      backgroundGradient: backgroundGradient ?? this.backgroundGradient,
      foregroundGradient: foregroundGradient ?? this.foregroundGradient,
      overlayGradient: overlayGradient ?? this.overlayGradient,
      outlineGradient: outlineGradient ?? this.outlineGradient,
    );
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        other is M3EFabDecoration &&
            backgroundColor == other.backgroundColor &&
            foregroundColor == other.foregroundColor &&
            overlayColor == other.overlayColor &&
            side == other.side &&
            backgroundGradient == other.backgroundGradient &&
            foregroundGradient == other.foregroundGradient &&
            overlayGradient == other.overlayGradient &&
            outlineGradient == other.outlineGradient;
  }

  @override
  int get hashCode => Object.hash(
    backgroundColor,
    foregroundColor,
    overlayColor,
    side,
    backgroundGradient,
    foregroundGradient,
    overlayGradient,
    outlineGradient,
  );
}
