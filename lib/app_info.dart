part of 'main.dart';

class AppInfoPage extends StatelessWidget {
  const AppInfoPage({required this.bridge, super.key});

  final NativeBridge bridge;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.appInfo)),
      body: ListView(
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
                    AppStrings.tr('appName'),
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  SizedBox(height: 4),
                  Text(
                    l.version,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
            ),
          ),
          SizedBox(height: 12),
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: Icon(Icons.code_rounded),
                  title: Text(AppStrings.tr('uiGitHub')),
                  subtitle: Text(AppStrings.tr('uiGitHubRepository')),
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
                    applicationName: AppStrings.tr('appName'),
                    applicationVersion: '1.0.0',
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
                  title: Text(AppStrings.tr('diagnosticLog')),
                  subtitle: Text(AppStrings.tr('diagnosticLogDescription')),
                  trailing: Icon(Icons.chevron_right_rounded),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => _DiagnosticLogPage(bridge: bridge),
                    ),
                  ),
                ),
              ],
            ),
          ),
          SizedBox(height: 20),
          Padding(
            padding: EdgeInsets.fromLTRB(12, 0, 12, 7),
            child: Text(
              AppStrings.tr('uiExperimentalFeatures'),
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: Theme.of(context).colorScheme.primary,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          _ExperimentalFeaturesCard(),
        ],
      ),
    );
  }
}

class _DiagnosticLogPage extends StatefulWidget {
  const _DiagnosticLogPage({required this.bridge});
  final NativeBridge bridge;

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
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(AppStrings.tr('diagnosticLog')),
      actions: [
        IconButton(
          tooltip: AppStrings.tr('copyDiagnosticLog'),
          onPressed: report.isEmpty
              ? null
              : () => Clipboard.setData(ClipboardData(text: report)),
          icon: Icon(Icons.copy_rounded),
        ),
        IconButton(
          tooltip: AppStrings.tr('shareDiagnosticLog'),
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
              child: Text(AppStrings.tr('clearDiagnosticLog')),
            ),
          ],
        ),
      ],
    ),
    body: loading
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
          ),
  );
}

class _ExperimentalFeaturesCard extends StatefulWidget {
  const _ExperimentalFeaturesCard();

  @override
  State<_ExperimentalFeaturesCard> createState() =>
      _ExperimentalFeaturesCardState();
}

class _ExperimentalFeaturesCardState extends State<_ExperimentalFeaturesCard> {
  var multiTouch = false;

  @override
  void initState() {
    super.initState();
    SharedPreferences.getInstance().then((preferences) {
      if (mounted) {
        setState(() {
          multiTouch = preferences.getBool('experimental_multitouch') ?? false;
        });
      }
    });
  }

  Future<void> updateMultiTouch(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('experimental_multitouch', value);
    if (mounted) setState(() => multiTouch = value);
  }

  @override
  Widget build(BuildContext context) => Card(
    margin: EdgeInsets.zero,
    child: SwitchListTile(
      secondary: Icon(Icons.science_outlined),
      value: multiTouch,
      onChanged: updateMultiTouch,
      title: Text(AppStrings.tr('uiExperimentalMultiTouch')),
      subtitle: Text(AppStrings.tr('uiItSupportsMultiTouchAndTheThree')),
    ),
  );
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
          enabled = preferences.getBool('keep_awake_during_session') ?? false;
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
    title: Text(AppStrings.tr('uiDoNotSleepWhileRunningDextop')),
    subtitle: Text(
      AppStrings.tr('uiPreventsTheScreenFromTurningOffAutomatically'),
    ),
  );
}

@pragma('vm:entry-point')
void overlayMain() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(OverlayApp());
}
