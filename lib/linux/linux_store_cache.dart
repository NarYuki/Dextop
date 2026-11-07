import 'dart:convert';
import 'dart:io';
import 'dart:isolate';

import 'linux_store_catalog.dart';

/// A complete source snapshot, shared across Store instances and app restarts.
class StoreSnapshotCache {
  StoreSnapshotCache({Directory? directory, DateTime Function()? now})
    : directory =
          directory ??
          Directory('${Directory.systemTemp.path}/store_snapshots_v2'),
      now = now ?? DateTime.now;

  final Directory directory;
  final DateTime Function() now;
  static const maxAge = Duration(days: 3);

  /// Snapshots already read this session, so opening the Store again does not decode them again.
  static final _memory = <String, (DateTime, List<StoreApp>)>{};
  static final _inflight = <String, Future<List<StoreApp>>>{};

  Future<List<StoreApp>> load(
    String key,
    Future<List<StoreApp>> Function() fetch, {
    bool refresh = false,
    void Function(StoreCatalogProgress)? onProgress,
  }) async {
    final identity = '${directory.absolute.path}/${Uri.encodeComponent(key)}';
    final running = _inflight[identity];
    if (running != null) return running;
    final future = _load(key, fetch, refresh: refresh, onProgress: onProgress);
    _inflight[identity] = future;
    try {
      return await future;
    } finally {
      _inflight.remove(identity);
    }
  }

  Future<List<StoreApp>> _load(
    String key,
    Future<List<StoreApp>> Function() fetch, {
    required bool refresh,
    void Function(StoreCatalogProgress)? onProgress,
  }) async {
    final file = File('${directory.path}/${Uri.encodeComponent(key)}.json');
    final remembered = _memory[file.path];
    if (!refresh &&
        remembered != null &&
        now().difference(remembered.$1) < maxAge) {
      onProgress?.call(const StoreCatalogProgress(false, 1, 1));
      return remembered.$2;
    }
    List<StoreApp>? saved;
    DateTime? modified;
    try {
      modified = await file.lastModified();
      onProgress?.call(const StoreCatalogProgress(false, 0, 1));
      final text = await file.readAsString();
      // Large catalogs (Flathub) are decoded off the UI thread.
      saved = await Isolate.run(
        () => (jsonDecode(text) as List)
            .map(
              (item) =>
                  StoreApp.fromJson(Map<String, Object?>.from(item as Map)),
            )
            .toList(),
      );
    } catch (_) {}
    if (!refresh &&
        saved != null &&
        modified != null &&
        now().difference(modified) < maxAge) {
      _memory[file.path] = (modified, saved);
      onProgress?.call(const StoreCatalogProgress(false, 1, 1));
      return saved;
    }
    final List<StoreApp> apps;
    try {
      onProgress?.call(const StoreCatalogProgress(true, 0, null));
      apps = await fetch();
    } catch (_) {
      if (saved != null) {
        onProgress?.call(const StoreCatalogProgress(false, 1, 1));
        return saved;
      }
      rethrow;
    }
    _memory[file.path] = (now(), apps);
    try {
      await directory.create(recursive: true);
      final pending = File('${file.path}.pending');
      await pending.writeAsString(
        jsonEncode([for (final app in apps) app.toJson()]),
        flush: true,
      );
      await pending.rename(file.path);
      await file.setLastModified(now());
    } catch (_) {
      // Persistence failure must not discard a successful network response.
    }
    return apps;
  }
}
