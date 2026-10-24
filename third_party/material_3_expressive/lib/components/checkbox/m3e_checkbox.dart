import 'dart:math' as math;

import 'package:flutter/physics.dart';
import 'package:material_ui/material_ui.dart';

import '../../foundations/foundations.dart';
import 'styles/m3e_checkbox_theme.dart';

export 'styles/m3e_checkbox_theme.dart';

/// A Material 3 Expressive checkbox.
///
/// Supports the binary and, when [tristate] is enabled, the indeterminate
/// state. A state layer surrounds the box, the container color and check mark
/// animate on change, and an [error] flavour is available.
///
/// When [label] is set, tapping the label also toggles this checkbox.
class M3ECheckbox extends StatefulWidget {
  /// M3ECheckbox.
  const M3ECheckbox({
    required this.value,
    required this.onChanged,
    this.tristate = false,
    this.error = false,
    this.label,
    this.boxSize,
    this.hitSize,
    this.targetSize,
    this.checkedChild,
    this.uncheckedChild,
    this.checkIconPadding,
    this.focusable = true,
    this.focusNode,
    this.autofocus = false,
    this.semanticLabel,
    super.key,
  }) : assert(
         tristate || value != null,
         'value may only be null when tristate is true.',
       );

  /// The current value. Null represents the indeterminate state.
  final bool? value;

  /// Called with the next value, or null to disable the checkbox.
  final ValueChanged<bool?>? onChanged;

  /// tristate.
  final bool tristate;

  /// error.
  final bool error;

  /// Optional text or widget beside the control; included in the tap target.
  final Widget? label;

  /// Checkbox box size. Defaults to [M3ECheckboxTheme.boxSize].
  final double? boxSize;

  /// Circular state-layer size. Defaults to [M3ECheckboxTheme.hitSize].
  final double? hitSize;

  /// Touch target. Defaults to [M3ECheckboxTheme.targetSize].
  ///
  /// The state layer stays centered in this slot. The slot grows when
  /// [hitSize] is larger so the state layer is not clipped.
  final double? targetSize;

  /// Custom widget for the checked state. Replaces the default filled box + check.
  final Widget? checkedChild;

  /// Custom widget for the unchecked state. Replaces the default empty box.
  final Widget? uncheckedChild;

  /// Extra optical offset for the default check icon only (checked state).
  ///
  /// Applied as a paint-time translation (not layout padding) so the glyph
  /// stays inside the box. The default is [EdgeInsets.zero].
  /// `left`/`top` shift it right/down, `right`/`bottom` shift it left/up.
  /// Does not apply to [checkedChild], indeterminate, or unchecked.
  final EdgeInsetsGeometry? checkIconPadding;

  /// Whether this checkbox is a keyboard Tab stop.
  ///
  /// Set to false when embedded in a focusable parent (e.g. a list row).
  final bool focusable;

  /// focusNode.
  final FocusNode? focusNode;

  /// autofocus.
  final bool autofocus;

  /// semanticLabel.
  final String? semanticLabel;

  @override
  State<M3ECheckbox> createState() => _M3ECheckboxState();
}

class _M3ECheckboxState extends State<M3ECheckbox>
    with SingleTickerProviderStateMixin {
  static const double _pulseScale = 0.88;

  late final AnimationController _scaleController;
  FocusNode? _ownedNode;

  bool get _enabled => widget.onChanged != null;

  FocusNode get _focusNode => widget.focusNode ?? _ownedNode!;

  @override
  void initState() {
    super.initState();
    if (widget.focusNode == null) {
      _ownedNode = FocusNode();
    }
    _scaleController = AnimationController.unbounded(vsync: this, value: 1);
  }

  @override
  void didUpdateWidget(M3ECheckbox oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.focusNode != widget.focusNode) {
      if (widget.focusNode == null) {
        _ownedNode ??= FocusNode();
      } else {
        _ownedNode?.dispose();
        _ownedNode = null;
      }
    }
    if (oldWidget.value != widget.value) {
      _pulse();
    }
  }

  @override
  void dispose() {
    _scaleController.dispose();
    _ownedNode?.dispose();
    super.dispose();
  }

  void _onTapOutside(PointerDownEvent _) {
    _clearFocusFromPointer();
  }

  void _onPointerUp(PointerUpEvent _) {
    _scheduleClearFocusFromPointer();
  }

  void _clearFocusFromPointer() {
    M3EFocusInteraction.instance.notePointerInteraction();
    if (_focusNode.hasFocus) {
      _focusNode.unfocus();
    }
  }

  void _scheduleClearFocusFromPointer() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      _clearFocusFromPointer();
    });
  }

  void _pulse() {
    final pulse = M3ETheme.of(context).checkboxTheme.pulseSpring;
    _scaleController.value = _pulseScale;
    _scaleController.animateWith(
      SpringSimulation(
        pulse.toDescription(),
        _scaleController.value,
        1,
        _scaleController.velocity,
      ),
    );
  }

  void _handleTap() {
    switch (widget.value) {
      case false:
        widget.onChanged!(true);
      case true:
        widget.onChanged!(widget.tristate ? null : false);
      case null:
        widget.onChanged!(false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3ECheckboxTheme checkboxTheme = theme.checkboxTheme;
    final M3EColorScheme scheme = theme.colorScheme;
    final double boxSize = widget.boxSize ?? checkboxTheme.boxSize;
    final double hitSize = widget.hitSize ?? checkboxTheme.hitSize;
    final double targetSize = widget.targetSize ?? checkboxTheme.targetSize;
    final double slot = math.max(targetSize, hitSize);
    final double sizeScale = boxSize / checkboxTheme.boxSize;
    final bool checked = widget.value ?? false;
    final bool active = widget.value == null || checked;

    return TapRegion(
      onTapOutside: _onTapOutside,
      child: Listener(
        behavior: HitTestBehavior.translucent,
        onPointerUp: _onPointerUp,
        child: M3EComponentTheme(
          builder: (BuildContext context) => M3ETappable(
            onTap: _enabled ? _handleTap : null,
            enabled: _enabled,
            focusable: widget.focusable,
            focusNode: _focusNode,
            autofocus: widget.autofocus,
            semanticLabel: widget.semanticLabel,
            semanticButton: false,
            semanticChecked: widget.value,
            semanticMixed: widget.value == null,
            builder: (BuildContext context, M3EInteractionState state) {
              final Widget control = SizedBox(
                width: slot,
                height: slot,
                child: Center(
                  child: M3EFocusRing(
                    focused: state.focused,
                    radius: BorderRadius.circular(hitSize / 2),
                    color: checkboxTheme.resolveFocusIndicatorColor(scheme),
                    width: checkboxTheme.focusIndicatorThickness,
                    gap: checkboxTheme.focusIndicatorOffset,
                    child: _buildControl(
                      checkboxTheme,
                      scheme,
                      state,
                      active: active,
                      hitSize: hitSize,
                      boxSize: boxSize,
                      sizeScale: sizeScale,
                    ),
                  ),
                ),
              );

              if (widget.label == null) {
                return control;
              }

              return Row(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  control,
                  SizedBox(width: checkboxTheme.labelGap),
                  DefaultTextStyle.merge(
                    style: theme.typeScale.bodyLarge.copyWith(
                      color: _enabled
                          ? scheme.onSurface
                          : scheme.onSurface.withValues(
                              alpha: checkboxTheme.disabledOpacity,
                            ),
                    ),
                    child: widget.label!,
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildControl(
    M3ECheckboxTheme checkboxTheme,
    M3EColorScheme scheme,
    M3EInteractionState state, {
    required bool active,
    required double hitSize,
    required double boxSize,
    required double sizeScale,
  }) {
    final Color base = checkboxTheme.stateLayerColor(
      scheme,
      active: active,
      error: widget.error,
      pressed: state.pressed,
    );
    final Widget visual = SizedBox(
      width: hitSize,
      height: hitSize,
      child: Stack(
        alignment: Alignment.center,
        children: <Widget>[
          Container(
            width: hitSize,
            height: hitSize,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: base.withValues(
                alpha: checkboxTheme.stateLayerOpacity(state),
              ),
            ),
          ),
          AnimatedBuilder(
            animation: _scaleController,
            builder: (BuildContext context, Widget? child) {
              return Transform.scale(
                scale: _scaleController.value,
                child: child,
              );
            },
            child: _buildBox(
              checkboxTheme,
              scheme,
              state: state,
              active: active,
              boxSize: boxSize,
              sizeScale: sizeScale,
            ),
          ),
        ],
      ),
    );
    if (!_enabled) {
      return visual;
    }
    final Color splash = checkboxTheme.stateLayerColor(
      scheme,
      active: active,
      error: widget.error,
      pressed: true,
    );
    return Material(
      type: MaterialType.transparency,
      color: Colors.transparent,
      shape: const CircleBorder(),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: _handleTap,
        canRequestFocus: false,
        excludeFromSemantics: true,
        customBorder: const CircleBorder(),
        splashFactory: InkSparkle.splashFactory,
        splashColor: splash.withValues(
          alpha: checkboxTheme.pressedStateLayerOpacity,
        ),
        highlightColor: Colors.transparent,
        hoverColor: Colors.transparent,
        focusColor: Colors.transparent,
        overlayColor: const WidgetStatePropertyAll<Color>(Colors.transparent),
        child: visual,
      ),
    );
  }

  Widget _buildBox(
    M3ECheckboxTheme checkboxTheme,
    M3EColorScheme scheme, {
    required M3EInteractionState state,
    required bool active,
    required double boxSize,
    required double sizeScale,
  }) {
    // Custom children fully replace the default box for checked/unchecked.
    if ((widget.value ?? false) && widget.checkedChild != null) {
      return SizedBox(
        width: boxSize,
        height: boxSize,
        child: widget.checkedChild,
      );
    }
    if (widget.value == false && widget.uncheckedChild != null) {
      return SizedBox(
        width: boxSize,
        height: boxSize,
        child: widget.uncheckedChild,
      );
    }

    final Color fill = checkboxTheme.fillColor(
      scheme,
      enabled: _enabled,
      active: active,
      error: widget.error,
    );
    final Color border = checkboxTheme.borderColor(
      scheme,
      enabled: _enabled,
      active: active,
      error: widget.error,
      hovered: state.hovered,
      focused: state.focused,
      pressed: state.pressed,
    );
    final double outline =
        checkboxTheme.outlineWidth(active: active) * sizeScale.clamp(0.5, 2);
    return AnimatedContainer(
      duration: M3EMotion.short3,
      curve: M3EMotion.standard,
      width: boxSize,
      height: boxSize,
      decoration: BoxDecoration(
        color: fill,
        borderRadius: checkboxTheme.borderRadius,
        border: Border.all(color: border, width: outline),
      ),
      // Tight child: mark handles its own centering so checkIconPadding insets.
      child: _buildMark(checkboxTheme, scheme, sizeScale),
    );
  }

  Widget _buildMark(
    M3ECheckboxTheme checkboxTheme,
    M3EColorScheme scheme,
    double sizeScale,
  ) {
    final Color color = checkboxTheme.markColor(
      scheme,
      error: widget.error,
      enabled: _enabled,
    );
    if (widget.value == null && widget.tristate) {
      return Center(
        child: Container(
          width: checkboxTheme.indeterminateWidth * sizeScale,
          height: checkboxTheme.indeterminateHeight * sizeScale,
          color: color,
        ),
      );
    }
    if (widget.value ?? false) {
      final EdgeInsets insets =
          (widget.checkIconPadding ?? checkboxTheme.checkIconPadding).resolve(
            Directionality.of(context),
          );
      final EdgeInsets scaled = sizeScale == 1 ? insets : insets * sizeScale;
      // Paint-time nudge only — layout Padding overflowed the 18dp box.
      return Center(
        child: Transform.translate(
          offset: Offset(
            scaled.left - scaled.right,
            scaled.top - scaled.bottom,
          ),
          child: Icon(
            M3EIcons.check,
            size: checkboxTheme.markSize * sizeScale,
            color: color,
          ),
        ),
      );
    }
    return const SizedBox.shrink();
  }
}
