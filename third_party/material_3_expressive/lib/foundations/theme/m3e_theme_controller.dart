import 'package:material_ui/material_ui.dart'
    show Brightness, ChangeNotifier, ThemeMode;

/// Manual brightness control for an adaptive `M3ETheme` root.
///
/// When `autoTheming` is enabled, `toggleBrightness` inverts relative to the
/// platform brightness instead of locking an absolute override. When
/// `autoTheming` is off, `brightnessOverride` takes precedence until
/// `followSystem` is called.
class M3EThemeController extends ChangeNotifier {
  Brightness? _brightnessOverride;
  bool _invertPlatformBrightness = false;

  /// Active manual brightness, or null when following system/initial rules.
  Brightness? get brightnessOverride => _brightnessOverride;

  /// Whether platform brightness is inverted when `autoTheming` is enabled.
  bool get invertPlatformBrightness => _invertPlatformBrightness;

  /// Material [ThemeMode] derived from the manual override, if any.
  ///
  /// When `autoTheming` is enabled, derive `ThemeMode` from the effective
  /// brightness returned by `resolveBrightness` instead.
  ThemeMode get materialThemeMode {
    if (_brightnessOverride == null) {
      return ThemeMode.system;
    }
    return _brightnessOverride == Brightness.dark
        ? ThemeMode.dark
        : ThemeMode.light;
  }

  /// Resolves effective brightness for an adaptive `M3ETheme` root.
  Brightness resolveBrightness(
    Brightness platformBrightness, {
    required bool autoTheming,
    Brightness? initialTheme,
  }) {
    if (autoTheming) {
      if (_invertPlatformBrightness) {
        return platformBrightness == Brightness.light
            ? Brightness.dark
            : Brightness.light;
      }
      return platformBrightness;
    }
    if (_brightnessOverride != null) {
      return _brightnessOverride!;
    }
    return initialTheme ?? Brightness.light;
  }

  /// Material [ThemeMode] for the given effective [brightness].
  static ThemeMode themeModeFor(Brightness brightness) {
    return brightness == Brightness.dark ? ThemeMode.dark : ThemeMode.light;
  }

  /// Resolves [ThemeMode] from platform brightness and adaptive rules.
  ThemeMode effectiveThemeMode({
    required Brightness platformBrightness,
    required bool autoTheming,
    Brightness? initialTheme,
  }) {
    return themeModeFor(
      resolveBrightness(
        platformBrightness,
        autoTheming: autoTheming,
        initialTheme: initialTheme,
      ),
    );
  }

  /// Locks the theme to [brightness] until [followSystem] is called.
  void setBrightness(Brightness brightness) {
    if (_brightnessOverride == brightness && !_invertPlatformBrightness) {
      return;
    }
    _invertPlatformBrightness = false;
    _brightnessOverride = brightness;
    notifyListeners();
  }

  /// Toggles between light and dark.
  ///
  /// When `autoTheming` is true, inverts relative to `platformBrightness`
  /// without stopping automatic platform detection.
  void toggleBrightness({
    Brightness fallback = Brightness.light,
    bool autoTheming = false,
  }) {
    if (autoTheming) {
      _brightnessOverride = null;
      _invertPlatformBrightness = !_invertPlatformBrightness;
      notifyListeners();
      return;
    }
    final Brightness effective = _brightnessOverride ?? fallback;
    setBrightness(
      effective == Brightness.light ? Brightness.dark : Brightness.light,
    );
  }

  /// Clears manual overrides so `autoTheming` / `initialTheme` apply again.
  void followSystem() {
    if (_brightnessOverride == null && !_invertPlatformBrightness) {
      return;
    }
    _brightnessOverride = null;
    _invertPlatformBrightness = false;
    notifyListeners();
  }
}
