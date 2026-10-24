import 'dart:math' as math;

import 'package:flutter/foundation.dart';

import '../enums/m3e_bottom_sheet_enums.dart';

/// Preset heights (in dp) a sheet can rest at, lowest first.
@immutable
class M3EBottomSheetDetents {
  /// M3EBottomSheetDetents.
  const M3EBottomSheetDetents(this.heights);

  /// Works out the preset heights for a sheet.
  ///
  /// [contentHeight] is the laid-out sheet height. Without [fullScreen] the
  /// sheet opens at most [initialFraction] of [screenHeight] and expands to
  /// its content, up to [screenHeight] minus [topMargin].
  factory M3EBottomSheetDetents.resolve({
    required double contentHeight,
    required double screenHeight,
    required double topMargin,
    required double initialFraction,
    required bool fullScreen,
    double? previewHeight,
  }) {
    final double maxHeight = math.max(0, screenHeight - topMargin);
    final double cap = screenHeight * initialFraction;
    final double expanded = fullScreen
        ? maxHeight
        : math.min(contentHeight, maxHeight);
    final heights = <M3EBottomSheetValue, double>{};
    final preview = previewHeight;
    if (preview != null && preview > 0 && preview < math.min(cap, expanded)) {
      heights[M3EBottomSheetValue.preview] = preview;
    }
    if (expanded > cap + 0.5) {
      heights[M3EBottomSheetValue.collapsed] = cap;
    }
    heights[M3EBottomSheetValue.expanded] = expanded;
    if (fullScreen) {
      heights[M3EBottomSheetValue.fullScreen] = screenHeight;
    }
    return M3EBottomSheetDetents(heights);
  }

  /// Visible height per value, lowest first. Never holds `hidden`.
  final Map<M3EBottomSheetValue, double> heights;

  /// Available values, lowest first.
  List<M3EBottomSheetValue> get values => heights.keys.toList();

  /// Tallest preset height.
  double get max => heights.values.last;

  /// Lowest preset height.
  double get min => heights.values.first;

  /// Visible height for [value]; 0 when hidden or missing.
  double heightOf(M3EBottomSheetValue value) => heights[value] ?? 0;

  /// [wanted] when available, else the closest available value above it.
  M3EBottomSheetValue resolve(M3EBottomSheetValue wanted) {
    if (wanted == M3EBottomSheetValue.hidden || heights.containsKey(wanted)) {
      return wanted;
    }
    for (final M3EBottomSheetValue value in values) {
      if (value.index > wanted.index) {
        return value;
      }
    }
    return values.last;
  }

  /// Value whose height is closest to [extent].
  M3EBottomSheetValue nearest(double extent) {
    M3EBottomSheetValue best = values.first;
    for (final M3EBottomSheetValue value in values) {
      if ((heights[value]! - extent).abs() < (heights[best]! - extent).abs()) {
        best = value;
      }
    }
    return best;
  }

  /// First value taller than [extent], or null.
  M3EBottomSheetValue? above(double extent) {
    for (final M3EBottomSheetValue value in values) {
      if (heights[value]! > extent + 0.5) {
        return value;
      }
    }
    return null;
  }

  /// First value lower than [extent], or null.
  M3EBottomSheetValue? below(double extent) {
    for (final M3EBottomSheetValue value in values.reversed) {
      if (heights[value]! < extent - 0.5) {
        return value;
      }
    }
    return null;
  }

  @override
  bool operator ==(Object other) =>
      other is M3EBottomSheetDetents && mapEquals(other.heights, heights);

  @override
  int get hashCode => Object.hashAllUnordered(heights.entries.map(_hash));

  static int _hash(MapEntry<M3EBottomSheetValue, double> e) =>
      Object.hash(e.key, e.value);
}
