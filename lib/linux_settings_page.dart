import 'l10n/inline_text.dart';

import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'l10n/app_localizations.dart';
import 'linux/linux_expressive.dart';
import 'linux/linux_widgets.dart';
import 'linux/linux_store_sources.dart';
import 'linux/linux_advanced_settings.dart';
import 'linux/linux_compat.dart';
import 'linux/linux_resources.dart';
import 'linux/linux_first_run.dart';

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
  bool clipboardSync = true;

  /// Light or dark Linux and Windows apps: 'auto' (as Android), 'light' or 'dark'.
  String colorTheme = 'auto';

  bool get _forContainer => widget.containerId != null;

  /// The category shown ('general', 'display', 'integration', 'store'), or the list of them.
  String? _page;

  /// 'privileged' or 'android', for the General category's summary.
  String executionMode = 'privileged';

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
      final mode = await _channel.invokeMethod<String>('executionMode');
      final appTheme = _forContainer
          ? 'auto'
          : await _channel.invokeMethod<String>('linuxTheme') ?? 'auto';
      if (mounted) {
        setState(() {
          settings = result;
          containers = list;
          launcherMode = launcher?['mode'] as String? ?? 'ask';
          silentAvailable = launcher?['silentAvailable'] == true;
          pinnedLaunchers = launcher?['pinned'] == true;
          privilegedLaunchers = launcher?['privilegedOnly'] == true;
          clipboardSync = clipboard;
          colorTheme = appTheme;
          executionMode = mode ?? 'privileged';
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

    String t(String ja, String en) => linuxText(context, ja, en);
    final modeName = executionMode == 'android'
        ? t('Android（試験的）', 'Android (experimental)')
        : t('完全特権', 'Full privilege');
    final gpuName = switch (globalGpu) {
      'virgl' => l.linuxGpuHardware,
      'software' => l.linuxGpuSoftware,
      _ => l.linuxGpuTurnip,
    };
    final themeName = switch (colorTheme) {
      'light' => t('ライト', 'Light'),
      'dark' => t('ダーク', 'Dark'),
      _ => t('自動', 'Automatic'),
    };

    List<Widget> general() => [
      LinuxGroup(
        children: [
          M3EListItem(
            supportingMaxLines: null,
            headline: linuxText(context, '高度な設定', 'Advanced settings'),
            supportingText: linuxText(
              context,
              'Linux の実行方式と特権アクセス',
              'Linux execution mode and privileged access',
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
    List<Widget> display() => [
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
                ? t(
                    'Adreno 6xx〜8xx 専用です。この GPU では使えません',
                    'For Adreno 6xx–8xx only; not for this GPU',
                  )
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
    ];
    List<Widget> integration() => [
      LinuxSection(linuxText(context, 'Android との連携', 'Android integration')),
      LinuxGroup(
        children: [
          LinuxToggle(
            value: clipboardSync,
            onChanged: _setClipboardSync,
            icon: M3EIcons.content_paste_rounded,
            title: linuxText(context, 'クリップボードを共有', 'Share the clipboard'),
            subtitle: linuxText(
              context,
              'Linux アプリでコピーした文字を Android に、Android でコピーした文字を Linux アプリに渡します',
              'Text copied in Linux apps goes to Android, and text copied in Android goes to Linux apps',
            ),
          ),
        ],
      ),
      LinuxSection(linuxText(context, 'ホーム画面のランチャー', 'Home-screen launchers')),
      if (privilegedLaunchers)
        LinuxGroup(
          children: [
            M3EListItem(
              headline: linuxText(
                context,
                '特権アクセスで自動インストール',
                'Installed through privileged access',
              ),
              supportingText: linuxText(
                context,
                'Shizuku・Stellar・内蔵バインダで、ランチャーを確認なしでインストール・削除します。',
                'Launchers are installed and removed without confirmation through Shizuku, Stellar or the built-in binder.',
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
              headline: linuxText(
                context,
                'ショートカットとして追加',
                'Added as shortcuts',
              ),
              supportingText: linuxText(
                context,
                'Linux アプリはホーム画面のショートカットとして追加されます。追加するときはホームアプリが確認します。削除はホーム画面からショートカットを外してください。',
                'Linux apps are added as home-screen shortcuts; the home app confirms each one. Remove them from the home screen.',
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
              headline: linuxText(
                context,
                'Android のインストール画面で追加',
                "Added through Android's installer",
              ),
              supportingText: linuxText(
                context,
                'ランチャーを追加・削除するたびに、Android のインストール確認画面が表示されます。',
                'Android asks to confirm each launcher you add or remove.',
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
              title: linuxText(context, '初回に確認する', 'Ask the first time'),
            ),
            LinuxChoice<String>(
              value: 'auto',
              groupValue: launcherMode,
              onChanged: _setLauncherMode,
              icon: M3EIcons.bolt_rounded,
              title: linuxText(context, '自動でインストール', 'Install automatically'),
              subtitle: linuxText(
                context,
                'Shizuku・Stellar・内蔵バインダで、確認なしでインストールします',
                'Installs without confirmation through Shizuku, Stellar or the built-in binder',
              ),
            ),
            LinuxChoice<String>(
              value: 'manual',
              groupValue: launcherMode,
              onChanged: _setLauncherMode,
              icon: M3EIcons.install_mobile_rounded,
              title: linuxText(context, '手動でインストール', 'Install manually'),
              subtitle: linuxText(
                context,
                'Android のインストール確認画面を毎回表示します',
                'Android asks to confirm each installation',
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
                title: 'Dextop',
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
                ? t(
                    'Adreno 6xx〜8xx 専用です。この GPU では使えません',
                    'For Adreno 6xx–8xx only; not for this GPU',
                  )
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
            t('一般', 'General'),
            '$modeName · ${t('既定の Linux 環境', 'Default environment')}',
          ),
          category(
            'display',
            M3EIcons.palette_rounded,
            t('表示とグラフィックス', 'Display and graphics'),
            '$gpuName · $themeName',
          ),
          category(
            'integration',
            M3EIcons.phone_android_rounded,
            t('Android との連携', 'Android integration'),
            t('クリップボード・ホーム画面のランチャー', 'Clipboard and home-screen launchers'),
          ),
          category(
            'compat',
            M3EIcons.verified_user_rounded,
            t('互換性と権限', 'Compatibility and permissions'),
            t(
              'プロセス数の制限・ストレージ・カメラとマイク・USB 機器',
              'Process limit, storage, camera and microphone, USB devices',
            ),
          ),
          category(
            'resources',
            M3EIcons.monitor_heart_rounded,
            t('リソース', 'Resources'),
            t(
              'プロセス・CPU・GPU・ストレージ・ハードウェア・コンテナとドライバの情報',
              'Processes, CPU, GPU, storage, hardware, containers and drivers',
            ),
          ),
          category(
            'store',
            M3EIcons.storefront_rounded,
            'Store',
            t('配布元と準備', 'Sources and their setup'),
          ),
        ],
      ),
      LinuxSection(t('初期化', 'Reset')),
      LinuxGroup(
        children: [
          M3EListItem(
            supportingMaxLines: null,
            headline: t('Linux を初期化', 'Reset Linux'),
            supportingText: t(
              'Linux の全データを削除し、初期セットアップ前の状態に戻します',
              'Deletes all Linux data, back to before the first setup',
            ),
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
      'general' => t('一般', 'General'),
      'display' => t('表示とグラフィックス', 'Display and graphics'),
      'integration' => t('Android との連携', 'Android integration'),
      'store' => 'Store',
      'compat' => t('互換性と権限', 'Compatibility and permissions'),
      'resources' => t('リソース', 'Resources'),
      _ => l.linuxSettingsTitle,
    };
    final content = switch (page) {
      'container' => container(),
      'general' => general(),
      'display' => display(),
      'integration' => integration(),
      'store' => store(),
      'compat' => const [LinuxCompatPanel()],
      'resources' => const [LinuxResourcesPanel()],
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
                    constraints: const BoxConstraints(maxWidth: 760),
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
    String t(String ja, String en) => linuxText(context, ja, en);
    final confirmed = await M3EDialog.show<bool>(
      context,
      dialog: Builder(
        builder: (dialogContext) => M3EDialog(
          title: t('Linux を初期化しますか？', 'Reset Linux?'),
          icon: const Icon(M3EIcons.restart_alt_rounded),
          content: Text(
            t(
              '両方の実行方式のすべての Linux 環境（中のアプリとファイルを含む）、Linux 設定、Store・アクセス許可・コントローラーの設定まで、Linux の全データを削除します。元に戻せません。',
              'All Linux data is deleted: every environment of both execution modes with its apps and files, the Linux settings, and the Store, access and controller choices. This cannot be undone.',
            ),
          ),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: Text(t('キャンセル', 'Cancel')),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: Text(t('初期化', 'Reset')),
            ),
          ],
        ),
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => saving = true);
    // Progress of the reset, shown until it is done: (fraction or null, what runs now).
    final progress = ValueNotifier<(double?, String)>((
      null,
      t('準備しています…', 'Preparing…'),
    ));
    final navigator = Navigator.of(context);
    unawaited(
      showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (dialogContext) => PopScope(
          canPop: false,
          child: M3EDialog(
            title: t('Linux を初期化しています', 'Resetting Linux'),
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
          inlineFormat(
            t(
              'Linux 環境を削除しています（{0}/{1}）\n{2}',
              'Deleting Linux environments ({0}/{1})\n{2}',
            ),
            [index + 1, list.length, container['name'] ?? container['id']],
          ),
        );
        await _channel.invokeMethod<bool>('remove', {'id': container['id']});
      }
      progress.value = (
        list.length / total,
        t(
          '設定と、もう一方の実行方式のデータを削除しています',
          "Deleting the settings and the other execution mode's data",
        ),
      );
      final result = await _channel.invokeMapMethod<String, Object?>(
        'resetLinux',
      );
      progress.value = (1, t('完了しました', 'Done'));
      if (result?['privilegedLeft'] == true && mounted) {
        M3ESnackbar.show(
          context,
          message: t(
            '特権アクセスに接続していないため、完全特権方式のデータ（/data/local/tmp/dextop/linux）は削除できませんでした',
            'Not connected to privileged access, so the full-privilege data (/data/local/tmp/dextop/linux) was not deleted',
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
