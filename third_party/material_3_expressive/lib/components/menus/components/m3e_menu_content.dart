import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../models/m3e_menu_node.dart';
import '../styles/m3e_menu_theme.dart';
import 'm3e_menu_divider.dart';
import 'm3e_menu_node_builders.dart';
import 'm3e_menu_style_scope.dart';

/// Callback when a menu node produces a selection result.
typedef M3EMenuSelectCallback = void Function(Object? value);

/// Callback to open a cascading submenu from an item rect.
typedef M3EMenuOpenSubmenuCallback = void Function(
  Rect anchorRect,
  List<M3EMenuNode> children,
);

/// Renders a tree of [M3EMenuNode]s inside one elevated menu surface.
class M3EMenuContent extends StatelessWidget {
  /// M3EMenuContent.
  const M3EMenuContent({
    required this.nodes,
    required this.onSelect,
    required this.closeOnSelect,
    this.onOpenSubmenu,
    this.selectedValue,
    this.applyGroupShapes = true,
    this.autofocusFirst = true,
    this.sectionLabel,
    super.key,
  });

  /// nodes.

  final List<M3EMenuNode> nodes;

  /// onSelect.
  final M3EMenuSelectCallback onSelect;

  /// closeOnSelect.
  final bool closeOnSelect;

  /// onOpenSubmenu.
  final M3EMenuOpenSubmenuCallback? onOpenSubmenu;

  /// selectedValue.
  final Object? selectedValue;

  /// applyGroupShapes.
  final bool applyGroupShapes;

  /// autofocusFirst.
  final bool autofocusFirst;

  /// Optional section header drawn above [nodes] (callout 9).
  final String? sectionLabel;

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final menuTheme = theme.menuTheme;
    final children = <Widget>[];
    var focused = false;

    if (sectionLabel != null) {
      children.add(_sectionLabel(context, sectionLabel!, menuTheme));
    }

    for (var i = 0; i < nodes.length; i++) {
      final node = nodes[i];
      if (i > 0 && node is M3EMenuGroup) {
        children.add(SizedBox(height: menuTheme.groupSpacing));
      }
      children.addAll(
        _buildNode(
          context,
          node,
          menuTheme,
          requestFocus: autofocusFirst && !focused,
          onFocused: () => focused = true,
        ),
      );
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: _withGaps(children, menuTheme),
    );
  }

  List<Widget> _withGaps(List<Widget> children, M3EMenuTheme menuTheme) {
    if (menuTheme.itemGap <= 0 || children.length < 2) {
      return children;
    }
    final gapped = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i > 0) {
        gapped.add(SizedBox(height: menuTheme.itemGap));
      }
      gapped.add(children[i]);
    }
    return gapped;
  }

  Widget _sectionLabel(
    BuildContext context,
    String label,
    M3EMenuTheme menuTheme,
  ) {
    final theme = M3ETheme.of(context);
    final style = M3EMenuStyleScope.styleOf(context);
    return SizedBox(
      height: menuTheme.groupLabelHeight,
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal:
              menuTheme.stateLayerInset + menuTheme.groupLabelHorizontalPadding,
          vertical: menuTheme.groupLabelVerticalPadding,
        ),
        child: Align(
          alignment: AlignmentDirectional.centerStart,
          child: Text(
            label,
            style: menuTheme.groupLabelStyle(
              theme.typeScale,
              theme.colorScheme,
              style,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ),
    );
  }

  List<Widget> _buildNode(
    BuildContext context,
    M3EMenuNode node,
    M3EMenuTheme menuTheme, {
    required bool requestFocus,
    required VoidCallback onFocused,
  }) {
    return switch (node) {
      M3EMenuDivider() => const <Widget>[M3EMenuDividerWidget()],
      M3EMenuGroup(:final label, :final children) => _buildGroup(
        context,
        label,
        children,
        menuTheme,
        requestFocus: requestFocus,
        onFocused: onFocused,
      ),
      M3EMenuEntry entry => _entryNode(entry, requestFocus, onFocused),
      M3EMenuSelectable item => _selectableNode(
        item,
        menuTheme,
        requestFocus,
        onFocused,
      ),
      M3EMenuToggleable item => _toggleableNode(
        item,
        menuTheme,
        requestFocus,
        onFocused,
      ),
      M3EMenuSubmenu item => _submenuNode(
        item,
        menuTheme,
        requestFocus,
        onFocused,
      ),
      M3EMenuWidget item => _widgetNode(
        context,
        item,
        menuTheme,
        requestFocus,
        onFocused,
      ),
    };
  }

  List<Widget> _entryNode(
    M3EMenuEntry entry,
    bool requestFocus,
    VoidCallback onFocused,
  ) {
    return _leaf(
      requestFocus: requestFocus,
      enabled: entry.enabled,
      onFocused: onFocused,
      child: M3EMenuNodeBuilders.entry(
        entry,
        autofocus: requestFocus && entry.enabled,
        onSelect: onSelect,
      ),
    );
  }

  List<Widget> _selectableNode(
    M3EMenuSelectable item,
    M3EMenuTheme menuTheme,
    bool requestFocus,
    VoidCallback onFocused,
  ) {
    final selected =
        item.selected || (selectedValue != null && selectedValue == item.value);
    return _leaf(
      requestFocus: requestFocus,
      enabled: item.enabled,
      onFocused: onFocused,
      child: M3EMenuNodeBuilders.selectable(
        item,
        selected: selected,
        autofocus: requestFocus && item.enabled,
        menuTheme: menuTheme,
        onSelect: onSelect,
      ),
    );
  }

  List<Widget> _toggleableNode(
    M3EMenuToggleable item,
    M3EMenuTheme menuTheme,
    bool requestFocus,
    VoidCallback onFocused,
  ) {
    return _leaf(
      requestFocus: requestFocus,
      enabled: item.enabled,
      onFocused: onFocused,
      child: M3EMenuNodeBuilders.toggleable(
        item,
        autofocus: requestFocus && item.enabled,
        menuTheme: menuTheme,
        onSelect: onSelect,
      ),
    );
  }

  List<Widget> _submenuNode(
    M3EMenuSubmenu item,
    M3EMenuTheme menuTheme,
    bool requestFocus,
    VoidCallback onFocused,
  ) {
    return _leaf(
      requestFocus: requestFocus,
      enabled: item.enabled,
      onFocused: onFocused,
      child: M3EMenuNodeBuilders.submenu(
        item,
        autofocus: requestFocus && item.enabled,
        menuTheme: menuTheme,
        onOpenSubmenu: onOpenSubmenu,
      ),
    );
  }

  List<Widget> _widgetNode(
    BuildContext context,
    M3EMenuWidget item,
    M3EMenuTheme menuTheme,
    bool requestFocus,
    VoidCallback onFocused,
  ) {
    return _leaf(
      requestFocus: requestFocus,
      enabled: item.enabled,
      onFocused: onFocused,
      child: M3EMenuNodeBuilders.customWidget(
        context,
        item,
        autofocus: requestFocus && item.enabled,
        menuTheme: menuTheme,
        onSelect: (Object? value) {
          if (closeOnSelect || value != null) {
            onSelect(value);
          }
        },
      ),
    );
  }

  List<Widget> _leaf({
    required bool requestFocus,
    required bool enabled,
    required VoidCallback onFocused,
    required Widget child,
  }) {
    if (requestFocus && enabled) {
      onFocused();
    }
    return <Widget>[child];
  }

  List<Widget> _buildGroup(
    BuildContext context,
    String? label,
    List<M3EMenuNode> children,
    M3EMenuTheme menuTheme, {
    required bool requestFocus,
    required VoidCallback onFocused,
  }) {
    final out = <Widget>[];
    if (label != null) {
      out.add(_sectionLabel(context, label, menuTheme));
    }

    final shaped = <M3EMenuNode>[];
    for (var i = 0; i < children.length; i++) {
      final child = children[i];
      if (!applyGroupShapes) {
        shaped.add(child);
        continue;
      }
      shaped.add(
        _withShape(child, menuTheme.shapeForIndex(i, children.length)),
      );
    }

    var focus = requestFocus;
    for (final child in shaped) {
      out.addAll(
        _buildNode(
          context,
          child,
          menuTheme,
          requestFocus: focus,
          onFocused: () {
            focus = false;
            onFocused();
          },
        ),
      );
    }
    return out;
  }

  M3EMenuNode _withShape(M3EMenuNode node, M3EMenuItemShape shape) {
    return switch (node) {
      M3EMenuEntry e => M3EMenuEntry(
        label: e.label,
        leading: e.leading,
        trailing: e.trailing,
        trailingText: e.trailingText,
        badge: e.badge,
        supportingText: e.supportingText,
        onPressed: e.onPressed,
        enabled: e.enabled,
        isDestructive: e.isDestructive,
        value: e.value,
        shape: shape,
      ),
      M3EMenuSelectable e => M3EMenuSelectable(
        label: e.label,
        value: e.value,
        leading: e.leading,
        trailing: e.trailing,
        trailingText: e.trailingText,
        badge: e.badge,
        supportingText: e.supportingText,
        selected: e.selected,
        onPressed: e.onPressed,
        enabled: e.enabled,
        shape: shape,
      ),
      M3EMenuToggleable e => M3EMenuToggleable(
        label: e.label,
        checked: e.checked,
        leading: e.leading,
        trailing: e.trailing,
        trailingText: e.trailingText,
        badge: e.badge,
        supportingText: e.supportingText,
        onChanged: e.onChanged,
        enabled: e.enabled,
        shape: shape,
      ),
      M3EMenuSubmenu e => M3EMenuSubmenu(
        label: e.label,
        children: e.children,
        leading: e.leading,
        badge: e.badge,
        enabled: e.enabled,
        shape: shape,
      ),
      M3EMenuWidget e => M3EMenuWidget(
        child: e.child,
        value: e.value,
        onPressed: e.onPressed,
        enabled: e.enabled,
        selected: e.selected,
        semanticLabel: e.semanticLabel,
        shape: shape,
      ),
      _ => node,
    };
  }
}
