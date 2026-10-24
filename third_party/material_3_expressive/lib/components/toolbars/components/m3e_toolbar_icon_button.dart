import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ETheme, M3EToolbar;
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../../icon_buttons/m3e_icon_buttons.dart';
import '../enums/m3e_toolbar_enums.dart';
import '../models/m3e_toolbar_item.dart';
import '../styles/m3e_toolbar_theme.dart';

/// Inline icon action for [M3EToolbar] — thin adapter over [M3EIconButton].
///
/// When [M3EToolbarAction.label] is set, the label lives **inside** the button
/// pill. Width springs between the icon-button visual size (inactive, icon
/// only) and the natural icon+label width (active). Layout width follows the
/// spring so the parent toolbar pill grows/shrinks in sync — unless the parent
/// reserves a fixed selection width (`pillActiveSpring: false`).
class M3EToolbarIconButton extends StatefulWidget {
  /// M3EToolbarIconButton.
  const M3EToolbarIconButton({
    required this.action,
    required this.size,
    this.onPressed,
    this.variant,
    this.shape = M3EIconButtonShapeVariant.round,
    this.colorStyle = M3EToolbarColorStyle.standard,
    this.pillActiveSpring = true,
    super.key,
  });

  /// Gap between the icon and label inside an expanded action.
  static const double labelGap = 6;

  /// action.
  final M3EToolbarAction action;

  /// size.
  final M3EIconButtonSize size;

  /// Overrides [M3EToolbarAction.onPressed] when set (e.g. expand trigger).
  final VoidCallback? onPressed;

  /// Defaults to filled for an expand trigger, tonal when selected, else
  /// standard.
  final M3EIconButtonVariant? variant;

  /// Round on a floating toolbar. Docked actions may be square.
  final M3EIconButtonShapeVariant shape;

  /// Selects the tonal colors for an active action.
  final M3EToolbarColorStyle colorStyle;

  /// Reserved for parent [M3EToolbar.pillActiveSpring]; labeled width always
  /// follows the morph spring so padding and animation stay correct.
  final bool pillActiveSpring;

  @override
  State<M3EToolbarIconButton> createState() => _M3EToolbarIconButtonState();
}

class _M3EToolbarIconButtonState extends State<M3EToolbarIconButton>
    with SingleTickerProviderStateMixin {
  late final SingleMotionController _labelCtrl;

  SpringMotion _labelMotion(M3ESpring spring) =>
      const MaterialSpringMotion.expressiveSpatialFast().copyWith(
        stiffness: spring.stiffness,
        damping: spring.damping,
      );

  bool get _hasLabel {
    final String? label = widget.action.label;
    return label != null && label.isNotEmpty;
  }

  bool get _showLabeled => _hasLabel && widget.action.active;

  @override
  void initState() {
    super.initState();
    _labelCtrl = SingleMotionController(
      motion: _labelMotion(M3EToolbarTheme.defaults.labelSpring),
      vsync: this,
      initialValue: _showLabeled ? 1 : 0,
    );
  }

  @override
  void didUpdateWidget(covariant M3EToolbarIconButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    final bool wasLabeled =
        oldWidget.action.active &&
        oldWidget.action.label != null &&
        oldWidget.action.label!.isNotEmpty;
    if (wasLabeled != _showLabeled) {
      final spring = M3ETheme.of(context).toolbarTheme.labelSpring;
      _labelCtrl
        ..motion = _labelMotion(spring)
        ..animateTo(_showLabeled ? 1 : 0);
    }
  }

  @override
  void dispose() {
    _labelCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_hasLabel) {
      return _buildIconButton(icon: Icon(widget.action.icon));
    }
    return AnimatedBuilder(
      animation: _labelCtrl,
      builder: (BuildContext context, Widget? child) {
        return _buildLabeled(context, progress: _labelCtrl.value);
      },
    );
  }

  M3EIconButtonVariant get _resolvedVariant =>
      widget.variant ??
      (widget.action.isExpandTrigger
          ? M3EIconButtonVariant.filled
          : widget.action.active
          ? M3EIconButtonVariant.tonal
          : M3EIconButtonVariant.standard);

  VoidCallback? get _resolvedOnPressed => widget.action.enabled
      ? (widget.onPressed ?? widget.action.onPressed)
      : null;

  Widget _buildIconButton({required Widget icon, Size? visualSize}) {
    Widget button = M3EIconButton(
      icon: icon,
      onPressed: _resolvedOnPressed,
      tooltip: widget.action.tooltip ?? widget.action.label,
      semanticLabel: widget.action.semanticLabel,
      size: widget.size,
      shape: widget.shape,
      variant: _resolvedVariant,
      visualSize: visualSize,
    );
    if (!widget.action.active || widget.action.isExpandTrigger) {
      return button;
    }
    final M3EThemeData theme = M3ETheme.of(context);
    final M3EToolbarColors colors = theme.toolbarTheme.colors(
      theme.colorScheme,
      widget.colorStyle,
    );
    final Color? selectedBackground = colors.selectedContainer;
    final Color? selectedForeground = colors.selectedContent;
    if (selectedBackground == null || selectedForeground == null) {
      return button;
    }
    return M3ETheme(
      data: theme.copyWith(
        colorScheme: theme.colorScheme.copyWith(
          secondaryContainer: selectedBackground,
          onSecondaryContainer: selectedForeground,
        ),
      ),
      child: button,
    );
  }

  Widget _buildLabeled(BuildContext context, {required double progress}) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3EIconButtonTheme iconTheme = theme.iconButtonTheme;
    final Size visual = iconTheme.visual(
      widget.size,
      M3EIconButtonWidth.defaultWidth,
    );
    final double iconPx = iconTheme.iconSize(widget.size);
    final double t = progress.clamp(0.0, 1.0);
    final TextStyle labelStyle = theme.typeScale.labelLarge.copyWith(
      fontWeight: FontWeight.w600,
    );
    final double labelWidth = _measureLabelWidth(
      widget.action.label!,
      labelStyle,
      MediaQuery.textScalerOf(context),
    );
    final expandedWidth =
        visual.width + M3EToolbarIconButton.labelGap + labelWidth;
    final sprungWidth = visual.width + (expandedWidth - visual.width) * t;

    return _buildIconButton(
      icon: _buildLabeledRow(
        iconPx: iconPx,
        labelStyle: labelStyle,
        progress: t,
      ),
      visualSize: Size(sprungWidth, visual.height),
    );
  }

  Widget _buildLabeledRow({
    required double iconPx,
    required TextStyle labelStyle,
    required double progress,
  }) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: <Widget>[
        Icon(widget.action.icon, size: iconPx),
        if (progress > 0.01)
          ClipRect(
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              widthFactor: progress,
              child: Opacity(
                opacity: progress,
                child: Padding(
                  padding: const EdgeInsetsDirectional.only(
                    start: M3EToolbarIconButton.labelGap,
                  ),
                  child: Builder(
                    builder: (BuildContext context) {
                      return Text(
                        widget.action.label!,
                        style: labelStyle.copyWith(
                          color: IconTheme.of(context).color,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.clip,
                        softWrap: false,
                      );
                    },
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }

  double _measureLabelWidth(
    String label,
    TextStyle style,
    TextScaler textScaler,
  ) {
    final painter = TextPainter(
      text: TextSpan(text: label, style: style),
      maxLines: 1,
      textDirection: TextDirection.ltr,
      textScaler: textScaler,
    )..layout();
    return painter.width;
  }
}
