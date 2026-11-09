import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_privilege_warning.dart';
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

  /// Full privilege needs Dextop's setup; until then only Android can be chosen.
  bool privilegeReady = false;

  /// False in the Google Play build, which has no full privilege to choose.
  bool privilegeOffered = false;
  String t(String key) => linuxText(context, key);

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final value = await _channel
          .invokeMethod<String>('executionMode')
          .timeout(const Duration(seconds: 10));
      final ready = await linuxPrivilegeReady();
      final offered = await linuxPrivilegedModeOffered();
      if (mounted) {
        setState(() {
          mode = value ?? 'android';
          privilegeReady = ready;
          privilegeOffered = offered;
        });
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(
          () => error = e is PlatformException ? e.message : e.toString(),
        );
      }
    }
  }

  Future<void> _choose(String value) async {
    if (saving || mode == null || value == mode) return;
    if (value == 'privileged' && !await linuxPrivilegeReady()) return;
    if (!mounted) return;
    if (value == 'privileged' && !await showLinuxPrivilegedWarning(context)) {
      return;
    }
    if (!mounted) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.admin_panel_settings_rounded),
        title: Text(t('uiChangeLinuxExecutionMode')),
        content: Text(
          t(
            'uiRunningLinuxAppsWillCloseSaveYourWorkFirstEachModeKeepsItsOwnEnvironmentExistingDataIsNotDeletedOrOv',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(t('uiCancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(t('uiSwitch')),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => saving = true);
    try {
      final result = await _channel
          .invokeMethod<String>('setExecutionMode', {
            'mode': value,
            'stop': true,
          })
          .timeout(const Duration(seconds: 50));
      if (mounted) {
        setState(() {
          mode = result ?? value;
          error = null;
        });
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(
          () => error = e is PlatformException ? e.message : e.toString(),
        );
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: M3EAppBar.top(
      titleText: t('uiAdvancedLinuxSettings'),
      automaticallyImplyLeading: true,
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
      children: [
        LinuxSection(t('uiExecutionMode')),
        if (error != null)
          Padding(padding: const EdgeInsets.all(8), child: Text(error!)),
        if ((mode == null && error == null) || saving)
          const LinearProgressIndicator(),
        if (mode == null && error != null)
          M3EButton.outlined(
            onPressed: _load,
            label: Text(t('uiRetry9da540e4')),
          ),
        LinuxGroup(
          children: [
            LinuxChoice<String>(
              value: 'android',
              groupValue: mode ?? '',
              enabled: !saving && mode != null,
              onChanged: _choose,
              icon: Icons.security_rounded,
              title: t('uiAndroidExperimental'),
              subtitle: t('uiRunsWithOrdinaryAppPermissions'),
            ),
            if (privilegeOffered) LinuxChoice<String>(
              value: 'privileged',
              groupValue: mode ?? '',
              enabled: !saving && mode != null && privilegeReady,
              onChanged: _choose,
              icon: Icons.admin_panel_settings_rounded,
              title: t('uiFullPrivilegeExistingMode'),
              subtitle: [
                t('uiDefaultUsesYourExistingLinuxEnvironment'),
                if (!privilegeReady) linuxPrivilegeUnavailableText(context),
              ].join('\n'),
            ),
          ],
        ),
      ],
    ),
  );
}
