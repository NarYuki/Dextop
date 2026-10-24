import 'dart:ui' show lerpDouble;

import 'package:flutter/widgets.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../buttons/utils/m3e_button_gradient_layer.dart';
import '../floating_action_buttons/components/m3e_fab_container_transform.dart';
import '../floating_action_buttons/enums/m3e_fab.dart';
import '../floating_action_buttons/styles/m3e_fab_decoration.dart';
import '../floating_action_buttons/styles/m3e_fab_theme.dart';
import 'controllers/m3e_extended_fab_controller.dart';
import 'enums/m3e_extended_fab.dart';

export 'controllers/m3e_extended_fab_controller.dart';
export 'enums/m3e_extended_fab.dart';

/// A Material 3 Expressive extended floating action button.
///
/// Pill-shaped FAB with a required [label] and optional [icon]. Three
/// [M3EExtendedFabSize]s (small 56 / medium 80 / large 96). Setting [extended]
/// to false (or collapsing via [controller]) morphs toward an icon-only square.
///
/// Optional [controller] drives scroll expand/collapse, appear morph, and
/// container transform. Pass [openBuilder] (or call
/// [M3EExtendedFabController.open]) to morph into another surface on tap.
class M3EExtendedFab extends StatefulWidget {
  /// Creates an extended FAB.
  const M3EExtendedFab({
    required this.label,
    this.icon,
    this.onPressed,
    this.size = M3EExtendedFabSize.small,
    this.color = M3EFabColor.primary,
    this.extended = true,
    this.decoration,
    this.elevation,
    this.hoverElevation,
    this.focusNode,
    this.autofocus = false,
    this.controller,
    this.appear = false,
    this.scaleAlignment = AlignmentDirectional.bottomEnd,
    this.openBuilder,
    this.openColor,
    this.transformScrimColor,
    super.key,
  }) : assert(elevation == null || elevation >= 0.0, 'assertion failed'),
       assert(
         hoverElevation == null || hoverElevation >= 0.0,
         'assertion failed',
       );

  /// Visible and semantic label text (required; icon-only is not supported).
  final String label;

  /// Optional leading icon. Decorative for semantics.
  final Widget? icon;

  /// Tap callback.
  final VoidCallback? onPressed;

  /// Expressive size. Defaults to [M3EExtendedFabSize.small] (56dp).
  final M3EExtendedFabSize size;

  /// Color role (container, filled, or surface).
  final M3EFabColor color;

  /// Whether the label is shown when no [controller] is attached.
  ///
  /// When a [controller] is set, [M3EExtendedFabController.isExtended] wins.
  final bool extended;

  /// Optional decoration for solid and gradient surfaces.
  final M3EFabDecoration? decoration;

  /// Resting surface elevation. Defaults to themed level 3.
  final double? elevation;

  /// Surface elevation while hovered. Defaults to themed level 4.
  final double? hoverElevation;

  /// Optional focus node.
  final FocusNode? focusNode;

  /// Whether to autofocus.
  final bool autofocus;

  /// Optional controller for expand/collapse, appear, and container transform.
  final M3EExtendedFabController? controller;

  /// When true, plays an appear morph on first layout.
  final bool appear;

  /// Anchor for appear scale (not center).
  ///
  /// Defaults to [AlignmentDirectional.bottomEnd] (typical end-float FAB).
  final AlignmentGeometry scaleAlignment;

  /// When set, tap opens a container transform to this destination.
  final WidgetBuilder? openBuilder;

  /// Destination fill color for the container transform.
  final Color? openColor;

  /// Scrim color behind the open container transform.
  final Color? transformScrimColor;

  @override
  State<M3EExtendedFab> createState() => _M3EExtendedFabState();
}

class _M3EExtendedFabState extends State<M3EExtendedFab>
    with TickerProviderStateMixin
    implements M3EExtendedFabControllerClient {
  final GlobalKey _originKey = GlobalKey();
  FocusNode? _ownedFocusNode;

  late SingleMotionController _appearCtrl;
  late SingleMotionController _extendedCtrl;

  M3EFabContainerTransformHandle<dynamic>? _transformHandle;

  bool get _interactive =>
      widget.onPressed != null || widget.openBuilder != null;

  bool get _targetExtended => widget.controller?.isExtended ?? widget.extended;

  FocusNode get _effectiveFocusNode =>
      widget.focusNode ?? (_ownedFocusNode ??= FocusNode());

  /// First word of the label for button semantics.
  String get _semanticLabel {
    final trimmed = widget.label.trim();
    if (trimmed.isEmpty) {
      return widget.label;
    }
    final space = trimmed.indexOf(' ');
    return space < 0 ? trimmed : trimmed.substring(0, space);
  }

  SpringMotion _springMotion(M3ESpring spring) =>
      const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: spring.stiffness,
        damping: spring.damping,
      );

  @override
  void initState() {
    super.initState();
    final bool startAppeared = !widget.appear;
    final bool startExtended = _targetExtended;
    _appearCtrl = SingleMotionController(
      motion: _springMotion(M3EMotion.expressiveSpatialDefault),
      vsync: this,
      initialValue: startAppeared ? 1 : 0,
    )..addListener(_onAppearTick);
    _extendedCtrl = SingleMotionController(
      motion: _springMotion(M3EMotion.expressiveSpatialDefault),
      vsync: this,
      initialValue: startExtended ? 1 : 0,
    )..addListener(_onExtendedTick);

    widget.controller?.attachClient(this);
    if (widget.appear) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          playAppearInternal();
        }
      });
    } else {
      widget.controller?.updateAppearProgress(1);
    }
    widget.controller?.updateExtendedProgress(_extendedCtrl.value);
  }

  @override
  void didUpdateWidget(covariant M3EExtendedFab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.detachClient(this);
      widget.controller?.attachClient(this);
    }
    if (widget.appear && !oldWidget.appear && _appearCtrl.value < 1) {
      playAppearInternal();
    }
    final bool nextExtended = _targetExtended;
    final bool prevExtended =
        oldWidget.controller?.isExtended ?? oldWidget.extended;
    if (nextExtended != prevExtended) {
      _animateExtended(nextExtended);
    }
  }

  @override
  void dispose() {
    widget.controller?.detachClient(this);
    _appearCtrl
      ..removeListener(_onAppearTick)
      ..dispose();
    _extendedCtrl
      ..removeListener(_onExtendedTick)
      ..dispose();
    _transformHandle?.close();
    _ownedFocusNode?.dispose();
    super.dispose();
  }

  void _onTapOutside(PointerDownEvent event) {
    M3EFocusInteraction.instance.notePointerInteraction();
    if (_effectiveFocusNode.hasFocus) {
      _effectiveFocusNode.unfocus();
    }
  }

  void _onAppearTick() {
    widget.controller?.updateAppearProgress(_appearCtrl.value);
    if (mounted) {
      setState(() {});
    }
  }

  void _onExtendedTick() {
    widget.controller?.updateExtendedProgress(_extendedCtrl.value);
    if (mounted) {
      setState(() {});
    }
  }

  void playAppearInternal() {
    _appearCtrl
      ..motion = _springMotion(M3EMotion.expressiveSpatialDefault)
      ..value = 0
      ..animateTo(1);
  }

  void _animateExtended(bool extended) {
    _extendedCtrl
      ..motion = _springMotion(M3EMotion.expressiveSpatialDefault)
      ..animateTo(extended ? 1 : 0);
  }

  @override
  bool get isTransformOpen => _transformHandle != null;

  @override
  void onExtendedRequested({required bool extended}) {
    _animateExtended(extended);
  }

  @override
  void onAppearRequested() => playAppearInternal();

  @override
  Future<T?> openTransform<T extends Object?>({WidgetBuilder? builder}) async {
    final WidgetBuilder? openBuilder = builder ?? widget.openBuilder;
    if (openBuilder == null || _transformHandle != null) {
      return null;
    }
    final BuildContext? originContext = _originKey.currentContext;
    if (originContext == null || !originContext.mounted) {
      return null;
    }
    final RenderObject? renderObject = originContext.findRenderObject();
    if (renderObject is! RenderBox || !renderObject.hasSize) {
      return null;
    }
    final theme = M3ETheme.of(context);
    final fabTheme = theme.fabTheme;
    final extendedTheme = fabTheme.extended;
    final sizeMetrics = extendedTheme.resolve(widget.size);
    final colorMetrics = fabTheme.resolve(
      size: M3EFabSize.regular,
      color: widget.color,
      scheme: theme.colorScheme,
      enabled: _interactive,
    );
    final Rect origin =
        renderObject.localToGlobal(Offset.zero) & renderObject.size;

    final handle = M3EFabContainerTransform.show<T>(
      context: context,
      origin: origin,
      originRadius: sizeMetrics.cornerRadius,
      originColor: colorMetrics.background,
      openColor: widget.openColor,
      scrimColor: widget.transformScrimColor,
      builder: openBuilder,
    );
    _transformHandle = handle;
    setState(() {});

    final T? result = await handle.future;
    _transformHandle = null;
    if (mounted) {
      setState(() {});
    }
    return result;
  }

  @override
  void closeTransform() {
    _transformHandle?.close();
  }

  void _handleTap() {
    widget.onPressed?.call();
    if (widget.openBuilder != null) {
      openTransform();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final fabTheme = theme.fabTheme;
    final extendedTheme = fabTheme.extended;
    final sizeMetrics = extendedTheme.resolve(widget.size);
    final colorMetrics = fabTheme.resolve(
      size: M3EFabSize.regular,
      color: widget.color,
      scheme: theme.colorScheme,
      enabled: _interactive,
    );

    final double appearT = _appearCtrl.value.clamp(0.0, 1.0);
    final double extendedT = _extendedCtrl.value.clamp(0.0, 1.0);
    final double combinedScale = lerpDouble(
      extendedTheme.appearStartScale,
      1,
      appearT,
    )!;
    final double appearRadius = lerpDouble(
      sizeMetrics.height / 2,
      sizeMetrics.cornerRadius,
      appearT,
    )!;
    final BorderRadius borderRadius = M3EShapes.resolve(appearRadius);
    final ShapeBorder border = RoundedRectangleBorder(
      borderRadius: borderRadius,
    );

    final hideForTransform = _transformHandle != null;
    final opacity = appearT.clamp(0.0, 1.0);

    Widget fab = M3EComponentTheme(
      builder: (context) => M3ETappable(
        onTap: _interactive ? _handleTap : null,
        enabled: _interactive,
        focusNode: _effectiveFocusNode,
        autofocus: widget.autofocus,
        semanticLabel: _semanticLabel,
        pressedScale: extendedTheme.pressedScale,
        materialInk: !m3eUsesGradientOverlay(
          widget.decoration?.overlayGradient,
        ),
        builder: (context, state) => _buildSurface(
          theme: theme,
          fabTheme: fabTheme,
          extendedTheme: extendedTheme,
          sizeMetrics: sizeMetrics,
          colorMetrics: colorMetrics,
          borderRadius: borderRadius,
          border: border,
          extendedT: extendedT,
          state: state,
        ),
      ),
    );

    fab = KeyedSubtree(key: _originKey, child: fab);

    fab = Opacity(
      opacity: hideForTransform ? 0 : opacity,
      child: Transform.scale(
        scale: hideForTransform ? 0 : combinedScale,
        alignment: widget.scaleAlignment,
        child: fab,
      ),
    );

    return TapRegion(onTapOutside: _onTapOutside, child: fab);
  }

  Widget _buildSurface({
    required M3EThemeData theme,
    required M3EFabTheme fabTheme,
    required M3EExtendedFabTheme extendedTheme,
    required M3EExtendedFabMetrics sizeMetrics,
    required M3EFabMetrics colorMetrics,
    required BorderRadius borderRadius,
    required ShapeBorder border,
    required double extendedT,
    required M3EInteractionState state,
  }) {
    final states = m3eStatesForInteraction(state, enabled: _interactive);
    final Gradient? fill =
        widget.decoration?.backgroundGradient?.resolve(states) ??
        fabTheme.gradient;
    final Color? solidBg =
        widget.decoration?.backgroundColor?.resolve(states) ??
        (fill == null ? colorMetrics.background : null);
    final Color fg =
        widget.decoration?.foregroundGradient?.resolve(states) != null
        ? m3eGradientForegroundSourceColor
        : (widget.decoration?.foregroundColor?.resolve(states) ??
              colorMetrics.foreground);
    final Gradient? outline = widget.decoration?.outlineGradient?.resolve(
      states,
    );
    final BorderSide? side = outline != null
        ? null
        : widget.decoration?.side?.resolve(states);
    final resolvedElevation = !_interactive
        ? M3EElevation.level0
        : state.hovered
        ? widget.hoverElevation ?? extendedTheme.elevation(hovered: true)
        : widget.elevation ?? extendedTheme.elevation(hovered: false);

    final hasIcon = widget.icon != null;
    final leading = hasIcon
        ? sizeMetrics.leadingSpace
        : sizeMetrics.trailingSpace;
    final trailing = sizeMetrics.trailingSpace;
    final collapsedPad = sizeMetrics.collapsedHorizontalPadding;
    final padStart = lerpDouble(collapsedPad, leading, extendedT)!;
    final padEnd = lerpDouble(collapsedPad, trailing, extendedT)!;

    // Collapsed square uses height; extended grows with content + minWidth.
    final collapsedWidth = sizeMetrics.height;
    final minExtendedWidth = extendedTheme.minWidth;

    return _composeExtendedSurface(
      theme: theme,
      extendedTheme: extendedTheme,
      sizeMetrics: sizeMetrics,
      borderRadius: borderRadius,
      border: border,
      extendedT: extendedT,
      state: state,
      states: states,
      fill: fill,
      solidBg: solidBg,
      fg: fg,
      outline: outline,
      side: side,
      resolvedElevation: resolvedElevation,
      hasIcon: hasIcon,
      padStart: padStart,
      padEnd: padEnd,
      collapsedWidth: collapsedWidth,
      minExtendedWidth: minExtendedWidth,
    );
  }

  Widget _decorateContent({
    required M3EInteractionState state,
    required Set<WidgetState> states,
    required Color fg,
    required BorderRadius borderRadius,
    required ShapeBorder border,
    required Widget child,
  }) {
    var content = child;
    final Gradient? fgGradient = widget.decoration?.foregroundGradient?.resolve(
      states,
    );
    if (fgGradient != null) {
      content = m3eGradientForegroundLayer(
        clipRadius: borderRadius,
        gradient: fgGradient,
        child: content,
      );
    }
    if (m3eUsesGradientOverlay(widget.decoration?.overlayGradient)) {
      return m3eResolveGradientOverlay(
        clipRadius: borderRadius,
        states: states,
        overlayGradient: widget.decoration?.overlayGradient,
        child: content,
      );
    }
    return M3EStateLayerOverlay(
      state: state,
      color: fg,
      shape: border,
      alignment: Alignment.center,
      child: content,
    );
  }

  Widget _buildContent({
    required M3EThemeData theme,
    required M3EExtendedFabMetrics sizeMetrics,
    required Color foreground,
    required double extendedT,
    required bool hasIcon,
  }) {
    final labelStyle = sizeMetrics.labelStyle(theme.typeScale, foreground);

    final children = <Widget>[];
    if (hasIcon) {
      children.add(
        ExcludeSemantics(
          child: IconTheme.merge(
            data: IconThemeData(color: foreground, size: sizeMetrics.iconSize),
            child: widget.icon!,
          ),
        ),
      );
    }

    children.add(
      ClipRect(
        child: Align(
          alignment: AlignmentDirectional.centerStart,
          widthFactor: extendedT,
          child: Opacity(
            opacity: extendedT.clamp(0.0, 1.0),
            child: Padding(
              padding: EdgeInsetsDirectional.only(
                start: hasIcon ? sizeMetrics.iconLabelGap * extendedT : 0,
              ),
              child: Text(
                widget.label,
                style: labelStyle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                softWrap: false,
              ),
            ),
          ),
        ),
      ),
    );

    return SizedBox(
      height: sizeMetrics.height,
      child: Row(mainAxisSize: MainAxisSize.min, children: children),
    );
  }
}

extension _M3EExtendedFabSurface on _M3EExtendedFabState {
  Widget _composeExtendedSurface({
    required M3EThemeData theme,
    required M3EExtendedFabTheme extendedTheme,
    required M3EExtendedFabMetrics sizeMetrics,
    required BorderRadius borderRadius,
    required ShapeBorder border,
    required double extendedT,
    required M3EInteractionState state,
    required Set<WidgetState> states,
    required Gradient? fill,
    required Color? solidBg,
    required Color fg,
    required Gradient? outline,
    required BorderSide? side,
    required double resolvedElevation,
    required bool hasIcon,
    required double padStart,
    required double padEnd,
    required double collapsedWidth,
    required double minExtendedWidth,
  }) {
    Widget content = _decorateContent(
      state: state,
      states: states,
      fg: fg,
      borderRadius: borderRadius,
      border: border,
      child: Padding(
        padding: EdgeInsetsDirectional.only(start: padStart, end: padEnd),
        child: _buildContent(
          theme: theme,
          sizeMetrics: sizeMetrics,
          foreground: fg,
          extendedT: extendedT,
          hasIcon: hasIcon,
        ),
      ),
    );

    Widget surface = ConstrainedBox(
      constraints: BoxConstraints(
        minWidth: lerpDouble(collapsedWidth, minExtendedWidth, extendedT)!,
        minHeight: sizeMetrics.height,
        maxHeight: sizeMetrics.height,
      ),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: fill == null ? solidBg : null,
          gradient: fill,
          borderRadius: borderRadius,
          border: side == null ? null : Border.fromBorderSide(side),
          boxShadow: M3EElevation.shadows(
            resolvedElevation,
            shadowColor: theme.colorScheme.shadow,
          ),
        ),
        child: content,
      ),
    );
    if (outline != null) {
      surface = m3eGradientOutlineLayer(
        clipRadius: borderRadius,
        gradient: outline,
        width: m3eOutlineWidth(widget.decoration?.side?.resolve(states)),
        child: surface,
      );
    }
    return M3EFocusRing(
      focused: state.focused,
      radius: borderRadius,
      width: extendedTheme.focusRingWidth,
      gap: extendedTheme.focusRingGap,
      color: extendedTheme.resolveFocusRingColor(theme.colorScheme),
      child: surface,
    );
  }
}
