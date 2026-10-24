import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/menus/m3e_menus.dart'
    show M3EMenu;
import 'package:material_3_expressive/material_3_expressive.dart' show M3EMenu;

import '../enums/m3e_menu_item_shape.dart';

export '../enums/m3e_menu_item_shape.dart';

/// A node in an [M3EMenu] content tree.
@immutable
sealed class M3EMenuNode {
  const M3EMenuNode();
}

/// Standard action row (Compose `DropdownMenuItem`).
@immutable
class M3EMenuEntry extends M3EMenuNode {
  /// M3EMenuEntry.
  const M3EMenuEntry({
    required this.label,
    this.leading,
    this.trailing,
    this.trailingText,
    this.badge,
    this.supportingText,
    this.onPressed,
    this.enabled = true,
    this.isDestructive = false,
    this.value,
    this.shape = M3EMenuItemShape.standalone,
  });

  /// label.

  final String label;

  /// leading.
  final Widget? leading;

  /// trailing.
  final Widget? trailing;

  /// Shortcut / secondary trailing label (e.g. `⌘C`).
  final String? trailingText;

  /// Optional status chip before trailing icon/text.
  final Widget? badge;

  /// supportingText.

  final String? supportingText;

  /// onPressed.
  final VoidCallback? onPressed;

  /// enabled.
  final bool enabled;

  /// isDestructive.
  final bool isDestructive;

  /// Optional result returned by `showM3EMenu` when this entry is chosen.
  final Object? value;

  /// shape.

  final M3EMenuItemShape shape;
}

/// Single-select row with a trailing check when selected.
@immutable
class M3EMenuSelectable extends M3EMenuNode {
  /// M3EMenuSelectable.
  const M3EMenuSelectable({
    required this.label,
    required this.value,
    this.leading,
    this.trailing,
    this.trailingText,
    this.badge,
    this.supportingText,
    this.selected = false,
    this.onPressed,
    this.enabled = true,
    this.shape = M3EMenuItemShape.standalone,
  });

  /// label.

  final String label;

  /// value.
  final Object value;

  /// leading.
  final Widget? leading;

  /// trailing.
  final Widget? trailing;

  /// trailingText.
  final String? trailingText;

  /// badge.
  final Widget? badge;

  /// supportingText.
  final String? supportingText;

  /// selected.
  final bool selected;

  /// onPressed.
  final VoidCallback? onPressed;

  /// enabled.
  final bool enabled;

  /// shape.
  final M3EMenuItemShape shape;
}

/// Toggleable row (Compose checked menu item).
@immutable
class M3EMenuToggleable extends M3EMenuNode {
  /// M3EMenuToggleable.
  const M3EMenuToggleable({
    required this.label,
    required this.checked,
    this.leading,
    this.trailing,
    this.trailingText,
    this.badge,
    this.supportingText,
    this.onChanged,
    this.enabled = true,
    this.shape = M3EMenuItemShape.standalone,
  });

  /// label.

  final String label;

  /// checked.
  final bool checked;

  /// leading.
  final Widget? leading;

  /// trailing.
  final Widget? trailing;

  /// trailingText.
  final String? trailingText;

  /// badge.
  final Widget? badge;

  /// supportingText.
  final String? supportingText;

  /// onChanged.
  final ValueChanged<bool>? onChanged;

  /// enabled.
  final bool enabled;

  /// shape.
  final M3EMenuItemShape shape;
}

/// Horizontal rule between menu sections inside one elevated surface.
@immutable
class M3EMenuDivider extends M3EMenuNode {
  /// M3EMenuDivider.
  const M3EMenuDivider();
}

/// Elevated surface / subgroup of items with optional section [label].
///
/// Each top-level [M3EMenuGroup] becomes its own elevated menu container;
/// consecutive non-group nodes share one implicit surface.
@immutable
class M3EMenuGroup extends M3EMenuNode {
  /// M3EMenuGroup.
  const M3EMenuGroup({required this.children, this.label});

  /// Alias that names the item list [entries] (maps to [children]).
  const M3EMenuGroup.entries({required List<M3EMenuNode> entries, this.label})
    : children = entries;

  /// label.

  final String? label;

  /// children.
  final List<M3EMenuNode> children;
}

/// Cascading submenu opened from a parent row.
@immutable
class M3EMenuSubmenu extends M3EMenuNode {
  /// M3EMenuSubmenu.
  const M3EMenuSubmenu({
    required this.label,
    required this.children,
    this.leading,
    this.badge,
    this.enabled = true,
    this.shape = M3EMenuItemShape.standalone,
  });

  /// label.

  final String label;

  /// leading.
  final Widget? leading;

  /// badge.
  final Widget? badge;

  /// children.
  final List<M3EMenuNode> children;

  /// enabled.
  final bool enabled;

  /// shape.
  final M3EMenuItemShape shape;
}

/// Row whose body is an arbitrary [child] (host-provided content).
@immutable
class M3EMenuWidget extends M3EMenuNode {
  /// M3EMenuWidget.
  const M3EMenuWidget({
    required this.child,
    this.value,
    this.onPressed,
    this.enabled = true,
    this.selected = false,
    this.semanticLabel,
    this.shape = M3EMenuItemShape.standalone,
  });

  /// child.

  final Widget child;

  /// value.
  final Object? value;

  /// onPressed.
  final VoidCallback? onPressed;

  /// enabled.
  final bool enabled;

  /// selected.
  final bool selected;

  /// semanticLabel.
  final String? semanticLabel;

  /// shape.
  final M3EMenuItemShape shape;
}

/// Partitions top-level menu nodes into elevated surface groups.
List<M3EMenuGroup> m3eMenuPartitionSurfaces(List<M3EMenuNode> nodes) {
  if (nodes.isEmpty) {
    return const <M3EMenuGroup>[];
  }
  final surfaces = <M3EMenuGroup>[];
  var flatRun = <M3EMenuNode>[];

  void flushFlat() {
    if (flatRun.isEmpty) {
      return;
    }
    surfaces.add(M3EMenuGroup(children: List<M3EMenuNode>.of(flatRun)));
    flatRun = <M3EMenuNode>[];
  }

  for (final node in nodes) {
    if (node is M3EMenuGroup) {
      flushFlat();
      surfaces.add(node);
    } else {
      flatRun.add(node);
    }
  }
  flushFlat();
  return surfaces;
}
