part of 'main.dart';

String _samsung(String key) => AppStrings.tr(key);

class _SamsungExperimentalSettingsTile extends StatefulWidget {
  const _SamsungExperimentalSettingsTile({
    required this.bridge,
    this.isRunning = false,
    this.onOpenSettings,
  });
  final NativeBridge bridge;
  final bool isRunning;
  final VoidCallback? onOpenSettings;

  @override
  State<_SamsungExperimentalSettingsTile> createState() =>
      _SamsungExperimentalSettingsTileState();
}

class _SamsungExperimentalSettingsTileState
    extends State<_SamsungExperimentalSettingsTile> {
  bool enabled = false;
  bool supported = false;
  bool loading = true;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    var isSupported = false;
    try {
      final state = await widget.bridge.samsungDesktopSettings();
      isSupported = state['supported'] == true;
    } catch (_) {}
    if (!mounted) return;
    setState(() {
      supported = isSupported;
      enabled = prefs.getBool('experimental_samsung_desktop_settings') ?? false;
      loading = false;
    });
  }

  Future<void> update(bool value) async {
    if (value) await widget.bridge.backupSamsungDesktopSettings();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('experimental_samsung_desktop_settings', value);
    if (mounted) setState(() => enabled = value);
  }

  @override
  Widget build(BuildContext context) => Card(
    child: Column(
      children: [
        SwitchListTile(
          secondary: const Icon(Icons.science_outlined),
          value: enabled && supported,
          onChanged: loading || !supported ? null : update,
          title: Text(_samsung('samsungExperimentalTitle')),
          subtitle: Text(
            !supported && !loading
                ? _samsung('samsungUnavailable')
                : _samsung('samsungExperimentalDescription'),
          ),
        ),
        if (enabled && supported)
          ListTile(
            leading: const Icon(Icons.desktop_windows_outlined),
            title: Text(_samsung('samsungSettingsTitle')),
            subtitle: Text(_samsung('samsungSettingsSummary')),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap:
                widget.onOpenSettings ??
                () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => SamsungDesktopSettingsPage(
                      bridge: widget.bridge,
                      isRunning: widget.isRunning,
                    ),
                  ),
                ),
          ),
      ],
    ),
  );
}

class SamsungDesktopSettingsPage extends StatefulWidget {
  const SamsungDesktopSettingsPage({
    required this.bridge,
    this.isRunning = false,
    this.embedded = false,
    super.key,
  });
  final NativeBridge bridge;
  final bool isRunning;
  final bool embedded;

  @override
  State<SamsungDesktopSettingsPage> createState() =>
      _SamsungDesktopSettingsPageState();
}

class _SamsungDesktopSettingsPageState
    extends State<SamsungDesktopSettingsPage> {
  Map<String, dynamic> values = {};
  bool loading = true;
  bool backupAvailable = false;
  bool sensitiveSettingsUnlocked = false;
  String? error;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final results = await Future.wait<dynamic>([
        widget.bridge.samsungDesktopSettings(),
        SharedPreferences.getInstance(),
      ]);
      final state = results[0] as Map<String, dynamic>;
      final prefs = results[1] as SharedPreferences;
      if (!mounted) return;
      setState(() {
        values = Map<String, dynamic>.from(state['values'] as Map? ?? const {});
        backupAvailable = state['backupAvailable'] == true;
        sensitiveSettingsUnlocked =
            prefs.getBool('samsung_sensitive_settings_unlocked') ??
            (prefs.getBool('samsung_display_settings_unlocked') == true ||
                prefs.getBool('samsung_desktop_settings_unlocked') == true);
        loading = false;
      });
    } catch (exception) {
      if (!mounted) return;
      setState(() {
        error = exception.toString();
        loading = false;
      });
    }
  }

  Future<void> save(String id, Object value) async {
    try {
      final state = await widget.bridge.setSamsungDesktopSetting(id, value);
      if (!mounted) return;
      setState(() {
        values = Map<String, dynamic>.from(state['values'] as Map? ?? const {});
      });
    } catch (exception) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(exception.toString())));
    }
  }

  Future<void> restoreEnvironment() async {
    try {
      final state = await widget.bridge.restoreSamsungDesktopSettings();
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove('samsung_display_settings_unlocked');
      await prefs.remove('samsung_desktop_settings_unlocked');
      await prefs.remove('samsung_sensitive_settings_unlocked');
      if (!mounted) return;
      setState(() {
        values = Map<String, dynamic>.from(state['values'] as Map? ?? const {});
        backupAvailable = false;
        sensitiveSettingsUnlocked = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_samsung('samsungRestoreSuccess'))),
      );
    } catch (exception) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(exception.toString())));
      }
    }
  }

  Future<void> requestUnlock() async {
    final accepted = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        icon: const Icon(Icons.warning_amber_rounded),
        title: Text(_samsung('samsungConfirmTitle')),
        content: Text(_samsung('samsungPermanentWarning')),
        actions: [
          SizedBox(
            width: double.infinity,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                FilledButton(
                  onPressed: () => Navigator.pop(dialogContext, true),
                  child: Text(_samsung('samsungAcceptEnable')),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: () => Navigator.pop(dialogContext, false),
                  child: Text(_samsung('cancel')),
                ),
              ],
            ),
          ),
        ],
      ),
    );
    if (accepted != true) return;
    final prefs = await SharedPreferences.getInstance();
    await widget.bridge.backupSamsungDesktopSettings();
    await prefs.setBool('samsung_sensitive_settings_unlocked', true);
    await prefs.remove('samsung_display_settings_unlocked');
    await prefs.remove('samsung_desktop_settings_unlocked');
    if (mounted) {
      setState(() {
        sensitiveSettingsUnlocked = true;
        backupAvailable = true;
      });
    }
  }

  int integer(String id, int fallback) =>
      (values[id] as num?)?.toInt() ?? fallback;
  double decimal(String id, double fallback) =>
      (values[id] as num?)?.toDouble() ?? fallback;
  bool boolean(String id, bool fallback) => integer(id, fallback ? 1 : 0) == 1;

  Widget section(String title) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 20, 16, 6),
    child: Text(title, style: Theme.of(context).textTheme.titleSmall),
  );

  Widget choice<T>(
    String id,
    String title,
    T value,
    Map<T, String> options, {
    String? subtitle,
    bool enabled = true,
    VoidCallback? lockedAction,
  }) => ListTile(
    enabled: enabled,
    onTap: enabled ? null : lockedAction,
    title: settingTitle(id, title),
    subtitle: subtitle == null ? null : Text(subtitle),
    trailing: DropdownButton<T>(
      value: options.containsKey(value) ? value : options.keys.first,
      items: options.entries
          .map(
            (entry) =>
                DropdownMenuItem<T>(value: entry.key, child: Text(entry.value)),
          )
          .toList(),
      onChanged: enabled
          ? (next) {
              if (next != null) save(id, next as Object);
            }
          : null,
    ),
  );

  Widget toggle(
    String id,
    String title, {
    String? subtitle,
    bool fallback = false,
    bool enabled = true,
    VoidCallback? lockedAction,
  }) => ListTile(
    enabled: enabled,
    onTap: enabled
        ? () => save(id, boolean(id, fallback) ? 0 : 1)
        : lockedAction,
    title: settingTitle(id, title),
    subtitle: subtitle == null ? null : Text(subtitle),
    trailing: Switch(
      value: boolean(id, fallback),
      onChanged: enabled ? (value) => save(id, value ? 1 : 0) : null,
    ),
  );

  Widget settingTitle(String id, String title) => Row(
    children: [
      Expanded(child: Text(title)),
      IconButton(
        visualDensity: VisualDensity.compact,
        tooltip: _samsung('samsungAboutSetting'),
        onPressed: () => showDialog<void>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(title),
            content: Text(settingHelp(id)),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: Text(_samsung('ok')),
              ),
            ],
          ),
        ),
        icon: const Icon(Icons.info_outline_rounded, size: 20),
      ),
    ],
  );

  String settingHelp(String id) => _samsung('samsungHelp_$id');

  @override
  Widget build(BuildContext context) {
    final body = loading
        ? const Center(child: CircularProgressIndicator())
        : error != null
        ? Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(error!),
            ),
          )
        : ListView(
            padding: const EdgeInsets.only(bottom: 32),
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
                child: FilledButton.tonalIcon(
                  onPressed: backupAvailable ? restoreEnvironment : null,
                  icon: const Icon(Icons.restore_rounded),
                  label: Text(_samsung('samsungRestoreEnvironment')),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(_samsung('samsungSettingsIntro')),
              ),
              if (!sensitiveSettingsUnlocked) unlockBanner(),
              section(_samsung('display')),
              choice<String>(
                'resolution',
                _samsung('samsungResolution'),
                values['resolution']?.toString() ?? 'FHD',
                const {
                  'HD': 'HD',
                  'FHD': 'FHD',
                  'WQHD': 'WQHD',
                  'UHD': 'UHD',
                  'WUXGA': 'WUXGA',
                  'WQXGA': 'WQXGA',
                  'UWFHD': 'UWFHD',
                  'UWQHD': 'UWQHD',
                },
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              choice<int>(
                'screenZoom',
                _samsung('samsungScreenZoom'),
                integer('screenZoom', 160),
                const {
                  100: '100',
                  120: '120',
                  140: '140',
                  160: '160',
                  180: '180',
                  200: '200',
                  220: '220',
                  240: '240',
                  280: '280',
                  320: '320',
                },
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              choice<double>(
                'fontScale',
                _samsung('samsungFontScale'),
                decimal('fontScale', 1.0),
                {
                  0.85: '85%',
                  1.0: '100%',
                  1.15: '115%',
                  1.3: '130%',
                  1.5: '150%',
                },
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              choice<int>(
                'screenTimeout',
                _samsung('samsungScreenTimeout'),
                integer('screenTimeout', 600000),
                {
                  15000: _samsung('samsungSeconds15'),
                  30000: _samsung('samsungSeconds30'),
                  60000: _samsung('samsungMinute1'),
                  120000: _samsung('samsungMinutes2'),
                  300000: _samsung('samsungMinutes5'),
                  600000: _samsung('samsungMinutes10'),
                  1200000: _samsung('samsungMinutes20'),
                  1800000: _samsung('samsungMinutes30'),
                  3600000: _samsung('samsungHour1'),
                },
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              toggle(
                'audioOutput',
                _samsung('samsungAudioOutput'),
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              choice<int>(
                'displayOrientation',
                _samsung('samsungDisplayOrientation'),
                integer('displayOrientation', 0),
                const {0: '0°', 1: '90°', 2: '180°', 3: '270°'},
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              choice<int>(
                'displayArrangement',
                _samsung('samsungDisplayArrangement'),
                integer('displayArrangement', 2),
                {
                  0: _samsung('samsungLeft'),
                  1: _samsung('samsungRight'),
                  2: _samsung('samsungAutomatic'),
                },
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              section(_samsung('samsungSectionInput')),
              if (!widget.isRunning)
                toggle('autorunTouchpad', _samsung('samsungAutorunTouchpad')),
              toggle(
                'touchpadScrollDirection',
                _samsung('samsungTouchpadScrollDirection'),
              ),
              if (!widget.isRunning)
                toggle('touchKeyboard', _samsung('samsungTouchKeyboard')),
              if (!widget.isRunning)
                toggle('keyboardDex', _samsung('samsungKeyboardDex')),
              toggle(
                'spenInputMode',
                _samsung('samsungSpenInputMode'),
                fallback: true,
              ),
              if (!widget.isRunning)
                choice<int>(
                  'threeFingerGesture',
                  _samsung('samsungThreeFingerGesture'),
                  integer('threeFingerGesture', 4),
                  gestureOptions,
                ),
              if (!widget.isRunning)
                choice<int>(
                  'fourFingerGesture',
                  _samsung('samsungFourFingerGesture'),
                  integer('fourFingerGesture', 1),
                  gestureOptions,
                ),
              section(_samsung('samsungSectionDesktop')),
              toggle(
                'autoHideTaskbar',
                _samsung('samsungAutoHideTaskbar'),
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              toggle(
                'dexCommandArrow',
                _samsung('samsungDexCommandArrow'),
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              toggle(
                'includePhoneDisplay',
                _samsung('samsungIncludePhoneDisplay'),
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              toggle(
                'mirrorPhoneDisplay',
                _samsung('samsungMirrorPhoneDisplay'),
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
            ],
          );
    if (widget.embedded) return body;
    return Scaffold(
      appBar: AppBar(
        title: Text(_samsung('samsungSettingsTitle')),
        actions: [
          IconButton(onPressed: load, icon: const Icon(Icons.refresh_rounded)),
        ],
      ),
      body: body,
    );
  }

  Widget unlockBanner() => Padding(
    padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
    child: OutlinedButton.icon(
      onPressed: requestUnlock,
      icon: const Icon(Icons.lock_outline_rounded),
      label: Text(_samsung('samsungReviewEnable')),
    ),
  );

  Map<int, String> get gestureOptions => {
    0: _samsung('samsungGestureNone'),
    1: _samsung('samsungGestureApps'),
    2: _samsung('home'),
    3: _samsung('samsungGestureRecents'),
    4: _samsung('back'),
    5: _samsung('samsungGestureNotifications'),
    6: _samsung('samsungGestureQuickSettings'),
  };
}
