import 'package:flutter/services.dart';

/// Haptic feedback intensity levels for M3E interactions.
///
/// Use with [M3EHaptics.trigger], component decoration / style haptic fields,
/// or the shared tappable interaction primitive.
///
/// ## Levels
/// - [none] — No haptic feedback (default)
/// - [light] — Light tap feedback for subtle interactions
/// - [medium] — Medium impact for standard presses
/// - [heavy] — Heavy impact for significant actions
enum M3EHapticFeedback {
  /// No haptic feedback.
  none(0),

  /// Light tap feedback.
  light(1),

  /// Medium impact feedback.
  medium(2),

  /// Heavy impact feedback.
  heavy(3);

  /// Ordinal value for the level.
  final int value;

  const M3EHapticFeedback(this.value);
}

/// Shared haptic helpers for Material 3 Expressive components.
///
/// Prefer this API over raw [HapticFeedback] calls so intensity stays consistent
/// and platform no-ops remain centralized.
abstract final class M3EHaptics {
  const M3EHaptics._();

  /// Fires impact feedback for [level]. [M3EHapticFeedback.none] is a no-op.
  static void trigger(M3EHapticFeedback level) {
    switch (level) {
      case M3EHapticFeedback.light:
        HapticFeedback.lightImpact();
      case M3EHapticFeedback.medium:
        HapticFeedback.mediumImpact();
      case M3EHapticFeedback.heavy:
        HapticFeedback.heavyImpact();
      case M3EHapticFeedback.none:
        break;
    }
  }

  /// Fires selection feedback (e.g. discrete slider ticks, picker selection).
  static void selection() {
    HapticFeedback.selectionClick();
  }
}
