import 'package:material_3_expressive/components/split_buttons/styles/m3e_split_button_decoration.dart'
    show M3ESplitButtonDecoration;

/// Menu presentation style used by [M3ESplitButtonDecoration.menuStyle].
enum M3ESplitButtonMenuStyle {
  /// Spring-animated popup menu anchored to the trailing segment.
  popup,

  /// Modal bottom sheet menu.
  bottomSheet,

  /// Native Flutter popup menu.
  native,
}
