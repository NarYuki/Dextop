import 'dart:math' as math;
import 'dart:ui' show clampDouble;

import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../buttons/enums/m3e_button_enums.dart';
import '../enums/m3e_button_group_enums.dart';

/// Resolved spacing metrics for a button group layout pass.
@immutable
class M3EButtonGroupMetrics {
  /// Creates metrics for between-item and run spacing.
  const M3EButtonGroupMetrics({
    required this.spacing,
    required this.runSpacing,
    required this.dividerThickness,
  });

  /// Main-axis gap between adjacent actions.
  final double spacing;

  /// Cross-axis gap when the group wraps (same as [spacing] by default).
  final double runSpacing;

  /// Thickness reserved for dividers between connected segments.
  final double dividerThickness;
}

/// Theme extension for button group tokens and helpers.
@immutable
class M3EButtonGroupTheme extends M3EThemeExtension<M3EButtonGroupTheme> {
  /// Creates a button group theme.
  const M3EButtonGroupTheme({
    this.connectedGap = 2,
    this.dividerThickness = 1,
    this.expandedRatio = 0.15,
    this.fullRoundRadius = 9999,
    this.maxWidth,
    this.neighborSquishSpring = M3EMotion.spatialFast,
    this.standardSpacingOverride,
    this.connectedInnerRadiusOverride,
    this.connectedPressedInnerRadiusOverride,
    this.connectedOuterSquareRadiusOverride,
  });

  /// Package defaults (spec tokens).
  static const M3EButtonGroupTheme defaults = M3EButtonGroupTheme();

  /// Gap between connected segments (spec: 2dp at every size).
  final double connectedGap;

  /// Divider thickness between connected segments.
  final double dividerThickness;

  /// Pressed width growth ratio for neighbour squish (spec: 15%).
  final double expandedRatio;

  /// Sentinel radius for fully round outer corners.
  final double fullRoundRadius;

  /// Optional max width for connected groups that expand to fill.
  final double? maxWidth;

  /// Spring for neighbour-squish width animation (spec: 1400 / 0.9).
  final M3ESpring neighborSquishSpring;

  /// When set, forces the same standard between-space for every size.
  final double? standardSpacingOverride;

  /// When set, forces the same connected inner radius for every size.
  final double? connectedInnerRadiusOverride;

  /// When set, forces the same connected pressed inner radius for every size.
  final double? connectedPressedInnerRadiusOverride;

  /// When set, forces the same connected square outer radius for every size.
  final double? connectedOuterSquareRadiusOverride;

  static const Map<String, double> _standardSpacing = {
    'xs': 18,
    'sm': 12,
    'md': 8,
    'lg': 8,
    'xl': 8,
  };

  static const Map<String, double> _connectedInner = {
    'xs': 4,
    'sm': 8,
    'md': 8,
    'lg': 16,
    'xl': 20,
  };

  static const Map<String, double> _connectedPressedInner = {
    'xs': 4,
    'sm': 4,
    'md': 4,
    'lg': 12,
    'xl': 16,
  };

  static const Map<String, double> _connectedOuterSquare = {
    'xs': 4,
    'sm': 8,
    'md': 8,
    'lg': 16,
    'xl': 20,
  };

  /// Standard between-space for [size] (18 / 12 / 8 / 8 / 8).
  double standardSpacingFor(M3EButtonSize size) =>
      standardSpacingOverride ?? _standardSpacing[size.name] ?? 8;

  /// Connected unselected inner corner radius for [size].
  double connectedInnerRadiusFor(M3EButtonSize size) =>
      connectedInnerRadiusOverride ?? _connectedInner[size.name] ?? 8;

  /// Connected pressed inner corner radius for [size].
  double connectedPressedInnerRadiusFor(M3EButtonSize size) =>
      connectedPressedInnerRadiusOverride ??
      _connectedPressedInner[size.name] ??
      4;

  /// Connected selected inner corner radius (50% of segment height).
  double connectedSelectedInnerRadiusFor(double height) => height / 2;

  /// Square connected outer corner radius for [size].
  double connectedOuterSquareRadiusFor(M3EButtonSize size) =>
      connectedOuterSquareRadiusOverride ??
      _connectedOuterSquare[size.name] ??
      8;

  /// Alias of [connectedOuterSquareRadiusFor] for square outer corners.
  double squareRadiusFor(M3EButtonSize size) =>
      connectedOuterSquareRadiusFor(size);

  /// Spacing metrics for [size] / [density] and connection mode.
  ///
  /// Density affects height via [containerHeightFor], not spacing.
  M3EButtonGroupMetrics metricsFor(
    M3EButtonSize size,
    M3EButtonGroupDensity density, {
    bool isConnected = false,
  }) {
    final spacing = isConnected ? connectedGap : standardSpacingFor(size);
    return M3EButtonGroupMetrics(
      spacing: spacing,
      runSpacing: spacing,
      dividerThickness: dividerThickness,
    );
  }

  /// Clip / container radius for the whole group.
  BorderRadius groupRadiusFor(M3EButtonShape shape, M3EButtonSize size) =>
      shape == M3EButtonShape.round
      ? BorderRadius.circular(fullRoundRadius)
      : BorderRadius.circular(connectedOuterSquareRadiusFor(size));

  /// Connected segment [BorderRadius] for position and interaction state.
  BorderRadius connectedRadiusFor({
    required M3EButtonShape shape,
    required M3EButtonSize size,
    required bool isFirst,
    required bool isLast,
    required bool isPressed,
    required bool isSelected,
    double? height,
  }) {
    final segmentHeight = height ?? containerHeightFor(size);
    if (isSelected) {
      return BorderRadius.circular(
        connectedSelectedInnerRadiusFor(segmentHeight),
      );
    }

    final outerRad = shape == M3EButtonShape.round
        ? fullRoundRadius
        : connectedOuterSquareRadiusFor(size);

    final innerRad = isPressed
        ? connectedPressedInnerRadiusFor(size)
        : connectedInnerRadiusFor(size);

    final tl = isFirst ? outerRad : innerRad;
    final tr = isLast ? outerRad : innerRad;
    final bl = isFirst ? outerRad : innerRad;
    final br = isLast ? outerRad : innerRad;

    return BorderRadius.only(
      topLeft: Radius.circular(tl),
      topRight: Radius.circular(tr),
      bottomLeft: Radius.circular(bl),
      bottomRight: Radius.circular(br),
    );
  }

  /// Resting radius for a standard (non-connected) action.
  BorderRadius standardRadiusFor(M3EButtonShape shape, M3EButtonSize size) =>
      shape == M3EButtonShape.round
      ? BorderRadius.circular(fullRoundRadius)
      : BorderRadius.circular(connectedOuterSquareRadiusFor(size));

  /// Neighbour-squish main-axis deltas for [pressedIndex].
  List<double> widthDeltas({
    required List<double> naturalWidths,
    required int? pressedIndex,
    double? expandedRatio,
  }) {
    final ratio = expandedRatio ?? this.expandedRatio;
    final n = naturalWidths.length;
    final deltas = List<double>.filled(n, 0);
    if (pressedIndex == null || n < 2) {
      return deltas;
    }

    final i = pressedIndex;
    final growth = ratio * naturalWidths[i];

    if (i == 0) {
      deltas[i] = growth;
      deltas[i + 1] = -growth;
    } else if (i == n - 1) {
      deltas[i] = growth;
      deltas[i - 1] = -growth;
    } else {
      final half = growth / 2.0;
      deltas[i] = growth;
      deltas[i - 1] = -half;
      deltas[i + 1] = -half;
    }

    return deltas;
  }

  /// Preferred main-axis extent for an overflow menu / paging trigger.
  double overflowTriggerExtent(M3EButtonSize size) =>
      clampDouble(containerHeightFor(size), 40, 56);

  /// Minimum accessible target edge (48dp when visual height is smaller).
  double minTargetFor(M3EButtonSize size) {
    final h = containerHeightFor(size);
    return h < 48 ? 48 : h;
  }

  /// Spec container heights (32 / 40 / 56 / 96 / 136) plus [density] adjustment.
  double containerHeightFor(
    M3EButtonSize size, {
    M3EButtonGroupDensity density = M3EButtonGroupDensity.regular,
  }) {
    final base = switch (size) {
      M3EButtonSize.xs => 32.0,
      M3EButtonSize.sm => 40.0,
      M3EButtonSize.md => 56.0,
      M3EButtonSize.lg => 96.0,
      M3EButtonSize.xl => 136.0,
      _ => size.height ?? 56.0,
    };
    return math.max(24, base + density.heightAdjustment);
  }

  /// Fallback main-axis estimate before labeled actions are measured.
  double fallbackChildWidth(M3EButtonSize size) => switch (size) {
    M3EButtonSize.xs => 56,
    M3EButtonSize.sm => 80,
    M3EButtonSize.md => 100,
    M3EButtonSize.lg => 120,
    M3EButtonSize.xl => 140,
    _ => size.width ?? 100,
  };

  @override
  M3EButtonGroupTheme copyWith({
    double? connectedGap,
    double? dividerThickness,
    double? expandedRatio,
    double? fullRoundRadius,
    double? maxWidth,
    bool clearMaxWidth = false,
    M3ESpring? neighborSquishSpring,
    double? standardSpacingOverride,
    bool clearStandardSpacingOverride = false,
    double? connectedInnerRadiusOverride,
    bool clearConnectedInnerRadiusOverride = false,
    double? connectedPressedInnerRadiusOverride,
    bool clearConnectedPressedInnerRadiusOverride = false,
    double? connectedOuterSquareRadiusOverride,
    bool clearConnectedOuterSquareRadiusOverride = false,
  }) {
    return M3EButtonGroupTheme(
      connectedGap: connectedGap ?? this.connectedGap,
      dividerThickness: dividerThickness ?? this.dividerThickness,
      expandedRatio: expandedRatio ?? this.expandedRatio,
      fullRoundRadius: fullRoundRadius ?? this.fullRoundRadius,
      maxWidth: clearMaxWidth ? null : (maxWidth ?? this.maxWidth),
      neighborSquishSpring: neighborSquishSpring ?? this.neighborSquishSpring,
      standardSpacingOverride: clearStandardSpacingOverride
          ? null
          : (standardSpacingOverride ?? this.standardSpacingOverride),
      connectedInnerRadiusOverride: clearConnectedInnerRadiusOverride
          ? null
          : (connectedInnerRadiusOverride ?? this.connectedInnerRadiusOverride),
      connectedPressedInnerRadiusOverride:
          clearConnectedPressedInnerRadiusOverride
          ? null
          : (connectedPressedInnerRadiusOverride ??
                this.connectedPressedInnerRadiusOverride),
      connectedOuterSquareRadiusOverride:
          clearConnectedOuterSquareRadiusOverride
          ? null
          : (connectedOuterSquareRadiusOverride ??
                this.connectedOuterSquareRadiusOverride),
    );
  }

  @override
  M3EButtonGroupTheme lerp(M3EButtonGroupTheme? other, double t) {
    if (other is! M3EButtonGroupTheme) {
      return this;
    }
    return M3EButtonGroupTheme(
      connectedGap: _lerpDouble(connectedGap, other.connectedGap, t)!,
      dividerThickness: _lerpDouble(
        dividerThickness,
        other.dividerThickness,
        t,
      )!,
      expandedRatio: _lerpDouble(expandedRatio, other.expandedRatio, t)!,
      fullRoundRadius: _lerpDouble(fullRoundRadius, other.fullRoundRadius, t)!,
      maxWidth: t < 0.5 ? maxWidth : other.maxWidth,
      neighborSquishSpring: t < 0.5
          ? neighborSquishSpring
          : other.neighborSquishSpring,
      standardSpacingOverride: t < 0.5
          ? standardSpacingOverride
          : other.standardSpacingOverride,
      connectedInnerRadiusOverride: t < 0.5
          ? connectedInnerRadiusOverride
          : other.connectedInnerRadiusOverride,
      connectedPressedInnerRadiusOverride: t < 0.5
          ? connectedPressedInnerRadiusOverride
          : other.connectedPressedInnerRadiusOverride,
      connectedOuterSquareRadiusOverride: t < 0.5
          ? connectedOuterSquareRadiusOverride
          : other.connectedOuterSquareRadiusOverride,
    );
  }

  double? _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
