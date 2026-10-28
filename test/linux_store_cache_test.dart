import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../lib/linux/linux_store_cache.dart';
import '../lib/linux/linux_store_catalog.dart';

void main() {
  test('snapshots survive reopening, expire at three days and allow forced refresh', () async {
    final dir = await Directory.systemTemp.createTemp('store-cache-test');
    addTearDown(() => dir.delete(recursive: true));
    var now = DateTime(2026, 10, 25);
    var calls = 0;
    Future<List<StoreApp>> fetch() async {
      calls++;
      return [
        StoreApp(
          id: '$calls',
          package: 'test',
          name: 'test',
          summary: '',
          description: '',
          categories: [],
          icon: '',
          desktopIds: [],
        ),
      ];
    }

    StoreSnapshotCache cache() =>
        StoreSnapshotCache(directory: dir, now: () => now);
    await cache().load('container/flathub', fetch);
    now = now.add(const Duration(days: 2));
    expect((await cache().load('container/flathub', fetch)).single.id, '1');
    expect(calls, 1);
    now = now.add(const Duration(days: 1));
    expect((await cache().load('container/flathub', fetch)).single.id, '2');
    await cache().load('container/flathub', fetch, refresh: true);
    expect(calls, 3);
    await cache().load('other/flathub', fetch);
    expect(calls, 4);
  });

  test(
    'failed refresh preserves the old snapshot and its expiration',
    () async {
      final dir = await Directory.systemTemp.createTemp('store-cache-test');
      addTearDown(() => dir.delete(recursive: true));
      var now = DateTime(2026, 10, 25);
      final cache = StoreSnapshotCache(directory: dir, now: () => now);
      await cache.load('source', () async => []);
      now = now.add(const Duration(days: 4));
      var failures = 0;
      Future<List<StoreApp>> offline() async {
        failures++;
        throw const SocketException('offline');
      }

      expect(await cache.load('source', offline), isEmpty);
      expect(await cache.load('source', offline), isEmpty);
      expect(failures, 2);
    },
  );
}
