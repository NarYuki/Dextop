import 'package:material_3_expressive/material_3_expressive.dart'
    show M3EButton;
import 'package:material_ui/material_ui.dart';

import '../enums/m3e_button_enums.dart';
import 'm3e_button_motion.dart';

/// Styling overrides for [M3EButton].
@immutable
class M3EButtonDecoration {
  /// backgroundColor.
  final WidgetStateProperty<Color?>? backgroundColor;

  /// foregroundColor.
  final WidgetStateProperty<Color?>? foregroundColor;

  /// shadowColor.
  final WidgetStateProperty<Color?>? shadowColor;

  /// elevation.
  final WidgetStateProperty<double?>? elevation;

  /// side.
  final WidgetStateProperty<BorderSide?>? side;

  /// mouseCursor.
  final WidgetStateProperty<MouseCursor?>? mouseCursor;

  /// overlayColor.
  final WidgetStateProperty<Color?>? overlayColor;

  /// surfaceTintColor.
  final WidgetStateProperty<Color?>? surfaceTintColor;

  /// iconSize.
  final double? iconSize;

  /// iconAlignment.
  final IconAlignment? iconAlignment;

  /// textStyle.
  final TextStyle? textStyle;

  /// padding.
  final EdgeInsetsGeometry? padding;

  /// minimumSize.
  final Size? minimumSize;

  /// fixedSize.
  final Size? fixedSize;

  /// maximumSize.
  final Size? maximumSize;

  /// visualDensity.
  final VisualDensity? visualDensity;

  /// tapTargetSize.
  final MaterialTapTargetSize? tapTargetSize;

  /// animationDuration.
  final Duration? animationDuration;

  /// enableFeedback.
  final bool? enableFeedback;

  /// alignment.
  final AlignmentGeometry? alignment;

  /// splashFactory.
  final InteractiveInkFeatureFactory? splashFactory;

  /// backgroundBuilder.
  final ButtonLayerBuilder? backgroundBuilder;

  /// Optional gradient fill. When set (and [backgroundBuilder] is null), paints
  /// behind the button content with a transparent Material background.
  final WidgetStateProperty<Gradient?>? backgroundGradient;

  /// Optional gradient for text and icons.
  final WidgetStateProperty<Gradient?>? foregroundGradient;

  /// Optional gradient state layer (hover / focus / pressed).
  final WidgetStateProperty<Gradient?>? overlayGradient;

  /// Optional gradient outline. Solid [side] still supplies stroke width.
  final WidgetStateProperty<Gradient?>? outlineGradient;

  /// foregroundBuilder.
  final ButtonLayerBuilder? foregroundBuilder;

  /// motion.
  final M3EButtonMotion? motion;

  /// haptic.
  final M3EHapticFeedback? haptic;

  /// borderRadius.
  final double? borderRadius;

  /// hoveredRadius.
  final double? hoveredRadius;

  /// pressedRadius.
  final double? pressedRadius;

  /// selectedRadius.
  final double? selectedRadius;

  /// unselectedRadius.
  final double? unselectedRadius;

  /// connectedInnerRadius.
  final double? connectedInnerRadius;

  /// M3EButtonDecoration.

  const M3EButtonDecoration({
    this.backgroundColor,
    this.foregroundColor,
    this.shadowColor,
    this.elevation,
    this.side,
    this.mouseCursor,
    this.overlayColor,
    this.surfaceTintColor,
    this.iconSize,
    this.iconAlignment,
    this.textStyle,
    this.padding,
    this.minimumSize,
    this.fixedSize,
    this.maximumSize,
    this.visualDensity,
    this.tapTargetSize,
    this.animationDuration,
    this.enableFeedback,
    this.alignment,
    this.splashFactory,
    this.backgroundBuilder,
    this.backgroundGradient,
    this.foregroundGradient,
    this.overlayGradient,
    this.outlineGradient,
    this.foregroundBuilder,
    this.motion,
    this.haptic,
    this.borderRadius,
    this.hoveredRadius,
    this.pressedRadius,
    this.selectedRadius,
    this.unselectedRadius,
    this.connectedInnerRadius,
  });

  /// styleFrom.

  static M3EButtonDecoration styleFrom({
    Color? foregroundColor,
    Color? backgroundColor,
    Color? disabledForegroundColor,
    Color? disabledBackgroundColor,
    Color? selectedForegroundColor,
    Color? selectedBackgroundColor,
    Color? shadowColor,
    Color? surfaceTintColor,
    Color? overlayColor,
    double? iconSize,
    IconAlignment? iconAlignment,
    double? elevation,
    TextStyle? textStyle,
    EdgeInsetsGeometry? padding,
    Size? minimumSize,
    Size? fixedSize,
    Size? maximumSize,
    BorderSide? side,
    MouseCursor? enabledMouseCursor,
    MouseCursor? disabledMouseCursor,
    VisualDensity? visualDensity,
    MaterialTapTargetSize? tapTargetSize,
    Duration? animationDuration,
    bool? enableFeedback,
    AlignmentGeometry? alignment,
    InteractiveInkFeatureFactory? splashFactory,
    ButtonLayerBuilder? backgroundBuilder,
    WidgetStateProperty<Gradient?>? backgroundGradient,
    WidgetStateProperty<Gradient?>? foregroundGradient,
    WidgetStateProperty<Gradient?>? overlayGradient,
    WidgetStateProperty<Gradient?>? outlineGradient,
    ButtonLayerBuilder? foregroundBuilder,
    M3EButtonMotion? motion,
    M3EHapticFeedback? haptic,
    double? borderRadius,
    double? hoveredRadius,
    double? pressedRadius,
    double? selectedRadius,
    double? unselectedRadius,
    double? connectedInnerRadius,
  }) {
    final _StyleFromColors colors = _styleFromColors(
      backgroundColor: backgroundColor,
      disabledBackgroundColor: disabledBackgroundColor,
      selectedBackgroundColor: selectedBackgroundColor,
      foregroundColor: foregroundColor,
      disabledForegroundColor: disabledForegroundColor,
      selectedForegroundColor: selectedForegroundColor,
      shadowColor: shadowColor,
      surfaceTintColor: surfaceTintColor,
      overlayColor: overlayColor,
      elevation: elevation,
      side: side,
      enabledMouseCursor: enabledMouseCursor,
      disabledMouseCursor: disabledMouseCursor,
    );

    return M3EButtonDecoration(
      backgroundColor: colors.backgroundColor,
      foregroundColor: colors.foregroundColor,
      shadowColor: colors.shadowColor,
      surfaceTintColor: colors.surfaceTintColor,
      overlayColor: colors.overlayColor,
      elevation: colors.elevation,
      side: colors.side,
      mouseCursor: colors.mouseCursor,
      iconSize: iconSize,
      iconAlignment: iconAlignment,
      textStyle: textStyle,
      padding: padding,
      minimumSize: minimumSize,
      fixedSize: fixedSize,
      maximumSize: maximumSize,
      visualDensity: visualDensity,
      tapTargetSize: tapTargetSize,
      animationDuration: animationDuration,
      enableFeedback: enableFeedback,
      alignment: alignment,
      splashFactory: splashFactory,
      backgroundBuilder: backgroundBuilder,
      backgroundGradient: backgroundGradient,
      foregroundGradient: foregroundGradient,
      overlayGradient: overlayGradient,
      outlineGradient: outlineGradient,
      foregroundBuilder: foregroundBuilder,
      motion: motion,
      haptic: haptic,
      borderRadius: borderRadius,
      hoveredRadius: hoveredRadius,
      pressedRadius: pressedRadius,
      selectedRadius: selectedRadius,
      unselectedRadius: unselectedRadius,
      connectedInnerRadius: connectedInnerRadius,
    );
  }

  /// Returns this with every non-null field of [other] applied on top.
  M3EButtonDecoration merge(M3EButtonDecoration? other) {
    if (other == null) {
      return this;
    }
    return copyWith(
      backgroundColor: other.backgroundColor,
      foregroundColor: other.foregroundColor,
      shadowColor: other.shadowColor,
      elevation: other.elevation,
      side: other.side,
      mouseCursor: other.mouseCursor,
      overlayColor: other.overlayColor,
      surfaceTintColor: other.surfaceTintColor,
      iconSize: other.iconSize,
      iconAlignment: other.iconAlignment,
      textStyle: other.textStyle,
      padding: other.padding,
      minimumSize: other.minimumSize,
      fixedSize: other.fixedSize,
      maximumSize: other.maximumSize,
      visualDensity: other.visualDensity,
      tapTargetSize: other.tapTargetSize,
      animationDuration: other.animationDuration,
      enableFeedback: other.enableFeedback,
      alignment: other.alignment,
      splashFactory: other.splashFactory,
      backgroundBuilder: other.backgroundBuilder,
      backgroundGradient: other.backgroundGradient,
      foregroundGradient: other.foregroundGradient,
      overlayGradient: other.overlayGradient,
      outlineGradient: other.outlineGradient,
      foregroundBuilder: other.foregroundBuilder,
      motion: other.motion,
      haptic: other.haptic,
      borderRadius: other.borderRadius,
      hoveredRadius: other.hoveredRadius,
      pressedRadius: other.pressedRadius,
      selectedRadius: other.selectedRadius,
      unselectedRadius: other.unselectedRadius,
      connectedInnerRadius: other.connectedInnerRadius,
    );
  }

  /// copyWith.

  M3EButtonDecoration copyWith({
    WidgetStateProperty<Color?>? backgroundColor,
    WidgetStateProperty<Color?>? foregroundColor,
    WidgetStateProperty<Color?>? shadowColor,
    WidgetStateProperty<double?>? elevation,
    WidgetStateProperty<BorderSide?>? side,
    WidgetStateProperty<MouseCursor?>? mouseCursor,
    WidgetStateProperty<Color?>? overlayColor,
    WidgetStateProperty<Color?>? surfaceTintColor,
    double? iconSize,
    IconAlignment? iconAlignment,
    TextStyle? textStyle,
    EdgeInsetsGeometry? padding,
    Size? minimumSize,
    Size? fixedSize,
    Size? maximumSize,
    VisualDensity? visualDensity,
    MaterialTapTargetSize? tapTargetSize,
    Duration? animationDuration,
    bool? enableFeedback,
    AlignmentGeometry? alignment,
    InteractiveInkFeatureFactory? splashFactory,
    ButtonLayerBuilder? backgroundBuilder,
    WidgetStateProperty<Gradient?>? backgroundGradient,
    WidgetStateProperty<Gradient?>? foregroundGradient,
    WidgetStateProperty<Gradient?>? overlayGradient,
    WidgetStateProperty<Gradient?>? outlineGradient,
    ButtonLayerBuilder? foregroundBuilder,
    M3EButtonMotion? motion,
    M3EHapticFeedback? haptic,
    double? borderRadius,
    double? hoveredRadius,
    double? pressedRadius,
    double? selectedRadius,
    double? unselectedRadius,
    double? connectedInnerRadius,
  }) {
    return M3EButtonDecoration(
      backgroundColor: backgroundColor ?? this.backgroundColor,
      foregroundColor: foregroundColor ?? this.foregroundColor,
      shadowColor: shadowColor ?? this.shadowColor,
      elevation: elevation ?? this.elevation,
      side: side ?? this.side,
      mouseCursor: mouseCursor ?? this.mouseCursor,
      overlayColor: overlayColor ?? this.overlayColor,
      surfaceTintColor: surfaceTintColor ?? this.surfaceTintColor,
      iconSize: iconSize ?? this.iconSize,
      iconAlignment: iconAlignment ?? this.iconAlignment,
      textStyle: textStyle ?? this.textStyle,
      padding: padding ?? this.padding,
      minimumSize: minimumSize ?? this.minimumSize,
      fixedSize: fixedSize ?? this.fixedSize,
      maximumSize: maximumSize ?? this.maximumSize,
      visualDensity: visualDensity ?? this.visualDensity,
      tapTargetSize: tapTargetSize ?? this.tapTargetSize,
      animationDuration: animationDuration ?? this.animationDuration,
      enableFeedback: enableFeedback ?? this.enableFeedback,
      alignment: alignment ?? this.alignment,
      splashFactory: splashFactory ?? this.splashFactory,
      backgroundBuilder: backgroundBuilder ?? this.backgroundBuilder,
      backgroundGradient: backgroundGradient ?? this.backgroundGradient,
      foregroundGradient: foregroundGradient ?? this.foregroundGradient,
      overlayGradient: overlayGradient ?? this.overlayGradient,
      outlineGradient: outlineGradient ?? this.outlineGradient,
      foregroundBuilder: foregroundBuilder ?? this.foregroundBuilder,
      motion: motion ?? this.motion,
      haptic: haptic ?? this.haptic,
      borderRadius: borderRadius ?? this.borderRadius,
      hoveredRadius: hoveredRadius ?? this.hoveredRadius,
      pressedRadius: pressedRadius ?? this.pressedRadius,
      selectedRadius: selectedRadius ?? this.selectedRadius,
      unselectedRadius: unselectedRadius ?? this.unselectedRadius,
      connectedInnerRadius: connectedInnerRadius ?? this.connectedInnerRadius,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is M3EButtonDecoration &&
          backgroundColor == other.backgroundColor &&
          foregroundColor == other.foregroundColor &&
          shadowColor == other.shadowColor &&
          elevation == other.elevation &&
          side == other.side &&
          mouseCursor == other.mouseCursor &&
          overlayColor == other.overlayColor &&
          surfaceTintColor == other.surfaceTintColor &&
          iconSize == other.iconSize &&
          iconAlignment == other.iconAlignment &&
          textStyle == other.textStyle &&
          padding == other.padding &&
          minimumSize == other.minimumSize &&
          fixedSize == other.fixedSize &&
          maximumSize == other.maximumSize &&
          visualDensity == other.visualDensity &&
          tapTargetSize == other.tapTargetSize &&
          animationDuration == other.animationDuration &&
          enableFeedback == other.enableFeedback &&
          alignment == other.alignment &&
          splashFactory == other.splashFactory &&
          backgroundBuilder == other.backgroundBuilder &&
          backgroundGradient == other.backgroundGradient &&
          foregroundGradient == other.foregroundGradient &&
          overlayGradient == other.overlayGradient &&
          outlineGradient == other.outlineGradient &&
          foregroundBuilder == other.foregroundBuilder &&
          motion == other.motion &&
          haptic == other.haptic &&
          borderRadius == other.borderRadius &&
          hoveredRadius == other.hoveredRadius &&
          pressedRadius == other.pressedRadius &&
          selectedRadius == other.selectedRadius &&
          unselectedRadius == other.unselectedRadius &&
          connectedInnerRadius == other.connectedInnerRadius;

  @override
  int get hashCode => Object.hashAll([
    backgroundColor,
    foregroundColor,
    shadowColor,
    elevation,
    side,
    mouseCursor,
    overlayColor,
    surfaceTintColor,
    iconSize,
    iconAlignment,
    textStyle,
    padding,
    minimumSize,
    fixedSize,
    maximumSize,
    visualDensity,
    tapTargetSize,
    animationDuration,
    enableFeedback,
    alignment,
    splashFactory,
    backgroundBuilder,
    backgroundGradient,
    foregroundGradient,
    overlayGradient,
    outlineGradient,
    foregroundBuilder,
    motion,
    haptic,
    borderRadius,
    hoveredRadius,
    pressedRadius,
    selectedRadius,
    unselectedRadius,
    connectedInnerRadius,
  ]);
}

@immutable
class _StyleFromColorProperty implements WidgetStateProperty<Color?> {
  const _StyleFromColorProperty(
    this.color,
    this.disabledColor,
    this.selectedColor,
  );
  final Color? color;
  final Color? disabledColor;
  final Color? selectedColor;

  @override
  Color? resolve(Set<WidgetState> states) {
    if (states.contains(WidgetState.disabled)) {
      return disabledColor;
    }
    if (states.contains(WidgetState.selected)) {
      return selectedColor ?? color;
    }
    return color;
  }
}

@immutable
class _StyleFromCursorProperty implements WidgetStateProperty<MouseCursor?> {
  const _StyleFromCursorProperty(this.enabledCursor, this.disabledCursor);
  final MouseCursor? enabledCursor;
  final MouseCursor? disabledCursor;

  @override
  MouseCursor? resolve(Set<WidgetState> states) {
    if (states.contains(WidgetState.disabled)) {
      return disabledCursor;
    }
    return enabledCursor;
  }
}

typedef _StyleFromColors = ({
  WidgetStateProperty<Color?>? backgroundColor,
  WidgetStateProperty<Color?>? foregroundColor,
  WidgetStateProperty<Color?>? shadowColor,
  WidgetStateProperty<Color?>? surfaceTintColor,
  WidgetStateProperty<Color?>? overlayColor,
  WidgetStateProperty<double?>? elevation,
  WidgetStateProperty<BorderSide?>? side,
  WidgetStateProperty<MouseCursor?>? mouseCursor,
});

_StyleFromColors _styleFromColors({
  Color? backgroundColor,
  Color? disabledBackgroundColor,
  Color? selectedBackgroundColor,
  Color? foregroundColor,
  Color? disabledForegroundColor,
  Color? selectedForegroundColor,
  Color? shadowColor,
  Color? surfaceTintColor,
  Color? overlayColor,
  double? elevation,
  BorderSide? side,
  MouseCursor? enabledMouseCursor,
  MouseCursor? disabledMouseCursor,
}) {
  return (
    backgroundColor:
        (backgroundColor == null &&
            disabledBackgroundColor == null &&
            selectedBackgroundColor == null)
        ? null
        : _StyleFromColorProperty(
            backgroundColor,
            disabledBackgroundColor,
            selectedBackgroundColor,
          ),
    foregroundColor:
        (foregroundColor == null &&
            disabledForegroundColor == null &&
            selectedForegroundColor == null)
        ? null
        : _StyleFromColorProperty(
            foregroundColor,
            disabledForegroundColor,
            selectedForegroundColor,
          ),
    shadowColor: shadowColor == null
        ? null
        : WidgetStatePropertyAll<Color?>(shadowColor),
    surfaceTintColor: surfaceTintColor == null
        ? null
        : WidgetStatePropertyAll<Color?>(surfaceTintColor),
    overlayColor: overlayColor == null
        ? null
        : WidgetStatePropertyAll<Color?>(overlayColor),
    elevation: elevation == null
        ? null
        : WidgetStatePropertyAll<double?>(elevation),
    side: side == null ? null : WidgetStatePropertyAll<BorderSide?>(side),
    mouseCursor: (enabledMouseCursor == null && disabledMouseCursor == null)
        ? null
        : _StyleFromCursorProperty(enabledMouseCursor, disabledMouseCursor),
  );
}
