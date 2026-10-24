part of '../m3e_buttons.dart';

extension _M3EButtonSelectionShape on _M3EButtonState {
  ({
    BorderRadius defaultShape,
    BorderRadius pressedShape,
    BorderRadius hoveredShape,
    bool freezeLeft,
    bool freezeRight,
  })
  _resolveSelectionShapes(M3EButtonMeasurements measurements) {
    final explicit = _decoration?.borderRadius;
    final fullyRound = BorderRadius.circular(measurements.height / 2);
    final square = BorderRadius.circular(
      _buttonTheme.squareRadius(widget.size),
    );
    final double? unselectedRadius = _decoration?.unselectedRadius;
    final double? selectedRadius = _decoration?.selectedRadius;
    final unselected = unselectedRadius != null
        ? BorderRadius.circular(unselectedRadius)
        : explicit != null
        ? BorderRadius.circular(explicit)
        : widget.shape == M3EButtonShape.round
        ? fullyRound
        : square;
    final selected = selectedRadius != null
        ? BorderRadius.circular(selectedRadius)
        : explicit != null
        ? BorderRadius.circular(explicit)
        : widget.shape == M3EButtonShape.round
        ? square
        : fullyRound;
    final pressed = BorderRadius.circular(
      _decoration?.pressedRadius ??
          explicit ??
          _buttonTheme.pressedRadius(widget.size),
    );
    // Spec (buttons + button groups): hover keeps resting shape; press morphs.
    final restingShape = _isSelected ? selected : unselected;
    final hovered = _decoration?.hoveredRadius != null
        ? BorderRadius.circular(_decoration!.hoveredRadius!)
        : restingShape;

    if (!widget.isGroupConnected) {
      return (
        defaultShape: restingShape,
        pressedShape: pressed,
        hoveredShape: hovered,
        freezeLeft: false,
        freezeRight: false,
      );
    }

    return _connectedSelectionShapes(
      measurements: measurements,
      explicit: explicit,
    );
  }

  ({
    BorderRadius defaultShape,
    BorderRadius pressedShape,
    BorderRadius hoveredShape,
    bool freezeLeft,
    bool freezeRight,
  })
  _connectedSelectionShapes({
    required M3EButtonMeasurements measurements,
    required double? explicit,
  }) {
    final groupTheme = M3ETheme.of(context).buttonGroupTheme;
    final outerRadius =
        explicit ??
        (widget.shape == M3EButtonShape.round
            ? measurements.height / 2
            : groupTheme.connectedOuterSquareRadiusFor(widget.size));
    final innerRadius =
        explicit ??
        _decoration?.connectedInnerRadius ??
        groupTheme.connectedInnerRadiusFor(widget.size);
    final pressedInnerRadius =
        _decoration?.pressedRadius ??
        explicit ??
        groupTheme.connectedPressedInnerRadiusFor(widget.size);
    final selectedInnerRadius =
        _decoration?.selectedRadius ??
        explicit ??
        groupTheme.connectedSelectedInnerRadiusFor(measurements.height);
    final resting = BorderRadiusDirectional.horizontal(
      start: Radius.circular(widget.isFirstInGroup ? outerRadius : innerRadius),
      end: Radius.circular(widget.isLastInGroup ? outerRadius : innerRadius),
    ).resolve(Directionality.of(context));
    final connectedSelected = BorderRadius.circular(selectedInnerRadius);
    final connectedPressed = BorderRadiusDirectional.horizontal(
      start: Radius.circular(
        widget.isFirstInGroup ? outerRadius : pressedInnerRadius,
      ),
      end: Radius.circular(
        widget.isLastInGroup ? outerRadius : pressedInnerRadius,
      ),
    ).resolve(Directionality.of(context));
    final connectedResting = _isSelected ? connectedSelected : resting;
    final connectedHovered = _decoration?.hoveredRadius != null
        ? BorderRadiusDirectional.horizontal(
            start: Radius.circular(
              widget.isFirstInGroup ? outerRadius : _decoration!.hoveredRadius!,
            ),
            end: Radius.circular(
              widget.isLastInGroup ? outerRadius : _decoration!.hoveredRadius!,
            ),
          ).resolve(Directionality.of(context))
        : connectedResting;
    return (
      defaultShape: connectedResting,
      pressedShape: connectedPressed,
      hoveredShape: connectedHovered,
      freezeLeft: false,
      freezeRight: false,
    );
  }
}
