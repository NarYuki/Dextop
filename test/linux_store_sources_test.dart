import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/linux/linux_store_catalog.dart';
import 'package:free_dextop/linux/linux_store_page.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('app.freedextop/linux');
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('app.freedextop/display'),
          (_) async => true,
        );
  });

  test('source and architecture survive catalog caching', () {
    final app = StoreApp.fromJson({
      'id': 'org.example.App',
      'package': 'flathub:org.example.App',
      'name': 'Example',
      'source': 'flathub',
      'architecture': 'ARM64',
    });
    final restored = StoreApp.fromJson(app.toJson());
    expect(restored.label('debian'), 'ARM64 / Debian / Flathub');
    expect(restored.label('ubuntu'), 'ARM64 / Ubuntu / Flathub');
    expect(restored.package, 'flathub:org.example.App');
    final legacy = StoreApp.fromJson({
      'id': 'editor',
      'package': 'editor',
      'name': 'Editor',
    });
    expect(legacy.source, 'apt');
    expect(legacy.label('debian'), 'ARM64 / Debian / APT');
  });

  test('Arch never falls back to Debian catalog', () {
    expect(() => StoreSource.forDistro('archlinux'), throwsUnsupportedError);
    expect(
      StoreSource.forDistro('ubuntu').urls
          .every((url) => url.startsWith('https://')),
      isTrue,
    );
  });

  test('Flatpak and deb-get operations never reach apt', () async {
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'storeJobMetadata') return null;
          calls.add(call);
          if (call.method == 'storeSourceRun') return true;
          throw StateError('Unexpected ${call.method}');
        });
    final container = StoreContainer('ctest', 'Test', 'debian');
    final store = LinuxStore([container], container);
    try {
      expect(await store.run('install', ['flathub:org.example.App']), isTrue);
      expect(await store.run('remove', ['deb-get:codium']), isTrue);
      expect(calls.map((call) => call.method), [
        'storeSourceRun',
        'storeSourceRun',
      ]);
      expect((calls[0].arguments as Map)['source'], 'flathub');
      expect((calls[0].arguments as Map)['package'], 'org.example.App');
      expect((calls[1].arguments as Map)['source'], 'deb-get');
      expect(
        store.jobs.keys,
        containsAll(['flathub:org.example.App', 'deb-get:codium']),
      );
    } finally {
      store.dispose();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    }
  });
  test('cached catalog becomes visible before slow source and package queries finish', () async {
    final slowSource = Completer<Map<String, Object?>>();
    final slowPackages = Completer<Map<String, Object?>>();
    var packageQueryStarted = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          switch (call.method) {
            case 'storeSources':
              return {'apt': false, 'deb-get': true, 'flathub': true};
            case 'settings':
              return <String, Object?>{};
            case 'storeSourceCatalog':
              if ((call.arguments as Map)['source'] == 'flathub')
                return slowSource.future;
              return {
                'apps': [
                  StoreApp.fromJson({
                    'id': 'editor',
                    'package': 'deb-get:editor',
                    'name': 'Editor',
                  }).toJson(),
                ],
                'more': false,
              };
            case 'storeState':
              return {
                'installed': <String>[],
                'external': <String>[],
                'ready': {'deb-get': true, 'flathub': true},
              };
            case 'storePackages':
              packageQueryStarted = true;
              return slowPackages.future;
            case 'storeSourceStatus':
              return <String, Object?>{};
            case 'winInstalled':
            case 'apps':
            case 'shortcuts':
              return [];
            default:
              throw StateError('Unexpected ${call.method}');
          }
        });
    final container = StoreContainer('early-catalog-test', 'Test', 'debian');
    final store = LinuxStore([container], container);
    try {
      final load = store.load(refresh: true);
      for (var i = 0; i < 100 && store.apps.isEmpty; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 10));
      }
      expect(store.apps.single.name, 'Editor');
      expect(store.loading, isFalse);
      expect(packageQueryStarted, isFalse);
      slowSource.complete({'apps': [], 'more': false});
      for (var i = 0; i < 100 && !packageQueryStarted; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 10));
      }
      expect(packageQueryStarted, isTrue);
      expect(store.apps.single.name, 'Editor');
      expect(store.loading, isFalse);
      slowPackages.complete({});
      await load;
    } finally {
      if (!slowSource.isCompleted)
        slowSource.complete({'apps': [], 'more': false});
      if (!slowPackages.isCompleted) slowPackages.complete({});
      store.dispose();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    }
  });
}
