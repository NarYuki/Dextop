import 'package:material_3_expressive/components/menus/m3e_menus.dart'
    show M3EMenu;
import 'package:material_3_expressive/material_3_expressive.dart' show M3EMenu;

/// How many items an [M3EMenu] may keep selected.
enum M3EMenuSelectionMode {
  /// One selected item. Choosing an item closes the menu when
  /// `closeOnSelect` is true.
  single,

  /// Many selected items. The menu stays open until it is dismissed.
  multi,
}
