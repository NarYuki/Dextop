import 'package:material_ui/material_ui.dart';

import 'm3e_navigation_rail_destination.dart';

/// Section groups a header and a list of destinations. One class per file.
class M3ENavigationRailSection {
  /// Creates a [M3ENavigationRailSection].
  const M3ENavigationRailSection({required this.destinations, this.header});

  /// Destinations shown in this section.
  final List<M3ENavigationRailDestination> destinations;

  /// Optional header widget displayed above the destinations.
  final Widget? header;
}
