import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import '../l10n/inline_text.dart';
import 'linux_expressive.dart';
import 'linux_widgets.dart';

/// Linux settings → Resources, as a task manager: live performance graphs (CPU overall or per
/// core, memory, GPU, storage), Linux's processes in a sortable, searchable table, the hardware
/// Linux may reach, and the containers' and GPU drivers' details.
class LinuxResourcesPanel extends StatefulWidget {
  const LinuxResourcesPanel({super.key});

  @override
  State<LinuxResourcesPanel> createState() => _LinuxResourcesPanelState();
}

enum _Resource { cpu, memory, gpu, storage }

enum _Sort { name, cpu, memory, threads, pid }

class _LinuxResourcesPanelState extends State<LinuxResourcesPanel> {
  static const _channel = MethodChannel('app.freedextop/linux');

  /// Samples kept for the graphs: one a second for a minute.
  static const _historyLength = 60;
  Map<Object?, Object?>? status;
  List<Map<Object?, Object?>> containers = const [];
  String? error;
  Timer? poll;
  bool showAll = false;
  _Resource resource = _Resource.cpu;
  bool perCore = false;
  _Sort sort = _Sort.cpu;
  bool descending = true;
  final search = TextEditingController();
  bool loading = false;

  final cpuHistory = <double>[];
  final linuxCpuHistory = <double>[];
  final memoryHistory = <double>[];
  final gpuHistory = <double>[];
  final coreHistory = <int, List<double>>{};

  String t(String key) => linuxText(context, key);

  @override
  void initState() {
    super.initState();
    _load();
    // CPU use is measured between two reads.
    poll = Timer.periodic(const Duration(seconds: 1), (_) => _load());
    search.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    poll?.cancel();
    search.dispose();
    super.dispose();
  }

  static void _push(List<double> history, double value) {
    history.add(value);
    if (history.length > _historyLength) history.removeAt(0);
  }

  Future<void> _load() async {
    if (loading) return;
    loading = true;
    try {
      final next = await _channel.invokeMapMethod<Object?, Object?>(
        'resources',
      );
      final list = await _channel.invokeListMethod<Object?>('list') ?? const [];
      if (!mounted || next == null) return;
      setState(() {
        status = next;
        containers = list.cast<Map<Object?, Object?>>();
        error = null;
        final cores = _cores(next);
        final known = cores.where((c) => c.usage >= 0).toList();
        _push(
          cpuHistory,
          known.isEmpty
              ? -1
              : known.fold<double>(0, (s, c) => s + c.usage) / known.length,
        );
        final count = (next['cores'] as num?)?.toInt() ?? 1;
        _push(
          linuxCpuHistory,
          (((next['cpu'] as num?)?.toDouble() ?? 0) / count).clamp(0, 100),
        );
        final total = (next['memoryTotal'] as num?)?.toDouble() ?? 1;
        final available = (next['memoryAvailable'] as num?)?.toDouble() ?? 0;
        _push(memoryHistory, ((total - available) / total * 100).clamp(0, 100));
        _push(gpuHistory, ((next['gpuBusy'] as num?)?.toDouble() ?? -1));
        for (final c in cores) {
          _push(coreHistory.putIfAbsent(c.index, () => []), c.usage);
        }
      });
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    } finally {
      loading = false;
    }
  }

  List<_Core> _cores(Map<Object?, Object?> s) =>
      (s['cpuCores'] as List? ?? const [])
          .cast<Map<Object?, Object?>>()
          .map(
            (c) => _Core(
              (c['core'] as num?)?.toInt() ?? 0,
              (c['usage'] as num?)?.toDouble() ?? -1,
              (c['frequency'] as num?)?.toInt() ?? -1,
              (c['maxFrequency'] as num?)?.toInt() ?? -1,
              c['online'] != false,
            ),
          )
          .toList();

  String _bytes(num? value) {
    if (value == null || value < 0) return t('uiMeasuring');
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    var v = value.toDouble();
    var i = 0;
    while (v >= 1024 && i < units.length - 1) {
      v /= 1024;
      i++;
    }
    return '${v.toStringAsFixed(v >= 100 || i == 0 ? 0 : 1)} ${units[i]}';
  }

  String _clock(int khz) => khz <= 0
      ? '—'
      : khz >= 1000000
      ? '${(khz / 1000000).toStringAsFixed(2)} GHz'
      : '${(khz / 1000).toStringAsFixed(0)} MHz';

  String _percent(double value) =>
      value < 0 ? '—' : '${value.toStringAsFixed(0)}%';

  String _containerName(String id) =>
      containers.firstWhere(
            (c) => c['id'] == id,
            orElse: () => const {},
          )['name']
          as String? ??
      id;

  Future<void> _stop(Map<Object?, Object?> process) async {
    final ok = await M3EDialog.show<bool>(
      context,
      dialog: Builder(
        builder: (dialog) => M3EDialog(
          icon: const Icon(M3EIcons.stop_circle_rounded),
          title: inlineFormat(t('uiEnd0'), [process['name']]),
          content: Text(
            t('uiForceStopsThisProcessAndTheOnesItStartedUnsavedWorkIsLost'),
          ),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialog, false),
              child: Text(t('uiCancel')),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialog, true),
              child: Text(t('uiEnd')),
            ),
          ],
        ),
      ),
    );
    if (ok != true) return;
    try {
      await _channel.invokeMethod<bool>('stopProcess', {
        'pid': process['pid'],
        'start': process['start'],
      });
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    }
    await _load();
  }

  Widget _row(IconData icon, String title, String detail, {Widget? trailing}) =>
      M3EListItem(
        headline: title,
        supportingText: detail,
        supportingMaxLines: null,
        leading: Icon(icon),
        trailing: trailing,
      );

  String _yes(bool value) => value ? t('uiSupported') : t('uiNotSupported');

  @override
  Widget build(BuildContext context) {
    final s = status;
    if (s == null) {
      return error != null
          ? Padding(padding: const EdgeInsets.all(16), child: Text(error!))
          : const Padding(
              padding: EdgeInsets.only(top: 24),
              child: M3EProgressIndicator.linearWavy(),
            );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (error != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 12, 8, 0),
            child: Text(
              error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        // One page, as a task manager: what the device does, what Linux runs, then the rest.
        LinuxSection(t('uiPerformance')),
        _performance(s),
        LinuxSection(t('uiProcesses')),
        _processes(s),
        LinuxSection(t('uiHardware')),
        _hardware(s),
        _details(s),
      ],
    );
  }

  // region performance
  Widget _performance(Map<Object?, Object?> s) {
    final scheme = Theme.of(context).colorScheme;
    final cores = _cores(s);
    final memory = (s['memoryTotal'] as num?)?.toInt() ?? 0;
    final available = (s['memoryAvailable'] as num?)?.toInt() ?? 0;
    final linuxMemory = (s['memory'] as num?)?.toInt() ?? 0;
    final gpuBusy = (s['gpuBusy'] as num?)?.toInt() ?? -1;
    final storageFree = (s['storageFree'] as num?)?.toInt() ?? -1;
    final storageTotal = (s['storageTotal'] as num?)?.toInt() ?? -1;
    final cpuNow = cpuHistory.isEmpty ? -1.0 : cpuHistory.last;
    final storageUsed = storageTotal > 0
        ? (1 - storageFree / storageTotal) * 100
        : -1.0;
    final tiles = <(_Resource, IconData, String, String, List<double>, Color)>[
      (
        _Resource.cpu,
        M3EIcons.memory_rounded,
        'CPU',
        '${_percent(cpuNow)} · ${_clock(cores.isEmpty ? -1 : cores.map((c) => c.frequency).reduce(math.max))}',
        cpuHistory,
        scheme.primary,
      ),
      (
        _Resource.memory,
        M3EIcons.storage_rounded,
        t('uiMemory'),
        '${_bytes(memory - available)} / ${_bytes(memory)}',
        memoryHistory,
        scheme.tertiary,
      ),
      (
        _Resource.gpu,
        M3EIcons.developer_board_rounded,
        'GPU',
        gpuBusy < 0 ? t('uiNotReported') : '$gpuBusy%',
        gpuHistory,
        scheme.secondary,
      ),
      (
        _Resource.storage,
        M3EIcons.sd_storage_rounded,
        t('uiStorage'),
        storageTotal > 0
            ? '${_bytes(storageTotal - storageFree)} / ${_bytes(storageTotal)}'
            : '—',
        [if (storageUsed >= 0) ...List.filled(2, storageUsed)],
        scheme.error,
      ),
    ];
    final list = Column(
      children: [
        for (final tile in tiles)
          _ResourceTile(
            icon: tile.$2,
            title: tile.$3,
            detail: tile.$4,
            history: tile.$5,
            color: tile.$6,
            selected: resource == tile.$1,
            onTap: () => setState(() => resource = tile.$1),
          ),
      ],
    );
    final detail = switch (resource) {
      _Resource.cpu => _cpuDetail(s, cores),
      _Resource.memory => _detailCard(
        title: t('uiMemory'),
        subtitle: _bytes(memory),
        history: memoryHistory,
        color: scheme.tertiary,
        facts: [
          (t('uiInUse'), _bytes(memory - available)),
          (t('uiAvailable'), _bytes(available)),
          (t('uiLinuxProcesses'), _bytes(linuxMemory)),
          (
            t('uiSwap'),
            '${_bytes(((s['swapTotal'] as num?) ?? 0) - ((s['swapFree'] as num?) ?? 0))} / ${_bytes(s['swapTotal'] as num?)}',
          ),
        ],
      ),
      _Resource.gpu => _detailCard(
        title: t('uiGPU'),
        subtitle: (s['gpuModel'] as String? ?? '').isEmpty
            ? t('uiUnknown')
            : s['gpuModel'] as String,
        history: gpuHistory,
        color: scheme.secondary,
        facts: [
          (
            t('uiLoad'),
            gpuBusy < 0 ? t('uiNotReportedByThisDevice') : '$gpuBusy%',
          ),
          ('Turnip (Zink)', _yes(s['turnipSupported'] == true)),
          ('VirGL', _yes(s['virglAvailable'] == true)),
        ],
      ),
      _Resource.storage => _detailCard(
        title: t('uiStorage'),
        subtitle: _bytes(storageTotal),
        history: const [],
        color: scheme.error,
        meter: storageUsed >= 0 ? storageUsed / 100 : null,
        facts: [
          (t('uiFree'), _bytes(storageFree)),
          (
            t('uiLinuxEnvironments'),
            _bytes(
              (s['containers'] as List? ?? const [])
                  .cast<Map<Object?, Object?>>()
                  .fold<int>(
                    0,
                    (sum, c) =>
                        sum +
                        ((c['size'] as num?)?.toInt() ?? 0).clamp(0, 1 << 62),
                  ),
            ),
          ),
        ],
      ),
    };
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth >= 900) {
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(width: 280, child: list),
              const SizedBox(width: 12),
              Expanded(child: detail),
            ],
          );
        }
        if (constraints.maxWidth >= 560) {
          // Two tiles a row above the graph.
          final width = (constraints.maxWidth - 8) / 2;
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Wrap(
                spacing: 8,
                children: [
                  for (final tile in list.children)
                    SizedBox(width: width, child: tile),
                ],
              ),
              const SizedBox(height: 6),
              detail,
            ],
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [list, const SizedBox(height: 6), detail],
        );
      },
    );
  }

  Widget _cpuDetail(Map<Object?, Object?> s, List<_Core> cores) {
    final scheme = Theme.of(context).colorScheme;
    final theme = Theme.of(context);
    final linux = (s['cpu'] as num?)?.toDouble() ?? 0;
    final count = (s['cores'] as num?)?.toInt() ?? cores.length;
    final hidden = cores.isNotEmpty && cores.every((c) => c.usage < 0);
    final header = Wrap(
      alignment: WrapAlignment.spaceBetween,
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: 12,
      runSpacing: 8,
      children: [
        ConstrainedBox(
          constraints: const BoxConstraints(minWidth: 160),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                localText(context, 'uiCPU'),
                style: theme.textTheme.headlineSmall,
              ),
              Text(
                (s['soc'] as String? ?? '').isEmpty
                    ? inlineFormat(t('ui0Cores'), [count])
                    : '${s['soc']} · ${inlineFormat(t('ui0Cores'), [count])}',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        M3ESegmentedButton<bool>(
          segments: [
            M3ESegment(value: false, label: t('uiOverall')),
            M3ESegment(value: true, label: t('uiPerCore')),
          ],
          selected: {perCore},
          onSelectionChanged: (value) => setState(() => perCore = value.first),
        ),
      ],
    );
    final graph = perCore
        ? LayoutBuilder(
            builder: (context, constraints) {
              // As many columns of at least 120 px as fit, up to 4.
              final columns = (constraints.maxWidth / 120).floor().clamp(
                1,
                math.min(4, math.max(1, cores.length)),
              );
              final width =
                  (constraints.maxWidth - (columns - 1) * 8) / columns;
              return Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final core in cores)
                    SizedBox(
                      width: width,
                      child: _CoreTile(
                        title: inlineFormat(t('uiCore0'), [core.index]),
                        usage: core.online
                            ? _percent(core.usage)
                            : t('uiOffline'),
                        clock: core.online ? _clock(core.frequency) : '',
                        history: coreHistory[core.index] ?? const [],
                        color: scheme.primary,
                      ),
                    ),
                ],
              );
            },
          )
        : _Graph(
            history: cpuHistory,
            color: scheme.primary,
            second: linuxCpuHistory,
            secondColor: scheme.tertiary,
            height: 180,
          );
    return M3ECard(
      variant: M3ECardVariant.filled,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          header,
          const SizedBox(height: 12),
          if (hidden)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(
                t(
                  'uiAndroidHidesTheOverallCPULoadInThisExecutionModeClocksAndLinuxSOwnLoadAreShown',
                ),
                style: theme.textTheme.bodySmall,
              ),
            ),
          graph,
          if (!perCore) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 16,
              children: [
                _Legend(color: scheme.primary, label: t('uiWholeDevice')),
                _Legend(
                  color: scheme.tertiary,
                  label: localText(context, 'uiLinux'),
                ),
              ],
            ),
          ],
          const SizedBox(height: 12),
          _Facts([
            (t('uiLoad'), _percent(cpuHistory.isEmpty ? -1 : cpuHistory.last)),
            (
              t('uiLinuxLoad'),
              '${(linux / math.max(1, count)).toStringAsFixed(0)}%',
            ),
            (
              t('uiClock'),
              _clock(
                cores.isEmpty
                    ? -1
                    : cores.map((c) => c.frequency).reduce(math.max),
              ),
            ),
            (
              t('uiMaximumClock'),
              _clock(
                cores.isEmpty
                    ? -1
                    : cores.map((c) => c.maxFrequency).reduce(math.max),
              ),
            ),
            (
              t('uiProcesses'),
              '${(s['processes'] as List? ?? const []).length}',
            ),
            (
              t('uiThreads'),
              '${(s['processes'] as List? ?? const []).cast<Map<Object?, Object?>>().fold<int>(0, (sum, p) => sum + ((p['threads'] as num?)?.toInt() ?? 0))}',
            ),
          ]),
        ],
      ),
    );
  }

  Widget _detailCard({
    required String title,
    required String subtitle,
    required List<double> history,
    required Color color,
    required List<(String, String)> facts,
    double? meter,
  }) {
    final theme = Theme.of(context);
    return M3ECard(
      variant: M3ECardVariant.filled,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(title, style: theme.textTheme.headlineSmall),
          Text(
            subtitle,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 12),
          if (meter != null)
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: LinearProgressIndicator(
                value: meter.clamp(0, 1).toDouble(),
                minHeight: 24,
                color: color,
              ),
            )
          else
            _Graph(history: history, color: color, height: 180),
          const SizedBox(height: 12),
          _Facts(facts),
        ],
      ),
    );
  }
  // endregion

  // region processes
  Widget _processes(Map<Object?, Object?> s) {
    final theme = Theme.of(context);
    final query = search.text.trim().toLowerCase();
    final processes =
        (s['processes'] as List? ?? const [])
            .cast<Map<Object?, Object?>>()
            .where((p) => showAll || p['plumbing'] != true)
            .where(
              (p) =>
                  query.isEmpty ||
                  (p['name'] as String? ?? '').toLowerCase().contains(query) ||
                  (p['command'] as String? ?? '').toLowerCase().contains(
                    query,
                  ) ||
                  '${p['pid']}' == query,
            )
            .toList()
          ..sort((a, b) {
            final order = switch (sort) {
              _Sort.name =>
                (a['name'] as String? ?? '').toLowerCase().compareTo(
                  (b['name'] as String? ?? '').toLowerCase(),
                ),
              _Sort.cpu => ((a['cpu'] as num?) ?? 0).compareTo(
                (b['cpu'] as num?) ?? 0,
              ),
              _Sort.memory => ((a['memory'] as num?) ?? 0).compareTo(
                (b['memory'] as num?) ?? 0,
              ),
              _Sort.threads => ((a['threads'] as num?) ?? 0).compareTo(
                (b['threads'] as num?) ?? 0,
              ),
              _Sort.pid => ((a['pid'] as num?) ?? 0).compareTo(
                (b['pid'] as num?) ?? 0,
              ),
            };
            return descending ? -order : order;
          });
    final count = (s['cores'] as num?)?.toInt() ?? 1;
    Widget header(String label, _Sort key, {int flex = 1, bool end = true}) =>
        Expanded(
          flex: flex,
          child: InkWell(
            borderRadius: BorderRadius.circular(8),
            onTap: () => setState(() {
              if (sort == key) {
                descending = !descending;
              } else {
                sort = key;
                descending = key != _Sort.name;
              }
            }),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
              child: Row(
                mainAxisAlignment: end
                    ? MainAxisAlignment.end
                    : MainAxisAlignment.start,
                children: [
                  Flexible(
                    child: Text(
                      label,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.labelLarge?.copyWith(
                        color: sort == key ? theme.colorScheme.primary : null,
                      ),
                    ),
                  ),
                  if (sort == key)
                    Icon(
                      descending
                          ? M3EIcons.arrow_downward_rounded
                          : M3EIcons.arrow_upward_rounded,
                      size: 16,
                      color: theme.colorScheme.primary,
                    ),
                ],
              ),
            ),
          ),
        );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: search,
          decoration: InputDecoration(
            prefixIcon: const Icon(M3EIcons.search_rounded),
            hintText: t('uiSearchByNameCommandOrPID'),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(28)),
            isDense: true,
          ),
        ),
        const SizedBox(height: 8),
        LayoutBuilder(
          builder: (context, constraints) {
            const minimum = 640.0;
            final table = M3ECard(
              variant: M3ECardVariant.filled,
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              child: Column(
                children: [
                  Row(
                    children: [
                      header(t('uiName'), _Sort.name, flex: 4, end: false),
                      header('CPU', _Sort.cpu),
                      header(t('uiMemory'), _Sort.memory, flex: 2),
                      header(t('uiThreads'), _Sort.threads),
                      header('PID', _Sort.pid),
                      const SizedBox(width: 40),
                    ],
                  ),
                  const Divider(height: 1),
                  if (processes.isEmpty)
                    Padding(
                      padding: const EdgeInsets.all(24),
                      child: Text(
                        query.isEmpty
                            ? t('uiNoProcessesRunning')
                            : t('uiNoMatchingProcesses'),
                      ),
                    ),
                  for (final p in processes.take(200))
                    _ProcessRow(
                      name: p['name'] as String? ?? '?',
                      detail:
                          '${_containerName(p['container'] as String? ?? '')} · ${p['command'] ?? ''}',
                      cpu:
                          ((p['cpu'] as num?) ?? 0).toDouble() /
                          math.max(1, count),
                      memory: _bytes(p['memory'] as num?),
                      threads: '${p['threads'] ?? ''}',
                      pid: '${p['pid']}',
                      endTooltip: t('uiEnd'),
                      onEnd: () => _stop(p),
                    ),
                ],
              ),
            );
            if (constraints.maxWidth >= minimum) return table;
            return SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: SizedBox(width: minimum, child: table),
            );
          },
        ),
        const SizedBox(height: 8),
        LinuxGroup(
          children: [
            LinuxToggle(
              value: showAll,
              onChanged: (v) => setState(() => showAll = v),
              icon: M3EIcons.visibility_rounded,
              title: t('uiShowPRootToo'),
              subtitle: t('uiListsThePRootProcessesThatRunLinuxAsWell'),
            ),
          ],
        ),
      ],
    );
  }
  // endregion

  // region hardware and details
  Widget _hardware(Map<Object?, Object?> s) {
    final sharing = (s['sharing'] as List? ?? const [])
        .cast<Map<Object?, Object?>>();
    final usb = (s['usbDevices'] as List? ?? const [])
        .cast<Map<Object?, Object?>>();
    final controllers = (s['controllers'] as List? ?? const []).cast<String>();
    return LinuxGroup(
      children: [
        for (final container in sharing) ...[
          _row(
            M3EIcons.photo_camera_rounded,
            '${t('uiCamera')} · ${container['name']}',
            container['camera'] == true
                ? t('uiAvailableToLinux')
                : t('uiNotAllowed'),
          ),
          _row(
            M3EIcons.mic_rounded,
            '${t('uiMicrophone')} · ${container['name']}',
            container['microphone'] == true
                ? t('uiAvailableToLinux')
                : t('uiNotAllowed'),
          ),
          _row(
            M3EIcons.folder_rounded,
            '${t('uiStorage')} · ${container['name']}',
            container['wholeStorage'] == true
                ? t('uiTheWholeStorageUnderMediaStorage')
                : (container['folders'] as num? ?? 0) > 0
                ? inlineFormat(t('ui0PickedFolders'), [container['folders']])
                : t('uiNotAllowed'),
          ),
        ],
        _row(
          M3EIcons.sports_esports_rounded,
          t('uiControllers'),
          controllers.isEmpty ? t('uiNoneConnected') : controllers.join(', '),
        ),
        for (final d in usb)
          _row(
            d['kind'] == 'serial'
                ? M3EIcons.cable_rounded
                : d['kind'] == 'fido'
                ? M3EIcons.key_rounded
                : d['kind'] == 'storage'
                ? M3EIcons.storage_rounded
                : M3EIcons.usb_rounded,
            d['name'] as String? ?? 'USB',
            [
              d['ids'] as String? ?? '',
              if (d['kind'] == 'fido') t('uiSecurityKey'),
              if (d['kind'] == 'storage') t('uiUSBStorageDevSdX'),
              d['enabled'] == true && d['permission'] == true
                  ? t('uiAvailableToLinux')
                  : d['enabled'] == true
                  ? t('uiWaitingForAndroid')
                  : t('uiNotGivenToLinux'),
            ].join(' · '),
          ),
        if (usb.isEmpty)
          _row(M3EIcons.usb_rounded, t('uiUSBDevices'), t('uiNoneConnected')),
      ],
    );
  }

  Widget _details(Map<Object?, Object?> s) {
    final info = (s['containers'] as List? ?? const [])
        .cast<Map<Object?, Object?>>();
    final gpuModel = s['gpuModel'] as String? ?? '';
    final android = s['executionMode'] == 'android';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LinuxSection(t('uiContainers')),
        LinuxGroup(
          children: [
            for (final c in containers)
              () {
                final id = c['id'] as String? ?? '';
                final detail = info.firstWhere(
                  (i) => i['id'] == id,
                  orElse: () => const {},
                );
                final options = c['setupOptions'] as Map? ?? const {};
                return _row(
                  M3EIcons.inventory_2_rounded,
                  c['name'] as String? ?? id,
                  [
                    '${c['distro'] ?? '?'} · $id',
                    inlineFormat(t('uiState0'), [
                      c['runtimeState'] ?? c['state'] ?? '?',
                    ]),
                    inlineFormat(t('uiDisplay0'), [
                      options['displayBackend'] == 'wayland'
                          ? 'Wayland'
                          : 'Xvfb',
                    ]),
                    inlineFormat(t('uiRendering0'), [
                      c['gpuEffective'] ?? detail['gpuMode'] ?? '?',
                    ]),
                    inlineFormat(t('uiWindowManager0'), [
                      detail['windowManager'] ?? '?',
                    ]),
                    inlineFormat(t('uiSize0'), [
                      _bytes(detail['size'] as num?),
                    ]),
                  ].join('\n'),
                );
              }(),
          ],
        ),
        LinuxSection(t('uiDrivers')),
        LinuxGroup(
          children: [
            _row(
              M3EIcons.phone_android_rounded,
              s['device'] as String? ?? '',
              [
                s['android'] ?? '',
                if ((s['soc'] as String? ?? '').isNotEmpty) 'SoC: ${s['soc']}',
                'GPU: ${gpuModel.isEmpty ? t('uiUnknownNotAdreno') : gpuModel}',
                inlineFormat(t('uiExecutionMode0'), [
                  android ? 'Android' : t('uiFullPrivilege'),
                ]),
              ].join('\n'),
            ),
            _row(
              M3EIcons.bolt_rounded,
              'Turnip (Zink)',
              '${s['turnipPackage'] ?? ''} · ${_yes(s['turnipSupported'] == true)}',
            ),
            _row(
              M3EIcons.layers_rounded,
              'VirGL',
              '${s['virglVersion'] ?? ''} · ${_yes(s['virglAvailable'] == true)}',
            ),
            _row(
              M3EIcons.computer_rounded,
              t('uiSoftwareLlvmpipe'),
              [
                ...{
                  for (final c in info)
                    if ((c['mesa'] as String? ?? '').isNotEmpty)
                      'Mesa ${c['mesa']}',
                },
                _yes(true),
              ].join(' · '),
            ),
          ],
        ),
      ],
    );
  }

  // endregion
}

class _Core {
  const _Core(
    this.index,
    this.usage,
    this.frequency,
    this.maxFrequency,
    this.online,
  );

  final int index;

  /// Percent since the last read; -1 when Android hides it.
  final double usage;
  final int frequency;
  final int maxFrequency;
  final bool online;
}

/// One resource in the performance list: name, current value and a small live graph.
class _ResourceTile extends StatelessWidget {
  const _ResourceTile({
    required this.icon,
    required this.title,
    required this.detail,
    required this.history,
    required this.color,
    required this.selected,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String detail;
  final List<double> history;
  final Color color;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Material(
        color: selected
            ? scheme.secondaryContainer
            : scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                SizedBox(
                  width: 72,
                  height: 40,
                  child: _Graph(
                    history: history,
                    color: color,
                    height: 40,
                    grid: false,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(icon, size: 18, color: color),
                          const SizedBox(width: 6),
                          Text(title, style: theme.textTheme.titleSmall),
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(
                        detail,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// One core in the per-core grid.
class _CoreTile extends StatelessWidget {
  const _CoreTile({
    required this.title,
    required this.usage,
    required this.clock,
    required this.history,
    required this.color,
  });

  final String title;
  final String usage;
  final String clock;
  final List<double> history;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(child: Text(title, style: theme.textTheme.labelMedium)),
              Text(
                usage,
                style: theme.textTheme.labelMedium?.copyWith(color: color),
              ),
            ],
          ),
          const SizedBox(height: 4),
          _Graph(history: history, color: color, height: 56),
          const SizedBox(height: 2),
          Text(
            clock,
            style: theme.textTheme.labelSmall?.copyWith(
              color: scheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

/// A process in the table: name and command, CPU (of the whole device), memory, threads, PID.
class _ProcessRow extends StatelessWidget {
  const _ProcessRow({
    required this.name,
    required this.detail,
    required this.cpu,
    required this.memory,
    required this.threads,
    required this.pid,
    required this.endTooltip,
    required this.onEnd,
  });

  final String name;
  final String detail;
  final double cpu;
  final String memory;
  final String threads;
  final String pid;
  final String endTooltip;
  final VoidCallback onEnd;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    // Heavier processes stand out, as in a task manager.
    final heat = Color.lerp(
      Colors.transparent,
      scheme.primary.withValues(alpha: .18),
      (cpu / 50).clamp(0, 1),
    )!;
    final numbers = theme.textTheme.bodyMedium?.copyWith(
      fontFeatures: const [FontFeature.tabularFigures()],
    );
    return Container(
      color: heat,
      padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 4),
      child: Row(
        children: [
          Expanded(
            flex: 4,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium,
                ),
                Text(
                  detail,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: Text(
              '${cpu.toStringAsFixed(1)}%',
              textAlign: TextAlign.end,
              style: numbers,
            ),
          ),
          Expanded(
            flex: 2,
            child: Text(memory, textAlign: TextAlign.end, style: numbers),
          ),
          Expanded(
            child: Text(threads, textAlign: TextAlign.end, style: numbers),
          ),
          Expanded(
            child: Text(pid, textAlign: TextAlign.end, style: numbers),
          ),
          SizedBox(
            width: 40,
            child: M3EIconButton(
              variant: M3EIconButtonVariant.standard,
              icon: const Icon(M3EIcons.close_rounded),
              tooltip: endTooltip,
              onPressed: onEnd,
            ),
          ),
        ],
      ),
    );
  }
}

class _Legend extends StatelessWidget {
  const _Legend({required this.color, required this.label});

  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Container(
        width: 12,
        height: 12,
        decoration: BoxDecoration(
          color: color,
          borderRadius: BorderRadius.circular(3),
        ),
      ),
      const SizedBox(width: 6),
      Text(label, style: Theme.of(context).textTheme.labelMedium),
    ],
  );
}

/// Label and value pairs under a graph.
class _Facts extends StatelessWidget {
  const _Facts(this.facts);

  final List<(String, String)> facts;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Wrap(
      spacing: 24,
      runSpacing: 12,
      children: [
        for (final (label, value) in facts)
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                label,
                style: theme.textTheme.labelMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              Text(value, style: theme.textTheme.titleMedium),
            ],
          ),
      ],
    );
  }
}

/// A live graph of percentages (0–100) over the last minute; unknown samples (-1) leave gaps.
class _Graph extends StatelessWidget {
  const _Graph({
    required this.history,
    required this.color,
    required this.height,
    this.second,
    this.secondColor,
    this.grid = true,
  });

  final List<double> history;
  final Color color;
  final double height;
  final List<double>? second;
  final Color? secondColor;
  final bool grid;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SizedBox(
      height: height,
      child: CustomPaint(
        painter: _GraphPainter(
          history: history,
          color: color,
          second: second,
          secondColor: secondColor,
          gridColor: grid ? scheme.outlineVariant.withValues(alpha: .5) : null,
          background: scheme.surfaceContainerHighest.withValues(alpha: .4),
        ),
      ),
    );
  }
}

class _GraphPainter extends CustomPainter {
  _GraphPainter({
    required this.history,
    required this.color,
    required this.background,
    this.second,
    this.secondColor,
    this.gridColor,
  }) : _signature = Object.hashAll([...history, ...?second]);

  final List<double> history;
  final Color color;
  final Color background;
  final List<double>? second;
  final Color? secondColor;
  final Color? gridColor;
  final int _signature;

  static const _samples = _LinuxResourcesPanelState._historyLength;

  @override
  void paint(Canvas canvas, Size size) {
    final frame = RRect.fromRectAndRadius(
      Offset.zero & size,
      const Radius.circular(8),
    );
    canvas.save();
    canvas.clipRRect(frame);
    canvas.drawRect(Offset.zero & size, Paint()..color = background);
    if (gridColor != null) {
      final paint = Paint()
        ..color = gridColor!
        ..strokeWidth = 1;
      for (var i = 1; i < 4; i++) {
        final y = size.height * i / 4;
        canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
      }
      for (var i = 1; i < 6; i++) {
        final x = size.width * i / 6;
        canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
      }
    }
    _series(canvas, size, history, color, fill: true);
    if (second != null) {
      _series(canvas, size, second!, secondColor ?? color, fill: false);
    }
    canvas.restore();
  }

  void _series(
    Canvas canvas,
    Size size,
    List<double> values,
    Color color, {
    required bool fill,
  }) {
    if (values.isEmpty) return;
    final step = size.width / (_samples - 1);
    // The newest sample is at the right edge.
    final start = _samples - values.length;
    final line = Path();
    final area = Path();
    var open = false;
    var firstX = 0.0;
    var lastX = 0.0;
    void close() {
      if (!open) return;
      area
        ..lineTo(lastX, size.height)
        ..lineTo(firstX, size.height)
        ..close();
      open = false;
    }

    for (var i = 0; i < values.length; i++) {
      final value = values[i];
      final x = (start + i) * step;
      if (value < 0) {
        close();
        continue;
      }
      final y = size.height - (value.clamp(0, 100) / 100) * size.height;
      if (!open) {
        line.moveTo(x, y);
        area.moveTo(x, size.height);
        area.lineTo(x, y);
        firstX = x;
        open = true;
      } else {
        line.lineTo(x, y);
        area.lineTo(x, y);
      }
      lastX = x;
    }
    close();
    if (fill) {
      canvas.drawPath(area, Paint()..color = color.withValues(alpha: .22));
    }
    canvas.drawPath(
      line,
      Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(_GraphPainter old) =>
      old._signature != _signature ||
      old.color != color ||
      old.background != background;
}
