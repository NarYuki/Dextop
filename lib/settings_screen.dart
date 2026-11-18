part of 'main.dart';

extension _SettingsContent on _HomeScreenState {
  Widget settings() {
    final l = AppLocalizations.of(context);
    if (wideLayout) return _desktopSettings(l);
    return CustomScrollView(
      key: const ValueKey('settings'),
      slivers: [
        M3EAppBar.sliver(
          pinned: true,
          variant: M3EAppBarVariant.largeFlexible,
          titleText: l.settings,
        ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
          sliver: SliverList.list(
            children: [
              DextopSection(
                l.theme,
                padding: const EdgeInsets.fromLTRB(8, 8, 8, 10),
              ),
              rootMyGalaxyThemeSwitch(l),
              const SizedBox(height: 24),
              DextopGroup(
                children: [
                  _categoryTile(
                    M3EIcons.display_settings_outlined,
                    l.display,
                    currentLocalizations().uiSecureDisplayFoldable,
                    _unlessLinuxOnly(() => _openDisplaySettings(l)),
                  ),
                  if (carCompanionInstalled)
                    _categoryTile(
                      M3EIcons.directions_car_outlined,
                      l.autoSettingsTitle,
                      l.autoSettingsDescription,
                      _unlessLinuxOnly(
                        () => Navigator.of(context)
                            .push(dextopRoute<void>(const AutoSettingsPage())),
                      ),
                    ),
                  _categoryTile(
                    M3EIcons.mouse_outlined,
                    l.mouseSettingsTitle,
                    l.mouseSettingsDescription,
                    _unlessLinuxOnly(() => _openMouseSettings(l)),
                  ),
                  _categoryTile(
                    M3EIcons.keyboard_alt_outlined,
                    l.keyboardSettingsTitle,
                    l.keyboardSettingsDescription,
                    _unlessLinuxOnly(
                      () => Navigator.of(context).push(
                        dextopRoute<void>(
                          KeyboardSettingsPage(isRunning: active),
                        ),
                      ),
                    ),
                  ),
                  _categoryTile(
                    M3EIcons.apps_outlined,
                    currentLocalizations().uiAppLauncherSettings,
                    currentLocalizations()
                        .uiManageLaunchedAppsAndConfigurations,
                    _unlessLinuxOnly(
                      () => _openFeatureCategory('apps', launcherOnly: true),
                    ),
                  ),
                  _categoryTile(
                    M3EIcons.gesture_rounded,
                    currentLocalizations().uiInputAndGestures,
                    currentLocalizations()
                        .uiTapPressAndHoldMultiFingerOperation,
                    _unlessLinuxOnly(() => _openFeatureCategory('interaction')),
                  ),
                  _categoryTile(
                    M3EIcons.monitor_heart_outlined,
                    currentLocalizations().uiConditionAndDiagnosis,
                    currentLocalizations().uiPerformanceCompatibility,
                    _unlessLinuxOnly(() => _openFeatureCategory('status')),
                  ),
                  _categoryTile(
                    M3EIcons.devices_outlined,
                    currentLocalizations().uiTerminalAndPermissions,
                    currentLocalizations()
                        .uiDeviceInformationDesktopModeAccessibility,
                    _unlessLinuxOnly(() => _openDeviceSettings(l)),
                  ),
                  _categoryTile(
                    M3EIcons.info_outline_rounded,
                    l.appInfo,
                    appVersion.isEmpty ? 'Dextop' : 'Dextop $appVersion',
                    () => Navigator.of(context).push(
                      dextopRoute<void>(
                        AppInfoPage(
                          bridge: bridge,
                          appVersion: appVersion,
                          updateAvailable: updateAvailable,
                          checking: releaseChecking,
                          checkSucceeded: releaseCheckSucceeded,
                          checkError: releaseCheckError,
                          onCheck: () => _checkForUpdates(manual: true),
                          onShowUpdate: _showUpdateDialog,
                          isRunning: active,
                          onExperimentalFeaturesChanged: _loadHomeSelections,
                          receivePreReleases: receivePreReleases,
                          onReceivePreReleasesChanged: isPlayDistribution
                              ? null
                              : _setReceivePreReleases,
                        ),
                      ),
                    ),
                    badge: updateAvailable ? l.updateAvailable : null,
                  ),
                ],
              ),
              const SizedBox(height: 16),
              DextopGroup(
                children: [
                  _categoryTile(
                    M3EIcons.fact_check_outlined,
                    currentLocalizations().deviceReport,
                    currentLocalizations().deviceReportDescription,
                    () =>
                        Navigator.of(context)
                            .push(dextopRoute<void>(const DeviceReportPage())),
                  ),
                ],
              ),
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
        M3EIcons.display_settings_outlined,
        l.display,
        currentLocalizations().uiSecureDisplayFoldable,
      ),
      if (carCompanionInstalled)
        (
          'auto',
          M3EIcons.directions_car_outlined,
          l.autoSettingsTitle,
          l.autoSettingsDescription,
        ),
      (
        'mouse',
        M3EIcons.mouse_outlined,
        l.mouseSettingsTitle,
        l.mouseSettingsDescription,
      ),
      (
        'keyboard',
        M3EIcons.keyboard_alt_outlined,
        l.keyboardSettingsTitle,
        l.keyboardSettingsDescription,
      ),
      (
        'apps',
        M3EIcons.apps_outlined,
        currentLocalizations().uiAppLauncherSettings,
        currentLocalizations().uiManageLaunchedAppsAndConfigurations,
      ),
      (
        'interaction',
        M3EIcons.gesture_rounded,
        currentLocalizations().uiInputAndGestures,
        currentLocalizations().uiTapPressAndHoldMultiFingerOperation,
      ),
      (
        'status',
        M3EIcons.monitor_heart_outlined,
        currentLocalizations().uiConditionAndDiagnosis,
        currentLocalizations().uiPerformanceCompatibility,
      ),
      (
        'device',
        M3EIcons.devices_outlined,
        currentLocalizations().uiTerminalAndPermissions,
        currentLocalizations().uiDeviceInformationDesktopModeAccessibility,
      ),
      (
        'about',
        M3EIcons.info_outline_rounded,
        l.appInfo,
        appVersion.isEmpty ? 'Dextop' : 'Dextop $appVersion',
      ),
    ];
    final colors = Theme.of(context).colorScheme;
    return LayoutBuilder(
      key: const ValueKey('desktop-settings'),
      builder: (context, constraints) {
        final selected = switch (desktopSettingsSection) {
          'samsung' => (
            'samsung',
            M3EIcons.desktop_windows_outlined,
            currentLocalizations().samsungSettingsTitle,
            '',
          ),
          'diagnostics' => (
            'diagnostics',
            M3EIcons.article_outlined,
            currentLocalizations().diagnosticLog,
            '',
          ),
          _ => sections.firstWhere((item) => item.$1 == desktopSettingsSection),
        };
        return Row(
          children: [
            SizedBox(
              width: constraints.maxWidth >= 1200 ? 276 : 240,
              child: DecoratedBox(
                decoration: BoxDecoration(color: colors.surfaceContainerLow),
                child: SafeArea(
                  right: false,
                  top: !widget.desktopWindow,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Padding(
                        padding: const EdgeInsets.fromLTRB(20, 20, 16, 12),
                        child: Text(
                          l.settings,
                          style: Theme.of(context).textTheme.headlineSmall,
                        ),
                      ),
                      Expanded(
                        child: ListView.builder(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 4,
                          ),
                          itemCount: sections.length,
                          itemBuilder: (context, index) {
                            final item = sections[index];
                            final isSelected =
                                item.$1 == desktopSettingsSection ||
                                (item.$1 == 'about' &&
                                    (desktopSettingsSection == 'samsung' ||
                                        desktopSettingsSection ==
                                            'diagnostics'));
                            final enabled =
                                !widget.linuxOnly || item.$1 == 'about';
                            final fg = !enabled
                                ? colors.onSurfaceVariant.withValues(
                                    alpha: 0.38,
                                  )
                                : isSelected
                                ? colors.onSecondaryContainer
                                : colors.onSurface;
                            final iconColor = !enabled
                                ? colors.onSurfaceVariant.withValues(
                                    alpha: 0.38,
                                  )
                                : isSelected
                                ? colors.onSecondaryContainer
                                : colors.onSurfaceVariant;
                            return Padding(
                              padding: const EdgeInsets.symmetric(vertical: 2),
                              child: Material(
                                color: isSelected
                                    ? colors.secondaryContainer
                                    : Colors.transparent,
                                shape: const StadiumBorder(),
                                clipBehavior: Clip.antiAlias,
                                child: InkWell(
                                  onTap: enabled
                                      ? () => mutate(
                                          () =>
                                              desktopSettingsSection = item.$1,
                                        )
                                      : null,
                                  child: Padding(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 16,
                                      vertical: 12,
                                    ),
                                    child: Row(
                                      children: [
                                        Icon(
                                          item.$2,
                                          size: 20,
                                          color: iconColor,
                                        ),
                                        const SizedBox(width: 12),
                                        Expanded(
                                          child: Text(
                                            item.$3,
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: Theme.of(context)
                                                .textTheme
                                                .labelLarge
                                                ?.copyWith(
                                                  color: fg,
                                                  fontWeight: isSelected
                                                      ? FontWeight.w700
                                                      : FontWeight.w500,
                                                ),
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(12, 8, 12, 14),
                        child: rootMyGalaxyThemeSwitch(l, compact: true),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const M3EDivider(axis: M3EDividerAxis.vertical),
            Expanded(
              child: SafeArea(
                left: false,
                top: !widget.desktopWindow,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Padding(
                      padding: EdgeInsets.fromLTRB(
                        desktopSettingsSection == 'samsung' ||
                                desktopSettingsSection == 'diagnostics'
                            ? 16
                            : 28,
                        20,
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
                              M3EIconButton(
                                variant: M3EIconButtonVariant.standard,
                                tooltip: l.back,
                                onPressed: () => mutate(
                                  () => desktopSettingsSection = 'about',
                                ),
                                icon: const Icon(M3EIcons.arrow_back_rounded),
                              ),
                              const SizedBox(width: 4),
                            ],
                            Icon(selected.$2, color: colors.primary),
                            const SizedBox(width: 12),
                            Text(
                              selected.$3,
                              style: Theme.of(context).textTheme.headlineSmall,
                            ),
                          ],
                        ),
                      ),
                    ),
                    const M3EDivider(),
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
                          child: Align(
                            alignment: Alignment.topCenter,
                            child: ConstrainedBox(
                              constraints: const BoxConstraints(maxWidth: 760),
                              child: _desktopSettingsDetail(l),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
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
        'keyboard' => KeyboardSettingsPage(embedded: true, isRunning: active),
        'mouse' => MouseSettingsPage(
          bridge: bridge,
          isRunning: active,
          embedded: true,
        ),
        'interaction' => ListView(
          padding: const EdgeInsets.all(20),
          children: [
            DextopFeaturesPage(
              isRunning: active,
              embedded: true,
              category: 'interaction',
              onAddHomeShortcut: addHomeScreenShortcut,
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
          onExperimentalFeaturesChanged: _loadHomeSelections,
          receivePreReleases: receivePreReleases,
          onReceivePreReleasesChanged: isPlayDistribution
              ? null
              : _setReceivePreReleases,
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

  /// Settings that need Dextop's setup are unavailable while only Linux is set up.
  VoidCallback? _unlessLinuxOnly(VoidCallback action) =>
      widget.linuxOnly ? null : action;

  M3EListItem _categoryTile(
    IconData icon,
    String title,
    String subtitle,
    VoidCallback? action, {
    String? badge,
  }) {
    final colors = Theme.of(context).colorScheme;
    return M3EListItem(
      enabled: action != null,
      leading: Icon(icon),
      headline: title,
      supportingText: subtitle,
      supportingMaxLines: null,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (badge != null) ...[
            DextopStatusPill(
              label: badge,
              containerColor: colors.errorContainer,
              contentColor: colors.onErrorContainer,
            ),
            const SizedBox(width: 8),
          ],
          const Icon(M3EIcons.chevron_right_rounded),
        ],
      ),
      onTap: action,
    );
  }

  void _openFeatureCategory(String category, {bool launcherOnly = false}) {
    Navigator.of(context).push(
      dextopRoute<void>(
        DextopFeaturesPage(
          isRunning: active,
          category: category,
          launcherOnly: launcherOnly,
          ensureDesktopRunning: ensureDesktopRunning,
          onAddHomeShortcut: addHomeScreenShortcut,
        ),
      ),
    );
  }

  void _openDisplaySettings(AppLocalizations l) {
    AppAnalytics.screen('display_settings');
    Navigator.of(context).push(
      dextopRoute<void>(
        StatefulBuilder(
          builder: (routeContext, updateRoute) => Scaffold(
            backgroundColor: Theme.of(routeContext).colorScheme.surface,
            appBar: M3EAppBar.top(
              automaticallyImplyLeading: true,
              titleText: l.display,
            ),
            body: _displaySettingsContent(l, updateRoute),
          ),
        ),
      ),
    );
  }

  void _openMouseSettings(AppLocalizations l) {
    AppAnalytics.screen('mouse_settings');
    Navigator.of(context).push(
      dextopRoute<void>(MouseSettingsPage(bridge: bridge, isRunning: active)),
    );
  }

  Widget _displaySettingsContent(
    AppLocalizations l,
    StateSetter updateRoute,
  ) => ListView(
    padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
    children: [
      DextopSection(currentLocalizations().uiDisplayCategory),
      DextopGroup(
        children: [
          M3EListItem(
            leading: const Icon(M3EIcons.desktop_windows_outlined),
            headline: currentLocalizations().displaySettingsTitle,
            supportingText: currentLocalizations().displaySettingsSummary,
            supportingMaxLines: null,
            trailing: const Icon(M3EIcons.chevron_right_rounded),
            onTap: () => Navigator.of(context)
                .push(dextopRoute<void>(DisplaySettingsPage(bridge: bridge))),
          ),
          M3EListItem(
            enabled: !active,
            leading: const Icon(M3EIcons.account_tree_outlined),
            headline: l.mirrorBackend,
            supportingText: _mirrorBackendLabel(l, mirrorBackend),
            trailing: const Icon(M3EIcons.chevron_right_rounded),
            onTap: active
                ? null
                : () => _selectMirrorBackend(context, l, updateRoute),
          ),
          M3EListItem(
            leading: const Icon(M3EIcons.space_dashboard_outlined),
            headline: l.windowManager,
            supportingText: _windowManagerLabel(l, windowManager),
            trailing: const Icon(M3EIcons.chevron_right_rounded),
            onTap: () => _selectWindowManager(context, l, updateRoute),
          ),
          if (windowManager != 'system')
            M3EListItem(
              leading: const Icon(M3EIcons.palette_outlined),
              headline: localText(context, 'uiDextopBook'),
              supportingText: localText(
                context,
                'uiAppearanceTaskbarAndStartMenu',
              ),
              trailing: const Icon(M3EIcons.chevron_right_rounded),
              onTap: () => Navigator.of(context)
                  .push(dextopRoute<void>(BookSettingsPage(bridge: bridge))),
            ),
          M3EListItem(
            leading: const Icon(M3EIcons.cast_rounded),
            headline: currentLocalizations().castMode,
            supportingText: castMode == 'receiver'
                ? currentLocalizations().castModeReceiver
                : currentLocalizations().castModeSimple,
            trailing: const Icon(M3EIcons.chevron_right_rounded),
            onTap: () => _selectCastMode(context, updateRoute),
          ),
        ],
      ),
      const SizedBox(height: 12),
      DextopFeaturesPage(
        isRunning: active,
        embedded: true,
        category: 'display',
      ),
      DextopSection(currentLocalizations().uiConvenience),
      DisplayEnvironmentSettingsCard(
        bridge: bridge,
        showDisplay: false,
        wrapInCard: true,
      ),
      const SizedBox(height: 10),
      DextopGroup(
        children: [
          DextopToggle(
            title: l.secureDisplay,
            subtitle: l.secureDisplayDescription,
            icon: M3EIcons.lock_rounded,
            value: secure,
            enabled: !active,
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
        ],
      ),
    ],
  );

  String _windowManagerLabel(AppLocalizations l, String value) =>
      switch (value) {
        'dextop_plasma' => 'Dextop Book',
        'dextop_plasma_v2' => 'Dextop Book',
        'dextop_book' => 'Dextop Book',
        _ => l.windowManagerSystem,
      };

  Future<void> _selectWindowManager(
    BuildContext routeContext,
    AppLocalizations l,
    StateSetter updateRoute,
  ) async {
    final selected = await M3EDialog.show<String>(
      routeContext,
      dialog: Builder(
        builder: (dialogContext) {
          final colors = Theme.of(dialogContext).colorScheme;
          return M3EDialog(
            icon: const Icon(M3EIcons.space_dashboard_outlined),
            title: l.windowManager,
            content: DextopGroup(
              color: colors.surfaceContainer,
              children: [
                DextopChoice<String>(
                  value: 'system',
                  groupValue: windowManager,
                  allowReselect: true,
                  title: l.windowManagerSystem,
                  subtitle: l.windowManagerSystemDescription,
                  onChanged: (choice) => Navigator.pop(dialogContext, choice),
                ),
                DextopChoice<String>(
                  value: 'dextop_book',
                  groupValue: windowManager,
                  allowReselect: true,
                  title: localText(context, 'uiDextopBook'),
                  subtitle: localText(
                    context,
                    'uiAMaterial3ExpressiveDesktopDateAndStatusOnTopADockAtTheBottomCentre',
                  ),
                  onChanged: (choice) => Navigator.pop(dialogContext, choice),
                ),
              ],
            ),
          );
        },
      ),
    );
    if (selected == null || selected == windowManager) return;
    updateRoute(() => windowManager = selected);
    await setWindowManager(selected);
    if (active && routeContext.mounted) {
      M3ESnackbar.show(routeContext, message: l.windowManagerAppliedLive);
    }
  }

  Future<void> _selectCastMode(
    BuildContext routeContext,
    StateSetter updateRoute,
  ) async {
    final selected = await M3EDialog.show<String>(
      routeContext,
      dialog: Builder(
        builder: (dialogContext) {
          final colors = Theme.of(dialogContext).colorScheme;
          return M3EDialog(
            icon: const Icon(M3EIcons.cast_rounded),
            title: currentLocalizations().castMode,
            content: DextopGroup(
              color: colors.surfaceContainer,
              children: [
                DextopChoice<String>(
                  value: 'simple',
                  groupValue: castMode,
                  allowReselect: true,
                  title: currentLocalizations().castModeSimple,
                  subtitle: currentLocalizations().castModeSimpleDescription,
                  onChanged: (choice) => Navigator.pop(dialogContext, choice),
                ),
                DextopChoice<String>(
                  value: 'receiver',
                  groupValue: castMode,
                  allowReselect: true,
                  title: currentLocalizations().castModeReceiver,
                  subtitle: currentLocalizations().castModeReceiverDescription,
                  onChanged: (choice) => Navigator.pop(dialogContext, choice),
                ),
              ],
            ),
          );
        },
      ),
    );
    if (selected == null || selected == castMode) return;
    updateRoute(() => castMode = selected);
    await setCastMode(selected);
  }

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
    final selected = await M3EDialog.show<String>(
      routeContext,
      dialog: Builder(
        builder: (dialogContext) {
          final colors = Theme.of(dialogContext).colorScheme;
          return M3EDialog(
            icon: const Icon(M3EIcons.account_tree_outlined),
            title: l.mirrorBackend,
            content: DextopGroup(
              color: colors.surfaceContainer,
              children: [
                for (final value in const [
                  'auto',
                  'window_manager',
                  'surface_control',
                  'virtual_display',
                ])
                  DextopChoice<String>(
                    value: value,
                    groupValue: mirrorBackend,
                    allowReselect: true,
                    title: _mirrorBackendLabel(l, value),
                    subtitle: value == 'auto'
                        ? l.mirrorBackendAutoDescription
                        : null,
                    onChanged: (choice) => Navigator.pop(dialogContext, choice),
                  ),
              ],
            ),
          );
        },
      ),
    );
    if (selected == null || selected == mirrorBackend) return;
    updateRoute(() => mirrorBackend = selected);
    await setMirrorBackend(selected);
  }

  void _openDeviceSettings(AppLocalizations l) {
    AppAnalytics.screen('device_and_permissions');
    Navigator.of(context).push(
      dextopRoute<void>(
        Scaffold(
          backgroundColor: Theme.of(context).colorScheme.surface,
          appBar: M3EAppBar.top(
            automaticallyImplyLeading: true,
            titleText: currentLocalizations().uiTerminalAndPermissions,
          ),
          body: _deviceSettingsContent(l),
        ),
      ),
    );
  }

  Widget _deviceSettingsContent(AppLocalizations l) => ListView(
    padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
    children: [
      DextopGroup(
        children: [
          M3EListItem(
            leading: const Icon(M3EIcons.smartphone_rounded),
            headline: [
              manufacturer,
              model,
            ].where((e) => e.isNotEmpty).join(' '),
            supportingText:
                '${currentLocalizations().uiAndroid} $androidVersion',
          ),
          M3EListItem(
            leading: const Icon(M3EIcons.desktop_windows_rounded),
            headline: l.desktopMode,
            supportingText: desktopMode,
          ),
          actionTile(
            M3EIcons.accessibility_new_rounded,
            l.accessibilitySettings,
            l.accessibilityDescription,
            bridge.openAccessibility,
          ),
        ],
      ),
      const SizedBox(height: 12),
      const _KeepAwakeTile(),
    ],
  );

  Widget rootMyGalaxyThemeSwitch(AppLocalizations l, {bool compact = false}) {
    if (compact) {
      final colors = Theme.of(context).colorScheme;
      final modes = [
        (ThemeMode.system, l.system),
        (ThemeMode.light, l.light),
        (ThemeMode.dark, l.dark),
      ];
      return DecoratedBox(
        decoration: ShapeDecoration(
          color: colors.surfaceContainerHigh,
          shape: const StadiumBorder(),
        ),
        child: Padding(
          padding: const EdgeInsets.all(3),
          child: Row(
            children: [
              for (final (mode, label) in modes)
                Expanded(
                  child: Material(
                    color: widget.themeMode == mode
                        ? colors.secondaryContainer
                        : Colors.transparent,
                    shape: const StadiumBorder(),
                    clipBehavior: Clip.antiAlias,
                    child: InkWell(
                      onTap: () => widget.onThemeModeChanged(mode),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 8,
                        ),
                        child: FittedBox(
                          fit: BoxFit.scaleDown,
                          child: Text(
                            label,
                            maxLines: 1,
                            style: Theme.of(context).textTheme.labelMedium
                                ?.copyWith(
                                  color: widget.themeMode == mode
                                      ? colors.onSecondaryContainer
                                      : colors.onSurfaceVariant,
                                  fontWeight: widget.themeMode == mode
                                      ? FontWeight.w700
                                      : FontWeight.w500,
                                ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      );
    }
    return DextopChoiceGroup<ThemeMode>(
      choices: [
        (ThemeMode.system, l.system, M3EIcons.brightness_auto_rounded),
        (ThemeMode.light, l.light, M3EIcons.light_mode_rounded),
        (ThemeMode.dark, l.dark, M3EIcons.dark_mode_rounded),
      ],
      selected: widget.themeMode,
      onSelected: widget.onThemeModeChanged,
    );
  }

  M3EListItem actionTile(
    IconData icon,
    String title,
    String subtitle,
    VoidCallback action,
  ) => M3EListItem(
    leading: Icon(icon),
    headline: title,
    supportingText: subtitle,
    supportingMaxLines: null,
    trailing: const Icon(M3EIcons.chevron_right_rounded),
    onTap: action,
  );

  Widget errorPanel() {
    final colors = Theme.of(context).colorScheme;
    return M3ECard(
      variant: M3ECardVariant.filled,
      color: colors.errorContainer,
      borderRadius: BorderRadius.circular(24),
      padding: const EdgeInsets.all(18),
      child: Row(
        children: [
          Icon(M3EIcons.error_rounded, color: colors.onErrorContainer),
          const SizedBox(width: 14),
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
    this.embedded = false,
    super.key,
  });

  final NativeBridge bridge;
  final bool isRunning;
  final bool embedded;

  @override
  State<MouseSettingsPage> createState() => _MouseSettingsPageState();
}

class _MouseSettingsPageState extends State<MouseSettingsPage> {
  SharedPreferences? prefs;
  var loading = true;
  var pointerProfile = 'touchpad';
  var naturalScroll = true;
  var pointerHapticsEnabled = true;
  var pointerHapticsStrength = 70;

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
      pointerHapticsEnabled = store.getBool('pointer_haptics_enabled') ?? true;
      pointerHapticsStrength = (store.getInt('pointer_haptics_strength') ?? 70)
          .clamp(1, 100);
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
      M3ESnackbar.show(context, message: error.toString());
    }
  }

  void _setNaturalScroll(bool value) {
    setState(() => naturalScroll = value);
    unawaited(prefs?.setBool('virtual_mouse_natural_scroll', value));
  }

  void _setPointerHapticsEnabled(bool value) {
    setState(() => pointerHapticsEnabled = value);
    unawaited(prefs?.setBool('pointer_haptics_enabled', value));
  }

  void _setPointerHapticsStrength(double value) {
    final strength = value.round().clamp(1, 100);
    setState(() => pointerHapticsStrength = strength);
    unawaited(prefs?.setInt('pointer_haptics_strength', strength));
  }

  Future<void> _pickOption<T>({
    required String title,
    required IconData icon,
    required T value,
    required Map<T, String> options,
    required ValueChanged<T> onChanged,
  }) async {
    final selected = await M3EDialog.show<T>(
      context,
      dialog: Builder(
        builder: (dialogContext) {
          final colors = Theme.of(dialogContext).colorScheme;
          return M3EDialog(
            icon: Icon(icon),
            title: title,
            content: DextopGroup(
              color: colors.surfaceContainer,
              children: [
                for (final entry in options.entries)
                  DextopChoice<T>(
                    value: entry.key,
                    groupValue: value,
                    allowReselect: true,
                    title: entry.value,
                    onChanged: (next) => Navigator.pop(dialogContext, next),
                  ),
              ],
            ),
          );
        },
      ),
    );
    if (selected != null && selected != value) {
      onChanged(selected);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final colors = Theme.of(context).colorScheme;
    final profileOptions = {
      'touchpad': l.virtualTouchpad,
      'mouse': l.virtualPointerMouse,
      'software': l.virtualPointerSoftware,
    };
    final scrollOptions = {
      true: l.virtualMouseNaturalScroll,
      false: l.virtualMouseStandardScroll,
    };
    final content = ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
      children: [
        DextopGroup(
          children: [
            M3EListItem(
              leading: const Icon(M3EIcons.mouse_outlined),
              headline: l.virtualPointerProfile,
              supportingText: switch (pointerProfile) {
                'touchpad' => l.virtualTouchpadDescription,
                'mouse' => l.virtualPointerMouseDescription,
                _ => l.virtualPointerSoftwareDescription,
              },
              supportingMaxLines: null,
              enabled: !loading,
              trailing: DextopDropdownButton(
                label: profileOptions[pointerProfile] ?? l.virtualTouchpad,
                enabled: !loading,
                onPressed: loading
                    ? null
                    : () => _pickOption<String>(
                        title: l.virtualPointerProfile,
                        icon: M3EIcons.mouse_outlined,
                        value: pointerProfile,
                        options: profileOptions,
                        onChanged: _setPointerProfile,
                      ),
              ),
              onTap: loading
                  ? null
                  : () => _pickOption<String>(
                      title: l.virtualPointerProfile,
                      icon: M3EIcons.mouse_outlined,
                      value: pointerProfile,
                      options: profileOptions,
                      onChanged: _setPointerProfile,
                    ),
            ),
            M3EListItem(
              leading: const Icon(M3EIcons.swap_vert_rounded),
              headline: l.virtualMouseScrollDirection,
              enabled: !loading,
              trailing: DextopDropdownButton(
                label: scrollOptions[naturalScroll] ?? '',
                enabled: !loading,
                onPressed: loading
                    ? null
                    : () => _pickOption<bool>(
                        title: l.virtualMouseScrollDirection,
                        icon: M3EIcons.swap_vert_rounded,
                        value: naturalScroll,
                        options: scrollOptions,
                        onChanged: _setNaturalScroll,
                      ),
              ),
              onTap: loading
                  ? null
                  : () => _pickOption<bool>(
                      title: l.virtualMouseScrollDirection,
                      icon: M3EIcons.swap_vert_rounded,
                      value: naturalScroll,
                      options: scrollOptions,
                      onChanged: _setNaturalScroll,
                    ),
            ),
            DextopToggle(
              icon: M3EIcons.vibration_rounded,
              value: pointerHapticsEnabled,
              enabled: !loading,
              onChanged: loading ? null : _setPointerHapticsEnabled,
              title: l.pointerHaptics,
              subtitle: l.pointerHapticsDescription,
            ),
          ],
        ),
        if (pointerHapticsEnabled) ...[
          const SizedBox(height: 12),
          DextopSliderTile(
            icon: M3EIcons.tune_rounded,
            title: l.hapticsStrength,
            valueLabel: '$pointerHapticsStrength%',
            value: pointerHapticsStrength.toDouble(),
            min: 1,
            max: 100,
            divisions: 99,
            onChanged: loading
                ? null
                : (value) =>
                      setState(() => pointerHapticsStrength = value.round()),
            onChangeEnd: loading ? null : _setPointerHapticsStrength,
          ),
        ],
      ],
    );
    if (widget.embedded) return content;
    return Scaffold(
      backgroundColor: colors.surface,
      appBar: M3EAppBar.top(
        automaticallyImplyLeading: true,
        titleText: l.mouseSettingsTitle,
      ),
      body: content,
    );
  }
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
  var autoShell = true;
  var hiddenAutoDisplay = false;
  var scrcpyStreaming = false;
  var correctFullscreenOverlap = false;
  var correctAutoSplit = false;
  var deck30Fps = false;
  var deckVsync = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final preferences = await SharedPreferences.getInstance();
    var savedHiddenDisplay =
        preferences.getBool('android_auto_hidden_display') ?? false;
    var savedDecoratedDisplay =
        preferences.getBool('android_auto_scrcpy_streaming') ?? false;
    final migratedTo21 =
        preferences.getBool('android_auto_21_default_migrated') ?? false;
    if (!migratedTo21) {
      savedHiddenDisplay = true;
      savedDecoratedDisplay = true;
      await preferences.setBool('android_auto_hidden_display', true);
      await preferences.setBool('android_auto_scrcpy_streaming', true);
      await preferences.setBool('android_auto_21_default_migrated', true);
    }
    if (!mounted) return;
    setState(() {
      matchPhoneOrientation =
          preferences.getBool('android_auto_match_phone_orientation') ?? true;
      autoShell =
          (preferences.getString('android_auto_shell') ?? 'dextop') == 'dextop';
      deck30Fps = preferences.getBool('android_auto_deck_30fps') ?? false;
      deckVsync = preferences.getBool('android_auto_deck_vsync') ?? true;
      hiddenAutoDisplay = savedHiddenDisplay;
      scrcpyStreaming = savedDecoratedDisplay;
      correctFullscreenOverlap =
          preferences.getBool('android_auto_correct_fullscreen_overlap') ??
          false;
      correctAutoSplit =
          preferences.getBool('android_auto_correct_split_transfer') ?? false;
      loading = false;
    });
  }

  Future<void> _setCarCompanionMode(int value) async {
    setState(() {
      hiddenAutoDisplay = value != 0;
      scrcpyStreaming = value == 2;
    });
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('android_auto_hidden_display', value != 0);
    await preferences.setBool('android_auto_scrcpy_streaming', value == 2);
    await preferences.setBool('android_auto_21_default_migrated', true);
  }

  Future<void> _setMatchPhoneOrientation(bool value) async {
    setState(() => matchPhoneOrientation = value);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('android_auto_match_phone_orientation', value);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final colors = Theme.of(context).colorScheme;
    final body = ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
      children: [
        DextopSection(localText(context, 'uiAutoHomeScreen')),
        DextopGroup(
          children: [
            DextopChoice<bool>(
              value: false,
              groupValue: autoShell,
              enabled: !loading,
              title: localText(context, 'uiAndroidDesktop'),
              subtitle: localText(context, 'uiShowTheAndroidDesktop'),
              onChanged: (value) async {
                final preferences = await SharedPreferences.getInstance();
                await preferences.setString('android_auto_shell', 'android');
                if (mounted) setState(() => autoShell = false);
              },
            ),
            DextopChoice<bool>(
              value: true,
              groupValue: autoShell,
              enabled: !loading,
              title: localText(context, 'uiDextopAuto'),
              subtitle: localText(
                context,
                'uiPrototypeCarHomeWithLargeControlsReconnectAutoToApply',
              ),
              onChanged: (value) async {
                final preferences = await SharedPreferences.getInstance();
                await preferences.setString('android_auto_shell', 'dextop');
                if (mounted) setState(() => autoShell = true);
              },
            ),
          ],
        ),
        DextopSection(l.autoSettingsOptions),
        DextopGroup(
          children: [
            DextopToggle(
              icon: M3EIcons.speed_rounded,
              title: localText(context, 'uiLimitDeckModeTo30Fps'),
              subtitle: localText(
                context,
                'uiApply30FpsPacingAndLowLatencySubmissionInputRemainsUnrestrictedReopenDeckModeToApply',
              ),
              value: deck30Fps,
              enabled: !loading,
              onChanged: loading
                  ? null
                  : (value) async {
                      final preferences = await SharedPreferences.getInstance();
                      await preferences.setBool(
                        'android_auto_deck_30fps',
                        value,
                      );
                      if (mounted) setState(() => deck30Fps = value);
                    },
            ),
            DextopToggle(
              icon: M3EIcons.sync_rounded,
              title: localText(context, 'uiDeckModeVSync'),
              subtitle: localText(
                context,
                'uiDisableVSyncWaitsForDeckRenderingAutoVideoCadenceIsUnchangedReopenDeckModeToApply',
              ),
              value: deckVsync,
              enabled: !loading,
              onChanged: loading
                  ? null
                  : (value) async {
                      final preferences = await SharedPreferences.getInstance();
                      await preferences.setBool(
                        'android_auto_deck_vsync',
                        value,
                      );
                      if (mounted) setState(() => deckVsync = value);
                    },
            ),
            DextopToggle(
              icon: M3EIcons.vertical_split_outlined,
              title: localText(context, 'uiCorrectSplitScreenTransferToPhone'),
              subtitle: localText(
                context,
                'uiReturnsAutoAppsSentToPhoneSplitScreenToASideBySideLayoutIn21',
              ),
              value: correctAutoSplit,
              enabled: !loading,
              onChanged: loading
                  ? null
                  : (value) async {
                      setState(() => correctAutoSplit = value);
                      final preferences = await SharedPreferences.getInstance();
                      await preferences.setBool(
                        'android_auto_correct_split_transfer',
                        value,
                      );
                    },
            ),
            DextopToggle(
              icon: M3EIcons.fit_screen_outlined,
              title: localText(context, 'uiCorrectFullscreenTaskbarOverlap'),
              subtitle: localText(
                context,
                'uiFitsFullscreenAndMaximizedAppsAboveTheTaskbarSwitchingToWindowedModeWhenNeeded',
              ),
              value: correctFullscreenOverlap,
              enabled: !loading,
              onChanged: loading
                  ? null
                  : (value) async {
                      setState(() => correctFullscreenOverlap = value);
                      final preferences = await SharedPreferences.getInstance();
                      await preferences.setBool(
                        'android_auto_correct_fullscreen_overlap',
                        value,
                      );
                    },
            ),
            DextopToggle(
              icon: M3EIcons.screen_rotation_alt_outlined,
              title: l.autoMatchPhoneOrientation,
              subtitle: l.autoMatchPhoneOrientationDescription,
              value: matchPhoneOrientation,
              enabled: !loading,
              onChanged: loading ? null : _setMatchPhoneOrientation,
            ),
            M3EListItem(
              leading: const Icon(M3EIcons.info_outline_rounded),
              headline: l.display,
              supportingText: l.autoDisplayModeDescription,
              supportingMaxLines: null,
            ),
          ],
        ),
        DextopSection(localText(context, 'uiCarCompanionMode')),
        DextopGroup(
          children: [
            DextopChoice<int>(
              icon: M3EIcons.layers_outlined,
              title: localText(context, 'uiCarCompanion113Legacy'),
              subtitle: localText(
                context,
                'uiUsesTheOriginalOverlayDisplayAndMirrorsItToAndroidAuto',
              ),
              value: 0,
              groupValue: scrcpyStreaming ? 2 : (hiddenAutoDisplay ? 1 : 0),
              enabled: !loading,
              onChanged: (value) {
                if (!loading) _setCarCompanionMode(value);
              },
            ),
            DextopChoice<int>(
              icon: M3EIcons.stream_rounded,
              title: localText(context, 'uiCarCompanion21Stable'),
              subtitle: localText(
                context,
                'uiConnectsADecoratedVirtualDisplayDirectlyToTheAndroidAutoSurface',
              ),
              value: 2,
              groupValue: scrcpyStreaming ? 2 : (hiddenAutoDisplay ? 1 : 0),
              enabled: !loading,
              onChanged: (value) {
                if (!loading) _setCarCompanionMode(value);
              },
            ),
          ],
        ),
      ],
    );
    if (widget.embedded) return body;
    return Scaffold(
      backgroundColor: colors.surface,
      appBar: M3EAppBar.top(
        automaticallyImplyLeading: true,
        titleText: l.autoSettingsTitle,
      ),
      body: body,
    );
  }
}
