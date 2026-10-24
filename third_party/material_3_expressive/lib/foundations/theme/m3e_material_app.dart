import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import '../type/m3e_typography.dart';
import '../type/m3e_variable_font_config.dart';
import 'm3e_theme.dart';

/// A [MaterialApp] wired to adaptive [M3ETheme] with minimal integration code.
///
/// Owns platform-brightness observation, [M3EThemeController] lifecycle, and
/// keeps Material [ThemeMode] aligned with the resolved M3E brightness.
///
/// System status and navigation bars are transparent. Icon brightness follows
/// the active M3E theme and updates when the theme switches.
///
/// `themeMode` and the core `MaterialApp.builder` are managed internally.
/// Use `appBuilder` to wrap the themed subtree when extra integration layers
/// are needed.
class M3EMaterialApp extends StatefulWidget {
  /// Creates a Material app wired to adaptive [M3ETheme].
  const M3EMaterialApp({
    required this.data,
    required this.home,
    this.fontFamily,
    this.fontFamilyFallback,
    this.package,
    this.fontVariations,
    this.typeScaleMode,
    this.typeface,
    this.variableFont,
    this.autoTheming,
    this.dynamicColoring,
    this.initialTheme,
    this.controller,
    this.drawUnderSystemBars = false,
    this.navigatorKey,
    this.scaffoldMessengerKey,
    this.routes = const <String, WidgetBuilder>{},
    this.initialRoute,
    this.onGenerateRoute,
    this.onGenerateInitialRoutes,
    this.onUnknownRoute,
    this.onNavigationNotification,
    this.navigatorObservers = const <NavigatorObserver>[],
    this.appBuilder,
    this.title = '',
    this.onGenerateTitle,
    this.color,
    this.theme,
    this.darkTheme,
    this.highContrastTheme,
    this.highContrastDarkTheme,
    this.themeAnimationDuration = kThemeAnimationDuration,
    this.themeAnimationCurve = Curves.linear,
    this.locale,
    this.localizationsDelegates,
    this.localeListResolutionCallback,
    this.localeResolutionCallback,
    this.supportedLocales = const <Locale>[Locale('en', 'US')],
    this.debugShowMaterialGrid = false,
    this.showPerformanceOverlay = false,
    this.checkerboardRasterCacheImages = false,
    this.checkerboardOffscreenLayers = false,
    this.showSemanticsDebugger = false,
    this.debugShowCheckedModeBanner = true,
    this.shortcuts,
    this.actions,
    this.restorationScopeId,
    this.scrollBehavior,
    this.themeAnimationStyle,
    super.key,
  });

  /// Expressive theme tokens for the app.
  final M3EThemeData data;

  /// Font family applied to [data]'s type scale when non-null.
  final String? fontFamily;

  /// Fallback families applied with [fontFamily] when non-null.
  final List<String>? fontFamilyFallback;

  /// Font package name applied with [fontFamily] when non-null.
  final String? package;

  /// Variable-font axes applied to [data]'s type scale when non-null.
  final List<FontVariation>? fontVariations;

  /// Replaces [data]'s token tables with static or variable M3 defaults.
  final M3ETypeScaleMode? typeScaleMode;

  /// Brand/plain typeface names applied to display vs body roles.
  final M3ETypefaceConfig? typeface;

  /// Per-role variable-font axis configuration.
  final M3EVariableFontConfig? variableFont;

  /// The default route widget.
  final Widget home;

  /// When true, follows platform brightness.
  final bool? autoTheming;

  /// When true, applies device dynamic color.
  final bool? dynamicColoring;

  /// Initial brightness when not following the system.
  final Brightness? initialTheme;

  /// Optional controller for manual brightness overrides.
  final M3EThemeController? controller;

  /// When true, enables edge-to-edge layout so app content draws under
  /// transparent system bars. When false, system bars remain transparent but
  /// default layout insets apply.
  final bool drawUnderSystemBars;

  /// Key for the root navigator.
  final GlobalKey<NavigatorState>? navigatorKey;

  /// Key for the root scaffold messenger.
  final GlobalKey<ScaffoldMessengerState>? scaffoldMessengerKey;

  /// Named routes table.
  final Map<String, WidgetBuilder> routes;

  /// Name of the first route to show.
  final String? initialRoute;

  /// Called to generate a route for a given [RouteSettings].
  final RouteFactory? onGenerateRoute;

  /// Called to generate initial routes.
  final InitialRouteListFactory? onGenerateInitialRoutes;

  /// Called when [onGenerateRoute] fails to generate a route.
  final RouteFactory? onUnknownRoute;

  /// Called when a navigation notification is received.
  final NotificationListenerCallback<NavigationNotification>?
  onNavigationNotification;

  /// Observers for the root navigator.
  final List<NavigatorObserver> navigatorObservers;

  /// Optional wrapper applied after `M3ETheme` in the internal `MaterialApp.builder`.
  final TransitionBuilder? appBuilder;

  /// A one-line description used by the device for this app.
  final String title;

  /// Callback to generate the app title.
  final GenerateAppTitle? onGenerateTitle;

  /// Primary color used for operating system UI.
  final Color? color;

  /// Optional Material light theme override. Defaults to `data.toThemeData`.
  final ThemeData? theme;

  /// Optional Material dark theme override. Defaults to
  /// `data.deriveDarkTemplate().toThemeData`.
  final ThemeData? darkTheme;

  /// Optional high-contrast light Material theme.
  final ThemeData? highContrastTheme;

  /// Optional high-contrast dark Material theme.
  final ThemeData? highContrastDarkTheme;

  /// Duration of theme change animations.
  final Duration themeAnimationDuration;

  /// Curve of theme change animations.
  final Curve themeAnimationCurve;

  /// Initial locale for the app.
  final Locale? locale;

  /// Delegates for localizing the app.
  final Iterable<LocalizationsDelegate<dynamic>>? localizationsDelegates;

  /// Resolves the locale from the device locale list.
  final LocaleListResolutionCallback? localeListResolutionCallback;

  /// Resolves the locale from the device locale.
  final LocaleResolutionCallback? localeResolutionCallback;

  /// Locales this app supports.
  final Iterable<Locale> supportedLocales;

  /// Turns on a grid overlay for debugging.
  final bool debugShowMaterialGrid;

  /// Turns on the performance overlay.
  final bool showPerformanceOverlay;

  /// Checkerboards images to diagnose raster cache.
  final bool checkerboardRasterCacheImages;

  /// Checkerboards layers rendered to offscreen bitmaps.
  final bool checkerboardOffscreenLayers;

  /// Turns on an overlay showing accessibility info.
  final bool showSemanticsDebugger;

  /// Shows the debug banner in checked mode.
  final bool debugShowCheckedModeBanner;

  /// Default map of shortcuts to intents.
  final Map<ShortcutActivator, Intent>? shortcuts;

  /// Default map of intent types to actions.
  final Map<Type, Action<Intent>>? actions;

  /// Restoration ID for state restoration.
  final String? restorationScopeId;

  /// Scroll behavior for the app.
  final ScrollBehavior? scrollBehavior;

  /// Animation style for theme changes.
  final AnimationStyle? themeAnimationStyle;

  bool get _usesAdaptiveLifecycle =>
      (autoTheming ?? false) ||
      (dynamicColoring ?? false) ||
      controller != null;

  @override
  /// Creates the mutable state for this widget.
  State<M3EMaterialApp> createState() => _M3EMaterialAppState();
}

class _M3EMaterialAppState extends State<M3EMaterialApp>
    with WidgetsBindingObserver {
  M3EThemeController? _internalController;

  M3EThemeController get _effectiveController =>
      widget.controller ?? (_internalController ??= M3EThemeController());

  @override
  void initState() {
    super.initState();
    _applySystemUiMode(widget.drawUnderSystemBars);
    if (widget._usesAdaptiveLifecycle) {
      WidgetsBinding.instance.addObserver(this);
      _effectiveController.addListener(_onThemeControllerChanged);
    }
  }

  @override
  void didUpdateWidget(covariant M3EMaterialApp oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.drawUnderSystemBars != widget.drawUnderSystemBars) {
      _applySystemUiMode(widget.drawUnderSystemBars);
    }
    if (oldWidget.controller != widget.controller) {
      if (oldWidget._usesAdaptiveLifecycle) {
        (oldWidget.controller ?? _internalController)?.removeListener(
          _onThemeControllerChanged,
        );
      }
      if (widget._usesAdaptiveLifecycle) {
        _effectiveController.addListener(_onThemeControllerChanged);
      }
    }
  }

  @override
  void dispose() {
    _applySystemUiMode(false);
    if (widget._usesAdaptiveLifecycle) {
      WidgetsBinding.instance.removeObserver(this);
      _effectiveController.removeListener(_onThemeControllerChanged);
    }
    super.dispose();
  }

  @override
  void didChangePlatformBrightness() {
    setState(() {});
  }

  void _onThemeControllerChanged() {
    setState(() {});
  }

  void _applySystemUiMode(bool drawUnderSystemBars) {
    if (WidgetsBinding.instance.platformDispatcher.views.isEmpty) {
      return;
    }
    if (drawUnderSystemBars) {
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
      return;
    }
    SystemChrome.setEnabledSystemUIMode(
      SystemUiMode.manual,
      overlays: const [SystemUiOverlay.top, SystemUiOverlay.bottom],
    );
  }

  Brightness _effectiveBrightness(BuildContext context) {
    final Brightness platformBrightness =
        MediaQuery.maybePlatformBrightnessOf(context) ??
        WidgetsBinding.instance.platformDispatcher.platformBrightness;

    return _effectiveController.resolveBrightness(
      platformBrightness,
      autoTheming: widget.autoTheming ?? false,
      initialTheme: widget.initialTheme ?? widget.data.brightness,
    );
  }

  SystemUiOverlayStyle _overlayStyleFor(Brightness brightness) {
    final Brightness iconBrightness = brightness == Brightness.dark
        ? Brightness.light
        : Brightness.dark;

    return const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.transparent,
    ).copyWith(
      statusBarIconBrightness: iconBrightness,
      systemNavigationBarIconBrightness: iconBrightness,
      statusBarBrightness: brightness,
      systemStatusBarContrastEnforced: false,
      systemNavigationBarContrastEnforced: false,
    );
  }

  Widget _wrapThemedChild(BuildContext context, Widget themed) {
    if (!widget.drawUnderSystemBars) {
      return themed;
    }
    final MediaQueryData media = MediaQuery.of(context);
    return MediaQuery(
      data: media.copyWith(padding: media.padding.copyWith(bottom: 0)),
      child: themed,
    );
  }

  @override
  Widget build(BuildContext context) {
    final Brightness platformBrightness =
        MediaQuery.maybePlatformBrightnessOf(context) ??
        WidgetsBinding.instance.platformDispatcher.platformBrightness;
    final ThemeMode themeMode = _effectiveController.effectiveThemeMode(
      platformBrightness: platformBrightness,
      autoTheming: widget.autoTheming ?? false,
      initialTheme: widget.initialTheme,
    );
    final SystemUiOverlayStyle overlayStyle = _overlayStyleFor(
      _effectiveBrightness(context),
    );
    SystemChrome.setSystemUIOverlayStyle(overlayStyle);

    M3EThemeData data = widget.data;
    if (widget.typeScaleMode != null) {
      data = data.copyWith(
        typography: M3ETypography.material3(mode: widget.typeScaleMode!),
      );
    }
    data = data.copyWith(
      fontFamily: widget.fontFamily,
      fontFamilyFallback: widget.fontFamilyFallback,
      package: widget.package,
      typeface: widget.typeface,
      variableFont: widget.variableFont,
      fontVariations: widget.fontVariations,
    );

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: overlayStyle,
      child: MaterialApp(
        navigatorKey: widget.navigatorKey,
        scaffoldMessengerKey: widget.scaffoldMessengerKey,
        home: widget.home,
        routes: widget.routes,
        initialRoute: widget.initialRoute,
        onGenerateRoute: widget.onGenerateRoute,
        onGenerateInitialRoutes: widget.onGenerateInitialRoutes,
        onUnknownRoute: widget.onUnknownRoute,
        onNavigationNotification: widget.onNavigationNotification,
        navigatorObservers: widget.navigatorObservers,
        title: widget.title,
        onGenerateTitle: widget.onGenerateTitle,
        color: widget.color,
        theme: widget.theme ?? data.toThemeData(),
        darkTheme: widget.darkTheme ?? data.deriveDarkTemplate().toThemeData(),
        highContrastTheme: widget.highContrastTheme,
        highContrastDarkTheme: widget.highContrastDarkTheme,
        themeMode: themeMode,
        themeAnimationDuration: widget.themeAnimationDuration,
        themeAnimationCurve: widget.themeAnimationCurve,
        locale: widget.locale,
        localizationsDelegates: widget.localizationsDelegates,
        localeListResolutionCallback: widget.localeListResolutionCallback,
        localeResolutionCallback: widget.localeResolutionCallback,
        supportedLocales: widget.supportedLocales,
        debugShowMaterialGrid: widget.debugShowMaterialGrid,
        showPerformanceOverlay: widget.showPerformanceOverlay,
        checkerboardRasterCacheImages: widget.checkerboardRasterCacheImages,
        checkerboardOffscreenLayers: widget.checkerboardOffscreenLayers,
        showSemanticsDebugger: widget.showSemanticsDebugger,
        debugShowCheckedModeBanner: widget.debugShowCheckedModeBanner,
        shortcuts: widget.shortcuts,
        actions: widget.actions,
        restorationScopeId: widget.restorationScopeId,
        scrollBehavior: widget.scrollBehavior,
        themeAnimationStyle: widget.themeAnimationStyle,
        builder: (BuildContext context, Widget? child) {
          Widget themed = M3ETheme(
            data: data,
            autoTheming: widget.autoTheming,
            dynamicColoring: widget.dynamicColoring,
            initialTheme: widget.initialTheme,
            controller: _effectiveController,
            child: child ?? const SizedBox.shrink(),
          );
          themed = _wrapThemedChild(context, themed);
          return widget.appBuilder?.call(context, themed) ?? themed;
        },
      ),
    );
  }
}
