import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/linux/linux_store_catalog.dart';

void main() {
  test('partial catalog is not cached and recovery retries all sources', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(() => server.close(force: true));
    var recovered = false;
    var metadataRequests = 0;
    var iconRequests = 0;
    server.listen((request) async {
      if (request.uri.path.endsWith('.tar.gz')) {
        iconRequests++;
        request.response.add(
          GZipEncoder().encode(TarEncoder().encode(Archive())),
        );
      } else {
        metadataRequests++;
        final main = request.uri.path.startsWith('/main/');
        if (!main && !recovered) {
          request.response.statusCode = 404;
        } else {
          final id = main ? 'main' : 'universe';
          request.response.add(
            gzip.encode(
              utf8.encode(
                'Type: desktop-application\nID: $id\nPackage: $id\nName:\n  C: $id\n',
              ),
            ),
          );
        }
      }
      await request.response.close();
    });
    final key = 'catalog-regression-${server.port}';
    final source = StoreSource(key, 'Test', [
      'http://127.0.0.1:${server.port}/main/Components-arm64.yml.gz',
      'http://127.0.0.1:${server.port}/universe/Components-arm64.yml.gz',
    ]);
    final cache = File(
      '${Directory.systemTemp.path}/linux_store_v2_$key-en.json',
    );
    addTearDown(() async {
      if (await cache.exists()) await cache.delete();
      final icons = StoreCatalog.iconDirectory(source);
      if (await icons.exists()) await icons.delete(recursive: true);
    });
    await expectLater(StoreCatalog.load(source), throwsA(isA<HttpException>()));
    expect(await cache.exists(), isFalse);
    recovered = true;
    final apps = await StoreCatalog.load(source);
    expect(apps.map((app) => app.id).toSet(), {'main', 'universe'});
    expect(metadataRequests, 4);
    final previousIcons = iconRequests;
    await Future.wait([
      StoreCatalog.load(source, refresh: true),
      StoreCatalog.load(source, refresh: true),
    ]);
    expect(metadataRequests, 6);
    expect(iconRequests, previousIcons);
  });
}
