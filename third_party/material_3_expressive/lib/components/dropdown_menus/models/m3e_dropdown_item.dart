import 'dart:convert';

import 'package:flutter/foundation.dart';

/// A single item in an `M3EDropdownMenu`.
///
/// Each item has a display [label] and an associated [value]. Items can be
/// individually [disabled] or [selected] at construction time.
///
/// Items are **immutable** — use [copyWith] to derive new instances when
/// toggling selection or disabled state. This prevents accidental state
/// mutations and ensures the controller can properly detect changes.
@immutable
class M3EDropdownItem<T> {
  /// The text shown in the dropdown list.
  final String label;

  /// The value associated with this item.
  final T value;

  /// Whether this item is disabled and cannot be selected.
  final bool disabled;

  /// Whether this item is currently selected.
  final bool selected;

  /// Creates a [M3EDropdownItem].
  const M3EDropdownItem({
    required this.label,
    required this.value,
    this.disabled = false,
    this.selected = false,
  });

  /// Creates a [M3EDropdownItem] from a [Map].
  ///
  /// The map should contain 'label' and 'value' keys.
  factory M3EDropdownItem.fromMap(Map<String, dynamic> map) {
    return M3EDropdownItem<T>(
      label: map['label'] as String? ?? '',
      value: map['value'] as T,
      disabled: map['disabled'] as bool? ?? false,
      selected: map['selected'] as bool? ?? false,
    );
  }

  /// Converts this item to a [Map].
  Map<String, dynamic> toMap() {
    return {
      'label': label,
      'value': value,
      'disabled': disabled,
      'selected': selected,
    };
  }

  /// Converts this item to a JSON string.
  String toJson() => jsonEncode(toMap());

  /// Returns a copy of this item with the given fields replaced.
  M3EDropdownItem<T> copyWith({
    String? label,
    T? value,
    bool? disabled,
    bool? selected,
  }) {
    return M3EDropdownItem<T>(
      label: label ?? this.label,
      value: value ?? this.value,
      disabled: disabled ?? this.disabled,
      selected: selected ?? this.selected,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is M3EDropdownItem<T> &&
          other.label == label &&
          other.value == value &&
          other.disabled == disabled &&
          other.selected == selected;

  @override
  int get hashCode =>
      label.hashCode ^ value.hashCode ^ disabled.hashCode ^ selected.hashCode;

  @override
  String toString() =>
      'M3EDropdownItem(label: $label, value: $value, disabled: $disabled, selected: $selected)';
}
