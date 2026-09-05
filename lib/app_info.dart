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

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final content = ListView(
      padding: EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Column(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.all(Radius.circular(24)),
                  child: Image(
                    image: AssetImage('assets/dextop_icon.png'),
                    width: 96,
                    height: 96,
                  ),
                ),
                SizedBox(height: 16),
                Text(
                  currentLocalizations().appName,
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                SizedBox(height: 4),
                if (updateAvailable)
                  TextButton.icon(
                    onPressed: onShowUpdate,
                    icon: Icon(Icons.system_update_alt_rounded),
                    label: Text(l.updateAvailable),
                  )
                else
                  Text(
                    appVersion.isEmpty ? '—' : appVersion,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
              ],
            ),
          ),
        ),
        SizedBox(height: 12),
        FutureBuilder<Map<String, dynamic>>(
          future: bridge.status(),
          builder: (context, snapshot) {
            final info = snapshot.data;
            final included = info?['embeddedPrivilegeIncluded'] == true;
            final selected = info?['embeddedPrivilegeSelected'] == true;
            final binderAlive = info?['shizukuBinderAlive'] == true;
            final granted = info?['shizukuGranted'] == true;
            final notifications = info?['embeddedNotificationGranted'] == true;
            return Card(
              child: Column(
                children: [
                  ListTile(
                    leading: snapshot.connectionState == ConnectionState.waiting
                        ? const SizedBox.square(
                            dimension: 24,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : Icon(
                            included
                                ? Icons.memory_rounded
                                : Icons.memory_outlined,
                          ),
                    title: Text(l.appInfoEmbeddedBinder),
                    subtitle: Text(
                      included
                          ? l.appInfoEmbeddedBinderIncluded
                          : l.appInfoEmbeddedBinderNotIncluded,
                    ),
                  ),
                  if (included && info != null) ...[
                    const Divider(height: 1),
                    _AppInfoStatusRow(
                      label: l.appInfoEmbeddedBinderProvider,
                      value: selected
                          ? l.appInfoEmbeddedBinderSelected
                          : l.appInfoEmbeddedBinderStandby,
                      active: selected,
                    ),
                    _AppInfoStatusRow(
                      label: l.appInfoEmbeddedBinderConnection,
                      value: binderAlive
                          ? l.appInfoStatusConnected
                          : l.appInfoStatusDisconnected,
                      active: binderAlive,
                    ),
                    _AppInfoStatusRow(
                      label: l.appInfoEmbeddedBinderPermission,
                      value: granted
                          ? l.appInfoStatusGranted
                          : l.appInfoStatusNotGranted,
                      active: granted,
                    ),
                    _AppInfoStatusRow(
                      label: l.appInfoEmbeddedBinderNotifications,
                      value: notifications
                          ? l.appInfoStatusGranted
                          : l.appInfoStatusNotGranted,
                      active: notifications,
                    ),
                  ],
                ],
              ),
            );
          },
        ),
        SizedBox(height: 12),
        Card(
          child: ListTile(
            leading: checking
                ? SizedBox.square(
                    dimension: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(
                    updateAvailable
                        ? Icons.system_update_alt_rounded
                        : Icons.update_rounded,
                  ),
            title: Text(l.checkForUpdates),
            subtitle: Text(
              checking
                  ? l.checkingForUpdates
                  : updateAvailable
                  ? l.updateAvailable
                  : checkError != null
                  ? l.updateCheckFailed
                  : checkSucceeded
                  ? l.upToDate
                  : l.updateNotChecked,
            ),
            trailing: Icon(Icons.refresh_rounded),
            onTap: checking ? null : onCheck,
          ),
        ),
        SizedBox(height: 12),
        Card(
          child: Column(
            children: [
              ListTile(
                leading: Icon(Icons.code_rounded),
                title: Text(currentLocalizations().uiGitHub),
                subtitle: Text(currentLocalizations().uiGitHubRepository),
                trailing: Icon(Icons.open_in_new_rounded),
                onTap: () =>
                    bridge.openUrl('https://github.com/NarYuki/Dextop'),
              ),
              ListTile(
                leading: Icon(Icons.description_outlined),
                title: Text(l.licenses),
                subtitle: Text(l.licensesDescription),
                trailing: Icon(Icons.chevron_right_rounded),
                onTap: () => showLicensePage(
                  context: context,
                  applicationName: currentLocalizations().appName,
                  applicationVersion: appVersion,
                  applicationIcon: ClipRRect(
                    borderRadius: BorderRadius.all(Radius.circular(16)),
                    child: Image(
                      image: AssetImage('assets/dextop_icon.png'),
                      width: 64,
                      height: 64,
                    ),
                  ),
                ),
              ),
              ListTile(
                leading: Icon(Icons.article_outlined),
                title: Text(currentLocalizations().diagnosticLog),
                subtitle: Text(currentLocalizations().diagnosticLogDescription),
                trailing: Icon(Icons.chevron_right_rounded),
                onTap:
                    onOpenDiagnosticLog ??
                    () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => _DiagnosticLogPage(bridge: bridge),
                      ),
                    ),
              ),
            ],
          ),
        ),
        SizedBox(height: 12),
        _GamepadExperimentalTile(isRunning: isRunning),
        SizedBox(height: 12),
        _CoverDisplayExperimentalTile(isRunning: isRunning),
        SizedBox(height: 12),
        _ForceLaptopModeExperimentalTile(isRunning: isRunning),
        SizedBox(height: 12),
        _SamsungExperimentalSettingsTile(
          bridge: bridge,
          isRunning: isRunning,
          onOpenSettings: onOpenSamsungSettings,
        ),
      ],
    );
    return embedded
        ? content
        : Scaffold(
            appBar: AppBar(title: Text(l.appInfo)),
            body: content,
          );
  }
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
    return Card(
      child: SwitchListTile(
        secondary: const Icon(Icons.sports_esports_rounded),
        value: enabled,
        onChanged: loading || widget.isRunning ? null : _update,
        title: Text(l.experimentalGamepad),
        subtitle: Text(l.experimentalGamepadDescription),
      ),
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
    return Card(
      child: SwitchListTile(
        secondary: const Icon(Icons.laptop_chromebook_rounded),
        value: enabled,
        onChanged: loading || widget.isRunning ? null : _update,
        title: Text(l.experimentalForceLaptopMode),
        subtitle: Text(l.experimentalForceLaptopModeDescription),
      ),
    );
  }
}

class _CoverDisplayExperimentalTile extends StatefulWidget {
  const _CoverDisplayExperimentalTile({required this.isRunning});

  final bool isRunning;

  @override
  State<_CoverDisplayExperimentalTile> createState() =>
      _CoverDisplayExperimentalTileState();
}

class _CoverDisplayExperimentalTileState
    extends State<_CoverDisplayExperimentalTile> {
  bool enabled = false;
  bool supported = false;
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final preferences = await SharedPreferences.getInstance();
    var foldable = false;
    try {
      foldable =
          await NativeBridge.channel.invokeMethod<bool>('isFoldableDevice') ??
          false;
    } catch (_) {}
    if (!mounted) return;
    setState(() {
      enabled = preferences.getBool('experimental_cover_display') ?? false;
      supported = foldable;
      loading = false;
    });
  }

  Future<void> _update(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('experimental_cover_display', value);
    if (mounted) setState(() => enabled = value);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Card(
      child: SwitchListTile(
        secondary: const Icon(Icons.flip_to_back_rounded),
        value: enabled && supported,
        onChanged: loading || !supported || widget.isRunning ? null : _update,
        title: Text(l.experimentalCoverDisplay),
        subtitle: Text(
          supported || loading
              ? l.experimentalCoverDisplayDescription
              : l.experimentalCoverDisplayUnavailable,
        ),
      ),
    );
  }
}

class _AppInfoStatusRow extends StatelessWidget {
  const _AppInfoStatusRow({
    required this.label,
    required this.value,
    required this.active,
  });

  final String label;
  final String value;
  final bool active;

  @override
  Widget build(BuildContext context) => ListTile(
    dense: true,
    leading: Icon(
      active ? Icons.check_circle_rounded : Icons.cancel_outlined,
      color: active ? Theme.of(context).colorScheme.primary : null,
    ),
    title: Text(label),
    trailing: Text(value),
  );
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
    final actions = <Widget>[
      IconButton(
        tooltip: currentLocalizations().copyDiagnosticLog,
        onPressed: report.isEmpty
            ? null
            : () => Clipboard.setData(ClipboardData(text: report)),
        icon: Icon(Icons.copy_rounded),
      ),
      IconButton(
        tooltip: currentLocalizations().shareDiagnosticLog,
        onPressed: loading ? null : widget.bridge.shareDiagnosticReport,
        icon: Icon(Icons.share_rounded),
      ),
      PopupMenuButton<String>(
        onSelected: (value) {
          if (value == 'clear') clear();
        },
        itemBuilder: (_) => [
          PopupMenuItem(
            value: 'clear',
            child: Text(currentLocalizations().clearDiagnosticLog),
          ),
        ],
      ),
    ];
    final body = loading
        ? Center(child: CircularProgressIndicator())
        : RefreshIndicator(
            onRefresh: load,
            child: ListView(
              padding: EdgeInsets.all(16),
              children: [
                SelectableText(
                  report,
                  style: TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 12,
                    height: 1.35,
                  ),
                ),
              ],
            ),
          );
    if (!widget.embedded) {
      return Scaffold(
        appBar: AppBar(
          title: Text(currentLocalizations().diagnosticLog),
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
        const Divider(height: 1),
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
  Widget build(BuildContext context) => SwitchListTile(
    secondary: Icon(Icons.screen_lock_portrait_rounded),
    value: enabled,
    onChanged: update,
    title: Text(currentLocalizations().uiDoNotSleepWhileRunningDextop),
    subtitle: Text(
      currentLocalizations().uiPreventsTheScreenFromTurningOffAutomatically,
    ),
  );
}

@pragma('vm:entry-point')
void overlayMain() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(OverlayApp());
}
