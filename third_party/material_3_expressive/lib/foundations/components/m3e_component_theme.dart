import 'package:flutter/widgets.dart';

import '../theme/m3e_theme.dart';

/// Registers an adaptive theme dependency for a single M3E component subtree.
///
/// Only descendants of an adaptive [M3ETheme] root with [M3EThemeScope] need
/// this wrapper. When no scope is present the [builder] runs unchanged.
class M3EComponentTheme extends StatelessWidget {
  /// Creates a component theme dependency wrapper.
  const M3EComponentTheme({required this.builder, super.key});

  /// Builds the themed component subtree.
  final WidgetBuilder builder;

  @override
  /// Builds the component subtree with an adaptive theme dependency.
  Widget build(BuildContext context) {
    final M3EInheritedTheme? inherited = context
        .dependOnInheritedWidgetOfExactType<M3EInheritedTheme>();
    if (inherited == null) {
      M3EThemeScope.resolveForComponent(context);
    }
    return builder(context);
  }
}
