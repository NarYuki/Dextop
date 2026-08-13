part of 'main.dart';

class DextopApp extends StatefulWidget {
  const DextopApp({super.key});

  @override
  State<DextopApp> createState() => _DextopAppState();
}

class _DextopAppState extends State<DextopApp> {
  var themeMode = ThemeMode.system;
  bool? setupCompleted;

  @override
  void initState() {
    super.initState();
    loadThemeMode();
    loadSetupState();
  }

  Future<void> loadSetupState() async {
    final preferences = await SharedPreferences.getInstance();
    final completed = preferences.getBool('setup_completed') ?? false;
    if (!completed) {
      // Mark the current gesture guide as known as soon as a fresh 1.1.0+
      // installation starts. Updated installations already have setup marked
      // complete, so they remain eligible for the one-time migration guide.
      await preferences.setBool(
        'multi_touch_upgrade_notice_acknowledged',
        true,
      );
      await preferences.setString('last_launched_app_version', '1.1.0');
    }
    if (mounted) {
      setState(() => setupCompleted = completed);
    }
  }

  Future<void> loadThemeMode() async {
    final prefs = await SharedPreferences.getInstance();
    final name = prefs.getString('theme_mode');
    if (!mounted) return;
    setState(() {
      themeMode = ThemeMode.values.firstWhere(
        (item) => item.name == name,
        orElse: () => ThemeMode.system,
      );
    });
  }

  Future<void> setThemeMode(ThemeMode value) async {
    setState(() => themeMode = value);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('theme_mode', value.name);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      themeMode: themeMode,
      theme: appTheme(Brightness.light),
      darkTheme: appTheme(Brightness.dark),
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: setupCompleted == null
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : setupCompleted == false
          ? DextopSetupPage(
              onCompleted: () => setState(() => setupCompleted = true),
            )
          : HomeScreen(themeMode: themeMode, onThemeModeChanged: setThemeMode),
    );
  }

  ThemeData appTheme(Brightness brightness) {
    final scheme = ColorScheme.fromSeed(
      seedColor: const Color(0xff6750a4),
      brightness: brightness,
      dynamicSchemeVariant: DynamicSchemeVariant.tonalSpot,
    );
    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: scheme.surface,
      textTheme: const TextTheme(
        displaySmall: TextStyle(
          fontSize: 38,
          height: 1.12,
          fontWeight: FontWeight.w300,
        ),
        headlineMedium: TextStyle(
          fontSize: 29,
          height: 1.2,
          fontWeight: FontWeight.w500,
        ),
        titleLarge: TextStyle(
          fontSize: 21,
          height: 1.28,
          fontWeight: FontWeight.w600,
        ),
        titleMedium: TextStyle(
          fontSize: 17,
          height: 1.35,
          fontWeight: FontWeight.w600,
        ),
        bodyLarge: TextStyle(fontSize: 16, height: 1.5),
        bodyMedium: TextStyle(fontSize: 14, height: 1.5),
        labelLarge: TextStyle(
          fontSize: 14,
          height: 1.4,
          fontWeight: FontWeight.w600,
        ),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: scheme.surfaceContainer,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
      ),
      navigationBarTheme: NavigationBarThemeData(
        height: 76,
        elevation: 0,
        backgroundColor: scheme.surfaceContainer,
        indicatorColor: scheme.secondaryContainer,
        indicatorShape: const StadiumBorder(),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(64, 56),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
        ),
      ),
    );
  }
}

class DisplayProfile {
  const DisplayProfile(
    this.name,
    this.detail,
    this.width,
    this.height,
    this.density,
    this.icon, {
    required this.id,
    this.isDevice = false,
  });

  final String name;
  final String detail;
  final int width;
  final int height;
  final int density;
  final IconData icon;
  final String id;
  final bool isDevice;

  Map<String, dynamic> toJson() => {
    'id': id,
    'width': width,
    'height': height,
    'density': density,
  };

  static DisplayProfile fromJson(Map<String, dynamic> json) {
    final width = json['width'] as int;
    final height = json['height'] as int;
    final density = json['density'] as int;
    return DisplayProfile(
      '$width × $height',
      '$density dpi',
      width,
      height,
      density,
      Icons.monitor_rounded,
      id: json['id'] as String,
    );
  }
}

class NativeBridge {
  static const channel = MethodChannel('app.freedextop/display');

  Future<Map<String, dynamic>> status() async {
    return await channel.invokeMapMethod<String, dynamic>('status') ?? {};
  }

  Future<bool> requestShizuku() async {
    return await channel.invokeMethod<bool>('requestShizuku') ?? false;
  }

  Future<void> openShizuku() => channel.invokeMethod('openShizuku');
  Future<Map<String, dynamic>> selectPrivilegeProvider(String provider) async =>
      await channel.invokeMapMethod<String, dynamic>(
        'selectPrivilegeProvider',
        {'provider': provider},
      ) ??
      {};
  Future<void> openAccessibility() => channel.invokeMethod('openAccessibility');
  Future<void> openWirelessDebugging() =>
      channel.invokeMethod('openWirelessDebugging');
  Future<void> openUrl(String url) =>
      channel.invokeMethod('openUrl', {'url': url});
  Future<String> diagnosticReport() async =>
      await channel.invokeMethod<String>('diagnosticReport') ?? '';
  Future<void> clearDiagnosticLog() =>
      channel.invokeMethod('clearDiagnosticLog');
  Future<void> shareDiagnosticReport() =>
      channel.invokeMethod('shareDiagnosticReport');
  Future<List<dynamic>> apps() async =>
      await channel.invokeListMethod<dynamic>('apps') ?? [];
  Future<bool> consumeTileAction() async =>
      await channel.invokeMethod<bool>('consumeTileAction') ?? false;
  Future<void> launchApp(
    String packageName, {
    List<int>? bounds,
    String? position,
  }) => channel.invokeMethod('launchApp', {
    'package': packageName,
    'bounds': ?bounds,
    'position': ?position,
  });
  Future<Map<String, dynamic>> recovery() async =>
      await channel.invokeMapMethod<String, dynamic>('recovery') ?? {};
  Future<Map<String, dynamic>> repairState() async =>
      await channel.invokeMapMethod<String, dynamic>('repairState') ?? {};
  Future<void> repairAndroid() => channel.invokeMethod('repairAndroid');
  Future<void> restartApp() => channel.invokeMethod('restartApp');
  Future<void> clearRecovery() => channel.invokeMethod('clearRecovery');
  Future<void> stop() async {
    await channel.invokeMethod('stop');
  }

  Future<void> start(
    DisplayProfile profile,
    bool portrait,
    bool secure, {
    required bool decorations,
  }) {
    AppAnalytics.event('desktop_start', {
      'orientation': portrait ? 'portrait' : 'landscape',
      'secure_display': secure,
      'resolution': '${profile.width}x${profile.height}',
    });
    return channel.invokeMethod('start', {
      'width': portrait ? profile.height : profile.width,
      'height': portrait ? profile.width : profile.height,
      'density': profile.density,
      'secure': secure,
      'decorations': decorations,
    });
  }
}
