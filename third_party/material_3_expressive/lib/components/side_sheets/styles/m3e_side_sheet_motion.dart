import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// Springs and predictive-back amounts for a side sheet.
@immutable
class M3ESideSheetMotion {
  /// M3ESideSheetMotion.
  const M3ESideSheetMotion({
    this.enterSpring = M3EMotion.spatialDefault,
    this.scrimSpring = M3EMotion.effectsDefault,
    this.layoutSpring = M3EMotion.spatialDefault,
    this.settleSpring = M3EMotion.spatialDefault,
    this.predictiveBackShrinkX = 24,
    this.predictiveBackGrowX = 12,
    this.predictiveBackLiftY = 48,
    this.predictiveBackCornerRadius = 16,
  });

  /// Spring for opening and closing.
  final M3ESpring enterSpring;

  /// Spring for the scrim fade.
  final M3ESpring scrimSpring;

  /// Spring for modal / standard morphs and body resizing.
  final M3ESpring layoutSpring;

  /// Spring back after a cancelled predictive back.
  final M3ESpring settleSpring;

  /// Width lost when the back swipe moves away from the anchored edge (24).
  final double predictiveBackShrinkX;

  /// Width gained when the back swipe starts at the anchored edge (12).
  final double predictiveBackGrowX;

  /// Height lost at full progress, split above and below (48).
  final double predictiveBackLiftY;

  /// Corner radius on every corner at full progress (16).
  final double predictiveBackCornerRadius;

  /// Returns a copy with the given fields replaced.
  M3ESideSheetMotion copyWith({
    M3ESpring? enterSpring,
    M3ESpring? scrimSpring,
    M3ESpring? layoutSpring,
    M3ESpring? settleSpring,
    double? predictiveBackShrinkX,
    double? predictiveBackGrowX,
    double? predictiveBackLiftY,
    double? predictiveBackCornerRadius,
  }) {
    return M3ESideSheetMotion(
      enterSpring: enterSpring ?? this.enterSpring,
      scrimSpring: scrimSpring ?? this.scrimSpring,
      layoutSpring: layoutSpring ?? this.layoutSpring,
      settleSpring: settleSpring ?? this.settleSpring,
      predictiveBackShrinkX:
          predictiveBackShrinkX ?? this.predictiveBackShrinkX,
      predictiveBackGrowX: predictiveBackGrowX ?? this.predictiveBackGrowX,
      predictiveBackLiftY: predictiveBackLiftY ?? this.predictiveBackLiftY,
      predictiveBackCornerRadius:
          predictiveBackCornerRadius ?? this.predictiveBackCornerRadius,
    );
  }

  /// Interpolates between this and [other]; springs switch at the midpoint.
  M3ESideSheetMotion lerp(M3ESideSheetMotion other, double t) {
    final end = t < 0.5 ? this : other;
    return end.copyWith(
      predictiveBackShrinkX: _lerp(
        predictiveBackShrinkX,
        other.predictiveBackShrinkX,
        t,
      ),
      predictiveBackGrowX: _lerp(
        predictiveBackGrowX,
        other.predictiveBackGrowX,
        t,
      ),
      predictiveBackLiftY: _lerp(
        predictiveBackLiftY,
        other.predictiveBackLiftY,
        t,
      ),
      predictiveBackCornerRadius: _lerp(
        predictiveBackCornerRadius,
        other.predictiveBackCornerRadius,
        t,
      ),
    );
  }

  static double _lerp(double a, double b, double t) => a + (b - a) * t;
}
