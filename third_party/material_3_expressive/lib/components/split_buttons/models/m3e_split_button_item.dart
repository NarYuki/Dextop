import 'package:material_3_expressive/components/split_buttons/m3e_split_buttons.dart'
    show M3ESplitButton;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ESplitButton;

/// A single selectable entry in an [M3ESplitButton] menu.
class M3ESplitButtonItem<T> {
  /// M3ESplitButtonItem.
  const M3ESplitButtonItem({
    required this.value,
    required this.child,
    this.enabled = true,
  });

  /// value.

  final T value;

  /// child.
  final Object child;

  /// enabled.
  final bool enabled;
}
