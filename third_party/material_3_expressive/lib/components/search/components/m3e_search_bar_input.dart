part of '../m3e_search_bar.dart';

double _resolveActionIconSize({
  required M3EIconButtonTheme iconButtonTheme,
  required M3ESearchBarTheme barTheme,
  required Iterable<Widget>? trailing,
  required Widget? leading,
}) {
  final Widget? referenceAction = trailing != null && trailing.isNotEmpty
      ? trailing.last
      : leading;
  if (referenceAction is M3EIconButton) {
    return iconButtonTheme.iconSize(referenceAction.size);
  }
  if (referenceAction is Icon && referenceAction.size != null) {
    return referenceAction.size!;
  }
  return barTheme.iconSize;
}

/// Square tap target; heightFactor keeps it from stretching the bar.
Widget _wrapActionSlot({required double width, required Widget child}) {
  return SizedBox(
    width: width,
    child: Center(heightFactor: 1, child: child),
  );
}

/// m3eDefaultSearchContextMenuBuilder.

Widget m3eDefaultSearchContextMenuBuilder(
  BuildContext context,
  EditableTextState editableTextState,
) {
  return AdaptiveTextSelectionToolbar.editableText(
    editableTextState: editableTextState,
  );
}

/// Shared editable search field used by [M3ESearchBar] and the search view header.
class M3ESearchBarInput extends StatefulWidget {
  /// M3ESearchBarInput.
  const M3ESearchBarInput({
    required this.controller,
    required this.focusNode,
    required this.hintText,
    required this.enabled,
    required this.readOnly,
    required this.autoFocus,
    required this.textStyle,
    required this.hintStyle,
    required this.cursorColor,
    required this.selectionColor,
    this.onTap,
    this.onTapOutside,
    this.onChanged,
    this.onSubmitted,
    this.onEscape,
    this.textCapitalization = TextCapitalization.none,
    this.textInputAction,
    this.keyboardType,
    this.scrollPadding = const EdgeInsets.all(20),
    this.contextMenuBuilder = m3eDefaultSearchContextMenuBuilder,
    this.smartDashesType,
    this.smartQuotesType,
    this.contentPadding = EdgeInsets.zero,
    this.hintAlignment = AlignmentDirectional.centerStart,
    super.key,
  });

  /// controller.

  final TextEditingController controller;

  /// focusNode.
  final FocusNode focusNode;

  /// hintText.
  final String? hintText;

  /// enabled.
  final bool enabled;

  /// readOnly.
  final bool readOnly;

  /// autoFocus.
  final bool autoFocus;

  /// textStyle.
  final TextStyle textStyle;

  /// hintStyle.
  final TextStyle hintStyle;

  /// cursorColor.
  final Color cursorColor;

  /// selectionColor.
  final Color selectionColor;

  /// onTap.
  final GestureTapCallback? onTap;

  /// onTapOutside.
  final TapRegionCallback? onTapOutside;

  /// onChanged.
  final ValueChanged<String>? onChanged;

  /// onSubmitted.
  final ValueChanged<String>? onSubmitted;

  /// onEscape — when set, Escape invokes this instead of unfocusing.
  final VoidCallback? onEscape;

  /// textCapitalization.
  final TextCapitalization textCapitalization;

  /// textInputAction.
  final TextInputAction? textInputAction;

  /// keyboardType.
  final TextInputType? keyboardType;

  /// scrollPadding.
  final EdgeInsets scrollPadding;

  /// contextMenuBuilder.
  final EditableTextContextMenuBuilder contextMenuBuilder;

  /// smartDashesType.
  final SmartDashesType? smartDashesType;

  /// smartQuotesType.
  final SmartQuotesType? smartQuotesType;

  /// contentPadding.
  final EdgeInsetsGeometry contentPadding;

  /// Alignment of the idle hint inside the field.
  final AlignmentGeometry hintAlignment;

  @override
  State<M3ESearchBarInput> createState() => _M3ESearchBarInputState();
}

class _M3ESearchBarInputState extends State<M3ESearchBarInput>
    implements TextSelectionGestureDetectorBuilderDelegate {
  final GlobalKey<EditableTextState> _editableKey =
      GlobalKey<EditableTextState>();
  late final TextSelectionGestureDetectorBuilder _selectionBuilder =
      TextSelectionGestureDetectorBuilder(delegate: this);
  bool _showSelectionHandles = false;

  @override
  GlobalKey<EditableTextState> get editableTextKey => _editableKey;

  @override
  bool get forcePressEnabled => defaultTargetPlatform == TargetPlatform.iOS;

  @override
  bool get selectionEnabled => widget.enabled;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_handleTextChange);
  }

  @override
  void didUpdateWidget(M3ESearchBarInput oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_handleTextChange);
      widget.controller.addListener(_handleTextChange);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_handleTextChange);
    super.dispose();
  }

  void _handleTextChange() => setState(() {});

  /// Touch selections show handles; keyboard and mouse selections do not.
  bool _shouldShowSelectionHandles(SelectionChangedCause? cause) {
    if (!_selectionBuilder.shouldShowSelectionToolbar ||
        !_selectionBuilder.shouldShowSelectionHandles) {
      return false;
    }
    if (cause == SelectionChangedCause.keyboard || !widget.enabled) {
      return false;
    }
    if (widget.readOnly && widget.controller.selection.isCollapsed) {
      return false;
    }
    if (cause == SelectionChangedCause.longPress ||
        cause == SelectionChangedCause.stylusHandwriting) {
      return true;
    }
    return widget.controller.text.isNotEmpty;
  }

  void _handleSelectionChanged(
    TextSelection selection,
    SelectionChangedCause? cause,
  ) {
    final bool show = _shouldShowSelectionHandles(cause);
    if (show != _showSelectionHandles) {
      setState(() => _showSelectionHandles = show);
    }
    if (cause == SelectionChangedCause.longPress) {
      _editableKey.currentState?.bringIntoView(selection.extent);
    }
    final bool desktop = switch (defaultTargetPlatform) {
      TargetPlatform.macOS ||
      TargetPlatform.linux ||
      TargetPlatform.windows => true,
      TargetPlatform.android ||
      TargetPlatform.fuchsia ||
      TargetPlatform.iOS => false,
    };
    if (desktop && cause == SelectionChangedCause.drag) {
      _editableKey.currentState?.hideToolbar();
    }
  }

  /// Tapping the caret handle toggles the toolbar.
  void _handleSelectionHandleTapped() {
    if (widget.controller.selection.isCollapsed) {
      _editableKey.currentState?.toggleToolbar();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: widget.hintText,
      textField: true,
      child: Padding(
        padding: widget.contentPadding,
        child: Stack(
          alignment: AlignmentDirectional.centerStart,
          children: <Widget>[
            if (widget.controller.text.isEmpty && widget.hintText != null)
              Positioned.fill(
                child: Align(
                  alignment: widget.hintAlignment,
                  child: _buildIdleHint(),
                ),
              ),
            Listener(
              behavior: HitTestBehavior.translucent,
              onPointerDown: (_) => widget.onTap?.call(),
              child: CallbackShortcuts(
                bindings: _shortcutBindings(),
                // Read-only anchors must not take the text-input client on tap
                // (soft keyboard flash when SearchAnchor opens the view).
                child: AbsorbPointer(
                  absorbing: widget.readOnly || !widget.enabled,
                  child: _selectionBuilder.buildGestureDetector(
                    behavior: HitTestBehavior.translucent,
                    child: _buildEditableText(),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildIdleHint() {
    return IgnorePointer(
      child: Text(
        widget.hintText!,
        style: widget.hintStyle,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
    );
  }

  Map<ShortcutActivator, VoidCallback> _shortcutBindings() {
    return <ShortcutActivator, VoidCallback>{
      ...M3EFocus.editableTabShortcuts(widget.focusNode),
      const SingleActivator(LogicalKeyboardKey.escape): _handleEscape,
    };
  }

  void _handleEscape() {
    if (widget.onEscape != null) {
      widget.onEscape!();
    } else if (widget.focusNode.hasPrimaryFocus) {
      widget.focusNode.unfocus();
    }
  }

  Widget _buildEditableText() {
    return EditableText(
      key: _editableKey,
      controller: widget.controller,
      focusNode: widget.focusNode,
      readOnly: widget.readOnly || !widget.enabled,
      autofocus: widget.autoFocus,
      onTapOutside:
          widget.onTapOutside ?? M3EFocus.tapOutsideHandler(widget.focusNode),
      onChanged: widget.onChanged,
      onSubmitted: widget.onSubmitted,
      style: widget.textStyle,
      cursorColor: widget.cursorColor,
      backgroundCursorColor: widget.cursorColor.withValues(alpha: 0.4),
      selectionColor: widget.selectionColor,
      textCapitalization: widget.textCapitalization,
      // Enter executes the search; the field unfocuses and keeps the query.
      textInputAction: widget.textInputAction ?? TextInputAction.search,
      keyboardType: widget.keyboardType,
      scrollPadding: widget.scrollPadding,
      contextMenuBuilder: widget.contextMenuBuilder,
      selectionControls: m3eSearchSelectionControls(defaultTargetPlatform),
      showSelectionHandles: _showSelectionHandles,
      onSelectionChanged: _handleSelectionChanged,
      onSelectionHandleTapped: _handleSelectionHandleTapped,
      rendererIgnoresPointer: true,
      smartDashesType: widget.smartDashesType,
      smartQuotesType: widget.smartQuotesType,
    );
  }
}

/// A Material 3 Expressive search bar.
