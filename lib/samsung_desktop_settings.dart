part of 'main.dart';

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
          title: Text(currentLocalizations().samsungExperimentalTitle),
          subtitle: Text(
            !supported && !loading
                ? currentLocalizations().samsungUnavailable
                : currentLocalizations().samsungExperimentalDescription,
          ),
        ),
        if (enabled && supported)
          ListTile(
            leading: const Icon(Icons.desktop_windows_outlined),
            title: Text(currentLocalizations().samsungSettingsTitle),
            subtitle: Text(currentLocalizations().samsungSettingsSummary),
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

class _SamsungDesktopSettingsPageState extends State<SamsungDesktopSettingsPage>
    with WidgetsBindingObserver {
  Map<String, dynamic> values = {};
  bool loading = true;
  bool backupAvailable = false;
  bool sensitiveSettingsUnlocked = false;
  bool sessionRunning = false;
  String? error;

  // These Samsung input options conflict with Dextop's own touch, keyboard,
  // and multi-finger routing. Keep their implementation and backup support,
  // but never expose them from either the portrait page or landscape pane.
  bool get inputSettingsHidden => true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant SamsungDesktopSettingsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.isRunning != widget.isRunning) refreshRunningState();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) refreshRunningState();
  }

  Future<void> refreshRunningState() async {
    final status = await widget.bridge.status().catchError(
      (_) => <String, dynamic>{},
    );
    if (mounted) {
      setState(() => sessionRunning = status['active'] == true);
    }
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
        widget.bridge.status().catchError((_) => <String, dynamic>{}),
      ]);
      final state = results[0] as Map<String, dynamic>;
      final prefs = results[1] as SharedPreferences;
      final status = results[2] as Map<String, dynamic>;
      if (!mounted) return;
      setState(() {
        values = Map<String, dynamic>.from(state['values'] as Map? ?? const {});
        backupAvailable = state['backupAvailable'] == true;
        sessionRunning = status['active'] == true;
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
        SnackBar(content: Text(currentLocalizations().samsungRestoreSuccess)),
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
        title: Text(currentLocalizations().samsungConfirmTitle),
        content: Text(currentLocalizations().samsungPermanentWarning),
        actions: [
          SizedBox(
            width: double.infinity,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                FilledButton(
                  onPressed: () => Navigator.pop(dialogContext, true),
                  child: Text(currentLocalizations().samsungAcceptEnable),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: () => Navigator.pop(dialogContext, false),
                  child: Text(currentLocalizations().uiCancel),
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
        tooltip: currentLocalizations().samsungAboutSetting,
        onPressed: () => showDialog<void>(
          context: context,
          builder: (context) => AlertDialog(
            scrollable: true,
            title: Text(title),
            content: Text(settingHelp(id)),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: Text(currentLocalizations().close),
              ),
            ],
          ),
        ),
        icon: const Icon(Icons.info_outline_rounded, size: 20),
      ),
    ],
  );

  String settingHelp(String id) {
    final l = currentLocalizations();
    return switch (id) {
      'resolution' => l.samsungHelp_resolution,
      'screenZoom' => l.samsungHelp_screenZoom,
      'fontScale' => l.samsungHelp_fontScale,
      'screenTimeout' => l.samsungHelp_screenTimeout,
      'audioOutput' => l.samsungHelp_audioOutput,
      'displayOrientation' => l.samsungHelp_displayOrientation,
      'displayArrangement' => l.samsungHelp_displayArrangement,
      'autorunTouchpad' => l.samsungHelp_autorunTouchpad,
      'touchpadScrollDirection' => l.samsungHelp_touchpadScrollDirection,
      'touchKeyboard' => l.samsungHelp_touchKeyboard,
      'keyboardDex' => l.samsungHelp_keyboardDex,
      'spenInputMode' => l.samsungHelp_spenInputMode,
      'threeFingerGesture' => l.samsungHelp_threeFingerGesture,
      'fourFingerGesture' => l.samsungHelp_fourFingerGesture,
      'autoHideTaskbar' => l.samsungHelp_autoHideTaskbar,
      'dexCommandArrow' => l.samsungHelp_dexCommandArrow,
      'includePhoneDisplay' => l.samsungHelp_includePhoneDisplay,
      'mirrorPhoneDisplay' => l.samsungHelp_mirrorPhoneDisplay,
      _ => id,
    };
  }

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
                  label: Text(currentLocalizations().samsungRestoreEnvironment),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(currentLocalizations().samsungSettingsIntro),
              ),
              if (!sensitiveSettingsUnlocked) unlockBanner(),
              section(currentLocalizations().display),
              choice<String>(
                'resolution',
                currentLocalizations().samsungResolution,
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
                currentLocalizations().samsungScreenZoom,
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
                currentLocalizations().samsungFontScale,
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
                currentLocalizations().samsungScreenTimeout,
                integer('screenTimeout', 600000),
                {
                  15000: currentLocalizations().samsungSeconds15,
                  30000: currentLocalizations().samsungSeconds30,
                  60000: currentLocalizations().samsungMinute1,
                  120000: currentLocalizations().samsungMinutes2,
                  300000: currentLocalizations().samsungMinutes5,
                  600000: currentLocalizations().samsungMinutes10,
                  1200000: currentLocalizations().samsungMinutes20,
                  1800000: currentLocalizations().samsungMinutes30,
                  3600000: currentLocalizations().samsungHour1,
                },
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              toggle(
                'audioOutput',
                currentLocalizations().samsungAudioOutput,
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              choice<int>(
                'displayOrientation',
                currentLocalizations().samsungDisplayOrientation,
                integer('displayOrientation', 0),
                const {0: '0°', 1: '90°', 2: '180°', 3: '270°'},
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              choice<int>(
                'displayArrangement',
                currentLocalizations().samsungDisplayArrangement,
                integer('displayArrangement', 2),
                {
                  0: currentLocalizations().samsungLeft,
                  1: currentLocalizations().samsungRight,
                  2: currentLocalizations().samsungAutomatic,
                },
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              section(currentLocalizations().samsungSectionInput),
              if (!inputSettingsHidden)
                toggle(
                  'autorunTouchpad',
                  currentLocalizations().samsungAutorunTouchpad,
                ),
              toggle(
                'touchpadScrollDirection',
                currentLocalizations().samsungTouchpadScrollDirection,
              ),
              if (!inputSettingsHidden)
                toggle(
                  'touchKeyboard',
                  currentLocalizations().samsungTouchKeyboard,
                ),
              if (!inputSettingsHidden)
                toggle(
                  'keyboardDex',
                  currentLocalizations().samsungKeyboardDex,
                ),
              toggle(
                'spenInputMode',
                currentLocalizations().samsungSpenInputMode,
                fallback: true,
              ),
              if (!inputSettingsHidden)
                choice<int>(
                  'threeFingerGesture',
                  currentLocalizations().samsungThreeFingerGesture,
                  integer('threeFingerGesture', 4),
                  gestureOptions,
                ),
              if (!inputSettingsHidden)
                choice<int>(
                  'fourFingerGesture',
                  currentLocalizations().samsungFourFingerGesture,
                  integer('fourFingerGesture', 1),
                  gestureOptions,
                ),
              section(currentLocalizations().samsungSectionDesktop),
              toggle(
                'autoHideTaskbar',
                currentLocalizations().samsungAutoHideTaskbar,
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              toggle(
                'dexCommandArrow',
                currentLocalizations().samsungDexCommandArrow,
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
              toggle(
                'mirrorPhoneDisplay',
                currentLocalizations().samsungMirrorPhoneDisplay,
                enabled: sensitiveSettingsUnlocked,
                lockedAction: requestUnlock,
              ),
            ],
          );
    if (widget.embedded) return body;
    return Scaffold(
      appBar: AppBar(
        title: Text(currentLocalizations().samsungSettingsTitle),
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
      label: Text(currentLocalizations().samsungReviewEnable),
    ),
  );

  Map<int, String> get gestureOptions => {
    0: currentLocalizations().samsungGestureNone,
    1: currentLocalizations().samsungGestureApps,
    2: currentLocalizations().home,
    3: currentLocalizations().samsungGestureRecents,
    4: currentLocalizations().back,
    5: currentLocalizations().samsungGestureNotifications,
    6: currentLocalizations().samsungGestureQuickSettings,
  };
}
