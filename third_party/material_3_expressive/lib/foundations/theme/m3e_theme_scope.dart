import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart' show Brightness, ColorScheme;

import '../color/m3e_color_scheme.dart';
import 'm3e_dynamic_color_host.dart';
import 'm3e_resolved_theme.dart';
import 'm3e_theme_controller.dart';
import 'm3e_theme_data.dart';

/// Hosts adaptive theme listeners and resolves [M3EThemeData] for components.
class M3EThemeScope extends StatefulWidget {
  /// Creates an adaptive theme scope for [child].
  const M3EThemeScope({
    required this.baseData,
    required this.controller,
    required this.child,
    this.initialTheme,
    this.autoTheming,
    this.dynamicColoring,
    super.key,
  });

  /// Light-oriented template used to derive light/dark resolved themes.
  final M3EThemeData baseData;

  /// Manual brightness / invert controller for this scope.
  final M3EThemeController controller;

  /// The subtree under this adaptive scope.
  final Widget child;

  /// Initial brightness when not following the system.
  final Brightness? initialTheme;

  /// When true, follows platform brightness (with optional invert).
  final bool? autoTheming;

  /// When true, applies device dynamic color to the active scheme.
  final bool? dynamicColoring;

  /// Returns the nearest scope, if any.
  static M3EThemeScopeState? maybeOf(BuildContext context) {
    final _InheritedM3EThemeScope? inherited = context
        .getInheritedWidgetOfExactType<_InheritedM3EThemeScope>();
    return inherited?.scopeState;
  }

  /// Resolves theme from the nearest scope, or null when none is present.
  static M3EThemeData? resolveOf(BuildContext context) {
    return maybeOf(context)?.resolve(context);
  }

  /// Returns the nearest adaptive [M3EThemeController], if any.
  static M3EThemeController? controllerOf(BuildContext context) {
    return maybeOf(context)?.controller;
  }

  /// Resolves theme for a component, registering an inherited dependency.
  static M3EThemeData? resolveForComponent(BuildContext context) {
    final _InheritedM3EThemeScope? inherited = context
        .dependOnInheritedWidgetOfExactType<_InheritedM3EThemeScope>();
    return inherited?.resolve(context);
  }

  @override
  /// Creates the mutable state for this widget.
  State<M3EThemeScope> createState() => M3EThemeScopeState();
}

/// State for [M3EThemeScope] that resolves brightness and dynamic color.
class M3EThemeScopeState extends State<M3EThemeScope> {
  late M3EThemeData _cachedDarkTemplate;

  /// Light-oriented template from the widget.
  M3EThemeData get baseData => widget.baseData;

  /// Adaptive theme controller for this scope.
  M3EThemeController get controller => widget.controller;

  /// Initial brightness from the widget.
  Brightness? get initialTheme => widget.initialTheme;

  /// Whether platform brightness is followed.
  bool get autoTheming => widget.autoTheming ?? false;

  /// Whether device dynamic color is applied.
  bool get dynamicColoring => widget.dynamicColoring ?? false;

  /// Light template (same as [baseData]).
  M3EThemeData get lightTemplate => widget.baseData;

  /// Cached dark template derived from [baseData].
  M3EThemeData get darkTemplate => _cachedDarkTemplate;

  @override
  /// Caches the dark template derived from [baseData].
  void initState() {
    super.initState();
    _cachedDarkTemplate = widget.baseData.deriveDarkTemplate();
  }

  @override
  /// Refreshes the dark template when [baseData] changes.
  void didUpdateWidget(M3EThemeScope oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.baseData != widget.baseData) {
      _cachedDarkTemplate = widget.baseData.deriveDarkTemplate();
    }
  }

  /// Resolves effective brightness for the current adaptive rules.
  Brightness resolveBrightness(BuildContext context) {
    return widget.controller.resolveBrightness(
      MediaQuery.platformBrightnessOf(context),
      autoTheming: autoTheming,
      initialTheme: initialTheme,
    );
  }

  /// Resolves [M3EThemeData] for the current brightness and optional dynamics.
  M3EThemeData resolve(
    BuildContext context, {
    ColorScheme? lightDynamic,
    ColorScheme? darkDynamic,
  }) {
    final Brightness brightness = resolveBrightness(context);
    final M3EThemeData template = brightness == Brightness.dark
        ? darkTemplate
        : lightTemplate;

    if (!dynamicColoring) {
      return template;
    }

    final dynamicScheme = brightness == Brightness.dark
        ? darkDynamic
        : lightDynamic;
    if (dynamicScheme == null) {
      return template;
    }

    final ColorScheme harmonizedDynamic = dynamicScheme.harmonized();
    final M3EColorScheme m3eScheme = M3EColorScheme.fromColorScheme(
      harmonizedDynamic,
    ).harmonized();

    return template.withColorScheme(m3eScheme);
  }

  Widget _buildInheritedScope(
    BuildContext context, {
    ColorScheme? lightDynamic,
    ColorScheme? darkDynamic,
  }) {
    final Brightness? manualBrightness = widget.controller.brightnessOverride;
    final Brightness? platformBrightness = autoTheming
        ? MediaQuery.platformBrightnessOf(context)
        : null;
    final M3EThemeData resolved = resolve(
      context,
      lightDynamic: lightDynamic,
      darkDynamic: darkDynamic,
    );

    return _InheritedM3EThemeScope(
      scopeState: this,
      manualBrightness: manualBrightness,
      platformBrightness: platformBrightness,
      invertPlatformBrightness: widget.controller.invertPlatformBrightness,
      lightDynamic: lightDynamic,
      darkDynamic: darkDynamic,
      child: M3EResolvedTheme(data: resolved, child: widget.child),
    );
  }

  @override
  /// Builds the adaptive inherited scope, optionally with dynamic color.
  Widget build(BuildContext context) {
    if (dynamicColoring) {
      return M3EDynamicColorHost(
        builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
          return ListenableBuilder(
            listenable: widget.controller,
            builder: (BuildContext context, Widget? _) {
              return _buildInheritedScope(
                context,
                lightDynamic: lightDynamic,
                darkDynamic: darkDynamic,
              );
            },
          );
        },
      );
    }

    return ListenableBuilder(
      listenable: widget.controller,
      builder: (BuildContext context, Widget? _) {
        return _buildInheritedScope(context);
      },
    );
  }
}

class _InheritedM3EThemeScope extends InheritedWidget {
  const _InheritedM3EThemeScope({
    required this.scopeState,
    required this.manualBrightness,
    required this.platformBrightness,
    required this.invertPlatformBrightness,
    required this.lightDynamic,
    required this.darkDynamic,
    required super.child,
  });

  final M3EThemeScopeState scopeState;
  final Brightness? manualBrightness;
  final Brightness? platformBrightness;
  final bool invertPlatformBrightness;
  final ColorScheme? lightDynamic;
  final ColorScheme? darkDynamic;

  M3EThemeData resolve(BuildContext context) {
    return scopeState.resolve(
      context,
      lightDynamic: lightDynamic,
      darkDynamic: darkDynamic,
    );
  }

  @override
  bool updateShouldNotify(_InheritedM3EThemeScope oldWidget) {
    return scopeState != oldWidget.scopeState ||
        manualBrightness != oldWidget.manualBrightness ||
        platformBrightness != oldWidget.platformBrightness ||
        invertPlatformBrightness != oldWidget.invertPlatformBrightness ||
        lightDynamic != oldWidget.lightDynamic ||
        darkDynamic != oldWidget.darkDynamic;
  }
}
