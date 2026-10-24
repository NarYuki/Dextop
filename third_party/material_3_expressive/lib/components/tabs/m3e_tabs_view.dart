import 'package:flutter/widgets.dart';

/// Horizontal body for `M3ETabs`.
///
/// A sideways swipe settles on the next child and reports it through
/// [onTabSelected]. Vertical scrolling inside a child is left to that child.
class M3ETabsView extends StatefulWidget {
  /// Creates a swipeable tab body.
  const M3ETabsView({
    required this.selectedIndex,
    required this.onTabSelected,
    required this.children,
    super.key,
  }) : assert(children.length > 0, 'A tab view needs a child.');

  /// Page that should be showing.
  final int selectedIndex;

  /// Called when a swipe settles on a page.
  final ValueChanged<int> onTabSelected;

  /// One child per tab, in tab order.
  final List<Widget> children;

  @override
  State<M3ETabsView> createState() => _M3ETabsViewState();
}

class _M3ETabsViewState extends State<M3ETabsView> {
  late final PageController _page;

  @override
  void initState() {
    super.initState();
    _page = PageController(initialPage: _index);
  }

  @override
  void didUpdateWidget(covariant M3ETabsView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.selectedIndex == widget.selectedIndex) {
      return;
    }
    final int current = _page.hasClients
        ? (_page.page?.round() ?? _index)
        : _index;
    if (current == _index) {
      return;
    }
    _page.animateToPage(
      _index,
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeOut,
    );
  }

  @override
  void dispose() {
    _page.dispose();
    super.dispose();
  }

  int get _index {
    if (widget.children.isEmpty) {
      return 0;
    }
    return widget.selectedIndex.clamp(0, widget.children.length - 1);
  }

  @override
  Widget build(BuildContext context) {
    return PageView(
      controller: _page,
      children: widget.children,
      onPageChanged: (int index) {
        if (index != widget.selectedIndex) {
          widget.onTabSelected(index);
        }
      },
    );
  }
}
