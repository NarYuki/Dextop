import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_side_sheet_enums.dart';
import 'm3e_side_sheet_action_style.dart';
import 'm3e_side_sheet_colors.dart';
import 'm3e_side_sheet_motion.dart';

export 'm3e_side_sheet_action_style.dart';
export 'm3e_side_sheet_colors.dart';
export 'm3e_side_sheet_motion.dart';

/// Theme values for `M3ESideSheet`.
///
/// Defaults follow the M3 side sheet spec.
@immutable
class M3ESideSheetTheme extends M3EThemeExtension<M3ESideSheetTheme> {
  /// M3ESideSheetTheme.
  const M3ESideSheetTheme({
    this.width = 256,
    this.maxWidth = 400,
    this.detachedMargin = 16,
    this.horizontalPadding = 24,
    this.startPaddingWithIcon = 16,
    this.topElementsGap = 12,
    this.headerVerticalPadding = 8,
    this.actionsHeight = 72,
    this.actionsTopPadding = 16,
    this.actionsBottomPadding = 24,
    this.actionsGap = 8,
    this.actionsAlignment = MainAxisAlignment.start,
    this.standardCornerRadius = 0,
    this.modalCornerRadius = 16,
    this.detachedCornerRadius = 16,
    this.standardElevation = M3EElevation.level0,
    this.modalElevation = M3EElevation.level1,
    this.headlineTextStyle,
    this.headlineMaxLines = 1,
    this.dividerThickness = 1,
    this.scrimOpacity = 0.32,
    this.compactBreakpoint = 600,
    this.bodyTrailingMargin = 24,
    this.colors = const M3ESideSheetColors(),
    this.action = const M3ESideSheetActionStyle(),
    this.motion = const M3ESideSheetMotion(),
  });

  /// defaults.
  static const M3ESideSheetTheme defaults = M3ESideSheetTheme();

  /// Default container width (256).
  final double width;

  /// Largest allowed width (400).
  final double maxWidth;

  /// Margin from the window edges when detached (16).
  final double detachedMargin;

  /// Start / end padding (24).
  final double horizontalPadding;

  /// Start padding before the back icon button (16).
  final double startPaddingWithIcon;

  /// Gap between the back icon, headline and close icon (12).
  final double topElementsGap;

  /// Padding above and below the header row (8), so the header is 64 tall
  /// like a small app bar.
  final double headerVerticalPadding;

  /// Minimum bottom actions height (72).
  final double actionsHeight;

  /// Padding above the action buttons (16).
  final double actionsTopPadding;

  /// Padding below the action buttons (24).
  final double actionsBottomPadding;

  /// Gap between action buttons (8).
  final double actionsGap;

  /// Horizontal alignment of the action buttons (start).
  final MainAxisAlignment actionsAlignment;

  /// Standard docked corner radius, no rounding (0).
  final double standardCornerRadius;

  /// Modal docked radius on the corners facing the content, large (16).
  final double modalCornerRadius;

  /// Radius on every corner when detached, large (16).
  final double detachedCornerRadius;

  /// Standard container elevation (level 0).
  final double standardElevation;

  /// Modal container elevation (level 1).
  final double modalElevation;

  /// Merged over the title large headline style.
  final TextStyle? headlineTextStyle;

  /// Headline lines before it ellipsizes (1, like an app bar title).
  final int headlineMaxLines;

  /// Divider thickness (1).
  final double dividerThickness;

  /// Scrim opacity (0.32).
  final double scrimOpacity;

  /// Windows narrower than this use the modal sheet in adaptive layouts.
  final double compactBreakpoint;

  /// Gap kept between the body and an open standard sheet (24).
  final double bodyTrailingMargin;

  /// Color overrides.
  final M3ESideSheetColors colors;

  /// Back and close icon button tokens.
  final M3ESideSheetActionStyle action;

  /// Springs and predictive-back amounts.
  final M3ESideSheetMotion motion;

  /// Container color for [variant].
  Color containerColor(M3EColorScheme scheme, M3ESideSheetVariant variant) =>
      variant == M3ESideSheetVariant.modal
      ? colors.modalContainerColor ?? scheme.surfaceContainerLow
      : colors.standardContainerColor ?? scheme.surface;

  /// Container elevation for [variant].
  double elevationFor(M3ESideSheetVariant variant) =>
      variant == M3ESideSheetVariant.modal ? modalElevation : standardElevation;

  /// Docked corner radius for [variant].
  double cornerRadiusFor(M3ESideSheetVariant variant) =>
      variant == M3ESideSheetVariant.modal
      ? modalCornerRadius
      : standardCornerRadius;

  /// Headline style: title large (22 / 28, weight 400) on surface variant.
  TextStyle headlineStyle(M3ETypeScale type, M3EColorScheme scheme) => type
      .titleLarge
      .copyWith(color: colors.headlineColor ?? scheme.onSurfaceVariant)
      .merge(headlineTextStyle);

  /// Back and close icon color at rest.
  Color iconColor(M3EColorScheme scheme) =>
      colors.iconColor ?? scheme.onSurfaceVariant;

  /// Divider color.
  Color dividerColor(M3EColorScheme scheme) =>
      colors.dividerColor ?? scheme.outlineVariant;

  /// Scrim color with [scrimOpacity].
  Color scrimColor(M3EColorScheme scheme) =>
      (colors.scrimColor ?? scheme.scrim).withValues(alpha: scrimOpacity);

  /// [requested] (or [width]) clamped to [maxWidth] and [available].
  double resolveWidth(double? requested, double available) {
    final double w = requested ?? width;
    final double cap = available < maxWidth ? available : maxWidth;
    return w.clamp(0, cap < 0 ? 0 : cap).toDouble();
  }

  @override
  M3ESideSheetTheme copyWith({
    double? width,
    double? maxWidth,
    double? detachedMargin,
    double? horizontalPadding,
    double? startPaddingWithIcon,
    double? topElementsGap,
    double? headerVerticalPadding,
    double? actionsHeight,
    double? actionsTopPadding,
    double? actionsBottomPadding,
    double? actionsGap,
    MainAxisAlignment? actionsAlignment,
    double? standardCornerRadius,
    double? modalCornerRadius,
    double? detachedCornerRadius,
    double? standardElevation,
    double? modalElevation,
    TextStyle? headlineTextStyle,
    int? headlineMaxLines,
    double? dividerThickness,
    double? scrimOpacity,
    double? compactBreakpoint,
    double? bodyTrailingMargin,
    M3ESideSheetColors? colors,
    M3ESideSheetActionStyle? action,
    M3ESideSheetMotion? motion,
  }) {
    return M3ESideSheetTheme(
      width: width ?? this.width,
      maxWidth: maxWidth ?? this.maxWidth,
      detachedMargin: detachedMargin ?? this.detachedMargin,
      horizontalPadding: horizontalPadding ?? this.horizontalPadding,
      startPaddingWithIcon: startPaddingWithIcon ?? this.startPaddingWithIcon,
      topElementsGap: topElementsGap ?? this.topElementsGap,
      headerVerticalPadding:
          headerVerticalPadding ?? this.headerVerticalPadding,
      actionsHeight: actionsHeight ?? this.actionsHeight,
      actionsTopPadding: actionsTopPadding ?? this.actionsTopPadding,
      actionsBottomPadding: actionsBottomPadding ?? this.actionsBottomPadding,
      actionsGap: actionsGap ?? this.actionsGap,
      actionsAlignment: actionsAlignment ?? this.actionsAlignment,
      standardCornerRadius: standardCornerRadius ?? this.standardCornerRadius,
      modalCornerRadius: modalCornerRadius ?? this.modalCornerRadius,
      detachedCornerRadius: detachedCornerRadius ?? this.detachedCornerRadius,
      standardElevation: standardElevation ?? this.standardElevation,
      modalElevation: modalElevation ?? this.modalElevation,
      headlineTextStyle: headlineTextStyle ?? this.headlineTextStyle,
      headlineMaxLines: headlineMaxLines ?? this.headlineMaxLines,
      dividerThickness: dividerThickness ?? this.dividerThickness,
      scrimOpacity: scrimOpacity ?? this.scrimOpacity,
      compactBreakpoint: compactBreakpoint ?? this.compactBreakpoint,
      bodyTrailingMargin: bodyTrailingMargin ?? this.bodyTrailingMargin,
      colors: colors ?? this.colors,
      action: action ?? this.action,
      motion: motion ?? this.motion,
    );
  }

  @override
  M3ESideSheetTheme lerp(M3ESideSheetTheme? other, double t) {
    if (other is! M3ESideSheetTheme) {
      return this;
    }
    final M3ESideSheetTheme end = t < 0.5 ? this : other;
    double l(double a, double b) => a + (b - a) * t;
    return end.copyWith(
      width: l(width, other.width),
      maxWidth: l(maxWidth, other.maxWidth),
      detachedMargin: l(detachedMargin, other.detachedMargin),
      horizontalPadding: l(horizontalPadding, other.horizontalPadding),
      startPaddingWithIcon: l(startPaddingWithIcon, other.startPaddingWithIcon),
      topElementsGap: l(topElementsGap, other.topElementsGap),
      headerVerticalPadding: l(
        headerVerticalPadding,
        other.headerVerticalPadding,
      ),
      actionsHeight: l(actionsHeight, other.actionsHeight),
      actionsTopPadding: l(actionsTopPadding, other.actionsTopPadding),
      actionsBottomPadding: l(actionsBottomPadding, other.actionsBottomPadding),
      actionsGap: l(actionsGap, other.actionsGap),
      standardCornerRadius: l(standardCornerRadius, other.standardCornerRadius),
      modalCornerRadius: l(modalCornerRadius, other.modalCornerRadius),
      detachedCornerRadius: l(detachedCornerRadius, other.detachedCornerRadius),
      standardElevation: l(standardElevation, other.standardElevation),
      modalElevation: l(modalElevation, other.modalElevation),
      headlineTextStyle: TextStyle.lerp(
        headlineTextStyle,
        other.headlineTextStyle,
        t,
      ),
      dividerThickness: l(dividerThickness, other.dividerThickness),
      scrimOpacity: l(scrimOpacity, other.scrimOpacity),
      compactBreakpoint: l(compactBreakpoint, other.compactBreakpoint),
      bodyTrailingMargin: l(bodyTrailingMargin, other.bodyTrailingMargin),
      colors: colors.lerp(other.colors, t),
      action: action.lerp(other.action, t),
      motion: motion.lerp(other.motion, t),
    );
  }
}
