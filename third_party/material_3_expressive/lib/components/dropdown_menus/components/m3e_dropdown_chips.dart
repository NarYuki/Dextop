import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../../tooltips/m3e_tooltips.dart';
import '../models/m3e_dropdown_item.dart';
import '../styles/m3e_dropdown_chip_style.dart';
import '../utils/m3e_dropdown_spring_motion.dart';

/// Internal widget for a bouncy spring chip.
class M3ESpringChip<T> extends StatefulWidget {
  /// item.
  final M3EDropdownItem<T> item;

  /// cd.
  final M3EDropdownChipStyle cd;

  /// chipColor.
  final Color chipColor;

  /// labelStyle.
  final TextStyle? labelStyle;

  /// scheme.
  final M3EColorScheme scheme;

  /// enabled.
  final bool enabled;

  /// onRemove.
  final VoidCallback onRemove;

  /// How much to slide left when a previous chip is removed.
  final double slideOffset;

  /// When non-null, replaces the default chip body with this widget.
  final Widget? customChild;

  /// M3ESpringChip.

  const M3ESpringChip({
    required super.key,
    required this.item,
    required this.cd,
    required this.chipColor,
    required this.labelStyle,
    required this.scheme,
    required this.enabled,
    required this.onRemove,
    this.slideOffset = 0,
    this.customChild,
  });

  @override
  State<M3ESpringChip<T>> createState() => M3ESpringChipState<T>();
}

/// M3ESpringChipState.

class M3ESpringChipState<T> extends State<M3ESpringChip<T>>
    with TickerProviderStateMixin {
  late SingleMotionController _scaleCtrl;
  late SingleMotionController _slideCtrl;
  late SingleMotionController _squishCtrl;
  @override
  void initState() {
    super.initState();
    _squishCtrl = SingleMotionController(
      motion: widget.cd.openMotion.toMotion(), // Bouncy spring
      vsync: this,
    );
    _squishCtrl.value = 1.0;
    _scaleCtrl = SingleMotionController(
      motion: widget.cd.openMotion.toMotion(),
      vsync: this,
    );
    _slideCtrl = SingleMotionController(
      motion: widget.cd.openMotion.toMotion(),
      vsync: this,
    );
    _scaleCtrl.animateTo(1);
  }

  @override
  void didUpdateWidget(covariant M3ESpringChip<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.slideOffset != widget.slideOffset) {
      _slideCtrl.animateTo(widget.slideOffset);
    }
  }

  /// triggerSquish.

  void triggerSquish(double intensity) {
    // We animate from 1.0 -> intensity -> 1.0 (handled by the spring)
    _squishCtrl.animateTo(1, from: intensity);
  }

  @override
  void dispose() {
    _scaleCtrl.dispose();
    _slideCtrl.dispose();
    _squishCtrl.dispose();
    super.dispose();
  }

  /// animateOut.

  void animateOut(VoidCallback onDone) {
    _scaleCtrl.motion = widget.cd.closeMotion.toMotion();
    _scaleCtrl.animateTo(0);
    void listener() {
      if (_scaleCtrl.value <= 0.01) {
        _scaleCtrl.removeListener(listener);
        onDone();
      }
    }

    _scaleCtrl.addListener(listener);
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge([_scaleCtrl, _slideCtrl, _squishCtrl]),
      builder: (context, child) {
        final scale = _scaleCtrl.value.clamp(0.0, 1.05);
        final slide = _slideCtrl.value;
        final squish = _squishCtrl.value;

        // combine the entrance/exit 'scale' with the 'squish'
        // result: horizontal = entrance_scale * squish_factor
        //         vertical   = entrance_scale
        final double finalXScale = scale * squish;
        final finalYScale = scale;

        return Transform.translate(
          offset: Offset(slide, 0),
          child: Opacity(
            opacity: scale.clamp(0.0, 1.0),
            child: Transform(
              alignment: Alignment.centerLeft,
              // diagonal3Values is the modern, clean way to set x, y, z scale
              transform: Matrix4.diagonal3Values(finalXScale, finalYScale, 1),
              child: child,
            ),
          ),
        );
      },
      child: widget.customChild ?? _buildChipBody(),
    );
  }

  Widget _buildChipBody() {
    final cd = widget.cd;
    final theme = M3ETheme.of(context);
    final disabledOpacity = theme.dropdownMenuTheme.disabledOpacity;
    final disabledFg = M3EColorUtils.withOpacity(
      widget.scheme.onSurface,
      disabledOpacity,
    );
    final disabledBg = M3EColorUtils.withOpacity(widget.scheme.onSurface, 0.12);
    final labelStyle =
        widget.labelStyle ??
        theme.dropdownMenuTheme.chipLabelStyle(theme.typeScale, widget.scheme);
    final BorderRadius chipRadius = cd.borderRadius;
    final Widget body = Container(
      decoration: BoxDecoration(
        borderRadius: chipRadius,
        color: widget.enabled ? widget.chipColor : disabledBg,
        border: cd.border != null ? Border.fromBorderSide(cd.border!) : null,
      ),
      padding: cd.padding,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            widget.item.label,
            style: labelStyle.copyWith(
              color: widget.enabled ? labelStyle.color : disabledFg,
            ),
          ),
          if (widget.enabled) ...[
            const SizedBox(width: 4),
            widget.cd.deleteIcon ??
                Icon(
                  Icons.close,
                  size: labelStyle.fontSize ?? 12,
                  color: widget.scheme.onSecondaryContainer,
                ),
          ],
        ],
      ),
    );

    if (!widget.enabled) {
      return MouseRegion(cursor: SystemMouseCursors.basic, child: body);
    }

    // Whole chip is one Tab stop; Activate / tap removes the selection.
    return M3ETappable(
      semanticLabel: 'Remove ${widget.item.label}',
      mouseCursor: cd.mouseCursor ?? SystemMouseCursors.click,
      onTap: () {
        M3EFocusInteraction.instance.notePointerInteraction();
        widget.onRemove();
      },
      builder: (BuildContext context, M3EInteractionState state) {
        return M3EFocusRing(
          focused: state.focused,
          radius: chipRadius,
          child: M3ETooltip(
            message: 'Remove ${widget.item.label}',
            child: body,
          ),
        );
      },
    );
  }
}

/// Layout delegate for the chips in non-wrap mode.
class M3EChipFlowDelegate extends FlowDelegate {
  /// slideAnimations.
  final List<Animation<double>> slideAnimations;

  /// spacing.
  final double spacing;

  /// M3EChipFlowDelegate.

  M3EChipFlowDelegate({required this.slideAnimations, required this.spacing})
    : super(repaint: Listenable.merge(slideAnimations));

  @override
  void paintChildren(FlowPaintingContext context) {
    double x = 0;
    for (var i = 0; i < context.childCount; i++) {
      final childSize = context.getChildSize(i)!;
      final slideOffset = slideAnimations[i].value;
      context.paintChild(
        i,
        transform: Matrix4.translationValues(x + slideOffset, 0, 0),
      );
      x += childSize.width + spacing;
    }
  }

  @override
  Size getSize(BoxConstraints constraints) =>
      Size(constraints.maxWidth, constraints.maxHeight);

  @override
  BoxConstraints getConstraintsForChild(int i, BoxConstraints constraints) =>
      constraints.loosen();

  @override
  bool shouldRepaint(M3EChipFlowDelegate old) =>
      spacing != old.spacing || slideAnimations != old.slideAnimations;
}

/// Internal indicator shown when more chips exist than the max display count.
class M3EMoreChipsIndicator extends StatefulWidget {
  /// count.
  final int count;

  /// cd.
  final M3EDropdownChipStyle cd;

  /// chipColor.
  final Color chipColor;

  /// labelStyle.
  final TextStyle? labelStyle;

  /// M3EMoreChipsIndicator.

  const M3EMoreChipsIndicator({
    super.key,
    required this.count,
    required this.cd,
    required this.chipColor,
    required this.labelStyle,
  });

  @override
  State<M3EMoreChipsIndicator> createState() => M3EMoreChipsIndicatorState();
}

/// M3EMoreChipsIndicatorState.

class M3EMoreChipsIndicatorState extends State<M3EMoreChipsIndicator>
    with TickerProviderStateMixin {
  late SingleMotionController _scaleCtrl;
  late SingleMotionController _popCtrl;
  late SingleMotionController _squishCtrl;

  @override
  void initState() {
    super.initState();
    _scaleCtrl = SingleMotionController(
      motion: widget.cd.openMotion.toMotion(),
      vsync: this,
    );
    _scaleCtrl.animateTo(1);

    _popCtrl = SingleMotionController(
      motion: M3EMotion.effectsFast.toMotion(),
      vsync: this,
    )..value = 1.0;

    _squishCtrl = SingleMotionController(
      motion: M3EMotion.effectsDefault.toMotion(),
      vsync: this,
    )..value = 1.0;
  }

  /// triggerSquish.

  void triggerSquish(double intensity) {
    _squishCtrl.animateTo(1, from: intensity);
  }

  /// animateOut.

  void animateOut(VoidCallback onDone) {
    _scaleCtrl.motion = widget.cd.closeMotion.toMotion();
    _scaleCtrl.animateTo(0);
    void listener() {
      if (_scaleCtrl.value <= 0.01) {
        _scaleCtrl.removeListener(listener);
        onDone();
      }
    }

    _scaleCtrl.addListener(listener);
  }

  /// animateIn.

  void animateIn() {
    _scaleCtrl.motion = widget.cd.openMotion.toMotion();
    _scaleCtrl.animateTo(1);
  }

  @override
  void didUpdateWidget(covariant M3EMoreChipsIndicator oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.count != widget.count) {
      // Pop effect when the number increments/decrements
      _popCtrl.animateTo(1, from: 1.25);
    }
  }

  @override
  void dispose() {
    _scaleCtrl.dispose();
    _popCtrl.dispose();
    _squishCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge([_scaleCtrl, _popCtrl, _squishCtrl]),
      builder: (context, child) {
        final scale = _scaleCtrl.value.clamp(0.0, 1.05);
        final pop = _popCtrl.value;
        final squish = _squishCtrl.value;
        return Opacity(
          opacity: scale.clamp(0.0, 1.0),
          child: Transform(
            alignment: Alignment.centerLeft,
            transform: Matrix4.diagonal3Values(
              scale * pop * squish,
              scale * pop,
              1,
            ),
            child: child,
          ),
        );
      },
      child: Builder(
        builder: (context) {
          final theme = M3ETheme.of(context);
          return Container(
            decoration: BoxDecoration(
              borderRadius: widget.cd.borderRadius,
              color: widget.chipColor,
              border: widget.cd.border != null
                  ? Border.fromBorderSide(widget.cd.border!)
                  : null,
            ),
            padding: widget.cd.padding,
            child: Text(
              '+${widget.count}',
              style:
                  widget.labelStyle ??
                  theme.dropdownMenuTheme.chipLabelStyle(
                    theme.typeScale,
                    theme.colorScheme,
                  ),
            ),
          );
        },
      ),
    );
  }
}
