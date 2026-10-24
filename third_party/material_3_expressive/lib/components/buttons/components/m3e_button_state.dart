// Button state + icon layout extracted for file_length.
part of '../m3e_buttons.dart';

class _M3EButtonIconLayout extends StatelessWidget {
  const _M3EButtonIconLayout({
    required this.icon,
    required this.label,
    required this.size,
    required this.iconAlignment,
  });

  final Widget? icon;
  final Widget? label;
  final M3EButtonSize size;
  final IconAlignment iconAlignment;

  @override
  Widget build(BuildContext context) {
    final m = M3ETheme.of(context).buttonTheme.measurements(size);
    final textScale = MediaQuery.textScalerOf(context).scale(1);
    final maxLines = textScale >= 2.0 ? 2 : 1;
    final iconWidget = icon == null
        ? null
        : RepaintBoundary(
            child: IconTheme.merge(
              data: IconThemeData(size: m.iconSize),
              child: icon!,
            ),
          );
    final labelWidget = label == null
        ? null
        : Flexible(
            child: DefaultTextStyle.merge(
              maxLines: maxLines,
              softWrap: maxLines > 1,
              overflow: TextOverflow.ellipsis,
              child: label!,
            ),
          );
    final children = <Widget>[
      ?iconWidget,
      if (iconWidget != null && labelWidget != null) SizedBox(width: m.iconGap),
      ?labelWidget,
    ];

    if (iconAlignment == IconAlignment.end &&
        iconWidget != null &&
        labelWidget != null) {
      children.setAll(0, [children[2], children[1], children[0]]);
    }

    return Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: children,
    );
  }
}

class _M3EButtonState extends State<M3EButton>
    with M3EBaseButtonState<M3EButton> {
  late M3EButtonMeasurements _measurements;

  /// [M3EButton.decoration] merged over the nearest scope decoration.
  M3EButtonDecoration? _decoration;

  M3EButtonTheme get _buttonTheme => M3ETheme.of(context).buttonTheme;

  M3EColorScheme get _scheme => M3ETheme.of(context).colorScheme;

  @override
  M3EButtonSize get buttonSize => widget.size;

  @override
  WidgetStatesController? get externalStatesController =>
      widget.statesController;

  @override
  FocusNode? get externalFocusNode => widget.focusNode;

  @override
  M3EButtonMotion? get effectiveMotion {
    final decorationMotion = _decoration?.motion;
    if (decorationMotion != null) {
      return decorationMotion;
    }
    final spring = _buttonTheme.shapeSpring;
    return M3EButtonMotion(
      stiffness: spring.stiffness,
      damping: spring.damping,
    );
  }

  @override
  void initState() {
    super.initState();
    initBaseButtonState();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _resolveDecoration();
    _updateMeasurements();
    updateLabelStyle(context);
    updateSpringMotion();
  }

  /// Own decoration merged over the nearest [M3EButtonDecorationScope].
  void _resolveDecoration() {
    final M3EButtonDecoration? scoped = M3EButtonDecorationScope.maybeOf(
      context,
      widget.style,
    );
    _decoration = scoped == null
        ? widget.decoration
        : scoped.merge(widget.decoration);
  }

  void _updateMeasurements() {
    _measurements = _buttonTheme.measurements(widget.size);
  }

  @override
  void didUpdateWidget(covariant M3EButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    handleStatesControllerUpdate(
      oldWidget.statesController,
      widget.statesController,
    );
    handleFocusNodeUpdate(oldWidget.focusNode, widget.focusNode);

    if (oldWidget.size != widget.size) {
      _updateMeasurements();
    }

    final M3EButtonDecoration? previous = _decoration;
    _resolveDecoration();
    if (oldWidget.size != widget.size ||
        previous?.foregroundColor != _decoration?.foregroundColor ||
        oldWidget.style != widget.style) {
      updateLabelStyle(context);
    }

    if (_decoration?.motion != previous?.motion) {
      updateSpringMotion();
    }
  }

  @override
  void dispose() {
    disposeBaseButtonState();
    super.dispose();
  }

  @override
  void debugFillProperties(DiagnosticPropertiesBuilder properties) {
    super.debugFillProperties(properties);
    properties
      ..add(EnumProperty<M3EButtonStyle>('style', widget.style))
      ..add(DiagnosticsProperty<M3EButtonSize>('size', widget.size))
      ..add(EnumProperty<M3EButtonShape>('shape', widget.shape))
      ..add(
        FlagProperty('enabled', value: widget.enabled, ifFalse: 'disabled'),
      );
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(builder: _buildContent);
  }
}
