import 'package:flutter/widgets.dart';

import '../enums/m3e_button_enums.dart';
import 'm3e_button_decoration.dart';

/// Default [M3EButtonDecoration] for `M3EButton`s below it.
///
/// A button's own `decoration` is merged on top, field by field. Containers
/// such as dialogs use this to style the buttons they are given.
class M3EButtonDecorationScope extends InheritedWidget {
  /// M3EButtonDecorationScope.
  const M3EButtonDecorationScope({
    required this.decoration,
    required super.child,
    this.styles,
    super.key,
  });

  /// Decoration applied to descendant buttons.
  final M3EButtonDecoration decoration;

  /// Button styles the scope applies to. Null applies to every style.
  final Set<M3EButtonStyle>? styles;

  /// Scope decoration for a button of [style], or null.
  static M3EButtonDecoration? maybeOf(
    BuildContext context,
    M3EButtonStyle style,
  ) {
    final M3EButtonDecorationScope? scope = context
        .dependOnInheritedWidgetOfExactType<M3EButtonDecorationScope>();
    if (scope == null || !(scope.styles?.contains(style) ?? true)) {
      return null;
    }
    return scope.decoration;
  }

  @override
  bool updateShouldNotify(M3EButtonDecorationScope oldWidget) =>
      decoration != oldWidget.decoration || styles != oldWidget.styles;
}
