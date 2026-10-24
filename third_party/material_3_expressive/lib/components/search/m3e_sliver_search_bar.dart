import 'package:flutter/rendering.dart' show FloatingHeaderSnapConfiguration;
import 'package:flutter/widgets.dart';

import '../../foundations/foundations.dart';
import 'enums/m3e_search_enums.dart';
import 'm3e_search_bar.dart';
import 'utils/m3e_search_spring.dart';

/// Places a search bar at the top of a [CustomScrollView].
///
/// With [M3ESearchBarScrollBehavior.scrollAway] the bar scrolls away with
/// content and springs back as soon as the user scrolls toward the top.
/// With [M3ESearchBarScrollBehavior.fixed] it stays at the top.
class M3ESliverSearchBar extends StatefulWidget {
  /// M3ESliverSearchBar.
  const M3ESliverSearchBar({
    required this.child,
    this.scrollBehavior = M3ESearchBarScrollBehavior.scrollAway,
    this.padding = EdgeInsets.zero,
    this.spring = M3EMotion.expressiveSpatialDefault,
    this.height,
    super.key,
  });

  /// Usually an [M3ESearchBar] or an anchor built with one.
  final Widget child;

  /// Scroll away and reappear, or stay fixed.
  final M3ESearchBarScrollBehavior scrollBehavior;

  /// Space around [child] inside the header.
  final EdgeInsetsGeometry padding;

  /// Spring for the reappear snap.
  final M3ESpring spring;

  /// Header extent without [padding]. Defaults to the search bar height (56).
  final double? height;

  @override
  State<M3ESliverSearchBar> createState() => _M3ESliverSearchBarState();
}

class _M3ESliverSearchBarState extends State<M3ESliverSearchBar>
    with TickerProviderStateMixin {
  @override
  Widget build(BuildContext context) {
    final double extent =
        (widget.height ?? M3ETheme.of(context).searchBarTheme.minHeight) +
        widget.padding.vertical;
    final floating =
        widget.scrollBehavior == M3ESearchBarScrollBehavior.scrollAway;
    return SliverPersistentHeader(
      pinned: !floating,
      floating: floating,
      delegate: _M3ESliverSearchBarDelegate(
        extent: extent,
        vsync: floating ? this : null,
        snap: floating
            ? FloatingHeaderSnapConfiguration(
                curve: m3eSearchSpringCurve(widget.spring),
                duration: m3eSearchSpringSettle(widget.spring),
              )
            : null,
        child: Padding(padding: widget.padding, child: widget.child),
      ),
    );
  }
}

class _M3ESliverSearchBarDelegate extends SliverPersistentHeaderDelegate {
  _M3ESliverSearchBarDelegate({
    required this.extent,
    required this.vsync,
    required this.snap,
    required this.child,
  });

  final double extent;
  final Widget child;
  final FloatingHeaderSnapConfiguration? snap;

  @override
  final TickerProvider? vsync;

  @override
  double get minExtent => extent;

  @override
  double get maxExtent => extent;

  @override
  FloatingHeaderSnapConfiguration? get snapConfiguration => snap;

  @override
  Widget build(
    BuildContext context,
    double shrinkOffset,
    bool overlapsContent,
  ) {
    return ClipRect(child: Align(child: child));
  }

  @override
  bool shouldRebuild(_M3ESliverSearchBarDelegate oldDelegate) {
    return extent != oldDelegate.extent ||
        child != oldDelegate.child ||
        vsync != oldDelegate.vsync ||
        snap?.duration != oldDelegate.snap?.duration;
  }
}
