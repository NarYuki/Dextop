import '../l10n/inline_text.dart';

import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import '../linux_settings_page.dart';

import 'linux_apps.dart';
import 'linux_installations.dart';
import 'linux_expressive.dart';
import 'linux_store_catalog.dart';
import 'linux_store_cache.dart';
import 'linux_widgets.dart';
import 'windows_store.dart';

/// A container the Store can install into.
class StoreContainer {
  const StoreContainer(this.id, this.name, this.distro);

  final String id;
  final String name;
  final String distro;
}

/// A package job the Store started with apt.
class StoreJob {
  const StoreJob(
    this.action,
    this.line, [
    this.percent = -1,
    this.queued = false,
  ]);

  final String action;
  final String line;

  /// Waiting for the jobs before it in the queue.
  final bool queued;

  /// 0–100 from apt's progress reports, or -1 before the first one.
  final int percent;

  /// Progress for the indicators; 0 until apt reports, never indeterminate.
  double get value => percent < 0 ? 0 : percent.clamp(0, 100) / 100;
}

/// A package that can be updated, with its installed and new versions.
class StoreUpdate {
  const StoreUpdate(this.name, this.from, this.to);

  final String name;
  final String from;
  final String to;
}

/// A wavy progress circle that glides to new values instead of jumping.
class StoreProgressCircle extends StatelessWidget {
  const StoreProgressCircle({super.key, required this.value, this.size = 32});

  final double value;
  final double size;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: size,
    height: size,
    child: TweenAnimationBuilder<double>(
      tween: Tween(end: value),
      duration: const Duration(milliseconds: 500),
      curve: Curves.easeOutCubic,
      builder: (context, v, _) => M3EProgressIndicator.circularWavy(
        strokeWidth: 2.5,
        trackStrokeWidth: 2.5,
        // The default track matches the list rows' surface and disappears into it.
        trackColor: Theme.of(context).colorScheme.primary
            .withValues(alpha: .22),
        value: v,
      ),
    ),
  );
}

/// Catalog, installed packages and running jobs of one container, shared by the Store pages.
class LinuxStore extends ChangeNotifier {
  LinuxStore(this.containers, StoreContainer initial) : container = initial;

  static const _channel = MethodChannel('app.freedextop/linux');
  static final _catalogCache = StoreSnapshotCache();

  final List<StoreContainer> containers;
  StoreContainer container;
  StoreSource get source => StoreSource.forDistro(container.distro);

  List<StoreApp> apps = const [];
  Set<String> installed = const {};
  Set<String> upgradable = const {};
  List<StoreUpdate> updates = const [];
  final Map<String, StoreJob> jobs = {};

  /// Windows applications installed in the container (winget ids), and the names of
  /// Windows jobs (`win:<id>`) for their messages.
  Set<String> windowsInstalled = const {};

  /// Search options: also list every apt package, and Windows applications (winget).
  bool searchAllPackages = false;
  bool searchWindows = false;
  final Map<String, String> windowsNames = {};
  bool loading = true;
  Map<String, bool> sources = const {'apt': true};
  final List<String> sourceErrors = [];

  /// Enabled sources whose tools are not in this container yet, and their running setup lines.
  List<String> unprepared = const [];
  final Map<String, String> preparing = {};
  bool setupSkipped = false;
  Timer? _preparePoll;

  Future<void> prepareSource(String name) async {
    try {
      final ok = await _channel.invokeMethod<bool>('storeSourcePrepare', {
        'id': container.id,
        'source': name,
      });
      if (ok != true) return;
      preparing[name] = '';
      notifyListeners();
      _preparePoll ??= Timer.periodic(
        const Duration(seconds: 2),
        (_) => _pollPreparations(),
      );
    } on PlatformException catch (e) {
      sourceErrors.add('$name: ${e.message}');
      notifyListeners();
    }
  }

  Future<void> _pollPreparations() async {
    final jobs =
        await _channel.invokeListMethod<Object?>('storeSourcePrepareJobs', {
          'id': container.id,
        }) ??
        const [];
    var finished = false;
    for (final raw in jobs) {
      final job = raw as Map;
      final name = ((job['packages'] as List).first as String).split(':').first;
      final state = job['state'];
      if (state == 'running' || state == 'queued') {
        preparing[name] = job['line'] as String? ?? '';
      } else {
        preparing.remove(name);
        finished = true;
        if (state != 'done') {
          sourceErrors.add('$name: ${job['output'] ?? 'setup failed'}');
        }
      }
    }
    if (preparing.isEmpty) {
      _preparePoll?.cancel();
      _preparePoll = null;
    }
    notifyListeners();
    if (finished && preparing.isEmpty) await load(refresh: true);
  }

  bool get supported => const ['debian', 'ubuntu'].contains(container.distro);
  bool flatpakCompatibility = false;
  String? error;
  Timer? _poll;
  String _language = 'en';

  /// Desktop entries of the container, to open installed applications.
  final entries = LinuxAppList(trackInstallations: false);

  Future<void> load({bool refresh = false, String? language}) async {
    if (language != null) _language = language;
    loading = true;
    error = null;
    sourceErrors.clear();
    notifyListeners();
    try {
      if (!supported) {
        apps = const [];
        throw StateError(
          'Store supports Debian and Ubuntu only. Arch is not supported.',
        );
      }
      // The cached catalog and the installed state do not wait for each other or for the
      // Linux service's settings: Android-mode Linux answers slowly while it starts.
      final aptCatalog = StoreCatalog.load(
        source,
        language: _language,
        refresh: refresh,
      );
      // Awaited below when APT is enabled; never an unhandled error otherwise.
      unawaited(aptCatalog.then((_) {}, onError: (_) {}));
      final state = loadState();
      final settings = _channel
          .invokeMapMethod<Object?, Object?>('settings')
          .then((value) {
            flatpakCompatibility = value?['flatpakCompatibility'] == true;
          })
          .catchError((_) {});
      sources =
          ((await _channel.invokeMapMethod<String, bool>('storeSources')) ??
          const {'apt': true});
      final loaded = <StoreApp>[];
      void publishCatalog() {
        apps = List.of(loaded);
        LinuxInstallations.instance.remember(container.id, source, apps);
        if (apps.isNotEmpty) loading = false;
        notifyListeners();
      }

      if (sources['apt'] == true) {
        try {
          loaded.addAll(await aptCatalog);
        } catch (e) {
          sourceErrors.add('APT: $e');
        }
        publishCatalog();
      }
      unprepared = [];
      if (sources['deb-get'] == true || sources['flathub'] == true) {
        // Without the state (an older Linux service) the sources are tried as they are.
        final ready = (await state)?['ready'] as Map?;
        unprepared = [
          if (ready != null)
            for (final name in ['deb-get', 'flathub'])
              if (sources[name] == true && ready[name] != true) name,
        ];
      }
      await settings;
      for (final name in ['deb-get', 'flathub']) {
        // Sources not set up in this container are offered on the setup screen instead.
        if (sources[name] != true || unprepared.contains(name)) continue;
        try {
          loaded.addAll(
            await _catalogCache.load(
              '${container.id}/${container.distro}/$name/$_language/arm64',
              () async {
                final snapshot = <StoreApp>[];
                var offset = 0;
                while (true) {
                  final page = await _channel.invokeMapMethod<Object?, Object?>(
                    'storeSourceCatalog',
                    {'id': container.id, 'source': name, 'offset': offset},
                  );
                  final batch = (page?['apps'] as List? ?? const [])
                      .map(
                        (item) => StoreApp.fromJson(
                          Map<String, Object?>.from(item as Map),
                        ),
                      )
                      .toList();
                  snapshot.addAll(batch);
                  if (page == null) throw StateError('Missing catalog page');
                  if (page['more'] != true) break;
                  if (batch.isEmpty) throw StateError('Incomplete catalog');
                  offset += batch.length;
                }
                return snapshot;
              },
              refresh: refresh,
            ),
          );
        } catch (e) {
          sourceErrors.add('$name: $e');
        }
        publishCatalog();
      }
      apps = loaded;
      LinuxInstallations.instance.remember(container.id, source, apps);
      if (loaded.isEmpty && sourceErrors.isNotEmpty) {
        error = sourceErrors.join('\n');
      }
      loading = false;
      notifyListeners();
      await refreshPackages(refresh: refresh);
    } catch (e) {
      error = '$e';
    }
    loading = false;
    notifyListeners();
  }

  Future<void> select(StoreContainer next) async {
    container = next;
    jobs.clear();
    await load();
  }

  /// Installed packages and set-up sources, read from the container's files (immediate);
  /// [refreshPackages] adds the update checks, which run commands in the container.
  Future<Map<Object?, Object?>?> loadState() async {
    if (!supported) return null;
    try {
      final state = await _channel.invokeMapMethod<Object?, Object?>(
        'storeState',
        {'id': container.id},
      );
      installed = {
        ...((state?['installed'] as List?) ?? const []).cast<String>(),
        ...((state?['external'] as List?) ?? const []).cast<String>(),
      };
      notifyListeners();
      return state;
    } catch (_) {
      return null;
    }
  }

  Future<void> refreshPackages({bool refresh = false}) async {
    if (!supported) return;
    final known = installed;
    try {
      final result = await _channel.invokeMapMethod<Object?, Object?>(
        'storePackages',
        {'id': container.id},
      );
      installed = {
        ...known.where((name) => name.contains(':')),
        ...((result?['installed'] as List?) ?? const []).cast<String>(),
      };
      updates = [
        for (final item
            in ((result?['updates'] as List?) ?? const [])
                .cast<Map<Object?, Object?>>())
          StoreUpdate(
            item['name'] as String? ?? '',
            item['from'] as String? ?? '',
            item['to'] as String? ?? '',
          ),
      ];
      upgradable = ((result?['upgradable'] as List?) ?? const [])
          .cast<String>()
          .toSet();
    } on PlatformException catch (e) {
      error = e.message;
    }
    try {
      final extra = await _channel.invokeMapMethod<Object?, Object?>(
        'storeSourceStatus',
        {'id': container.id, 'refresh': refresh},
      );
      final nativeUpdates = upgradable;
      installed = {
        ...installed,
        ...(extra?['installed'] as List? ?? const []).cast<String>(),
      };
      final externalInstalled = (extra?['installed'] as List? ?? const [])
          .cast<String>();
      upgradable = {
        ...upgradable,
        ...(extra?['upgradable'] as List? ?? const []).cast<String>(),
        for (final name in externalInstalled)
          if (name.startsWith('deb-get:') &&
              nativeUpdates.contains(name.substring(8)))
            name,
      };
    } on PlatformException catch (e) {
      sourceErrors.add('Installed sources: ${e.message}');
    }
    try {
      windowsInstalled =
          ((await _channel.invokeListMethod<Object?>('winInstalled', {
                    'id': container.id,
                  })) ??
                  const [])
              .cast<String>()
              .toSet();
    } on PlatformException {
      windowsInstalled = const {};
    }
    await entries.load(container.id);
    notifyListeners();
  }

  /// Installs ([spec] from the catalogue) or removes Windows application [id].
  Future<bool> runWindows(String action, String id, String? spec) async {
    await _prepareJobMetadata(['$windowsJobPrefix$id']);
    final started = await _channel.invokeMethod<bool>('winRun', {
      'id': container.id,
      'action': action,
      'package': id,
      'spec': spec,
    });
    if (started != true) return false;
    jobs['$windowsJobPrefix$id'] = StoreJob(action, '');
    _queueInstallations(action, ['$windowsJobPrefix$id']);
    notifyListeners();
    _poll ??= Timer.periodic(const Duration(seconds: 2), (_) => _pollJobs());
    return true;
  }

  StoreApp? byPackage(String package) {
    for (final app in apps) {
      if (app.package == package) return app;
    }
    return null;
  }

  /// The Linux app list entry of an installed [app], to open it or add it to the home screen.
  LinuxAppEntry? entryFor(StoreApp app) {
    for (final entry in entries.apps) {
      if (app.opens(entry.desktopId)) return entry;
    }
    return null;
  }

  /// What keeps apt from working: (kind, detail) with kind "interrupted", "broken" or "locked".
  Future<List<(String, String)>> diagnose() async {
    try {
      final list =
          await _channel.invokeListMethod<Object?>('aptDiagnose', {
            'id': container.id,
          }) ??
          const [];
      return [
        for (final item in list.cast<Map<Object?, Object?>>())
          (item['kind'] as String? ?? '', item['detail'] as String? ?? ''),
      ];
    } on PlatformException {
      return const [];
    }
  }

  /// Asks whether to repair what [diagnose] found before going on; set by the Store page.
  Future<bool> Function(List<(String, String)> issues, {bool failed})?
  confirmRepair;

  /// Runs apt [action] on [packages]. Problems apt has (a cut-off dpkg run, broken
  /// dependencies) are shown first, and repaired before it when the user agrees.
  Future<bool> run(
    String action,
    List<String> packages, {
    String? onDone,
    bool repair = false,
  }) async {
    try {
      return await _run(action, packages, onDone: onDone, repair: repair);
    } on PlatformException catch (exception) {
      error = exception.message ?? '$exception';
      sourceErrors.add(error!);
      notifyListeners();
      return false;
    }
  }

  bool _notificationPermissionRequested = false;

  Future<void> _prepareJobMetadata(List<String> packages) async {
    if (!_notificationPermissionRequested) {
      _notificationPermissionRequested = true;
      try {
        await const MethodChannel('app.freedextop/display')
            .invokeMethod<bool>('requestStoreNotificationPermission');
      } on PlatformException {
        /* Installation does not depend on notification permission. */
      }
    }
    LinuxInstallations.instance.remember(container.id, source, apps);
    await _channel.invokeMethod<void>('storeJobMetadata', {
      'id': container.id,
      'apps': [
        for (final package in packages)
          {
            'package': package,
            'name':
                windowsNames[package] ??
                byPackage(package)?.name ??
                package.split(':').last,
          },
      ],
    });
  }

  void _queueInstallations(String action, List<String> packages) {
    if (action != 'install') return;
    for (final package in packages) {
      LinuxInstallations.instance.enqueue(
        container.id,
        package,
        name: windowsNames[package],
      );
    }
  }

  Future<bool> _run(
    String action,
    List<String> packages, {
    String? onDone,
    bool repair = false,
  }) async {
    await _prepareJobMetadata(packages);
    final external = packages
        .where((p) => p.startsWith('deb-get:') || p.startsWith('flathub:'))
        .toList();
    if (external.isNotEmpty) {
      if (external.length != packages.length) {
        var ok = true;
        for (final package in packages) {
          ok = await run(action, [package]) && ok;
        }
        return ok;
      }
      var ok = true;
      for (final package in external) {
        final colon = package.indexOf(':');
        final started = await _channel.invokeMethod<bool>('storeSourceRun', {
          'id': container.id,
          'source': package.substring(0, colon),
          'action': action,
          'package': package.substring(colon + 1),
        });
        if (started == true) {
          jobs[package] = StoreJob(action, '');
          _queueInstallations(action, [package]);
        } else {
          ok = false;
        }
      }
      notifyListeners();
      _poll ??= Timer.periodic(const Duration(seconds: 2), (_) => _pollJobs());
      return ok;
    }
    if (packages.isEmpty && action == 'upgrade') {
      final externalUpdates = upgradable.where((p) => p.contains(':')).toList();
      if (externalUpdates.isNotEmpty) await run(action, externalUpdates);
      if (sources['apt'] != true) return externalUpdates.isNotEmpty;
    }
    if (!repair) {
      final issues = await diagnose();
      if (issues.isNotEmpty) {
        final agreed = await confirmRepair?.call(issues) ?? false;
        if (!agreed) return false;
        repair = issues.any((issue) => issue.$1 != 'locked');
      }
    }
    final started = await _channel.invokeMethod<bool>('storeRun', {
      'id': container.id,
      'action': action,
      'packages': packages,
      'repair': repair,
    });
    if (started != true) return false;
    for (final package in packages) {
      jobs[package] = StoreJob(action, '');
    }
    _queueInstallations(action, packages);
    if (packages.isEmpty) jobs['*'] = StoreJob(action, '');
    notifyListeners();
    _poll ??= Timer.periodic(const Duration(seconds: 2), (_) => _pollJobs());
    return true;
  }

  /// Takes a job that still waits out of the queue.
  Future<void> cancel(String name) async {
    final done = await _channel.invokeMethod<bool>('storeCancel', {
      'id': container.id,
      'name': name,
    });
    if (done == true) {
      jobs.remove(name);
      notifyListeners();
    }
  }

  /// The name of a job's package or application, for the queue.
  String jobName(String key, {String all = '*'}) {
    if (key == '*') return all;
    if (key.startsWith(windowsJobPrefix)) {
      return windowsNames[key] ?? key.replaceFirst(windowsJobPrefix, '');
    }
    return byPackage(key)?.name ?? key;
  }

  /// Finished jobs, reported to [onFinished] once each.
  void Function(String action, List<String> packages, bool ok, String output)?
  onFinished;

  Future<void> _pollJobs() async {
    final list =
        await _channel.invokeListMethod<Object?>('storeJobs', {
          'id': container.id,
        }) ??
        const [];
    var finished = false;
    for (final item in list.cast<Map<Object?, Object?>>()) {
      final packages = ((item['packages'] as List?) ?? const []).cast<String>();
      final keys = packages.isEmpty ? const ['*'] : packages;
      final state = item['state'] as String? ?? 'failed';
      final action = item['action'] as String? ?? 'install';
      if (state == 'running' || state == 'queued') {
        for (final key in keys) {
          jobs[key] = StoreJob(
            action,
            item['line'] as String? ?? '',
            (item['percent'] as num?)?.toInt() ?? -1,
            state == 'queued',
          );
        }
      } else if (state == 'cancelled') {
        keys.forEach(jobs.remove);
      } else {
        keys.forEach(jobs.remove);
        finished = true;
        onFinished?.call(
          action,
          packages,
          state == 'done',
          item['output'] as String? ?? '',
        );
      }
    }
    if (jobs.isEmpty) {
      _poll?.cancel();
      _poll = null;
    }
    if (finished) await refreshPackages();
    notifyListeners();
  }

  @override
  void dispose() {
    _poll?.cancel();
    _preparePoll?.cancel();
    super.dispose();
  }
}

/// The Linux Store: applications from the container distribution's official repositories.
class LinuxStorePage extends StatefulWidget {
  const LinuxStorePage({
    super.key,
    required this.containers,
    required this.initial,
  });

  final List<StoreContainer> containers;
  final StoreContainer initial;

  @override
  State<LinuxStorePage> createState() => _LinuxStorePageState();
}

class _LinuxStorePageState extends State<LinuxStorePage> {
  late final LinuxStore store = LinuxStore(widget.containers, widget.initial);
  final search = TextEditingController();
  int tab = 0;

  /// The search bar shows only after the search button is pressed.
  bool searching = false;
  String? category;

  @override
  void initState() {
    super.initState();
    store.onFinished = _finished;
    store.confirmRepair = _confirmRepair;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        store.load(language: Localizations.localeOf(context).languageCode);
      }
    });
    search.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    store.dispose();
    search.dispose();
    super.dispose();
  }

  /// The install queue: downloads and installations running now, and those waiting for them.
  Future<void> _showQueue() => M3EBottomSheet.show<void>(
    context,
    builder: (sheet) => ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        String t(String ja, String en) => linuxText(context, ja, en);
        final theme = Theme.of(context);
        final entries = store.jobs.entries.toList();
        String action(String value) => switch (value) {
          'remove' => t('削除', 'Remove'),
          'upgrade' => t('アップデート', 'Update'),
          'repair' => t('修復', 'Repair'),
          _ => t('インストール', 'Install'),
        };
        return Padding(
          padding: const EdgeInsets.fromLTRB(24, 0, 16, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                t('インストールキュー', 'Install queue'),
                style: theme.textTheme.titleLarge,
              ),
              const SizedBox(height: 12),
              if (entries.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  child: Text(
                    t('実行中・待機中の処理はありません', 'Nothing is running or waiting'),
                    style: theme.textTheme.bodyMedium,
                  ),
                ),
              for (final (index, entry) in entries.indexed)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(
                    children: [
                      entry.value.queued
                          ? Icon(
                              M3EIcons.schedule_rounded,
                              color: theme.colorScheme.onSurfaceVariant,
                            )
                          : StoreProgressCircle(value: entry.value.value),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              store.jobName(
                                entry.key,
                                all: t('すべてのパッケージ', 'All packages'),
                              ),
                              style: theme.textTheme.titleSmall,
                            ),
                            Text(
                              [
                                action(entry.value.action),
                                if (entry.value.queued)
                                  inlineFormat(
                                    t('待機中 ({0} 番目)', 'Waiting (#{0})'),
                                    [index + 1],
                                  )
                                else if (entry.value.percent >= 0)
                                  '${entry.value.percent}%',
                                if (!entry.value.queued &&
                                    entry.value.line.isNotEmpty)
                                  entry.value.line,
                                if (!entry.value.queued &&
                                    entry.value.percent < 0 &&
                                    entry.value.line.isEmpty)
                                  t('準備しています', 'Preparing'),
                              ].join(' · '),
                              style: theme.textTheme.bodySmall,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      if (entry.value.queued)
                        M3EIconButton(
                          variant: M3EIconButtonVariant.standard,
                          icon: const Icon(M3EIcons.close_rounded),
                          tooltip: t('取り消す', 'Cancel'),
                          onPressed: () => store.cancel(entry.key),
                        ),
                    ],
                  ),
                ),
            ],
          ),
        );
      },
    ),
  );

  /// A failed apt job: when apt itself is in trouble, the problem is shown with a repair
  /// that runs the job again; otherwise its log.
  Future<void> _failed(
    String action,
    List<String> packages,
    String output,
  ) async {
    String t(String ja, String en) => linuxText(context, ja, en);
    final external = packages.any((p) => p.contains(':'));
    final issues = external
        ? const <(String, String)>[]
        : await store.diagnose();
    if (!mounted) return;
    if (issues.any((issue) => issue.$1 != 'locked')) {
      if (await _confirmRepair(issues, failed: true)) {
        await store.run(action, packages, repair: true);
      }
      return;
    }
    showStoreLog(context, t('失敗しました', 'Failed'), output);
  }

  /// "What is in which state; repair it?" for what [LinuxStore.diagnose] found.
  Future<bool> _confirmRepair(
    List<(String, String)> issues, {
    bool failed = false,
  }) async {
    if (!mounted) return false;
    String t(String ja, String en) => linuxText(context, ja, en);
    final onlyLocked = issues.every((issue) => issue.$1 == 'locked');
    String describe((String, String) issue) {
      final detail = issue.$2.trim();
      return switch (issue.$1) {
        'interrupted' =>
          detail.isEmpty
              ? t(
                  '前回のパッケージ処理が途中で止まっています。',
                  'An earlier package operation was cut off.',
                )
              : inlineFormat(
                  t(
                    '前回のパッケージ処理が途中で止まっています（未完了: {0}）。',
                    'An earlier package operation was cut off (unfinished: {0}).',
                  ),
                  [detail],
                ),
        'broken' =>
          detail.isEmpty
              ? t('パッケージの依存関係が壊れています。', 'Package dependencies are broken.')
              : inlineFormat(
                  t(
                    'パッケージの依存関係が壊れています（{0}）。',
                    'Package dependencies are broken ({0}).',
                  ),
                  [detail],
                ),
        _ => t(
          '別のパッケージ管理(ソフトウェアセンターなど)が動作中です。終わるまで待ってから続けます。',
          'Another package manager (a software centre, for example) is working. Dextop waits for it to finish.',
        ),
      };
    }

    final agreed = await M3EDialog.show<bool>(
      context,
      dialog: Builder(
        builder: (dialog) => M3EDialog(
          icon: const Icon(M3EIcons.build_rounded),
          title: failed
              ? t('インストールできませんでした', 'The installation failed')
              : onlyLocked
              ? t('パッケージ管理が使用中です', 'The package manager is busy')
              : t('パッケージの修復が必要です', 'Packages need a repair'),
          content: Text(
            [
              for (final issue in issues) describe(issue),
              if (!onlyLocked)
                t(
                  '修復(中断した処理の完了と依存関係の修正)をしてから続けますか？',
                  'Repair them (finish the cut-off operation and fix dependencies) and go on?',
                ),
            ].join('\n\n'),
          ),
          actions: [
            M3EButton.text(
              onPressed: () => Navigator.pop(dialog, false),
              child: Text(t('キャンセル', 'Cancel')),
            ),
            M3EButton.filled(
              onPressed: () => Navigator.pop(dialog, true),
              child: Text(
                onlyLocked
                    ? t('待って続ける', 'Wait and go on')
                    : failed
                    ? t('修復して再試行', 'Repair and retry')
                    : t('修復して続ける', 'Repair and go on'),
              ),
            ),
          ],
        ),
      ),
    );
    return agreed == true;
  }

  void _finished(String action, List<String> packages, bool ok, String output) {
    if (!mounted) return;
    String t(String ja, String en) => linuxText(context, ja, en);
    final app = packages.length == 1 ? store.byPackage(packages.first) : null;
    final name =
        app?.name ??
        packages
            .map(
              (p) =>
                  store.windowsNames[p] ?? p.replaceFirst(windowsJobPrefix, ''),
            )
            .join(', ');
    if (!ok) {
      _failed(action, packages, output);
      return;
    }
    switch (action) {
      case 'install':
        M3ESnackbar.show(
          context,
          message: inlineFormat(t('{0} をインストールしました', '{0} installed'), [name]),
          actionLabel: app == null ? null : t('ホーム画面に追加', 'Add to Home'),
          onAction: app == null ? null : () => addToHome(context, store, app),
          // With an action the snackbar would otherwise stay until dismissed.
          duration: const Duration(seconds: 6),
        );
      case 'remove':
        M3ESnackbar.show(
          context,
          message: inlineFormat(t('{0} を削除しました', '{0} removed'), [name]),
        );
      default:
        M3ESnackbar.show(context, message: t('アップデートしました', 'Updated'));
    }
  }

  @override
  Widget build(BuildContext context) {
    String t(String ja, String en) => linuxText(context, ja, en);
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        final scheme = Theme.of(context).colorScheme;
        final updates = store.apps
            .where((a) => store.upgradable.contains(a.package))
            .toList();
        final wide = linuxWide(context);
        return Scaffold(
          backgroundColor: scheme.surface,
          appBar: M3EAppBar.top(
            automaticallyImplyLeading: true,
            // The tabs separate the bar from the lists, so it never takes the
            // scrolled-under tint.
            backgroundColor: scheme.surface,
            elevation: 0,
            titleText: 'Store',
            subtitleText: '${store.container.name} · ${store.source.label}',
            actions: [
              M3EIconButton(
                variant: M3EIconButtonVariant.standard,
                icon: const Icon(M3EIcons.settings_rounded),
                tooltip: t('信頼するストアソース', 'Trusted Store sources'),
                onPressed: () async {
                  await Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const LinuxSettingsPage(),
                    ),
                  );
                  if (mounted) await store.load();
                },
              ),
              if (!wide)
                M3EIconButton(
                  variant: M3EIconButtonVariant.standard,
                  icon: Icon(
                    searching
                        ? M3EIcons.close_rounded
                        : M3EIcons.search_rounded,
                  ),
                  tooltip: searching
                      ? t('検索を閉じる', 'Close search')
                      : t('検索', 'Search'),
                  onPressed: () => setState(() {
                    searching = !searching;
                    if (!searching) search.clear();
                  }),
                ),
              // The install queue: what runs and what waits for it.
              M3EIconButton(
                variant: M3EIconButtonVariant.standard,
                icon: Badge(
                  isLabelVisible: store.jobs.isNotEmpty,
                  label: Text('${store.jobs.length}'),
                  child: const Icon(M3EIcons.download_rounded),
                ),
                tooltip: t('インストールキュー', 'Install queue'),
                onPressed: _showQueue,
              ),
              if (widget.containers.length > 1)
                M3EIconButton(
                  variant: M3EIconButtonVariant.standard,
                  icon: const Icon(M3EIcons.dns_rounded),
                  tooltip: t('コンテナ', 'Container'),
                  onPressed: _chooseContainer,
                ),
            ],
          ),
          body: wide
              ? Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _sidebar(updates.length),
                    Expanded(child: _content(updates)),
                  ],
                )
              : Column(
                  children: [
                    AnimatedSize(
                      duration: const Duration(milliseconds: 260),
                      curve: Curves.easeOutCubic,
                      alignment: Alignment.topCenter,
                      child: searching
                          ? Padding(
                              padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                              // Full width, and it keeps its width when focused.
                              child: M3ESearchBar(
                                controller: search,
                                autoFocus: true,
                                expandOnFocus: false,
                                margin: 0,
                                focusedMargin: 0,
                                hintText: t('Linuxアプリを検索', 'Search Linux apps'),
                                leading: const Icon(M3EIcons.search_rounded),
                                showClearButton: true,
                              ),
                            )
                          : const SizedBox(width: double.infinity),
                    ),
                    M3ETabs(
                      selectedIndex: tab,
                      onTabSelected: (i) => setState(() => tab = i),
                      tabs: [
                        M3ETab(
                          label: t('おすすめ', 'Recommended'),
                          icon: const Icon(M3EIcons.storefront_rounded),
                        ),
                        M3ETab(
                          label: t('インストール済み', 'Installed'),
                          icon: const Icon(M3EIcons.download_done_rounded),
                        ),
                        M3ETab(
                          label: t('アップデート', 'Updates'),
                          icon: const Icon(M3EIcons.system_update_rounded),
                          badgeCount: updates.isEmpty ? null : updates.length,
                        ),
                        M3ETab(
                          label: 'Windows',
                          icon: const Icon(M3EIcons.window_rounded),
                        ),
                      ],
                    ),
                    Expanded(child: _content(updates)),
                  ],
                ),
        );
      },
    );
  }

  Widget _content(List<StoreApp> updates) => AnimatedSwitcher(
    duration: const Duration(milliseconds: 260),
    switchInCurve: Curves.easeOutCubic,
    child: KeyedSubtree(
      key: ValueKey('$tab/$category/${store.loading}'),
      // The tabs sit between the list and the app bar, so the list's
      // scrolling must not tint the app bar as if content ran under it.
      // M3EAppBar reads the primary scroll controller's offset, so the
      // lists must not attach to it.
      child: PrimaryScrollController.none(
        child: NotificationListener<ScrollNotification>(
          onNotification: (_) => true,
          child: Column(
            children: [
              if (store.sourceErrors.isNotEmpty)
                M3EListItem(
                  supportingMaxLines: null,
                  headline: linuxText(
                    context,
                    '一部のソースを読み込めません',
                    'Some sources could not be loaded',
                  ),
                  supportingText: store.sourceErrors.join('\n'),
                  onTap: () => showStoreLog(
                    context,
                    'Store sources',
                    store.sourceErrors.join('\n'),
                  ),
                ),
              Expanded(
                child: M3ERefreshIndicator.contained(
                  onRefresh: () => store.load(refresh: true),
                  child: _body(updates),
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );

  /// The sidebar of a tablet-sized window: search, the sections and the categories.
  Widget _sidebar(int updateCount) {
    String t(String ja, String en) => linuxText(context, ja, en);
    final theme = Theme.of(context);
    Widget item(
      IconData icon,
      String label, {
      required bool selected,
      required VoidCallback onTap,
      int? badge,
    }) => _SideItem(
      icon: icon,
      label: label,
      selected: selected,
      onTap: onTap,
      badge: badge,
    );
    return SizedBox(
      width: 280,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 4, 8, 24),
        children: [
          M3ESearchBar(
            controller: search,
            expandOnFocus: false,
            margin: 0,
            focusedMargin: 0,
            hintText: t('Linuxアプリを検索', 'Search Linux apps'),
            leading: const Icon(M3EIcons.search_rounded),
            showClearButton: true,
          ),
          const SizedBox(height: 16),
          item(
            M3EIcons.storefront_rounded,
            t('おすすめ', 'Recommended'),
            selected: tab == 0 && category == null,
            onTap: () => setState(() {
              tab = 0;
              category = null;
            }),
          ),
          item(
            M3EIcons.download_done_rounded,
            t('インストール済み', 'Installed'),
            selected: tab == 1,
            onTap: () => setState(() => tab = 1),
          ),
          item(
            M3EIcons.system_update_rounded,
            t('アップデート', 'Updates'),
            selected: tab == 2,
            badge: updateCount == 0 ? null : updateCount,
            onTap: () => setState(() => tab = 2),
          ),
          item(
            M3EIcons.window_rounded,
            t('Windows アプリ', 'Windows apps'),
            selected: tab == 3,
            onTap: () => setState(() => tab = 3),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 20, 16, 8),
            child: Text(
              t('カテゴリ', 'Categories'),
              style: theme.textTheme.titleSmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          for (final c in storeCategories)
            item(
              _categoryIcon(c),
              categoryName(context, c),
              selected: tab == 0 && category == c,
              onTap: () => setState(() {
                tab = 0;
                category = c;
              }),
            ),
        ],
      ),
    );
  }

  static IconData _categoryIcon(String category) => switch (category) {
    'Office' => M3EIcons.description_rounded,
    'Graphics' => M3EIcons.palette_rounded,
    'AudioVideo' => M3EIcons.movie_rounded,
    'Development' => M3EIcons.code_rounded,
    'Network' => M3EIcons.public_rounded,
    'Game' => M3EIcons.sports_esports_rounded,
    'Education' => M3EIcons.school_rounded,
    'Science' => M3EIcons.science_rounded,
    'Utility' => M3EIcons.build_rounded,
    'System' => M3EIcons.settings_rounded,
    _ => M3EIcons.category_rounded,
  };

  /// Shown before the catalog while enabled sources still need their tools in this container.
  Widget _setup() {
    String t(String ja, String en) => linuxText(context, ja, en);
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Icon(
          M3EIcons.download_rounded,
          size: 48,
          color: theme.colorScheme.primary,
        ),
        const SizedBox(height: 16),
        Text(
          t('ストアのセットアップ', 'Store setup'),
          style: theme.textTheme.headlineSmall,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          t(
            '有効にした配布元のツールがこの Linux にまだありません。準備すると、そのアプリが Store に表示されます。',
            'The tools of the enabled sources are not in this Linux yet. Set them up to list their apps.',
          ),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 24),
        for (final name in store.unprepared)
          M3EListItem(
            supportingMaxLines: null,
            headline: name == 'flathub' ? 'Flathub' : 'deb-get',
            supportingText: store.preparing.containsKey(name)
                ? (store.preparing[name]!.isEmpty
                      ? t('準備しています…', 'Setting up…')
                      : store.preparing[name]!)
                : name == 'flathub'
                ? t(
                    'Flatpak をインストールし Flathub を追加',
                    'Install Flatpak and add Flathub',
                  )
                : t('deb-get の定義を取得', 'Fetch deb-get definitions'),
            leading: Icon(
              name == 'flathub'
                  ? M3EIcons.apps_rounded
                  : M3EIcons.download_rounded,
            ),
            trailing: store.preparing.containsKey(name)
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: M3EProgressIndicator.circularWavy(),
                  )
                : M3EButton.filled(
                    onPressed: () => store.prepareSource(name),
                    child: Text(t('準備', 'Set up')),
                  ),
          ),
        const SizedBox(height: 16),
        if (store.unprepared.length > 1)
          M3EButton.tonal(
            onPressed: store.preparing.isNotEmpty
                ? null
                : () {
                    for (final name in store.unprepared) {
                      store.prepareSource(name);
                    }
                  },
            child: Text(t('すべて準備', 'Set up all')),
          ),
        M3EButton.text(
          onPressed: () => setState(() => store.setupSkipped = true),
          child: Text(t('APT だけで続ける', 'Continue with APT only')),
        ),
      ],
    );
  }

  Widget _body(List<StoreApp> updates) {
    String t(String ja, String en) => linuxText(context, ja, en);
    if (store.unprepared.isNotEmpty && !store.setupSkipped) return _setup();
    if (store.loading && store.apps.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const M3ELoadingIndicator(
              variant: M3ELoadingIndicatorVariant.contained,
              size: 72,
            ),
            const SizedBox(height: 16),
            Text(t('カタログを読み込んでいます', 'Loading the catalog')),
          ],
        ),
      );
    }
    if (store.error != null && store.apps.isEmpty) {
      return _Empty(icon: M3EIcons.error_outline_rounded, text: store.error!);
    }
    final query = search.text.trim().toLowerCase();
    bool matches(StoreApp a) =>
        query.isEmpty ||
        a.name.toLowerCase().contains(query) ||
        a.package.contains(query) ||
        a.summary.toLowerCase().contains(query);
    switch (tab) {
      case 3:
        return WindowsStoreView(store: store, query: search.text.trim());
      case 1:
        final list = store.apps
            .where((a) => store.installed.contains(a.package) && matches(a))
            .toList();
        if (list.isEmpty) {
          return _Empty(
            icon: M3EIcons.inventory_2_rounded,
            text: t('Storeからインストールしたアプリはまだありません', 'No apps installed yet'),
          );
        }
        return _list(list);
      case 2:
        final apps = updates.where(matches).toList();
        final appPackages = {for (final app in apps) app.package};
        final others = store.updates
            .where(
              (u) =>
                  !appPackages.contains(u.name) &&
                  (query.isEmpty || u.name.contains(query)),
            )
            .toList();
        final all = store.jobs['*'];
        return CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        store.updates.isEmpty
                            ? t('すべて最新です', 'Everything is up to date')
                            : t('アップデートがあります', 'Updates available'),
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                    if (store.updates.isNotEmpty)
                      all != null
                          ? StoreProgressCircle(value: all.value)
                          : M3EButton.filled(
                              icon: const Icon(M3EIcons.upgrade_rounded),
                              onPressed: () => store.run('upgrade', const []),
                              label: Text(t('すべて更新', 'Update all')),
                            ),
                  ],
                ),
              ),
            ),
            if (store.updates.isEmpty)
              SliverFillRemaining(
                hasScrollBody: false,
                child: _Empty(
                  icon: M3EIcons.check_circle_outline_rounded,
                  text: t('更新が必要なパッケージはありません', 'No packages need updating'),
                ),
              ),
            if (apps.isNotEmpty) ...[
              _header(t('アプリ', 'Apps')),
              _sliverList(apps),
            ],
            if (others.isNotEmpty) ...[
              _header(t('システムとライブラリ', 'System and libraries')),
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
                sliver: M3EList.sliver(
                  itemCount: others.length,
                  itemBuilder: (context, index) {
                    final update = others[index];
                    final job = store.jobs[update.name] ?? all;
                    return M3EListItem(
                      headline: update.name,
                      supportingText: update.from.isEmpty
                          ? update.to
                          : '${update.from} → ${update.to}',
                      leading: const Icon(M3EIcons.inventory_2_rounded),
                      trailing: job != null
                          ? StoreProgressCircle(value: job.value)
                          : M3EButton.tonal(
                              onPressed: () =>
                                  store.run('upgrade', [update.name]),
                              child: Text(t('更新', 'Update')),
                            ),
                    );
                  },
                ),
              ),
            ],
          ],
        );
      default:
        final recommended = [
          for (final package in storeRecommended) ?store.byPackage(package),
        ];
        final filtered = store.apps
            .where(
              (a) =>
                  matches(a) &&
                  (category == null || a.categories.contains(category)),
            )
            .toList();
        final wide = linuxWide(context);
        return CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            if (wide && category != null)
              _header(categoryName(context, category!)),
            if (!wide)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
                  child: Text(
                    t('カテゴリ', 'Categories'),
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
              ),
            if (!wide)
              SliverToBoxAdapter(
                child: SizedBox(
                  height: 56,
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                    children: [
                      for (final c in storeCategories)
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: M3EChip(
                            label: categoryName(context, c),
                            type: M3EChipType.filter,
                            selected: category == c,
                            onPressed: () => setState(
                              () => category = category == c ? null : c,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            if (query.isEmpty &&
                category == null &&
                recommended.isNotEmpty) ...[
              _header(t('おすすめ', 'Recommended')),
              _sliverList(recommended),
              _header(t('すべてのアプリ', 'All apps')),
            ],
            if (query.isNotEmpty) _searchOptions(),
            _sliverList(filtered),
            if (query.isNotEmpty && store.searchAllPackages) ...[
              _header(t('その他の apt パッケージ', 'Other apt packages')),
              AptPackagesSliver(
                store: store,
                query: query,
                exclude: {for (final app in filtered) app.package},
              ),
            ],
            if (query.isNotEmpty && store.searchWindows) ...[
              _header(t('Windows アプリ', 'Windows apps')),
              WindowsResultsSliver(store: store, query: query, limit: 10),
            ],
          ],
        );
    }
  }

  /// What a search also covers besides the distribution's applications.
  Widget _searchOptions() {
    String t(String ja, String en) => linuxText(context, ja, en);
    final side = linuxWide(context) ? 24.0 : 16.0;
    return SliverToBoxAdapter(
      child: Padding(
        padding: EdgeInsets.fromLTRB(side, 12, side, 0),
        child: Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            if (store.sources['apt'] == true)
              M3EChip(
                label: t('すべての apt パッケージ', 'All apt packages'),
                type: M3EChipType.filter,
                selected: store.searchAllPackages,
                onPressed: () => setState(
                  () => store.searchAllPackages = !store.searchAllPackages,
                ),
              ),
            M3EChip(
              label: t('Windows アプリ (winget)', 'Windows apps (winget)'),
              type: M3EChipType.filter,
              selected: store.searchWindows,
              onPressed: () =>
                  setState(() => store.searchWindows = !store.searchWindows),
            ),
          ],
        ),
      ),
    );
  }

  Widget _header(String text) => SliverToBoxAdapter(
    child: Padding(
      padding: EdgeInsets.fromLTRB(linuxWide(context) ? 24 : 16, 20, 16, 4),
      child: Text(text, style: Theme.of(context).textTheme.titleMedium),
    ),
  );

  /// A list right under the tabs keeps the same 16 from them as from the sides.
  Widget _list(List<StoreApp> apps) => CustomScrollView(
    physics: const AlwaysScrollableScrollPhysics(),
    slivers: [_sliverList(apps, top: 16)],
  );

  Widget _sliverList(List<StoreApp> apps, {double top = 4}) {
    if (linuxWide(context)) return _sliverGrid(apps, top: top);
    return SliverPadding(
      padding: EdgeInsets.fromLTRB(16, top, 16, 32),
      sliver: M3EList.sliver(
        itemCount: apps.length,
        onTap: (index) => Navigator.of(context)
            .push(StoreAppRoute(StoreAppPage(store: store, app: apps[index]))),
        itemBuilder: (context, index) =>
            StoreAppTile(store: store, app: apps[index]),
      ),
    );
  }

  /// Apps in two side-by-side lists across a wide window, each grouped like the phone list.
  Widget _sliverGrid(List<StoreApp> apps, {double top = 4}) {
    final half = (apps.length + 1) ~/ 2;
    Widget column(List<StoreApp> part, EdgeInsets padding) => SliverPadding(
      padding: padding,
      sliver: M3EList.sliver(
        itemCount: part.length,
        onTap: (index) => Navigator.of(context)
            .push(StoreAppRoute(StoreAppPage(store: store, app: part[index]))),
        itemBuilder: (context, index) =>
            StoreAppTile(store: store, app: part[index]),
      ),
    );
    return SliverCrossAxisGroup(
      slivers: [
        // The columns sit as close together as the rows (the list's own gap).
        column(
          apps.sublist(0, half),
          EdgeInsets.fromLTRB(16, top, M3EListCardListTheme.defaultGap / 2, 32),
        ),
        column(
          apps.sublist(half),
          EdgeInsets.fromLTRB(M3EListCardListTheme.defaultGap / 2, top, 24, 32),
        ),
      ],
    );
  }

  Future<void> _chooseContainer() async {
    final next = await M3EBottomSheet.show<StoreContainer>(
      context,
      builder: (sheetContext) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final c in widget.containers)
              LinuxMenuRow(
                title: c.name,
                subtitle: StoreSource.forDistro(c.distro).label,
                icon: c.id == store.container.id
                    ? M3EIcons.check_rounded
                    : M3EIcons.dns_rounded,
                onTap: () => Navigator.pop(sheetContext, c),
              ),
          ],
        ),
      ),
    );
    if (next != null && next.id != store.container.id) await store.select(next);
  }
}

String categoryName(BuildContext context, String category) {
  return switch (category) {
    'Office' => localText(context, 'オフィス', 'Office'),
    'Graphics' => localText(context, 'グラフィックス', 'Graphics'),
    'AudioVideo' => localText(context, '音楽と動画', 'Audio & Video'),
    'Network' => localText(context, 'インターネット', 'Internet'),
    'Development' => localText(context, '開発', 'Development'),
    'Education' => localText(context, '教育', 'Education'),
    'Game' => localText(context, 'ゲーム', 'Games'),
    'Science' => localText(context, '科学', 'Science'),
    'Utility' => localText(context, 'ユーティリティ', 'Utilities'),
    'System' => localText(context, 'システム', 'System'),
    _ => category,
  };
}

/// The application's icon from the distribution's icon archive.
class StoreIcon extends StatelessWidget {
  const StoreIcon({
    super.key,
    required this.source,
    required this.app,
    this.size = 48,
  });

  final StoreSource source;
  final StoreApp app;
  final double size;

  @override
  Widget build(BuildContext context) {
    final file = StoreCatalog.iconFile(source, app);
    final scheme = Theme.of(context).colorScheme;
    return ClipRRect(
      borderRadius: BorderRadius.circular(size * .28),
      child: Container(
        width: size,
        height: size,
        color: scheme.surfaceContainerHigh,
        padding: EdgeInsets.all(size * .1),
        child: file == null
            ? Icon(
                M3EIcons.apps_rounded,
                size: size * .6,
                color: scheme.primary,
              )
            : Image.file(
                file,
                fit: BoxFit.contain,
                filterQuality: FilterQuality.medium,
              ),
      ),
    );
  }
}

/// One application in a Store list, with its install state at the end.
class StoreAppTile extends StatelessWidget {
  const StoreAppTile({super.key, required this.store, required this.app});

  final LinuxStore store;
  final StoreApp app;

  @override
  Widget build(BuildContext context) {
    String t(String ja, String en) => linuxText(context, ja, en);
    final job = store.jobs[app.package];
    final installed = store.installed.contains(app.package);
    final Widget trailing;
    if (job != null) {
      trailing = StoreProgressCircle(value: job.value);
    } else if (installed && store.upgradable.contains(app.package)) {
      trailing = M3EButton.filled(
        onPressed: () => store.run('upgrade', [app.package]),
        child: Text(t('更新', 'Update')),
      );
    } else if (installed) {
      trailing = M3EButton.tonal(
        onPressed: store.entryFor(app) == null
            ? null
            : () => openStoreApp(context, store, app),
        child: Text(t('開く', 'Open')),
      );
    } else {
      trailing = M3EButton.filled(
        onPressed: () => store.run('install', [app.package]),
        child: Text(t('入手', 'Get')),
      );
    }
    return M3EListItem(
      headline: app.name,
      supportingText: '${app.label(store.container.distro)} · ${app.summary}',
      leading: Hero(
        tag: 'store-${app.package}-${app.id}',
        transitionOnUserGestures: true,
        child: StoreIcon(source: store.source, app: app),
      ),
      trailing: trailing,
      onTap: () =>
          Navigator.of(context)
              .push(StoreAppRoute(StoreAppPage(store: store, app: app))),
    );
  }
}

Future<void> openStoreApp(
  BuildContext context,
  LinuxStore store,
  StoreApp app,
) async {
  final entry = store.entryFor(app);
  if (entry == null) return;
  await LinuxAppList.launch(store.container.id, entry);
}

Future<void> addToHome(
  BuildContext context,
  LinuxStore store,
  StoreApp app,
) async {
  final entry = store.entryFor(app);
  if (entry == null) return;
  await LinuxAppList.addToHome(context, store.container.id, entry);
}

void showStoreLog(BuildContext context, String title, String output) {
  M3EDialog.show<void>(
    context,
    dialog: M3EDialog(
      title: title,
      content: SizedBox(
        width: 560,
        child: SingleChildScrollView(
          child: SelectableText(
            output,
            style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
          ),
        ),
      ),
      actions: [
        M3EButton.text(
          onPressed: () => Navigator.pop(context),
          child: const Text('OK'),
        ),
      ],
    ),
  );
}

/// Opens an application's page and closes it again with the same motion: the page
/// rises and fades in while the icon flies to its place, and sinks back on the way out.
class StoreAppRoute<T> extends PageRouteBuilder<T> {
  StoreAppRoute(Widget page)
    : super(
        transitionDuration: const Duration(milliseconds: 420),
        reverseTransitionDuration: const Duration(milliseconds: 360),
        pageBuilder: (context, animation, secondary) => page,
        transitionsBuilder: (context, animation, secondary, child) {
          final curve = CurvedAnimation(
            parent: animation,
            curve: Curves.easeOutCubic,
            reverseCurve: Curves.easeInCubic,
          );
          return FadeTransition(
            opacity: curve,
            child: SlideTransition(
              position: Tween(
                begin: const Offset(0, .06),
                end: Offset.zero,
              ).animate(curve),
              child: ScaleTransition(
                scale: Tween(begin: .96, end: 1.0).animate(curve),
                child: child,
              ),
            ),
          );
        },
      );
}

/// One application: what it is, and installing, opening, updating or removing it.
class StoreAppPage extends StatelessWidget {
  const StoreAppPage({super.key, required this.store, required this.app});

  final LinuxStore store;
  final StoreApp app;

  @override
  Widget build(BuildContext context) {
    String t(String ja, String en) => linuxText(context, ja, en);
    final theme = Theme.of(context);
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        final job = store.jobs[app.package];
        final installed = store.installed.contains(app.package);
        final upgradable = store.upgradable.contains(app.package);
        final entry = store.entryFor(app);
        return Scaffold(
          backgroundColor: theme.colorScheme.surface,
          appBar: M3EAppBar.top(automaticallyImplyLeading: true, titleText: ''),
          body: ListView(
            // In a wide window the page keeps a readable width, centred.
            padding: EdgeInsets.symmetric(
              horizontal: ((MediaQuery.sizeOf(context).width - 880) / 2).clamp(
                20.0,
                double.infinity,
              ),
            ).copyWith(top: 8, bottom: 40),
            children: [
              Row(
                children: [
                  Hero(
                    tag: 'store-${app.package}-${app.id}',
                    transitionOnUserGestures: true,
                    child: StoreIcon(source: store.source, app: app, size: 96),
                  ),
                  const SizedBox(width: 20),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(app.name, style: theme.textTheme.headlineSmall),
                        const SizedBox(height: 6),
                        Text(
                          app.package,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.primary,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          app.label(store.container.distro),
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 320),
                switchInCurve: Curves.easeOutCubic,
                switchOutCurve: Curves.easeInCubic,
                transitionBuilder: (child, animation) => FadeTransition(
                  opacity: animation,
                  child: SizeTransition(
                    sizeFactor: animation,
                    alignment: Alignment.topCenter,
                    child: ScaleTransition(
                      scale: Tween(begin: .94, end: 1.0).animate(animation),
                      child: child,
                    ),
                  ),
                ),
                child: job != null
                    ? _Progress(key: const ValueKey('job'), job: job)
                    : installed
                    ? Column(
                        key: const ValueKey('installed'),
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: M3EButton.filled(
                                  size: M3EButtonSize.sm,
                                  icon: const Icon(M3EIcons.play_arrow_rounded),
                                  onPressed: entry == null
                                      ? null
                                      : () => openStoreApp(context, store, app),
                                  label: Text(t('開く', 'Open')),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: M3EButton.tonal(
                                  size: M3EButtonSize.sm,
                                  icon: const Icon(
                                    M3EIcons.add_to_home_screen_rounded,
                                  ),
                                  onPressed: entry == null
                                      ? null
                                      : () => addToHome(context, store, app),
                                  label: Text(t('ホーム画面に追加', 'Add to Home')),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),
                          LinuxGroup(
                            children: [
                              if (upgradable || app.source == 'deb-get')
                                M3EListItem(
                                  supportingMaxLines: null,
                                  headline: upgradable
                                      ? t('アップデートがあります', 'Update available')
                                      : t(
                                          '開発元の最新版に更新',
                                          'Update to the latest upstream version',
                                        ),
                                  supportingText: t(
                                    '新しいバージョンをインストールします',
                                    'Installs the newer version',
                                  ),
                                  leading: const Icon(
                                    M3EIcons.system_update_rounded,
                                  ),
                                  trailing: M3EButton.tonal(
                                    onPressed: () =>
                                        store.run('upgrade', [app.package]),
                                    child: Text(t('更新', 'Update')),
                                  ),
                                ),
                              M3EListItem(
                                supportingMaxLines: null,
                                headline: t('アンインストール', 'Uninstall'),
                                supportingText: t(
                                  'コンテナから削除します',
                                  'Removes it from the container',
                                ),
                                leading: Icon(
                                  M3EIcons.delete_outline_rounded,
                                  color: theme.colorScheme.error,
                                ),
                                onTap: () => _confirmRemove(context),
                              ),
                            ],
                          ),
                        ],
                      )
                    : SizedBox(
                        key: const ValueKey('install'),
                        width: double.infinity,
                        child: M3EButton.filled(
                          size: M3EButtonSize.sm,
                          icon: const Icon(M3EIcons.download_rounded),
                          onPressed: () => store.run('install', [app.package]),
                          label: Text(t('インストール', 'Install')),
                        ),
                      ),
              ),
              if (app.summary.isNotEmpty) ...[
                const SizedBox(height: 28),
                Text(app.summary, style: theme.textTheme.titleMedium),
              ],
              if (app.source == 'flathub') ...[
                const SizedBox(height: 16),
                Text(
                  store.flatpakCompatibility
                      ? t(
                          '互換実行モード：Flatpak のサンドボックス・権限制限は適用されません。ソフトウェア描画を使用します。',
                          'Compatibility mode: Flatpak sandbox and permission restrictions are not enforced. Uses software rendering.',
                        )
                      : t(
                          '標準 Flatpak で起動します。名前空間が使えない端末では Linux 設定の「Flatpak の互換実行」を有効にしてください。',
                          'Uses standard Flatpak execution. On devices without namespaces, enable Flatpak compatibility execution in Linux settings.',
                        ),
                ),
              ],
              if (app.categories.isNotEmpty) ...[
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final c in app.categories.where(
                      storeCategories.contains,
                    ))
                      M3EChip(
                        label: categoryName(context, c),
                        leading: const Icon(M3EIcons.category_rounded),
                      ),
                  ],
                ),
              ],
              if (app.description.isNotEmpty) ...[
                const SizedBox(height: 20),
                M3ECard(
                  variant: M3ECardVariant.filled,
                  padding: const EdgeInsets.all(16),
                  child: Text(
                    app.description,
                    style: theme.textTheme.bodyMedium,
                  ),
                ),
              ],
            ],
          ),
        );
      },
    );
  }

  Future<void> _confirmRemove(BuildContext context) async {
    String t(String ja, String en) => linuxText(context, ja, en);
    final ok = await M3EDialog.show<bool>(
      context,
      dialog: M3EDialog(
        title: inlineFormat(t('{0} をアンインストールしますか？', 'Uninstall {0}?'), [
          app.name,
        ]),
        icon: const Icon(M3EIcons.delete_outline_rounded),
        content: Text(
          inlineFormat(
            t('コンテナから {0} を削除します。', 'Removes {0} from the container.'),
            [app.package],
          ),
        ),
        actions: [
          M3EButton.text(
            onPressed: () => Navigator.pop(context, false),
            child: Text(t('キャンセル', 'Cancel')),
          ),
          M3EButton.filled(
            onPressed: () => Navigator.pop(context, true),
            child: Text(t('アンインストール', 'Uninstall')),
          ),
        ],
      ),
    );
    if (ok == true) await store.run('remove', [app.package]);
  }
}

class _Empty extends StatelessWidget {
  const _Empty({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        SliverFillRemaining(
          hasScrollBody: false,
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(icon, size: 48, color: scheme.outline),
                  const SizedBox(height: 12),
                  Text(
                    text,
                    textAlign: TextAlign.center,
                    style: TextStyle(color: scheme.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// What apt is doing: its latest line and the percentage above a wavy progress bar.
class _Progress extends StatelessWidget {
  const _Progress({super.key, required this.job});

  final StoreJob job;

  @override
  Widget build(BuildContext context) {
    String t(String ja, String en) => linuxText(context, ja, en);
    final theme = Theme.of(context);
    final line = job.line.isNotEmpty
        ? job.line
        : switch (job.action) {
            'remove' => t('アンインストールしています', 'Uninstalling'),
            'upgrade' => t('アップデートしています', 'Updating'),
            _ => t('インストールしています', 'Installing'),
          };
    // The pill takes the place of the button it replaced.
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 16),
      decoration: BoxDecoration(
        color: theme.colorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(28),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Expanded(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 200),
                  layoutBuilder: (current, previous) => Stack(
                    alignment: Alignment.centerLeft,
                    children: [...previous, ?current],
                  ),
                  child: Text(
                    line,
                    key: ValueKey(line),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSecondaryContainer,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Text(
                '${job.percent < 0 ? 0 : job.percent}%',
                style: theme.textTheme.titleSmall?.copyWith(
                  color: theme.colorScheme.primary,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          TweenAnimationBuilder<double>(
            tween: Tween(end: job.value),
            duration: const Duration(milliseconds: 500),
            curve: Curves.easeOutCubic,
            builder: (context, v, _) => M3EProgressIndicator.linearWavy(
              strokeWidth: 3,
              trackStrokeWidth: 3,
              trackColor: theme.colorScheme.onSecondaryContainer.withValues(
                alpha: .18,
              ),
              value: v,
            ),
          ),
        ],
      ),
    );
  }
}

/// One entry of the Store's sidebar: icon, label and an optional count, filled when selected.
class _SideItem extends StatelessWidget {
  const _SideItem({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
    this.badge,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;
  final int? badge;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final color = selected
        ? scheme.onSecondaryContainer
        : scheme.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.only(bottom: 2),
      child: Material(
        color: selected ? scheme.secondaryContainer : const Color(0x00000000),
        borderRadius: BorderRadius.circular(28),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: SizedBox(
            height: 48,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Icon(icon, size: 22, color: color),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      label,
                      overflow: TextOverflow.ellipsis,
                      style: text.labelLarge?.copyWith(
                        color: color,
                        fontWeight: selected
                            ? FontWeight.w700
                            : FontWeight.w500,
                      ),
                    ),
                  ),
                  if (badge != null)
                    Text(
                      '$badge',
                      style: text.labelLarge?.copyWith(color: color),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
