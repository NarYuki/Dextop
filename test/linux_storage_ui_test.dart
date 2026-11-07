import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';

import '../lib/linux/linux_storage_ui.dart';

void main() {
  testWidgets(
    'categories drill into directories and preserve measurements on back',
    (tester) async {
      const channel = MethodChannel('app.freedextop/linux');
      final paths = <String>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            switch (call.method) {
              case 'containerUsage':
                return true;
              case 'snapshots':
                return {
                  'job': {
                    'state': 'done',
                    'result': {
                      'total': 30,
                      'cacheBytes': 0,
                      'parts': [
                        {'id': 'usr', 'path': '/usr', 'bytes': 30},
                      ],
                    },
                  },
                };
              case 'storageBreakdown':
                final path = call.arguments['path'] as String;
                paths.add(path);
                return {
                  'path': path,
                  'total': 30,
                  'count': 1,
                  'nextOffset': 1,
                  'entries': [
                    path == '/usr'
                        ? {
                            'name': 'bin',
                            'path': '/usr/bin',
                            'directory': true,
                            'bytes': 30,
                          }
                        : {
                            'name': 'tool',
                            'path': '/usr/bin/tool',
                            'directory': false,
                            'bytes': 30,
                          },
                  ],
                };
            }
            return null;
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () =>
                    LinuxStorageUi.show(context, 'c123', stopped: true),
                child: const Text('Storage'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Storage'));
      await tester.pump();
      await tester.pump();
      await tester.pumpAndSettle();
      await tester.tap(find.text('Installed software (/usr)'));
      await tester.pumpAndSettle();
      expect(find.text('bin'), findsOneWidget);
      await tester.tap(find.text('bin'));
      await tester.pumpAndSettle();
      expect(find.text('tool'), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('bin'), findsOneWidget);
      expect(paths, ['/usr', '/usr/bin']);
    },
  );
}
