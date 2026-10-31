import 'dart:convert';
import 'dart:io';
import 'dart:isolate';

import 'linux_store_catalog.dart';

/// A complete source snapshot, shared across Store instances and app restarts.
class StoreSnapshotCache {
  StoreSnapshotCache({Directory? directory, DateTime Function()? now})
    : directory =
          directory ??
          Directory('${Directory.systemTemp.path}/store_snapshots_v1'),
      now = now ?? DateTime.now;

  final Directory directory;
  final DateTime Function() now;
  static const maxAge = Duration(days: 3);

  /// Snapshots already read this session, so opening the Store again does not decode them again.
  static final _memory = <String, (DateTime, List<StoreApp>)>{};

  Future<List<StoreApp>> load(
    String key,
    Future<List<StoreApp>> Function() fetch, {
    bool refresh = false,
  }) async {
    final file = File('${directory.path}/${Uri.encodeComponent(key)}.json');
    final remembered = _memory[file.path];
    if (!refresh &&
        remembered != null &&
        now().difference(remembered.$1) < maxAge) {
      return remembered.$2;
    }
    List<StoreApp>? saved;
    DateTime? modified;
    try {
      modified = await file.lastModified();
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
      return saved;
    }
    try {
      final apps = await fetch();
      await directory.create(recursive: true);
      final pending = File('${file.path}.pending');
      await pending.writeAsString(
        jsonEncode([for (final app in apps) app.toJson()]),
        flush: true,
      );
      await pending.rename(file.path);
      await file.setLastModified(now());
      _memory[file.path] = (now(), apps);
      return apps;
    } catch (_) {
      // An offline fallback must not extend the snapshot's lifetime.
      if (saved != null) return saved;
      rethrow;
    }
  }
}
