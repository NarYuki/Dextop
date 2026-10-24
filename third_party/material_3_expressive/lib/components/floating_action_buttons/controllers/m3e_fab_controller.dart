import 'package:flutter/widgets.dart';

/// Drives FAB scroll visibility, appear morph, and container transform.
///
/// Attach to a [ScrollController] with [attachScroll], or feed notifications
/// via [handleScrollNotification] / [M3EFabScrollVisibility].
class M3EFabController extends ChangeNotifier {
  /// Creates a FAB controller.
  M3EFabController({this.scrollThreshold = 8});

  /// Scroll delta (dp) before toggling visibility.
  final double scrollThreshold;

  bool _visible = true;
  bool _hasAppeared = false;
  double _appearProgress = 1;
  double _visibilityProgress = 1;
  double _scrollAccum = 0;
  ScrollController? _scroll;
  VoidCallback? _scrollListener;
  M3EFabControllerClient? _client;
  double? _lastScrollOffset;

  /// Whether the FAB should be shown (scroll / manual).
  bool get isVisible => _visible;

  /// Whether [playAppear] has completed at least once.
  bool get hasAppeared => _hasAppeared;

  /// 0 = appear start, 1 = fully appeared.
  double get appearProgress => _appearProgress;

  /// 0 = dismissed, 1 = fully visible.
  double get visibilityProgress => _visibilityProgress;

  /// Whether a container transform is open.
  bool get isOpen => _client?.isTransformOpen ?? false;

  /// Binds the owning FAB state.
  // ignore: use_setters_to_change_properties -- attach/detach pair.
  void attachClient(M3EFabControllerClient client) {
    _client = client;
  }

  /// Releases the owning FAB state without disposing this controller.
  void detachClient(M3EFabControllerClient client) {
    if (identical(_client, client)) {
      _client = null;
    }
  }

  /// Updates appear progress from the FAB animation (0→1).
  void updateAppearProgress(double value) {
    final double coerced = value.clamp(0.0, 1.0);
    if (_appearProgress == coerced) {
      return;
    }
    _appearProgress = coerced;
    if (coerced >= 1) {
      _hasAppeared = true;
    }
    notifyListeners();
  }

  /// Updates visibility progress from the FAB animation (0→1).
  void updateVisibilityProgress(double value) {
    final double coerced = value.clamp(0.0, 1.0);
    if (_visibilityProgress == coerced) {
      return;
    }
    _visibilityProgress = coerced;
    notifyListeners();
  }

  /// Shows the FAB (spring dismiss reverse).
  void show() {
    if (_visible) {
      return;
    }
    _visible = true;
    _scrollAccum = 0;
    notifyListeners();
    _client?.onVisibilityRequested(visible: true);
  }

  /// Hides the FAB (scroll dismiss / manual).
  void hide() {
    if (!_visible) {
      return;
    }
    _visible = false;
    _scrollAccum = 0;
    notifyListeners();
    _client?.onVisibilityRequested(visible: false);
  }

  /// Toggles [isVisible].
  void toggleVisibility() {
    if (_visible) {
      hide();
    } else {
      show();
    }
  }

  /// Plays the appear morph from compact scale into resting size.
  void playAppear() {
    _hasAppeared = false;
    _appearProgress = 0;
    notifyListeners();
    _client?.onAppearRequested();
  }

  /// Opens the container transform using the FAB open builder, or [builder].
  Future<T?> open<T extends Object?>({WidgetBuilder? builder}) {
    final client = _client;
    if (client == null) {
      return Future<T?>.value();
    }
    return client.openTransform<T>(builder: builder);
  }

  /// Closes an open container transform.
  void close() => _client?.closeTransform();

  /// Listens to [controller] for scroll-driven show/hide.
  void attachScroll(ScrollController controller) {
    detachScroll();
    _scroll = controller;
    _lastScrollOffset = controller.hasClients ? controller.offset : null;
    _scrollListener = () {
      if (!_scroll!.hasClients) {
        return;
      }
      final double offset = _scroll!.offset;
      final double? last = _lastScrollOffset;
      _lastScrollOffset = offset;
      if (last == null) {
        return;
      }
      _applyScrollDelta(offset - last);
    };
    controller.addListener(_scrollListener!);
  }

  /// Stops listening to the attached [ScrollController].
  void detachScroll() {
    final ScrollController? scroll = _scroll;
    final VoidCallback? listener = _scrollListener;
    if (scroll != null && listener != null) {
      scroll.removeListener(listener);
    }
    _scroll = null;
    _scrollListener = null;
    _scrollAccum = 0;
    _lastScrollOffset = null;
  }

  /// Consumes scroll notifications for show-on-up / hide-on-down.
  ///
  /// Returns false so nested scrollables keep receiving the notification.
  bool handleScrollNotification(ScrollNotification notification) {
    if (notification is! ScrollUpdateNotification) {
      return false;
    }
    final double? delta = notification.scrollDelta;
    if (delta == null || delta == 0) {
      return false;
    }
    _applyScrollDelta(delta);
    return false;
  }

  void _applyScrollDelta(double delta) {
    // Positive delta = content moving up = finger scrolling down → hide.
    if (delta > 0) {
      _scrollAccum = _scrollAccum > 0 ? _scrollAccum + delta : delta;
      if (_scrollAccum >= scrollThreshold) {
        hide();
      }
    } else if (delta < 0) {
      _scrollAccum = _scrollAccum < 0 ? _scrollAccum + delta : delta;
      if (_scrollAccum.abs() >= scrollThreshold) {
        show();
      }
    }
  }

  @override
  void dispose() {
    detachScroll();
    _client = null;
    super.dispose();
  }
}

/// Wraps a scrollable and forwards notifications to [controller].
class M3EFabScrollVisibility extends StatelessWidget {
  /// Creates a scroll-visibility bridge for [controller].
  const M3EFabScrollVisibility({
    required this.controller,
    required this.child,
    super.key,
  });

  /// FAB controller receiving scroll updates.
  final M3EFabController controller;

  /// Scrollable subtree.
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return NotificationListener<ScrollNotification>(
      onNotification: controller.handleScrollNotification,
      child: child,
    );
  }
}

/// Host bridge implemented by FAB state. Not intended for app code.
abstract class M3EFabControllerClient {
  /// Whether a container transform overlay is showing.
  bool get isTransformOpen;

  /// Animates visibility to match [visible].
  void onVisibilityRequested({required bool visible});

  /// Starts the appear morph.
  void onAppearRequested();

  /// Opens the container transform.
  Future<T?> openTransform<T extends Object?>({WidgetBuilder? builder});

  /// Closes the container transform.
  void closeTransform();
}
