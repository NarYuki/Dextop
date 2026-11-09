import '../l10n/inline_text.dart';

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
      LinuxSection(linuxText(context, 'uiMacOSCompatibilityExperimental')),
      LinuxGroup(
        children: [
          M3EListItem(
            leading: const Icon(M3EIcons.info_outline_rounded),
            headline: linuxText(context, 'uiRunLocalAppBundles'),
            supportingMaxLines: null,
            supportingText: linuxText(
              context,
              'uiRequiresACompatibilityRuntimeSupportVariesByAppSwiftUIAndIntelAppsAreNotSupportedYet',
            ),
          ),
        ],
      ),
      if (containers.isEmpty)
        Padding(
          padding: const EdgeInsets.all(16),
          child: Text(linuxText(context, 'uiSetUpALinuxContainerFirst')),
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
  String t(String key) => linuxText(context, key);

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
        title: Text(t('uiRegisterAMacOSApp')),
        content: TextField(
          onChanged: (value) => path = value,
          autofocus: true,
          decoration: InputDecoration(
            labelText: t('uiAppPathInsideLinux'),
            hintText: localText(context, 'uiRootApplicationsExampleApp'),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(t('uiCancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, path.trim()),
            child: Text(t('uiRegister')),
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
        title: Text(t('uiRemoveRegistration')),
        content: Text(t('uiTheApplicationBundleAndSavedDataWillBeKept')),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(t('uiCancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(t('uiRemove5442faae')),
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
              headline: t('uiCompatibilityRuntime'),
              supportingMaxLines: null,
              supportingText: status == null
                  ? error == null
                        ? t('uiChecking')
                        : t('uiCouldNotCheckRuntimeStatus')
                  : present
                  ? t('uiComponentsInstalledAppCompatibilityIsUnverified')
                  : t('uiNotInstalledOrRequiredFilesAreMissing'),
              trailing: const Icon(M3EIcons.refresh_rounded),
              onTap: busy ? null : _load,
            ),
            if (!present && status != null)
              M3EListItem(
                leading: const Icon(M3EIcons.download_rounded),
                headline: t('uiInstallCompatibilityRuntime'),
                supportingMaxLines: null,
                supportingText: status?['bundleAvailable'] == true
                    ? t('uiVerifyAndInstallTheRuntimeBundledWithDextop')
                    : t('uiThisVersionDoesNotIncludeAnInstallableRuntime'),
                trailing: const Icon(M3EIcons.chevron_right_rounded),
                onTap: busy || status?['bundleAvailable'] != true
                    ? null
                    : () => _run('macosInstall', ''),
              ),
            M3EListItem(
              leading: const Icon(M3EIcons.add_rounded),
              headline: t('uiRegisterAnApplication'),
              supportingText: t('uiRegisteredAppsAppearInTheApplicationList'),
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
                    : t('uiApplicationBundleNotFound'),
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
