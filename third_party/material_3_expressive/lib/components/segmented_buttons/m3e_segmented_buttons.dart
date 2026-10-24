import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../buttons/utils/m3e_button_gradient_layer.dart';
import 'components/m3e_segment_divider.dart';
import 'enums/m3e_segmented_button_enums.dart';
import 'models/m3e_segment.dart';
import 'styles/m3e_segmented_button_theme.dart';

export 'enums/m3e_segmented_button_enums.dart';
export 'models/m3e_segment.dart';
export 'styles/m3e_segmented_button_theme.dart';

/// A Material 3 Expressive segmented button.
///
/// Presents 2–5 connected [M3ESegment]s for selecting options, switching views
/// or sorting. Supports single or multiple selection and shows a check icon on
/// selected segments when [showSelectedIcon] is true.
class M3ESegmentedButton<T> extends StatefulWidget {
  /// Creates a segmented button.
  const M3ESegmentedButton({
    required this.segments,
    required this.selected,
    required this.onSelectionChanged,
    this.multiSelect = false,
    this.showSelectedIcon = true,
    this.density = M3ESegmentedButtonDensity.regular,
    this.enabled = true,
    this.semanticLabel,
    super.key,
  }) : assert(
         segments.length >= 2 && segments.length <= 5,
         'A segmented button needs 2–5 segments.',
       );

  /// Segments rendered in reading order (2–5).
  final List<M3ESegment<T>> segments;

  /// Currently selected values.
  final Set<T> selected;

  /// Called when selection changes.
  final ValueChanged<Set<T>> onSelectionChanged;

  /// When true, multiple segments may be selected (including none).
  final bool multiSelect;

  /// When true, selected segments show a checkmark (replacing the category icon).
  final bool showSelectedIcon;

  /// Density level that shrinks container height (−4dp per step).
  final M3ESegmentedButtonDensity density;

  /// When false, the whole group is disabled.
  final bool enabled;

  /// Optional accessibility label for the group.
  final String? semanticLabel;

  @override
  State<M3ESegmentedButton<T>> createState() => _M3ESegmentedButtonState<T>();

  void _handleTap(T value) {
    if (multiSelect) {
      final next = Set<T>.of(selected);
      if (next.contains(value)) {
        next.remove(value);
      } else {
        next.add(value);
      }
      onSelectionChanged(next);
      return;
    }
    // Single-select: select only; never clear by re-tapping.
    if (selected.contains(value)) {
      return;
    }
    onSelectionChanged(<T>{value});
  }

  bool _isSegmentEnabled(int index) => enabled && segments[index].enabled;
}

class _M3ESegmentedButtonState<T> extends State<M3ESegmentedButton<T>> {
  /// Segment row. Dividers sample their gradient across this box.
  final GlobalKey _rowKey = GlobalKey();

  /// Segment currently showing a keyboard focus ring, if any.
  final ValueNotifier<int?> _focusedIndex = ValueNotifier<int?>(null);

  late List<FocusNode> _focusNodes;

  @override
  void dispose() {
    M3EFocusInteraction.instance.removeListener(_onFocusInteractionChanged);
    _focusedIndex.dispose();
    for (final FocusNode node in _focusNodes) {
      node.dispose();
    }
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _focusNodes = _createFocusNodes(widget.segments.length);
    M3EFocusInteraction.instance.addListener(_onFocusInteractionChanged);
  }

  @override
  void didUpdateWidget(covariant M3ESegmentedButton<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.segments.length != widget.segments.length) {
      for (final FocusNode node in _focusNodes) {
        node.dispose();
      }
      _focusNodes = _createFocusNodes(widget.segments.length);
      _focusedIndex.value = null;
    }
  }

  List<FocusNode> _createFocusNodes(int count) {
    return List<FocusNode>.generate(
      count,
      (int i) => FocusNode(debugLabel: 'M3ESegmentedButton<$T>[$i]'),
    );
  }

  void _onTapOutside(PointerDownEvent event) {
    M3EFocusInteraction.instance.notePointerInteraction();
    _focusedIndex.value = null;
    for (final FocusNode node in _focusNodes) {
      if (node.hasFocus) {
        node.unfocus();
      }
    }
  }

  void _onFocusInteractionChanged() {
    if (!M3EFocusInteraction.instance.ringsAllowed &&
        _focusedIndex.value != null) {
      _focusedIndex.value = null;
    }
  }

  void _handleSegmentFocus(int index, {required bool focused}) {
    if (focused && M3EFocusInteraction.instance.ringsAllowed) {
      _focusedIndex.value = index;
    } else if (_focusedIndex.value == index) {
      _focusedIndex.value = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(builder: _buildButton);
  }

  Widget _buildButton(BuildContext context) {
    final theme = M3ETheme.of(context);
    final segmentedButtonTheme = theme.segmentedButtonTheme;
    final scheme = theme.colorScheme;
    final visualHeight = segmentedButtonTheme.heightFor(widget.density);
    final layoutHeight = segmentedButtonTheme.layoutHeightFor(widget.density);
    final borderRadius = segmentedButtonTheme.borderRadiusFor(widget.density);
    final pad = (layoutHeight - visualHeight) / 2;
    final groupEnabled = widget.enabled;

    final Widget visualBand = _visualBand(
      segmentedButtonTheme: segmentedButtonTheme,
      scheme: scheme,
      visualHeight: visualHeight,
      borderRadius: borderRadius,
      groupEnabled: groupEnabled,
    );

    Widget ring = SizedBox(
      height: layoutHeight,
      child: Stack(
        clipBehavior: Clip.none,
        alignment: Alignment.center,
        children: <Widget>[
          Positioned(
            left: 0,
            right: 0,
            top: pad,
            height: visualHeight,
            child: visualBand,
          ),
          Positioned.fill(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: _buildSegmentHitTargets(
                context,
                segmentedButtonTheme,
                visualHeight,
                layoutHeight,
              ),
            ),
          ),
          Positioned(
            left: 0,
            right: 0,
            top: pad,
            height: visualHeight,
            child: _buildFocusRingOverlay(segmentedButtonTheme),
          ),
        ],
      ),
    );

    final double? maxWidth = segmentedButtonTheme.maxWidth;
    if (maxWidth != null) {
      ring = ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: ring,
      );
    }

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: widget.semanticLabel,
      child: TapRegion(onTapOutside: _onTapOutside, child: ring),
    );
  }

  Widget _visualBand({
    required M3ESegmentedButtonTheme segmentedButtonTheme,
    required M3EColorScheme scheme,
    required double visualHeight,
    required BorderRadius borderRadius,
    required bool groupEnabled,
  }) {
    final Color outlineColor = segmentedButtonTheme.outline(
      scheme,
      enabled: groupEnabled,
    );
    final Gradient? outlineGradient = segmentedButtonTheme.outlineGradient;
    Widget visualBand = ClipRRect(
      borderRadius: borderRadius,
      child: Row(
        key: _rowKey,
        mainAxisSize: MainAxisSize.min,
        children: _buildSegmentFills(context, segmentedButtonTheme),
      ),
    );
    visualBand = Container(
      height: visualHeight,
      decoration: BoxDecoration(
        borderRadius: borderRadius,
        border: outlineGradient == null
            ? Border.all(
                color: outlineColor,
                width: segmentedButtonTheme.borderWidth,
              )
            : null,
      ),
      child: visualBand,
    );
    if (outlineGradient != null && groupEnabled) {
      visualBand = m3eGradientOutlineLayer(
        clipRadius: borderRadius,
        gradient: outlineGradient,
        width: segmentedButtonTheme.borderWidth,
        child: visualBand,
      );
    }
    return visualBand;
  }

  /// Selected fills and dividers (non-interactive paint layer).
  List<Widget> _buildSegmentFills(
    BuildContext context,
    M3ESegmentedButtonTheme segmentedButtonTheme,
  ) {
    final theme = M3ETheme.of(context);
    final children = <Widget>[];
    for (var i = 0; i < widget.segments.length; i++) {
      if (i > 0) {
        children.add(
          M3ESegmentDivider(
            hostKey: _rowKey,
            width: segmentedButtonTheme.borderWidth,
            color: segmentedButtonTheme.divider(
              theme.colorScheme,
              enabled: widget.enabled,
            ),
            gradient: widget.enabled
                ? segmentedButtonTheme.dividerGradient
                : null,
          ),
        );
      }
      final segment = widget.segments[i];
      final selected = widget.selected.contains(segment.value);
      final Gradient? gradient = selected
          ? segmentedButtonTheme.selectedBackgroundGradient
          : segmentedButtonTheme.unselectedBackgroundGradient;
      final Color? solidBg = segmentedButtonTheme.backgroundColor(
        theme.colorScheme,
        selected: selected,
      );
      children.add(
        Flexible(
          child: ColoredBox(
            color: gradient == null
                ? (solidBg ?? const Color(0x00000000))
                : const Color(0x00000000),
            child: gradient == null
                ? const SizedBox.expand()
                : DecoratedBox(
                    decoration: BoxDecoration(gradient: gradient),
                    child: const SizedBox.expand(),
                  ),
          ),
        ),
      );
    }
    return children;
  }

  List<Widget> _buildSegmentHitTargets(
    BuildContext context,
    M3ESegmentedButtonTheme segmentedButtonTheme,
    double visualHeight,
    double layoutHeight,
  ) {
    final children = <Widget>[];
    for (var i = 0; i < widget.segments.length; i++) {
      if (i > 0) {
        children.add(SizedBox(width: segmentedButtonTheme.borderWidth));
      }
      children.add(
        Flexible(
          child: _M3ESegmentTile<T>(
            segmentedButtonTheme: segmentedButtonTheme,
            index: i,
            parent: widget,
            visualHeight: visualHeight,
            layoutHeight: layoutHeight,
            segmentRadius: _segmentRadius(
              segmentedButtonTheme,
              i,
              Directionality.of(context),
            ),
            focusNode: _focusNodes[i],
            onFocusChanged: (bool focused) =>
                _handleSegmentFocus(i, focused: focused),
          ),
        ),
      );
    }
    return children;
  }

  /// Mirrors the segment row's flex structure so the ring of the focused
  /// segment lines up with it without measuring anything.
  Widget _buildFocusRingOverlay(M3ESegmentedButtonTheme segmentedButtonTheme) {
    return IgnorePointer(
      child: ValueListenableBuilder<int?>(
        valueListenable: _focusedIndex,
        builder: (BuildContext context, int? focusedIndex, _) {
          if (focusedIndex == null) {
            return const SizedBox.shrink();
          }
          final TextDirection direction = Directionality.of(context);
          final scheme = M3ETheme.of(context).colorScheme;
          final slots = <Widget>[];
          for (var i = 0; i < widget.segments.length; i++) {
            if (i > 0) {
              slots.add(SizedBox(width: segmentedButtonTheme.borderWidth));
            }
            slots.add(
              Flexible(
                child: i == focusedIndex
                    ? M3EFocusRing(
                        focused: true,
                        radius: _segmentRadius(
                          segmentedButtonTheme,
                          i,
                          direction,
                        ),
                        color: segmentedButtonTheme.focusColor(scheme),
                        width: segmentedButtonTheme.focusIndicatorWidth,
                        gap: segmentedButtonTheme.focusIndicatorGap,
                        child: const SizedBox.expand(),
                      )
                    : const SizedBox.expand(),
              ),
            );
          }
          return Row(children: slots);
        },
      ),
    );
  }

  /// Outer corners are rounded only where the segment meets the group edge.
  BorderRadius _segmentRadius(
    M3ESegmentedButtonTheme segmentedButtonTheme,
    int index,
    TextDirection direction,
  ) {
    final Radius outer = segmentedButtonTheme
        .borderRadiusFor(widget.density)
        .topLeft;
    return BorderRadiusDirectional.horizontal(
      start: index == 0 ? outer : Radius.zero,
      end: index == widget.segments.length - 1 ? outer : Radius.zero,
    ).resolve(direction);
  }
}

class _M3ESegmentTile<T> extends StatelessWidget {
  const _M3ESegmentTile({
    required this.segmentedButtonTheme,
    required this.index,
    required this.parent,
    required this.visualHeight,
    required this.layoutHeight,
    required this.segmentRadius,
    required this.focusNode,
    required this.onFocusChanged,
  });

  final M3ESegmentedButtonTheme segmentedButtonTheme;
  final int index;
  final M3ESegmentedButton<T> parent;
  final double visualHeight;
  final double layoutHeight;
  final BorderRadius segmentRadius;
  final FocusNode focusNode;

  /// Reports keyboard focus so the group can paint the ring above its clip.
  final ValueChanged<bool> onFocusChanged;

  @override
  Widget build(BuildContext context) {
    final M3ESegment<T> segment = parent.segments[index];
    final bool isSelected = parent.selected.contains(segment.value);
    final bool enabled = parent._isSegmentEnabled(index);
    final bool multi = parent.multiSelect;
    final ShapeBorder segmentShape = RoundedRectangleBorder(
      borderRadius: segmentRadius,
    );

    return Semantics(
      selected: isSelected,
      checked: multi ? isSelected : null,
      inMutuallyExclusiveGroup: multi ? null : true,
      enabled: enabled,
      button: true,
      child: M3ETappable(
        onTap: enabled ? () => parent._handleTap(segment.value) : null,
        enabled: enabled,
        focusNode: focusNode,
        semanticLabel: segment.resolvedSemanticLabel,
        semanticButton: false,
        excludeSemantics: true,
        materialInk: true,
        onStateChanged: (M3EInteractionState state) =>
            onFocusChanged(state.focused),
        builder: (BuildContext context, M3EInteractionState state) {
          final resolvedScheme = M3ETheme.of(context).colorScheme;
          final Gradient? fgGradient = isSelected
              ? segmentedButtonTheme.selectedForegroundGradient
              : segmentedButtonTheme.unselectedForegroundGradient;
          final resolvedForeground = !enabled
              ? segmentedButtonTheme.foregroundColor(
                  resolvedScheme,
                  selected: isSelected,
                  enabled: false,
                )
              : fgGradient != null
              ? m3eGradientForegroundSourceColor
              : segmentedButtonTheme.foregroundColor(
                  resolvedScheme,
                  selected: isSelected,
                  enabled: true,
                );
          return SizedBox(
            width: double.infinity,
            height: layoutHeight,
            child: Center(
              child: ClipRRect(
                borderRadius: segmentRadius,
                child: SizedBox(
                  width: double.infinity,
                  height: visualHeight,
                  child: M3EStateLayerOverlay(
                    state: enabled ? state : const M3EInteractionState(),
                    color: resolvedForeground,
                    shape: segmentShape,
                    alignment: Alignment.center,
                    child: Padding(
                      padding: EdgeInsets.symmetric(
                        horizontal:
                            segmentedButtonTheme.segmentHorizontalPadding,
                      ),
                      child: _wrapForeground(
                        segmentedButtonTheme,
                        isSelected,
                        enabled,
                        _buildLabel(
                          context,
                          segment,
                          resolvedForeground,
                          isSelected,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildLabel(
    BuildContext context,
    M3ESegment<T> segment,
    Color foreground,
    bool selected,
  ) {
    final theme = M3ETheme.of(context);
    final Widget? leading = _resolveLeading(segment, foreground, selected);
    final children = <Widget>[
      if (leading != null) ...<Widget>[
        leading,
        SizedBox(width: segmentedButtonTheme.iconLabelGap),
      ],
      if (segment.label != null)
        Flexible(
          child: Text(
            segment.label!,
            style: theme.typeScale.labelLarge.copyWith(color: foreground),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
          ),
        ),
    ];
    return Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: children,
    );
  }

  Widget _wrapForeground(
    M3ESegmentedButtonTheme theme,
    bool selected,
    bool enabled,
    Widget child,
  ) {
    if (!enabled) {
      return child;
    }
    final Gradient? gradient = selected
        ? theme.selectedForegroundGradient
        : theme.unselectedForegroundGradient;
    if (gradient == null) {
      return child;
    }
    return ShaderMask(
      blendMode: BlendMode.srcIn,
      shaderCallback: (Rect bounds) => gradient.createShader(bounds),
      child: child,
    );
  }

  Widget? _resolveLeading(M3ESegment<T> segment, Color foreground, bool sel) {
    if (sel && parent.showSelectedIcon) {
      return Icon(
        M3EIcons.check,
        size: segmentedButtonTheme.iconSize,
        color: foreground,
      );
    }
    if (segment.icon != null) {
      return IconTheme.merge(
        data: IconThemeData(
          color: foreground,
          size: segmentedButtonTheme.iconSize,
        ),
        child: segment.icon!,
      );
    }
    return null;
  }
}
