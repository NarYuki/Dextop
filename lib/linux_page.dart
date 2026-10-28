import 'dart:async';

import 'package:flutter/services.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/linux/linux_apps.dart';
import 'package:free_dextop/linux/linux_expressive.dart';
import 'package:free_dextop/linux/linux_store_page.dart';
import 'package:free_dextop/linux/linux_widgets.dart';
import 'package:free_dextop/linux_settings_page.dart';
import 'package:free_dextop/linux_setup_page.dart';
import 'package:free_dextop/penguin_icon.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Linux destination: the containers' applications, the containers themselves,
/// and the Store.
///
/// Containers live in the shell user's /data/local/tmp and are managed by a
/// Shizuku/Stellar UserService over the 'app.freedextop/linux' channel. The
/// apps open in Plasma v2 when it runs, otherwise as Android windows.
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
  static const _tabKey = 'linux_tab';

  Timer? _runtimePoll;
  bool _refreshing = false;
  bool loading = true;
  bool enabled = false;
  Map<Object?, Object?> status = const {};
  List<_LinuxContainer> containers = const [];
  String defaultContainer = '';
  List<Map<Object?, Object?>> catalog = const [];
  final Map<String, _Progress> progress = {};
  final Map<String, List<String>> logs = {};
  String? error;
  final setupProgress = ValueNotifier<Map<String, LinuxSetupProgress>>({});

  /// 0: applications, 1: containers.
  int tab = 0;
  final apps = LinuxAppList()..track();
  String _appsSignature = '';

  bool get plasmaV2 => status['plasmaV2'] == true;
  bool get privilege => status['privilege'] == true;
  bool get supportedDevice => status['supportedDevice'] == true;

  /// Plasma v2 is optional: without it, Linux apps open as Android windows.
  bool get usable => privilege && supportedDevice;

  List<_LinuxContainer> get installedContainers =>
      containers.where((c) => c.state == 'installed').toList();

  /// The containers whose applications are listed: the default one, or all.
  List<_LinuxContainer> get appContainers {
    final installed = installedContainers;
    final preferred = installed.where((c) => c.id == defaultContainer).toList();
    return preferred.isNotEmpty ? preferred : installed;
  }

  String t(String ja, String en) => linuxText(context, ja, en);

  @override
  void initState() {
    super.initState();
    _channel.setMethodCallHandler(_onEvent);
    SharedPreferences.getInstance().then((p) {
      if (mounted) setState(() => tab = p.getInt(_tabKey) ?? 0);
    });
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
    apps.dispose();
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
      final settings =
          await _channel.invokeMapMethod<Object?, Object?>('settings') ??
          const {};
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
        defaultContainer = settings['defaultContainer'] as String? ?? '';
        error = null;
      });
      _publishSetupProgress();
      // The application list follows the installed containers.
      final signature = appContainers.map((c) => c.id).join(',');
      if (signature != _appsSignature) {
        _appsSignature = signature;
        unawaited(_loadApps());
      }
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message);
    } finally {
      _refreshing = false;
    }
  }

  Future<void> _loadApps() =>
      apps.loadAll([for (final c in appContainers) c.id]);

  Future<void> _reload() async {
    await _load();
    await _loadApps();
  }

  Future<void> _setEnabled(bool value) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool(_enabledKey, value);
    setState(() => enabled = value);
    if (value) await _load();
  }

  Future<void> _setTab(int value) async {
    setState(() => tab = value);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setInt(_tabKey, value);
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
      linuxRoute(
        LinuxSetupPage(
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

  Future<bool> _confirm(
    String title,
    String message,
    String action,
    IconData icon,
  ) async =>
      await M3EDialog.show<bool>(
        context,
        dialog: Builder(
          builder: (dialogContext) => M3EDialog(
            title: title,
            icon: Icon(icon),
            content: Text(message),
            actions: [
              M3EButton.text(
                onPressed: () => Navigator.pop(dialogContext, false),
                child: Text(AppLocalizations.of(context).linuxCancel),
              ),
              M3EButton.filled(
                onPressed: () => Navigator.pop(dialogContext, true),
                child: Text(action),
              ),
            ],
          ),
        ),
      ) ==
      true;

  Future<void> _resetSession(_LinuxContainer container, bool force) async {
    final l = AppLocalizations.of(context);
    final title = force ? l.linuxForceStop : l.linuxSessionReset;
    if (!await _confirm(
      title,
      l.linuxForceStopConfirm,
      title,
      M3EIcons.restart_alt_rounded,
    )) {
      return;
    }
    await _channel.invokeMethod('resetSession', {
      'id': container.id,
      'force': force,
    });
    await _refreshContainers();
  }

  Future<void> _remove(_LinuxContainer container) async {
    final l = AppLocalizations.of(context);
    if (!await _confirm(
      l.linuxDelete,
      l.linuxDeleteConfirm(container.name),
      l.linuxDelete,
      M3EIcons.delete_forever_rounded,
    )) {
      return;
    }
    await _channel.invokeMethod('remove', {'id': container.id});
    await _refreshContainers();
  }

  /// Opens an interactive shell of [container] in its own window.
  Future<void> _openTerminal(_LinuxContainer container) async {
    try {
      await _channel.invokeMethod('openTerminal', {
        'id': container.id,
        'name': container.name,
      });
    } on PlatformException catch (e) {
      if (mounted) M3ESnackbar.show(context, message: e.message ?? '$e');
    }
  }

  Future<void> _openStore() async {
    final installed = installedContainers
        .where((c) => const ['debian', 'ubuntu'].contains(c.distro))
        .toList();
    if (installed.isEmpty) {
      M3ESnackbar.show(
        context,
        message: t(
          'Store は Debian／Ubuntu のコンテナに対応しています',
          'Store supports Debian and Ubuntu containers',
        ),
      );
      return;
    }
    final stores = [
      for (final c in installed) StoreContainer(c.id, c.name, c.distro),
    ];
    final initial = stores.firstWhere(
      (c) => c.id == defaultContainer,
      orElse: () => stores.first,
    );
    await Navigator.of(context).push<void>(
      linuxRoute(LinuxStorePage(containers: stores, initial: initial)),
    );
    await _loadApps();
  }

  Future<void> _openSettings([_LinuxContainer? container]) async {
    await Navigator.of(context).push(
      linuxRoute<void>(
        LinuxSettingsPage(
          containerId: container?.id,
          containerName: container?.name,
        ),
      ),
    );
    await _refreshContainers();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    if (loading) {
      return const Center(
        child: M3ELoadingIndicator(
          variant: M3ELoadingIndicatorVariant.contained,
        ),
      );
    }
    final ready = enabled && usable;
    final wide = linuxWide(context);
    final tabs = M3ESegmentedButton<int>(
      segments: [
        M3ESegment(
          value: 0,
          label: t('アプリ', 'Apps'),
          icon: const Icon(M3EIcons.apps_rounded),
        ),
        M3ESegment(
          value: 1,
          label: t('コンテナ', 'Containers'),
          icon: const Icon(M3EIcons.dns_rounded),
        ),
      ],
      selected: {tab},
      onSelectionChanged: (value) => _setTab(value.first),
    );
    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      floatingActionButton: AnimatedSwitcher(
        duration: const Duration(milliseconds: 220),
        transitionBuilder: (child, animation) =>
            ScaleTransition(scale: animation, child: child),
        child: ready && tab == 1
            ? M3EExtendedFab(
                key: const ValueKey('add'),
                label: l.linuxAddContainer,
                icon: const Icon(M3EIcons.add_rounded),
                onPressed: _addContainer,
              )
            : const SizedBox.shrink(key: ValueKey('none')),
      ),
      body: SafeArea(
        bottom: false,
        // A desktop window has no status bar over it, only its own title bar.
        top: !wide,
        child: M3ERefreshIndicator.contained(
          elevation: 0,
          onRefresh: _reload,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverToBoxAdapter(
                child: wide
                    ? _wideHeader(l, theme, ready ? tabs : null)
                    : _header(l, theme),
              ),
              if (!ready) ..._setupNotices(l, theme),
              if (error != null)
                SliverToBoxAdapter(
                  child: _notice(M3EIcons.error_outline_rounded, error!),
                ),
              if (ready) ...[
                if (!wide)
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                      child: tabs,
                    ),
                  ),
                if (tab == 0)
                  ..._appsSlivers(theme)
                else
                  ..._containerSlivers(l, theme),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _header(AppLocalizations l, ThemeData theme) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 20, 12, 12),
    child: Row(
      children: [
        PenguinIcon(filled: true, size: 34, color: theme.colorScheme.primary),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            l.nativePlasmaCategoryLinux,
            style: theme.textTheme.headlineMedium?.copyWith(
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        if (enabled && usable) ...[
          M3EButton.tonal(
            icon: const Icon(M3EIcons.storefront_rounded),
            onPressed: _openStore,
            label: const Text('Store'),
          ),
          const SizedBox(width: 4),
          M3EIconButton(
            variant: M3EIconButtonVariant.standard,
            icon: const Icon(M3EIcons.tune_rounded),
            tooltip: l.linuxSettingsTitle,
            onPressed: () => _openSettings(),
          ),
        ],
      ],
    ),
  );

  /// The header of a tablet-sized window: one compact row with the title, the
  /// Apps/Containers switch and the actions, so the content starts near the top.
  Widget _wideHeader(AppLocalizations l, ThemeData theme, Widget? tabs) =>
      Padding(
        padding: const EdgeInsets.fromLTRB(24, 12, 16, 12),
        child: Row(
          children: [
            PenguinIcon(
              filled: true,
              size: 28,
              color: theme.colorScheme.primary,
            ),
            const SizedBox(width: 12),
            Text(
              l.nativePlasmaCategoryLinux,
              style: theme.textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(width: 24),
            if (tabs != null) SizedBox(width: 340, child: tabs),
            const Spacer(),
            if (enabled && usable) ...[
              M3EButton.tonal(
                icon: const Icon(M3EIcons.storefront_rounded),
                onPressed: _openStore,
                label: const Text('Store'),
              ),
              const SizedBox(width: 4),
              M3EIconButton(
                variant: M3EIconButtonVariant.standard,
                icon: const Icon(M3EIcons.tune_rounded),
                tooltip: l.linuxSettingsTitle,
                onPressed: () => _openSettings(),
              ),
            ],
          ],
        ),
      );

  List<Widget> _setupNotices(AppLocalizations l, ThemeData theme) => [
    SliverToBoxAdapter(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
        child: Text(l.linuxDescription, style: theme.textTheme.bodyMedium),
      ),
    ),
    if (!supportedDevice)
      SliverToBoxAdapter(
        child: _notice(M3EIcons.block_rounded, l.linuxUnsupportedDevice),
      )
    else if (!privilege)
      SliverToBoxAdapter(
        child: _card(
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l.linuxRequiresPrivilege),
                if (widget.onSetupPrivilege != null) ...[
                  const SizedBox(height: 12),
                  M3EButton.tonal(
                    icon: const Icon(M3EIcons.key_rounded),
                    onPressed: () async {
                      await widget.onSetupPrivilege!();
                      if (mounted) await _load();
                    },
                    label: Text(l.setupEmbeddedTitle),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    SliverToBoxAdapter(
      child: _card(
        M3EListItem(
          headline: l.linuxEnable,
          supportingText: l.linuxEnableDescription,
          leading: const PenguinIcon(),
          trailing: M3ESwitch(
            value: enabled,
            onChanged: usable ? _setEnabled : null,
          ),
        ),
      ),
    ),
  ];

  /// One list item on its own, as a single-row expressive list.
  Widget _card(Widget child) => Padding(
    padding: EdgeInsets.symmetric(
      horizontal: linuxWide(context) ? 24 : 16,
      vertical: 4,
    ),
    child: child is M3EListItem
        ? LinuxGroup(children: [child])
        : M3ECard(
            variant: M3ECardVariant.filled,
            padding: EdgeInsets.zero,
            child: child,
          ),
  );

  Widget _notice(IconData icon, String text) =>
      _card(M3EListItem(headline: text, leading: Icon(icon)));

  // region applications

  List<Widget> _appsSlivers(ThemeData theme) {
    if (installedContainers.isEmpty) {
      return [
        SliverFillRemaining(
          hasScrollBody: false,
          child: _emptyState(
            theme,
            M3EIcons.dns_rounded,
            t('まだコンテナがありません', 'No containers yet'),
            t(
              'コンテナを作成すると、ここにLinuxアプリが並びます。',
              'Create a container and its Linux apps appear here.',
            ),
            M3EButton.filled(
              icon: const Icon(M3EIcons.add_rounded),
              onPressed: () {
                _setTab(1);
                _addContainer();
              },
              label: Text(t('コンテナを作成', 'Create a container')),
            ),
          ),
        ),
      ];
    }
    return [
      SliverToBoxAdapter(
        child: ListenableBuilder(
          listenable: apps,
          builder: (context, _) => AnimatedSwitcher(
            duration: const Duration(milliseconds: 240),
            child: apps.loading && apps.displayedApps.isEmpty
                ? const Padding(
                    padding: EdgeInsets.all(48),
                    child: Center(child: M3ELoadingIndicator()),
                  )
                : apps.displayedApps.isEmpty
                ? _emptyState(
                    theme,
                    M3EIcons.storefront_rounded,
                    t('アプリがありません', 'No apps'),
                    t(
                      'Storeからアプリをインストールしましょう。',
                      'Install apps from the Store.',
                    ),
                    M3EButton.filled(
                      icon: const Icon(M3EIcons.storefront_rounded),
                      onPressed: _openStore,
                      label: Text(t('Storeを開く', 'Open the Store')),
                    ),
                  )
                : const SizedBox.shrink(),
          ),
        ),
      ),
      LinuxAppsGrid(
        list: apps,
        onReload: _loadApps,
        containerNames: {for (final c in appContainers) c.id: c.name},
      ),
    ];
  }

  Widget _emptyState(
    ThemeData theme,
    IconData icon,
    String title,
    String text,
    Widget action,
  ) => Padding(
    padding: const EdgeInsets.fromLTRB(32, 48, 32, 48),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        M3EShapeContainer(
          kind: M3EShapeKind.cookie9Sided,
          width: 112,
          height: 112,
          color: theme.colorScheme.primaryContainer,
          child: Icon(
            icon,
            size: 48,
            color: theme.colorScheme.onPrimaryContainer,
          ),
        ),
        const SizedBox(height: 20),
        Text(
          title,
          style: theme.textTheme.titleLarge,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          text,
          style: theme.textTheme.bodyMedium,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 20),
        action,
      ],
    ),
  );
  // endregion

  // region containers

  List<Widget> _containerSlivers(AppLocalizations l, ThemeData theme) {
    if (containers.isEmpty) {
      return [
        SliverFillRemaining(
          hasScrollBody: false,
          child: _emptyState(
            theme,
            M3EIcons.dns_rounded,
            l.linuxNoContainers,
            '',
            M3EButton.filled(
              icon: const Icon(M3EIcons.add_rounded),
              onPressed: _addContainer,
              label: Text(l.linuxAddContainer),
            ),
          ),
        ),
      ];
    }
    return [
      SliverPadding(
        padding: const EdgeInsets.only(bottom: 120),
        sliver: SliverList.list(
          children: [
            for (final container in containers)
              _containerCard(l, theme, container),
            _card(
              M3EListItem(
                headline: l.linuxEnable,
                supportingText: l.linuxEnableDescription,
                leading: const PenguinIcon(),
                trailing: M3ESwitch(value: enabled, onChanged: _setEnabled),
              ),
            ),
          ],
        ),
      ),
    ];
  }

  Widget _containerCard(
    AppLocalizations l,
    ThemeData theme,
    _LinuxContainer container,
  ) {
    final current = progress[container.id] ?? container.progress;
    final installing =
        container.state == 'installing' || container.state == 'updating';
    final running = container.runtimeState == 'running';
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
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: M3ECard(
        variant: M3ECardVariant.filled,
        padding: const EdgeInsets.fromLTRB(16, 16, 8, 16),
        onPressed: () => _containerMenu(l, container),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                PenguinIcon(
                  filled: running,
                  size: 40,
                  color: running
                      ? theme.colorScheme.primary
                      : theme.colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              container.name,
                              style: theme.textTheme.titleMedium,
                            ),
                          ),
                          if (container.id == defaultContainer) ...[
                            const SizedBox(width: 8),
                            M3EChip(
                              label: l.linuxDefaultBadge,
                              type: M3EChipType.assist,
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(subtitle, style: theme.textTheme.bodySmall),
                    ],
                  ),
                ),
                M3EIconButton(
                  variant: M3EIconButtonVariant.standard,
                  icon: const Icon(M3EIcons.more_vert_rounded),
                  onPressed: () => _containerMenu(l, container),
                ),
              ],
            ),
            if (installing) ...[
              const SizedBox(height: 16),
              M3EProgressIndicator.linearWavy(
                strokeWidth: 3,
                trackStrokeWidth: 3,
                value: current != null && current.percent >= 0
                    ? current.percent / 100
                    : null,
              ),
              if (detail != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    detail,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodySmall,
                  ),
                ),
              Align(
                alignment: Alignment.centerRight,
                child: M3EButton.text(
                  onPressed: () => _openSetup(container),
                  child: Text(l.linuxSetupLog),
                ),
              ),
            ],
            if (!installing && container.runtimeState == 'starting') ...[
              const SizedBox(height: 16),
              const M3EProgressIndicator.linearWavy(
                strokeWidth: 3,
                trackStrokeWidth: 3,
              ),
            ],
            if (!installing && container.runtimeError != null)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: SelectableText(
                  container.runtimeError!,
                  style: TextStyle(color: theme.colorScheme.error),
                ),
              ),
            if (!installing && container.error != null)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(
                  container.error!,
                  style: TextStyle(color: theme.colorScheme.error),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _containerMenu(
    AppLocalizations l,
    _LinuxContainer container,
  ) async {
    final installing =
        container.state == 'installing' || container.state == 'updating';
    final installed = container.state == 'installed';
    final action = await M3EBottomSheet.show<String>(
      context,
      builder: (sheet) {
        final items = <(String, String, IconData)>[
          if (installing) ('cancel', l.linuxCancel, M3EIcons.close_rounded),
          if (installed)
            ('desktop', l.linuxOpenDesktop, M3EIcons.desktop_windows_rounded),
          if (installed)
            (
              'terminal',
              t('ターミナルを開く', 'Open terminal'),
              M3EIcons.terminal_rounded,
            ),
          if (!installing)
            ('reset', l.linuxSessionReset, M3EIcons.restart_alt_rounded),
          if (!installing)
            ('force', l.linuxForceStop, M3EIcons.stop_circle_rounded),
          if (installed)
            ('settings', l.linuxContainerSettings, M3EIcons.tune_rounded),
          if (!installing)
            ('setup', l.linuxSetupCustomize, M3EIcons.settings_suggest_rounded),
          if (!installing)
            ('repair', l.linuxRepair, M3EIcons.home_repair_service_rounded),
          ('log', l.linuxShowLog, M3EIcons.description_rounded),
          ('delete', l.linuxDelete, M3EIcons.delete_forever_rounded),
        ];
        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(8, 0, 8, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: Text(
                  container.name,
                  style: Theme.of(sheet).textTheme.titleLarge,
                ),
              ),
              for (final item in items)
                LinuxMenuRow(
                  title: item.$2,
                  icon: item.$3,
                  onTap: () => Navigator.pop(sheet, item.$1),
                ),
            ],
          ),
        );
      },
    );
    if (action == null || !mounted) return;
    try {
      switch (action) {
        case 'cancel':
          await _channel.invokeMethod('cancel', {'id': container.id});
        case 'setup':
          await _openSetup(container);
        case 'settings':
          await _openSettings(container);
        case 'repair':
          await _channel.invokeMethod('repair', {'id': container.id});
        case 'reset':
          await _resetSession(container, false);
        case 'force':
          await _resetSession(container, true);
        case 'log':
          final output = await _channel.invokeMethod<String>('runtimeLogs', {
            'id': container.id,
          });
          if (mounted) {
            showStoreLog(
              context,
              l.linuxShowLog,
              output ?? (logs[container.id] ?? const []).join('\n'),
            );
          }
        case 'terminal':
          await _openTerminal(container);
        case 'desktop':
          final opened = await _channel.invokeMethod<bool>('openDesktop', {
            'id': container.id,
            'name': container.name,
          });
          if (opened != true && mounted) {
            M3ESnackbar.show(context, message: l.linuxStartPlasmaFirst);
          }
        case 'delete':
          await _remove(container);
      }
    } catch (e) {
      if (mounted) {
        showStoreLog(
          context,
          l.linuxStateFailed,
          e is PlatformException ? e.message ?? e.toString() : e.toString(),
        );
      }
    }
  }
  // endregion
}
