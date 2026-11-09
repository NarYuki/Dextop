import 'l10n/inline_text.dart';

import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'l10n/app_localizations.dart';
import 'linux/linux_expressive.dart';
import 'linux/linux_scale_setting.dart';
import 'linux/linux_widgets.dart';
import 'linux/linux_store_sources.dart';
import 'linux/linux_advanced_settings.dart';
import 'linux/linux_privilege_warning.dart';
import 'linux/linux_compat.dart';
import 'linux/linux_resources.dart';
import 'linux/linux_first_run.dart';
import 'linux/linux_macos.dart';
import 'linux/macos_feature.dart';
import 'linux/linux_integrations.dart';

/// Linux settings, stored by the shell-side Linux service: the defaults
/// (default container and graphics) or, with [containerId], one container's
/// own graphics, which may follow the default.
class LinuxSettingsPage extends StatefulWidget {
  const LinuxSettingsPage({super.key, this.containerId, this.containerName});

  final String? containerId;
  final String? containerName;

  @override
  State<LinuxSettingsPage> createState() => _LinuxSettingsPageState();
}

class _LinuxSettingsPageState extends State<LinuxSettingsPage> {
  static const _channel = MethodChannel('app.freedextop/linux');

  Map<Object?, Object?>? settings;
  List<Map<Object?, Object?>> containers = const [];
  String? error;
  bool saving = false;

  /// How home-screen launchers are installed ('ask', 'auto' or 'manual').
  String launcherMode = 'ask';
  bool silentAvailable = false;

  /// Google Play build: launchers are home-screen shortcuts, with nothing to install.
  bool pinnedLaunchers = false;

  /// Google Play build in the full privilege mode: launchers install through privilege only.
  bool privilegedLaunchers = false;

  /// Whether Android and Linux share the clipboard.
  bool clipboardSync = false;
  bool keyboardButtonVisible = true;
  bool macosCompatibilityEnabled = false;

  /// Light or dark Linux and Windows apps: 'auto' (as Android), 'light' or 'dark'.
  String colorTheme = 'auto';

  bool get _forContainer => widget.containerId != null;

  /// The category shown ('general', 'display', 'integration', 'store'), or the list of them.
  String? _page;

  /// 'privileged' or 'android', for the General category's summary.
  String executionMode = 'android';

  /// False in the Google Play build, whose only execution mode needs no advanced settings.
  bool privilegedModeOffered = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final result = await _channel.invokeMapMethod<Object?, Object?>(
        'settings',
      );
      final list = _forContainer
          ? const <Map<Object?, Object?>>[]
          : ((await _channel.invokeListMethod<Object?>('list')) ?? const [])
                .whereType<Map<Object?, Object?>>()
                .where((c) => c['state'] == 'installed')
                .toList();
      final launcher = _forContainer
          ? null
          : await _channel.invokeMapMethod<Object?, Object?>('launcherInstall');
      final clipboard = _forContainer
          ? false
          : await _channel.invokeMethod<bool>('clipboardSync') ?? false;
      final mode = await _channel.invokeMethod<String>('executionMode');
      final offered = await linuxPrivilegedModeOffered();
      final keyboardButton =
          await _channel.invokeMethod<bool>('keyboardButtonVisible') ?? true;
      final appTheme = _forContainer
          ? 'auto'
          : await _channel.invokeMethod<String>('linuxTheme') ?? 'auto';
      final preferences = await SharedPreferences.getInstance();
      final macosEnabled =
          preferences.getBool(macosCompatibilityPreferenceKey) ?? false;
      if (mounted) {
        setState(() {
          macosCompatibilityEnabled = macosEnabled;
          settings = result;
          containers = list;
          launcherMode = launcher?['mode'] as String? ?? 'ask';
          silentAvailable = launcher?['silentAvailable'] == true;
          pinnedLaunchers = launcher?['pinned'] == true;
          privilegedLaunchers = launcher?['privilegedOnly'] == true;
          clipboardSync = clipboard;
          keyboardButtonVisible = keyboardButton;
          colorTheme = appTheme;
          executionMode = mode ?? 'android';
          privilegedModeOffered = offered;
        });
      }
    } on PlatformException catch (exception) {
      if (mounted) setState(() => error = exception.message);
    }
  }

  Future<void> _save(Map<String, Object?> update) async {
    setState(() => saving = true);
    try {
      final result = await _channel.invokeMapMethod<Object?, Object?>(
        'setSettings',
        {
          'settings': {
            if (_forContainer) 'container': widget.containerId,
            ...update,
          },
        },
      );
      if (mounted) {
        setState(() {
          settings = result;
          error = null;
        });
      }
    } on PlatformException catch (exception) {
      if (mounted) setState(() => error = exception.message);
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> _setKeyboardButtonVisible(bool visible) async {
    setState(() => saving = true);
    try {
      final value = await _channel.invokeMethod<bool>(
        'setKeyboardButtonVisible',
        {'visible': visible},
      );
      if (mounted) {
        setState(() {
          keyboardButtonVisible = value ?? visible;
          error = null;
        });
      }
    } on PlatformException catch (exception) {
      if (mounted) setState(() => error = exception.message);
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> _editDns() async {
    var input = (settings?['dnsServers'] as List? ?? const []).join('\n');
    String? validation;
    final result = await showDialog<List<String>>(
      context: context,
      builder: (dialog) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(linuxText(context, 'uiLinuxDNS')),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                linuxText(
                  context,
                  'uiEnterUpToThreeIPAddressesOnePerLineLeaveEmptyToUseAndroidDNS',
                ),
              ),
              const SizedBox(height: 16),
              TextFormField(
                initialValue: input,
                onChanged: (value) => input = value,
                minLines: 3,
                maxLines: 4,
                keyboardType: TextInputType.multiline,
                decoration: InputDecoration(
                  hintText: localText(context, 'ui11112606470047001111'),
                  errorText: validation,
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialog),
              child: Text(linuxText(context, 'uiCancel')),
            ),
            TextButton(
              onPressed: () {
                final servers = input
                    .split(RegExp(r'[\s,]+'))
                    .where((v) => v.isNotEmpty)
                    .toSet()
                    .toList();
                if (servers.length > 3 ||
                    servers.any((v) => InternetAddress.tryParse(v) == null)) {
                  setDialogState(
                    () => validation = linuxText(
                      context,
                      'uiEnterUpToThreeValidIPAddresses',
                    ),
                  );
                  return;
                }
                Navigator.pop(dialog, servers);
              },
              child: Text(linuxText(context, 'uiSave')),
            ),
          ],
        ),
      ),
    );
    if (result != null && mounted) await _save({'dnsServers': result});
  }

  Timer? _probePoll;

  /// Runs the device's GPU test, then follows it until it has a result.
  Future<void> _testGpu() async {
    await _save({'gpuProbe': true});
    _probePoll?.cancel();
    _probePoll = Timer.periodic(const Duration(seconds: 2), (timer) async {
      try {
        final result = await _channel.invokeMapMethod<Object?, Object?>(
          'settings',
        );
        if (!mounted) return timer.cancel();
        setState(() => settings = result);
        if (result?['gpuProbeRunning'] != true) timer.cancel();
      } on PlatformException catch (_) {
        timer.cancel();
      }
    });
  }

  @override
  void dispose() {
    _probePoll?.cancel();
    super.dispose();
  }

  /// The device test's last result, for Settings > Graphics.
  Widget _gpuProbeGroup(String Function(String) t) {
    final running = settings?['gpuProbeRunning'] == true;
    final chosen = settings?['gpuChosen'] == true;
    final probe = (settings?['gpuProbe'] as Map?)?.cast<Object?, Object?>();
    final state = probe?['state'] as String?;
    String fps(Object? value) =>
        value is num ? value.toStringAsFixed(value < 10 ? 1 : 0) : '-';
    final measured = probe?['gpuFps'] != null || probe?['cpuFps'] != null
        ? inlineFormat(t('uiGPU0FpsCPU1Fps'), [
            fps(probe?['gpuFps']),
            fps(probe?['cpuFps']),
          ])
        : null;
    final summary = running
        ? t('uiTestingAbout30Seconds')
        : switch (state) {
            'passed' => t(
              'uiTheGPUDrewReliablyTheGPUIsRecommendedTheTestSceneIsLightHeavy3DWorkGainsMuchMoreThanThis',
            ),
            'slow' => t(
              'uiTheGPUWasStableButClearlySlowerThanTheProcessorSoftwareRenderingIsRecommended',
            ),
            'failed' => t(
              'uiTheGPUDidNotDrawCorrectlySoftwareRenderingIsRecommended',
            ),
            'unavailable' => t('uiTheTestCouldNotRun'),
            _ => t(
              'uiNotTestedYetTheSameDrawingRunsOnTheGPUAndTheProcessorTheGPUIsRecommendedWhenItIsStableAndNotSlower',
            ),
          };
    final details = [
      summary,
      if (!running && measured != null) measured,
      if (!running && (probe?['renderer'] as String? ?? '').isNotEmpty)
        probe!['renderer'] as String,
      if (!running && (probe?['error'] as String? ?? '').isNotEmpty)
        probe!['error'] as String,
      if (!running && !chosen && state != null)
        t('uiUsingTheRecommendedSetting'),
    ].join('\n');
    return LinuxGroup(
      children: [
        M3EListItem(
          supportingMaxLines: null,
          headline: t('uiTestThisDevice'),
          supportingText: details,
          leading: running
              ? const SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(strokeWidth: 2.5),
                )
              : const Icon(M3EIcons.speed_rounded),
          trailing: running
              ? null
              : TextButton(
                  onPressed: saving ? null : _testGpu,
                  child: Text(state == null ? t('uiTest') : t('uiTestAgain')),
                ),
        ),
        if (chosen && state != null && !running)
          M3EListItem(
            supportingMaxLines: null,
            headline: t('uiUseTheRecommendedSetting'),
            supportingText: t('uiLetsTheTestResultChooseTheRenderer'),
            leading: const Icon(M3EIcons.auto_awesome_rounded),
            onTap: saving ? null : () => _save({'gpuRecommended': true}),
          ),
      ],
    );
  }

  Future<void> _setLauncherMode(String mode) async {
    if (mode == launcherMode) return;
    setState(() => launcherMode = mode);
    await _channel.invokeMethod('setLauncherInstall', {'mode': mode});
  }

  Future<void> _setTheme(String mode) async {
    if (mode == colorTheme) return;
    setState(() => colorTheme = mode);
    await _channel.invokeMethod('setLinuxTheme', {'mode': mode});
  }

  Future<void> _setClipboardSync(bool enabled) async {
    setState(() => clipboardSync = enabled);
    await _channel.invokeMethod('setClipboardSync', {'enabled': enabled});
  }

  Map<Object?, Object?>? get _containerSettings =>
      (settings?['containers'] as Map?)?[widget.containerId]
          as Map<Object?, Object?>?;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    // Turnip is for Adreno 6xx–8xx only; elsewhere its setting means VirGL.
    final turnipSupported = settings?['turnipSupported'] as bool? ?? true;
    String supported(String gpu) =>
        !turnipSupported && gpu == 'turnip' ? 'virgl' : gpu;
    final globalGpu = supported(settings?['gpu'] as String? ?? 'turnip');
    // A container without its own value follows the default ('').
    final current = _forContainer
        ? supported((_containerSettings?['gpu'] as String?) ?? '')
        : globalGpu;
    final hardwareAvailable = settings?['hardwareAvailable'] as bool? ?? true;
    final running = (settings?['rendererRunning'] as List?) ?? const [];
    final turnipInstalled = (settings?['turnipInstalled'] as List?) ?? const [];
    final turnipReady = _forContainer
        ? turnipInstalled.contains(widget.containerId)
        : turnipInstalled.isNotEmpty;
    final defaultContainer = settings?['defaultContainer'] as String? ?? '';
    final effective = _forContainer
        ? (_containerSettings?['gpuEffective'] as String?) ?? globalGpu
        : globalGpu;
    final virglRunning = _forContainer
        ? (running.contains(widget.containerId) ? 1 : 0)
        : running.length;
    void choose(String key, String value, String current) {
      if (value != current && !saving) _save({key: value});
    }

    String t(String key) => linuxText(context, key);
    final modeName = executionMode == 'android'
        ? t('uiAndroidExperimental')
        : t('uiFullPrivilege');
    final gpuName = switch (globalGpu) {
      'virgl' => l.linuxGpuHardware,
      'software' => l.linuxGpuSoftware,
      _ => l.linuxGpuTurnip,
    };
    final themeName = switch (colorTheme) {
      'light' => t('uiLight'),
      'dark' => t('uiDark'),
      _ => t('uiAutomatic'),
    };

    List<Widget> general() => [
      if (privilegedModeOffered) LinuxGroup(
        children: [
          M3EListItem(
            supportingMaxLines: null,
            headline: linuxText(context, 'uiAdvancedSettings'),
            supportingText: linuxText(
              context,
              'uiLinuxExecutionModeAndPrivilegedAccess',
            ),
            leading: const Icon(Icons.admin_panel_settings_rounded),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () async {
              await Navigator.of(context).push<void>(
                MaterialPageRoute(
                  builder: (_) => const LinuxAdvancedSettingsPage(),
                ),
              );
              if (mounted) _load();
            },
          ),
        ],
      ),
      LinuxSection(l.linuxDefaultSection),
      LinuxGroup(
        children: [
          M3EListItem(
            supportingMaxLines: null,
            headline: l.linuxDefaultContainer,
            supportingText: l.linuxDefaultContainerDescription,
            leading: const Icon(M3EIcons.star_outline_rounded),
          ),
          LinuxChoice<String>(
            value: '',
            groupValue: defaultContainer,
            onChanged: (v) => choose('defaultContainer', v, defaultContainer),
            title: l.linuxDefaultContainerAll,
          ),
          for (final container in containers)
            LinuxChoice<String>(
              value: container['id'] as String? ?? '',
              groupValue: defaultContainer,
              onChanged: (v) => choose('defaultContainer', v, defaultContainer),
              title: container['name'] as String? ?? '',
            ),
        ],
      ),
    ];
    List<Widget> network() => [
      LinuxSection(t('uiLinuxNetworkSettings')),
      LinuxGroup(
        children: [
          M3EListItem(
            headline: t('uiDNSServers'),
            supportingText:
                (settings?['dnsServers'] as List? ?? const []).isEmpty
                ? t('uiAutomaticAndroidDNS')
                : (settings!['dnsServers'] as List).join('\n'),
            supportingMaxLines: null,
            leading: const Icon(M3EIcons.dns_rounded),
            trailing: const Icon(M3EIcons.chevron_right_rounded),
            onTap: saving ? null : _editDns,
          ),
        ],
      ),
      Padding(
        padding: const EdgeInsets.all(8),
        child: Text(
          t(
            'uiAppliesToNameResolutionInsideLinuxAndroidNetworkSettingsStayUnchangedAppsUsingTheirOwnDNSMayBypassTh',
          ),
        ),
      ),
    ];
    List<Widget> display() => [
      if (!_forContainer) ...[
        LinuxSection(t('uiKeyboardButton')),
        LinuxGroup(
          children: [
            LinuxToggle(
              value: keyboardButtonVisible,
              onChanged: saving ? null : _setKeyboardButtonVisible,
              icon: M3EIcons.keyboard_rounded,
              title: t('uiShowPencilButton'),
              subtitle: t('uiTapToShowTheKeyboardDragToMoveThePositionIsSaved'),
            ),
          ],
        ),
        LinuxSection(t('uiDisplayScale')),
        LinuxScaleSetting(
          value: (settings?['uiScale'] as num?)?.toInt() ?? 100,
          t: t,
          enabled: !saving,
          onChanged: (value) => _save({'uiScale': value}),
        ),
      ],
      LinuxSection(
        _forContainer ? l.linuxGpuSection : l.linuxGpuDefaultSection,
      ),
      LinuxGroup(
        children: [
          if (_forContainer)
            LinuxChoice<String>(
              value: '',
              groupValue: current,
              onChanged: (v) => choose('gpu', v, current),
              icon: M3EIcons.sync_alt_rounded,
              title: l.linuxGpuFollowDefault,
              subtitle: l.linuxGpuFollowDefaultDescription,
            ),
          LinuxChoice<String>(
            value: 'turnip',
            groupValue: current,
            enabled: turnipSupported,
            onChanged: (v) => choose('gpu', v, current),
            icon: M3EIcons.bolt_rounded,
            title: l.linuxGpuTurnip,
            subtitle: !turnipSupported
                ? t('uiForAdreno6xx8xxOnlyNotForThisGPU')
                : turnipReady
                ? l.linuxGpuTurnipDescription
                : '${l.linuxGpuTurnipDescription}\n${l.linuxGpuTurnipSetup}',
          ),
          LinuxChoice<String>(
            value: 'virgl',
            groupValue: current,
            enabled: hardwareAvailable,
            onChanged: (v) => choose('gpu', v, current),
            icon: M3EIcons.memory_rounded,
            title: l.linuxGpuHardware,
            subtitle: hardwareAvailable
                ? l.linuxGpuHardwareDescription
                : l.linuxGpuUnavailable,
          ),
          LinuxChoice<String>(
            value: 'software',
            groupValue: current,
            onChanged: (v) => choose('gpu', v, current),
            icon: M3EIcons.developer_board_rounded,
            title: l.linuxGpuSoftware,
            subtitle: l.linuxGpuSoftwareDescription,
          ),
        ],
      ),
      if (!_forContainer) ...[const SizedBox(height: 12), _gpuProbeGroup(t)],
      Padding(
        padding: const EdgeInsets.fromLTRB(8, 12, 8, 0),
        child: Text(
          [
            if (!_forContainer) l.linuxGpuDefaultNote,
            l.linuxGpuApplyNote,
            if (effective == 'virgl' && virglRunning > 0)
              l.linuxGpuRunning(virglRunning),
          ].join('\n'),
          style: theme.textTheme.bodySmall,
        ),
      ),
      LinuxSection(linuxText(context, 'uiThemeOfLinuxAndWindowsApps')),
      LinuxGroup(
        children: [
          LinuxChoice<String>(
            value: 'auto',
            groupValue: colorTheme,
            onChanged: _setTheme,
            icon: M3EIcons.brightness_auto_rounded,
            title: linuxText(context, 'uiAutomatic'),
            subtitle: linuxText(context, 'uiFollowsAndroidSDarkOrLightSetting'),
          ),
          LinuxChoice<String>(
            value: 'light',
            groupValue: colorTheme,
            onChanged: _setTheme,
            icon: M3EIcons.light_mode_rounded,
            title: linuxText(context, 'uiLight'),
          ),
          LinuxChoice<String>(
            value: 'dark',
            groupValue: colorTheme,
            onChanged: _setTheme,
            icon: M3EIcons.dark_mode_rounded,
            title: linuxText(context, 'uiDark'),
          ),
        ],
      ),
      Padding(
        padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
        child: Text(
          linuxText(
            context,
            'uiAppliesToAppsStartedFromNowOnOpenAppsSwitchWhenStartedAgain',
          ),
          style: theme.textTheme.bodySmall,
        ),
      ),
    ];
    List<Widget> integration() => [
      LinuxSection(linuxText(context, 'uiAndroidIntegration')),
      LinuxGroup(
        children: [
          LinuxToggle(
            value: clipboardSync,
            onChanged: _setClipboardSync,
            icon: M3EIcons.content_paste_rounded,
            title: linuxText(context, 'uiShareTheClipboard'),
            subtitle: linuxText(
              context,
              'uiTextCopiedInLinuxAppsGoesToAndroidAndTextCopiedInAndroidGoesToLinuxApps',
            ),
          ),
        ],
      ),
      LinuxSection(linuxText(context, 'uiHomeScreenLaunchers')),
      if (privilegedLaunchers)
        LinuxGroup(
          children: [
            M3EListItem(
              headline: linuxText(
                context,
                'uiInstalledThroughPrivilegedAccess',
              ),
              supportingText: linuxText(
                context,
                'uiLaunchersAreInstalledAndRemovedWithoutConfirmationThroughShizukuStellarOrTheBuiltInBinder',
              ),
              supportingMaxLines: null,
              leading: const Icon(M3EIcons.bolt_rounded),
            ),
          ],
        )
      else if (pinnedLaunchers)
        LinuxGroup(
          children: [
            M3EListItem(
              headline: linuxText(context, 'uiAddedAsShortcuts'),
              supportingText: linuxText(
                context,
                'uiLinuxAppsAreAddedAsHomeScreenShortcutsTheHomeAppConfirmsEachOneRemoveThemFromTheHomeScreen',
              ),
              supportingMaxLines: null,
              leading: const Icon(M3EIcons.add_to_home_screen_rounded),
            ),
          ],
        )
      else if (executionMode == 'android')
        LinuxGroup(
          children: [
            M3EListItem(
              headline: linuxText(context, 'uiAddedThroughAndroidSInstaller'),
              supportingText: linuxText(
                context,
                'uiAndroidAsksToConfirmEachLauncherYouAddOrRemove',
              ),
              supportingMaxLines: null,
              leading: const Icon(M3EIcons.install_mobile_rounded),
            ),
          ],
        )
      else
        LinuxGroup(
          children: [
            LinuxChoice<String>(
              value: 'ask',
              groupValue: launcherMode,
              onChanged: _setLauncherMode,
              icon: M3EIcons.help_outline_rounded,
              title: linuxText(context, 'uiAskTheFirstTime'),
            ),
            LinuxChoice<String>(
              value: 'auto',
              groupValue: launcherMode,
              // Silent installs need privileged access that is set up and running.
              enabled: silentAvailable || launcherMode == 'auto',
              onChanged: _setLauncherMode,
              icon: M3EIcons.bolt_rounded,
              title: linuxText(context, 'uiInstallAutomatically'),
              subtitle: silentAvailable
                  ? linuxText(
                      context,
                      'uiInstallsWithoutConfirmationThroughShizukuStellarOrTheBuiltInBinder',
                    )
                  : linuxText(
                      context,
                      'uiAvailableOncePrivilegedAccessThroughShizukuStellarOrTheBuiltInBinderIsSetUp',
                    ),
            ),
            LinuxChoice<String>(
              value: 'manual',
              groupValue: launcherMode,
              onChanged: _setLauncherMode,
              icon: M3EIcons.install_mobile_rounded,
              title: linuxText(context, 'uiInstallManually'),
              subtitle: linuxText(
                context,
                'uiAndroidAsksToConfirmEachInstallation',
              ),
            ),
          ],
        ),
    ];
    List<Widget> store() => [
      LinuxStoreSourcesPanel(
        containers: containers,
        flatpakCompatibility: settings?['flatpakCompatibility'] == true,
        onFlatpakCompatibility: (value) =>
            _save({'flatpakCompatibility': value}),
      ),
    ];
    List<Widget> container() => [
      LinuxSection(l.linuxWmSection),
      Builder(
        builder: (context) {
          final wm = _containerSettings?['wm'] == 'dextop' ? 'dextop' : '';
          return LinuxGroup(
            children: [
              LinuxChoice<String>(
                value: '',
                groupValue: wm,
                onChanged: (v) => choose('wm', v, wm),
                icon: M3EIcons.sync_alt_rounded,
                title: l.linuxWmDefault,
              ),
              LinuxChoice<String>(
                value: 'dextop',
                groupValue: wm,
                onChanged: (v) => choose('wm', v, wm),
                icon: M3EIcons.web_asset_rounded,
                title: t('uiDextopProduct'),
                subtitle: l.linuxWmDextopDescription,
              ),
            ],
          );
        },
      ),
      LinuxSection(
        _forContainer ? l.linuxGpuSection : l.linuxGpuDefaultSection,
      ),
      LinuxGroup(
        children: [
          if (_forContainer)
            LinuxChoice<String>(
              value: '',
              groupValue: current,
              onChanged: (v) => choose('gpu', v, current),
              icon: M3EIcons.sync_alt_rounded,
              title: l.linuxGpuFollowDefault,
              subtitle: l.linuxGpuFollowDefaultDescription,
            ),
          LinuxChoice<String>(
            value: 'turnip',
            groupValue: current,
            enabled: turnipSupported,
            onChanged: (v) => choose('gpu', v, current),
            icon: M3EIcons.bolt_rounded,
            title: l.linuxGpuTurnip,
            subtitle: !turnipSupported
                ? t('uiForAdreno6xx8xxOnlyNotForThisGPU')
                : turnipReady
                ? l.linuxGpuTurnipDescription
                : '${l.linuxGpuTurnipDescription}\n${l.linuxGpuTurnipSetup}',
          ),
          LinuxChoice<String>(
            value: 'virgl',
            groupValue: current,
            enabled: hardwareAvailable,
            onChanged: (v) => choose('gpu', v, current),
            icon: M3EIcons.memory_rounded,
            title: l.linuxGpuHardware,
            subtitle: hardwareAvailable
                ? l.linuxGpuHardwareDescription
                : l.linuxGpuUnavailable,
          ),
          LinuxChoice<String>(
            value: 'software',
            groupValue: current,
            onChanged: (v) => choose('gpu', v, current),
            icon: M3EIcons.developer_board_rounded,
            title: l.linuxGpuSoftware,
            subtitle: l.linuxGpuSoftwareDescription,
          ),
        ],
      ),
      if (!_forContainer) ...[const SizedBox(height: 12), _gpuProbeGroup(t)],
      Padding(
        padding: const EdgeInsets.fromLTRB(8, 12, 8, 0),
        child: Text(
          [
            if (!_forContainer) l.linuxGpuDefaultNote,
            l.linuxGpuApplyNote,
            if (effective == 'virgl' && virglRunning > 0)
              l.linuxGpuRunning(virglRunning),
          ].join('\n'),
          style: theme.textTheme.bodySmall,
        ),
      ),
      LinuxCompatPanel(containerId: widget.containerId),
    ];

    Widget category(String id, IconData icon, String title, String summary) =>
        M3EListItem(
          supportingMaxLines: null,
          headline: title,
          supportingText: summary,
          leading: Icon(icon),
          trailing: const Icon(M3EIcons.chevron_right_rounded),
          onTap: () => setState(() => _page = id),
        );

    List<Widget> hub() => [
      const SizedBox(height: 8),
      LinuxGroup(
        children: [
          category(
            'general',
            M3EIcons.tune_rounded,
            t('uiGeneral'),
            '$modeName · ${t('uiDefaultEnvironment')}',
          ),
          category(
            'display',
            M3EIcons.palette_rounded,
            t('uiDisplayAndGraphics'),
            '$gpuName · $themeName',
          ),
          category(
            'network',
            M3EIcons.dns_rounded,
            t('uiNetwork'),
            t('uiLinuxDNS'),
          ),
          category(
            'integration',
            M3EIcons.phone_android_rounded,
            t('uiAndroidIntegrationAndPermissions'),
            t('uiIntegrationPermissionsCompatibilitySSHVNCAndCalendars'),
          ),
          category(
            'services',
            M3EIcons.settings_applications_rounded,
            t('uiServicesAndPrinting'),
            t('uiServiceCompatibilityAndAndroidPrinting'),
          ),
          category(
            'resources',
            M3EIcons.monitor_heart_rounded,
            t('uiResources'),
            t('uiProcessesCPUGPUStorageHardwareContainersAndDrivers'),
          ),
          category(
            'store',
            M3EIcons.storefront_rounded,
            'Store',
            t('uiSourcesAndTheirSetup'),
          ),
          if (macosCompatibilityEnabled)
            category(
              'macos',
              M3EIcons.computer_rounded,
              t('uiMacOSCompatibility4f7a9442'),
              t('uiCompatibilityRuntimeAndLocalAppRegistration'),
            ),
        ],
      ),
      LinuxSection(t('uiReset')),
      LinuxGroup(
        children: [
          M3EListItem(
            supportingMaxLines: null,
            headline: t('uiResetLinux'),
            supportingText: t('uiDeletesAllLinuxDataBackToBeforeTheFirstSetup'),
            leading: Icon(
              M3EIcons.restart_alt_rounded,
              color: theme.colorScheme.error,
            ),
            onTap: _resetLinux,
          ),
        ],
      ),
    ];

    final page = _forContainer ? 'container' : _page;
    final title = switch (page) {
      'container' => l.linuxContainerSettings,
      'general' => t('uiGeneral'),
      'display' => t('uiDisplayAndGraphics'),
      'network' => t('uiNetwork'),
      'integration' => t('uiAndroidIntegrationAndPermissions'),
      'store' => 'Store',
      'resources' => t('uiResources'),
      'services' => t('uiServicesAndPrinting'),
      'macos' => t('uiMacOSCompatibility4f7a9442'),
      _ => l.linuxSettingsTitle,
    };
    final content = switch (page) {
      'container' => container(),
      'general' => general(),
      'display' => display(),
      'network' => network(),
      'integration' => [
        ...integration(),
        LinuxCompatPanel(containerId: widget.containerId),
        LinuxSection(t('uiSSHVNCAndCalendars')),
        LinuxIntegrationsPanel(
          containers: containers,
          features: const {'ssh', 'vnc', 'calendar'},
        ),
      ],
      'store' => store(),
      'resources' => const [LinuxResourcesPanel()],
      'services' => [
        LinuxIntegrationsPanel(
          containers: containers,
          features: const {'services', 'printing'},
        ),
      ],
      'macos' =>
        macosCompatibilityEnabled
            ? [LinuxMacosPanel(containers: containers)]
            : hub(),
      _ => hub(),
    };

    return PopScope(
      canPop: _page == null,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) setState(() => _page = null);
      },
      child: Scaffold(
        backgroundColor: theme.colorScheme.surface,
        appBar: M3EAppBar.top(
          automaticallyImplyLeading: true,
          titleText: title,
          subtitleText: _forContainer ? widget.containerName : null,
        ),
        body: AnimatedSwitcher(
          duration: const Duration(milliseconds: 240),
          child: settings == null && error == null
              ? const Center(child: M3ELoadingIndicator())
              : Center(
                  key: ValueKey(page),
                  child: ConstrainedBox(
                    // Resources is a task manager: its graphs and table use the window's width.
                    constraints: BoxConstraints(
                      maxWidth: page == 'resources' ? double.infinity : 760,
                    ),
                    child: ListView(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
                      children: [
                        if (saving) const M3EProgressIndicator.linearWavy(),
                        if (error != null)
                          LinuxGroup(
                            children: [
                              M3EListItem(
                                headline: error!,
                                leading: const Icon(
                                  M3EIcons.error_outline_rounded,
                                ),
                              ),
                            ],
                          ),
                        ...content,
                      ],
                    ),
                  ),
                ),
        ),
      ),
    );
  }

  Future<void> _resetLinux() async {
    String t(String key) => linuxText(context, key);
    final confirmed = await M3EDialog.show<bool>(
      context,
      dialog: Builder(
        builder: (dialogContext) => M3EDialog(
          title: t('uiResetLinux29825e1a'),
          icon: const Icon(M3EIcons.restart_alt_rounded),
          content: Text(
            t(
              'uiAllLinuxDataIsDeletedEveryEnvironmentOfBothExecutionModesWithItsAppsAndFilesTheLinuxSettingsAndTheSt',
            ),
          ),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: Text(t('uiCancel')),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: Text(t('uiReset')),
            ),
          ],
        ),
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => saving = true);
    // Progress of the reset, shown until it is done: (fraction or null, what runs now).
    final progress = ValueNotifier<(double?, String)>((null, t('uiPreparing')));
    final navigator = Navigator.of(context);
    unawaited(
      showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (dialogContext) => PopScope(
          canPop: false,
          child: M3EDialog(
            title: t('uiResettingLinux'),
            icon: const Icon(M3EIcons.restart_alt_rounded),
            content: ValueListenableBuilder<(double?, String)>(
              valueListenable: progress,
              builder: (context, value, _) => Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  M3EProgressIndicator.linearWavy(value: value.$1),
                  const SizedBox(height: 16),
                  Text(value.$2),
                ],
              ),
            ),
            actions: const [],
          ),
        ),
      ),
    );
    try {
      // Environments one by one, so the dialog can say which one goes.
      final list =
          (await _channel.invokeListMethod<Object?>('list') ?? const [])
              .whereType<Map<Object?, Object?>>()
              .toList();
      final total = list.length + 1;
      for (final (index, container) in list.indexed) {
        progress.value = (
          index / total,
          inlineFormat(t('uiDeletingLinuxEnvironments012'), [
            index + 1,
            list.length,
            container['name'] ?? container['id'],
          ]),
        );
        await _channel.invokeMethod<bool>('remove', {'id': container['id']});
      }
      progress.value = (
        list.length / total,
        t('uiDeletingTheSettingsAndTheOtherExecutionModeSData'),
      );
      final result = await _channel.invokeMapMethod<String, Object?>(
        'resetLinux',
      );
      progress.value = (1, t('uiDone'));
      if (result?['privilegedLeft'] == true && mounted) {
        M3ESnackbar.show(
          context,
          message: t(
            'uiNotConnectedToPrivilegedAccessSoTheFullPrivilegeDataDataLocalTmpDextopLinuxWasNotDeleted',
          ),
        );
      }
      final preferences = await SharedPreferences.getInstance();
      await preferences.remove(LinuxFirstRun.doneKey);
      await preferences.remove(LinuxFirstRun.prepareKey);
      navigator.pop();
      if (mounted) navigator.pop();
    } on PlatformException catch (e) {
      navigator.pop();
      if (mounted) M3ESnackbar.show(context, message: e.message ?? '$e');
    } finally {
      progress.dispose();
      if (mounted) setState(() => saving = false);
    }
  }
}
