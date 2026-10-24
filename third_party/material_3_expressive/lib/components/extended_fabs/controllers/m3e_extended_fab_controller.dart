import 'package:flutter/widgets.dart';

/// Drives extended FAB expand/collapse, appear morph, and container transform.
///
/// Attach to a [ScrollController] with [attachScroll], or feed notifications
/// via [handleScrollNotification] / [M3EExtendedFabScrollVisibility].
/// Scroll-down collapses the label; scroll-up expands it.
class M3EExtendedFabController extends ChangeNotifier {
  /// Creates an extended FAB controller.
  M3EExtendedFabController({
    this.scrollThreshold = 8,
    bool initiallyExtended = true,
  }) : _extended = initiallyExtended;

  /// Scroll delta (dp) before toggling extended state.
  final double scrollThreshold;

  bool _extended;
  bool _hasAppeared = false;
  double _appearProgress = 1;
  double _extendedProgress = 1;
  double _scrollAccum = 0;
  ScrollController? _scroll;
  VoidCallback? _scrollListener;
  M3EExtendedFabControllerClient? _client;
  double? _lastScrollOffset;

  /// Whether the label is shown (extended) vs collapsed icon-only.
  bool get isExtended => _extended;

  /// Whether [playAppear] has completed at least once.
  bool get hasAppeared => _hasAppeared;

  /// 0 = appear start, 1 = fully appeared.
  double get appearProgress => _appearProgress;

  /// 0 = collapsed, 1 = fully extended.
  double get extendedProgress => _extendedProgress;

  /// Whether a container transform is open.
  bool get isOpen => _client?.isTransformOpen ?? false;

  /// Binds the owning extended FAB state.
  // ignore: use_setters_to_change_properties -- attach/detach pair.
  void attachClient(M3EExtendedFabControllerClient client) {
    _client = client;
  }

  /// Releases the owning state without disposing this controller.
  void detachClient(M3EExtendedFabControllerClient client) {
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

  /// Updates extended progress from the FAB animation (0→1).
  void updateExtendedProgress(double value) {
    final double coerced = value.clamp(0.0, 1.0);
    if (_extendedProgress == coerced) {
      return;
    }
    _extendedProgress = coerced;
    notifyListeners();
  }

  /// Shows the label (spring expand).
  void expand() {
    if (_extended) {
      return;
    }
    _extended = true;
    _scrollAccum = 0;
    notifyListeners();
    _client?.onExtendedRequested(extended: true);
  }

  /// Hides the label (spring collapse to icon-only).
  void collapse() {
    if (!_extended) {
      return;
    }
    _extended = false;
    _scrollAccum = 0;
    notifyListeners();
    _client?.onExtendedRequested(extended: false);
  }

  /// Toggles [isExtended].
  void toggleExtended() {
    if (_extended) {
      collapse();
    } else {
      expand();
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

  /// Listens to [controller] for scroll-driven expand/collapse.
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

  /// Consumes scroll notifications for expand-on-up / collapse-on-down.
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
    // Positive delta = content moving up = finger scrolling down → collapse.
    if (delta > 0) {
      _scrollAccum = _scrollAccum > 0 ? _scrollAccum + delta : delta;
      if (_scrollAccum >= scrollThreshold) {
        collapse();
      }
    } else if (delta < 0) {
      _scrollAccum = _scrollAccum < 0 ? _scrollAccum + delta : delta;
      if (_scrollAccum.abs() >= scrollThreshold) {
        expand();
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
class M3EExtendedFabScrollVisibility extends StatelessWidget {
  /// Creates a scroll expand/collapse bridge for [controller].
  const M3EExtendedFabScrollVisibility({
    required this.controller,
    required this.child,
    super.key,
  });

  /// Extended FAB controller receiving scroll updates.
  final M3EExtendedFabController controller;

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

/// Host bridge implemented by extended FAB state. Not intended for app code.
abstract class M3EExtendedFabControllerClient {
  /// Whether a container transform overlay is showing.
  bool get isTransformOpen;

  /// Animates extended state to match [extended].
  void onExtendedRequested({required bool extended});

  /// Starts the appear morph.
  void onAppearRequested();

  /// Opens the container transform.
  Future<T?> openTransform<T extends Object?>({WidgetBuilder? builder});

  /// Closes the container transform.
  void closeTransform();
}
