import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import 'm3e_dialog_appearance.dart';
import 'm3e_full_screen_dialog_theme.dart';

/// Theme values for `M3EDialog`.
///
/// Defaults follow the M3 "Dialog - Basic" and "Dialog - Full screen" specs.
@immutable
class M3EDialogTheme extends M3EThemeExtension<M3EDialogTheme> {
  /// M3EDialogTheme.
  const M3EDialogTheme({
    this.minWidth = 280,
    this.maxWidth = 560,
    this.padding = const EdgeInsets.all(24),
    this.screenMargin = const EdgeInsets.all(24),
    this.entranceScale = 0.9,
    this.iconSize = 24,
    this.gapAfterIcon = 16,
    this.gapAfterTitle = 16,
    this.gapBeforeActions = 24,
    this.actionGap = 8,
    this.selectionItemHeight = 56,
    this.fullScreenHeaderHeight = 56,
    this.headerEdgeGap = 4,
    this.closeButtonPadding = const EdgeInsets.all(12),
    this.headerActionGap = 16,
    this.scrimOpacity = 0.32,
    this.resizeToAvoidBottomInset = true,
    this.insetAnimationDuration = const Duration(milliseconds: 100),
    this.insetAnimationCurve = Curves.decelerate,
    this.elevation = M3EElevation.level3,
    this.cornerRadius = 28,
    this.dividerThickness = 1,
    this.stackedActionGap = 8,
    this.compactBreakpoint = 600,
    this.customPositionMargin = 56,
    this.enterSpring = M3EMotion.spatialDefault,
    this.fadeSpring = M3EMotion.effectsDefault,
    this.appearance = const M3EDialogAppearance(),
    this.fullScreen = const M3EFullScreenDialogTheme(),
  });

  /// defaults.

  static const M3EDialogTheme defaults = M3EDialogTheme();

  /// minWidth.

  final double minWidth;

  /// maxWidth.
  final double maxWidth;

  /// Container padding: 24 on every side.
  final EdgeInsets padding;

  /// Screen margin around centred dialogs on compact screens.
  final EdgeInsets screenMargin;

  /// Start scale of the enter transition.
  final double entranceScale;

  /// Hero icon size (24).
  final double iconSize;

  /// Gap between icon and headline (16).
  final double gapAfterIcon;

  /// Gap between headline and body (16).
  final double gapAfterTitle;

  /// Gap between body and actions (24).
  final double gapBeforeActions;

  /// Gap between side-by-side actions (8).
  final double actionGap;

  /// Height of each selectable row in selection dialogs.
  final double selectionItemHeight;

  /// Full-screen header height (56).
  final double fullScreenHeaderHeight;

  /// Gap before the 48dp close target, so the icon sits 16 from the edge.
  final double headerEdgeGap;

  /// Legacy: no longer applied. The close action is an `M3EIconButton`.
  final EdgeInsets closeButtonPadding;

  /// Gap after the trailing header action.
  final double headerActionGap;

  /// Scrim opacity (0.32).
  final double scrimOpacity;

  /// Whether dialog hosts pad with keyboard view insets.
  final bool resizeToAvoidBottomInset;

  /// Duration for keyboard / inset padding animation.
  final Duration insetAnimationDuration;

  /// Curve for keyboard / inset padding animation.
  final Curve insetAnimationCurve;

  /// Basic container elevation (level 3).
  final double elevation;

  /// Basic container corner radius (28).
  final double cornerRadius;

  /// Divider thickness (1).
  final double dividerThickness;

  /// Gap between stacked actions.
  final double stackedActionGap;

  /// Widths below this are compact (600). Adaptive dialogs go full-screen.
  final double compactBreakpoint;

  /// Edge margin for custom-positioned dialogs on medium and wider (56).
  final double customPositionMargin;

  /// Spatial spring for the enter / exit scale.
  final M3ESpring enterSpring;

  /// Effects spring for the enter / exit fade.
  final M3ESpring fadeSpring;

  /// Basic dialog colors, type and action states.
  final M3EDialogAppearance appearance;

  /// Full-screen dialog tokens.
  final M3EFullScreenDialogTheme fullScreen;

  /// Basic container radius.
  BorderRadius get borderRadius => BorderRadius.circular(cornerRadius);

  /// Basic container color.
  Color containerColor(M3EColorScheme scheme) =>
      appearance.resolveContainer(scheme);

  /// Full-screen container color.
  Color fullScreenBackground(M3EColorScheme scheme) =>
      fullScreen.resolveContainer(scheme);

  /// Scrim color.

  Color scrimColor(M3EColorScheme scheme) =>
      scheme.scrim.withValues(alpha: scrimOpacity);

  @override
  M3EDialogTheme copyWith({
    double? minWidth,
    double? maxWidth,
    EdgeInsets? padding,
    EdgeInsets? screenMargin,
    double? entranceScale,
    double? iconSize,
    double? gapAfterIcon,
    double? gapAfterTitle,
    double? gapBeforeActions,
    double? actionGap,
    double? selectionItemHeight,
    double? fullScreenHeaderHeight,
    double? headerEdgeGap,
    EdgeInsets? closeButtonPadding,
    double? headerActionGap,
    double? scrimOpacity,
    bool? resizeToAvoidBottomInset,
    Duration? insetAnimationDuration,
    Curve? insetAnimationCurve,
    double? elevation,
    double? cornerRadius,
    double? dividerThickness,
    double? stackedActionGap,
    double? compactBreakpoint,
    double? customPositionMargin,
    M3ESpring? enterSpring,
    M3ESpring? fadeSpring,
    M3EDialogAppearance? appearance,
    M3EFullScreenDialogTheme? fullScreen,
  }) {
    return M3EDialogTheme(
      minWidth: minWidth ?? this.minWidth,
      maxWidth: maxWidth ?? this.maxWidth,
      padding: padding ?? this.padding,
      screenMargin: screenMargin ?? this.screenMargin,
      entranceScale: entranceScale ?? this.entranceScale,
      iconSize: iconSize ?? this.iconSize,
      gapAfterIcon: gapAfterIcon ?? this.gapAfterIcon,
      gapAfterTitle: gapAfterTitle ?? this.gapAfterTitle,
      gapBeforeActions: gapBeforeActions ?? this.gapBeforeActions,
      actionGap: actionGap ?? this.actionGap,
      selectionItemHeight: selectionItemHeight ?? this.selectionItemHeight,
      fullScreenHeaderHeight:
          fullScreenHeaderHeight ?? this.fullScreenHeaderHeight,
      headerEdgeGap: headerEdgeGap ?? this.headerEdgeGap,
      closeButtonPadding: closeButtonPadding ?? this.closeButtonPadding,
      headerActionGap: headerActionGap ?? this.headerActionGap,
      scrimOpacity: scrimOpacity ?? this.scrimOpacity,
      resizeToAvoidBottomInset:
          resizeToAvoidBottomInset ?? this.resizeToAvoidBottomInset,
      insetAnimationDuration:
          insetAnimationDuration ?? this.insetAnimationDuration,
      insetAnimationCurve: insetAnimationCurve ?? this.insetAnimationCurve,
      elevation: elevation ?? this.elevation,
      cornerRadius: cornerRadius ?? this.cornerRadius,
      dividerThickness: dividerThickness ?? this.dividerThickness,
      stackedActionGap: stackedActionGap ?? this.stackedActionGap,
      compactBreakpoint: compactBreakpoint ?? this.compactBreakpoint,
      customPositionMargin: customPositionMargin ?? this.customPositionMargin,
      enterSpring: enterSpring ?? this.enterSpring,
      fadeSpring: fadeSpring ?? this.fadeSpring,
      appearance: appearance ?? this.appearance,
      fullScreen: fullScreen ?? this.fullScreen,
    );
  }

  @override
  M3EDialogTheme lerp(M3EDialogTheme? other, double t) {
    if (other is! M3EDialogTheme) {
      return this;
    }
    return _lerpBase(other, t).copyWith(
      elevation: _lerpDouble(elevation, other.elevation, t),
      cornerRadius: _lerpDouble(cornerRadius, other.cornerRadius, t),
      dividerThickness: _lerpDouble(
        dividerThickness,
        other.dividerThickness,
        t,
      ),
      stackedActionGap: _lerpDouble(
        stackedActionGap,
        other.stackedActionGap,
        t,
      ),
      compactBreakpoint: t < 0.5 ? compactBreakpoint : other.compactBreakpoint,
      customPositionMargin: _lerpDouble(
        customPositionMargin,
        other.customPositionMargin,
        t,
      ),
      enterSpring: t < 0.5 ? enterSpring : other.enterSpring,
      fadeSpring: t < 0.5 ? fadeSpring : other.fadeSpring,
      appearance: appearance.lerp(other.appearance, t),
      fullScreen: fullScreen.lerp(other.fullScreen, t),
    );
  }

  M3EDialogTheme _lerpBase(M3EDialogTheme other, double t) {
    return M3EDialogTheme(
      minWidth: _lerpDouble(minWidth, other.minWidth, t)!,
      maxWidth: _lerpDouble(maxWidth, other.maxWidth, t)!,
      padding: EdgeInsets.lerp(padding, other.padding, t)!,
      screenMargin: EdgeInsets.lerp(screenMargin, other.screenMargin, t)!,
      entranceScale: _lerpDouble(entranceScale, other.entranceScale, t)!,
      iconSize: _lerpDouble(iconSize, other.iconSize, t)!,
      gapAfterIcon: _lerpDouble(gapAfterIcon, other.gapAfterIcon, t)!,
      gapAfterTitle: _lerpDouble(gapAfterTitle, other.gapAfterTitle, t)!,
      gapBeforeActions: _lerpDouble(
        gapBeforeActions,
        other.gapBeforeActions,
        t,
      )!,
      actionGap: _lerpDouble(actionGap, other.actionGap, t)!,
      selectionItemHeight: _lerpDouble(
        selectionItemHeight,
        other.selectionItemHeight,
        t,
      )!,
      fullScreenHeaderHeight: _lerpDouble(
        fullScreenHeaderHeight,
        other.fullScreenHeaderHeight,
        t,
      )!,
      headerEdgeGap: _lerpDouble(headerEdgeGap, other.headerEdgeGap, t)!,
      closeButtonPadding: EdgeInsets.lerp(
        closeButtonPadding,
        other.closeButtonPadding,
        t,
      )!,
      headerActionGap: _lerpDouble(headerActionGap, other.headerActionGap, t)!,
      scrimOpacity: _lerpDouble(scrimOpacity, other.scrimOpacity, t)!,
      resizeToAvoidBottomInset: t < 0.5
          ? resizeToAvoidBottomInset
          : other.resizeToAvoidBottomInset,
      insetAnimationDuration: t < 0.5
          ? insetAnimationDuration
          : other.insetAnimationDuration,
      insetAnimationCurve: t < 0.5
          ? insetAnimationCurve
          : other.insetAnimationCurve,
    );
  }

  double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
