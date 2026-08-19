part of 'main.dart';

extension _SettingsContent on _HomeScreenState {
  Widget settings() {
    final l = AppLocalizations.of(context);
    if (widget.desktopWindow &&
        MediaQuery.orientationOf(context) == Orientation.landscape) {
      return _desktopSettings(l);
    }
    return CustomScrollView(
      key: ValueKey('settings'),
      slivers: [
        SliverAppBar.large(title: Text(l.settings)),
        SliverPadding(
          padding: EdgeInsets.fromLTRB(16, 0, 16, 32),
          sliver: SliverList.list(
            children: [
              sectionTitle(l.theme),
              SizedBox(height: 12),
              rootMyGalaxyThemeSwitch(l),
              SizedBox(height: 32),
              settingsCard([
                _categoryTile(
                  Icons.display_settings_outlined,
                  l.display,
                  AppStrings.tr('uiSecureDisplayFoldable'),
                  () => _openDisplaySettings(l),
                ),
                Divider(height: 1),
                _categoryTile(
                  Icons.directions_car_outlined,
                  AppStrings.tr('autoSettingsTitle'),
                  AppStrings.tr('autoSettingsDescription'),
                  () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const AutoSettingsPage(),
                    ),
                  ),
                ),
                Divider(height: 1),
                _categoryTile(
                  Icons.mouse_outlined,
                  l.mouseSettingsTitle,
                  l.mouseSettingsDescription,
                  () => _openMouseSettings(l),
                ),
                Divider(height: 1),
                _categoryTile(
                  Icons.keyboard_alt_outlined,
                  l.keyboardThemesTitle,
                  l.keyboardThemesDescription,
                  active
                      ? null
                      : () => Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => const KeyboardThemesPage(),
                          ),
                        ),
                ),
                Divider(height: 1),
                _categoryTile(
                  Icons.apps_outlined,
                  AppStrings.tr('uiAppLauncherSettings'),
                  AppStrings.tr('uiManageLaunchedAppsAndConfigurations'),
                  () => _openFeatureCategory('apps', launcherOnly: true),
                ),
                Divider(height: 1),
                _categoryTile(
                  Icons.gesture_rounded,
                  AppStrings.tr('uiInputAndGestures'),
                  AppStrings.tr('uiTapPressAndHoldMultiFingerOperation'),
                  () => _openFeatureCategory('interaction'),
                ),
                Divider(height: 1),
                _categoryTile(
                  Icons.monitor_heart_outlined,
                  AppStrings.tr('uiConditionAndDiagnosis'),
                  AppStrings.tr('uiPerformanceCompatibility'),
                  () => _openFeatureCategory('status'),
                ),
                Divider(height: 1),
                _categoryTile(
                  Icons.devices_outlined,
                  AppStrings.tr('uiTerminalAndPermissions'),
                  AppStrings.tr('uiDeviceInformationDesktopModeAccessibility'),
                  () => _openDeviceSettings(l),
                ),
                Divider(height: 1),
                _categoryTile(
                  Icons.info_outline_rounded,
                  l.appInfo,
                  appVersion.isEmpty ? 'Dextop' : 'Dextop $appVersion',
                  () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => AppInfoPage(
                        bridge: bridge,
                        appVersion: appVersion,
                        updateAvailable: updateAvailable,
                        checking: releaseChecking,
                        checkSucceeded: releaseCheckSucceeded,
                        checkError: releaseCheckError,
                        onCheck: () => _checkForUpdates(manual: true),
                        onShowUpdate: _showUpdateDialog,
                        isRunning: active,
                      ),
                    ),
                  ),
                  badge: updateAvailable ? l.updateAvailable : null,
                ),
              ]),
              SizedBox(height: 20),
              settingsCard([
                _categoryTile(
                  Icons.fact_check_outlined,
                  AppStrings.tr('deviceReport'),
                  AppStrings.tr('deviceReportDescription'),
                  () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const DeviceReportPage(),
                    ),
                  ),
                ),
              ]),
            ],
          ),
        ),
      ],
    );
  }

  Widget _desktopSettings(AppLocalizations l) {
    final sections = <(String, IconData, String, String)>[
      (
        'display',
        Icons.display_settings_outlined,
        l.display,
        AppStrings.tr('uiSecureDisplayFoldable'),
      ),
      (
        'auto',
        Icons.directions_car_outlined,
        AppStrings.tr('autoSettingsTitle'),
        AppStrings.tr('autoSettingsDescription'),
      ),
      (
        'mouse',
        Icons.mouse_outlined,
        l.mouseSettingsTitle,
        l.mouseSettingsDescription,
      ),
      (
        'keyboard',
        Icons.keyboard_alt_outlined,
        l.keyboardThemesTitle,
        l.keyboardThemesDescription,
      ),
      (
        'apps',
        Icons.apps_outlined,
        AppStrings.tr('uiAppLauncherSettings'),
        AppStrings.tr('uiManageLaunchedAppsAndConfigurations'),
      ),
      (
        'interaction',
        Icons.gesture_rounded,
        AppStrings.tr('uiInputAndGestures'),
        AppStrings.tr('uiTapPressAndHoldMultiFingerOperation'),
      ),
      (
        'status',
        Icons.monitor_heart_outlined,
        AppStrings.tr('uiConditionAndDiagnosis'),
        AppStrings.tr('uiPerformanceCompatibility'),
      ),
      (
        'device',
        Icons.devices_outlined,
        AppStrings.tr('uiTerminalAndPermissions'),
        AppStrings.tr('uiDeviceInformationDesktopModeAccessibility'),
      ),
      (
        'about',
        Icons.info_outline_rounded,
        l.appInfo,
        appVersion.isEmpty ? 'Dextop' : 'Dextop $appVersion',
      ),
    ];
    return LayoutBuilder(
      key: const ValueKey('desktop-settings'),
      builder: (context, constraints) {
        final selected = switch (desktopSettingsSection) {
          'samsung' => (
            'samsung',
            Icons.desktop_windows_outlined,
            AppStrings.tr('samsungSettingsTitle'),
            '',
          ),
          'diagnostics' => (
            'diagnostics',
            Icons.article_outlined,
            AppStrings.tr('diagnosticLog'),
            '',
          ),
          _ => sections.firstWhere((item) => item.$1 == desktopSettingsSection),
        };
        return Row(
          children: [
            SizedBox(
              width: constraints.maxWidth >= 1200
                  ? 340
                  : constraints.maxWidth >= 820
                  ? 300
                  : 240,
              child: Material(
                color: Theme.of(context).colorScheme.surfaceContainerLow,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(24, 24, 24, 14),
                      child: Text(
                        l.settings,
                        style: Theme.of(context).textTheme.headlineMedium,
                      ),
                    ),
                    Expanded(
                      child: ListView.builder(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        itemCount: sections.length,
                        itemBuilder: (context, index) {
                          final item = sections[index];
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 4),
                            child: ListTile(
                              dense: true,
                              minTileHeight: item.$1 == 'about' ? 58 : 52,
                              selected:
                                  item.$1 == desktopSettingsSection ||
                                  (item.$1 == 'about' &&
                                      (desktopSettingsSection == 'samsung' ||
                                          desktopSettingsSection ==
                                              'diagnostics')),
                              selectedTileColor: Theme.of(
                                context,
                              ).colorScheme.secondaryContainer,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(14),
                              ),
                              leading: Icon(item.$2),
                              enabled: !(active && item.$1 == 'keyboard'),
                              title: Text(
                                item.$3,
                                style: const TextStyle(fontSize: 15),
                              ),
                              subtitle: item.$1 == 'about'
                                  ? Text(
                                      item.$4,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(fontSize: 12),
                                    )
                                  : null,
                              trailing: const Icon(Icons.chevron_right_rounded),
                              onTap: active && item.$1 == 'keyboard'
                                  ? null
                                  : () => mutate(
                                      () => desktopSettingsSection = item.$1,
                                    ),
                            ),
                          );
                        },
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(20),
                      child: rootMyGalaxyThemeSwitch(l),
                    ),
                  ],
                ),
              ),
            ),
            const VerticalDivider(width: 1),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Padding(
                    padding: EdgeInsets.fromLTRB(
                      desktopSettingsSection == 'samsung' ||
                              desktopSettingsSection == 'diagnostics'
                          ? 16
                          : 28,
                      22,
                      28,
                      14,
                    ),
                    child: AnimatedSwitcher(
                      duration: const Duration(milliseconds: 240),
                      transitionBuilder: (child, animation) => FadeTransition(
                        opacity: animation,
                        child: SlideTransition(
                          position: Tween(
                            begin: const Offset(.04, 0),
                            end: Offset.zero,
                          ).animate(animation),
                          child: child,
                        ),
                      ),
                      child: Row(
                        key: ValueKey('header-$desktopSettingsSection'),
                        children: [
                          if (desktopSettingsSection == 'samsung' ||
                              desktopSettingsSection == 'diagnostics') ...[
                            IconButton(
                              tooltip: l.back,
                              onPressed: () => mutate(
                                () => desktopSettingsSection = 'about',
                              ),
                              icon: const Icon(Icons.arrow_back_rounded),
                            ),
                            const SizedBox(width: 4),
                          ],
                          Icon(selected.$2),
                          const SizedBox(width: 12),
                          Text(
                            selected.$3,
                            style: Theme.of(context).textTheme.headlineSmall,
                          ),
                        ],
                      ),
                    ),
                  ),
                  const Divider(height: 1),
                  Expanded(
                    child: AnimatedSwitcher(
                      duration: const Duration(milliseconds: 240),
                      transitionBuilder: (child, animation) => FadeTransition(
                        opacity: animation,
                        child: SlideTransition(
                          position: Tween(
                            begin: const Offset(.025, 0),
                            end: Offset.zero,
                          ).animate(animation),
                          child: child,
                        ),
                      ),
                      child: KeyedSubtree(
                        key: ValueKey(desktopSettingsSection),
                        child: _desktopSettingsDetail(l),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _desktopSettingsDetail(AppLocalizations l) =>
      switch (desktopSettingsSection) {
        'apps' => ListView(
          padding: const EdgeInsets.all(20),
          children: [
            DextopFeaturesPage(
              isRunning: active,
              embedded: true,
              launcherOnly: true,
              ensureDesktopRunning: ensureDesktopRunning,
            ),
          ],
        ),
        'auto' => const AutoSettingsPage(embedded: true),
        'keyboard' => const KeyboardThemesPage(),
        'mouse' => MouseSettingsPage(bridge: bridge, isRunning: active),
        'interaction' => ListView(
          padding: const EdgeInsets.all(20),
          children: [
            DextopFeaturesPage(
              isRunning: active,
              embedded: true,
              category: 'interaction',
            ),
          ],
        ),
        'status' => ListView(
          padding: const EdgeInsets.all(20),
          children: [
            DextopFeaturesPage(
              isRunning: active,
              embedded: true,
              category: 'status',
            ),
          ],
        ),
        'device' => _deviceSettingsContent(l),
        'about' => AppInfoPage(
          bridge: bridge,
          appVersion: appVersion,
          updateAvailable: updateAvailable,
          checking: releaseChecking,
          checkSucceeded: releaseCheckSucceeded,
          checkError: releaseCheckError,
          onCheck: () => _checkForUpdates(manual: true),
          onShowUpdate: _showUpdateDialog,
          embedded: true,
          isRunning: active,
          onOpenSamsungSettings: () =>
              mutate(() => desktopSettingsSection = 'samsung'),
          onOpenDiagnosticLog: () =>
              mutate(() => desktopSettingsSection = 'diagnostics'),
        ),
        'samsung' => SamsungDesktopSettingsPage(
          bridge: bridge,
          isRunning: active,
          embedded: true,
        ),
        'diagnostics' => _DiagnosticLogPage(bridge: bridge, embedded: true),
        _ => StatefulBuilder(
          builder: (context, update) => _displaySettingsContent(l, update),
        ),
      };

  Widget _categoryTile(
    IconData icon,
    String title,
    String subtitle,
    VoidCallback? action, {
    String? badge,
  }) => ListTile(
    enabled: action != null,
    leading: Icon(icon),
    title: Text(title),
    subtitle: Text(subtitle),
    trailing: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (badge != null) ...[
          Text(
            badge,
            style: TextStyle(
              color: Theme.of(context).colorScheme.error,
              fontWeight: FontWeight.w600,
            ),
          ),
          SizedBox(width: 8),
        ],
        Icon(Icons.chevron_right_rounded),
      ],
    ),
    onTap: action,
  );

  void _openFeatureCategory(String category, {bool launcherOnly = false}) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => DextopFeaturesPage(
          isRunning: active,
          category: category,
          launcherOnly: launcherOnly,
          ensureDesktopRunning: ensureDesktopRunning,
        ),
      ),
    );
  }

  void _openDisplaySettings(AppLocalizations l) {
    AppAnalytics.screen('display_settings');
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => StatefulBuilder(
          builder: (routeContext, updateRoute) => Scaffold(
            appBar: AppBar(title: Text(l.display)),
            body: _displaySettingsContent(l, updateRoute),
          ),
        ),
      ),
    );
  }

  void _openMouseSettings(AppLocalizations l) {
    AppAnalytics.screen('mouse_settings');
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => MouseSettingsPage(bridge: bridge, isRunning: active),
      ),
    );
  }

  Widget _displaySettingsContent(AppLocalizations l, StateSetter updateRoute) =>
      ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        children: [
          _displaySettingsSection(AppStrings.tr('uiDisplayCategory'), [
            DisplayEnvironmentSettingsCard(
              bridge: bridge,
              showDisplay: false,
              showConvenience: false,
              showTopology: true,
              wrapInCard: false,
            ),
            Divider(height: 1),
            ListTile(
              enabled: !active,
              leading: Icon(Icons.account_tree_outlined),
              title: Text(l.mirrorBackend),
              subtitle: Text(_mirrorBackendLabel(l, mirrorBackend)),
              trailing: Icon(Icons.chevron_right_rounded),
              onTap: active
                  ? null
                  : () => _selectMirrorBackend(context, l, updateRoute),
            ),
            DisplayEnvironmentSettingsCard(
              bridge: bridge,
              showConvenience: false,
              showTopology: false,
              displayLeadingDivider: true,
              wrapInCard: false,
            ),
          ]),
          DextopFeaturesPage(
            isRunning: active,
            embedded: true,
            category: 'display',
          ),
          _displaySettingsSection(AppStrings.tr('uiConvenience'), [
            DisplayEnvironmentSettingsCard(
              bridge: bridge,
              showDisplay: false,
              showTopology: false,
              wrapInCard: false,
            ),
            Divider(height: 1),
            SwitchListTile(
              title: Text(l.secureDisplay),
              subtitle: Text(l.secureDisplayDescription),
              secondary: Icon(Icons.lock_rounded),
              value: secure,
              onChanged: active
                  ? null
                  : (value) async {
                      final previous = secure;
                      updateRoute(() => secure = value);
                      try {
                        await setSecureDisplay(value);
                      } catch (_) {
                        updateRoute(() => secure = previous);
                        rethrow;
                      }
                    },
            ),
          ]),
        ],
      );

  Widget _displaySettingsSection(String title, List<Widget> children) =>
      Padding(
        padding: const EdgeInsets.only(bottom: 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 0, 12, 7),
              child: Text(
                title,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: Theme.of(context).colorScheme.primary,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            Card(
              margin: EdgeInsets.zero,
              child: ListTileTheme(
                data: const ListTileThemeData(
                  dense: true,
                  minTileHeight: 56,
                  minVerticalPadding: 4,
                  contentPadding: EdgeInsets.symmetric(horizontal: 16),
                  visualDensity: VisualDensity(vertical: -2),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Column(children: children),
                ),
              ),
            ),
          ],
        ),
      );

  String _mirrorBackendLabel(AppLocalizations l, String value) =>
      switch (value) {
        'window_manager' => l.mirrorBackendWindowManager,
        'surface_control' => l.mirrorBackendSurfaceControl,
        'virtual_display' => l.mirrorBackendVirtualDisplay,
        _ => l.mirrorBackendAuto,
      };

  Future<void> _selectMirrorBackend(
    BuildContext routeContext,
    AppLocalizations l,
    StateSetter updateRoute,
  ) async {
    final selected = await showDialog<String>(
      context: routeContext,
      builder: (dialogContext) => SimpleDialog(
        title: Text(l.mirrorBackend),
        children: [
          RadioGroup<String>(
            groupValue: mirrorBackend,
            onChanged: (choice) => Navigator.pop(dialogContext, choice),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final value in const [
                  'auto',
                  'window_manager',
                  'surface_control',
                  'virtual_display',
                ])
                  RadioListTile<String>(
                    value: value,
                    title: Text(_mirrorBackendLabel(l, value)),
                    subtitle: value == 'auto'
                        ? Text(l.mirrorBackendAutoDescription)
                        : null,
                  ),
              ],
            ),
          ),
        ],
      ),
    );
    if (selected == null || selected == mirrorBackend) return;
    updateRoute(() => mirrorBackend = selected);
    await setMirrorBackend(selected);
  }

  void _openDeviceSettings(AppLocalizations l) {
    AppAnalytics.screen('device_and_permissions');
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => Scaffold(
          appBar: AppBar(
            title: Text(AppStrings.tr('uiTerminalAndPermissions')),
          ),
          body: _deviceSettingsContent(l),
        ),
      ),
    );
  }

  Widget _deviceSettingsContent(AppLocalizations l) => ListView(
    padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
    children: [
      settingsCard([
        ListTile(
          leading: Icon(Icons.smartphone_rounded),
          title: Text(
            [manufacturer, model].where((e) => e.isNotEmpty).join(' '),
          ),
          subtitle: Text('${AppStrings.tr('uiAndroid')} $androidVersion'),
        ),
        Divider(height: 1),
        ListTile(
          leading: Icon(Icons.desktop_windows_rounded),
          title: Text(l.desktopMode),
          subtitle: Text(desktopMode),
        ),
        Divider(height: 1),
        _KeepAwakeTile(),
        Divider(height: 1),
        actionTile(
          Icons.accessibility_new_rounded,
          l.accessibilitySettings,
          l.accessibilityDescription,
          bridge.openAccessibility,
        ),
      ]),
    ],
  );

  Widget rootMyGalaxyThemeSwitch(AppLocalizations l) {
    return independentSegmentSwitch<ThemeMode>(
      choices: [
        (ThemeMode.system, l.system, Icons.brightness_auto_rounded),
        (ThemeMode.light, l.light, Icons.light_mode_rounded),
        (ThemeMode.dark, l.dark, Icons.dark_mode_rounded),
      ],
      selected: widget.themeMode,
      onSelected: widget.onThemeModeChanged,
    );
  }

  Widget independentSegmentSwitch<T>({
    required List<(T, String, IconData)> choices,
    required T selected,
    required ValueChanged<T>? onSelected,
  }) {
    final colors = Theme.of(context).colorScheme;
    return SizedBox(
      height: 38,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: List.generate(choices.length, (index) {
          final item = choices[index];
          final isSelected = item.$1 == selected;
          final unselectedRadius = index == 0
              ? BorderRadius.horizontal(
                  left: Radius.circular(28),
                  right: Radius.circular(8),
                )
              : index == choices.length - 1
              ? BorderRadius.horizontal(
                  left: Radius.circular(8),
                  right: Radius.circular(28),
                )
              : BorderRadius.circular(8);
          final radius = isSelected
              ? BorderRadius.circular(28)
              : unselectedRadius;
          return Expanded(
            child: Padding(
              padding: EdgeInsets.only(left: index == 0 ? 0 : 2),
              child: AnimatedContainer(
                duration: Duration(milliseconds: 200),
                curve: Curves.fastOutSlowIn,
                decoration: BoxDecoration(
                  color: isSelected
                      ? colors.primary
                      : colors.surfaceContainerHighest,
                  borderRadius: radius,
                ),
                child: Material(
                  color: Colors.transparent,
                  child: InkWell(
                    borderRadius: radius,
                    splashFactory: NoSplash.splashFactory,
                    overlayColor: WidgetStatePropertyAll(Colors.transparent),
                    splashColor: Colors.transparent,
                    highlightColor: Colors.transparent,
                    hoverColor: Colors.transparent,
                    focusColor: Colors.transparent,
                    onTap: onSelected == null
                        ? null
                        : () => onSelected(item.$1),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          item.$3,
                          size: 18,
                          color: isSelected
                              ? colors.onPrimary
                              : colors.onSurfaceVariant,
                        ),
                        SizedBox(width: 8),
                        Flexible(
                          child: Text(
                            item.$2,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: isSelected
                                  ? colors.onPrimary
                                  : colors.onSurfaceVariant,
                              fontWeight: FontWeight.w600,
                              fontSize: 12,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          );
        }),
      ),
    );
  }

  Widget sectionTitle(String title) {
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.headlineMedium),
        ],
      ),
    );
  }

  Widget settingsCard(List<Widget> children) {
    return Card(
      child: ListTileTheme(
        data: ListTileThemeData(
          dense: true,
          minTileHeight: 64,
          minVerticalPadding: 6,
          contentPadding: EdgeInsets.symmetric(horizontal: 16),
          visualDensity: VisualDensity(vertical: -1),
        ),
        child: Column(children: children),
      ),
    );
  }

  Widget actionTile(
    IconData icon,
    String title,
    String subtitle,
    VoidCallback action,
  ) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Text(subtitle),
      trailing: Icon(Icons.chevron_right_rounded),
      onTap: action,
    );
  }

  Widget errorPanel() {
    final colors = Theme.of(context).colorScheme;
    return Container(
      padding: EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: colors.errorContainer,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Row(
        children: [
          Icon(Icons.error_rounded, color: colors.onErrorContainer),
          SizedBox(width: 14),
          Expanded(
            child: Text(
              error!,
              style: TextStyle(color: colors.onErrorContainer),
            ),
          ),
        ],
      ),
    );
  }
}

class MouseSettingsPage extends StatefulWidget {
  const MouseSettingsPage({
    required this.bridge,
    required this.isRunning,
    super.key,
  });

  final NativeBridge bridge;
  final bool isRunning;

  @override
  State<MouseSettingsPage> createState() => _MouseSettingsPageState();
}

class _MouseSettingsPageState extends State<MouseSettingsPage> {
  SharedPreferences? prefs;
  var loading = true;
  var pointerProfile = 'touchpad';
  var naturalScroll = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final store = await SharedPreferences.getInstance();
    final display = await widget.bridge.displayEnvironmentSettings().catchError(
      (_) => <String, dynamic>{},
    );
    if (!mounted) return;
    // DPI and pointer acceleration are intentionally no longer supported.
    // Remove values written by older releases so a stale setting cannot
    // affect input after an upgrade (or leave the native side in a bad state).
    await store.remove('virtual_mouse_dpi');
    await store.remove('virtual_mouse_acceleration');
    final savedProfile = store.getString('virtual_pointer_profile');
    final fallbackProfile = display['softwareCursorFallback'] == true
        ? 'software'
        : 'touchpad';
    setState(() {
      prefs = store;
      pointerProfile =
          const {'touchpad', 'mouse', 'software'}.contains(savedProfile)
          ? savedProfile!
          : fallbackProfile;
      naturalScroll = store.getBool('virtual_mouse_natural_scroll') ?? true;
      loading = false;
    });
  }

  Future<void> _setPointerProfile(String value) async {
    final previous = pointerProfile;
    setState(() => pointerProfile = value);
    await prefs?.setString('virtual_pointer_profile', value);
    try {
      await widget.bridge.setVirtualPointerProfile(value);
    } catch (error) {
      if (!mounted) return;
      setState(() => pointerProfile = previous);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    }
  }

  void _setNaturalScroll(bool value) {
    setState(() => naturalScroll = value);
    unawaited(prefs?.setBool('virtual_mouse_natural_scroll', value));
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.mouseSettingsTitle)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        children: [
          _mouseSectionHeader(l.mouseSettingsTitle),
          Card(
            child: ListTileTheme(
              data: const ListTileThemeData(
                dense: true,
                minTileHeight: 60,
                minVerticalPadding: 6,
                contentPadding: EdgeInsets.symmetric(horizontal: 16),
              ),
              child: Column(
                children: [
                  _mouseChoiceTile<String>(
                    leading: const Icon(Icons.mouse_outlined),
                    title: l.virtualPointerProfile,
                    subtitle: switch (pointerProfile) {
                      'touchpad' => l.virtualTouchpadDescription,
                      'mouse' => l.virtualPointerMouseDescription,
                      _ => l.virtualPointerSoftwareDescription,
                    },
                    value: pointerProfile,
                    options: {
                      'touchpad': l.virtualTouchpad,
                      'mouse': l.virtualPointerMouse,
                      'software': l.virtualPointerSoftware,
                    },
                    onChanged: _setPointerProfile,
                  ),
                  const Divider(height: 1),
                  _mouseChoiceTile<bool>(
                    leading: const Icon(Icons.swap_vert_rounded),
                    title: l.virtualMouseScrollDirection,
                    subtitle: naturalScroll
                        ? l.virtualMouseNaturalScroll
                        : l.virtualMouseStandardScroll,
                    value: naturalScroll,
                    options: {
                      true: l.virtualMouseNaturalScroll,
                      false: l.virtualMouseStandardScroll,
                    },
                    onChanged: _setNaturalScroll,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _mouseSectionHeader(String title) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 0, 12, 7),
    child: Text(
      title,
      style: Theme.of(context).textTheme.labelLarge?.copyWith(
        color: Theme.of(context).colorScheme.primary,
        fontWeight: FontWeight.w600,
      ),
    ),
  );

  Widget _mouseChoiceTile<T>({
    required Widget leading,
    required String title,
    required String subtitle,
    required T value,
    required Map<T, String> options,
    required ValueChanged<T> onChanged,
  }) => ListTile(
    leading: leading,
    title: Text(title),
    subtitle: Text(subtitle),
    trailing: DropdownButtonHideUnderline(
      child: DropdownButton<T>(
        value: options.containsKey(value) ? value : options.keys.first,
        borderRadius: BorderRadius.circular(16),
        items: options.entries
            .map(
              (entry) => DropdownMenuItem<T>(
                value: entry.key,
                child: Text(entry.value),
              ),
            )
            .toList(),
        onChanged: loading
            ? null
            : (selected) {
                if (selected != null) onChanged(selected);
              },
      ),
    ),
  );
}

class AutoSettingsPage extends StatefulWidget {
  const AutoSettingsPage({this.embedded = false, super.key});

  final bool embedded;

  @override
  State<AutoSettingsPage> createState() => _AutoSettingsPageState();
}

class _AutoSettingsPageState extends State<AutoSettingsPage> {
  var loading = true;
  var matchPhoneOrientation = true;
  var hiddenAutoDisplay = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final preferences = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      matchPhoneOrientation =
          preferences.getBool('android_auto_match_phone_orientation') ?? true;
      hiddenAutoDisplay =
          preferences.getBool('android_auto_hidden_display') ?? false;
      loading = false;
    });
  }

  Future<void> _setHiddenAutoDisplay(bool value) async {
    setState(() => hiddenAutoDisplay = value);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('android_auto_hidden_display', value);
  }

  Future<void> _setMatchPhoneOrientation(bool value) async {
    setState(() => matchPhoneOrientation = value);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('android_auto_match_phone_orientation', value);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final body = ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        Text(
          AppStrings.tr('autoSettingsTitle'),
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(AppStrings.tr('autoSettingsDescription')),
        const SizedBox(height: 16),
        Card(
          child: SwitchListTile(
            secondary: const Icon(Icons.screen_rotation_alt_outlined),
            title: Text(AppStrings.tr('autoMatchPhoneOrientation')),
            subtitle: Text(
              AppStrings.tr('autoMatchPhoneOrientationDescription'),
            ),
            value: matchPhoneOrientation,
            onChanged: loading ? null : _setMatchPhoneOrientation,
          ),
        ),
        const SizedBox(height: 12),
        Padding(
          padding: const EdgeInsets.fromLTRB(4, 8, 4, 4),
          child: Text(
            AppStrings.tr('autoExperimentalFeatures'),
            style: Theme.of(context).textTheme.titleSmall,
          ),
        ),
        Card(
          child: SwitchListTile(
            secondary: const Icon(Icons.visibility_off_outlined),
            title: Text(AppStrings.tr('autoHiddenDisplay')),
            subtitle: Text(AppStrings.tr('autoHiddenDisplayDescription')),
            value: hiddenAutoDisplay,
            onChanged: loading ? null : _setHiddenAutoDisplay,
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: ListTile(
            leading: const Icon(Icons.info_outline_rounded),
            title: Text(l.display),
            subtitle: Text(AppStrings.tr('autoDisplayModeDescription')),
          ),
        ),
      ],
    );
    if (widget.embedded) return body;
    return Scaffold(
      appBar: AppBar(title: Text(AppStrings.tr('autoSettingsTitle'))),
      body: body,
    );
  }
}
