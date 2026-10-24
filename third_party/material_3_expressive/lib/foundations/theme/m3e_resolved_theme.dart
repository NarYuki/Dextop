import 'package:material_ui/material_ui.dart';

import 'm3e_theme.dart';

/// Applies a resolved [M3EThemeData] to a subtree.
///
/// Projects [data] onto Material [Theme] so host widgets that read
/// [Theme.of] (text/icon/color scheme) track the active M3E tokens,
/// including after dynamic color updates.
///
/// Does **not** call [Theme.of]: MaterialApp theme animations would otherwise
/// rebuild this subtree every frame and can drop weighted-sliver children
/// (e.g. the first hero carousel item).
class M3EResolvedTheme extends StatelessWidget {
  /// Creates a resolved theme projection for [child].
  const M3EResolvedTheme({required this.data, required this.child, super.key});

  /// The expressive theme applied to [child].
  final M3EThemeData data;

  /// The subtree under this resolved theme.
  final Widget child;

  @override
  /// Projects [data] onto Material [Theme] and expressive inherited theme.
  Widget build(BuildContext context) {
    final IconThemeData icons = data.resolvedIconTheme;
    final TextStyle baseStyle = (data.textTheme.bodyMedium ?? const TextStyle())
        .copyWith(decoration: TextDecoration.none);

    // Merge without depending on AnimatedTheme. Prefer ancestor Theme data
    // when present so non-token fields (e.g. extensions) are preserved.
    final ThemeData? parentTheme = context
        .findAncestorWidgetOfExactType<Theme>()
        ?.data;
    final ThemeData projected = (parentTheme ?? data.toThemeData()).copyWith(
      colorScheme: data.colorScheme.toColorScheme(),
      textTheme: data.textTheme,
      iconTheme: icons,
      primaryIconTheme: icons.copyWith(color: data.colorScheme.onPrimary),
      splashColor: data.splashColor,
      highlightColor: data.highlightColor,
    );

    return Theme(
      data: projected,
      child: M3EInheritedTheme(
        data: data,
        child: IconTheme(
          data: icons,
          child: DefaultTextStyle(style: baseStyle, child: child),
        ),
      ),
    );
  }
}

/// Inherited theme handle used by [M3ETheme.of].
class M3EInheritedTheme extends InheritedTheme {
  /// Creates an inherited expressive theme.
  const M3EInheritedTheme({
    super.key,
    required this.data,
    required super.child,
  });

  /// The expressive theme for descendants.
  final M3EThemeData data;

  @override
  /// Wraps [child] in a [M3EResolvedTheme] with this [data].
  Widget wrap(BuildContext context, Widget child) {
    return M3EResolvedTheme(data: data, child: child);
  }

  @override
  /// Whether descendants should rebuild when [data] changes.
  bool updateShouldNotify(M3EInheritedTheme oldWidget) =>
      data != oldWidget.data;
}
