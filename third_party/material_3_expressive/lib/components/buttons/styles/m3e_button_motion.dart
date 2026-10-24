import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3EButton, M3EMotion;
import 'package:motor/motor.dart';

/// Spring-based motion configuration for [M3EButton].
@immutable
class M3EButtonMotion {
  /// stiffness.
  final double stiffness;

  /// damping.
  final double damping;

  /// M3EButtonMotion.

  const M3EButtonMotion({required this.stiffness, required this.damping});

  /// standard.

  static const M3EButtonMotion standard = M3EButtonMotion(
    stiffness: 1200,
    damping: 0.8,
  );

  /// standardOverflow.

  static const M3EButtonMotion standardOverflow = M3EButtonMotion(
    stiffness: 1600,
    damping: 0.85,
  );

  /// standardPopup.

  static const M3EButtonMotion standardPopup = M3EButtonMotion(
    stiffness: 1600,
    damping: 0.85,
  );

  /// expressiveEffectsFast.

  static const M3EButtonMotion expressiveEffectsFast = M3EButtonMotion(
    stiffness: 1600,
    damping: 0.6,
  );

  /// Matches foundation [M3EMotion.expressiveSpatialDefault] (380 / 0.8).
  static const M3EButtonMotion expressiveSpatialDefault = M3EButtonMotion(
    stiffness: 380,
    damping: 0.8,
  );

  /// Interactive press/release morph — same recipe as floating toolbar / FAB menu.
  static const M3EButtonMotion expressiveSpatialPress = M3EButtonMotion(
    stiffness: 380,
    damping: 0.55,
  );

  /// M3E button shape morph (stiffness 1400 / damping 0.9).
  static const M3EButtonMotion shapeMorph = M3EButtonMotion(
    stiffness: 1400,
    damping: 0.9,
  );

  /// standardSpatialFast.

  static const M3EButtonMotion standardSpatialFast = M3EButtonMotion(
    stiffness: 1400,
    damping: 0.8,
  );

  /// custom.

  factory M3EButtonMotion.custom(double stiffness, double damping) {
    return M3EButtonMotion(stiffness: stiffness, damping: damping);
  }

  /// Converts this configuration to a [SpringMotion].
  SpringMotion toMotion() => SpringMotion(
    SpringDescription.withDampingRatio(
      mass: 1,
      stiffness: stiffness,
      ratio: damping,
    ),
  );

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is M3EButtonMotion &&
          stiffness == other.stiffness &&
          damping == other.damping;

  @override
  int get hashCode => Object.hash(stiffness, damping);
}
