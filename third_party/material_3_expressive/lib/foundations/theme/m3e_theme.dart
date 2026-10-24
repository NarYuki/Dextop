import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart' show Theme;

import 'm3e_resolved_theme.dart';
import 'm3e_theme_controller.dart';
import 'm3e_theme_data.dart';
import 'm3e_theme_scope.dart';

export '../components/m3e_component_theme.dart';
export 'm3e_resolved_theme.dart';
export 'm3e_theme_controller.dart';
export 'm3e_theme_data.dart';
export 'm3e_theme_scope.dart';

/// Provides an [M3EThemeData] to the widget subtree.
///
/// At the app entry point, pass [initialTheme], [autoTheming], [dynamicColoring],
/// and an optional [controller] to enable adaptive theming. Nested instances
/// that only pass [data] continue to provide a static local override.
class M3ETheme extends StatefulWidget {
  /// Creates an expressive theme provider for [child].
  const M3ETheme({
    required this.data,
    required this.child,
    this.initialTheme,
    this.autoTheming,
    this.dynamicColoring,
    this.controller,
    super.key,
  });

  /// Theme tokens and component themes for this subtree.
  final M3EThemeData data;

  /// The subtree under this theme.
  final Widget child;

  /// Initial brightness when adaptive theming is enabled.
  final Brightness? initialTheme;

  /// When true, follows platform brightness (with optional invert via
  /// [controller]).
  final bool? autoTheming;

  /// When true, applies device dynamic color to the active scheme.
  final bool? dynamicColoring;

  /// Optional controller for manual brightness overrides.
  final M3EThemeController? controller;

  /// Whether this instance should host an adaptive [M3EThemeScope].
  bool get usesAdaptiveScope =>
      initialTheme != null ||
      (autoTheming ?? false) ||
      (dynamicColoring ?? false) ||
      controller != null;

  /// Returns the nearest expressive theme, or derives one from Material.
  static M3EThemeData of(BuildContext context) {
    final M3EInheritedTheme? inherited = context
        .dependOnInheritedWidgetOfExactType<M3EInheritedTheme>();
    if (inherited != null) {
      return inherited.data;
    }
    M3EThemeScope.resolveForComponent(context);
    final M3EThemeData? scoped = M3EThemeScope.resolveOf(context);
    if (scoped != null) {
      return scoped;
    }
    return M3EThemeData.fromMaterial(Theme.of(context));
  }

  /// Returns the nearest expressive theme, or null if none is found.
  static M3EThemeData? maybeOf(BuildContext context) {
    final M3EInheritedTheme? inherited = context
        .getInheritedWidgetOfExactType<M3EInheritedTheme>();
    if (inherited != null) {
      return inherited.data;
    }
    return M3EThemeScope.resolveOf(context);
  }

  /// Returns the nearest adaptive [M3EThemeController], if any.
  static M3EThemeController? controllerOf(BuildContext context) {
    return M3EThemeScope.controllerOf(context);
  }

  /// Brightness from the nearest [M3ETheme], else platform brightness.
  static Brightness brightnessOf(BuildContext context) {
    return maybeOf(context)?.brightness ??
        MediaQuery.platformBrightnessOf(context);
  }

  /// Platform from the nearest [M3ETheme], else [Theme] or default.
  static TargetPlatform platformOf(BuildContext context) {
    final M3EThemeData? theme = maybeOf(context);
    if (theme?.platform != null) {
      return theme!.platform!;
    }
    return Theme.of(context).platform;
  }

  @override
  /// Creates the mutable state for this widget.
  State<M3ETheme> createState() => _M3EThemeState();
}

class _M3EThemeState extends State<M3ETheme> {
  M3EThemeController? _internalController;

  M3EThemeController get _effectiveController =>
      widget.controller ?? (_internalController ??= M3EThemeController());

  @override
  Widget build(BuildContext context) {
    if (widget.usesAdaptiveScope) {
      return M3EThemeScope(
        baseData: widget.data,
        initialTheme: widget.initialTheme,
        autoTheming: widget.autoTheming,
        dynamicColoring: widget.dynamicColoring,
        controller: _effectiveController,
        child: widget.child,
      );
    }

    return M3EResolvedTheme(data: widget.data, child: widget.child);
  }
}
