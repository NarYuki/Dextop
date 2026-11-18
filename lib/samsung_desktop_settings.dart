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
  Widget build(BuildContext context) => DextopGroup(
    children: [
      DextopToggle(
        icon: M3EIcons.science_outlined,
        value: enabled && supported,
        enabled: !loading && supported,
        onChanged: loading || !supported ? null : update,
        title: currentLocalizations().samsungExperimentalTitle,
        subtitle: !supported && !loading
            ? currentLocalizations().samsungUnavailable
            : currentLocalizations().samsungExperimentalDescription,
      ),
      if (enabled && supported)
        M3EListItem(
          leading: const Icon(M3EIcons.desktop_windows_outlined),
          headline: currentLocalizations().samsungSettingsTitle,
          supportingText: currentLocalizations().samsungSettingsSummary,
          supportingMaxLines: null,
          trailing: const Icon(M3EIcons.chevron_right_rounded),
          onTap:
              widget.onOpenSettings ??
              () => Navigator.of(context).push(
                dextopRoute<void>(
                  SamsungDesktopSettingsPage(
                    bridge: widget.bridge,
                    isRunning: widget.isRunning,
                  ),
                ),
              ),
        ),
    ],
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
    AppAnalytics.screen('samsung_desktop_settings');
    load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) load();
  }

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    var unlocked =
        prefs.getBool('samsung_sensitive_settings_unlocked') ?? false;
    if (!unlocked &&
        ((prefs.getBool('samsung_display_settings_unlocked') ?? false) ||
            (prefs.getBool('samsung_desktop_settings_unlocked') ?? false))) {
      unlocked = true;
      await prefs.setBool('samsung_sensitive_settings_unlocked', true);
    }
    try {
      final state = await widget.bridge.samsungDesktopSettings();
      if (!mounted) return;
      setState(() {
        values = Map<String, dynamic>.from(state['values'] as Map? ?? const {});
        backupAvailable = state['backupAvailable'] == true;
        sessionRunning = widget.isRunning || state['active'] == true;
        sensitiveSettingsUnlocked = unlocked;
        loading = false;
        error = null;
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
      M3ESnackbar.show(context, message: exception.toString());
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
      M3ESnackbar.show(
        context,
        message: currentLocalizations().samsungRestoreSuccess,
      );
    } catch (exception) {
      if (mounted) {
        M3ESnackbar.show(context, message: exception.toString());
      }
    }
  }

  Future<void> requestUnlock() async {
    final accepted = await M3EDialog.show<bool>(
      context,
      dialog: Builder(
        builder: (dialogContext) => M3EDialog(
          icon: const Icon(M3EIcons.warning_amber_rounded),
          title: currentLocalizations().samsungConfirmTitle,
          content: Text(currentLocalizations().samsungPermanentWarning),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: Text(currentLocalizations().uiCancel),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: Text(currentLocalizations().samsungAcceptEnable),
            ),
          ],
        ),
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

  void _showSettingHelp(String id, String title) {
    M3EDialog.show<void>(
      context,
      dialog: Builder(
        builder: (dialogContext) => M3EDialog(
          icon: const Icon(M3EIcons.info_outline_rounded),
          title: title,
          content: Text(settingHelp(id)),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialogContext),
              child: Text(currentLocalizations().close),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _selectChoice<T>(
    String id,
    String title,
    T current,
    Map<T, String> options,
  ) async {
    final selected = await M3EDialog.show<T>(
      context,
      dialog: Builder(
        builder: (dialogContext) {
          final colors = Theme.of(dialogContext).colorScheme;
          return M3EDialog(
            title: title,
            content: DextopGroup(
              color: colors.surfaceContainer,
              children: [
                for (final entry in options.entries)
                  DextopChoice<T>(
                    value: entry.key,
                    groupValue: current,
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
    if (selected != null && selected != current) {
      await save(id, selected as Object);
    }
  }

  M3EListItem choice<T>(
    String id,
    String title,
    T value,
    Map<T, String> options, {
    String? subtitle,
    bool enabled = true,
    VoidCallback? lockedAction,
  }) {
    final resolved = options.containsKey(value) ? value : options.keys.first;
    final onTap = enabled
        ? () => _selectChoice<T>(id, title, resolved, options)
        : lockedAction;
    return M3EListItem(
      enabled: enabled || lockedAction != null,
      headline: title,
      supportingText: subtitle,
      supportingMaxLines: null,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          DextopDropdownButton(
            label: options[resolved] ?? '',
            enabled: enabled || lockedAction != null,
            onPressed: onTap,
          ),
          const SizedBox(width: 4),
          M3EIconButton(
            variant: M3EIconButtonVariant.standard,
            tooltip: currentLocalizations().samsungAboutSetting,
            onPressed: () => _showSettingHelp(id, title),
            icon: const Icon(M3EIcons.info_outline_rounded, size: 20),
          ),
        ],
      ),
      onTap: onTap,
    );
  }

  M3EListItem toggle(
    String id,
    String title, {
    String? subtitle,
    bool fallback = false,
    bool enabled = true,
    VoidCallback? lockedAction,
  }) {
    final current = boolean(id, fallback);
    return M3EListItem(
      enabled: enabled || lockedAction != null,
      headline: title,
      supportingText: subtitle,
      supportingMaxLines: null,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          M3EIconButton(
            variant: M3EIconButtonVariant.standard,
            tooltip: currentLocalizations().samsungAboutSetting,
            onPressed: () => _showSettingHelp(id, title),
            icon: const Icon(M3EIcons.info_outline_rounded, size: 20),
          ),
          M3ESwitch(
            value: current,
            onChanged: enabled ? (value) => save(id, value ? 1 : 0) : null,
          ),
        ],
      ),
      onTap: enabled
          ? () => save(id, current ? 0 : 1)
          : lockedAction,
    );
  }

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
    final colors = Theme.of(context).colorScheme;
    final body = loading
        ? const Center(
            child: M3ELoadingIndicator(
              variant: M3ELoadingIndicatorVariant.contained,
            ),
          )
        : error != null
        ? Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(error!),
            ),
          )
        : ListView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
            children: [
              M3EButton.tonal(
                size: M3EButtonSize.md,
                enabled: backupAvailable,
                onPressed: backupAvailable ? restoreEnvironment : null,
                icon: const Icon(M3EIcons.restore_rounded),
                label: Text(currentLocalizations().samsungRestoreEnvironment),
              ),
              const SizedBox(height: 12),
              M3ECard(
                variant: M3ECardVariant.filled,
                borderRadius: BorderRadius.circular(24),
                padding: const EdgeInsets.all(16),
                child: Text(
                  currentLocalizations().samsungSettingsIntro,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
                ),
              ),
              if (!sensitiveSettingsUnlocked) ...[
                const SizedBox(height: 12),
                unlockBanner(),
              ],
              DextopSection(currentLocalizations().display),
              DextopGroup(
                children: [
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
                ],
              ),
              DextopSection(currentLocalizations().samsungSectionInput),
              DextopGroup(
                children: [
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
                ],
              ),
              DextopSection(currentLocalizations().samsungSectionDesktop),
              DextopGroup(
                children: [
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
              ),
            ],
          );
    if (widget.embedded) return body;
    return Scaffold(
      backgroundColor: colors.surface,
      appBar: M3EAppBar.top(
        automaticallyImplyLeading: true,
        titleText: currentLocalizations().samsungSettingsTitle,
        actions: [
          M3EIconButton(
            variant: M3EIconButtonVariant.standard,
            onPressed: load,
            icon: const Icon(M3EIcons.refresh_rounded),
          ),
        ],
      ),
      body: body,
    );
  }

  Widget unlockBanner() => M3EButton.outlined(
    size: M3EButtonSize.md,
    onPressed: requestUnlock,
    icon: const Icon(M3EIcons.lock_outline_rounded),
    label: Text(currentLocalizations().samsungReviewEnable),
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
