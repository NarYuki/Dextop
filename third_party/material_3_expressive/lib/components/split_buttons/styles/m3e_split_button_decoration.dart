import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';

import '../../buttons/enums/m3e_button_enums.dart';
import '../../buttons/styles/m3e_button_decoration.dart';
import '../../buttons/styles/m3e_button_motion.dart';
import '../enums/m3e_split_button_menu_style.dart';
import 'm3e_split_button_bottom_sheet_decoration.dart';
import 'm3e_split_button_popup_decoration.dart';

/// M3ESplitButtonDecoration.

@immutable
class M3ESplitButtonDecoration extends M3EButtonDecoration {
  /// trailingBackgroundColor.
  final Color? trailingBackgroundColor;

  /// trailingForegroundColor.
  final Color? trailingForegroundColor;

  /// menuBackgroundColor.
  final Color? menuBackgroundColor;

  /// menuForegroundColor.
  final Color? menuForegroundColor;

  /// dividerColor.
  final Color? dividerColor;

  /// leadingCustomSize.
  final M3EButtonSize? leadingCustomSize;

  /// trailingCustomSize.
  final M3EButtonSize? trailingCustomSize;

  /// trailingSelectedRadius.
  final double? trailingSelectedRadius;

  /// gap.
  final double? gap;

  /// Optional trailing fill gradient; null inherits [backgroundGradient].
  final WidgetStateProperty<Gradient?>? trailingBackgroundGradient;

  /// Optional trailing content gradient; null inherits [foregroundGradient].
  final WidgetStateProperty<Gradient?>? trailingForegroundGradient;

  /// Optional trailing outline gradient; null inherits [outlineGradient].
  final WidgetStateProperty<Gradient?>? trailingOutlineGradient;

  /// Optional trailing overlay gradient; null inherits [overlayGradient].
  final WidgetStateProperty<Gradient?>? trailingOverlayGradient;

  /// menuStyle.

  /// menuStyle.
  final M3ESplitButtonMenuStyle menuStyle;

  /// popupDecoration.
  final M3ESplitButtonPopupDecoration? popupDecoration;

  /// bottomSheetDecoration.
  final M3ESplitButtonBottomSheetDecoration? bottomSheetDecoration;

  /// M3ESplitButtonDecoration.

  const M3ESplitButtonDecoration({
    super.backgroundColor,
    super.foregroundColor,
    super.shadowColor,
    super.elevation,
    super.side,
    super.mouseCursor,
    super.overlayColor,
    super.surfaceTintColor,
    super.iconSize,
    super.iconAlignment,
    super.textStyle,
    super.padding,
    super.minimumSize,
    super.fixedSize,
    super.maximumSize,
    super.visualDensity,
    super.tapTargetSize,
    super.animationDuration,
    super.enableFeedback,
    super.alignment,
    super.splashFactory,
    super.backgroundBuilder,
    super.backgroundGradient,
    super.foregroundGradient,
    super.overlayGradient,
    super.outlineGradient,
    super.foregroundBuilder,
    super.motion,
    super.haptic,
    super.borderRadius,
    super.hoveredRadius,
    super.pressedRadius,
    super.selectedRadius,
    super.unselectedRadius,
    super.connectedInnerRadius,
    this.trailingBackgroundColor,
    this.trailingForegroundColor,
    this.menuBackgroundColor,
    this.menuForegroundColor,
    this.dividerColor,
    this.leadingCustomSize,
    this.trailingCustomSize,
    this.trailingSelectedRadius,
    this.gap,
    this.trailingBackgroundGradient,
    this.trailingForegroundGradient,
    this.trailingOutlineGradient,
    this.trailingOverlayGradient,
    this.menuStyle = M3ESplitButtonMenuStyle.popup,
    this.popupDecoration,
    this.bottomSheetDecoration,
  });

  /// styleFrom.

  static M3ESplitButtonDecoration styleFrom({
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
    Color? trailingBackgroundColor,
    Color? trailingForegroundColor,
    Color? menuBackgroundColor,
    Color? menuForegroundColor,
    Color? dividerColor,
    M3EButtonSize? leadingCustomSize,
    M3EButtonSize? trailingCustomSize,
    double? trailingSelectedRadius,
    double? gap,
    WidgetStateProperty<Gradient?>? trailingBackgroundGradient,
    WidgetStateProperty<Gradient?>? trailingForegroundGradient,
    WidgetStateProperty<Gradient?>? trailingOutlineGradient,
    WidgetStateProperty<Gradient?>? trailingOverlayGradient,
    M3ESplitButtonMenuStyle menuStyle = M3ESplitButtonMenuStyle.popup,
    M3ESplitButtonPopupDecoration? popupDecoration,
    M3ESplitButtonBottomSheetDecoration? bottomSheetDecoration,
  }) {
    final base = M3EButtonDecoration.styleFrom(
      foregroundColor: foregroundColor,
      backgroundColor: backgroundColor,
      disabledForegroundColor: disabledForegroundColor,
      disabledBackgroundColor: disabledBackgroundColor,
      selectedForegroundColor: selectedForegroundColor,
      selectedBackgroundColor: selectedBackgroundColor,
      shadowColor: shadowColor,
      surfaceTintColor: surfaceTintColor,
      overlayColor: overlayColor,
      iconSize: iconSize,
      iconAlignment: iconAlignment,
      elevation: elevation,
      textStyle: textStyle,
      padding: padding,
      minimumSize: minimumSize,
      fixedSize: fixedSize,
      maximumSize: maximumSize,
      side: side,
      enabledMouseCursor: enabledMouseCursor,
      disabledMouseCursor: disabledMouseCursor,
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

    return _fromButtonDecoration(
      base,
      trailingBackgroundColor: trailingBackgroundColor,
      trailingForegroundColor: trailingForegroundColor,
      menuBackgroundColor: menuBackgroundColor,
      menuForegroundColor: menuForegroundColor,
      dividerColor: dividerColor,
      leadingCustomSize: leadingCustomSize,
      trailingCustomSize: trailingCustomSize,
      trailingSelectedRadius: trailingSelectedRadius,
      gap: gap,
      trailingBackgroundGradient: trailingBackgroundGradient,
      trailingForegroundGradient: trailingForegroundGradient,
      trailingOutlineGradient: trailingOutlineGradient,
      trailingOverlayGradient: trailingOverlayGradient,
      menuStyle: menuStyle,
      popupDecoration: popupDecoration,
      bottomSheetDecoration: bottomSheetDecoration,
    );
  }

  static M3ESplitButtonDecoration _fromButtonDecoration(
    M3EButtonDecoration base, {
    Color? trailingBackgroundColor,
    Color? trailingForegroundColor,
    Color? menuBackgroundColor,
    Color? menuForegroundColor,
    Color? dividerColor,
    M3EButtonSize? leadingCustomSize,
    M3EButtonSize? trailingCustomSize,
    double? trailingSelectedRadius,
    double? gap,
    WidgetStateProperty<Gradient?>? trailingBackgroundGradient,
    WidgetStateProperty<Gradient?>? trailingForegroundGradient,
    WidgetStateProperty<Gradient?>? trailingOutlineGradient,
    WidgetStateProperty<Gradient?>? trailingOverlayGradient,
    required M3ESplitButtonMenuStyle menuStyle,
    M3ESplitButtonPopupDecoration? popupDecoration,
    M3ESplitButtonBottomSheetDecoration? bottomSheetDecoration,
  }) {
    return M3ESplitButtonDecoration(
      backgroundColor: base.backgroundColor,
      foregroundColor: base.foregroundColor,
      shadowColor: base.shadowColor,
      elevation: base.elevation,
      side: base.side,
      mouseCursor: base.mouseCursor,
      overlayColor: base.overlayColor,
      surfaceTintColor: base.surfaceTintColor,
      iconSize: base.iconSize,
      iconAlignment: base.iconAlignment,
      textStyle: base.textStyle,
      padding: base.padding,
      minimumSize: base.minimumSize,
      fixedSize: base.fixedSize,
      maximumSize: base.maximumSize,
      visualDensity: base.visualDensity,
      tapTargetSize: base.tapTargetSize,
      animationDuration: base.animationDuration,
      enableFeedback: base.enableFeedback,
      alignment: base.alignment,
      splashFactory: base.splashFactory,
      backgroundBuilder: base.backgroundBuilder,
      backgroundGradient: base.backgroundGradient,
      foregroundGradient: base.foregroundGradient,
      overlayGradient: base.overlayGradient,
      outlineGradient: base.outlineGradient,
      foregroundBuilder: base.foregroundBuilder,
      motion: base.motion,
      haptic: base.haptic,
      hoveredRadius: base.hoveredRadius,
      pressedRadius: base.pressedRadius,
      selectedRadius: base.selectedRadius,
      unselectedRadius: base.unselectedRadius,
      connectedInnerRadius: base.connectedInnerRadius,
      trailingBackgroundColor: trailingBackgroundColor,
      trailingForegroundColor: trailingForegroundColor,
      menuBackgroundColor: menuBackgroundColor,
      menuForegroundColor: menuForegroundColor,
      dividerColor: dividerColor,
      leadingCustomSize: leadingCustomSize,
      trailingCustomSize: trailingCustomSize,
      trailingSelectedRadius: trailingSelectedRadius,
      gap: gap,
      trailingBackgroundGradient: trailingBackgroundGradient,
      trailingForegroundGradient: trailingForegroundGradient,
      trailingOutlineGradient: trailingOutlineGradient,
      trailingOverlayGradient: trailingOverlayGradient,
      menuStyle: menuStyle,
      popupDecoration: popupDecoration,
      bottomSheetDecoration: bottomSheetDecoration,
    );
  }

  @override
  M3ESplitButtonDecoration copyWith({
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
    Color? trailingBackgroundColor,
    Color? trailingForegroundColor,
    Color? menuBackgroundColor,
    Color? menuForegroundColor,
    Color? dividerColor,
    M3EButtonSize? leadingCustomSize,
    M3EButtonSize? trailingCustomSize,
    double? trailingSelectedRadius,
    double? gap,
    WidgetStateProperty<Gradient?>? trailingBackgroundGradient,
    WidgetStateProperty<Gradient?>? trailingForegroundGradient,
    WidgetStateProperty<Gradient?>? trailingOutlineGradient,
    WidgetStateProperty<Gradient?>? trailingOverlayGradient,
    M3ESplitButtonMenuStyle? menuStyle,
    M3ESplitButtonPopupDecoration? popupDecoration,
    M3ESplitButtonBottomSheetDecoration? bottomSheetDecoration,
  }) {
    return M3ESplitButtonDecoration(
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
      trailingBackgroundColor:
          trailingBackgroundColor ?? this.trailingBackgroundColor,
      trailingForegroundColor:
          trailingForegroundColor ?? this.trailingForegroundColor,
      menuBackgroundColor: menuBackgroundColor ?? this.menuBackgroundColor,
      menuForegroundColor: menuForegroundColor ?? this.menuForegroundColor,
      dividerColor: dividerColor ?? this.dividerColor,
      leadingCustomSize: leadingCustomSize ?? this.leadingCustomSize,
      trailingCustomSize: trailingCustomSize ?? this.trailingCustomSize,
      trailingSelectedRadius:
          trailingSelectedRadius ?? this.trailingSelectedRadius,
      gap: gap ?? this.gap,
      trailingBackgroundGradient:
          trailingBackgroundGradient ?? this.trailingBackgroundGradient,
      trailingForegroundGradient:
          trailingForegroundGradient ?? this.trailingForegroundGradient,
      trailingOutlineGradient:
          trailingOutlineGradient ?? this.trailingOutlineGradient,
      trailingOverlayGradient:
          trailingOverlayGradient ?? this.trailingOverlayGradient,
      menuStyle: menuStyle ?? this.menuStyle,
      popupDecoration: popupDecoration ?? this.popupDecoration,
      bottomSheetDecoration:
          bottomSheetDecoration ?? this.bottomSheetDecoration,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is M3ESplitButtonDecoration && listEquals(_props, other._props);

  @override
  int get hashCode => Object.hashAll(_props);

  List<Object?> get _props => <Object?>[
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
    trailingBackgroundColor,
    trailingForegroundColor,
    menuBackgroundColor,
    menuForegroundColor,
    dividerColor,
    leadingCustomSize,
    trailingCustomSize,
    trailingSelectedRadius,
    gap,
    trailingBackgroundGradient,
    trailingForegroundGradient,
    trailingOutlineGradient,
    trailingOverlayGradient,
    menuStyle,
    popupDecoration,
    bottomSheetDecoration,
  ];
}
