import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/menus/m3e_menus.dart'
    show M3EMenu;
import 'package:material_3_expressive/material_3_expressive.dart' show M3EMenu;
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import '../enums/m3e_menu_anchor_position.dart';
import '../enums/m3e_menu_color_style.dart';
import '../enums/m3e_menu_selection_mode.dart';
import '../enums/m3e_menu_variant.dart';
import '../models/m3e_menu_node.dart';
import '../styles/m3e_menu_theme.dart';
import '../utils/m3e_menu_placer.dart';
import '../utils/m3e_menu_spring_motion.dart';
import 'm3e_menu_content.dart';
import 'm3e_menu_key_scope.dart';
import 'm3e_menu_key_target.dart';
import 'm3e_menu_style_scope.dart';

part 'm3e_menu_popup_build.dart';

/// Shows an expressive menu popup anchored to [anchor].
///
/// Returns the selected value from a [M3EMenuSelectable] / [M3EMenuEntry.value],
/// or `null` if dismissed.
Future<T?> showM3EMenu<T>({
  required BuildContext context,
  required Rect anchor,
  required List<M3EMenuNode> children,
  M3EMenuAnchorPosition position = M3EMenuAnchorPosition.bottomEnd,
  M3EMenuColorStyle colorStyle = M3EMenuColorStyle.standard,
  M3EMenuVariant variant = M3EMenuVariant.vertical,
  M3EMenuSelectionMode selectionMode = M3EMenuSelectionMode.single,
  T? selectedValue,
  bool closeOnSelect = true,
  double? preferredWidth,
  FocusNode? callerFocusNode,
  M3EMenuTheme? themeOverride,
  List<M3EMenuNode> Function()? readChildren,
  ValueChanged<Object?>? onItemChosen,
  void Function(OverlayEntry entry)? onEntry,
}) {
  final completer = Completer<T?>();
  final ModalRoute<dynamic>? historyRoute = ModalRoute.of(context);
  late OverlayEntry entry;
  entry = OverlayEntry(
    builder: (BuildContext overlayContext) {
      return M3EMenuPopup<T>(
        anchor: anchor,
        children: readChildren?.call() ?? children,
        position: position,
        colorStyle: colorStyle,
        variant: variant,
        selectionMode: selectionMode,
        selectedValue: selectedValue,
        closeOnSelect: closeOnSelect,
        preferredWidth: preferredWidth,
        callerFocusNode: callerFocusNode,
        themeOverride: themeOverride,
        historyRoute: historyRoute,
        onItemChosen: onItemChosen,
        onSelected: (Object? value) => _completeOnce(completer, value as T?),
        onDismiss: () => _completeOnce(completer, null),
        onRemove: () => entry.remove(),
      );
    },
  );
  Overlay.of(context, rootOverlay: true).insert(entry);
  onEntry?.call(entry);
  return completer.future;
}

void _completeOnce<T>(Completer<T?> completer, T? value) {
  if (!completer.isCompleted) {
    completer.complete(value);
  }
}

/// Overlay surface for [showM3EMenu] / [M3EMenu] (Compose `DropdownMenuPopup`).
class M3EMenuPopup<T> extends StatefulWidget {
  /// M3EMenuPopup.
  const M3EMenuPopup({
    required this.anchor,
    required this.children,
    required this.onSelected,
    required this.onDismiss,
    required this.onRemove,
    this.position = M3EMenuAnchorPosition.bottomEnd,
    this.colorStyle = M3EMenuColorStyle.standard,
    this.variant = M3EMenuVariant.vertical,
    this.selectionMode = M3EMenuSelectionMode.single,
    this.selectedValue,
    this.closeOnSelect = true,
    this.preferredWidth,
    this.callerFocusNode,
    this.themeOverride,
    this.historyRoute,
    this.onItemChosen,
    this.isSubmenu = false,
    super.key,
  });

  /// anchor.

  final Rect anchor;

  /// children.
  final List<M3EMenuNode> children;

  /// position.
  final M3EMenuAnchorPosition position;

  /// colorStyle.
  final M3EMenuColorStyle colorStyle;

  /// variant.
  final M3EMenuVariant variant;

  /// selectionMode.
  final M3EMenuSelectionMode selectionMode;

  /// selectedValue.
  final T? selectedValue;

  /// closeOnSelect.
  final bool closeOnSelect;

  /// preferredWidth.
  final double? preferredWidth;

  /// callerFocusNode.
  final FocusNode? callerFocusNode;

  /// themeOverride.
  final M3EMenuTheme? themeOverride;

  /// Route that owns this popup's local-history entry.
  ///
  /// Submenus reuse the same route so back closes from the inside out.
  final ModalRoute<dynamic>? historyRoute;

  /// Called when a choice should not close the menu.
  final ValueChanged<Object?>? onItemChosen;

  /// True when this popup was opened from another menu row.
  final bool isSubmenu;

  /// onSelected.
  final ValueChanged<Object?> onSelected;

  /// onDismiss.
  final VoidCallback onDismiss;

  /// onRemove.
  final VoidCallback onRemove;

  @override
  State<M3EMenuPopup<T>> createState() => _M3EMenuPopupState<T>();
}

class _M3EMenuPopupState<T> extends State<M3EMenuPopup<T>>
    with SingleTickerProviderStateMixin {
  late final SingleMotionController _expandCtrl;
  bool _isDismissing = false;
  bool _selected = false;
  bool _removed = false;
  late final bool _keyboardActivated;

  OverlayEntry? _submenuEntry;
  M3EOverlayHistory? _overlayHistory;

  final FocusScopeNode _focusScopeNode = FocusScopeNode(
    debugLabel: 'M3EMenuPopup',
  );
  final FocusNode _keyFocus = FocusNode(
    skipTraversal: true,
    debugLabel: 'M3EMenuKeys',
  );
  final ScrollController _scroll = ScrollController();
  final List<M3EMenuKeyTarget> _targets = <M3EMenuKeyTarget>[];
  Timer? _typeaheadTimer;
  String _typeaheadQuery = '';
  bool _didInitialFocus = false;

  M3EMenuTheme _resolvedTheme(BuildContext context) {
    final M3EMenuTheme ambient = M3ETheme.of(context).menuTheme;
    return widget.themeOverride ?? ambient.metricsFor(widget.variant);
  }

  @override
  void initState() {
    super.initState();
    _keyboardActivated = widget.callerFocusNode?.hasFocus ?? false;
    _overlayHistory = M3EOverlayHistory.registerRoute(
      widget.historyRoute,
      onBack: () => _dismiss(),
    );

    _expandCtrl = SingleMotionController(
      motion: M3EMotion.expressiveSpatialDefault.toMotion(),
      vsync: this,
    )..addListener(_onExpandTick);
    if (widget.themeOverride?.openInstantly ?? false) {
      _expandCtrl.value = 1;
    } else {
      _expandCtrl.animateTo(1);
    }

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      final M3EMenuTheme openTheme = _resolvedTheme(context);
      if (openTheme.openInstantly) {
        _expandCtrl.value = 1;
      } else if (_expandCtrl.value < 1) {
        _expandCtrl.motion = openTheme.openMotion.toMotion();
        _expandCtrl.animateTo(1);
      }
      _focusFirstEnabled();
    });
  }

  void _releaseOverlayHistory() {
    final M3EOverlayHistory? history = _overlayHistory;
    _overlayHistory = null;
    history?.release();
  }

  @override
  void dispose() {
    _typeaheadTimer?.cancel();
    _releaseOverlayHistory();
    _expandCtrl
      ..removeListener(_onExpandTick)
      ..dispose();
    _removeSubmenu();
    _focusScopeNode.dispose();
    _keyFocus.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _onExpandTick() {
    if (_isDismissing && !_removed && _expandCtrl.value <= 0.01 && mounted) {
      _removed = true;
      widget.onRemove();
    }
  }

  void _removeSubmenu() {
    _submenuEntry?.remove();
    _submenuEntry = null;
  }

  void _dismiss({bool restoreFocus = false}) {
    if (_isDismissing) {
      return;
    }
    _releaseOverlayHistory();
    _removeSubmenu();
    if (!_selected) {
      widget.onDismiss();
    }
    _isDismissing = true;
    if (_keyboardActivated && restoreFocus) {
      widget.callerFocusNode?.requestFocus();
    }
    if (_resolvedTheme(context).openInstantly) {
      _removed = true;
      widget.onRemove();
      return;
    }
    _expandCtrl.motion = _resolvedTheme(context).closeMotion.toMotion();
    _expandCtrl.animateTo(0);
  }

  void _handleSelect(Object? value) {
    final bool stayOpen =
        widget.selectionMode == M3EMenuSelectionMode.multi ||
        !widget.closeOnSelect;
    if (stayOpen) {
      widget.onItemChosen?.call(value);
      return;
    }
    _selected = true;
    widget.onSelected(value);
    _dismiss(restoreFocus: _focusScopeNode.hasFocus);
  }

  void _openSubmenu(Rect itemRect, List<M3EMenuNode> children) {
    _removeSubmenu();
    final M3EMenuVariant childVariant = widget.variant;
    late OverlayEntry entry;
    entry = OverlayEntry(
      builder: (BuildContext context) {
        return M3EMenuPopup<T>(
          anchor: itemRect,
          children: children,
          position: M3EMenuAnchorPosition.end,
          colorStyle: widget.colorStyle,
          variant: childVariant,
          selectionMode: widget.selectionMode,
          selectedValue: widget.selectedValue,
          closeOnSelect: widget.closeOnSelect,
          callerFocusNode: widget.callerFocusNode,
          themeOverride: widget.themeOverride,
          historyRoute: widget.historyRoute,
          onItemChosen: widget.onItemChosen,
          isSubmenu: true,
          onSelected: (Object? value) {
            _selected = true;
            widget.onSelected(value);
            _dismiss(restoreFocus: _focusScopeNode.hasFocus);
          },
          onDismiss: () {
            entry.remove();
            _submenuEntry = null;
          },
          onRemove: () {
            entry.remove();
            _submenuEntry = null;
          },
        );
      },
    );
    _submenuEntry = entry;
    Overlay.of(context, rootOverlay: true).insert(entry);
  }

  void _focusFirstEnabled() {
    if (_didInitialFocus) {
      return;
    }
    for (final M3EMenuKeyTarget target in _targets) {
      if (target.enabled) {
        target.node.requestFocus();
        _didInitialFocus = true;
        return;
      }
    }
  }

  void _exitMenu({required bool forward}) {
    final FocusNode? caller = widget.callerFocusNode;
    _dismiss();
    if (caller == null) {
      return;
    }
    if (forward) {
      caller.nextFocus();
    } else {
      caller.previousFocus();
    }
  }

  List<M3EMenuKeyTarget> get _enabledTargets => _targets
      .where((M3EMenuKeyTarget target) => target.enabled)
      .toList(growable: false);

  void _move(int delta) {
    final List<M3EMenuKeyTarget> enabled = _enabledTargets;
    if (enabled.isEmpty) {
      return;
    }
    final int current = enabled.indexWhere(
      (M3EMenuKeyTarget target) => target.node.hasFocus,
    );
    final int next = current < 0 ? 0 : (current + delta) % enabled.length;
    enabled[next].node.requestFocus();
  }

  void _openFocusedSubmenu() {
    for (final M3EMenuKeyTarget target in _targets) {
      if (target.node.hasFocus) {
        target.onOpenSubmenu?.call();
        return;
      }
    }
  }

  void _typeahead(String character) {
    _typeaheadTimer?.cancel();
    _typeaheadQuery = (_typeaheadQuery + character).toLowerCase();
    _typeaheadTimer = Timer(const Duration(milliseconds: 500), () {
      _typeaheadQuery = '';
    });
    final List<M3EMenuKeyTarget> enabled = _enabledTargets;
    if (enabled.isEmpty) {
      return;
    }
    final int current = enabled.indexWhere(
      (M3EMenuKeyTarget target) => target.node.hasFocus,
    );
    for (var step = 1; step <= enabled.length; step++) {
      final int index = (current + step) % enabled.length;
      if (enabled[index].label.toLowerCase().startsWith(_typeaheadQuery)) {
        enabled[index].node.requestFocus();
        return;
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_didInitialFocus) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _focusFirstEnabled();
        }
      });
    }
    final M3EThemeData ambient = M3ETheme.of(context);
    final M3EMenuTheme menuTheme = _resolvedTheme(context);
    return M3ETheme(
      data: ambient.copyWith(menuTheme: menuTheme),
      child: _buildPopup(context, menuTheme, ambient.colorScheme),
    );
  }
}
