import 'package:flutter/widgets.dart';

import '../controllers/m3e_button_group_overflow_controller.dart';

/// Shares the group's [M3EButtonGroupOverflowController] with descendants.
///
/// Inserted by `M3EButtonGroup` for overflow menu / paging internals.
class M3EButtonGroupProvider extends InheritedWidget {
  /// Creates a provider for [controller].
  const M3EButtonGroupProvider({
    super.key,
    required this.controller,
    required super.child,
  });

  /// Reactive overflow / paging controller for this group.
  final M3EButtonGroupOverflowController controller;

  /// Nearest controller, or null outside a group.
  static M3EButtonGroupOverflowController? maybeOf(BuildContext context) {
    return context
        .dependOnInheritedWidgetOfExactType<M3EButtonGroupProvider>()
        ?.controller;
  }

  /// Nearest controller; throws if none is found.
  static M3EButtonGroupOverflowController of(BuildContext context) {
    final controller = maybeOf(context);
    assert(
      controller != null,
      'M3EButtonGroupProvider.of() called with no M3EButtonGroupProvider '
      'ancestor.\nEnsure the widget is a descendant of M3EButtonGroup.',
    );
    return controller!;
  }

  @override
  bool updateShouldNotify(M3EButtonGroupProvider oldWidget) {
    return controller != oldWidget.controller;
  }
}
