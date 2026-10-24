import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../../foundations/foundations.dart';
import 'components/m3e_menu_popup.dart';
import 'enums/m3e_menu_anchor_position.dart';
import 'enums/m3e_menu_color_style.dart';
import 'enums/m3e_menu_selection_mode.dart';
import 'enums/m3e_menu_variant.dart';
import 'models/m3e_menu_node.dart';
import 'utils/m3e_menu_overlay_rect.dart';

export 'components/m3e_menu_content.dart';
export 'components/m3e_menu_divider.dart';
export 'components/m3e_menu_item.dart';
export 'components/m3e_menu_popup.dart';
export 'enums/m3e_menu_anchor_position.dart';
export 'enums/m3e_menu_color_style.dart';
export 'enums/m3e_menu_item_shape.dart';
export 'enums/m3e_menu_selection_mode.dart';
export 'enums/m3e_menu_variant.dart';
export 'models/m3e_menu_node.dart';
export 'styles/m3e_menu_theme.dart';
export 'utils/m3e_menu_overlay_rect.dart';
export 'utils/m3e_menu_placer.dart';
export 'utils/m3e_menu_spring_motion.dart';

/// Builds the anchor for an [M3EMenu], given a callback to open the menu.
typedef M3EMenuAnchorBuilder = Widget Function(
  BuildContext context,
  VoidCallback open,
);

/// A Material 3 Expressive menu (Compose `DropdownMenu` + `DropdownMenuPopup`).
///
/// Displays a temporary surface of [children] anchored to a widget built by
/// [anchorBuilder]. The menu springs open from the anchor and closes when an
/// entry is chosen or the scrim is tapped.
class M3EMenu extends StatefulWidget {
  /// M3EMenu.
  const M3EMenu({
    required this.anchorBuilder,
    this.children,
    this.entries,
    this.position = M3EMenuAnchorPosition.bottomStart,
    this.colorStyle = M3EMenuColorStyle.standard,
    this.variant = M3EMenuVariant.vertical,
    this.selectionMode = M3EMenuSelectionMode.single,
    this.closeOnSelect = true,
    this.onSelected,
    this.selectedValue,
    super.key,
  }) : assert(
         children != null || entries != null,
         'Provide children or entries.',
       );

  /// Convenience constructor for a flat list of action [M3EMenuEntry]s.
  factory M3EMenu.entries({
    Key? key,
    required M3EMenuAnchorBuilder anchorBuilder,
    required List<M3EMenuEntry> entries,
    M3EMenuAnchorPosition position = M3EMenuAnchorPosition.bottomStart,
    M3EMenuColorStyle colorStyle = M3EMenuColorStyle.standard,
    M3EMenuVariant variant = M3EMenuVariant.vertical,
    M3EMenuSelectionMode selectionMode = M3EMenuSelectionMode.single,
    bool closeOnSelect = true,
    ValueChanged<Object?>? onSelected,
    Object? selectedValue,
  }) {
    return M3EMenu(
      key: key,
      anchorBuilder: anchorBuilder,
      children: entries,
      position: position,
      colorStyle: colorStyle,
      variant: variant,
      selectionMode: selectionMode,
      closeOnSelect: closeOnSelect,
      onSelected: onSelected,
      selectedValue: selectedValue,
    );
  }

  /// anchorBuilder.

  final M3EMenuAnchorBuilder anchorBuilder;

  /// Full menu content tree (items, groups, dividers, submenus).
  final List<M3EMenuNode>? children;

  /// Convenience alias for a flat list of [M3EMenuEntry] action rows.
  final List<M3EMenuEntry>? entries;

  /// position.

  final M3EMenuAnchorPosition position;

  /// Standard (surface) vs vibrant (tertiary) color mapping.
  final M3EMenuColorStyle colorStyle;

  /// Vertical or baseline layout.
  final M3EMenuVariant variant;

  /// Single-select closes according to [closeOnSelect]. Multi-select stays open.
  final M3EMenuSelectionMode selectionMode;

  /// closeOnSelect.

  final bool closeOnSelect;

  /// onSelected.
  final ValueChanged<Object?>? onSelected;

  /// selectedValue.
  final Object? selectedValue;

  List<M3EMenuNode> get _nodes => children ?? entries!;

  @override
  State<M3EMenu> createState() => _M3EMenuState();
}

class _M3EMenuState extends State<M3EMenu> {
  final GlobalKey _anchorKey = GlobalKey();
  final FocusNode _anchorFocus = FocusNode(debugLabel: 'M3EMenuAnchor');
  OverlayEntry? _menuEntry;
  bool _open = false;

  bool get _multi => widget.selectionMode == M3EMenuSelectionMode.multi;

  @override
  void didUpdateWidget(covariant M3EMenu oldWidget) {
    super.didUpdateWidget(oldWidget);
    final OverlayEntry? entry = _menuEntry;
    if (entry == null) {
      return;
    }
    // The parent often setStates from onSelected while this menu is building.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (entry.mounted) {
        entry.markNeedsBuild();
      }
    });
  }

  @override
  void dispose() {
    _anchorFocus.dispose();
    super.dispose();
  }

  Future<void> _openMenu() async {
    if (_open) {
      return;
    }
    final BuildContext? anchorContext = _anchorKey.currentContext;
    final Rect? anchor = anchorContext == null
        ? null
        : m3eOverlayRectFor(anchorContext);
    if (anchor == null) {
      return;
    }
    setState(() => _open = true);

    final bool stayOpen = _multi || !widget.closeOnSelect;
    final result = await showM3EMenu<Object>(
      context: context,
      anchor: anchor,
      children: widget._nodes,
      readChildren: () => widget._nodes,
      position: widget.position,
      colorStyle: widget.colorStyle,
      variant: widget.variant,
      selectionMode: widget.selectionMode,
      closeOnSelect: !stayOpen && widget.closeOnSelect,
      selectedValue: widget.selectedValue,
      callerFocusNode: _focusedAnchorNode(),
      onItemChosen: stayOpen ? widget.onSelected : null,
      onEntry: (OverlayEntry entry) => _menuEntry = entry,
    );

    _menuEntry = null;
    if (mounted) {
      setState(() => _open = false);
    }
    if (result != null && !stayOpen) {
      widget.onSelected?.call(result);
    }
  }

  FocusNode _focusedAnchorNode() {
    final FocusNode? current = FocusManager.instance.primaryFocus;
    if (current == null) {
      return _anchorFocus;
    }
    if (current == _anchorFocus || current.ancestors.contains(_anchorFocus)) {
      return current;
    }
    return _anchorFocus;
  }

  KeyEventResult _onAnchorKey(FocusNode node, KeyEvent event) {
    if (_open || event is! KeyDownEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    final bool openKey =
        key == LogicalKeyboardKey.arrowDown ||
        key == LogicalKeyboardKey.arrowUp ||
        key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.space;
    if (!openKey) {
      return KeyEventResult.ignored;
    }
    _openMenu();
    return KeyEventResult.handled;
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(
      builder: (BuildContext context) {
        return KeyedSubtree(
          key: _anchorKey,
          child: Focus(
            focusNode: _anchorFocus,
            canRequestFocus: false,
            skipTraversal: true,
            onKeyEvent: _onAnchorKey,
            child: widget.anchorBuilder(context, _openMenu),
          ),
        );
      },
    );
  }
}
