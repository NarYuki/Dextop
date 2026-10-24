import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../selection/components/m3e_selection_scope.dart';
import '../../selection/controllers/m3e_selection_controller.dart';
import '../styles/m3e_list_reorder_state.dart';
import '../styles/m3e_list_selection_state.dart';
import '../utils/m3e_list_selection_binding.dart';
import 'm3e_list_feature_scope.dart';

/// Owns or binds selection state and exposes [M3EListFeatureScope].
class M3EListFeatureHost extends StatefulWidget {
  /// Creates a list feature host.
  const M3EListFeatureHost({
    required this.itemCount,
    required this.selection,
    required this.reorder,
    required this.child,
    this.selectionController,
    this.onSelectionChanged,
    this.selectionState,
    this.reorderState,
    super.key,
  });

  /// Top-level item count.
  final int itemCount;

  /// Whether selection is enabled.
  final bool selection;

  /// Whether reorder is enabled.
  final bool reorder;

  /// Optional external controller (ignored when ancestor scope exists).
  final M3ESelectionController? selectionController;

  /// Fired when selection indices change.
  final ValueChanged<Set<int>>? onSelectionChanged;

  /// Optional selection state override.
  final M3EListSelectionState? selectionState;

  /// Optional reorder state override.
  final M3EListReorderState? reorderState;

  /// List body.
  final Widget child;

  @override
  State<M3EListFeatureHost> createState() => _M3EListFeatureHostState();
}

class _M3EListFeatureHostState extends State<M3EListFeatureHost> {
  M3ESelectionController? _owned;
  M3ESelectionController? _listening;
  void _onControllerTick() {
    if (mounted) {
      setState(() {});
    }
  }

  M3ESelectionController? get _active {
    if (!widget.selection) {
      return null;
    }
    final M3ESelectionScope? scope = M3ESelectionScope.maybeOf(context);
    if (scope != null) {
      return scope.controller;
    }
    if (widget.selectionController != null) {
      return widget.selectionController;
    }
    return _owned ??= M3ESelectionController();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncListener();
  }

  @override
  void didUpdateWidget(M3EListFeatureHost oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.selection != widget.selection ||
        oldWidget.selectionController != widget.selectionController) {
      _syncListener();
    }
  }

  void _syncListener() {
    final M3ESelectionController? next = _active;
    if (identical(next, _listening)) {
      return;
    }
    _listening?.removeListener(_onControllerTick);
    _listening = next;
    _listening?.addListener(_onControllerTick);

    // Drop owned controller when an ancestor/external controller is used.
    if (_owned != null && !identical(_owned, next)) {
      _owned!.dispose();
      _owned = null;
    }
  }

  @override
  void dispose() {
    _listening?.removeListener(_onControllerTick);
    _owned?.dispose();
    super.dispose();
  }

  void _toggle(int index) {
    final M3ESelectionController? controller = _active;
    if (controller == null) {
      return;
    }
    final M3EListSelectionState state = mergeListSelectionState(
      theme: M3ETheme.of(context).listTheme.selection,
      override: widget.selectionState,
    );
    applyListSelectionToggle(
      controller: controller,
      index: index,
      mode: state.mode,
      onChanged: widget.onSelectionChanged,
    );
  }

  @override
  Widget build(BuildContext context) {
    _syncListener();
    final listTheme = M3ETheme.of(context).listTheme;
    final M3EListSelectionState selectionState = mergeListSelectionState(
      theme: listTheme.selection,
      override: widget.selectionState,
    );
    final M3EListReorderState reorderState =
        widget.reorderState ?? listTheme.reorder;

    return M3EListFeatureScope(
      selectionEnabled: widget.selection,
      reorderEnabled: widget.reorder,
      selectionState: selectionState,
      reorderState: reorderState,
      controller: _active,
      itemCount: widget.itemCount,
      onToggleSelection: _toggle,
      child: widget.child,
    );
  }
}
