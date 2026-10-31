import '../l10n/inline_text.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart';

import 'linux_apps.dart';
import 'linux_expressive.dart';
import 'linux_store_page.dart';

const _channel = MethodChannel('app.freedextop/linux');

/// A Windows application of the winget catalogue.
class WindowsPackage {
  WindowsPackage(Map<Object?, Object?> json)
    : id = json['id'] as String? ?? '',
      name = json['name'] as String? ?? '',
      publisher = json['publisher'] as String? ?? '',
      version = json['version'] as String? ?? '';

  final String id;
  final String name;
  final String publisher;
  final String version;

  /// The Store job and Linux desktop entry of this application.
  String get job => '$windowsJobPrefix$id';
  String get desktopId =>
      'dextop-win-${id.toLowerCase().replaceAll(RegExp('[^a-z0-9]+'), '-').replaceAll(RegExp(r'^-+|-+$'), '')}.desktop';
}

const windowsJobPrefix = 'win:';

/// Starts installing or removing [package]; installing fetches the installer choice first.
Future<bool> runWindowsJob(
  BuildContext context,
  LinuxStore store,
  String action,
  WindowsPackage package,
) async {
  String t(String ja, String en) => linuxText(context, ja, en);
  String? spec;
  if (action == 'install') {
    try {
      final details = await _channel.invokeMapMethod<Object?, Object?>(
        'winDetails',
        {'package': package.id},
      );
      spec = details?['spec'] as String?;
    } on PlatformException catch (e) {
      if (context.mounted) {
        M3ESnackbar.show(context, message: e.message ?? '$e');
      }
      return false;
    }
    if (spec == null) {
      if (context.mounted) {
        M3ESnackbar.show(
          context,
          message: inlineFormat(t('{0} には Wine で使えるインストーラーがありません', '{0} has no installer that works with Wine'), [package.name]),
        );
      }
      return false;
    }
  }
  store.windowsNames[package.job] = package.name;
  return store.runWindows(action, package.id, spec);
}

/// Opens an installed Windows application like any Linux application of the container.
Future<void> openWindowsApp(LinuxStore store, WindowsPackage package) =>
    LinuxAppList.launch(
      store.container.id,
      LinuxAppEntry({
        'id': package.desktopId,
        'name': package.name,
      }, store.container.id),
    );

/// Windows applications in the Store: featured ones, or what matches [query].
class WindowsStoreView extends StatefulWidget {
  const WindowsStoreView({super.key, required this.store, required this.query});

  final LinuxStore store;
  final String query;

  @override
  State<WindowsStoreView> createState() => _WindowsStoreViewState();
}

class _WindowsStoreViewState extends State<WindowsStoreView> {
  List<WindowsPackage>? packages;
  String? error;
  Timer? _debounce;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _search();
  }

  @override
  void didUpdateWidget(WindowsStoreView old) {
    super.didUpdateWidget(old);
    if (old.query != widget.query) {
      _debounce?.cancel();
      _debounce = Timer(const Duration(milliseconds: 350), _search);
    }
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  Future<void> _search() async {
    final generation = ++_generation;
    try {
      final list =
          await _channel.invokeListMethod<Object?>('winSearch', {
            'query': widget.query,
          }) ??
          const [];
      if (!mounted || generation != _generation) return;
      setState(() {
        packages = list
            .cast<Map<Object?, Object?>>()
            .map(WindowsPackage.new)
            .toList();
        error = null;
      });
    } on PlatformException catch (e) {
      if (mounted && generation == _generation) {
        setState(() => error = e.message);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    String t(String ja, String en) => linuxText(context, ja, en);
    final theme = Theme.of(context);
    final list = packages;
    if (list == null && error == null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const M3ELoadingIndicator(
              variant: M3ELoadingIndicatorVariant.contained,
              size: 72,
            ),
            const SizedBox(height: 16),
            Text(t('winget のカタログを読み込んでいます', 'Loading the winget catalogue')),
          ],
        ),
      );
    }
    final wide = linuxWide(context);
    return ListenableBuilder(
      listenable: widget.store,
      builder: (context, _) => CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(wide ? 24 : 16, 16, 16, 4),
              child: Text(
                widget.query.isEmpty
                    ? t('おすすめの Windows アプリ', 'Featured Windows apps')
                    : t('Windows アプリ', 'Windows apps'),
                style: theme.textTheme.titleMedium,
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(wide ? 24 : 16, 0, 16, 8),
              child: Text(
                error ??
                    t(
                      'winget のカタログから入手し、Proton(ARM64)で動かします。動作はアプリによって異なります。',
                      'From the winget catalogue, run with Proton (ARM64). How well each app works varies.',
                    ),
                style: theme.textTheme.bodySmall,
              ),
            ),
          ),
          SliverPadding(
            padding: EdgeInsets.fromLTRB(16, 4, wide ? 24 : 16, 32),
            sliver: M3EList.sliver(
              itemCount: list?.length ?? 0,
              onTap: (index) => Navigator.of(context).push(
                StoreAppRoute(
                  WindowsAppPage(store: widget.store, package: list![index]),
                ),
              ),
              itemBuilder: (context, index) =>
                  WindowsAppTile(store: widget.store, package: list![index]),
            ),
          ),
        ],
      ),
    );
  }
}

class WindowsAppTile extends StatelessWidget {
  const WindowsAppTile({super.key, required this.store, required this.package});

  final LinuxStore store;
  final WindowsPackage package;

  @override
  Widget build(BuildContext context) {
    String t(String ja, String en) => linuxText(context, ja, en);
    final job = store.jobs[package.job];
    final installed = store.windowsInstalled.contains(package.id);
    final Widget trailing;
    if (job != null) {
      trailing = StoreProgressCircle(value: job.value);
    } else if (installed) {
      trailing = M3EButton.tonal(
        onPressed: () => openWindowsApp(store, package),
        child: Text(t('開く', 'Open')),
      );
    } else {
      trailing = M3EButton.filled(
        onPressed: () => runWindowsJob(context, store, 'install', package),
        child: Text(t('入手', 'Get')),
      );
    }
    return M3EListItem(
      headline: package.name,
      supportingText: '${package.publisher} · ${package.version}',
      leading: const _WindowsIcon(url: '', size: 40),
      trailing: trailing,
      onTap: () => Navigator.of(context)
          .push(StoreAppRoute(WindowsAppPage(store: store, package: package))),
    );
  }
}

class _WindowsIcon extends StatelessWidget {
  const _WindowsIcon({required this.url, required this.size});

  final String url;
  final double size;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final fallback = Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: scheme.secondaryContainer,
        borderRadius: BorderRadius.circular(size * .28),
      ),
      child: Icon(
        M3EIcons.window_rounded,
        size: size * .55,
        color: scheme.onSecondaryContainer,
      ),
    );
    if (url.isEmpty || url.toLowerCase().endsWith('.ico')) return fallback;
    return ClipRRect(
      borderRadius: BorderRadius.circular(size * .22),
      child: Image.network(
        url,
        width: size,
        height: size,
        fit: BoxFit.contain,
        errorBuilder: (_, _, _) => fallback,
      ),
    );
  }
}

/// A Windows application's page: what it is, which installer Dextop uses, and its actions.
class WindowsAppPage extends StatefulWidget {
  const WindowsAppPage({super.key, required this.store, required this.package});

  final LinuxStore store;
  final WindowsPackage package;

  @override
  State<WindowsAppPage> createState() => _WindowsAppPageState();
}

class _WindowsAppPageState extends State<WindowsAppPage> {
  Map<Object?, Object?>? details;
  String? error;

  @override
  void initState() {
    super.initState();
    _channel
        .invokeMapMethod<Object?, Object?>('winDetails', {
          'package': widget.package.id,
        })
        .then((value) {
          if (mounted) setState(() => details = value);
        })
        .catchError((Object e) {
          if (mounted) {
            setState(() => error = e is PlatformException ? e.message : '$e');
          }
        });
  }

  @override
  Widget build(BuildContext context) {
    String t(String ja, String en) => linuxText(context, ja, en);
    final theme = Theme.of(context);
    final store = widget.store;
    final package = widget.package;
    final info = details;
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        final job = store.jobs[package.job];
        final installed = store.windowsInstalled.contains(package.id);
        final installable = info?['spec'] != null;
        String text(String key) => info?[key] as String? ?? '';
        return Scaffold(
          backgroundColor: theme.colorScheme.surface,
          appBar: M3EAppBar.top(automaticallyImplyLeading: true, titleText: ''),
          body: ListView(
            padding: EdgeInsets.symmetric(
              horizontal: ((MediaQuery.sizeOf(context).width - 880) / 2).clamp(
                20.0,
                double.infinity,
              ),
            ).copyWith(top: 8, bottom: 40),
            children: [
              Row(
                children: [
                  _WindowsIcon(url: text('iconUrl'), size: 96),
                  const SizedBox(width: 20),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          info?['name'] as String? ?? package.name,
                          style: theme.textTheme.headlineSmall,
                        ),
                        const SizedBox(height: 6),
                        Text(
                          info?['publisher'] as String? ?? package.publisher,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.primary,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          [
                            'winget',
                            info?['version'] as String? ?? package.version,
                            if (info?['installer'] != null)
                              info!['installer'] as String,
                          ].join(' · '),
                          style: theme.textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              if (job != null)
                Row(
                  children: [
                    StoreProgressCircle(value: job.value),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        job.line.isEmpty ? t('準備しています', 'Preparing') : job.line,
                      ),
                    ),
                  ],
                )
              else if (installed)
                Row(
                  children: [
                    Expanded(
                      child: M3EButton.filled(
                        onPressed: () => openWindowsApp(store, package),
                        child: Text(t('開く', 'Open')),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: M3EButton.tonal(
                        onPressed: () =>
                            runWindowsJob(context, store, 'remove', package),
                        child: Text(t('アンインストール', 'Uninstall')),
                      ),
                    ),
                  ],
                )
              else
                M3EButton.filled(
                  onPressed: info == null || !installable
                      ? null
                      : () => runWindowsJob(context, store, 'install', package),
                  child: Text(
                    info != null && !installable
                        ? t(
                            'Wine で使えるインストーラーがありません',
                            'No installer works with Wine',
                          )
                        : t('インストール', 'Install'),
                  ),
                ),
              const SizedBox(height: 24),
              if (error != null)
                Text(error!, style: theme.textTheme.bodyMedium),
              if (info == null && error == null)
                const Center(child: M3ELoadingIndicator()),
              if (text('summary').isNotEmpty) ...[
                Text(text('summary'), style: theme.textTheme.titleMedium),
                const SizedBox(height: 12),
              ],
              if (text('description').isNotEmpty)
                Text(text('description'), style: theme.textTheme.bodyMedium),
              const SizedBox(height: 16),
              if (text('license').isNotEmpty)
                Text(
                  '${t('ライセンス', 'License')}: ${text('license')}',
                  style: theme.textTheme.bodySmall,
                ),
              if (text('homepage').isNotEmpty)
                Text(text('homepage'), style: theme.textTheme.bodySmall),
            ],
          ),
        );
      },
    );
  }
}

/// Windows applications matching [query], as a part of a longer list (the Store's search).
class WindowsResultsSliver extends StatefulWidget {
  const WindowsResultsSliver({
    super.key,
    required this.store,
    required this.query,
    this.limit = 10,
  });

  final LinuxStore store;
  final String query;
  final int limit;

  @override
  State<WindowsResultsSliver> createState() => _WindowsResultsSliverState();
}

class _WindowsResultsSliverState extends State<WindowsResultsSliver> {
  List<WindowsPackage> packages = const [];
  Timer? _debounce;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _search();
  }

  @override
  void didUpdateWidget(WindowsResultsSliver old) {
    super.didUpdateWidget(old);
    if (old.query != widget.query) {
      _debounce?.cancel();
      _debounce = Timer(const Duration(milliseconds: 400), _search);
    }
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  Future<void> _search() async {
    final generation = ++_generation;
    try {
      final list =
          await _channel.invokeListMethod<Object?>('winSearch', {
            'query': widget.query,
          }) ??
          const [];
      if (!mounted || generation != _generation) return;
      setState(() {
        packages = list
            .cast<Map<Object?, Object?>>()
            .map(WindowsPackage.new)
            .take(widget.limit)
            .toList();
      });
    } on PlatformException {
      if (mounted && generation == _generation) {
        setState(() => packages = const []);
      }
    }
  }

  @override
  Widget build(BuildContext context) => SliverPadding(
    padding: EdgeInsets.fromLTRB(16, 4, linuxWide(context) ? 24 : 16, 8),
    sliver: M3EList.sliver(
      itemCount: packages.length,
      onTap: (index) => Navigator.of(context).push(
        StoreAppRoute(
          WindowsAppPage(store: widget.store, package: packages[index]),
        ),
      ),
      itemBuilder: (context, index) =>
          WindowsAppTile(store: widget.store, package: packages[index]),
    ),
  );
}

/// Every apt package matching [query] (not only applications with AppStream data), except
/// those in [exclude], to install or remove.
class AptPackagesSliver extends StatefulWidget {
  const AptPackagesSliver({
    super.key,
    required this.store,
    required this.query,
    required this.exclude,
  });

  final LinuxStore store;
  final String query;
  final Set<String> exclude;

  @override
  State<AptPackagesSliver> createState() => _AptPackagesSliverState();
}

class _AptPackagesSliverState extends State<AptPackagesSliver> {
  List<(String, String)> packages = const [];
  Timer? _debounce;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _search();
  }

  @override
  void didUpdateWidget(AptPackagesSliver old) {
    super.didUpdateWidget(old);
    if (old.query != widget.query) {
      _debounce?.cancel();
      _debounce = Timer(const Duration(milliseconds: 400), _search);
    }
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  Future<void> _search() async {
    final generation = ++_generation;
    try {
      final list =
          await _channel.invokeListMethod<Object?>('aptSearch', {
            'id': widget.store.container.id,
            'query': widget.query,
          }) ??
          const [];
      if (!mounted || generation != _generation) return;
      setState(() {
        packages = [
          for (final item in list.cast<Map<Object?, Object?>>())
            (item['name'] as String? ?? '', item['summary'] as String? ?? ''),
        ];
      });
    } on PlatformException {
      if (mounted && generation == _generation) {
        setState(() => packages = const []);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    String t(String ja, String en) => linuxText(context, ja, en);
    final store = widget.store;
    final shown = packages
        .where((p) => !widget.exclude.contains(p.$1))
        .toList();
    return SliverPadding(
      padding: EdgeInsets.fromLTRB(16, 4, linuxWide(context) ? 24 : 16, 8),
      sliver: M3EList.sliver(
        itemCount: shown.length,
        itemBuilder: (context, index) {
          final (name, summary) = shown[index];
          final job = store.jobs[name];
          final installed = store.installed.contains(name);
          return M3EListItem(
            headline: name,
            supportingText: summary,
            leading: const Icon(M3EIcons.inventory_2_rounded),
            trailing: job != null
                ? StoreProgressCircle(value: job.value)
                : installed
                ? M3EButton.tonal(
                    onPressed: () => store.run('remove', [name]),
                    child: Text(t('削除', 'Remove')),
                  )
                : M3EButton.filled(
                    onPressed: () => store.run('install', [name]),
                    child: Text(t('入手', 'Get')),
                  ),
          );
        },
      ),
    );
  }
}
