import 'package:flutter/foundation.dart';

import 'linux_store_catalog.dart';

class AppInstallation {
  const AppInstallation({
    required this.container,
    required this.package,
    required this.name,
    this.app,
    this.iconPath,
    this.queued = true,
    this.percent = -1,
  });

  final String container, package, name;
  final StoreApp? app;
  final String? iconPath;
  final bool queued;
  final int percent;
  String get key => '$container:$package';

  bool matches(String desktopId) =>
      app?.opens(desktopId) ??
      desktopId == '${package.split(':').last}.desktop';
}

/// Shared across Store routes so leaving the Store does not hide its queue.
class LinuxInstallations extends ChangeNotifier {
  static final instance = LinuxInstallations();
  final _catalog = <String, AppInstallation>{};
  final _sources = <String, StoreSource>{};
  final _active = <String, AppInstallation>{};

  Iterable<AppInstallation> forContainer(String container) =>
      _active.values.where((item) => item.container == container);

  void remember(String container, StoreSource source, List<StoreApp> apps) {
    _sources[container] = source;
    for (final app in apps) {
      final item = AppInstallation(
        container: container,
        package: app.package,
        name: app.name,
        app: app,
      );
      _catalog[item.key] = item;
    }
  }

  String? _iconPath(String container, AppInstallation? known) {
    if (known?.iconPath != null) return known!.iconPath;
    final source = _sources[container];
    final app = known?.app;
    return source == null || app == null
        ? null
        : StoreCatalog.iconFile(source, app)?.path;
  }

  void enqueue(String container, String package, {String? name}) {
    final known = _catalog['$container:$package'];
    final item = AppInstallation(
      container: container,
      package: package,
      name: name ?? known?.name ?? package.split(':').last,
      app: known?.app,
      iconPath: _iconPath(container, known),
    );
    _active[item.key] = item;
    notifyListeners();
  }

  void snapshot(String container, List<Map<Object?, Object?>> jobs) {
    final next = <String, AppInstallation>{};
    for (final job in jobs) {
      if (job['action'] != 'install' ||
          !const ['queued', 'running'].contains(job['state'])) {
        continue;
      }
      for (final package
          in (job['packages'] as List? ?? const []).cast<String>()) {
        if (package == '*' || package.endsWith(':@prepare')) continue;
        final key = '$container:$package';
        final known = _catalog[key] ?? _active[key];
        next[key] = AppInstallation(
          container: container,
          package: package,
          name: known?.name ?? package.split(':').last,
          app: known?.app,
          iconPath: _iconPath(container, known),
          queued: job['state'] == 'queued',
          percent: (job['percent'] as num?)?.toInt() ?? -1,
        );
      }
    }
    final old = {for (final item in forContainer(container)) item.key: item};
    if (old.length == next.length &&
        next.entries.every(
          (e) =>
              old[e.key]?.queued == e.value.queued &&
              old[e.key]?.percent == e.value.percent &&
              old[e.key]?.name == e.value.name,
        )) {
      return;
    }
    _active.removeWhere((_, item) => item.container == container);
    _active.addAll(next);
    notifyListeners();
  }
}
