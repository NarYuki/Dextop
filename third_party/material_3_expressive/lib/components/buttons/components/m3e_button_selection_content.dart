part of '../m3e_buttons.dart';

extension _M3EButtonSelectionContent on _M3EButtonState {
  bool get _usesSelection =>
      widget.isSelected != null && widget.style != M3EButtonStyle.text;

  bool get _isSelected => _usesSelection && widget.isSelected!;

  Widget? get _effectiveSelectionIcon =>
      _isSelected ? (widget.selectedIcon ?? widget.icon) : widget.icon;

  Widget? get _effectiveSelectionLabel =>
      _isSelected ? (widget.selectedLabel ?? widget.label) : widget.label;

  bool get _hasSelectionLabel => _isSelected
      ? widget.selectedLabel != null || widget.label != null
      : widget.label != null;

  bool get _animateIconToSelectedLabel =>
      widget.icon != null &&
      widget.selectedLabel != null &&
      widget.label == null &&
      widget.selectedIcon == null;

  bool get _animateLabelToSelectedIcon =>
      widget.selectedIcon != null &&
      widget.label != null &&
      widget.icon == null &&
      widget.selectedLabel == null;

  bool get _hasDistinctLabelStates =>
      _animateIconToSelectedLabel || _animateLabelToSelectedIcon;

  Widget _buildSelectionContent(M3EButtonMeasurements measurements) {
    final icon = _effectiveSelectionIcon;
    final unselectedLabel = widget.label;
    final selectedLabel = widget.selectedLabel ?? widget.label;
    if (icon == null && unselectedLabel == null && selectedLabel == null) {
      return widget.child ?? const SizedBox.shrink();
    }

    final iconWidget = _buildSelectionIcon(icon, measurements);
    final row = _hasDistinctLabelStates
        ? SingleMotionBuilder(
            motion: _labelTransitionMotion(),
            value: _isSelected ? 1 : 0,
            builder: (context, progress, _) => _buildSelectionRow(
              measurements: measurements,
              progress: progress,
              iconWidget: iconWidget,
              unselectedLabel: unselectedLabel,
              selectedLabel: selectedLabel,
              animateLabels: true,
            ),
          )
        : _buildSelectionRow(
            measurements: measurements,
            progress: _isSelected ? 1 : 0,
            iconWidget: iconWidget,
            unselectedLabel: unselectedLabel,
            selectedLabel: selectedLabel,
            animateLabels: false,
          );

    return LayoutBuilder(
      builder: (context, constraints) {
        if (!constraints.hasBoundedWidth) {
          return row;
        }
        return SizedBox(
          height: measurements.height,
          child: FittedBox(
            fit: BoxFit.none,
            clipBehavior: Clip.hardEdge,
            child: row,
          ),
        );
      },
    );
  }

  Widget? _buildSelectionIcon(
    Widget? icon,
    M3EButtonMeasurements measurements,
  ) {
    if (icon == null) {
      return null;
    }
    return RepaintBoundary(
      child: IconTheme.merge(
        data: IconThemeData(size: measurements.iconSize),
        child: icon,
      ),
    );
  }

  Widget _buildSelectionRow({
    required M3EButtonMeasurements measurements,
    required double progress,
    required Widget? iconWidget,
    required Widget? unselectedLabel,
    required Widget? selectedLabel,
    required bool animateLabels,
  }) {
    final p = progress.clamp(0.0, 1.0);
    final label = animateLabels
        ? _buildAnimatedLabelSlot(
            unselectedLabel: unselectedLabel,
            selectedLabel: selectedLabel,
            progress: p,
          )
        : _effectiveSelectionLabel == null
        ? null
        : _buildSelectionLabel(_effectiveSelectionLabel!, _isSelected);
    final labelProgress =
        (unselectedLabel != null ? 1.0 : 0.0) +
        ((selectedLabel != null ? 1.0 : 0.0) -
                (unselectedLabel != null ? 1.0 : 0.0)) *
            p;

    if (iconWidget != null && label != null) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          iconWidget,
          SizedBox(width: measurements.iconGap * labelProgress),
          label,
        ],
      );
    }
    return iconWidget ?? label ?? const SizedBox.shrink();
  }

  Widget _buildSelectionLabel(Widget child, bool selected) {
    return KeyedSubtree(
      key: ValueKey('button-label-$selected-${child.hashCode}'),
      child: DefaultTextStyle.merge(maxLines: 1, softWrap: false, child: child),
    );
  }

  Widget _buildAnimatedLabelSlot({
    required Widget? unselectedLabel,
    required Widget? selectedLabel,
    required double progress,
  }) {
    final unselected = unselectedLabel == null
        ? null
        : _buildSelectionLabel(unselectedLabel, false);
    final selected = selectedLabel == null
        ? null
        : _buildSelectionLabel(selectedLabel, true);
    final hasBothLabels = unselected != null && selected != null;
    final slideSelectedAppearance =
        widget.icon != null &&
        widget.selectedLabel != null &&
        widget.label == null &&
        widget.selectedIcon == null;
    final outgoingSlide = hasBothLabels
        ? _buttonTheme.labelSlideDistance * progress
        : 0.0;
    final incomingSlide = hasBothLabels || slideSelectedAppearance
        ? _buttonTheme.labelSlideDistance * (1.0 - progress)
        : 0.0;

    return ClipRect(
      child: Stack(
        alignment: Alignment.centerLeft,
        children: [
          if (unselected != null)
            Align(
              widthFactor: 1.0 - progress,
              alignment: Alignment.centerLeft,
              child: Opacity(
                opacity: hasBothLabels
                    ? 1.0 - progress
                    : _lingerOpacity(1.0 - progress),
                child: Transform.translate(
                  offset: Offset(-outgoingSlide, 0),
                  child: unselected,
                ),
              ),
            ),
          if (selected != null)
            Align(
              widthFactor: progress,
              alignment: Alignment.centerLeft,
              child: Opacity(
                opacity: hasBothLabels ? progress : _lingerOpacity(progress),
                child: Transform.translate(
                  offset: Offset(incomingSlide, 0),
                  child: selected,
                ),
              ),
            ),
        ],
      ),
    );
  }

  SpringMotion _labelTransitionMotion() {
    final base = effectiveMotion ?? M3EButtonMotion.standard;
    return M3EButtonMotion.custom(
      base.stiffness * 0.5,
      base.damping < 1.05 ? 1.05 : base.damping,
    ).toMotion();
  }

  double _lingerOpacity(double value) {
    final progress = value.clamp(0.0, 1.0);
    return progress >= 0.45 ? 1 : progress / 0.45;
  }
}
