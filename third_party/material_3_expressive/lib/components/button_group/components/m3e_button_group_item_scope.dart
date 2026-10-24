import 'package:flutter/widgets.dart';

/// Per-action position inside a button group.
///
/// Used so buttons can lock outer corners (first / last) and adapt when
/// neighbour-squish squeezes them.
@immutable
class M3EButtonGroupItemScope extends InheritedWidget {
  /// Creates an item scope for [index] within a group of [count] actions.
  const M3EButtonGroupItemScope({
    super.key,
    required super.child,
    required this.index,
    required this.count,
    this.visualIsFirst,
    this.visualIsLast,
  });

  /// Optional visual-first override (e.g. RTL).
  final bool? visualIsFirst;

  /// Optional visual-last override (e.g. RTL).
  final bool? visualIsLast;

  /// Zero-based index in the group's action list.
  final int index;

  /// Number of visible actions in this layout pass.
  final int count;

  /// Whether this is the first visual segment.
  bool get isFirst => visualIsFirst ?? index == 0;

  /// Whether this is the last visual segment.
  bool get isLast => visualIsLast ?? index == count - 1;

  /// Whether this is the only action in the group.
  bool get isOnly => count == 1;

  /// Nearest item scope, or null outside a group item.
  static M3EButtonGroupItemScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<M3EButtonGroupItemScope>();

  /// Nearest item scope; throws if none is found.
  static M3EButtonGroupItemScope of(BuildContext context) {
    final scope = maybeOf(context);
    assert(
      scope != null,
      'M3EButtonGroupItemScope.of() called with no item scope ancestor.\n'
      'Each action in M3EButtonGroup is wrapped automatically.',
    );
    return scope!;
  }

  @override
  bool updateShouldNotify(M3EButtonGroupItemScope old) =>
      index != old.index ||
      count != old.count ||
      visualIsFirst != old.visualIsFirst ||
      visualIsLast != old.visualIsLast;
}
