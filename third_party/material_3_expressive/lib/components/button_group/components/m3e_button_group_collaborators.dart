part of '../m3e_button_group.dart';

class _SpringMenuWrapper extends StatefulWidget {
  final Widget child;
  final M3EButtonMotion motion;
  final Alignment alignment;
  final bool isBottomSheet;

  const _SpringMenuWrapper({
    required this.child,
    required this.motion,
    required this.alignment,
    this.isBottomSheet = false,
  });

  @override
  State<_SpringMenuWrapper> createState() => _SpringMenuWrapperState();
}

class _SpringMenuWrapperState extends State<_SpringMenuWrapper>
    with SingleTickerProviderStateMixin {
  late SingleMotionController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = SingleMotionController(
      motion: widget.motion.toMotion(),
      vsync: this,
    );
    _ctrl.animateTo(1);
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (context, child) {
        final val = _ctrl.value;
        if (widget.isBottomSheet) {
          return Transform.translate(
            offset: Offset(0, 40 * (1.0 - val.clamp(0.0, 1.5))),
            child: child,
          );
        } else {
          // Match SplitButton popup feel: anchored uniform spring scale.
          // Keep spring overshoot visible by not clamping the upper bound.
          final scale = 0.72 + (val * 0.28);
          return Opacity(
            opacity: val.clamp(0.0, 1.0),
            child: Transform.scale(
              scale: scale,
              alignment: widget.alignment,
              child: child,
            ),
          );
        }
      },
      child: widget.child,
    );
  }
}

class _ButtonGroupFocusManager {
  _ButtonGroupFocusManager._();

  static List<FocusNode?> buildInternalFocusNodes(
    List<M3EButtonGroupAction> actions,
  ) {
    return List<FocusNode?>.generate(actions.length, (i) {
      final actionNode = actions[i].focusNode;
      if (actionNode != null) {
        return null;
      }
      return FocusNode(debugLabel: 'M3EButtonGroup Button $i');
    });
  }

  static void disposeInternalFocusNodes(List<FocusNode?> nodes) {
    for (final node in nodes) {
      node?.dispose();
    }
    nodes.clear();
  }

  static int computeFocusNodeSignature(List<M3EButtonGroupAction> actions) {
    var hash = 0;
    for (final action in actions) {
      hash = Object.hash(hash, action.focusNode);
    }
    return hash;
  }
}

class _ButtonGroupPressCoordinator {
  _ButtonGroupPressCoordinator({required this._isMounted});

  final bool Function() _isMounted;

  int? _lastPressedIndex;
  int? get lastPressedIndex => _lastPressedIndex;

  final ValueNotifier<int?> pressedIndexNotifier = ValueNotifier<int?>(null);

  double _pressProgress = 0;
  bool _isWaitingForRelease = false;
  Duration? _releaseDeadline;
  int? _physicallyPressedIndex;
  bool _disposed = false;

  void dispose() {
    _disposed = true;
    _isWaitingForRelease = false;
    _releaseDeadline = null;
    _physicallyPressedIndex = null;
    pressedIndexNotifier.dispose();
  }

  void clearPressedIndex() {
    _setPressedIndex(null);
  }

  void handlePressedStateChange({required int index, required bool isPressed}) {
    if (_disposed) {
      return;
    }
    if (isPressed) {
      _physicallyPressedIndex = index;
    } else if (_physicallyPressedIndex == index) {
      _physicallyPressedIndex = null;
    }
    if (isPressed && pressedIndexNotifier.value != index) {
      _isWaitingForRelease = false;
      _releaseDeadline = null;
      _setPressedIndex(index);
    } else if (!isPressed && pressedIndexNotifier.value == index) {
      _isWaitingForRelease = true;
      _scheduleReleaseCheck();
    }
  }

  void animateSelection(int index) {
    if (_disposed || _physicallyPressedIndex != null) {
      return;
    }
    _setPressedIndex(index);
    Future<void>.delayed(const Duration(milliseconds: 120), () {
      if (_disposed || !_isMounted()) {
        return;
      }
      if (_physicallyPressedIndex == null &&
          pressedIndexNotifier.value == index) {
        _setPressedIndex(null);
      }
    });
  }

  void onAnimationProgress(double animValue) {
    if (_disposed) {
      return;
    }
    if (animValue > 0.01 && _lastPressedIndex != null) {
      _pressProgress = animValue;
      if (_isWaitingForRelease) {
        _checkRelease();
      }
    }
  }

  void _scheduleReleaseCheck() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_disposed || !_isMounted()) {
        return;
      }
      _releaseDeadline =
          SchedulerBinding.instance.currentFrameTimeStamp +
          M3EButtonConstants.kReleaseTimeout;
      _checkRelease();
    });
  }

  void _checkRelease() {
    if (_disposed || !_isWaitingForRelease || !_isMounted()) {
      return;
    }
    final timedOut =
        _releaseDeadline != null &&
        SchedulerBinding.instance.currentFrameTimeStamp >= _releaseDeadline!;
    if (_pressProgress >= M3EButtonConstants.kPressReleaseThreshold ||
        timedOut) {
      _isWaitingForRelease = false;
      _releaseDeadline = null;
      _pressProgress = 0.0;
      _setPressedIndex(null);
    }
  }

  void _setPressedIndex(int? index) {
    if (_disposed) {
      return;
    }
    if (index != null) {
      _lastPressedIndex = index;
    }
    if (SchedulerBinding.instance.schedulerPhase ==
        SchedulerPhase.persistentCallbacks) {
      SchedulerBinding.instance.addPostFrameCallback((_) {
        if (_disposed || !_isMounted()) {
          return;
        }
        pressedIndexNotifier.value = index;
      });
      return;
    }
    if (!_isMounted()) {
      return;
    }
    pressedIndexNotifier.value = index;
  }
}

class _ButtonGroupMeasurementOrchestrator {
  int generation = 0;
  bool hasAnyLabel = false;

  List<GlobalKey> unselectedKeys = <GlobalKey>[];
  List<GlobalKey> selectedKeys = <GlobalKey>[];
  List<double?> measuredUnselectedWidths = <double?>[];
  List<double?> measuredSelectedWidths = <double?>[];

  List<WidgetStatesController>? _measurerUnselectedControllers;
  List<WidgetStatesController>? _measurerSelectedControllers;

  void initMeasurementState({
    required int actionCount,
    required M3EButtonGroupOverflowController overflowController,
    bool clearMeasuredWidths = false,
  }) {
    unselectedKeys = List.generate(actionCount, (_) => GlobalKey());
    selectedKeys = List.generate(actionCount, (_) => GlobalKey());

    // Keep last-known widths across selection/content remotion so interim
    // frames do not collapse labeled buttons. Clear on size/density so the
    // group container can recompute intrinsic widths for the new tokens.
    final lengthChanged =
        measuredUnselectedWidths.length != actionCount ||
        measuredSelectedWidths.length != actionCount;
    if (lengthChanged || clearMeasuredWidths) {
      measuredUnselectedWidths = List.filled(actionCount, null);
      measuredSelectedWidths = List.filled(actionCount, null);
    }

    // Remotion may produce new extents; remeasure before treating as stable.
    overflowController.stableAllOverflowMeasured.value = false;

    disposeMeasurerControllers();
    initMeasurerControllers(actionCount);
  }

  void initMeasurerControllers(int actionCount) {
    _measurerUnselectedControllers = List.generate(
      actionCount,
      (_) => WidgetStatesController(),
    );
    _measurerSelectedControllers = List.generate(
      actionCount,
      (_) => WidgetStatesController(),
    );
  }

  void disposeMeasurerControllers() {
    if (_measurerUnselectedControllers != null) {
      for (final c in _measurerUnselectedControllers!) {
        c.dispose();
      }
      _measurerUnselectedControllers = null;
    }
    if (_measurerSelectedControllers != null) {
      for (final c in _measurerSelectedControllers!) {
        c.dispose();
      }
      _measurerSelectedControllers = null;
    }
  }

  bool isMeasured(int index) {
    return measuredUnselectedWidths[index] != null &&
        measuredSelectedWidths[index] != null;
  }
}

class _FocusRingGapRenderer {
  _FocusRingGapRenderer._();

  static double resolveGap({
    required bool connected,
    required int? focusedIndex,
    required int beforeIndex,
    required double spacing,
    required double focusRingOutset,
  }) {
    var gap = spacing;
    if (connected) {
      final isFocusedLeft = focusedIndex == beforeIndex;
      final isFocusedRight = focusedIndex == beforeIndex + 1;
      if (isFocusedLeft || isFocusedRight) {
        gap += focusRingOutset;
      }
    }
    return gap;
  }
}

/// Tab-order traversal only (spec: Tab between items; Space/Enter activate).
class _M3EButtonGroupTabTraversalPolicy extends WidgetOrderTraversalPolicy {
  _M3EButtonGroupTabTraversalPolicy();

  @override
  // Spec documents Tab only; block arrow-key directional moves.
  // ignore: must_call_super
  bool inDirection(FocusNode currentNode, TraversalDirection direction) {
    return false;
  }
}
