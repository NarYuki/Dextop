import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/toolbars/m3e_toolbars.dart'
    show M3EToolbar;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3EToolbar;

import '../../../foundations/foundations.dart';
import '../../icon_buttons/enums/m3e_icon_button_enums.dart';
import '../enums/m3e_toolbar_enums.dart';
import '../res/m3e_toolbar_tokens.dart';

/// Resolved colors for a toolbar (+ optional FAB).
@immutable
class M3EToolbarColors {
  /// M3EToolbarColors.
  const M3EToolbarColors({
    required this.container,
    required this.content,
    required this.fabContainer,
    required this.fabContent,
    this.selectedContainer,
    this.selectedContent,
    this.emphasisContainer,
    this.emphasisContent,
    this.disabledContent,
  });

  /// container.

  final Color container;

  /// Unselected icon and label.
  final Color content;

  /// fabContainer.
  final Color fabContainer;

  /// fabContent.
  final Color fabContent;

  /// Selected toggle container. Tonal, not filled.
  final Color? selectedContainer;

  /// Selected toggle icon and label.
  final Color? selectedContent;

  /// Single filled emphasis action (primary).
  final Color? emphasisContainer;

  /// Icon and label on the filled emphasis action.
  final Color? emphasisContent;

  /// Disabled icon and label (on-surface at 0.38).
  final Color? disabledContent;
}

/// Resolved layout metrics for floating / docked toolbars.
@immutable
class M3EToolbarMetrics {
  /// M3EToolbarMetrics.
  const M3EToolbarMetrics({
    required this.crossAxisSize,
    required this.contentPadding,
    required this.gap,
    required this.iconSize,
    required this.elevation,
    required this.elevationWithFab,
  });

  /// crossAxisSize.

  final double crossAxisSize;

  /// contentPadding.
  final EdgeInsetsGeometry contentPadding;

  /// gap.
  final double gap;

  /// iconSize.
  final double iconSize;

  /// elevation.
  final double elevation;

  /// elevationWithFab.
  final double elevationWithFab;
}

/// Theme values for [M3EToolbar].
@immutable
class M3EToolbarTheme extends M3EThemeExtension<M3EToolbarTheme> {
  /// M3EToolbarTheme.
  const M3EToolbarTheme({
    this.containerSize = M3EToolbarTokens.containerSize,
    this.floatingPadding = M3EToolbarTokens.floatingContentPadding,
    this.dockedHorizontalPadding = M3EToolbarTokens.dockedHorizontalPadding,
    this.actionGap = M3EToolbarTokens.containerBetweenSpace,
    this.dockedPreferredGap = M3EToolbarTokens.dockedPreferredGap,
    this.dockedMinGap = M3EToolbarTokens.containerBetweenSpace,
    this.centeredGap = M3EToolbarTokens.centeredGap,
    this.iconSize = 24,
    this.elevation = M3EToolbarTokens.elevationNone,
    this.floatingElevation = M3EToolbarTokens.floatingElevation,
    this.elevationWithFab = M3EToolbarTokens.floatingElevation,
    this.toolbarToFabGap = M3EToolbarTokens.toolbarToFabGap,
    this.screenOffset = M3EToolbarTokens.screenOffset,
    this.verticalScreenOffset = M3EToolbarTokens.verticalScreenOffset,
    this.compactBreakpoint = M3EToolbarTokens.compactBreakpoint,
    this.fabExpandedIcon = M3EToolbarTokens.fabExpandedIcon,
    this.fabCollapsedIcon = M3EToolbarTokens.fabCollapsedIcon,
    this.dockedRadius = M3EToolbarTokens.dockedRadius,
    // Legacy fields retained for copyWith / lerp compatibility.
    this.heightSmall = 40,
    this.heightMedium = 48,
    this.heightLarge = 56,
    this.compactHeightReduction = 4,
    this.elevationSurface = 0,
    this.elevationProminent = 2,
    this.expandSpring = M3EMotion.expressiveSpatialFast,
    this.labelSpring = const M3ESpring(stiffness: 800, damping: 0.4),
  });

  /// defaults.

  static const M3EToolbarTheme defaults = M3EToolbarTheme();

  /// containerSize.

  final double containerSize;

  /// floatingPadding.
  final double floatingPadding;

  /// dockedHorizontalPadding.
  final double dockedHorizontalPadding;

  /// Gap between floating actions.
  final double actionGap;

  /// Preferred docked gap. Clamped down to [dockedMinGap] when the bar is tight.
  final double dockedPreferredGap;

  /// Smallest docked gap before actions overflow.
  final double dockedMinGap;

  /// Gap when [M3EToolbarContentAlignment.centered] is active.
  final double centeredGap;

  /// iconSize.
  final double iconSize;

  /// Docked elevation.
  final double elevation;

  /// Floating elevation (level 3). Does not change when a FAB is attached.
  final double floatingElevation;

  /// Kept for theme compatibility. Resolved metrics use [floatingElevation].
  final double elevationWithFab;

  /// toolbarToFabGap.
  final double toolbarToFabGap;

  /// Horizontal floating screen margin.
  final double screenOffset;

  /// Vertical floating screen margin.
  final double verticalScreenOffset;

  /// Window width below which docked actions stay evenly spaced.
  final double compactBreakpoint;

  /// Expanded FAB icon size.
  final double fabExpandedIcon;

  /// Collapsed FAB icon size.
  final double fabCollapsedIcon;

  /// Docked corner radius. Default 0.
  final double dockedRadius;

  /// heightSmall.

  final double heightSmall;

  /// heightMedium.
  final double heightMedium;

  /// heightLarge.
  final double heightLarge;

  /// compactHeightReduction.
  final double compactHeightReduction;

  /// elevationSurface.
  final double elevationSurface;

  /// elevationProminent.
  final double elevationProminent;

  /// Floating toolbar morph / visibility expand spring.
  final M3ESpring expandSpring;

  /// Labeled action expand / collapse spring.
  final M3ESpring labelSpring;

  /// metricsFor.

  M3EToolbarMetrics metricsFor(M3EToolbarPlacement placement) {
    final EdgeInsetsGeometry padding = placement == M3EToolbarPlacement.floating
        ? EdgeInsets.all(floatingPadding)
        : EdgeInsets.fromLTRB(
            dockedHorizontalPadding,
            floatingPadding,
            dockedHorizontalPadding,
            floatingPadding,
          );
    final double barElevation = placement == M3EToolbarPlacement.floating
        ? floatingElevation
        : elevation;
    return M3EToolbarMetrics(
      crossAxisSize: containerSize,
      contentPadding: padding,
      gap: placement == M3EToolbarPlacement.floating ? actionGap : dockedMinGap,
      iconSize: iconSize,
      elevation: barElevation,
      elevationWithFab: barElevation,
    );
  }

  /// Legacy metrics API used by older call sites.
  M3EToolbarMetrics metrics(M3EToolbarDensity density, M3ESpacing spacing) {
    return metricsFor(M3EToolbarPlacement.floating).copyWithGap(spacing.sm);
  }

  /// colors.

  M3EToolbarColors colors(M3EColorScheme scheme, M3EToolbarColorStyle style) {
    switch (style) {
      case M3EToolbarColorStyle.standard:
        return M3EToolbarColors(
          container: scheme.surfaceContainer,
          content: scheme.onSurfaceVariant,
          fabContainer: scheme.secondaryContainer,
          fabContent: scheme.onSecondaryContainer,
          selectedContainer: scheme.secondaryContainer,
          selectedContent: scheme.onSecondaryContainer,
          emphasisContainer: scheme.primary,
          emphasisContent: scheme.onPrimary,
          disabledContent: scheme.onSurface.withValues(
            alpha: M3EToolbarTokens.disabledContentAlpha,
          ),
        );
      case M3EToolbarColorStyle.vibrant:
        return M3EToolbarColors(
          container: scheme.primaryContainer,
          content: scheme.onPrimaryContainer,
          fabContainer: scheme.tertiaryContainer,
          fabContent: scheme.onTertiaryContainer,
          selectedContainer: scheme.surfaceContainer,
          selectedContent: scheme.onSurface,
          emphasisContainer: scheme.primary,
          emphasisContent: scheme.onPrimary,
          disabledContent: scheme.onSurface.withValues(
            alpha: M3EToolbarTokens.disabledContentAlpha,
          ),
        );
    }
  }

  /// Maps legacy [M3EToolbarVariant] onto [M3EToolbarColorStyle].
  M3EToolbarColorStyle colorStyleFromVariant(M3EToolbarVariant variant) {
    return switch (variant) {
      M3EToolbarVariant.primary => M3EToolbarColorStyle.vibrant,
      M3EToolbarVariant.surface ||
      M3EToolbarVariant.tonal => M3EToolbarColorStyle.standard,
    };
  }

  /// containerColor.

  Color containerColor(M3EColorScheme scheme, M3EToolbarVariant variant) {
    return colors(scheme, colorStyleFromVariant(variant)).container;
  }

  /// foregroundColor.

  Color foregroundColor(M3EColorScheme scheme, M3EToolbarVariant variant) {
    return colors(scheme, colorStyleFromVariant(variant)).content;
  }

  /// floatingShape.

  ShapeBorder floatingShape() {
    return const StadiumBorder();
  }

  /// dockedShape.

  ShapeBorder dockedShape() {
    if (dockedRadius <= 0) {
      return const RoundedRectangleBorder();
    }
    return RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(dockedRadius),
    );
  }

  /// shape.

  ShapeBorder shape(M3EToolbarShapeFamily family) {
    return family == M3EToolbarShapeFamily.round
        ? floatingShape()
        : dockedShape();
  }

  /// titleStyle.

  TextStyle titleStyle(M3ETypeScale typeScale) => typeScale.titleSmall;

  /// subtitleStyle.

  TextStyle subtitleStyle(M3ETypeScale typeScale) => typeScale.bodySmall;

  /// scopedTheme.

  /// Unselected toolbar icons read [M3EColorScheme.onSurfaceVariant].
  ///
  /// Disabled content stays on [M3EColorScheme.onSurface] so the icon button
  /// can fade it to [M3EToolbarTokens.disabledContentAlpha].
  M3EThemeData scopedTheme(M3EThemeData base, Color foreground) {
    return base.copyWith(
      colorScheme: base.colorScheme.copyWith(onSurfaceVariant: foreground),
    );
  }

  /// iconButtonSize.

  M3EIconButtonSize iconButtonSize(M3EToolbarSize size) {
    switch (size) {
      case M3EToolbarSize.small:
        return M3EIconButtonSize.xs;
      case M3EToolbarSize.medium:
      case M3EToolbarSize.large:
        return M3EIconButtonSize.sm;
    }
  }

  @override
  M3EToolbarTheme copyWith({
    double? containerSize,
    double? floatingPadding,
    double? dockedHorizontalPadding,
    double? actionGap,
    double? dockedPreferredGap,
    double? dockedMinGap,
    double? centeredGap,
    double? iconSize,
    double? elevation,
    double? floatingElevation,
    double? elevationWithFab,
    double? toolbarToFabGap,
    double? screenOffset,
    double? verticalScreenOffset,
    double? compactBreakpoint,
    double? fabExpandedIcon,
    double? fabCollapsedIcon,
    double? dockedRadius,
    double? heightSmall,
    double? heightMedium,
    double? heightLarge,
    double? compactHeightReduction,
    double? elevationSurface,
    double? elevationProminent,
    M3ESpring? expandSpring,
    M3ESpring? labelSpring,
  }) {
    return M3EToolbarTheme(
      containerSize: containerSize ?? this.containerSize,
      floatingPadding: floatingPadding ?? this.floatingPadding,
      dockedHorizontalPadding:
          dockedHorizontalPadding ?? this.dockedHorizontalPadding,
      actionGap: actionGap ?? this.actionGap,
      dockedPreferredGap: dockedPreferredGap ?? this.dockedPreferredGap,
      dockedMinGap: dockedMinGap ?? this.dockedMinGap,
      centeredGap: centeredGap ?? this.centeredGap,
      iconSize: iconSize ?? this.iconSize,
      elevation: elevation ?? this.elevation,
      floatingElevation: floatingElevation ?? this.floatingElevation,
      elevationWithFab: elevationWithFab ?? this.elevationWithFab,
      toolbarToFabGap: toolbarToFabGap ?? this.toolbarToFabGap,
      screenOffset: screenOffset ?? this.screenOffset,
      verticalScreenOffset: verticalScreenOffset ?? this.verticalScreenOffset,
      compactBreakpoint: compactBreakpoint ?? this.compactBreakpoint,
      fabExpandedIcon: fabExpandedIcon ?? this.fabExpandedIcon,
      fabCollapsedIcon: fabCollapsedIcon ?? this.fabCollapsedIcon,
      dockedRadius: dockedRadius ?? this.dockedRadius,
      heightSmall: heightSmall ?? this.heightSmall,
      heightMedium: heightMedium ?? this.heightMedium,
      heightLarge: heightLarge ?? this.heightLarge,
      compactHeightReduction:
          compactHeightReduction ?? this.compactHeightReduction,
      elevationSurface: elevationSurface ?? this.elevationSurface,
      elevationProminent: elevationProminent ?? this.elevationProminent,
      expandSpring: expandSpring ?? this.expandSpring,
      labelSpring: labelSpring ?? this.labelSpring,
    );
  }

  @override
  M3EToolbarTheme lerp(M3EToolbarTheme? other, double t) {
    if (other is! M3EToolbarTheme) {
      return this;
    }
    return M3EToolbarTheme(
      containerSize: _lerp(containerSize, other.containerSize, t),
      floatingPadding: _lerp(floatingPadding, other.floatingPadding, t),
      dockedHorizontalPadding: _lerp(
        dockedHorizontalPadding,
        other.dockedHorizontalPadding,
        t,
      ),
      actionGap: _lerp(actionGap, other.actionGap, t),
      dockedPreferredGap: _lerp(
        dockedPreferredGap,
        other.dockedPreferredGap,
        t,
      ),
      dockedMinGap: _lerp(dockedMinGap, other.dockedMinGap, t),
      centeredGap: _lerp(centeredGap, other.centeredGap, t),
      iconSize: _lerp(iconSize, other.iconSize, t),
      elevation: _lerp(elevation, other.elevation, t),
      floatingElevation: _lerp(floatingElevation, other.floatingElevation, t),
      elevationWithFab: _lerp(elevationWithFab, other.elevationWithFab, t),
      toolbarToFabGap: _lerp(toolbarToFabGap, other.toolbarToFabGap, t),
      screenOffset: _lerp(screenOffset, other.screenOffset, t),
      verticalScreenOffset: _lerp(
        verticalScreenOffset,
        other.verticalScreenOffset,
        t,
      ),
      compactBreakpoint: _lerp(compactBreakpoint, other.compactBreakpoint, t),
      fabExpandedIcon: _lerp(fabExpandedIcon, other.fabExpandedIcon, t),
      fabCollapsedIcon: _lerp(fabCollapsedIcon, other.fabCollapsedIcon, t),
      dockedRadius: _lerp(dockedRadius, other.dockedRadius, t),
      heightSmall: _lerp(heightSmall, other.heightSmall, t),
      heightMedium: _lerp(heightMedium, other.heightMedium, t),
      heightLarge: _lerp(heightLarge, other.heightLarge, t),
      compactHeightReduction: _lerp(
        compactHeightReduction,
        other.compactHeightReduction,
        t,
      ),
      elevationSurface: _lerp(elevationSurface, other.elevationSurface, t),
      elevationProminent: _lerp(
        elevationProminent,
        other.elevationProminent,
        t,
      ),
      expandSpring: t < 0.5 ? expandSpring : other.expandSpring,
      labelSpring: t < 0.5 ? labelSpring : other.labelSpring,
    );
  }

  double _lerp(double a, double b, double t) => a + (b - a) * t;
}

extension on M3EToolbarMetrics {
  M3EToolbarMetrics copyWithGap(double gap) {
    return M3EToolbarMetrics(
      crossAxisSize: crossAxisSize,
      contentPadding: contentPadding,
      gap: gap,
      iconSize: iconSize,
      elevation: elevation,
      elevationWithFab: elevationWithFab,
    );
  }
}
