import 'dart:math' as math;
import 'dart:ui' show SemanticsRole;

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import '../../foundations/foundations.dart';
import 'styles/m3e_radio_theme.dart';

export 'styles/m3e_radio_theme.dart';

part 'm3e_radio_group.dart';

/// A Material 3 Expressive radio button.
///
/// Selecting one value from a set. The inner dot scales in when selected and
/// a 40dp state layer surrounds the 20dp ring, inside a 48dp target.
///
/// When [label] is set, tapping the label also selects this value. Selecting
/// an already selected radio does not clear the group.
class M3ERadio<T> extends StatefulWidget {
  /// M3ERadio.
  const M3ERadio({
    required this.value,
    required this.groupValue,
    required this.onChanged,
    this.label,
    this.error = false,
    this.focusable = true,
    this.focusNode,
    this.autofocus = false,
    this.semanticLabel,
    super.key,
  });

  /// value.

  final T value;

  /// groupValue.
  final T? groupValue;

  /// onChanged.
  final ValueChanged<T>? onChanged;

  /// Optional text or widget beside the control; included in the tap target.
  final Widget? label;

  /// error.

  final bool error;

  /// Whether this radio is a keyboard Tab stop.
  ///
  /// Set to false when embedded in a focusable parent (e.g. a list row).
  /// Inside an [M3ERadioGroup], only the selected radio (or the first or last
  /// when nothing is selected) stays in the tab order.
  final bool focusable;

  /// focusNode.
  final FocusNode? focusNode;

  /// autofocus.
  final bool autofocus;

  /// semanticLabel.
  final String? semanticLabel;

  @override
  State<M3ERadio<T>> createState() => _M3ERadioState<T>();
}

class _M3ERadioState<T> extends State<M3ERadio<T>> {
  FocusNode? _ownedNode;
  _M3ERadioGroupRegistration<T>? _registration;

  FocusNode get _focusNode => widget.focusNode ?? _ownedNode!;

  bool get _enabled => widget.onChanged != null;
  bool get _selected => widget.value == widget.groupValue;

  @override
  void initState() {
    super.initState();
    if (widget.focusNode == null) {
      _ownedNode = FocusNode();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _attachGroup();
  }

  @override
  void didUpdateWidget(M3ERadio<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.focusNode != widget.focusNode) {
      _detachGroup();
      if (widget.focusNode == null) {
        _ownedNode ??= FocusNode();
      } else {
        _ownedNode?.dispose();
        _ownedNode = null;
      }
    }
    _attachGroup();
  }

  @override
  void dispose() {
    _detachGroup();
    _ownedNode?.dispose();
    super.dispose();
  }

  void _attachGroup() {
    final _M3ERadioGroupScope<T>? scope = _M3ERadioGroupScope.maybeOf<T>(
      context,
    );
    if (scope == null) {
      _detachGroup();
      return;
    }
    _registration ??= scope.attach(_focusNode);
    _registration!.sync(
      value: widget.value,
      enabled: _enabled,
      selected: _selected,
      focusable: widget.focusable,
      onSelect: _selectFromGroup,
    );
  }

  void _detachGroup() {
    _registration?._dispose();
    _registration = null;
  }

  void _selectFromGroup() {
    _handleTap();
  }

  void _handleTap() {
    if (!_enabled || _selected) {
      return;
    }
    widget.onChanged!(widget.value);
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

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3ERadioTheme radioTheme = theme.radioTheme;
    final M3EColorScheme scheme = theme.colorScheme;
    final double hitSize = radioTheme.hitSize;
    final double slot = math.max(radioTheme.targetSize, hitSize);

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
            semanticChecked: _selected,
            semanticInMutuallyExclusiveGroup: true,
            builder: (BuildContext context, M3EInteractionState state) {
              final Widget control = SizedBox(
                width: slot,
                height: slot,
                child: Center(
                  child: M3EFocusRing(
                    focused: state.focused,
                    radius: BorderRadius.circular(hitSize / 2),
                    child: _buildControl(radioTheme, scheme, state, hitSize),
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
                  SizedBox(width: radioTheme.labelGap),
                  DefaultTextStyle.merge(
                    style: theme.typeScale.bodyLarge.copyWith(
                      color: _enabled
                          ? scheme.onSurface
                          : scheme.onSurface.withValues(
                              alpha: radioTheme.disabledOpacity,
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
    M3ERadioTheme radioTheme,
    M3EColorScheme scheme,
    M3EInteractionState state,
    double hitSize,
  ) {
    final Color overlay = radioTheme.stateLayerColor(
      scheme,
      selected: _selected,
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
              color: overlay.withValues(
                alpha: radioTheme.stateLayerOpacity(state),
              ),
            ),
          ),
          _buildRing(radioTheme, scheme, state),
        ],
      ),
    );
    if (!_enabled) {
      return visual;
    }
    final Color splash = radioTheme.stateLayerColor(
      scheme,
      selected: _selected,
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
          alpha: radioTheme.pressedStateLayerOpacity,
        ),
        highlightColor: Colors.transparent,
        hoverColor: Colors.transparent,
        focusColor: Colors.transparent,
        overlayColor: const WidgetStatePropertyAll<Color>(Colors.transparent),
        child: visual,
      ),
    );
  }

  Widget _buildRing(
    M3ERadioTheme radioTheme,
    M3EColorScheme scheme,
    M3EInteractionState state,
  ) {
    final Color color = radioTheme.color(
      scheme,
      enabled: _enabled,
      error: widget.error,
      selected: _selected,
      hovered: state.hovered,
      focused: state.focused,
      pressed: state.pressed,
    );
    return Container(
      width: radioTheme.ringSize,
      height: radioTheme.ringSize,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: color, width: radioTheme.borderWidth),
      ),
      child: Center(
        child: AnimatedScale(
          scale: _selected ? 1 : 0,
          duration: M3EMotion.short4,
          curve: M3EMotion.emphasizedDecelerate,
          child: Container(
            width: radioTheme.dotSize,
            height: radioTheme.dotSize,
            decoration: BoxDecoration(shape: BoxShape.circle, color: color),
          ),
        ),
      ),
    );
  }
}
