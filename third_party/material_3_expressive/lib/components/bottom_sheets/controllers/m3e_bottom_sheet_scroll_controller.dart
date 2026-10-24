import 'package:flutter/widgets.dart';

/// Sheet that an inner scroll view hands drags to.
abstract class M3EBottomSheetDragTarget {
  /// Whether the sheet is at its tallest height.
  bool get isAtMax;

  /// Whether dragging may resize the sheet.
  bool get canDrag;

  /// Moves the sheet by [delta] dp (positive = down).
  void dragBy(double delta);

  /// Snaps after a drag; [velocity] in dp/s (positive = up).
  void settle(double velocity);
}

/// Scroll controller that resizes the sheet before scrolling its content.
///
/// While the list is at its top, dragging down shrinks the sheet and
/// dragging up grows it until it reaches its tallest height.
class M3EBottomSheetScrollController extends ScrollController {
  /// M3EBottomSheetScrollController.
  M3EBottomSheetScrollController({required this.sheet})
    : super(debugLabel: 'M3EBottomSheet');

  /// Sheet that receives the drags.
  final M3EBottomSheetDragTarget sheet;

  @override
  ScrollPosition createScrollPosition(
    ScrollPhysics physics,
    ScrollContext context,
    ScrollPosition? oldPosition,
  ) {
    return _M3EBottomSheetScrollPosition(
      sheet: sheet,
      physics: physics,
      context: context,
      oldPosition: oldPosition,
    );
  }
}

class _M3EBottomSheetScrollPosition extends ScrollPositionWithSingleContext {
  _M3EBottomSheetScrollPosition({
    required this.sheet,
    required super.physics,
    required super.context,
    super.oldPosition,
  });

  final M3EBottomSheetDragTarget sheet;
  bool _movedSheet = false;

  bool get _listAtTop => pixels <= minScrollExtent;

  @override
  void applyUserOffset(double delta) {
    final bool grow = delta < 0 && !sheet.isAtMax;
    final bool shrink = delta > 0;
    if (sheet.canDrag && _listAtTop && (grow || shrink)) {
      _movedSheet = true;
      sheet.dragBy(delta);
      return;
    }
    super.applyUserOffset(delta);
  }

  @override
  void goBallistic(double velocity) {
    if (!_movedSheet) {
      super.goBallistic(velocity);
      return;
    }
    _movedSheet = false;
    sheet.settle(velocity);
    super.goBallistic(0);
  }
}
