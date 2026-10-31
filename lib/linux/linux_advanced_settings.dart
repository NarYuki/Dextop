import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';

class LinuxAdvancedSettingsPage extends StatefulWidget {
  const LinuxAdvancedSettingsPage({super.key});
  @override
  State<LinuxAdvancedSettingsPage> createState() =>
      _LinuxAdvancedSettingsPageState();
}

class _LinuxAdvancedSettingsPageState extends State<LinuxAdvancedSettingsPage> {
  static const _channel = MethodChannel('app.freedextop/linux');
  String? mode;
  String? error;
  bool saving = false;
  String t(String ja, String en) => linuxText(context, ja, en);

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final value = await _channel.invokeMethod<String>('executionMode');
      if (mounted) setState(() => mode = value ?? 'privileged');
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    }
  }

  Future<void> _choose(String value) async {
    if (saving || mode == null || value == mode) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.admin_panel_settings_rounded),
        title: Text(t('Linux の実行方式を切り替える', 'Change Linux execution mode')),
        content: Text(
          t(
            '実行中の Linux アプリを終了して切り替えます。先に作業を保存してください。方式ごとに環境を保持し、既存環境は削除・上書きしません。Android では、通常のアプリ権限で動く新しい環境をセットアップします。',
            'Running Linux apps will close. Save your work first. Each mode keeps its own environment; existing data is not deleted or overwritten. Android sets up a new environment running with ordinary app permissions.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(t('キャンセル', 'Cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(t('切り替える', 'Switch')),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => saving = true);
    try {
      final result = await _channel.invokeMethod<String>('setExecutionMode', {
        'mode': value,
        'stop': true,
      });
      if (mounted) {
        setState(() {
          mode = result;
          error = null;
        });
      }
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: M3EAppBar.top(
      titleText: t('Linux の高度な設定', 'Advanced Linux settings'),
      automaticallyImplyLeading: true,
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
      children: [
        LinuxSection(t('実行方式', 'Execution mode')),
        if (error != null)
          Padding(padding: const EdgeInsets.all(8), child: Text(error!)),
        if (mode == null || saving) const LinearProgressIndicator(),
        LinuxGroup(
          children: [
            LinuxChoice<String>(
              value: 'privileged',
              groupValue: mode ?? '',
              enabled: !saving && mode != null,
              onChanged: _choose,
              icon: Icons.admin_panel_settings_rounded,
              title: t('完全特権（従来方式）', 'Full privilege (existing mode)'),
              subtitle: t(
                '既定の方式。これまでの Linux 環境をそのまま使用します。',
                'Default. Uses your existing Linux environment.',
              ),
            ),
            LinuxChoice<String>(
              value: 'android',
              groupValue: mode ?? '',
              enabled: !saving && mode != null,
              onChanged: _choose,
              icon: Icons.security_rounded,
              title: t('Android（試験的）', 'Android (experimental)'),
              subtitle: t(
                '通常のアプリ権限で動かします。',
                'Runs with ordinary app permissions.',
              ),
            ),
          ],
        ),
      ],
    ),
  );
}
