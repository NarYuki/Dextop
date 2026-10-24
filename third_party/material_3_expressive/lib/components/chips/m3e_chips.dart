import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import '../../foundations/foundations.dart';
import 'enums/m3e_chip_type.dart';
import 'styles/m3e_chip_theme.dart';

export 'enums/m3e_chip_type.dart';
export 'styles/m3e_chip_theme.dart';

part 'm3e_chip_group.dart';

/// A Material 3 Expressive chip.
///
/// Assist and suggestion chips trigger an action. Filter chips toggle a
/// selection. Input chips can be selected and removed. When both [onPressed]
/// and [onDeleted] are set, the remove icon is its own 48dp Tab stop.
class M3EChip extends StatefulWidget {
  /// Creates a chip.
  const M3EChip({
    required this.label,
    this.type = M3EChipType.assist,
    this.leading,
    this.avatar,
    this.trailing,
    this.selected = false,
    this.elevated = false,
    this.onPressed,
    this.onDeleted,
    this.focusNode,
    super.key,
  });

  /// Label shown on the chip.
  final String label;

  /// Which chip variant to paint.
  final M3EChipType type;

  /// Optional leading icon. Ignored when [avatar] is set.
  final Widget? leading;

  /// Optional input avatar. A 24dp image clipped to a 12dp radius.
  final Widget? avatar;

  /// Optional trailing icon, separate from the remove affordance.
  final Widget? trailing;

  /// Whether a filter or input chip is selected.
  ///
  /// Assist and suggestion chips ignore this for color.
  final bool selected;

  /// Whether to draw the elevated container instead of an outline.
  final bool elevated;

  /// Called when the chip is tapped, or activated with Space or Enter.
  final VoidCallback? onPressed;

  /// Called when the remove icon is used, or when Backspace or Delete is
  /// pressed while this chip or its remove control is focused.
  final VoidCallback? onDeleted;

  /// Focus node for the chip. The remove control uses its own node.
  final FocusNode? focusNode;

  @override
  State<M3EChip> createState() => _M3EChipState();
}

class _M3EChipState extends State<M3EChip> {
  FocusNode? _ownedNode;
  FocusNode? _removeNode;
  _M3EChipGroupRegistration? _registration;
  bool _dragged = false;

  bool get _enabled => widget.onPressed != null || widget.onDeleted != null;

  bool get _splitRemove => widget.onPressed != null && widget.onDeleted != null;

  bool get _selectable =>
      widget.onPressed != null &&
      (widget.type == M3EChipType.filter || widget.type == M3EChipType.input);

  bool get _removeOnly => widget.onPressed == null && widget.onDeleted != null;

  FocusNode get _focusNode => widget.focusNode ?? _ownedNode!;

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
  void didUpdateWidget(M3EChip oldWidget) {
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
    _removeNode?.dispose();
    super.dispose();
  }

  void _attachGroup() {
    if (_splitRemove) {
      _removeNode ??= FocusNode();
    }
    final _M3EChipGroupScope? scope = _M3EChipGroupScope.maybeOf(context);
    if (scope == null) {
      _detachGroup();
      return;
    }
    _registration ??= scope.attach(_focusNode);
    _registration!.sync(
      enabled: _enabled,
      onDeleted: widget.onDeleted,
      removeNode: _splitRemove ? (_removeNode ??= FocusNode()) : null,
    );
  }

  void _detachGroup() {
    _registration?._dispose();
    _registration = null;
  }

  void _clearFocusFromPointer() {
    M3EFocusInteraction.instance.notePointerInteraction();
    if (_focusNode.hasFocus) {
      _focusNode.unfocus();
    }
    final FocusNode? remove = _removeNode;
    if (remove != null && remove.hasFocus) {
      remove.unfocus();
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

  void _setDragged(bool dragged) {
    if (_dragged == dragged) {
      return;
    }
    setState(() => _dragged = dragged);
  }

  Widget _deleteKeys(Widget child) {
    final VoidCallback? onDeleted = widget.onDeleted;
    if (onDeleted == null) {
      return child;
    }
    return CallbackShortcuts(
      bindings: <ShortcutActivator, VoidCallback>{
        const SingleActivator(LogicalKeyboardKey.backspace): onDeleted,
        const SingleActivator(LogicalKeyboardKey.delete): onDeleted,
      },
      child: child,
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final chipTheme = theme.chipTheme;
    final scheme = theme.colorScheme;
    final stateLayer = chipTheme.stateLayerColor(
      scheme,
      selected: widget.selected,
      type: widget.type,
    );
    final labelColor = chipTheme.labelColor(
      scheme,
      enabled: _enabled,
      selected: widget.selected,
      type: widget.type,
    );
    final leadingColor = chipTheme.leadingIconColor(
      scheme,
      enabled: _enabled,
      selected: widget.selected,
      type: widget.type,
    );
    final trailingColor = chipTheme.trailingIconColor(
      scheme,
      enabled: _enabled,
      selected: widget.selected,
      type: widget.type,
    );

    return TapRegion(
      onTapOutside: _onTapOutside,
      child: Listener(
        behavior: HitTestBehavior.translucent,
        onPointerUp: _onPointerUp,
        child: GestureDetector(
          onPanStart: _enabled ? _onPanStart : null,
          onPanEnd: _enabled ? _onPanEnd : null,
          onPanCancel: _enabled ? _onPanCancel : null,
          child: M3EComponentTheme(
            builder: (BuildContext context) {
              return Stack(
                clipBehavior: Clip.none,
                children: <Widget>[
                  _deleteKeys(
                    M3ETappable(
                      onTap: widget.onPressed ?? widget.onDeleted,
                      enabled: _enabled,
                      focusNode: _focusNode,
                      semanticLabel: _removeOnly
                          ? 'Remove ${widget.label}'
                          : widget.label,
                      semanticButton: !_selectable,
                      semanticChecked: _selectable ? widget.selected : null,
                      excludeSemantics: true,
                      builder:
                          (BuildContext context, M3EInteractionState state) {
                            return _buildSurface(
                              theme,
                              chipTheme,
                              scheme,
                              _dragged ? state.copyWith(dragged: true) : state,
                              stateLayer: stateLayer,
                              labelColor: labelColor,
                              leadingColor: leadingColor,
                              trailingColor: trailingColor,
                            );
                          },
                    ),
                  ),
                  if (_splitRemove)
                    _buildRemoveTarget(chipTheme, trailingColor),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildRemoveTarget(M3EChipTheme chipTheme, Color trailingColor) {
    final onDeleted = widget.onDeleted;
    final removeNode = _removeNode;
    if (onDeleted == null || removeNode == null) {
      return const SizedBox.shrink();
    }
    final end = chipTheme.endPaddingFor(hasTrailing: true);
    final iconCenter = end + chipTheme.iconSize / 2;
    return Positioned(
      right: iconCenter - chipTheme.removeTargetSize / 2,
      top: (chipTheme.height - chipTheme.removeTargetSize) / 2,
      child: _deleteKeys(
        M3ETappable(
          onTap: onDeleted,
          focusNode: removeNode,
          semanticLabel: 'Remove ${widget.label}',
          excludeSemantics: true,
          builder: (BuildContext context, M3EInteractionState state) {
            return M3EFocusRing(
              focused: state.focused,
              radius: BorderRadius.circular(chipTheme.removeTargetSize / 2),
              color: chipTheme.resolveFocusIndicatorColor(
                M3ETheme.of(context).colorScheme,
              ),
              width: chipTheme.focusIndicatorThickness,
              gap: chipTheme.focusIndicatorOffset,
              child: _inkWell(
                onTap: onDeleted,
                shape: const CircleBorder(),
                splash: trailingColor,
                splashOpacity: chipTheme.pressedStateLayerOpacity,
                child: SizedBox(
                  width: chipTheme.removeTargetSize,
                  height: chipTheme.removeTargetSize,
                  child: Icon(
                    M3EIcons.close,
                    size: chipTheme.iconSize,
                    color: trailingColor,
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildSurface(
    M3EThemeData theme,
    M3EChipTheme chipTheme,
    M3EColorScheme scheme,
    M3EInteractionState state, {
    required Color stateLayer,
    required Color labelColor,
    required Color leadingColor,
    required Color trailingColor,
  }) {
    final radius = chipTheme.borderRadius;
    final outlineWidth = chipTheme.resolvedOutlineWidth(
      type: widget.type,
      selected: widget.selected,
      elevated: widget.elevated,
    );
    final hasAvatar = widget.avatar != null;
    final hasLeading = widget.leading != null && !hasAvatar;
    final hasTrailing = widget.trailing != null || widget.onDeleted != null;
    final opacity = chipTheme.stateLayerOpacity(state);
    final shape = RoundedRectangleBorder(
      borderRadius: radius,
      side: outlineWidth == 0
          ? BorderSide.none
          : BorderSide(
              color: chipTheme.outlineColor(
                scheme,
                enabled: _enabled,
                focused: state.focused,
                type: widget.type,
              ),
              width: outlineWidth,
            ),
    );
    final elevation = chipTheme.elevation(
      elevated: widget.elevated,
      dragged: state.dragged,
    );
    final shadows = M3EElevation.shadows(elevation, shadowColor: scheme.shadow);
    final Widget surface = _chipMaterial(
      chipTheme: chipTheme,
      scheme: scheme,
      state: state,
      stateLayer: stateLayer,
      shape: shape,
      labelColor: labelColor,
      leadingColor: leadingColor,
      trailingColor: trailingColor,
      theme: theme,
      hasAvatar: hasAvatar,
      hasLeading: hasLeading,
      hasTrailing: hasTrailing,
      radius: radius,
      opacity: opacity,
    );

    return M3EFocusRing(
      focused: state.focused,
      radius: radius,
      color: chipTheme.resolveFocusIndicatorColor(scheme),
      width: chipTheme.focusIndicatorThickness,
      gap: chipTheme.focusIndicatorOffset,
      child: DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: radius,
          boxShadow: shadows.isEmpty ? null : shadows,
        ),
        child: surface,
      ),
    );
  }

  Widget _chipMaterial({
    required M3EThemeData theme,
    required M3EChipTheme chipTheme,
    required M3EColorScheme scheme,
    required M3EInteractionState state,
    required Color stateLayer,
    required Color labelColor,
    required Color leadingColor,
    required Color trailingColor,
    required ShapeBorder shape,
    required bool hasAvatar,
    required bool hasLeading,
    required bool hasTrailing,
    required BorderRadius radius,
    required double opacity,
  }) {
    final Widget content = Container(
      height: chipTheme.height,
      constraints: BoxConstraints(
        minWidth: _splitRemove ? chipTheme.minWidth : 0,
      ),
      foregroundDecoration: opacity == 0
          ? null
          : BoxDecoration(
              color: stateLayer.withValues(alpha: opacity),
              borderRadius: radius,
            ),
      child: Padding(
        padding: EdgeInsets.only(
          left: chipTheme.startPadding(
            type: widget.type,
            hasLeading: hasLeading,
            hasAvatar: hasAvatar,
          ),
          right: chipTheme.endPaddingFor(hasTrailing: hasTrailing),
        ),
        child: _buildContent(
          theme,
          chipTheme,
          labelColor: labelColor,
          leadingColor: leadingColor,
          trailingColor: trailingColor,
          hasAvatar: hasAvatar,
        ),
      ),
    );
    return Material(
      color: chipTheme.containerColor(
        scheme,
        enabled: _enabled,
        selected: widget.selected,
        elevated: widget.elevated,
        type: widget.type,
      ),
      shape: shape,
      clipBehavior: Clip.antiAlias,
      child: _enabled
          ? _inkWell(
              onTap: (widget.onPressed ?? widget.onDeleted)!,
              shape: shape,
              splash: stateLayer,
              splashOpacity: chipTheme.pressedStateLayerOpacity,
              child: content,
            )
          : content,
    );
  }

  Widget _buildContent(
    M3EThemeData theme,
    M3EChipTheme chipTheme, {
    required Color labelColor,
    required Color leadingColor,
    required Color trailingColor,
    required bool hasAvatar,
  }) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        if (hasAvatar) ...<Widget>[
          _buildAvatar(chipTheme, leadingColor),
          SizedBox(width: chipTheme.iconLabelGap),
        ] else if (widget.leading != null) ...<Widget>[
          IconTheme.merge(
            data: IconThemeData(color: leadingColor, size: chipTheme.iconSize),
            child: widget.leading!,
          ),
          SizedBox(width: chipTheme.iconLabelGap),
        ],
        Flexible(
          child: Text(
            widget.label,
            style: theme.typeScale.labelLarge.copyWith(color: labelColor),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        if (widget.trailing != null) ...<Widget>[
          SizedBox(width: chipTheme.iconLabelGap),
          IconTheme.merge(
            data: IconThemeData(color: trailingColor, size: chipTheme.iconSize),
            child: widget.trailing!,
          ),
        ],
        if (widget.onDeleted != null) ...<Widget>[
          SizedBox(width: chipTheme.iconLabelGap),
          if (_splitRemove)
            SizedBox(width: chipTheme.iconSize, height: chipTheme.iconSize)
          else
            Icon(
              M3EIcons.close,
              size: chipTheme.iconSize,
              color: trailingColor,
            ),
        ],
      ],
    );
  }

  Widget _buildAvatar(M3EChipTheme chipTheme, Color leadingColor) {
    final avatar = widget.avatar;
    if (avatar == null) {
      return const SizedBox.shrink();
    }
    return SizedBox(
      width: chipTheme.avatarSize,
      height: chipTheme.avatarSize,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(chipTheme.avatarRadius),
        child: IconTheme.merge(
          data: IconThemeData(color: leadingColor, size: chipTheme.avatarSize),
          child: avatar,
        ),
      ),
    );
  }

  Widget _inkWell({
    required VoidCallback onTap,
    required ShapeBorder shape,
    required Color splash,
    required double splashOpacity,
    required Widget child,
  }) {
    return Material(
      type: MaterialType.transparency,
      color: Colors.transparent,
      shape: shape,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        canRequestFocus: false,
        excludeFromSemantics: true,
        customBorder: shape,
        splashFactory: InkSparkle.splashFactory,
        splashColor: splash.withValues(alpha: splashOpacity),
        highlightColor: Colors.transparent,
        hoverColor: Colors.transparent,
        focusColor: Colors.transparent,
        overlayColor: const WidgetStatePropertyAll<Color>(Colors.transparent),
        child: child,
      ),
    );
  }
}

extension _M3EChipPointer on _M3EChipState {
  void _onTapOutside(PointerDownEvent _) {
    _clearFocusFromPointer();
  }

  void _onPointerUp(PointerUpEvent _) {
    _scheduleClearFocusFromPointer();
  }

  void _onPanStart(DragStartDetails _) {
    _setDragged(true);
  }

  void _onPanEnd(DragEndDetails _) {
    _setDragged(false);
  }

  void _onPanCancel() {
    _setDragged(false);
  }
}
