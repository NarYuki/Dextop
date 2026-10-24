part of '../m3e_app_bars.dart';

/// Bottom bar. Resting fill stays surface container; elevation follows scroll.
class _M3EBottomAppBar extends StatefulWidget {
  const _M3EBottomAppBar({required this.bar});

  final M3EAppBar bar;

  @override
  State<_M3EBottomAppBar> createState() => _M3EBottomAppBarState();
}

class _M3EBottomAppBarState extends State<_M3EBottomAppBar> {
  ScrollNotificationObserverState? _observer;
  bool _under = false;
  final _M3EPageScroll _pageScroll = _M3EPageScroll();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final ScrollNotificationObserverState? next =
        ScrollNotificationObserver.maybeOf(context);
    if (next == _observer) {
      return;
    }
    _observer?.removeListener(_onNotification);
    _observer = next;
    _observer?.addListener(_onNotification);
  }

  @override
  void dispose() {
    _observer?.removeListener(_onNotification);
    super.dispose();
  }

  void _onNotification(ScrollNotification notification) {
    if (!mounted || !_pageScroll.accepts(notification, context)) {
      return;
    }
    final bool under = notification.metrics.extentBefore > 0;
    if (under != _under && mounted) {
      setState(() => _under = under);
    }
  }

  @override
  Widget build(BuildContext context) {
    final M3EAppBar bar = widget.bar;
    final theme = M3ETheme.of(context);
    final appBarTheme = theme.appBarTheme;
    final scheme = theme.colorScheme;
    final metrics = appBarTheme.metrics(bar.density);
    final contentPadding = appBarTheme.bottomPadding.resolve(
      Directionality.of(context),
    );
    final Widget contentBand = SizedBox(
      height: appBarTheme.bottomHeight,
      child: Padding(
        padding: contentPadding,
        child: Row(
          children: <Widget>[
            IconTheme.merge(
              data: IconThemeData(
                color: scheme.onSurfaceVariant,
                size: appBarTheme.bottomIconSize,
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: bar.actions ?? const <Widget>[],
              ),
            ),
            const Spacer(),
            ?bar.floatingActionButton,
          ],
        ),
      ),
    );
    return Material(
      color: appBarTheme.bottomBackgroundColor(scheme),
      elevation: _under ? metrics.scrolledElevation : metrics.elevation,
      shadowColor: scheme.shadow,
      surfaceTintColor: const Color(0x00000000),
      child: Padding(
        padding: bar._edgeSafeAreaInset(context),
        child: contentBand,
      ),
    );
  }
}
