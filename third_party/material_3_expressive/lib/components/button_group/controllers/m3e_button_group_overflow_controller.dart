import 'package:flutter/widgets.dart';

import '../models/m3e_button_group_overflow_paging_window.dart';

/// Reactive overflow / paging state for a button group.
class M3EButtonGroupOverflowController {
  /// Creates an overflow controller.
  M3EButtonGroupOverflowController({
    int windowStartIndex = 0,
    bool stableAllOverflowMeasured = false,
  }) : windowStartIndex = ValueNotifier<int>(windowStartIndex),
       stableAllOverflowMeasured = ValueNotifier<bool>(
         stableAllOverflowMeasured,
       );

  /// Start index of the current paging window.
  final ValueNotifier<int> windowStartIndex;

  /// Whether labeled action extents are measured and layout can stabilize.
  final ValueNotifier<bool> stableAllOverflowMeasured;

  /// Disposes notifiers owned by this controller.
  void dispose() {
    windowStartIndex.dispose();
    stableAllOverflowMeasured.dispose();
  }

  /// Rounds a consumed main-axis extent up to a whole pixel.
  static double roundConsumed(double extent) => extent.ceilToDouble();

  /// Rounds an available main-axis extent down to a whole pixel.
  static double roundAvailable(double extent) => extent.floorToDouble();

  /// Whether [current] differs from [last] by more than half a pixel.
  static bool hasMainExtentChanged(double? last, double current) {
    return last == null || (last - current).abs() > 0.5;
  }

  /// How many leading items fit before an overflow menu trigger is required.
  int computeVisibleCountForMenu({
    required double maxMain,
    required List<double> itemExtents,
    required double triggerExtent,
    required double Function() separatorExtent,
  }) {
    final availableMain = roundAvailable(maxMain);
    var currentExtent = 0.toDouble();
    var visibleCount = 0;

    for (var i = 0; i < itemExtents.length; i++) {
      final gapBefore = i == 0 ? 0.0 : separatorExtent();
      final remainingAfter = itemExtents.length - i - 1;
      final reservedForTrigger = remainingAfter > 0
          ? separatorExtent() + triggerExtent
          : 0.0;
      final nextExtent = currentExtent + gapBefore + itemExtents[i];
      if (nextExtent + reservedForTrigger < availableMain) {
        currentExtent = nextExtent;
        visibleCount = i + 1;
      } else {
        break;
      }
    }

    return visibleCount;
  }

  /// Visible paging window for [windowStartIndex] and [maxMain].
  M3EButtonGroupOverflowPagingWindow computePagingWindow({
    required double maxMain,
    required List<double> itemExtents,
    required double triggerExtent,
    required double Function(int indexBefore) separatorBetweenItems,
    required double Function({required bool isFirst}) separatorBeforeOverflow,
  }) {
    final effectiveMaxMain = roundAvailable(maxMain);
    final windowStart = windowStartIndex.value.clamp(0, itemExtents.length);
    final needsBack = windowStart > 0;

    var currentWidth = 0.toDouble();
    if (needsBack) {
      currentWidth += triggerExtent + separatorBeforeOverflow(isFirst: true);
    }

    final fitted = _fitPagingForward(
      itemExtents: itemExtents,
      windowStart: windowStart,
      needsBack: needsBack,
      currentWidth: currentWidth,
      effectiveMaxMain: effectiveMaxMain,
      triggerExtent: triggerExtent,
      separatorBeforeOverflow: separatorBeforeOverflow,
    );

    final window = M3EButtonGroupOverflowPagingWindow(
      start: windowStart,
      end: fitted.windowEnd,
      needsBack: needsBack,
      needsForward: fitted.needsForward,
    );

    if (window.start != windowStartIndex.value) {
      windowStartIndex.value = window.start;
    }

    return window;
  }

  ({int windowEnd, bool needsForward}) _fitPagingForward({
    required List<double> itemExtents,
    required int windowStart,
    required bool needsBack,
    required double currentWidth,
    required double effectiveMaxMain,
    required double triggerExtent,
    required double Function({required bool isFirst}) separatorBeforeOverflow,
  }) {
    final grown = _growPagingWindow(
      itemExtents: itemExtents,
      windowStart: windowStart,
      needsBack: needsBack,
      currentWidth: currentWidth,
      effectiveMaxMain: effectiveMaxMain,
      separatorBeforeOverflow: separatorBeforeOverflow,
    );
    if (!grown.needsForward) {
      return (windowEnd: grown.windowEnd, needsForward: false);
    }
    return (
      windowEnd: _shrinkPagingWindowForForwardTrigger(
        itemExtents: itemExtents,
        windowStart: windowStart,
        windowEnd: grown.windowEnd,
        needsBack: needsBack,
        currentWidth: grown.width,
        effectiveMaxMain: effectiveMaxMain,
        triggerExtent: triggerExtent,
        separatorBeforeOverflow: separatorBeforeOverflow,
      ),
      needsForward: true,
    );
  }

  ({int windowEnd, bool needsForward, double width}) _growPagingWindow({
    required List<double> itemExtents,
    required int windowStart,
    required bool needsBack,
    required double currentWidth,
    required double effectiveMaxMain,
    required double Function({required bool isFirst}) separatorBeforeOverflow,
  }) {
    var width = currentWidth;
    var windowEnd = windowStart;
    for (var i = windowStart; i < itemExtents.length; i++) {
      final gap = separatorBeforeOverflow(
        isFirst: i == windowStart && !needsBack,
      );
      if (width + gap + itemExtents[i] >= effectiveMaxMain) {
        return (windowEnd: windowEnd, needsForward: true, width: width);
      }
      width += gap + itemExtents[i];
      windowEnd = i;
    }
    return (windowEnd: windowEnd, needsForward: false, width: width);
  }

  int _shrinkPagingWindowForForwardTrigger({
    required List<double> itemExtents,
    required int windowStart,
    required int windowEnd,
    required bool needsBack,
    required double currentWidth,
    required double effectiveMaxMain,
    required double triggerExtent,
    required double Function({required bool isFirst}) separatorBeforeOverflow,
  }) {
    var width = currentWidth;
    var end = windowEnd;
    while (end >= windowStart) {
      final gapBeforeForward = separatorBeforeOverflow(isFirst: false);
      if (width + gapBeforeForward + triggerExtent < effectiveMaxMain) {
        break;
      }
      final gapBeforeItem = separatorBeforeOverflow(
        isFirst: end == windowStart && !needsBack,
      );
      width -= gapBeforeItem + itemExtents[end];
      end--;
    }
    return end;
  }
}
