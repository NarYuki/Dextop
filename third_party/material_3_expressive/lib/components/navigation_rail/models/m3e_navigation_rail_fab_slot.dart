import 'package:material_ui/material_ui.dart';

import '../../floating_action_buttons/enums/m3e_fab.dart';

/// Configuration for the rail's built-in FAB.
///
/// The rail renders:
/// - a `M3EFab` when collapsed
/// - an `M3EExtendedFab` when expanded
///
/// Consumers provide values (icon, label, onPressed, etc.) instead of a widget.
@immutable
class M3ENavigationRailFabSlot {
  /// Creates a [M3ENavigationRailFabSlot].
  const M3ENavigationRailFabSlot({
    required this.icon,
    required this.label,
    this.onPressed,
    this.tooltip,
    this.color = M3EFabColor.primary,
    this.size = M3EFabSize.regular,
    this.elevation,
    this.hoverElevation,
    this.semanticLabel,
  }) : assert(elevation == null || elevation >= 0.0, 'assertion failed'),
       assert(
         hoverElevation == null || hoverElevation >= 0.0,
         'assertion failed',
       );

  /// Icon widget shown inside the FAB (collapsed) and leading icon (expanded).
  final Widget icon;

  /// Text label for the extended FAB (expanded rail variant).
  final String label;

  /// Tap callback for the FAB.
  final VoidCallback? onPressed;

  /// Tooltip text for hover/long-press.
  final String? tooltip;

  /// Visual color role (primary, secondary, tertiary, surface).
  final M3EFabColor color;

  /// Size of the FAB button.
  final M3EFabSize size;

  /// Resting surface elevation for the rail FAB.
  final double? elevation;

  /// Surface elevation for the rail FAB while hovered.
  final double? hoverElevation;

  /// Optional semantic label for accessibility.
  final String? semanticLabel;
}
