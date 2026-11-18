part of 'main.dart';

class AppInfoPage extends StatelessWidget {
  const AppInfoPage({
    required this.bridge,
    required this.appVersion,
    required this.updateAvailable,
    required this.checking,
    required this.checkSucceeded,
    required this.checkError,
    required this.onCheck,
    required this.onShowUpdate,
    this.embedded = false,
    this.isRunning = false,
    this.onOpenSamsungSettings,
    this.onOpenDiagnosticLog,
    this.onExperimentalFeaturesChanged,
    this.receivePreReleases = false,
    this.onReceivePreReleasesChanged,
    super.key,
  });

  final NativeBridge bridge;
  final String appVersion;
  final bool updateAvailable;
  final bool checking;
  final bool checkSucceeded;
  final String? checkError;
  final Future<void> Function() onCheck;
  final Future<void> Function() onShowUpdate;
  final bool embedded;
  final bool isRunning;
  final VoidCallback? onOpenSamsungSettings;
  final VoidCallback? onOpenDiagnosticLog;
  final VoidCallback? onExperimentalFeaturesChanged;
  final bool receivePreReleases;

  /// Null where updates do not come from GitHub releases (the Play build).
  final ValueChanged<bool>? onReceivePreReleasesChanged;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final colors = Theme.of(context).colorScheme;
    final content = ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
      children: [
        M3ECard(
          variant: M3ECardVariant.filled,
          borderRadius: BorderRadius.circular(26),
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              ClipRRect(
                borderRadius: const BorderRadius.all(Radius.circular(24)),
                child: const Image(
                  image: AssetImage('assets/dextop_icon.png'),
                  width: 96,
                  height: 96,
                ),
              ),
              const SizedBox(height: 16),
              Text(
                currentLocalizations().appName,
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 6),
              if (updateAvailable)
                M3EButton.tonal(
                  onPressed: onShowUpdate,
                  icon: const Icon(M3EIcons.system_update_alt_rounded),
                  label: Text(l.updateAvailable),
                )
              else
                Text(
                  appVersion.isEmpty ? '—' : appVersion,
                  style: Theme.of(context).textTheme.bodyMedium
                      ?.copyWith(color: colors.onSurfaceVariant),
                ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        FutureBuilder<Map<String, dynamic>>(
          future: bridge.status(),
          builder: (context, snapshot) {
            final info = snapshot.data;
            final included = info?['embeddedPrivilegeIncluded'] == true;
            final selected = info?['embeddedPrivilegeSelected'] == true;
            final binderAlive = info?['shizukuBinderAlive'] == true;
            final granted = info?['shizukuGranted'] == true;
            final notifications = info?['embeddedNotificationGranted'] == true;
            return M3ECard(
              variant: M3ECardVariant.filled,
              borderRadius: BorderRadius.circular(26),
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  M3EListItem(
                    leading: snapshot.connectionState == ConnectionState.waiting
                        ? const SizedBox.square(
                            dimension: 24,
                            child: M3ELoadingIndicator(size: 24),
                          )
                        : Icon(
                            included
                                ? M3EIcons.memory_rounded
                                : M3EIcons.memory_outlined,
                          ),
                    headline: l.appInfoEmbeddedBinder,
                    supportingText: included
                        ? l.appInfoEmbeddedBinderIncluded
                        : l.appInfoEmbeddedBinderNotIncluded,
                  ),
                  if (included && info != null) ...[
                    const M3EDivider(inset: M3EDividerInset.inset),
                    const SizedBox(height: 4),
                    _AppInfoStatusRow(
                      label: l.appInfoEmbeddedBinderProvider,
                      value: selected
                          ? l.appInfoEmbeddedBinderSelected
                          : l.appInfoEmbeddedBinderStandby,
                      active: selected,
                    ).buildRow(context),
                    _AppInfoStatusRow(
                      label: l.appInfoEmbeddedBinderConnection,
                      value: binderAlive
                          ? l.appInfoStatusConnected
                          : l.appInfoStatusDisconnected,
                      active: binderAlive,
                    ).buildRow(context),
                    _AppInfoStatusRow(
                      label: l.appInfoEmbeddedBinderPermission,
                      value: granted
                          ? l.appInfoStatusGranted
                          : l.appInfoStatusNotGranted,
                      active: granted,
                    ).buildRow(context),
                    _AppInfoStatusRow(
                      label: l.appInfoEmbeddedBinderNotifications,
                      value: notifications
                          ? l.appInfoStatusGranted
                          : l.appInfoStatusNotGranted,
                      active: notifications,
                    ).buildRow(context),
                    const SizedBox(height: 4),
                  ],
                ],
              ),
            );
          },
        ),
        const SizedBox(height: 12),
        DextopGroup(
          children: [
            M3EListItem(
              leading: checking
                  ? const SizedBox.square(
                      dimension: 24,
                      child: M3ELoadingIndicator(size: 24),
                    )
                  : Icon(
                      updateAvailable
                          ? M3EIcons.system_update_alt_rounded
                          : M3EIcons.update_rounded,
                    ),
              headline: l.checkForUpdates,
              supportingText: checking
                  ? l.checkingForUpdates
                  : updateAvailable
                  ? l.updateAvailable
                  : checkError != null
                  ? l.updateCheckFailed
                  : checkSucceeded
                  ? l.upToDate
                  : l.updateNotChecked,
              trailing: const Icon(M3EIcons.refresh_rounded),
              enabled: !checking,
              onTap: checking ? null : onCheck,
            ),
            // Beta builds always follow pre-releases, so the switch is only
            // offered on stable GitHub builds.
            if (onReceivePreReleasesChanged != null &&
                !(appVersion.isNotEmpty && isPreRelease(appVersion)))
              _PreReleaseSwitch(
                initial: receivePreReleases,
                onChanged: onReceivePreReleasesChanged!,
              ),
          ],
        ),
        const SizedBox(height: 12),
        DextopGroup(
          children: [
            M3EListItem(
              leading: const Icon(M3EIcons.code_rounded),
              headline: currentLocalizations().uiGitHub,
              supportingText: currentLocalizations().uiGitHubRepository,
              trailing: const Icon(M3EIcons.open_in_new_rounded),
              onTap: () => bridge.openUrl('https://github.com/NarYuki/Dextop'),
            ),
            M3EListItem(
              leading: const Icon(M3EIcons.description_outlined),
              headline: l.licenses,
              supportingText: l.licensesDescription,
              trailing: const Icon(M3EIcons.chevron_right_rounded),
              onTap: () => showLicensePage(
                context: context,
                applicationName: currentLocalizations().appName,
                applicationVersion: appVersion,
                applicationIcon: ClipRRect(
                  borderRadius: const BorderRadius.all(Radius.circular(18)),
                  child: const Image(
                    image: AssetImage('assets/dextop_icon.png'),
                    width: 64,
                    height: 64,
                  ),
                ),
              ),
            ),
            M3EListItem(
              leading: const Icon(M3EIcons.article_outlined),
              headline: currentLocalizations().diagnosticLog,
              supportingText: currentLocalizations().diagnosticLogDescription,
              trailing: const Icon(M3EIcons.chevron_right_rounded),
              onTap:
                  onOpenDiagnosticLog ??
                  () => Navigator.of(
                    context,
                  ).push(dextopRoute<void>(_DiagnosticLogPage(bridge: bridge))),
            ),
          ],
        ),
        const SizedBox(height: 12),
        DextopGroup(
          children: [
            M3EListItem(
              leading: const Icon(M3EIcons.science_outlined),
              headline: currentLocalizations().uiExperimentalFeatures,
              trailing: const Icon(M3EIcons.chevron_right_rounded),
              onTap: () => Navigator.of(context).push(
                dextopRoute<void>(
                  _ExperimentalFeaturesPage(
                    bridge: bridge,
                    isRunning: isRunning,
                    onOpenSamsungSettings: onOpenSamsungSettings,
                    onChanged: onExperimentalFeaturesChanged,
                  ),
                ),
              ),
            ),
          ],
        ),
      ],
    );
    return embedded
        ? content
        : Scaffold(
            backgroundColor: colors.surface,
            appBar: M3EAppBar.top(
              automaticallyImplyLeading: true,
              titleText: l.appInfo,
            ),
            body: content,
          );
  }
}

class _ExperimentalFeaturesPage extends StatelessWidget {
  const _ExperimentalFeaturesPage({
    required this.bridge,
    required this.isRunning,
    this.onOpenSamsungSettings,
    this.onChanged,
  });

  final NativeBridge bridge;
  final bool isRunning;
  final VoidCallback? onOpenSamsungSettings;
  final VoidCallback? onChanged;

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Theme.of(context).colorScheme.surface,
    appBar: M3EAppBar.top(
      automaticallyImplyLeading: true,
      titleText: currentLocalizations().uiExperimentalFeatures,
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
      children: [
        DisplayEnvironmentSettingsCard(bridge: bridge, showConvenience: false),
        const SizedBox(height: 12),
        _MacosCompatibilityExperimentalTile(onChanged: onChanged),
        const SizedBox(height: 12),
        LinuxFastModeExperimentalTile(onChanged: onChanged),
        const SizedBox(height: 12),
        _GamepadExperimentalTile(isRunning: isRunning),
        const SizedBox(height: 12),
        _ForceLaptopModeExperimentalTile(isRunning: isRunning),
        const SizedBox(height: 12),
        _SamsungExperimentalSettingsTile(
          bridge: bridge,
          isRunning: isRunning,
          onOpenSettings: onOpenSamsungSettings,
        ),
      ],
    ),
  );
}

class _MacosCompatibilityExperimentalTile extends StatefulWidget {
  const _MacosCompatibilityExperimentalTile({this.onChanged});
  final VoidCallback? onChanged;

  @override
  State<_MacosCompatibilityExperimentalTile> createState() =>
      _MacosCompatibilityExperimentalTileState();
}

class _MacosCompatibilityExperimentalTileState
    extends State<_MacosCompatibilityExperimentalTile> {
  bool enabled = false;
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final preferences = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      enabled = preferences.getBool(macosCompatibilityPreferenceKey) ?? false;
      loading = false;
    });
  }

  Future<void> _update(bool value) async {
    setState(() => loading = true);
    try {
      final preferences = await SharedPreferences.getInstance();
      final saved = await preferences.setBool(
        macosCompatibilityPreferenceKey,
        value,
      );
      if (!mounted) return;
      if (saved) {
        setState(() => enabled = value);
        widget.onChanged?.call();
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => DextopGroup(
    children: [
      DextopToggle(
        icon: M3EIcons.computer_rounded,
        value: enabled,
        enabled: !loading,
        onChanged: loading ? null : _update,
        title: linuxText(context, 'uiMacOSCompatibility'),
        subtitle: linuxText(
          context,
          'uiShowMacOSCompatibilityInLinuxSettingsAppSupportIsLimited',
        ),
      ),
    ],
  );
}

class _GamepadExperimentalTile extends StatefulWidget {
  const _GamepadExperimentalTile({required this.isRunning});

  final bool isRunning;

  @override
  State<_GamepadExperimentalTile> createState() =>
      _GamepadExperimentalTileState();
}

class _GamepadExperimentalTileState extends State<_GamepadExperimentalTile> {
  bool enabled = false;
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final preferences = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      enabled = preferences.getBool('experimental_gamepad') ?? false;
      loading = false;
    });
  }

  Future<void> _update(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('experimental_gamepad', value);
    if (mounted) setState(() => enabled = value);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return DextopGroup(
      children: [
        DextopToggle(
          icon: M3EIcons.sports_esports_rounded,
          value: enabled,
          enabled: !loading && !widget.isRunning,
          onChanged: loading || widget.isRunning ? null : _update,
          title: l.experimentalGamepad,
          subtitle: l.experimentalGamepadDescription,
        ),
      ],
    );
  }
}

class _ForceLaptopModeExperimentalTile extends StatefulWidget {
  const _ForceLaptopModeExperimentalTile({required this.isRunning});

  final bool isRunning;

  @override
  State<_ForceLaptopModeExperimentalTile> createState() =>
      _ForceLaptopModeExperimentalTileState();
}

class _ForceLaptopModeExperimentalTileState
    extends State<_ForceLaptopModeExperimentalTile> {
  bool enabled = false;
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final preferences = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      enabled = preferences.getBool('experimental_force_laptop_mode') ?? false;
      loading = false;
    });
  }

  Future<void> _update(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('experimental_force_laptop_mode', value);
    if (mounted) setState(() => enabled = value);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return DextopGroup(
      children: [
        DextopToggle(
          icon: M3EIcons.laptop_chromebook_rounded,
          value: enabled,
          enabled: !loading && !widget.isRunning,
          onChanged: loading || widget.isRunning ? null : _update,
          title: l.experimentalForceLaptopMode,
          subtitle: l.experimentalForceLaptopModeDescription,
        ),
      ],
    );
  }
}

class _AppInfoStatusRow {
  const _AppInfoStatusRow({
    required this.label,
    required this.value,
    required this.active,
  });

  final String label;
  final String value;
  final bool active;

  Widget buildRow(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return DextopKeyValueRow(
      leading: Icon(
        active ? M3EIcons.check_circle_rounded : M3EIcons.cancel_outlined,
        size: 20,
        color: active ? colors.primary : colors.onSurfaceVariant,
      ),
      label: label,
      value: value,
      valueColor: active ? colors.onSurface : colors.onSurfaceVariant,
    );
  }
}

class _DiagnosticLogPage extends StatefulWidget {
  const _DiagnosticLogPage({required this.bridge, this.embedded = false});
  final NativeBridge bridge;
  final bool embedded;

  @override
  State<_DiagnosticLogPage> createState() => _DiagnosticLogPageState();
}

class _DiagnosticLogPageState extends State<_DiagnosticLogPage> {
  String report = '';
  bool loading = true;

  @override
  void initState() {
    super.initState();
    AppAnalytics.screen('diagnostics');
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    final value = await widget.bridge.diagnosticReport();
    if (mounted) {
      setState(() {
        report = value;
        loading = false;
      });
    }
  }

  Future<void> clear() async {
    await widget.bridge.clearDiagnosticLog();
    await load();
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final actions = <Widget>[
      M3EIconButton(
        variant: M3EIconButtonVariant.standard,
        tooltip: currentLocalizations().copyDiagnosticLog,
        onPressed: report.isEmpty
            ? null
            : () {
                Clipboard.setData(ClipboardData(text: report));
                M3ESnackbar.show(
                  context,
                  message: linuxText(context, 'uiCopied'),
                );
              },
        icon: const Icon(M3EIcons.copy_rounded),
      ),
      M3EIconButton(
        variant: M3EIconButtonVariant.standard,
        tooltip: currentLocalizations().shareDiagnosticLog,
        onPressed: loading ? null : widget.bridge.shareDiagnosticReport,
        icon: const Icon(M3EIcons.share_rounded),
      ),
      M3EIconButton(
        variant: M3EIconButtonVariant.standard,
        tooltip: currentLocalizations().clearDiagnosticLog,
        onPressed: loading ? null : clear,
        icon: const Icon(M3EIcons.delete_sweep_rounded),
      ),
    ];
    final body = loading
        ? const Center(
            child: M3ELoadingIndicator(
              variant: M3ELoadingIndicatorVariant.contained,
            ),
          )
        : M3ERefreshIndicator.contained(
            elevation: 0,
            onRefresh: load,
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                M3ECard(
                  variant: M3ECardVariant.filled,
                  borderRadius: BorderRadius.circular(24),
                  padding: const EdgeInsets.all(18),
                  child: SelectableText(
                    report,
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 12,
                      height: 1.4,
                    ),
                  ),
                ),
              ],
            ),
          );
    if (!widget.embedded) {
      return Scaffold(
        backgroundColor: colors.surface,
        appBar: M3EAppBar.top(
          automaticallyImplyLeading: true,
          titleText: currentLocalizations().diagnosticLog,
          actions: actions,
        ),
        body: body,
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Align(
          alignment: Alignment.centerRight,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 4, 12, 4),
            child: Row(mainAxisSize: MainAxisSize.min, children: actions),
          ),
        ),
        const M3EDivider(),
        Expanded(child: body),
      ],
    );
  }
}

class _KeepAwakeTile extends StatefulWidget {
  const _KeepAwakeTile();

  @override
  State<_KeepAwakeTile> createState() => _KeepAwakeTileState();
}

class _KeepAwakeTileState extends State<_KeepAwakeTile> {
  var enabled = false;

  @override
  void initState() {
    super.initState();
    SharedPreferences.getInstance().then((preferences) {
      if (mounted) {
        setState(() {
          enabled = preferences.getBool('keep_awake_during_session') ?? true;
        });
      }
    });
  }

  Future<void> update(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('keep_awake_during_session', value);
    await NativeBridge.channel.invokeMethod('keepAwake', {'enabled': value});
    if (mounted) setState(() => enabled = value);
  }

  @override
  Widget build(BuildContext context) => DextopGroup(
    children: [
      DextopToggle(
        icon: M3EIcons.screen_lock_portrait_rounded,
        value: enabled,
        onChanged: update,
        title: currentLocalizations().uiDoNotSleepWhileRunningDextop,
        subtitle: currentLocalizations()
            .uiPreventsTheScreenFromTurningOffAutomatically,
      ),
    ],
  );
}

@pragma('vm:entry-point')
void overlayMain() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const OverlayApp());
}

/// Keeps its own value: the page is pushed with a snapshot of the parent's
/// state, so a plain switch would never redraw after a tap.
class _PreReleaseSwitch extends StatefulWidget {
  const _PreReleaseSwitch({required this.initial, required this.onChanged});

  final bool initial;
  final ValueChanged<bool> onChanged;

  @override
  State<_PreReleaseSwitch> createState() => _PreReleaseSwitchState();
}

class _PreReleaseSwitchState extends State<_PreReleaseSwitch> {
  late var _value = widget.initial;

  @override
  Widget build(BuildContext context) => DextopToggle(
    icon: M3EIcons.science_outlined,
    value: _value,
    onChanged: (value) {
      setState(() => _value = value);
      widget.onChanged(value);
    },
    title: linuxText(context, 'uiReceivePreReleases'),
    subtitle: linuxText(context, 'uiReceivePreReleasesDescription'),
  );
}
