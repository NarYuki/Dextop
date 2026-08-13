part of 'main.dart';

extension _SettingsContent on _HomeScreenState {
  Widget settings() {
    final l = AppLocalizations.of(context);
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
              SizedBox(height: 20),
              settingsCard([
                _categoryTile(
                  Icons.display_settings_outlined,
                  l.display,
                  AppStrings.tr('uiSecureDisplayFoldable'),
                  () => _openDisplaySettings(l),
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
                  'Dextop 1.0.0',
                  () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => AppInfoPage(bridge: bridge),
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

  Widget _categoryTile(
    IconData icon,
    String title,
    String subtitle,
    VoidCallback action,
  ) => ListTile(
    leading: Icon(icon),
    title: Text(title),
    subtitle: Text(subtitle),
    trailing: Icon(Icons.chevron_right_rounded),
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
            body: ListView(
              padding: EdgeInsets.fromLTRB(16, 8, 16, 32),
              children: [
                Padding(
                  padding: EdgeInsets.fromLTRB(12, 0, 12, 7),
                  child: Text(
                    AppStrings.tr('uiSecurity'),
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: Theme.of(context).colorScheme.primary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                settingsCard([
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
                SizedBox(height: 12),
                DextopFeaturesPage(
                  isRunning: active,
                  embedded: true,
                  category: 'display',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _openDeviceSettings(AppLocalizations l) {
    AppAnalytics.screen('device_and_permissions');
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => Scaffold(
          appBar: AppBar(
            title: Text(AppStrings.tr('uiTerminalAndPermissions')),
          ),
          body: ListView(
            padding: EdgeInsets.fromLTRB(16, 8, 16, 32),
            children: [
              settingsCard([
                ListTile(
                  leading: Icon(Icons.smartphone_rounded),
                  title: Text(
                    [manufacturer, model].where((e) => e.isNotEmpty).join(' '),
                  ),
                  subtitle: Text(
                    '${AppStrings.tr('uiAndroid')} $androidVersion',
                  ),
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
          ),
        ),
      ),
    );
  }

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
