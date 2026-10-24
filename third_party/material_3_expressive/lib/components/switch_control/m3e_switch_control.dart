import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

import '../../foundations/foundations.dart';
import 'styles/m3e_switch_theme.dart';

export 'styles/m3e_switch_theme.dart';

/// A Material 3 Expressive switch.
///
/// Toggles a single setting on or off. The handle slides and grows with the
/// existing thumb springs. A 40dp state layer and focus ring follow the handle
/// inside a 48dp target.
class M3ESwitch extends StatefulWidget {
  /// M3ESwitch.
  const M3ESwitch({
    required this.value,
    required this.onChanged,
    this.selectedIcon,
    this.unselectedIcon,
    this.stateLayerSize,
    this.focusNode,
    this.autofocus = false,
    this.semanticLabel,
    super.key,
  });

  /// value.

  final bool value;

  /// onChanged.
  final ValueChanged<bool>? onChanged;

  /// selectedIcon.
  final Widget? selectedIcon;

  /// unselectedIcon.
  final Widget? unselectedIcon;

  /// Diameter of the handle-centered state layer.
  ///
  /// Defaults to [M3ESwitchTheme.stateLayerSize].
  final double? stateLayerSize;

  /// focusNode.
  final FocusNode? focusNode;

  /// autofocus.
  final bool autofocus;

  /// semanticLabel.
  final String? semanticLabel;

  @override
  State<M3ESwitch> createState() => _M3ESwitchState();
}

class _M3ESwitchState extends State<M3ESwitch> with TickerProviderStateMixin {
  late final SingleMotionController _positionCtrl;
  late final SingleMotionController _sizeCtrl;
  FocusNode? _ownedNode;
  bool _dragging = false;
  double _dragValue = 0;

  bool get _enabled => widget.onChanged != null;

  FocusNode get _focusNode => widget.focusNode ?? _ownedNode!;

  SpringMotion _springMotion(M3ESpring spring) =>
      const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: spring.stiffness,
        damping: spring.damping,
      );

  SpringMotion _positionMotion(M3ESwitchTheme switchTheme) =>
      _springMotion(switchTheme.positionSpring);

  SpringMotion _sizeMotion(M3ESwitchTheme switchTheme) =>
      _springMotion(switchTheme.sizeSpring);

  double get _position => _dragging ? _dragValue : _positionCtrl.value;

  @override
  void initState() {
    super.initState();
    if (widget.focusNode == null) {
      _ownedNode = FocusNode();
    }
    // Theme may be unavailable; match [M3ESwitchTheme] defaults.
    const defaults = M3ESwitchTheme.defaults;
    _positionCtrl = SingleMotionController(
      motion: _positionMotion(defaults),
      vsync: this,
      initialValue: widget.value ? 1.0 : 0.0,
    );
    _sizeCtrl = SingleMotionController(
      motion: _sizeMotion(defaults),
      vsync: this,
      initialValue: widget.value ? 1.0 : 0.0,
    );
  }

  @override
  void didUpdateWidget(covariant M3ESwitch oldWidget) {
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
      final switchTheme = M3ETheme.of(context).switchTheme;
      _positionCtrl.motion = _positionMotion(switchTheme);
      _sizeCtrl.motion = _sizeMotion(switchTheme);
      _positionCtrl.animateTo(widget.value ? 1.0 : 0.0);
      _sizeCtrl.animateTo(widget.value ? 1.0 : 0.0);
    }
  }

  @override
  void dispose() {
    _positionCtrl.dispose();
    _sizeCtrl.dispose();
    _ownedNode?.dispose();
    super.dispose();
  }

  void _toggle() {
    if (!_enabled) {
      return;
    }
    widget.onChanged!(!widget.value);
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

  void _onDragStart() {
    if (!_enabled) {
      return;
    }
    _positionCtrl.value = _positionCtrl.value.clamp(0.0, 1.0);
    setState(() {
      _dragging = true;
      _dragValue = _positionCtrl.value;
    });
  }

  void _onDragUpdate(DragUpdateDetails details, M3ESwitchTheme switchTheme) {
    if (!_dragging) {
      return;
    }
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final double delta = rtl ? -details.delta.dx : details.delta.dx;
    final double travel = _dragTravel(switchTheme);
    setState(() {
      _dragValue = (_dragValue + delta / travel).clamp(0.0, 1.0);
    });
  }

  void _onDragEnd() {
    if (!_dragging) {
      return;
    }
    final double dropped = _dragValue;
    final bool next = dropped >= 0.5;
    setState(() => _dragging = false);
    _positionCtrl.value = dropped;
    if (!_enabled) {
      return;
    }
    if (next != widget.value) {
      widget.onChanged!(next);
      return;
    }
    final switchTheme = M3ETheme.of(context).switchTheme;
    _positionCtrl.motion = _positionMotion(switchTheme);
    _positionCtrl.animateTo(widget.value ? 1.0 : 0.0);
  }

  double _dragTravel(M3ESwitchTheme switchTheme) {
    final double innerWidth =
        switchTheme.trackWidth - 2 * switchTheme.trackPadding;
    final double innerHeight =
        switchTheme.trackHeight - 2 * switchTheme.trackPadding;
    final double size = switchTheme.thumbSizePressed;
    final double bleed = size > innerHeight ? (size - innerHeight) / 2 : 0;
    return math.max(innerWidth - size + 2 * bleed, 1);
  }

  bool _hasIconFor(bool value) =>
      value ? widget.selectedIcon != null : widget.unselectedIcon != null;

  double _restingSize(M3ESwitchTheme switchTheme, bool value) {
    return switchTheme.thumbSize(
      pressed: false,
      grown: value,
      hasIcon: _hasIconFor(value),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final switchTheme = theme.switchTheme;
    final scheme = theme.colorScheme;
    final double slotWidth = math.max(
      switchTheme.targetSize,
      switchTheme.trackWidth,
    );
    final double slotHeight = math.max(
      switchTheme.targetSize,
      switchTheme.trackHeight,
    );

    return TapRegion(
      onTapOutside: (_) => _clearFocusFromPointer(),
      child: Listener(
        behavior: HitTestBehavior.translucent,
        onPointerUp: (_) => _scheduleClearFocusFromPointer(),
        child: M3EComponentTheme(
          builder: (BuildContext context) {
            return M3ETappable(
              onTap: _enabled ? _toggle : null,
              enabled: _enabled,
              focusNode: _focusNode,
              autofocus: widget.autofocus,
              semanticLabel: widget.semanticLabel,
              semanticButton: false,
              semanticToggled: widget.value,
              builder: (BuildContext context, M3EInteractionState state) {
                return _switchControl(
                  switchTheme,
                  scheme,
                  state,
                  slotWidth,
                  slotHeight,
                );
              },
            );
          },
        ),
      ),
    );
  }

  Widget _switchControl(
    M3ESwitchTheme switchTheme,
    M3EColorScheme scheme,
    M3EInteractionState state,
    double slotWidth,
    double slotHeight,
  ) {
    final trackRadius = M3EShapes.resolve(switchTheme.trackHeight / 2);
    final Widget track = AnimatedContainer(
      duration: M3EMotion.short3,
      width: switchTheme.trackWidth,
      height: switchTheme.trackHeight,
      padding: EdgeInsets.all(switchTheme.trackPadding),
      decoration: BoxDecoration(
        color: switchTheme.trackColor(
          scheme,
          enabled: _enabled,
          value: widget.value,
        ),
        borderRadius: trackRadius,
        border: _trackBorder(switchTheme, scheme),
      ),
      child: AnimatedBuilder(
        animation: Listenable.merge(<Listenable>[_positionCtrl, _sizeCtrl]),
        builder: (BuildContext context, Widget? child) {
          return _buildThumb(switchTheme, scheme, state);
        },
      ),
    );

    return SizedBox(
      width: slotWidth,
      height: slotHeight,
      child: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onHorizontalDragStart: _enabled ? _startDrag : null,
        onHorizontalDragUpdate: _enabled ? _updateDrag : null,
        onHorizontalDragEnd: _enabled ? _endDrag : null,
        onHorizontalDragCancel: _enabled ? _onDragEnd : null,
        child: Center(child: track),
      ),
    );
  }

  Border? _trackBorder(M3ESwitchTheme switchTheme, M3EColorScheme scheme) {
    if (widget.value) {
      return null;
    }
    return Border.all(
      color: switchTheme.outlineColor(scheme, enabled: _enabled),
      width: switchTheme.borderWidth,
    );
  }

  void _startDrag(DragStartDetails _) {
    _onDragStart();
  }

  void _updateDrag(DragUpdateDetails details) {
    _onDragUpdate(details, M3ETheme.of(context).switchTheme);
  }

  void _endDrag(DragEndDetails _) {
    _onDragEnd();
  }

  Widget _buildThumb(
    M3ESwitchTheme switchTheme,
    M3EColorScheme scheme,
    M3EInteractionState state,
  ) {
    final bool pressed = state.pressed || _dragging;
    final double from = _restingSize(switchTheme, false);
    final double to = _restingSize(switchTheme, true);
    final double size = pressed
        ? switchTheme.thumbSizePressed
        : from + _sizeCtrl.value * (to - from);
    final double layer = widget.stateLayerSize ?? switchTheme.stateLayerSize;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final double position = rtl ? 1 - _position : _position;

    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final maxW = constraints.maxWidth;
        final maxH = constraints.maxHeight;
        final bleed = size > maxH ? (size - maxH) / 2 : 0.0;
        final left = -bleed + (maxW - size + 2 * bleed) * position;
        final top = (maxH - size) / 2;
        final double centerX = left + size / 2;
        final double centerY = top + size / 2;
        return Stack(
          clipBehavior: Clip.none,
          children: <Widget>[
            Positioned(
              left: centerX - layer / 2,
              top: centerY - layer / 2,
              width: layer,
              height: layer,
              child: M3EFocusRing(
                focused: state.focused,
                radius: BorderRadius.circular(layer / 2),
                width: switchTheme.focusIndicatorThickness,
                gap: switchTheme.focusIndicatorOffset,
                color: switchTheme.resolveFocusIndicatorColor(scheme),
                child: _buildHandleLayer(
                  switchTheme,
                  scheme,
                  state,
                  size,
                  layer,
                ),
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _buildHandleLayer(
    M3ESwitchTheme switchTheme,
    M3EColorScheme scheme,
    M3EInteractionState state,
    double size,
    double layer,
  ) {
    final Color overlay = switchTheme.stateLayerColor(
      scheme,
      value: widget.value,
    );
    final Widget visual = SizedBox(
      width: layer,
      height: layer,
      child: Stack(
        alignment: Alignment.center,
        children: <Widget>[
          Container(
            width: layer,
            height: layer,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: overlay.withValues(
                alpha: _enabled ? switchTheme.stateLayerOpacity(state) : 0,
              ),
            ),
          ),
          DecoratedBox(
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: switchTheme.thumbColor(
                scheme,
                enabled: _enabled,
                value: widget.value,
                hovered: state.hovered,
                focused: state.focused,
                pressed: state.pressed || _dragging,
              ),
            ),
            child: SizedBox(
              width: size,
              height: size,
              child: _buildThumbIcon(switchTheme, scheme),
            ),
          ),
        ],
      ),
    );
    if (!_enabled) {
      return visual;
    }
    final Color splash = switchTheme.stateLayerColor(
      scheme,
      value: widget.value,
    );
    return Material(
      type: MaterialType.transparency,
      color: Colors.transparent,
      shape: const CircleBorder(),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: _toggle,
        canRequestFocus: false,
        excludeFromSemantics: true,
        customBorder: const CircleBorder(),
        splashFactory: InkSparkle.splashFactory,
        splashColor: splash.withValues(
          alpha: switchTheme.pressedStateLayerOpacity,
        ),
        highlightColor: Colors.transparent,
        hoverColor: Colors.transparent,
        focusColor: Colors.transparent,
        overlayColor: const WidgetStatePropertyAll<Color>(Colors.transparent),
        child: visual,
      ),
    );
  }

  Widget _buildThumbIcon(M3ESwitchTheme switchTheme, M3EColorScheme scheme) {
    final Widget? icon = widget.value
        ? widget.selectedIcon
        : widget.unselectedIcon;
    if (icon == null) {
      return const SizedBox.shrink();
    }
    return Center(
      child: IconTheme.merge(
        data: IconThemeData(
          color: switchTheme.iconColor(
            scheme,
            value: widget.value,
            enabled: _enabled,
          ),
          size: switchTheme.iconSize,
        ),
        child: icon,
      ),
    );
  }
}
