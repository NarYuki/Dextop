import 'package:flutter/widgets.dart';

import '../../buttons/enums/m3e_button_enums.dart';
import '../enums/m3e_button_group_enums.dart';

/// Ambient group configuration for descendant buttons.
///
/// Inserted by `M3EButtonGroup`. Descendants read [maybeOf] / [of] for
/// [type], [shape], [size], [density], and [direction].
@immutable
class M3EButtonGroupScope extends InheritedWidget {
  /// Creates a scope with the group's shared configuration.
  const M3EButtonGroupScope({
    super.key,
    required super.child,
    required this.type,
    required this.shape,
    required this.size,
    required this.density,
    required this.direction,
  });

  /// Visual connection variant of the enclosing group.
  final M3EButtonGroupType type;

  /// Default corner strategy for actions in the group.
  final M3EButtonShape shape;

  /// Size token applied to every action.
  final M3EButtonSize size;

  /// Density level applied to container height.
  final M3EButtonGroupDensity density;

  /// Main layout axis of the group.
  final Axis direction;

  /// Whether [type] is [M3EButtonGroupType.connected].
  bool get isConnected => type == M3EButtonGroupType.connected;

  /// Nearest scope, or null if this context is outside a group.
  static M3EButtonGroupScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<M3EButtonGroupScope>();

  /// Nearest scope; throws if none is found.
  static M3EButtonGroupScope of(BuildContext context) {
    final scope = maybeOf(context);
    assert(
      scope != null,
      'M3EButtonGroupScope.of() called with no M3EButtonGroupScope ancestor.\n'
      'Ensure the widget is a descendant of M3EButtonGroup.',
    );
    return scope!;
  }

  @override
  bool updateShouldNotify(M3EButtonGroupScope old) =>
      type != old.type ||
      shape != old.shape ||
      size != old.size ||
      density != old.density ||
      direction != old.direction;
}
