import '../../../foundations/tokens/m3e_elevation.dart';

/// Numeric constants matching Compose Material 3 toolbar tokens.
abstract final class M3EToolbarTokens {
  const M3EToolbarTokens._();

  /// Cross-axis size for floating and docked expressive toolbars.
  static const double containerSize = 64;

  /// floatingContentPadding.

  static const double floatingContentPadding = 8;

  /// dockedHorizontalPadding.
  static const double dockedHorizontalPadding = 16;

  /// Minimum gap between docked actions, and the floating action gap.
  static const double containerBetweenSpace = 4;

  /// Preferred docked gap. Shrinks toward [containerBetweenSpace] when tight.
  static const double dockedPreferredGap = 32;

  /// Gap used when a wide docked toolbar centers its actions.
  static const double centeredGap = 8;

  /// toolbarToFabGap.
  static const double toolbarToFabGap = 8;

  /// Screen margin for a horizontal floating toolbar.
  static const double screenOffset = 16;

  /// Screen margin for a vertical floating toolbar.
  static const double verticalScreenOffset = 24;

  /// Window width below which a docked toolbar always spaces items evenly.
  static const double compactBreakpoint = 600;

  /// fabBaseline.

  static const double fabBaseline = 56;

  /// Icon size for the expanded (56) FAB.
  static const double fabExpandedIcon = 24;

  /// fabMedium.
  static const double fabMedium = 80;

  /// Icon size for the collapsed (80) FAB.
  static const double fabCollapsedIcon = 28;

  /// Docked corner radius. Straight corners.
  static const double dockedRadius = 0;

  /// Disabled icon and label opacity.
  static const double disabledContentAlpha = 0.38;

  /// elevationNone.

  static const double elevationNone = 0;

  /// Floating toolbar elevation. Matches [M3EElevation.level3].
  static const double floatingElevation = M3EElevation.level3;

  /// Legacy name. Floating elevation no longer changes when a FAB is present.
  static const double elevationWithFabExpanded = floatingElevation;
}
