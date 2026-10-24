import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/search/m3e_search.dart'
    show M3ESearchBar;
import 'package:material_3_expressive/components/search/m3e_search_bar.dart'
    show M3ESearchBar;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ESearchBar;
import 'package:material_ui/material_ui.dart'
    show InkSparkle, InteractiveInkFeatureFactory;

import '../../../foundations/foundations.dart';

/// Theme values for [M3ESearchBar].
///
/// Defaults follow the M3 Expressive "Search - Bar" token set (contained).
@immutable
class M3ESearchBarTheme extends M3EThemeExtension<M3ESearchBarTheme> {
  /// M3ESearchBarTheme.
  ///
  /// [horizontalPadding], [restingExpandPadding], and
  /// [noLeadingHintExtraPadding] are legacy aliases. When set they map onto
  /// [leadingSpace] / [trailingSpace], [unfocusedMargin] / [focusedMargin],
  /// and [noActionsLeadingSpace].
  const M3ESearchBarTheme({
    this.elevation = M3EElevation.level0,
    double? horizontalPadding,
    this.iconSize = 24,
    this.selectionOpacity = 0.4,
    this.disabledOpacity = M3EStateOpacity.disabledContent,
    this.minWidth = 360,
    this.maxWidth = 720,
    this.minHeight = 56,
    double? restingExpandPadding,
    this.expandOnFocus = true,
    this.focusExpandSpring = M3EMotion.expressiveSpatialDefault,
    double? noLeadingHintExtraPadding,
    this.pressedOverlayOpacity = M3EStateOpacity.pressed,
    this.hoveredOverlayOpacity = M3EStateOpacity.hover,
    double unfocusedMargin = 24,
    double focusedMargin = 12,
    double leadingSpace = 4,
    double trailingSpace = 4,
    double noActionsLeadingSpace = 16,
    this.noActionsTrailingSpace = 16,
    this.iconLabelGap = 4,
    this.trailingActionsLeadingSpace = 4,
    this.trailingActionsGap = 0,
    this.tapTargetSize = 48,
    this.avatarSize = 30,
    this.avatarTargetSize = 48,
    this.avatarShape = const CircleBorder(),
    this.focusIndicatorThickness = 3,
    this.focusIndicatorOffset = 2,
    this.splashFactory = InkSparkle.splashFactory,
  }) : unfocusedMargin = restingExpandPadding ?? unfocusedMargin,
       focusedMargin = restingExpandPadding == null
           ? focusedMargin
           : restingExpandPadding / 2,
       leadingSpace = horizontalPadding ?? leadingSpace,
       trailingSpace = horizontalPadding ?? trailingSpace,
       noActionsLeadingSpace = noLeadingHintExtraPadding == null
           ? noActionsLeadingSpace
           : (horizontalPadding ?? leadingSpace) + noLeadingHintExtraPadding;

  /// defaults.

  static const M3ESearchBarTheme defaults = M3ESearchBarTheme();

  /// Container elevation. Level 0: no shadow by default.
  final double elevation;

  /// Leading / trailing icon glyph size (24).
  final double iconSize;

  /// selectionOpacity.
  final double selectionOpacity;

  /// disabledOpacity.
  final double disabledOpacity;

  /// Container min width (360).
  final double minWidth;

  /// Container max width (720).
  final double maxWidth;

  /// Container height (56).
  final double minHeight;

  /// Whether the bar widens on focus ([unfocusedMargin] → [focusedMargin]).
  final bool expandOnFocus;

  /// Spring for the 24 → 12 margin change on focus.
  final M3ESpring focusExpandSpring;

  /// Pressed state layer opacity on on-surface (0.1).
  final double pressedOverlayOpacity;

  /// Hovered state layer opacity on on-surface (0.08).
  final double hoveredOverlayOpacity;

  /// Side margin around the bar while unfocused (24).
  final double unfocusedMargin;

  /// Side margin around the bar while focused (12).
  final double focusedMargin;

  /// Container edge → leading tap target (4).
  final double leadingSpace;

  /// Last trailing tap target → container edge (4).
  final double trailingSpace;

  /// Container edge → label when there is no leading action (16).
  final double noActionsLeadingSpace;

  /// Label → container edge when there are no trailing actions (16).
  final double noActionsTrailingSpace;

  /// Leading tap target → label (4).
  final double iconLabelGap;

  /// Label → first trailing tap target (4).
  final double trailingActionsLeadingSpace;

  /// Gap between trailing tap targets (0).
  final double trailingActionsGap;

  /// Leading / trailing icon tap target (48).
  final double tapTargetSize;

  /// Avatar diameter (30).
  final double avatarSize;

  /// Avatar tap target (48).
  final double avatarTargetSize;

  /// Avatar shape (circle).
  final ShapeBorder avatarShape;

  /// Focus indicator thickness (3).
  final double focusIndicatorThickness;

  /// Focus indicator offset from the container (2).
  final double focusIndicatorOffset;

  /// Pressed ripple. Defaults to the package sparkle.
  final InteractiveInkFeatureFactory splashFactory;

  /// Legacy alias of [leadingSpace].
  double get horizontalPadding => leadingSpace;

  /// Legacy alias of [unfocusedMargin].
  double get restingExpandPadding => unfocusedMargin;

  /// Legacy alias: [noActionsLeadingSpace] minus [leadingSpace].
  double get noLeadingHintExtraPadding => noActionsLeadingSpace - leadingSpace;

  /// constraints.

  BoxConstraints constraints({BoxConstraints? override}) {
    return override ??
        BoxConstraints(
          minWidth: minWidth,
          maxWidth: maxWidth,
          minHeight: minHeight,
        );
  }

  /// Container color: surface container high.

  Color backgroundColor(M3EColorScheme scheme) => scheme.surfaceContainerHigh;

  /// shadowColor.

  Color shadowColor(M3EColorScheme scheme) => scheme.shadow;

  /// Container surface tint layer color: primary.

  Color surfaceTintColor(M3EColorScheme scheme) => scheme.primary;

  /// Leading icon color: on surface.

  Color leadingIconColor(M3EColorScheme scheme) => scheme.onSurface;

  /// Trailing icon color: on surface variant.

  Color trailingIconColor(M3EColorScheme scheme) => scheme.onSurfaceVariant;

  /// Focus indicator color: secondary.

  Color focusIndicatorColor(M3EColorScheme scheme) => scheme.secondary;

  /// State layer base color (hover / pressed): on surface.

  Color stateLayerColor(M3EColorScheme scheme) => scheme.onSurface;

  /// Input text: body large, on surface.

  TextStyle textStyle(M3ETypeScale type, M3EColorScheme scheme) =>
      type.bodyLarge.copyWith(color: scheme.onSurface);

  /// Supporting (hinted) text: body large, on surface variant.

  TextStyle hintStyle(M3ETypeScale type, M3EColorScheme scheme) =>
      type.bodyLarge.copyWith(color: scheme.onSurfaceVariant);

  /// cursorColor.

  Color cursorColor(M3EColorScheme scheme) => scheme.primary;

  /// selectionColor.

  Color selectionColor(M3EColorScheme scheme) =>
      scheme.primary.withValues(alpha: selectionOpacity);

  /// Container padding: [leadingSpace] start, [trailingSpace] end.

  EdgeInsetsGeometry padding({EdgeInsetsGeometry? override}) {
    return override ??
        EdgeInsetsDirectional.only(start: leadingSpace, end: trailingSpace);
  }

  /// Label insets inside [padding] for the given actions.
  ///
  /// With actions: [iconLabelGap] / [trailingActionsLeadingSpace]. Without:
  /// the remainder up to [noActionsLeadingSpace] / [noActionsTrailingSpace].
  EdgeInsetsDirectional labelPadding({
    required bool hasLeading,
    required bool hasTrailing,
  }) {
    return EdgeInsetsDirectional.only(
      start: hasLeading ? iconLabelGap : noActionsLeadingSpace - leadingSpace,
      end: hasTrailing
          ? trailingActionsLeadingSpace
          : noActionsTrailingSpace - trailingSpace,
    );
  }

  /// Container shape: fully rounded.

  ShapeBorder shape({ShapeBorder? override}) => override ?? M3EShapes.stadium;

  /// resolveElevation.

  double resolveElevation({
    required Set<WidgetState> states,
    WidgetStateProperty<double?>? widgetValue,
    WidgetStateProperty<double?>? themeValue,
  }) {
    return widgetValue?.resolve(states) ??
        themeValue?.resolve(states) ??
        elevation;
  }

  /// resolveBackground.

  Color resolveBackground({
    required M3EColorScheme scheme,
    required Set<WidgetState> states,
    WidgetStateProperty<Color?>? widgetValue,
    WidgetStateProperty<Color?>? themeValue,
  }) {
    return widgetValue?.resolve(states) ??
        themeValue?.resolve(states) ??
        backgroundColor(scheme);
  }

  /// resolveShadowColor.

  Color resolveShadowColor({
    required M3EColorScheme scheme,
    required Set<WidgetState> states,
    WidgetStateProperty<Color?>? widgetValue,
    WidgetStateProperty<Color?>? themeValue,
  }) {
    return widgetValue?.resolve(states) ??
        themeValue?.resolve(states) ??
        shadowColor(scheme);
  }

  /// resolveSurfaceTint.

  Color resolveSurfaceTint({
    required M3EColorScheme scheme,
    required Set<WidgetState> states,
    WidgetStateProperty<Color?>? widgetValue,
    WidgetStateProperty<Color?>? themeValue,
  }) {
    return widgetValue?.resolve(states) ??
        themeValue?.resolve(states) ??
        surfaceTintColor(scheme);
  }

  /// resolveOverlay.

  Color? resolveOverlay({
    required M3EColorScheme scheme,
    required Set<WidgetState> states,
    WidgetStateProperty<Color?>? widgetValue,
    WidgetStateProperty<Color?>? themeValue,
  }) {
    final Color? resolved =
        widgetValue?.resolve(states) ?? themeValue?.resolve(states);
    if (resolved != null) {
      return resolved;
    }
    if (states.contains(WidgetState.pressed)) {
      return stateLayerColor(scheme).withValues(alpha: pressedOverlayOpacity);
    }
    if (states.contains(WidgetState.hovered)) {
      return stateLayerColor(scheme).withValues(alpha: hoveredOverlayOpacity);
    }
    return null;
  }

  /// resolveTextStyle.

  TextStyle resolveTextStyle({
    required M3EThemeData theme,
    required Set<WidgetState> states,
    WidgetStateProperty<TextStyle?>? widgetValue,
    WidgetStateProperty<TextStyle?>? themeValue,
  }) {
    return widgetValue?.resolve(states) ??
        themeValue?.resolve(states) ??
        textStyle(theme.typeScale, theme.colorScheme);
  }

  /// resolveHintStyle.

  TextStyle resolveHintStyle({
    required M3EThemeData theme,
    required Set<WidgetState> states,
    WidgetStateProperty<TextStyle?>? widgetValue,
    WidgetStateProperty<TextStyle?>? themeValue,
    WidgetStateProperty<TextStyle?>? textStyleOverride,
  }) {
    return widgetValue?.resolve(states) ??
        themeValue?.resolve(states) ??
        textStyleOverride?.resolve(states) ??
        hintStyle(theme.typeScale, theme.colorScheme);
  }

  @override
  M3ESearchBarTheme copyWith({
    double? elevation,
    double? horizontalPadding,
    double? iconSize,
    double? selectionOpacity,
    double? disabledOpacity,
    double? minWidth,
    double? maxWidth,
    double? minHeight,
    double? restingExpandPadding,
    bool? expandOnFocus,
    M3ESpring? focusExpandSpring,
    double? noLeadingHintExtraPadding,
    double? pressedOverlayOpacity,
    double? hoveredOverlayOpacity,
    double? unfocusedMargin,
    double? focusedMargin,
    double? leadingSpace,
    double? trailingSpace,
    double? noActionsLeadingSpace,
    double? noActionsTrailingSpace,
    double? iconLabelGap,
    double? trailingActionsLeadingSpace,
    double? trailingActionsGap,
    double? tapTargetSize,
    double? avatarSize,
    double? avatarTargetSize,
    ShapeBorder? avatarShape,
    double? focusIndicatorThickness,
    double? focusIndicatorOffset,
    InteractiveInkFeatureFactory? splashFactory,
  }) {
    final double lead = leadingSpace ?? horizontalPadding ?? this.leadingSpace;
    return M3ESearchBarTheme(
      elevation: elevation ?? this.elevation,
      iconSize: iconSize ?? this.iconSize,
      selectionOpacity: selectionOpacity ?? this.selectionOpacity,
      disabledOpacity: disabledOpacity ?? this.disabledOpacity,
      minWidth: minWidth ?? this.minWidth,
      maxWidth: maxWidth ?? this.maxWidth,
      minHeight: minHeight ?? this.minHeight,
      expandOnFocus: expandOnFocus ?? this.expandOnFocus,
      focusExpandSpring: focusExpandSpring ?? this.focusExpandSpring,
      pressedOverlayOpacity:
          pressedOverlayOpacity ?? this.pressedOverlayOpacity,
      hoveredOverlayOpacity:
          hoveredOverlayOpacity ?? this.hoveredOverlayOpacity,
      unfocusedMargin:
          unfocusedMargin ?? restingExpandPadding ?? this.unfocusedMargin,
      focusedMargin:
          focusedMargin ??
          (restingExpandPadding == null
              ? this.focusedMargin
              : restingExpandPadding / 2),
      leadingSpace: lead,
      trailingSpace: trailingSpace ?? horizontalPadding ?? this.trailingSpace,
      noActionsLeadingSpace:
          noActionsLeadingSpace ??
          (noLeadingHintExtraPadding == null
              ? this.noActionsLeadingSpace
              : lead + noLeadingHintExtraPadding),
      noActionsTrailingSpace:
          noActionsTrailingSpace ?? this.noActionsTrailingSpace,
      iconLabelGap: iconLabelGap ?? this.iconLabelGap,
      trailingActionsLeadingSpace:
          trailingActionsLeadingSpace ?? this.trailingActionsLeadingSpace,
      trailingActionsGap: trailingActionsGap ?? this.trailingActionsGap,
      tapTargetSize: tapTargetSize ?? this.tapTargetSize,
      avatarSize: avatarSize ?? this.avatarSize,
      avatarTargetSize: avatarTargetSize ?? this.avatarTargetSize,
      avatarShape: avatarShape ?? this.avatarShape,
      focusIndicatorThickness:
          focusIndicatorThickness ?? this.focusIndicatorThickness,
      focusIndicatorOffset: focusIndicatorOffset ?? this.focusIndicatorOffset,
      splashFactory: splashFactory ?? this.splashFactory,
    );
  }

  @override
  M3ESearchBarTheme lerp(M3ESearchBarTheme? other, double t) {
    if (other is! M3ESearchBarTheme) {
      return this;
    }
    final bool first = t < 0.5;
    return M3ESearchBarTheme(
      elevation: _lerp(elevation, other.elevation, t),
      iconSize: _lerp(iconSize, other.iconSize, t),
      selectionOpacity: _lerp(selectionOpacity, other.selectionOpacity, t),
      disabledOpacity: _lerp(disabledOpacity, other.disabledOpacity, t),
      minWidth: _lerp(minWidth, other.minWidth, t),
      maxWidth: _lerp(maxWidth, other.maxWidth, t),
      minHeight: _lerp(minHeight, other.minHeight, t),
      expandOnFocus: first ? expandOnFocus : other.expandOnFocus,
      focusExpandSpring: first ? focusExpandSpring : other.focusExpandSpring,
      pressedOverlayOpacity: _lerp(
        pressedOverlayOpacity,
        other.pressedOverlayOpacity,
        t,
      ),
      hoveredOverlayOpacity: _lerp(
        hoveredOverlayOpacity,
        other.hoveredOverlayOpacity,
        t,
      ),
      unfocusedMargin: _lerp(unfocusedMargin, other.unfocusedMargin, t),
      focusedMargin: _lerp(focusedMargin, other.focusedMargin, t),
      leadingSpace: _lerp(leadingSpace, other.leadingSpace, t),
      trailingSpace: _lerp(trailingSpace, other.trailingSpace, t),
      noActionsLeadingSpace: _lerp(
        noActionsLeadingSpace,
        other.noActionsLeadingSpace,
        t,
      ),
      noActionsTrailingSpace: _lerp(
        noActionsTrailingSpace,
        other.noActionsTrailingSpace,
        t,
      ),
      iconLabelGap: _lerp(iconLabelGap, other.iconLabelGap, t),
      trailingActionsLeadingSpace: _lerp(
        trailingActionsLeadingSpace,
        other.trailingActionsLeadingSpace,
        t,
      ),
      trailingActionsGap: _lerp(
        trailingActionsGap,
        other.trailingActionsGap,
        t,
      ),
      tapTargetSize: _lerp(tapTargetSize, other.tapTargetSize, t),
      avatarSize: _lerp(avatarSize, other.avatarSize, t),
      avatarTargetSize: _lerp(avatarTargetSize, other.avatarTargetSize, t),
      avatarShape: first ? avatarShape : other.avatarShape,
      focusIndicatorThickness: _lerp(
        focusIndicatorThickness,
        other.focusIndicatorThickness,
        t,
      ),
      focusIndicatorOffset: _lerp(
        focusIndicatorOffset,
        other.focusIndicatorOffset,
        t,
      ),
      splashFactory: first ? splashFactory : other.splashFactory,
    );
  }

  double _lerp(double a, double b, double t) => a + (b - a) * t;
}
