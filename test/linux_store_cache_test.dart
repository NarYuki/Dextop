import 'dart:io';
import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import 'package:free_dextop/linux/linux_store_cache.dart';
import 'package:free_dextop/linux/linux_store_catalog.dart';

void main() {
  test('progress distinguishes fresh fetch, memory read, forced refresh and fallback', () async {
    final directory = await Directory.systemTemp.createTemp('store-progress');
    addTearDown(() => directory.delete(recursive: true));
    final cache = StoreSnapshotCache(directory: directory);
    final events = <StoreCatalogProgress>[];
    await cache.load('progress', () async => [], onProgress: events.add);
    expect(events.any((p) => p.refreshing), isTrue);
    events.clear();
    await cache.load(
      'progress',
      () async => throw StateError('must use memory'),
      onProgress: events.add,
    );
    expect(events.single.refreshing, isFalse);
    expect(events.single.completed, events.single.total);
    events.clear();
    await cache.load(
      'progress',
      () async => throw StateError('offline'),
      refresh: true,
      onProgress: events.add,
    );
    expect(events.any((p) => p.refreshing), isTrue);
    expect(events.last.refreshing, isFalse);
    expect(events.last.completed, events.last.total);
  });

  test('successful fetch survives cache write failure', () async {
    final dir = await Directory.systemTemp.createTemp('store-write-failure');
    addTearDown(() => dir.delete(recursive: true));
    final cache = StoreSnapshotCache(directory: dir);
    await cache.load('source', () async => []);
    await Directory('${dir.path}/source.json.pending').create();
    final fresh = StoreApp.fromJson({
      'id': 'fresh',
      'package': 'fresh',
      'name': 'Fresh',
    });
    expect(
      (await cache.load(
        'source',
        () async => [fresh],
        refresh: true,
      )).single.id,
      'fresh',
    );
    expect(
      (await cache.load(
        'source',
        () async => throw StateError('unnecessary fetch'),
      )).single.id,
      'fresh',
    );
  });

  test('simultaneous refreshes share one fetch', () async {
    final dir = await Directory.systemTemp.createTemp('store-coalesce');
    addTearDown(() => dir.delete(recursive: true));
    final completion = Completer<List<StoreApp>>();
    var calls = 0;
    Future<List<StoreApp>> fetch() {
      calls++;
      return completion.future;
    }

    final cache = StoreSnapshotCache(directory: dir);
    final first = cache.load('source', fetch, refresh: true);
    final second = cache.load('source', fetch, refresh: true);
    completion.complete([]);
    await Future.wait([first, second]);
    expect(calls, 1);
  });
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
