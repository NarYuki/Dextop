import 'package:flutter/widgets.dart';

import 'm3e_menu_key_scope.dart';
import 'm3e_menu_key_target.dart';

/// Registers a menu row with [M3EMenuKeyScope] for arrow and typeahead keys.
class M3EMenuKeyRegistration extends StatefulWidget {
  /// Creates a registration around [builder].
  const M3EMenuKeyRegistration({
    required this.label,
    required this.enabled,
    required this.builder,
    this.onOpenSubmenu,
    super.key,
  });

  /// Typeahead label.
  final String label;

  /// Whether the row can take keyboard focus.
  final bool enabled;

  /// Called for Left/Right when this row
  /// opens a submenu.
  final VoidCallback? onOpenSubmenu;

  /// Builds the row with the focus node owned by this registration.
  final Widget Function(FocusNode node) builder;

  @override
  State<M3EMenuKeyRegistration> createState() => _M3EMenuKeyRegistrationState();
}

class _M3EMenuKeyRegistrationState extends State<M3EMenuKeyRegistration> {
  final FocusNode _node = FocusNode();
  List<M3EMenuKeyTarget>? _targets;
  M3EMenuKeyTarget? _target;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_target != null) {
      return;
    }
    final M3EMenuKeyScope? scope = M3EMenuKeyScope.maybeOf(context);
    if (scope == null) {
      return;
    }
    final target = M3EMenuKeyTarget(
      node: _node,
      label: widget.label,
      enabled: widget.enabled,
      onOpenSubmenu: widget.onOpenSubmenu,
    );
    scope.targets.add(target);
    _targets = scope.targets;
    _target = target;
  }

  @override
  void didUpdateWidget(covariant M3EMenuKeyRegistration oldWidget) {
    super.didUpdateWidget(oldWidget);
    final M3EMenuKeyTarget? target = _target;
    if (target == null) {
      return;
    }
    target
      ..label = widget.label
      ..enabled = widget.enabled
      ..onOpenSubmenu = widget.onOpenSubmenu;
  }

  @override
  void dispose() {
    final M3EMenuKeyTarget? target = _target;
    if (target != null) {
      _targets?.remove(target);
    }
    _node.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return widget.builder(_node);
  }
}
