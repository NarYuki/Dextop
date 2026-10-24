import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

enum _SlotStatus { visible, collapsing }

/// Lightweight per-item bookkeeping for a dismissible card slot.
class M3EDismissibleSlot {
  _SlotStatus _status;

  /// capturedHeight.
  double capturedHeight = 0;

  /// capturedWidth.
  double capturedWidth = 0;

  /// dismissedDirection.
  DismissDirection? dismissedDirection;

  /// collapseCtrl.
  SingleMotionController? collapseCtrl;

  /// flyCtrl.
  SingleMotionController? flyCtrl;

  /// flyNotifier.
  final ValueNotifier<double> flyNotifier = ValueNotifier(0);
  bool _flyDisposed = false;

  /// frozenChild.
  Widget? frozenChild;

  /// identity.
  final Object identity = Object();

  /// M3EDismissibleSlot.

  M3EDismissibleSlot() : _status = _SlotStatus.visible;

  /// The isVisible.

  bool get isVisible => _status == _SlotStatus.visible;

  /// The isCollapsing.
  bool get isCollapsing => _status == _SlotStatus.collapsing;

  /// Transitions the slot into the collapsing state.
  void markCollapsing() => _status = _SlotStatus.collapsing;

  /// dispose.

  void dispose() {
    collapseCtrl?.dispose();
    flyCtrl?.dispose();
    disposeFlyNotifier();
  }

  /// disposeFlyNotifier.

  void disposeFlyNotifier() {
    if (!_flyDisposed) {
      _flyDisposed = true;
      flyNotifier.dispose();
    }
  }
}
