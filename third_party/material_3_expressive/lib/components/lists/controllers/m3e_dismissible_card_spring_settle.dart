part of 'm3e_dismissible_card_controller.dart';

/// Spring-settle motion (snap open, spring back, and their shared
/// push / neighbour / roundness controllers) for
/// [M3EDismissibleCardDragMixin].
extension M3EDismissibleCardSpringSettle<T extends StatefulWidget>
    on M3EDismissibleCardDragMixin<T> {
  void _startPushController({
    required double multiplier,
    required double target,
    double? initialValue,
  }) {
    _pushCtrl?.dispose();
    _pushCtrl =
        SingleMotionController(
            motion: _spatialMotion(
              style.detachPushSpring,
              stiffness: style.detachPushSpring.stiffness * multiplier,
            ),
            vsync: this,
            initialValue: initialValue ?? 0,
          )
          ..addListener(() {
            if (mounted) {
              setState(() => _detachPush = _pushCtrl!.value);
            }
          })
          ..addStatusListener(_onMotionSettled)
          ..animateTo(target);
  }

  void _startNeighbourController({
    required double multiplier,
    required double target,
  }) {
    _nbrCtrl?.dispose();
    _nbrCtrl =
        SingleMotionController(
            motion: _spatialMotion(
              style.neighbourSpring,
              stiffness: style.neighbourSpring.stiffness * multiplier,
            ),
            vsync: this,
            initialValue: _neighbourFraction,
          )
          ..addListener(() {
            if (mounted) {
              setState(() => _neighbourFraction = _nbrCtrl!.value);
            }
          })
          ..addStatusListener(_onMotionSettled)
          ..animateTo(target);
  }

  void _startRoundnessController({
    required double multiplier,
    required double target,
  }) {
    _roundnessCtrl?.dispose();
    _roundnessCtrl =
        SingleMotionController(
            motion: _spatialMotion(
              style.roundnessSnapSpring,
              stiffness: style.roundnessSnapSpring.stiffness * multiplier,
            ),
            vsync: this,
            initialValue: _roundnessFraction,
          )
          ..addListener(() {
            if (mounted) {
              setState(() => _roundnessFraction = _roundnessCtrl!.value);
            }
          })
          ..addStatusListener(_onMotionSettled)
          ..animateTo(target);
  }

  void _snapToRevealed(double targetOffset, double speedMul) {
    _lockHover();
    _pushCtrl?.dispose();
    _pushCtrl = null;
    _detachPush = 0.0;
    _pastThreshold = false;
    _pastActionThreshold = true;
    _reEngaging = false;

    final back = style.springBackSpring;
    _springCtrl?.dispose();
    _springCtrl =
        SingleMotionController(
            motion: _spatialMotion(back, stiffness: back.stiffness * speedMul),
            vsync: this,
            initialValue: _dragOffset,
          )
          ..addListener(() {
            if (mounted) {
              setState(() => _dragOffset = _springCtrl!.value);
            }
          })
          ..addStatusListener(_onMotionSettled)
          ..animateTo(targetOffset);

    _nbrCtrl?.dispose();
    _nbrCtrl =
        SingleMotionController(
            motion: _spatialMotion(back, stiffness: back.stiffness * speedMul),
            vsync: this,
            initialValue: _neighbourFraction,
          )
          ..addListener(() {
            if (mounted) {
              setState(() => _neighbourFraction = _nbrCtrl!.value);
            }
          })
          ..addStatusListener(_onMotionSettled)
          ..animateTo(0);

    _roundnessCtrl?.dispose();
    _roundnessCtrl =
        SingleMotionController(
            motion: _spatialMotion(back, stiffness: back.stiffness * speedMul),
            vsync: this,
            initialValue: _roundnessFraction,
          )
          ..addListener(() {
            if (mounted) {
              setState(() => _roundnessFraction = _roundnessCtrl!.value);
            }
          })
          ..addStatusListener(_onMotionSettled)
          ..animateTo(0);
  }

  void _resetDragState() {
    setState(() {
      _dragSlotRef = null;
      _dragSlotIndex = -1;
      _dragOffset = 0.0;
      _hoverLocked = false;
      _detachPush = 0.0;
      _neighbourFraction = 0.0;
      _pastThreshold = false;
      _pastActionThreshold = false;
      _isDismissDragging = false;
      _reEngaging = false;
      _roundnessFraction = 0.0;
    });
    _dismissDxAcc = 0;
  }

  void _playPullHaptics() {
    if (!style.enableFeedback) {
      return;
    }
    if (_hapticStopwatch.elapsedMilliseconds < _kVibrationThresholdMs) {
      return;
    }
    _hapticStopwatch.reset();
    M3EHaptics.selection();
  }

  void _springBack(double speedMul) {
    _lockHover();
    _pushCtrl?.dispose();
    _pushCtrl = null;
    _detachPush = 0.0;
    _roundnessCtrl?.dispose();
    _roundnessCtrl = null;
    _roundnessFraction = 0.0;
    _pastActionThreshold = false;

    final ref = _dragSlotRef;
    final back = style.springBackSpring;
    _springCtrl?.dispose();
    _springCtrl =
        SingleMotionController(
            motion: _spatialMotion(back, stiffness: back.stiffness * speedMul),
            vsync: this,
            initialValue: _dragOffset,
          )
          ..addListener(() {
            if (mounted) {
              setState(() => _dragOffset = _springCtrl!.value);
            }
          })
          ..addStatusListener((s) {
            if ((s == AnimationStatus.completed ||
                    s == AnimationStatus.dismissed) &&
                mounted &&
                _dragSlotRef == ref) {
              _resetDragState();
            }
          })
          ..animateTo(0);

    _nbrCtrl?.dispose();
    _nbrCtrl =
        SingleMotionController(
            motion: _spatialMotion(back, stiffness: back.stiffness * speedMul),
            vsync: this,
            initialValue: _neighbourFraction,
          )
          ..addListener(() {
            if (mounted) {
              setState(() => _neighbourFraction = _nbrCtrl!.value);
            }
          })
          ..addStatusListener(_onMotionSettled)
          ..animateTo(0);
  }
}
