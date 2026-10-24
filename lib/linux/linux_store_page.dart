import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_apps.dart';
import 'linux_expressive.dart';
import 'linux_store_catalog.dart';
import 'linux_widgets.dart';

/// A container the Store can install into.
class StoreContainer {
  const StoreContainer(this.id, this.name, this.distro);

  final String id;
  final String name;
  final String distro;
}

/// A package job the Store started with apt.
class StoreJob {
  const StoreJob(this.action, this.line, [this.percent = -1]);

  final String action;
  final String line;

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
        trackColor: Theme.of(
          context,
        ).colorScheme.primary.withValues(alpha: .22),
        value: v,
      ),
    ),
  );
}

/// Catalog, installed packages and running jobs of one container, shared by the Store pages.
class LinuxStore extends ChangeNotifier {
  LinuxStore(this.containers, StoreContainer initial) : container = initial;

  static const _channel = MethodChannel('app.freedextop/linux');

  final List<StoreContainer> containers;
  StoreContainer container;
  StoreSource get source => StoreSource.forDistro(container.distro);

  List<StoreApp> apps = const [];
  Set<String> installed = const {};
  Set<String> upgradable = const {};
  List<StoreUpdate> updates = const [];
  final Map<String, StoreJob> jobs = {};
  bool loading = true;
  String? error;
  Timer? _poll;
  String _language = 'en';

  /// Desktop entries of the container, to open installed applications.
  final entries = LinuxAppList();

  Future<void> load({bool refresh = false, String? language}) async {
    if (language != null) _language = language;
    loading = true;
    error = null;
    notifyListeners();
    try {
      apps = await StoreCatalog.load(
        source,
        language: _language,
        refresh: refresh,
      );
      await refreshPackages();
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

  Future<void> refreshPackages() async {
    try {
      final result = await _channel.invokeMapMethod<Object?, Object?>(
        'storePackages',
        {'id': container.id},
      );
      installed = ((result?['installed'] as List?) ?? const [])
          .cast<String>()
          .toSet();
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
    await entries.load(container.id);
    notifyListeners();
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

  Future<bool> run(
    String action,
    List<String> packages, {
    String? onDone,
  }) async {
    final started = await _channel.invokeMethod<bool>('storeRun', {
      'id': container.id,
      'action': action,
      'packages': packages,
    });
    if (started != true) return false;
    for (final package in packages) {
      jobs[package] = StoreJob(action, '');
    }
    if (packages.isEmpty) jobs['*'] = StoreJob(action, '');
    notifyListeners();
    _poll ??= Timer.periodic(const Duration(seconds: 2), (_) => _pollJobs());
    return true;
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
      if (state == 'running') {
        for (final key in keys) {
          jobs[key] = StoreJob(
            action,
            item['line'] as String? ?? '',
            (item['percent'] as num?)?.toInt() ?? -1,
          );
        }
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

  void _finished(String action, List<String> packages, bool ok, String output) {
    if (!mounted) return;
    String t(String ja, String en) => linuxText(context, ja, en);
    final app = packages.length == 1 ? store.byPackage(packages.first) : null;
    final name = app?.name ?? packages.join(', ');
    if (!ok) {
      showStoreLog(context, t('失敗しました', 'Failed'), output);
      return;
    }
    switch (action) {
      case 'install':
        M3ESnackbar.show(
          context,
          message: t('$name をインストールしました', '$name installed'),
          actionLabel: app == null ? null : t('ホーム画面に追加', 'Add to Home'),
          onAction: app == null ? null : () => addToHome(context, store, app),
          // With an action the snackbar would otherwise stay until dismissed.
          duration: const Duration(seconds: 6),
        );
      case 'remove':
        M3ESnackbar.show(context, message: t('$name を削除しました', '$name removed'));
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
                icon: Icon(
                  searching ? M3EIcons.close_rounded : M3EIcons.search_rounded,
                ),
                tooltip: searching
                    ? t('検索を閉じる', 'Close search')
                    : t('検索', 'Search'),
                onPressed: () => setState(() {
                  searching = !searching;
                  if (!searching) search.clear();
                }),
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
          body: Column(
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
                ],
              ),
              Expanded(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 260),
                  switchInCurve: Curves.easeOutCubic,
                  child: KeyedSubtree(
                    key: ValueKey('$tab/${store.loading}'),
                    // The tabs sit between the list and the app bar, so the list's
                    // scrolling must not tint the app bar as if content ran under it.
                    // M3EAppBar reads the primary scroll controller's offset, so the
                    // lists must not attach to it.
                    child: PrimaryScrollController.none(
                      child: NotificationListener<ScrollNotification>(
                        onNotification: (_) => true,
                        child: _body(updates),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _body(List<StoreApp> updates) {
    String t(String ja, String en) => linuxText(context, ja, en);
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
        return CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
                child: Text(
                  t('カテゴリ', 'Categories'),
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ),
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
            _sliverList(filtered),
          ],
        );
    }
  }

  Widget _header(String text) => SliverToBoxAdapter(
    child: Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 4),
      child: Text(text, style: Theme.of(context).textTheme.titleMedium),
    ),
  );

  /// A list right under the tabs keeps the same 16 from them as from the sides.
  Widget _list(List<StoreApp> apps) =>
      CustomScrollView(slivers: [_sliverList(apps, top: 16)]);

  Widget _sliverList(List<StoreApp> apps, {double top = 4}) => SliverPadding(
    padding: EdgeInsets.fromLTRB(16, top, 16, 32),
    sliver: M3EList.sliver(
      itemCount: apps.length,
      onTap: (index) => Navigator.of(
        context,
      ).push(StoreAppRoute(StoreAppPage(store: store, app: apps[index]))),
      itemBuilder: (context, index) =>
          StoreAppTile(store: store, app: apps[index]),
    ),
  );

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
  final ja = Localizations.localeOf(context).languageCode == 'ja';
  return switch (category) {
    'Office' => ja ? 'オフィス' : 'Office',
    'Graphics' => ja ? 'グラフィックス' : 'Graphics',
    'AudioVideo' => ja ? '音楽と動画' : 'Audio & Video',
    'Network' => ja ? 'インターネット' : 'Internet',
    'Development' => ja ? '開発' : 'Development',
    'Education' => ja ? '教育' : 'Education',
    'Game' => ja ? 'ゲーム' : 'Games',
    'Science' => ja ? '科学' : 'Science',
    'Utility' => ja ? 'ユーティリティ' : 'Utilities',
    'System' => ja ? 'システム' : 'System',
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
      supportingText: '${store.source.label} · ${app.summary}',
      leading: Hero(
        tag: 'store-${app.package}-${app.id}',
        transitionOnUserGestures: true,
        child: StoreIcon(source: store.source, app: app),
      ),
      trailing: trailing,
      onTap: () => Navigator.of(
        context,
      ).push(StoreAppRoute(StoreAppPage(store: store, app: app))),
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
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 40),
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
                          store.source.label,
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
                              if (upgradable)
                                M3EListItem(
                                  headline: t(
                                    'アップデートがあります',
                                    'Update available',
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
        title: t('${app.name} をアンインストールしますか？', 'Uninstall ${app.name}?'),
        icon: const Icon(M3EIcons.delete_outline_rounded),
        content: Text(
          t(
            'コンテナから ${app.package} を削除します。',
            'Removes ${app.package} from the container.',
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
    return Center(
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
