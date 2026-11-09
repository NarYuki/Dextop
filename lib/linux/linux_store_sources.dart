import '../l10n/inline_text.dart';

import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_expressive.dart';
import 'linux_widgets.dart';

/// Trust is explicit and separate from preparing tools in each container.
class LinuxStoreSourcesPanel extends StatefulWidget {
  const LinuxStoreSourcesPanel({
    super.key,
    required this.containers,
    required this.flatpakCompatibility,
    required this.onFlatpakCompatibility,
  });
  final List<Map<Object?, Object?>> containers;
  final bool flatpakCompatibility;
  final ValueChanged<bool> onFlatpakCompatibility;

  @override
  State<LinuxStoreSourcesPanel> createState() => _LinuxStoreSourcesPanelState();
}

class _LinuxStoreSourcesPanelState extends State<LinuxStoreSourcesPanel> {
  static const channel = MethodChannel('app.freedextop/linux');
  Map<String, bool>? sources;

  /// Per container: which sources have their tools installed.
  final ready = <String, Map<String, bool>>{};
  final running = <String, String>{};
  Timer? timer;
  String? error;
  bool saving = false;
  bool polling = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final result = await channel.invokeMapMethod<String, bool>(
        'storeSources',
      );
      if (mounted) setState(() => sources = result ?? {'apt': true});
      await _poll();
      await _checkReady();
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    }
  }

  Future<void> _checkReady() async {
    for (final container in _aptContainers) {
      final id = container['id'] as String;
      try {
        final result = await channel.invokeMapMethod<String, Object?>(
          'storeSourceReady',
          {'id': id},
        );
        if (mounted && result != null) {
          setState(
            () => ready[id] = {
              for (final e in result.entries) e.key: e.value == true,
            },
          );
        }
      } catch (_) {}
    }
  }

  List<Map<Object?, Object?>> get _aptContainers => widget.containers
      .where((c) => const ['debian', 'ubuntu'].contains(c['distro']))
      .toList();

  Future<void> _set(String source, bool value) async {
    setState(() => saving = true);
    try {
      final result = await channel.invokeMapMethod<String, bool>(
        'setStoreSource',
        {'source': source, 'enabled': value},
      );
      if (mounted) {
        setState(() {
          sources = result;
          error = null;
        });
      }
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> _prepare(String id, String source) async {
    try {
      final ok = await channel.invokeMethod<bool>('storeSourcePrepare', {
        'id': id,
        'source': source,
      });
      if (ok != true) {
        throw StateError(currentUiText('uiPreparationIsAlreadyRunning'));
      }
      if (!mounted) return;
      setState(
        () => running['$id/$source'] = linuxText(context, 'uiPreparing'),
      );
      timer ??= Timer.periodic(const Duration(seconds: 2), (_) => _poll());
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    }
  }

  Future<void> _poll() async {
    if (polling) return;
    polling = true;
    try {
      for (final container in widget.containers) {
        final id = container['id'] as String;
        final jobs =
            await channel.invokeListMethod<Object?>('storeSourcePrepareJobs', {
              'id': id,
            }) ??
            const [];
        if (!mounted) return;
        for (final raw in jobs) {
          final job = raw as Map;
          final name = (job['packages'] as List).first as String;
          final source = name.split(':').first;
          final key = '$id/$source';
          final state = job['state'];
          setState(() {
            if (state == 'running' || state == 'queued') {
              running[key] = (job['line'] as String?)?.isNotEmpty == true
                  ? job['line'] as String
                  : '$state…';
            } else {
              running.remove(key);
              error = state == 'done'
                  ? null
                  : job['output'] as String? ??
                        currentUiText('uiPreparationFailed');
              if (state == 'done') unawaited(_checkReady());
            }
          });
        }
      }
      if (running.isNotEmpty) {
        timer ??= Timer.periodic(const Duration(seconds: 2), (_) => _poll());
      }
      if (running.isEmpty) {
        timer?.cancel();
        timer = null;
      }
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      polling = false;
    }
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  Future<void> _details(String title, String text) => M3EBottomSheet.show<void>(
    context,
    builder: (sheet) => SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(title, style: Theme.of(sheet).textTheme.titleLarge),
          const SizedBox(height: 16),
          SelectableText(text, style: Theme.of(sheet).textTheme.bodyMedium),
        ],
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    String t(String key) => linuxText(context, key);
    final containers = _aptContainers;

    /// One row per Debian/Ubuntu environment: whether [source] is set up there.
    List<Widget> setupRows(String source) => [
      for (final container in containers)
        () {
          final id = container['id'] as String;
          final key = '$id/$source';
          final busy = running.containsKey(key);
          final done = ready[id]?[source] == true;
          return M3EListItem(
            supportingMaxLines: null,
            headline: container['name'] as String? ?? id,
            supportingText: busy
                ? running[key]
                : done
                ? t('uiReadyTapToUpdate')
                : t('uiNotSetUpTapToSetUp'),
            leading: Icon(
              done ? M3EIcons.check_circle_rounded : M3EIcons.download_rounded,
            ),
            trailing: busy
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: M3EProgressIndicator.circularWavy(),
                  )
                : const Icon(M3EIcons.chevron_right_rounded),
            onTap: busy
                ? () => _details(source, running[key]!)
                : () => _prepare(id, source),
          );
        }(),
    ];

    Widget note(String text) => Padding(
      padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
      child: Text(text, style: Theme.of(context).textTheme.bodySmall),
    );

    LinuxToggle toggle(
      String source,
      IconData icon,
      String title,
      String subtitle,
    ) => LinuxToggle(
      value: sources![source] == true,
      onChanged: saving ? null : (value) => _set(source, value),
      icon: icon,
      title: title,
      subtitle: subtitle,
    );

    if (sources == null) {
      return const Padding(
        padding: EdgeInsets.only(top: 16),
        child: M3EProgressIndicator.linearWavy(),
      );
    }
    final debGet = sources!['deb-get'] == true;
    final flathub = sources!['flathub'] == true;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LinuxSection(t('uiSources')),
        LinuxGroup(
          children: [
            toggle(
              'apt',
              M3EIcons.inventory_2_rounded,
              'Debian / Ubuntu APT',
              t('uiOfficialDistributionPackagesNoSetupNeeded'),
            ),
            toggle(
              'deb-get',
              M3EIcons.download_rounded,
              'deb-get',
              t('uiDEBPackagesFromAppDevelopers'),
            ),
            toggle(
              'flathub',
              M3EIcons.apps_rounded,
              'Flathub',
              t('uiFlatpakApplications'),
            ),
          ],
        ),
        note(t('uiDebGetAndFlathubNeedTheirToolsSetUpInEachLinuxEnvironment')),
        if (debGet && containers.isNotEmpty) ...[
          LinuxSection(t('uiDebGetSetup')),
          LinuxGroup(children: setupRows('deb-get')),
        ],
        if (flathub && containers.isNotEmpty) ...[
          LinuxSection(t('uiFlathubSetup')),
          LinuxGroup(children: setupRows('flathub')),
        ],
        if (flathub) ...[
          LinuxSection(t('uiRunningFlatpakApps')),
          LinuxGroup(
            children: [
              LinuxToggle(
                value: widget.flatpakCompatibility,
                onChanged: widget.onFlatpakCompatibility,
                icon: M3EIcons.science_rounded,
                title: t('uiCompatibilityExecutionExperimental'),
                subtitle: t(
                  'uiRunsAppsThroughProotWithoutNamespacesNoSandboxSoftwareRendering',
                ),
              ),
            ],
          ),
        ],
        if (error != null) ...[
          const SizedBox(height: 16),
          LinuxGroup(
            children: [
              M3EListItem(
                supportingMaxLines: null,
                headline: t('uiSetupFailed'),
                supportingText: t('uiTapToViewDetails'),
                leading: const Icon(M3EIcons.error_outline_rounded),
                trailing: const Icon(M3EIcons.chevron_right_rounded),
                onTap: () => _details(t('uiStore'), error!),
              ),
            ],
          ),
        ],
        LinuxSection(t('uiMore')),
        LinuxGroup(
          children: [
            M3EListItem(
              headline: t('uiAboutSources'),
              leading: const Icon(M3EIcons.info_outline_rounded),
              trailing: const Icon(M3EIcons.chevron_right_rounded),
              onTap: () => _details(
                t('uiAboutSources'),
                t(
                  'uiEnabledSourcesAppearInTheStoreForDebianAndUbuntuAPTListsOfficialDistributionPackagesDebGetAndFlathub',
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}
