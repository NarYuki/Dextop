import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';

class LinuxMacosPanel extends StatelessWidget {
  const LinuxMacosPanel({super.key, required this.containers});
  final List<Map<Object?, Object?>> containers;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      LinuxSection(
        linuxText(
          context,
          'macOS 互換（試験的）',
          'macOS compatibility (experimental)',
        ),
      ),
      LinuxGroup(
        children: [
          M3EListItem(
            leading: const Icon(M3EIcons.info_outline_rounded),
            headline: linuxText(
              context,
              'ローカルの .app を実行',
              'Run local .app bundles',
            ),
            supportingMaxLines: null,
            supportingText: linuxText(
              context,
              '互換環境が必要です。対応状況はアプリごとに異なり、SwiftUI と Intel アプリの実行は未対応です。',
              'Requires a compatibility runtime. Support varies by app; SwiftUI and Intel apps are not supported yet.',
            ),
          ),
        ],
      ),
      if (containers.isEmpty)
        Padding(
          padding: const EdgeInsets.all(16),
          child: Text(
            linuxText(
              context,
              '先に Linux 環境をセットアップしてください。',
              'Set up a Linux container first.',
            ),
          ),
        ),
      for (final container in containers)
        _MacosContainer(
          key: ValueKey(container['id']),
          id: container['id'] as String,
          name: container['name'] as String? ?? container['id'] as String,
        ),
    ],
  );
}

class _MacosContainer extends StatefulWidget {
  const _MacosContainer({super.key, required this.id, required this.name});
  final String id;
  final String name;
  @override
  State<_MacosContainer> createState() => _MacosContainerState();
}

class _MacosContainerState extends State<_MacosContainer> {
  static const channel = MethodChannel('app.freedextop/linux');
  Map<Object?, Object?>? status;
  String? error;
  bool busy = false;
  String t(String ja, String en) => linuxText(context, ja, en);

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final next = await channel.invokeMapMethod<Object?, Object?>(
        'macosStatus',
        {'id': widget.id},
      );
      if (mounted) {
        setState(() {
          status = next;
          error = null;
        });
      }
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    }
  }

  Future<void> _run(String method, String value) async {
    if (busy) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await channel.invokeMethod<Object?>(method, {
        'id': widget.id,
        'value': value,
      });
      await _load();
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _register() async {
    var path = '';
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(t('macOS アプリを登録', 'Register a macOS app')),
        content: TextField(
          onChanged: (value) => path = value,
          autofocus: true,
          decoration: InputDecoration(
            labelText: t('Linux 内の .app のパス', '.app path inside Linux'),
            hintText: '/root/Applications/Example.app',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(t('キャンセル', 'Cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, path.trim()),
            child: Text(t('登録', 'Register')),
          ),
        ],
      ),
    );
    if (value != null && value.isNotEmpty && mounted) {
      await _run('macosRegister', value);
    }
  }

  Future<void> _remove(Map<Object?, Object?> app) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(t('登録を解除', 'Remove registration')),
        content: Text(
          t(
            'アプリ本体と保存データは残ります。',
            'The application bundle and saved data will be kept.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(t('キャンセル', 'Cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(t('解除', 'Remove')),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      await _run('macosUnregister', app['key'] as String);
    }
  }

  @override
  Widget build(BuildContext context) {
    final present =
        status?['componentsPresent'] == true && status?['guiPresent'] == true;
    final apps = (status?['applications'] as List? ?? const [])
        .whereType<Map<Object?, Object?>>();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LinuxSection(widget.name),
        LinuxGroup(
          children: [
            M3EListItem(
              leading: const Icon(M3EIcons.computer_rounded),
              headline: t('互換環境', 'Compatibility runtime'),
              supportingMaxLines: null,
              supportingText: status == null
                  ? error == null
                        ? t('確認中…', 'Checking…')
                        : t('状態を確認できませんでした', 'Could not check runtime status')
                  : present
                  ? t(
                      '配置済み・アプリの互換性は未検証',
                      'Components installed; app compatibility is unverified',
                    )
                  : t(
                      '未導入または必要なファイルが不足しています',
                      'Not installed or required files are missing',
                    ),
              trailing: const Icon(M3EIcons.refresh_rounded),
              onTap: busy ? null : _load,
            ),
            if (!present && status != null)
              M3EListItem(
                leading: const Icon(M3EIcons.download_rounded),
                headline: t('互換環境をインストール', 'Install compatibility runtime'),
                supportingMaxLines: null,
                supportingText: status?['bundleAvailable'] == true
                    ? t(
                        'Dextop に同梱された互換環境を検証して追加します',
                        'Verify and install the runtime bundled with Dextop',
                      )
                    : t(
                        'このバージョンにはインストール用の互換環境が同梱されていません',
                        'This version does not include an installable runtime',
                      ),
                trailing: const Icon(M3EIcons.chevron_right_rounded),
                onTap: busy || status?['bundleAvailable'] != true
                    ? null
                    : () => _run('macosInstall', ''),
              ),
            M3EListItem(
              leading: const Icon(M3EIcons.add_rounded),
              headline: t('アプリを登録', 'Register an application'),
              supportingText: t(
                '登録したアプリはアプリ一覧に表示されます',
                'Registered apps appear in the application list',
              ),
              supportingMaxLines: null,
              trailing: const Icon(M3EIcons.chevron_right_rounded),
              onTap: busy || !present ? null : _register,
            ),
            for (final app in apps)
              M3EListItem(
                leading: const Icon(M3EIcons.apps_rounded),
                headline: app['name'] as String,
                supportingMaxLines: null,
                supportingText: app['present'] == true
                    ? app['bundle'] as String?
                    : t('アプリが見つかりません', 'Application bundle not found'),
                trailing: const Icon(M3EIcons.remove_circle_outline_rounded),
                onTap: busy ? null : () => _remove(app),
              ),
          ],
        ),
        if (busy)
          const Padding(
            padding: EdgeInsets.all(16),
            child: LinearProgressIndicator(),
          ),
        if (error != null)
          Padding(
            padding: const EdgeInsets.all(16),
            child: SelectableText(error!),
          ),
      ],
    );
  }
}
