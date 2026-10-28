import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'l10n/app_localizations.dart';
import 'linux/linux_expressive.dart';
import 'linux/linux_widgets.dart';
import 'linux/linux_store_sources.dart';

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

  /// Whether Android and Linux share the clipboard.
  bool clipboardSync = true;

  /// Light or dark Linux and Windows apps: 'auto' (as Android), 'light' or 'dark'.
  String colorTheme = 'auto';

  bool get _forContainer => widget.containerId != null;

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
          ? true
          : await _channel.invokeMethod<bool>('clipboardSync') ?? true;
      final appTheme = _forContainer
          ? 'auto'
          : await _channel.invokeMethod<String>('linuxTheme') ?? 'auto';
      if (mounted) {
        setState(() {
          settings = result;
          containers = list;
          launcherMode = launcher?['mode'] as String? ?? 'ask';
          silentAvailable = launcher?['silentAvailable'] == true;
          clipboardSync = clipboard;
          colorTheme = appTheme;
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
    final globalGpu = settings?['gpu'] as String? ?? 'turnip';
    // A container without its own value follows the default ('').
    final current = _forContainer
        ? (_containerSettings?['gpu'] as String?) ?? ''
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

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: M3EAppBar.top(
        automaticallyImplyLeading: true,
        titleText: _forContainer
            ? l.linuxContainerSettings
            : l.linuxSettingsTitle,
        subtitleText: _forContainer ? widget.containerName : null,
      ),
      body: AnimatedSwitcher(
        duration: const Duration(milliseconds: 240),
        child: settings == null && error == null
            ? const Center(child: M3ELoadingIndicator())
            : ListView(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
                children: [
                  if (saving) const M3EProgressIndicator.linearWavy(),
                  if (error != null)
                    LinuxGroup(
                      children: [
                        M3EListItem(
                          headline: error!,
                          leading: const Icon(M3EIcons.error_outline_rounded),
                        ),
                      ],
                    ),
                  if (!_forContainer) ...[
                    LinuxStoreSourcesPanel(containers: containers),
                    LinuxGroup(
                      children: [
                        LinuxToggle(
                          value: settings?['flatpakCompatibility'] == true,
                          onChanged: (value) =>
                              _save({'flatpakCompatibility': value}),
                          icon: M3EIcons.science_rounded,
                          title: linuxText(
                            context,
                            'Flatpak の互換実行（試験的）',
                            'Flatpak compatibility execution (experimental)',
                          ),
                          subtitle: linuxText(
                            context,
                            '名前空間が使えない端末で、proot 経由で起動します。Flatpak のサンドボックスや権限制限は適用されず、通常の Linux アプリと同じ shell 権限で動きます。ソフトウェア描画を使用し、すべてのアプリの動作は保証しません。',
                            'Runs deployed apps through proot on devices without namespaces. Flatpak sandbox and permission restrictions are not enforced; apps have the same shell privileges as ordinary Linux apps. Uses software rendering; not all apps are compatible.',
                          ),
                        ),
                      ],
                    ),
                    LinuxSection(l.linuxDefaultSection),
                    LinuxGroup(
                      children: [
                        M3EListItem(
                          headline: l.linuxDefaultContainer,
                          supportingText: l.linuxDefaultContainerDescription,
                          leading: const Icon(M3EIcons.star_outline_rounded),
                        ),
                        LinuxChoice<String>(
                          value: '',
                          groupValue: defaultContainer,
                          onChanged: (v) =>
                              choose('defaultContainer', v, defaultContainer),
                          title: l.linuxDefaultContainerAll,
                        ),
                        for (final container in containers)
                          LinuxChoice<String>(
                            value: container['id'] as String? ?? '',
                            groupValue: defaultContainer,
                            onChanged: (v) =>
                                choose('defaultContainer', v, defaultContainer),
                            title: container['name'] as String? ?? '',
                          ),
                      ],
                    ),
                  ],
                  if (!_forContainer) ...[
                    LinuxSection(
                      linuxText(context, 'Android との連携', 'Android integration'),
                    ),
                    LinuxGroup(
                      children: [
                        LinuxToggle(
                          value: clipboardSync,
                          onChanged: _setClipboardSync,
                          icon: M3EIcons.content_paste_rounded,
                          title: linuxText(
                            context,
                            'クリップボードを共有',
                            'Share the clipboard',
                          ),
                          subtitle: linuxText(
                            context,
                            'Linux アプリでコピーした文字を Android に、Android でコピーした文字を Linux アプリに渡します',
                            'Text copied in Linux apps goes to Android, and text copied in Android goes to Linux apps',
                          ),
                        ),
                      ],
                    ),
                    LinuxSection(
                      linuxText(
                        context,
                        'Linux・Windows アプリのテーマ',
                        'Theme of Linux and Windows apps',
                      ),
                    ),
                    LinuxGroup(
                      children: [
                        LinuxChoice<String>(
                          value: 'auto',
                          groupValue: colorTheme,
                          onChanged: _setTheme,
                          icon: M3EIcons.brightness_auto_rounded,
                          title: linuxText(context, '自動', 'Automatic'),
                          subtitle: linuxText(
                            context,
                            'Android のダーク・ライト設定に合わせます',
                            "Follows Android's dark or light setting",
                          ),
                        ),
                        LinuxChoice<String>(
                          value: 'light',
                          groupValue: colorTheme,
                          onChanged: _setTheme,
                          icon: M3EIcons.light_mode_rounded,
                          title: linuxText(context, 'ライト', 'Light'),
                        ),
                        LinuxChoice<String>(
                          value: 'dark',
                          groupValue: colorTheme,
                          onChanged: _setTheme,
                          icon: M3EIcons.dark_mode_rounded,
                          title: linuxText(context, 'ダーク', 'Dark'),
                        ),
                      ],
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
                      child: Text(
                        linuxText(
                          context,
                          '次に起動したアプリから反映されます。開いているアプリは起動し直すと切り替わります。',
                          'Applies to apps started from now on; open apps switch when started again.',
                        ),
                        style: theme.textTheme.bodySmall,
                      ),
                    ),
                    LinuxSection(
                      linuxText(
                        context,
                        'ホーム画面のランチャー',
                        'Home-screen launchers',
                      ),
                    ),
                    LinuxGroup(
                      children: [
                        LinuxChoice<String>(
                          value: 'ask',
                          groupValue: launcherMode,
                          onChanged: _setLauncherMode,
                          icon: M3EIcons.help_outline_rounded,
                          title: linuxText(
                            context,
                            '初回に確認する',
                            'Ask the first time',
                          ),
                        ),
                        LinuxChoice<String>(
                          value: 'auto',
                          groupValue: launcherMode,
                          onChanged: _setLauncherMode,
                          icon: M3EIcons.bolt_rounded,
                          title: linuxText(
                            context,
                            '自動でインストール',
                            'Install automatically',
                          ),
                          subtitle: silentAvailable
                              ? linuxText(
                                  context,
                                  'Shizuku・Stellar または Dextop の内蔵アクセスで、確認なしでインストールします',
                                  "Installs without confirmation through Shizuku, Stellar or Dextop's built-in access",
                                )
                              : linuxText(
                                  context,
                                  'いまは Shizuku・Stellar・内蔵アクセスが使えないため、手動でインストールします',
                                  'Shizuku, Stellar and built-in access are unavailable now, so Android asks each time',
                                ),
                        ),
                        LinuxChoice<String>(
                          value: 'manual',
                          groupValue: launcherMode,
                          onChanged: _setLauncherMode,
                          icon: M3EIcons.install_mobile_rounded,
                          title: linuxText(
                            context,
                            '手動でインストール',
                            'Install manually',
                          ),
                          subtitle: linuxText(
                            context,
                            'Android のインストール確認画面を毎回表示します',
                            'Android asks to confirm each installation',
                          ),
                        ),
                      ],
                    ),
                  ],
                  if (_forContainer) ...[
                    LinuxSection(l.linuxWmSection),
                    Builder(
                      builder: (context) {
                        final wm = _containerSettings?['wm'] == 'dextop'
                            ? 'dextop'
                            : '';
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
                              title: 'Dextop',
                              subtitle: l.linuxWmDextopDescription,
                            ),
                          ],
                        );
                      },
                    ),
                  ],
                  LinuxSection(
                    _forContainer
                        ? l.linuxGpuSection
                        : l.linuxGpuDefaultSection,
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
                        onChanged: (v) => choose('gpu', v, current),
                        icon: M3EIcons.bolt_rounded,
                        title: l.linuxGpuTurnip,
                        subtitle: turnipReady
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
                ],
              ),
      ),
    );
  }
}
