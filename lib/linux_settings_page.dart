import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'l10n/app_localizations.dart';
import 'linux/linux_widgets.dart';

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
      if (mounted) {
        setState(() {
          settings = result;
          containers = list;
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
