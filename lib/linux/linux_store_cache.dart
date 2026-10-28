import 'dart:convert';
import 'dart:io';

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

  Future<List<StoreApp>> load(
    String key,
    Future<List<StoreApp>> Function() fetch, {
    bool refresh = false,
  }) async {
    final file = File('${directory.path}/${Uri.encodeComponent(key)}.json');
    List<StoreApp>? saved;
    DateTime? modified;
    try {
      modified = await file.lastModified();
      saved = (jsonDecode(await file.readAsString()) as List)
          .map(
            (item) => StoreApp.fromJson(Map<String, Object?>.from(item as Map)),
          )
          .toList();
    } catch (_) {}
    if (!refresh &&
        saved != null &&
        modified != null &&
        now().difference(modified) < maxAge) {
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
      return apps;
    } catch (_) {
      // An offline fallback must not extend the snapshot's lifetime.
      if (saved != null) return saved;
      rethrow;
    }
  }
}
