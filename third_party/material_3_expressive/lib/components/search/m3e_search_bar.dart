import 'package:flutter/foundation.dart' show defaultTargetPlatform;
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart'
    show
        AdaptiveTextSelectionToolbar,
        InkWell,
        Material,
        MaterialType,
        WidgetStateProperty,
        WidgetStatePropertyAll,
        WidgetStatesController;

import '../../../foundations/foundations.dart';
import '../icon_buttons/m3e_icon_buttons.dart';
import 'components/m3e_search_anchor_scope.dart';
import 'models/m3e_search_anchor_surface.dart';
import 'res/m3e_search_constants.dart';
import 'styles/m3e_search_bar_theme.dart';
import 'utils/m3e_search_selection_controls.dart';
import 'utils/m3e_search_spring.dart';

part 'components/m3e_search_bar_input.dart';
part 'components/m3e_search_bar_build.dart';

/// A Material 3 Expressive search bar (contained style).
///
/// A 56dp pill on surface container high with an optional leading icon,
/// hinted search text, up to two trailing icons, and an optional [avatar].
/// The bar sits [margin] (24) from its pane and widens to [focusedMargin]
/// (12) on focus with a spatial spring.
class M3ESearchBar extends StatefulWidget {
  /// M3ESearchBar.
  const M3ESearchBar({
    this.controller,
    this.focusNode,
    this.hintText,
    this.leading,
    this.trailing,
    this.onTap,
    this.onTapOutside,
    this.onChanged,
    this.onSubmitted,
    this.constraints,
    this.elevation,
    this.backgroundColor,
    this.shadowColor,
    this.surfaceTintColor,
    this.overlayColor,
    this.side,
    this.shape,
    this.padding,
    this.textStyle,
    this.hintStyle,
    this.textCapitalization,
    this.enabled = true,
    this.autoFocus = false,
    this.textInputAction,
    this.keyboardType,
    this.scrollPadding = const EdgeInsets.all(20),
    this.contextMenuBuilder = m3eDefaultSearchContextMenuBuilder,
    this.readOnly = false,
    this.expandOnFocus = true,
    this.expandRestPadding,
    this.smartDashesType,
    this.smartQuotesType,
    this.alignment = AlignmentDirectional.centerStart,
    this.wrapActions,
    this.onEscape,
    this.avatar,
    this.showClearButton = false,
    this.focusIndicatorColor,
    this.margin,
    this.focusedMargin,
    super.key,
  });

  /// controller.

  final TextEditingController? controller;

  /// focusNode.
  final FocusNode? focusNode;

  /// hintText.
  final String? hintText;

  /// Leading navigational icon button (menu, arrow) or a non-functional
  /// search [Icon]. A plain [Icon] is hidden from screen readers.
  final Widget? leading;

  /// One or two trailing icons or icon buttons. With an [avatar], use at
  /// most one.
  final Iterable<Widget>? trailing;

  /// onTap.
  final GestureTapCallback? onTap;

  /// onTapOutside.
  final TapRegionCallback? onTapOutside;

  /// onChanged.
  final ValueChanged<String>? onChanged;

  /// onSubmitted.
  final ValueChanged<String>? onSubmitted;

  /// constraints.
  final BoxConstraints? constraints;

  /// elevation.
  final WidgetStateProperty<double?>? elevation;

  /// backgroundColor.
  final WidgetStateProperty<Color?>? backgroundColor;

  /// shadowColor.
  final WidgetStateProperty<Color?>? shadowColor;

  /// surfaceTintColor.
  final WidgetStateProperty<Color?>? surfaceTintColor;

  /// overlayColor.
  final WidgetStateProperty<Color?>? overlayColor;

  /// side.
  final WidgetStateProperty<BorderSide?>? side;

  /// shape.
  final WidgetStateProperty<OutlinedBorder?>? shape;

  /// padding.
  final WidgetStateProperty<EdgeInsetsGeometry?>? padding;

  /// textStyle.
  final WidgetStateProperty<TextStyle?>? textStyle;

  /// hintStyle.
  final WidgetStateProperty<TextStyle?>? hintStyle;

  /// textCapitalization.
  final TextCapitalization? textCapitalization;

  /// enabled.
  final bool enabled;

  /// autoFocus.
  final bool autoFocus;

  /// textInputAction.
  final TextInputAction? textInputAction;

  /// keyboardType.
  final TextInputType? keyboardType;

  /// scrollPadding.
  final EdgeInsets scrollPadding;

  /// contextMenuBuilder.
  final EditableTextContextMenuBuilder contextMenuBuilder;

  /// readOnly.
  final bool readOnly;

  /// expandOnFocus.
  final bool expandOnFocus;

  /// Legacy resting margin. When set (and [margin] is null), the bar rests at
  /// this margin and narrows it by half on focus.
  final double? expandRestPadding;

  /// smartDashesType.
  final SmartDashesType? smartDashesType;

  /// smartQuotesType.
  final SmartQuotesType? smartQuotesType;

  /// Alignment of leading, hint, and trailing while the field is empty and
  /// unfocused. Switches to start layout when focused or when text is present.
  final AlignmentGeometry alignment;

  /// Groups leading, hint, and trailing into one row that follows [alignment].
  ///
  /// Null keeps the previous rule: group only when [alignment] is centered.
  /// True always groups while idle. False keeps leading and trailing at the
  /// ends of the pill and aligns only the hint.
  final bool? wrapActions;

  /// Called when Escape is pressed while the search field has focus.
  ///
  /// Defaults to unfocusing the field. Search views pass a dismiss callback
  /// so Escape closes the overlay instead of only clearing focus.
  final VoidCallback? onEscape;

  /// Optional trailing avatar, clipped to a 30dp circle in a 48dp target.
  final Widget? avatar;

  /// Shows a clear (X) action before [trailing] while the field has text.
  final bool showClearButton;

  /// Focus indicator color. Defaults to secondary.
  final Color? focusIndicatorColor;

  /// Side margin while unfocused. Defaults to the theme (24).
  final double? margin;

  /// Side margin while focused. Defaults to the theme (12).
  final double? focusedMargin;

  @override
  State<M3ESearchBar> createState() => _M3ESearchBarState();
}

class _M3ESearchBarState extends State<M3ESearchBar>
    with SingleTickerProviderStateMixin {
  late final TextEditingController _controller =
      widget.controller ?? TextEditingController();
  late final WidgetStatesController _statesController =
      WidgetStatesController();
  late final AnimationController _expandPaddingController;
  FocusNode? _internalFocusNode;
  bool _expandPaddingSyncScheduled = false;
  bool _showFocusRing = false;
  bool _marginReady = false;
  M3ESearchAnchorSurface? _surface;
  BuildContext? _surfaceContext;

  FocusNode get _focusNode =>
      widget.focusNode ?? (_internalFocusNode ??= FocusNode());

  @override
  void initState() {
    super.initState();
    _expandPaddingController = AnimationController.unbounded(vsync: this);
    _statesController.addListener(() => setState(() {}));
    _controller.addListener(_handleTextChange);
    _focusNode.addListener(_handleFocusChange);
    _syncFocusedState();
    FocusManager.instance.addHighlightModeListener(_handleHighlightModeChange);
    M3EFocusInteraction.instance.addListener(_handleFocusInteractionChanged);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_marginReady) {
      // Start at the resting margin so the first frame does not jump.
      _marginReady = true;
      _expandPaddingController.value = _targetExpandPadding(
        M3ETheme.of(context).searchBarTheme,
      );
    }
  }

  void _handleFocusInteractionChanged() {
    if (!mounted) {
      return;
    }
    final bool show = M3EFocusRing.shouldShow(_focusNode, context);
    if (show == _showFocusRing) {
      return;
    }
    setState(() => _showFocusRing = show);
  }

  void _scheduleExpandPaddingSync(
    M3ESearchBarTheme barTheme, {
    bool animate = false,
  }) {
    if (_expandPaddingSyncScheduled) {
      return;
    }
    _expandPaddingSyncScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _expandPaddingSyncScheduled = false;
      if (!mounted) {
        return;
      }
      _syncExpandPaddingController(barTheme, animate: animate);
    });
  }

  @override
  void didUpdateWidget(covariant M3ESearchBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.focusNode != widget.focusNode) {
      (oldWidget.focusNode ?? _internalFocusNode)?.removeListener(
        _handleFocusChange,
      );
      _focusNode.addListener(_handleFocusChange);
      _syncFocusedState();
    }
  }

  @override
  void dispose() {
    FocusManager.instance.removeHighlightModeListener(
      _handleHighlightModeChange,
    );
    M3EFocusInteraction.instance.removeListener(_handleFocusInteractionChanged);
    _focusNode.removeListener(_handleFocusChange);
    _controller.removeListener(_handleTextChange);
    if (_surfaceContext != null) {
      _surface?.detach(_surfaceContext!);
    }
    _expandPaddingController.dispose();
    _statesController.dispose();
    if (widget.controller == null) {
      _controller.dispose();
    }
    _internalFocusNode?.dispose();
    super.dispose();
  }

  void _syncFocusedState() {
    if (!mounted) {
      return;
    }
    // The states controller listener rebuilds, so the ring flag rides along.
    _showFocusRing = M3EFocusRing.shouldShow(_focusNode, context);
    _statesController.update(WidgetState.focused, _focusNode.hasFocus);
  }

  void _handleHighlightModeChange(FocusHighlightMode mode) {
    if (!mounted) {
      return;
    }
    final bool show = M3EFocusRing.shouldShow(_focusNode, context);
    if (show == _showFocusRing) {
      return;
    }
    setState(() => _showFocusRing = show);
  }

  void _handleTextChange() => setState(() {});

  /// Empty and unfocused: may group leading + hint + trailing for alignment.
  bool get _alignIdleContent =>
      !_focusNode.hasFocus && _controller.text.isEmpty;

  /// Whether idle content should shrink-wrap and honor [M3ESearchBar.alignment]
  /// (horizontal center). Start-aligned idle keeps the expanded field layout so
  /// trailing stays at the end of the pill.
  bool _groupsIdleContent(TextDirection textDirection) {
    if (!_alignIdleContent) {
      return false;
    }
    if (widget.wrapActions != null) {
      return widget.wrapActions!;
    }
    final Alignment resolved = widget.alignment.resolve(textDirection);
    return resolved.x.abs() < 0.001;
  }

  double _restingExpandPadding(M3ESearchBarTheme barTheme) {
    return widget.margin ??
        widget.expandRestPadding ??
        barTheme.unfocusedMargin;
  }

  double _focusedExpandPadding(M3ESearchBarTheme barTheme) {
    if (widget.focusedMargin != null) {
      return widget.focusedMargin!;
    }
    if (widget.margin == null && widget.expandRestPadding != null) {
      return widget.expandRestPadding! / 2;
    }
    return barTheme.focusedMargin;
  }

  bool _shouldAnimateExpandPadding(M3ESearchBarTheme barTheme) {
    // Read-only and disabled bars keep the resting margin; they never take
    // focus, so they never widen.
    if (!widget.expandOnFocus || !barTheme.expandOnFocus) {
      return false;
    }
    return _restingExpandPadding(barTheme) > 0.5 ||
        _focusedExpandPadding(barTheme) > 0.5;
  }

  double _targetExpandPadding(M3ESearchBarTheme barTheme) {
    if (!_shouldAnimateExpandPadding(barTheme)) {
      return 0;
    }
    return _focusNode.hasFocus && widget.enabled && !widget.readOnly
        ? _focusedExpandPadding(barTheme)
        : _restingExpandPadding(barTheme);
  }

  void _syncExpandPaddingController(
    M3ESearchBarTheme barTheme, {
    bool animate = false,
  }) {
    final double target = _targetExpandPadding(barTheme);
    if (!_shouldAnimateExpandPadding(barTheme)) {
      _expandPaddingController.value = target;
      return;
    }
    if (animate &&
        (_expandPaddingController.isAnimating ||
            (target - _expandPaddingController.value).abs() > 0.5)) {
      _expandPaddingController
        ..stop()
        ..animateWith(
          m3eSearchSpringSimulation(
            barTheme.focusExpandSpring,
            from: _expandPaddingController.value,
            to: target,
            velocity: _expandPaddingController.velocity,
          ),
        );
      return;
    }
    if (!_expandPaddingController.isAnimating) {
      _expandPaddingController.value = target;
    }
  }

  void _handleFocusChange() {
    _syncFocusedState();
    _syncExpandPaddingController(
      M3ETheme.of(context).searchBarTheme,
      animate: true,
    );
    setState(() {});
  }

  void _handleTap() {
    widget.onTap?.call();
    M3EFocusInteraction.instance.notePointerInteraction();
    // Read-only bars (e.g. SearchAnchor.bar) open a view and must not take
    // keyboard focus — the view's search field owns editing.
    if (widget.readOnly || !widget.enabled) {
      return;
    }
    if (!_focusNode.hasFocus) {
      _focusNode.requestFocus();
    } else {
      _syncExpandPaddingController(
        M3ETheme.of(context).searchBarTheme,
        animate: true,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(
      builder: (BuildContext context) {
        final theme = M3ETheme.of(context);
        final barTheme = theme.searchBarTheme;
        final scheme = theme.colorScheme;
        final states = _statesController.value;
        final textDirection = Directionality.of(context);

        if (!_expandPaddingController.isAnimating &&
            _shouldAnimateExpandPadding(barTheme) &&
            (_expandPaddingController.value - _targetExpandPadding(barTheme))
                    .abs() >
                0.5) {
          _scheduleExpandPaddingSync(barTheme);
        }

        final Widget bar = _buildBarContent(
          theme: theme,
          barTheme: barTheme,
          scheme: scheme,
          states: states,
          textDirection: textDirection,
        );

        final BoxConstraints barConstraints = barTheme.constraints(
          override: widget.constraints,
        );

        // Capped at the max width (720) and centered in wider panes.
        final Widget sized = Align(
          heightFactor: 1,
          child: ConstrainedBox(constraints: barConstraints, child: bar),
        );

        if (!_shouldAnimateExpandPadding(barTheme)) {
          return sized;
        }

        return AnimatedBuilder(
          animation: _expandPaddingController,
          builder: (BuildContext context, Widget? child) {
            // Allow spring overshoot past the resting inset; never go negative.
            final pad = _expandPaddingController.value;
            final horizontal = pad.isFinite && pad > 0 ? pad : 0.0;
            return Padding(
              padding: EdgeInsets.symmetric(horizontal: horizontal),
              child: child,
            );
          },
          child: sized,
        );
      },
    );
  }
}
