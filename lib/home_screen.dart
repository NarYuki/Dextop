part of 'main.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({
    required this.themeMode,
    required this.onThemeModeChanged,
    super.key,
  });

  final ThemeMode themeMode;
  final ValueChanged<ThemeMode> onThemeModeChanged;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  void mutate(VoidCallback change) => setState(change);
  final bridge = NativeBridge();
  var page = 0;
  var profiles = <DisplayProfile>[];
  var profile = DisplayProfile(
    AppStrings.tr('uiTerminalResolution'),
    '240 dpi',
    1920,
    1080,
    240,
    Icons.smartphone_rounded,
    id: 'device',
    isDevice: true,
  );
  var deviceProfileInitialized = false;
  var portrait = false;
  var secure = false;
  var loading = true;
  var active = false;
  var shizukuInstalled = false;
  var shizukuRunning = false;
  var shizukuGranted = false;
  String privilegeProvider = 'stellar';
  String privilegeProviderName = 'Stellar';
  var providerChoiceShown = false;
  var secureSettingsGranted = false;
  String? error;
  String manufacturer = '';
  String model = '';
  String androidVersion = '';
  String desktopMode = '';
  var recovery = <String, dynamic>{};
  var androidRepair = <String, dynamic>{};
  var androidRepairCompleted = false;
  var workspaceExpanded = false;
  var homeWorkspaces = <Map<String, dynamic>>[];
  var homeApps = <String, Map<String, dynamic>>{};
  var homeAppsLoading = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _initializeDeviceProfile(),
    );
    _initializeHome();
    _loadHomeSelections();
    AppAnalytics.screen('home');
  }

  Future<void> _loadHomeSelections() async {
    final preferences = await SharedPreferences.getInstance();
    if (mounted) {
      setState(() {
        secure = preferences.getBool('secure_display') ?? false;
        portrait = preferences.getBool('home_portrait') ?? false;
      });
    }
  }

  Future<void> setPortrait(bool value) async {
    mutate(() => portrait = value);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('home_portrait', value);
  }

  Future<void> setSecureDisplay(bool value) async {
    mutate(() => secure = value);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('secure_display', value);
  }

  bool get effectiveDecorations => manufacturer.toLowerCase() != 'samsung';

  Future<void> _initializeHome() async {
    await Future.wait([refresh(), loadHomeWorkspaces(loadIcons: false)]);
    if (mounted) {
      WidgetsBinding.instance.addPostFrameCallback((_) => loadHomeAppIcons());
    }
    if (mounted) await _consumeTileAction();
  }

  Future<void> _consumeTileAction() async {
    if (!mounted || !await bridge.consumeTileAction()) return;
    await loadHomeWorkspaces();
    final preferences = await SharedPreferences.getInstance();
    final lastId = preferences.getString('last_workspace_id');
    final matches = homeWorkspaces.where((item) => '${item['id']}' == lastId);
    if (matches.isNotEmpty) {
      await launchHomeWorkspace(matches.first);
    } else if (mounted) {
      setState(() => workspaceExpanded = true);
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _initializeDeviceProfile();
  }

  void _initializeDeviceProfile() {
    if (deviceProfileInitialized) return;
    final views = WidgetsBinding.instance.platformDispatcher.views;
    if (views.isEmpty) return;
    final physical = views.first.physicalSize;
    final width = physical.width.round();
    final height = physical.height.round();
    if (width < 480 || height < 480) {
      Future<void>.delayed(Duration(milliseconds: 100), () {
        if (mounted) _initializeDeviceProfile();
      });
      return;
    }
    final landscapeWidth = width > height ? width : height;
    final landscapeHeight = width > height ? height : width;
    // Choose a readable density for the initial device profile. A density
    // saved by the user takes precedence on subsequent launches.
    final deviceDensity = (160 + (views.first.devicePixelRatio * 24))
        .round()
        .clamp(160, 320);
    final deviceProfile = DisplayProfile(
      '${AppStrings.tr('uiTerminalResolution')} ($landscapeWidth × $landscapeHeight)',
      '$deviceDensity dpi',
      landscapeWidth,
      landscapeHeight,
      deviceDensity,
      Icons.smartphone_rounded,
      id: 'device',
      isDevice: true,
    );
    profiles = [deviceProfile];
    profile = deviceProfile;
    deviceProfileInitialized = true;
    _loadProfiles(deviceProfile, deviceDensity);
  }

  Future<void> _loadProfiles(
    DisplayProfile deviceProfile,
    int defaultDeviceDensity,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final deviceDpi =
        prefs.getInt('device_resolution_dpi') ?? defaultDeviceDensity;
    final device = DisplayProfile(
      deviceProfile.name,
      '$deviceDpi dpi',
      deviceProfile.width,
      deviceProfile.height,
      deviceDpi,
      deviceProfile.icon,
      id: 'device',
      isDevice: true,
    );
    final custom = <DisplayProfile>[];
    try {
      final decoded =
          jsonDecode(prefs.getString('custom_resolution_profiles') ?? '[]')
              as List<dynamic>;
      custom.addAll(
        decoded.map(
          (item) =>
              DisplayProfile.fromJson(Map<String, dynamic>.from(item as Map)),
        ),
      );
    } catch (_) {
      await prefs.remove('custom_resolution_profiles');
    }
    final selectedId = prefs.getString('selected_resolution_id') ?? 'device';
    if (!mounted) return;
    setState(() {
      profiles = [device, ...custom];
      profile =
          profiles.where((item) => item.id == selectedId).firstOrNull ?? device;
    });
  }

  Future<void> _saveProfiles() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('device_resolution_dpi', profiles.first.density);
    await prefs.setString(
      'custom_resolution_profiles',
      jsonEncode(
        profiles
            .where((item) => !item.isDevice)
            .map((item) => item.toJson())
            .toList(),
      ),
    );
    await prefs.setString('selected_resolution_id', profile.id);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      refresh();
      _consumeTileAction();
      if (profiles.isNotEmpty) {
        _loadProfiles(profiles.first, profiles.first.density);
      }
    }
  }

  Future<void> refresh() async {
    try {
      var value = await bridge.status();
      for (
        var attempt = 0;
        attempt < 8 && value['shizukuRunning'] != true;
        attempt++
      ) {
        await Future<void>.delayed(Duration(milliseconds: 250));
        value = await bridge.status();
      }
      if (value['shizukuGranted'] == true && value['privileged'] != true) {
        await bridge.requestShizuku();
        value = await bridge.status();
      }
      final recoveryValue = await bridge.recovery();
      final repairValue = await bridge.repairState();
      if (recoveryValue['phase'] == 'paused') {
        repairValue['required'] = false;
        repairValue['pausedByUser'] = true;
      }
      if (!mounted) return;
      setState(() {
        active = value['active'] == true;
        shizukuInstalled = value['shizukuInstalled'] == true;
        shizukuRunning = value['shizukuRunning'] == true;
        shizukuGranted = value['shizukuGranted'] == true;
        privilegeProvider = '${value['privilegeProvider'] ?? 'stellar'}';
        privilegeProviderName =
            '${value['privilegeProviderName'] ?? 'Stellar'}';
        secureSettingsGranted = value['privileged'] == true;
        manufacturer = '${value['manufacturer'] ?? ''}';
        model = '${value['model'] ?? ''}';
        androidVersion = '${value['androidVersion'] ?? ''}';
        desktopMode = '${value['desktopMode'] ?? ''}';
        recovery = recoveryValue;
        androidRepair = repairValue;
        loading = false;
        error = null;
      });
      if (value['privilegeProviderSelectionRequired'] == true &&
          !providerChoiceShown &&
          mounted) {
        providerChoiceShown = true;
        WidgetsBinding.instance.addPostFrameCallback(
          (_) => _choosePrivilegeProvider(),
        );
      }
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = e.message;
      });
    }
  }

  Future<void> _choosePrivilegeProvider() async {
    if (!mounted) return;
    final choice = await showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.admin_panel_settings_rounded),
        title: const Text('特権サービスを選択'),
        content: const Text(
          'StellarとShizukuの両方がインストールされています。Dextopで使用するサービスを選択してください。選択は、どちらかをアンインストールするまで保存されます。',
        ),
        actions: [
          OutlinedButton(
            onPressed: () => Navigator.pop(context, 'shizuku'),
            child: const Text('Shizuku'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, 'stellar'),
            child: const Text('Stellar（推奨）'),
          ),
        ],
      ),
    );
    if (choice == null) return;
    await bridge.selectPrivilegeProvider(choice);
    await refresh();
  }

  Future<void> loadHomeWorkspaces({bool loadIcons = true}) async {
    final preferences = await SharedPreferences.getInstance();
    var decoded = <dynamic>[];
    try {
      decoded = jsonDecode(preferences.getString('workspaces') ?? '[]') as List;
    } catch (_) {
      decoded = [];
    }
    if (!mounted) return;
    setState(() {
      homeWorkspaces = decoded
          .map((item) => Map<String, dynamic>.from(item as Map))
          .toList();
    });
    if (loadIcons) await loadHomeAppIcons();
  }

  Future<void> loadHomeAppIcons() async {
    if (homeAppsLoading) return;
    setState(() => homeAppsLoading = true);
    try {
      final rawApps = await bridge.apps();
      if (!mounted) return;
      setState(() {
        homeApps = {
          for (final item in rawApps)
            '${(item as Map)['package']}': Map<String, dynamic>.from(item),
        };
        homeAppsLoading = false;
      });
    } catch (_) {
      if (mounted) setState(() => homeAppsLoading = false);
    }
  }

  Future<void> launchHomeWorkspace(Map<String, dynamic> workspace) async {
    final packages = (workspace['apps'] as List).cast<String>();
    final positions = workspace['positions'] is Map
        ? Map<String, dynamic>.from(workspace['positions'] as Map)
        : <String, dynamic>{};
    final savedBounds = workspace['bounds'] is Map
        ? Map<String, dynamic>.from(workspace['bounds'] as Map)
        : <String, dynamic>{};
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('last_workspace_id', '${workspace['id']}');
    if (!await ensureDesktopRunning()) return;
    for (var index = 0; index < packages.length; index++) {
      final position = positions[packages[index]] as String?;
      final exactBounds = savedBounds[packages[index]];
      final column = index % 2;
      final row = (index ~/ 2).clamp(0, 1);
      await bridge.launchApp(
        packages[index],
        position: exactBounds is List ? null : position,
        bounds: exactBounds is List && exactBounds.length == 4
            ? exactBounds.cast<int>()
            : position == null
            ? [column * 960, row * 540, (column + 1) * 960, (row + 1) * 540]
            : null,
      );
      await Future<void>.delayed(Duration(milliseconds: 350));
    }
  }

  Future<void> connect() async {
    if (!shizukuInstalled || !shizukuRunning) {
      await bridge.openShizuku();
      return;
    }
    setState(() => loading = true);
    try {
      await bridge.requestShizuku();
      await refresh();
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = e.message;
      });
    }
  }

  Future<void> toggleDisplay() async {
    if (loading) return;
    if (!active && recovery['recoverable'] == true) return;
    setState(() {
      loading = true;
      error = null;
    });
    try {
      if (active) {
        await bridge.stop();
      } else {
        await bridge.start(
          profile,
          portrait,
          secure,
          decorations: effectiveDecorations,
        );
      }
      await Future<void>.delayed(Duration(milliseconds: 350));
      await refresh();
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = e.message;
      });
    }
  }

  Future<bool> ensureDesktopRunning() async {
    if (active) return true;
    if (loading ||
        !secureSettingsGranted ||
        !shizukuRunning ||
        !shizukuGranted ||
        recovery['recoverable'] == true) {
      return false;
    }
    setState(() {
      loading = true;
      error = null;
    });
    try {
      await bridge.start(
        profile,
        portrait,
        secure,
        decorations: effectiveDecorations,
      );
      await Future<void>.delayed(Duration(milliseconds: 450));
      await refresh();
      return active;
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() {
          loading = false;
          error = e.message;
        });
      }
      return false;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: AnimatedSwitcher(
        duration: Duration(milliseconds: 450),
        switchInCurve: Curves.easeOutBack,
        switchOutCurve: Curves.easeInCubic,
        child: page == 0 ? overview() : settings(),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: page,
        onDestinationSelected: (value) {
          setState(() => page = value);
          AppAnalytics.screen(value == 0 ? 'home' : 'settings');
        },
        destinations: [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home_rounded),
            label: AppStrings.tr('home'),
          ),
          NavigationDestination(
            icon: Icon(Icons.tune_outlined),
            selectedIcon: Icon(Icons.tune_rounded),
            label: AppStrings.tr('settings'),
          ),
        ],
      ),
    );
  }
}
