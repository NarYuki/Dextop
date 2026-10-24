import 'package:flutter/widgets.dart';

/// Material 3 Expressive motion tokens.
///
/// Expressive motion favours spring based physics for spatial movement while
/// keeping duration/easing pairs for effects such as opacity and color. The
/// values below mirror the published M3 Expressive spring and easing sets.
abstract final class M3EMotion {
  const M3EMotion._();

  /// 50ms duration token.
  static const Duration short1 = Duration(milliseconds: 50);

  /// 100ms duration token.
  static const Duration short2 = Duration(milliseconds: 100);

  /// 150ms duration token.
  static const Duration short3 = Duration(milliseconds: 150);

  /// 200ms duration token.
  static const Duration short4 = Duration(milliseconds: 200);

  /// 250ms duration token.
  static const Duration medium1 = Duration(milliseconds: 250);

  /// 300ms duration token.
  static const Duration medium2 = Duration(milliseconds: 300);

  /// 350ms duration token.
  static const Duration medium3 = Duration(milliseconds: 350);

  /// 400ms duration token.
  static const Duration medium4 = Duration(milliseconds: 400);

  /// 450ms duration token.
  static const Duration long1 = Duration(milliseconds: 450);

  /// 500ms duration token.
  static const Duration long2 = Duration(milliseconds: 500);

  /// 550ms duration token.
  static const Duration long3 = Duration(milliseconds: 550);

  /// 600ms duration token.
  static const Duration long4 = Duration(milliseconds: 600);

  /// 700ms duration token.
  static const Duration extraLong1 = Duration(milliseconds: 700);

  /// 800ms duration token.
  static const Duration extraLong2 = Duration(milliseconds: 800);

  /// 900ms duration token.
  static const Duration extraLong3 = Duration(milliseconds: 900);

  /// 1000ms duration token.
  static const Duration extraLong4 = Duration(milliseconds: 1000);

  /// Standard easing curve.
  static const Curve standard = Cubic(0.2, 0, 0, 1);

  /// Standard accelerate easing curve.
  static const Curve standardAccelerate = Cubic(0.3, 0, 1, 1);

  /// Standard decelerate easing curve.
  static const Curve standardDecelerate = Cubic(0, 0, 0, 1);

  /// Emphasized easing curve.
  static const Curve emphasized = Cubic(0.2, 0, 0, 1);

  /// Emphasized accelerate easing curve.
  static const Curve emphasizedAccelerate = Cubic(0.3, 0, 0.8, 0.15);

  /// Emphasized decelerate easing curve.
  static const Curve emphasizedDecelerate = Cubic(0.05, 0.7, 0.1, 1);

  /// Linear easing curve.
  static const Curve linear = Cubic(0, 0, 1, 1);

  /// Fast spatial spring for size, position, and shape morphs.
  static const M3ESpring spatialFast = M3ESpring(stiffness: 1400, damping: 0.9);

  /// Default spatial spring for size, position, and shape morphs.
  static const M3ESpring spatialDefault = M3ESpring(
    stiffness: 700,
    damping: 0.9,
  );

  /// Slow spatial spring for size, position, and shape morphs.
  static const M3ESpring spatialSlow = M3ESpring(stiffness: 300, damping: 0.9);

  /// AOSP spatial spring (stiffness: 380, damping: 1.0).
  ///
  /// Matches AOSP notification-list expansion — no overshoot.
  static const M3ESpring aospSpatial = M3ESpring(stiffness: 380, damping: 1);

  /// Fast expressive spatial spring with slight overshoot.
  static const M3ESpring expressiveSpatialFast = M3ESpring(
    stiffness: 800,
    damping: 0.6,
  );

  /// Default expressive spatial spring with slight overshoot.
  static const M3ESpring expressiveSpatialDefault = M3ESpring(
    stiffness: 380,
    damping: 0.8,
  );

  /// Interactive press scale — same recipe as floating toolbar / button morph.
  static const M3ESpring expressiveSpatialPress = M3ESpring(
    stiffness: 380,
    damping: 0.55,
  );

  /// Slow expressive spatial spring with slight overshoot.
  static const M3ESpring expressiveSpatialSlow = M3ESpring(
    stiffness: 300,
    damping: 1,
  );

  /// Fast effects spring for color and opacity (no overshoot).
  static const M3ESpring effectsFast = M3ESpring(stiffness: 3800, damping: 1);

  /// Default effects spring for color and opacity (no overshoot).
  static const M3ESpring effectsDefault = M3ESpring(
    stiffness: 1600,
    damping: 1,
  );

  /// Slow effects spring for color and opacity (no overshoot).
  static const M3ESpring effectsSlow = M3ESpring(stiffness: 800, damping: 1);
}

/// A serialisable description of a Material 3 Expressive spring.
///
/// [damping] is expressed as a damping ratio where `1.0` is critically damped
/// (no overshoot) and values below `1.0` produce an expressive overshoot.
@immutable
class M3ESpring {
  /// Creates a spring with [stiffness] and [damping] ratio.
  const M3ESpring({required this.stiffness, required this.damping});

  /// Spring stiffness. Higher values settle faster.
  final double stiffness;

  /// Damping ratio. `1.0` is critically damped; lower values overshoot.
  final double damping;

  /// Builds a [SpringDescription] for a unit mass.
  SpringDescription toDescription() {
    return SpringDescription.withDampingRatio(
      mass: 1,
      stiffness: stiffness,
      ratio: damping,
    );
  }

  @override
  /// Equality based on public fields.
  bool operator ==(Object other) {
    return other is M3ESpring &&
        other.stiffness == stiffness &&
        other.damping == damping;
  }

  @override
  /// Hash code for this spring.
  int get hashCode => Object.hash(stiffness, damping);
}
