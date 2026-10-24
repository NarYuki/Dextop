part of '../m3e_dialogs.dart';

/// Selection list hosted inside [M3EDialog.showSelectionScreen].
class _M3ESelectionDialog extends StatefulWidget {
  const _M3ESelectionDialog({
    required this.title,
    required this.options,
    required this.multiSelect,
    required this.initialSelection,
    required this.cancelLabel,
    required this.confirmLabel,
    this.icon,
  });

  final String title;
  final List<String> options;
  final bool multiSelect;
  final List<String> initialSelection;
  final String cancelLabel;
  final String confirmLabel;
  final Widget? icon;

  @override
  State<_M3ESelectionDialog> createState() => _M3ESelectionDialogState();
}

class _M3ESelectionDialogState extends State<_M3ESelectionDialog> {
  late final Set<String> _selected;

  // Roving Tab stop: only [_active] is in Tab order; arrows move it.
  late final List<FocusNode> _nodes;
  int _active = 0;

  @override
  void initState() {
    super.initState();
    final Set<String> allowed = widget.options.toSet();
    _selected = widget.initialSelection.where(allowed.contains).toSet();
    if (!widget.multiSelect && _selected.length > 1) {
      final String first = _selected.first;
      _selected
        ..clear()
        ..add(first);
    }
    _nodes = <FocusNode>[
      for (final String option in widget.options)
        FocusNode(debugLabel: 'M3EDialog option $option'),
    ];
    final int selected = widget.options.indexWhere(_selected.contains);
    _active = selected < 0 ? 0 : selected;
  }

  @override
  void dispose() {
    for (final FocusNode node in _nodes) {
      node.dispose();
    }
    super.dispose();
  }

  void _focusRow(int index) {
    final int next = index.clamp(0, _nodes.length - 1);
    setState(() => _active = next);
    final FocusNode node = _nodes[next]..requestFocus();
    final BuildContext? rowContext = node.context;
    if (rowContext != null) {
      Scrollable.ensureVisible(rowContext, alignmentPolicy: _policy);
    }
  }

  static const ScrollPositionAlignmentPolicy _policy =
      ScrollPositionAlignmentPolicy.keepVisibleAtEnd;

  Map<ShortcutActivator, VoidCallback> get _listKeys {
    return <ShortcutActivator, VoidCallback>{
      const SingleActivator(LogicalKeyboardKey.arrowDown): () =>
          _focusRow(_active + 1),
      const SingleActivator(LogicalKeyboardKey.arrowUp): () =>
          _focusRow(_active - 1),
      const SingleActivator(LogicalKeyboardKey.home): () => _focusRow(0),
      const SingleActivator(LogicalKeyboardKey.end): () =>
          _focusRow(_nodes.length - 1),
    };
  }

  bool get _hasSelection => _selected.isNotEmpty;

  void _selectSingle(String value) {
    setState(() {
      _selected
        ..clear()
        ..add(value);
    });
  }

  void _toggleMulti(String value) {
    setState(() {
      if (_selected.contains(value)) {
        _selected.remove(value);
      } else {
        _selected.add(value);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3EDialogTheme dialogTheme = theme.dialogTheme;
    final NavigatorState navigator = Navigator.of(context);

    return M3EDialog(
      title: widget.title,
      icon: widget.icon,
      topDivider: true,
      bottomDivider: true,
      contentPadding: EdgeInsets.only(
        top: dialogTheme.padding.top / 2,
        bottom: dialogTheme.padding.bottom / 2,
      ),
      content: CallbackShortcuts(
        bindings: _listKeys,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            for (int i = 0; i < widget.options.length; i++)
              _buildSelectionItem(
                theme: theme,
                dialogTheme: dialogTheme,
                index: i,
              ),
          ],
        ),
      ),
      actions: <Widget>[
        m3eDialogTextAction(
          label: widget.cancelLabel,
          onPressed: () => navigator.pop(),
        ),
        m3eDialogTextAction(
          label: widget.confirmLabel,
          onPressed: _hasSelection
              ? () => navigator.pop(List<String>.unmodifiable(_selected))
              : null,
        ),
      ],
    );
  }

  Widget _buildSelectionItem({
    required M3EThemeData theme,
    required M3EDialogTheme dialogTheme,
    required int index,
  }) {
    final String option = widget.options[index];
    final EdgeInsets padding = dialogTheme.padding;
    final ShapeBorder shape = const RoundedRectangleBorder();

    // Row owns Tab / Enter; embedded radio/checkbox stay enabled visually but
    // are not Tab stops ([focusable]: false + [ExcludeFocus]).
    return M3ETappable(
      focusNode: _nodes[index],
      skipTraversal: index != _active,
      onTap: () {
        _active = index;
        if (widget.multiSelect) {
          _toggleMulti(option);
        } else {
          _selectSingle(option);
        }
      },
      materialInk: true,
      semanticLabel: option,
      builder: (BuildContext context, M3EInteractionState state) {
        return M3EFocusRing(
          focused: state.focused,
          radius: BorderRadius.zero,
          child: SizedBox(
            height: dialogTheme.selectionItemHeight,
            width: double.infinity,
            child: M3EStateLayerOverlay(
              state: state,
              color: theme.colorScheme.onSurface,
              shape: shape,
              child: Padding(
                padding: EdgeInsets.only(
                  left: padding.left,
                  right: padding.right,
                ),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: ExcludeSemantics(
                    child: ExcludeFocus(
                      child: IgnorePointer(
                        child: widget.multiSelect
                            ? M3ECheckbox(
                                value: _selected.contains(option),
                                onChanged: (_) => _toggleMulti(option),
                                label: Text(option),
                                focusable: false,
                              )
                            : M3ERadio<String>(
                                value: option,
                                groupValue: _selected.isEmpty
                                    ? null
                                    : _selected.first,
                                label: Text(option),
                                onChanged: _selectSingle,
                                focusable: false,
                              ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
