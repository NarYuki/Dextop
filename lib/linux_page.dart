import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/penguin_icon.dart';
import 'package:free_dextop/linux_setup_page.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Linux destination: enable the feature and manage containers.
///
/// Containers live in the shell user's /data/local/tmp and are managed by a
/// Shizuku/Stellar UserService over the 'app.freedextop/linux' channel. The
/// feature is available only while Plasma v2 is the selected window manager.
class LinuxPage extends StatefulWidget {
  const LinuxPage({
    super.key,
    required this.onSwitchToPlasmaV2,
    this.onSetupPrivilege,
  });

  /// Selects Plasma v2 as the window manager (owned by the home screen).
  final Future<void> Function() onSwitchToPlasmaV2;
  final Future<void> Function()? onSetupPrivilege;

  @override
  State<LinuxPage> createState() => _LinuxPageState();
}

class _LinuxContainer {
  _LinuxContainer(Map<Object?, Object?> json)
    : id = json['id'] as String,
      name = json['name'] as String? ?? '',
      distro = json['distro'] as String? ?? '',
      state = json['state'] as String? ?? 'failed',
      runtimeState = json['runtimeState'] as String? ?? 'stopped',
      runtimeError = json['runtimeError'] as String?,
      bytes = (json['bytes'] as num?)?.toInt() ?? -1,
      error = json['error'] as String?,
      setupOptions =
          (json['setupOptions'] as Map?)?.cast<Object?, Object?>() ?? const {},
      progress = json['phase'] is String
          ? _Progress(
              json['phase'] as String,
              (json['percent'] as num?)?.toInt() ?? -1,
              json['message'] as String? ?? '',
            )
          : null;

  final String id;
  final String name;
  final String distro;
  final String state;
  final String runtimeState;
  final String? runtimeError;
  final int bytes;
  final String? error;
  final Map<Object?, Object?> setupOptions;

  /// The install phase reported by the service when the list was read.
  final _Progress? progress;
}

class _Progress {
  const _Progress(this.phase, this.percent, this.message);
  final String phase;
  final int percent;
  final String message;
}

class _LinuxPageState extends State<LinuxPage> {
  static const _channel = MethodChannel('app.freedextop/linux');
  static const _enabledKey = 'linux_enabled';

  Timer? _runtimePoll;
  bool _refreshing = false;
  bool loading = true;
  bool enabled = false;
  Map<Object?, Object?> status = const {};
  List<_LinuxContainer> containers = const [];
  List<Map<Object?, Object?>> catalog = const [];
  final Map<String, _Progress> progress = {};
  final Map<String, List<String>> logs = {};
  String? error;
  final setupProgress = ValueNotifier<Map<String, LinuxSetupProgress>>({});

  bool get plasmaV2 => status['plasmaV2'] == true;
  bool get privilege => status['privilege'] == true;
  bool get supportedDevice => status['supportedDevice'] == true;
  bool get usable => plasmaV2 && privilege && supportedDevice;

  @override
  void initState() {
    super.initState();
    _channel.setMethodCallHandler(_onEvent);
    _load();
    _runtimePoll = Timer.periodic(
      const Duration(seconds: 2),
      (_) => _refreshContainers(),
    );
  }

  @override
  void dispose() {
    _channel.setMethodCallHandler(null);
    _runtimePoll?.cancel();
    setupProgress.dispose();
    super.dispose();
  }

  Future<void> _onEvent(MethodCall call) async {
    if (!mounted) return;
    final args = call.arguments;
    switch (call.method) {
      case 'progress':
        final map = args as Map<Object?, Object?>;
        final id = map['id'] as String;
        setState(() {
          final phase = map['phase'] as String;
          if (phase == 'done') {
            progress.remove(id);
          } else {
            progress[id] = _Progress(
              phase,
              (map['percent'] as num).toInt(),
              map['message'] as String? ?? '',
            );
          }
        });
      case 'log':
        final map = args as Map<Object?, Object?>;
        final lines = logs.putIfAbsent(map['id'] as String, () => []);
        lines.add(map['line'] as String);
        if (lines.length > 500) lines.removeRange(0, lines.length - 500);
      case 'changed':
        unawaited(_refreshContainers());
      case 'connection':
        unawaited(_load());
    }
    _publishSetupProgress();
  }

  void _publishSetupProgress() {
    if (!mounted) return;
    setupProgress.value = {
      for (final container in containers)
        container.id: LinuxSetupProgress(
          state: container.state,
          phase: (progress[container.id] ?? container.progress)?.phase ?? '',
          percent:
              (progress[container.id] ?? container.progress)?.percent ?? -1,
          detail:
              _phaseDetail(progress[container.id] ?? container.progress) ?? '',
          error: container.error,
          logs: List<String>.of(logs[container.id] ?? const []),
        ),
    };
  }

  Future<void> _load() async {
    final preferences = await SharedPreferences.getInstance();
    final next =
        await _channel.invokeMapMethod<Object?, Object?>('status') ?? const {};
    if (!mounted) return;
    setState(() {
      enabled = preferences.getBool(_enabledKey) ?? false;
      status = next;
      loading = false;
    });
    if (enabled && usable) {
      await _channel.invokeMethod('connect');
      await _refreshContainers();
    }
  }

  Future<void> _refreshContainers() async {
    if (!enabled || !usable || _refreshing) return;
    _refreshing = true;
    try {
      final list = await _channel.invokeListMethod<Object?>('list') ?? const [];
      final distros = catalog.isEmpty
          ? (await _channel.invokeListMethod<Object?>('catalog') ?? const [])
                .cast<Map<Object?, Object?>>()
          : catalog;
      if (!mounted) return;
      setState(() {
        containers = list
            .cast<Map<Object?, Object?>>()
            .map(_LinuxContainer.new)
            .toList();
        catalog = distros;
        error = null;
      });
      _publishSetupProgress();
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    } finally {
      _refreshing = false;
    }
  }

  Future<void> _setEnabled(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool(_enabledKey, value);
    setState(() => enabled = value);
    if (value) await _load();
  }

  String _distroName(String id) =>
      catalog.firstWhere(
            (d) => d['id'] == id,
            orElse: () => {'name': id},
          )['name']
          as String;

  String _size(int bytes) {
    if (bytes < 0) return '';
    const units = ['B', 'KB', 'MB', 'GB'];
    var value = bytes.toDouble();
    var unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
      value /= 1024;
      unit++;
    }
    return '${value.toStringAsFixed(unit == 0 ? 0 : 1)} ${units[unit]}';
  }

  /// Install phases in order; the step shown is the phase's position.
  static const _phases = [
    'prepare',
    'download',
    'verify',
    'extract',
    'configure',
    'refresh',
    'provision',
    'language',
    'input',
    'desktop',
    'graphics',
    'applications',
    'integration',
    'finish',
  ];

  String _phaseLabel(AppLocalizations l, String phase) =>
      phase == 'error' ? l.linuxStateFailed : linuxSetupPhaseLabel(l, phase);

  /// "Step 3 of 8 · Extracting", or the plain state before the first report.
  String _phaseTitle(AppLocalizations l, _Progress? current) {
    if (current == null) return l.linuxStateInstalling;
    final step = _phases.indexOf(current.phase);
    final label = _phaseLabel(l, current.phase);
    if (step < 0) return label;
    return '${l.linuxPhaseStep(step + 1, _phases.length)} · $label';
  }

  /// Detail under the progress bar: transferred size, package name, percentage.
  String? _phaseDetail(_Progress? current) {
    if (current == null) return null;
    final message = current.message;
    if (current.phase == 'download') {
      final parts = message.split('/');
      final received = int.tryParse(parts.first) ?? 0;
      final total = parts.length > 1 ? int.tryParse(parts[1]) ?? -1 : -1;
      return total > 0
          ? '${_size(received)} / ${_size(total)} (${current.percent}%)'
          : _size(received);
    }
    final percent = current.percent >= 0 ? ' (${current.percent}%)' : '';
    if (message.isEmpty) return percent.isEmpty ? null : percent.trim();
    return '$message$percent';
  }

  Future<void> _addContainer() => _openSetup();

  Future<void> _openSetup([_LinuxContainer? container]) async {
    if (catalog.isEmpty) await _refreshContainers();
    if (!mounted || catalog.isEmpty) return;
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => LinuxSetupPage(
          catalog: catalog,
          containerId: container?.id,
          containerName: container?.name,
          distroId: container?.distro,
          initialOptions: container?.setupOptions ?? const {},
          resume:
              container?.state == 'installing' ||
              container?.state == 'updating',
          progress: setupProgress,
          refresh: _refreshContainers,
          install: (name, distro, options, targetId) async {
            String id;
            if (targetId != null) {
              final accepted = await _channel.invokeMethod<bool>('setup', {
                'id': targetId,
                'options': options,
              });
              if (accepted != true) {
                throw StateError(
                  'Linux setup is already running or unavailable',
                );
              }
              id = targetId;
            } else {
              id = (await _channel.invokeMethod<String>('install', {
                'name': name,
                'distro': distro,
                'options': options,
              }))!;
            }
            await _refreshContainers();
            return id;
          },
        ),
      ),
    );
    await _refreshContainers();
  }

  Future<void> _resetSession(_LinuxContainer container, bool force) async {
    final l = AppLocalizations.of(context);
    final accepted = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(force ? l.linuxForceStop : l.linuxSessionReset),
        content: Text(l.linuxForceStopConfirm),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(l.linuxCancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(force ? l.linuxForceStop : l.linuxSessionReset),
          ),
        ],
      ),
    );
    if (accepted != true) return;
    await _channel.invokeMethod('resetSession', {
      'id': container.id,
      'force': force,
    });
    await _refreshContainers();
  }

  Future<void> _remove(_LinuxContainer container) async {
    final l = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(l.linuxDelete),
        content: Text(l.linuxDeleteConfirm(container.name)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(l.linuxCancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(l.linuxDelete),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    await _channel.invokeMethod('remove', {'id': container.id});
    await _refreshContainers();
  }

  Future<void> _runCommand(_LinuxContainer container) async {
    final l = AppLocalizations.of(context);
    final command = TextEditingController(
      text: 'uname -a && cat /etc/os-release | head -2',
    );
    final run = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(l.linuxRunCommand),
        content: TextField(controller: command, autofocus: true, maxLines: 3),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(l.linuxCancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(l.linuxRun),
          ),
        ],
      ),
    );
    if (run != true || !mounted) return;
    String output;
    try {
      final result = await _channel.invokeMapMethod<Object?, Object?>('run', {
        'id': container.id,
        'command': command.text,
        'timeout': 300,
      });
      output = 'exit ${result?['exitCode']}\n${result?['output'] ?? ''}';
    } on PlatformException catch (e) {
      output = e.message ?? '$e';
    }
    if (mounted) _showText(l.linuxRunCommand, output);
  }

  void _showText(String title, String text) {
    final l = AppLocalizations.of(context);
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: 560,
          child: SingleChildScrollView(
            child: SelectableText(
              text,
              style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(l.close),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    if (loading) return const Center(child: CircularProgressIndicator());
    return Scaffold(
      floatingActionButton: enabled && usable
          ? FloatingActionButton.extended(
              onPressed: _addContainer,
              icon: const Icon(Icons.add_rounded),
              label: Text(l.linuxAddContainer),
            )
          : null,
      body: SafeArea(
        bottom: false,
        child: RefreshIndicator(
          onRefresh: _load,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 24, 16, 96),
            children: [
              Row(
                children: [
                  PenguinIcon(
                    filled: true,
                    size: 32,
                    color: theme.colorScheme.primary,
                  ),
                  const SizedBox(width: 12),
                  Text(
                    l.nativePlasmaCategoryLinux,
                    style: theme.textTheme.headlineMedium,
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(l.linuxDescription, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 16),
              if (!supportedDevice)
                _notice(Icons.block_rounded, l.linuxUnsupportedDevice)
              else if (!plasmaV2)
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.space_dashboard_outlined),
                    title: Text(l.linuxRequiresPlasma),
                    trailing: FilledButton.tonal(
                      onPressed: () async {
                        await widget.onSwitchToPlasmaV2();
                        await _load();
                      },
                      child: Text(l.linuxSwitchToPlasma),
                    ),
                  ),
                )
              else if (!privilege)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(l.linuxRequiresPrivilege),
                        if (widget.onSetupPrivilege != null) ...[
                          const SizedBox(height: 12),
                          FilledButton.tonalIcon(
                            onPressed: () async {
                              await widget.onSetupPrivilege!();
                              if (mounted) await _load();
                            },
                            icon: const Icon(Icons.key_rounded),
                            label: Text(l.setupEmbeddedTitle),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              Card(
                child: SwitchListTile(
                  secondary: const PenguinIcon(),
                  title: Text(l.linuxEnable),
                  subtitle: Text(l.linuxEnableDescription),
                  value: enabled,
                  onChanged: usable ? _setEnabled : null,
                ),
              ),
              if (error != null) _notice(Icons.error_outline_rounded, error!),
              if (enabled && usable) ...[
                const SizedBox(height: 8),
                if (containers.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(
                      l.linuxNoContainers,
                      textAlign: TextAlign.center,
                    ),
                  ),
                for (final container in containers)
                  _containerCard(l, theme, container),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _notice(IconData icon, String text) => Card(
    child: ListTile(leading: Icon(icon), title: Text(text)),
  );

  Widget _containerCard(
    AppLocalizations l,
    ThemeData theme,
    _LinuxContainer container,
  ) {
    final current = progress[container.id] ?? container.progress;
    final installing =
        container.state == 'installing' || container.state == 'updating';
    final subtitle = [
      _distroName(container.distro),
      switch (container.state) {
        'installed' => l.linuxStateInstalled,
        'installing' || 'updating' => _phaseTitle(l, current),
        _ => l.linuxStateFailed,
      },
      if (!installing)
        switch (container.runtimeState) {
          'starting' => l.linuxRuntimeStarting,
          'running' => l.linuxRuntimeRunning,
          'failed' => l.linuxStateFailed,
          _ => l.linuxRuntimeStopped,
        },
      if (container.bytes >= 0 && !installing) _size(container.bytes),
    ].join(' · ');
    final detail = installing ? _phaseDetail(current) : null;
    return Card(
      child: Column(
        children: [
          ListTile(
            leading: const PenguinIcon(),
            title: Text(container.name),
            subtitle: Text(subtitle),
            trailing: PopupMenuButton<String>(
              onSelected: (action) async {
                try {
                  switch (action) {
                    case 'cancel':
                      await _channel.invokeMethod('cancel', {
                        'id': container.id,
                      });
                    case 'setup':
                      await _openSetup(container);
                    case 'repair':
                      await _channel.invokeMethod('repair', {
                        'id': container.id,
                      });
                    case 'reset':
                      await _resetSession(container, false);
                    case 'force':
                      await _resetSession(container, true);
                    case 'log':
                      final output = await _channel.invokeMethod<String>(
                        'runtimeLogs',
                        {'id': container.id},
                      );
                      if (mounted) {
                        _showText(
                          l.linuxShowLog,
                          output ?? (logs[container.id] ?? const []).join('\n'),
                        );
                      }
                    case 'run':
                      await _runCommand(container);
                    case 'desktop':
                      final opened = await _channel.invokeMethod<bool>(
                        'openDesktop',
                        {'id': container.id, 'name': container.name},
                      );
                      if (opened != true && mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(l.linuxStartPlasmaFirst)),
                        );
                      }
                    case 'delete':
                      await _remove(container);
                  }
                } catch (e) {
                  if (mounted) {
                    _showText(
                      l.linuxStateFailed,
                      e is PlatformException
                          ? e.message ?? e.toString()
                          : e.toString(),
                    );
                  }
                }
              },
              itemBuilder: (_) => [
                if (installing)
                  PopupMenuItem(value: 'cancel', child: Text(l.linuxCancel)),
                if (container.state == 'installed')
                  PopupMenuItem(
                    value: 'desktop',
                    child: Text(l.linuxOpenDesktop),
                  ),
                if (container.state == 'installed')
                  PopupMenuItem(value: 'run', child: Text(l.linuxRunCommand)),
                if (!installing) ...[
                  PopupMenuItem(
                    value: 'reset',
                    child: Text(l.linuxSessionReset),
                  ),
                  PopupMenuItem(value: 'force', child: Text(l.linuxForceStop)),
                ],
                if (!installing)
                  PopupMenuItem(
                    value: 'setup',
                    child: Text(l.linuxSetupCustomize),
                  ),
                if (!installing)
                  PopupMenuItem(value: 'repair', child: Text(l.linuxRepair)),
                PopupMenuItem(value: 'log', child: Text(l.linuxShowLog)),
                PopupMenuItem(value: 'delete', child: Text(l.linuxDelete)),
              ],
            ),
          ),
          if (installing)
            Align(
              alignment: Alignment.centerRight,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: TextButton.icon(
                  onPressed: () => _openSetup(container),
                  icon: const Icon(Icons.downloading_rounded),
                  label: Text(l.linuxSetupLog),
                ),
              ),
            ),
          if (!installing)
            Align(
              alignment: Alignment.centerRight,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: TextButton.icon(
                  onPressed: () => _openSetup(container),
                  icon: const Icon(Icons.tune_rounded),
                  label: Text(l.linuxSetupCustomize),
                ),
              ),
            ),
          if (installing)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  LinearProgressIndicator(
                    value: current != null && current.percent >= 0
                        ? current.percent / 100
                        : null,
                  ),
                  if (detail != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: Text(
                        detail,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall,
                      ),
                    ),
                ],
              ),
            ),
          if (!installing && container.runtimeState == 'starting')
            const Padding(
              padding: EdgeInsets.all(16),
              child: LinearProgressIndicator(),
            ),
          if (!installing && container.runtimeError != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: SelectableText(
                container.runtimeError!,
                style: TextStyle(color: theme.colorScheme.error),
              ),
            ),
          if (!installing && container.error != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: Text(
                container.error!,
                style: TextStyle(color: theme.colorScheme.error),
              ),
            ),
        ],
      ),
    );
  }
}
