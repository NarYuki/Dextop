import 'm3e_navigation_destination.dart';

/// A labeled group of destinations below the drawer's primary list.
class M3ENavigationDrawerSection {
  /// Creates a section. A divider is painted before the group.
  const M3ENavigationDrawerSection({required this.destinations, this.header});

  /// Destinations in this group. Indices continue after earlier groups.
  final List<M3ENavigationDestination> destinations;

  /// Optional subhead, such as `Labels`.
  final String? header;
}
