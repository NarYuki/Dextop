part of 'm3e_chips.dart';

/// Moves focus between chips with the arrow keys.
///
/// The group does not lay out its children. Backspace and Delete remove the
/// focused input chip. Arrows do not change selection.
class M3EChipGroup extends StatefulWidget {
  /// Creates a chip focus group around [child].
  const M3EChipGroup({required this.child, this.groupLabel, super.key});

  /// Accessibility label for the set of chips.
  final String? groupLabel;

  /// Chips and the layout around them.
  final Widget child;

  @override
  State<M3EChipGroup> createState() => _M3EChipGroupState();
}

/// One chip registered with [M3EChipGroup].
class _M3EChipGroupRegistration {
  _M3EChipGroupRegistration._(this._state, this.focusNode);

  final _M3EChipGroupState _state;
  final FocusNode focusNode;

  bool _enabled = false;
  VoidCallback? _onDeleted;
  FocusNode? _removeNode;

  /// Updates interaction flags for this chip.
  void sync({
    required bool enabled,
    required VoidCallback? onDeleted,
    required FocusNode? removeNode,
  }) {
    _enabled = enabled;
    _onDeleted = onDeleted;
    _removeNode = removeNode;
  }

  bool get _hasFocus => focusNode.hasFocus || (_removeNode?.hasFocus ?? false);

  void _dispose() {
    _state._members.remove(this);
  }
}

/// Exposes [M3EChipGroup] registration to descendant chips.
class _M3EChipGroupScope extends InheritedWidget {
  /// Creates a scope for one chip group.
  const _M3EChipGroupScope({required this._state, required super.child});

  final _M3EChipGroupState _state;

  /// The nearest chip group, if any.
  static _M3EChipGroupScope? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<_M3EChipGroupScope>();
  }

  /// Registers [focusNode] and returns a handle the chip keeps updated.
  _M3EChipGroupRegistration attach(FocusNode focusNode) {
    return _state._attach(focusNode);
  }

  @override
  bool updateShouldNotify(_M3EChipGroupScope oldWidget) =>
      _state != oldWidget._state;
}

class _M3EChipGroupState extends State<M3EChipGroup> {
  final List<_M3EChipGroupRegistration> _members =
      <_M3EChipGroupRegistration>[];
  late final _ChipGroupTraversalPolicy _policy = _ChipGroupTraversalPolicy(
    this,
  );
  late final _ChipGroupShortcutManager _shortcuts = _ChipGroupShortcutManager(
    state: this,
    shortcuts: <ShortcutActivator, Intent>{
      const SingleActivator(LogicalKeyboardKey.arrowDown): VoidCallbackIntent(
        _moveNext,
      ),
      const SingleActivator(LogicalKeyboardKey.arrowRight): VoidCallbackIntent(
        _moveNext,
      ),
      const SingleActivator(LogicalKeyboardKey.arrowUp): VoidCallbackIntent(
        _movePrevious,
      ),
      const SingleActivator(LogicalKeyboardKey.arrowLeft): VoidCallbackIntent(
        _movePrevious,
      ),
      const SingleActivator(LogicalKeyboardKey.backspace): VoidCallbackIntent(
        _deleteFocused,
      ),
      const SingleActivator(LogicalKeyboardKey.delete): VoidCallbackIntent(
        _deleteFocused,
      ),
    },
  );

  @override
  void dispose() {
    _shortcuts.dispose();
    super.dispose();
  }

  void _moveNext() => _move(1);

  void _movePrevious() => _move(-1);

  _M3EChipGroupRegistration _attach(FocusNode focusNode) {
    final registration = _M3EChipGroupRegistration._(this, focusNode);
    _members.add(registration);
    return registration;
  }

  bool _owns(FocusNode node) {
    for (final _M3EChipGroupRegistration member in _members) {
      if (member.focusNode == node || member._removeNode == node) {
        return true;
      }
    }
    return false;
  }

  bool _memberHasFocus() {
    for (final _M3EChipGroupRegistration member in _members) {
      if (member._hasFocus) {
        return true;
      }
    }
    return false;
  }

  List<_M3EChipGroupRegistration> _ordered() {
    final members =
        _members
            .where((_M3EChipGroupRegistration member) => member._enabled)
            .toList()
          ..sort((_M3EChipGroupRegistration a, _M3EChipGroupRegistration b) {
            final ao = a.focusNode.offset;
            final bo = b.focusNode.offset;
            final dy = ao.dy.compareTo(bo.dy);
            if (dy != 0) {
              return dy;
            }
            return ao.dx.compareTo(bo.dx);
          });
    return members;
  }

  void _move(int delta) {
    final ordered = _ordered();
    if (ordered.isEmpty) {
      return;
    }
    var index = ordered.indexWhere(
      (_M3EChipGroupRegistration member) => member._hasFocus,
    );
    if (index < 0) {
      index = delta > 0 ? -1 : 0;
    }
    var next = index + delta;
    if (next < 0) {
      next = ordered.length - 1;
    } else if (next >= ordered.length) {
      next = 0;
    }
    ordered[next].focusNode.requestFocus();
  }

  void _deleteFocused() {
    for (final _M3EChipGroupRegistration member in _members) {
      if (member._hasFocus) {
        member._onDeleted?.call();
        return;
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return _M3EChipGroupScope(
      state: this,
      child: Semantics(
        container: true,
        explicitChildNodes: true,
        label: widget.groupLabel,
        child: Shortcuts.manager(
          manager: _shortcuts,
          child: FocusTraversalGroup(policy: _policy, child: widget.child),
        ),
      ),
    );
  }
}

class _ChipGroupShortcutManager extends ShortcutManager {
  _ChipGroupShortcutManager({required super.shortcuts, required this.state});

  final _M3EChipGroupState state;

  @override
  KeyEventResult handleKeypress(BuildContext context, KeyEvent event) {
    if (!state._memberHasFocus()) {
      return KeyEventResult.ignored;
    }
    return super.handleKeypress(context, event);
  }
}

class _ChipGroupTraversalPolicy extends ReadingOrderTraversalPolicy {
  _ChipGroupTraversalPolicy(this._group);

  final _M3EChipGroupState _group;

  @override
  bool inDirection(FocusNode currentNode, TraversalDirection direction) {
    if (!_group._owns(currentNode)) {
      return super.inDirection(currentNode, direction);
    }
    switch (direction) {
      case TraversalDirection.down:
      case TraversalDirection.right:
        _group._move(1);
        return true;
      case TraversalDirection.up:
      case TraversalDirection.left:
        _group._move(-1);
        return true;
    }
  }
}
