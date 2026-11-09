import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:free_dextop/linux/linux_apps.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('app.freedextop/linux');
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null),
  );
  test(
    'uninstall waits for its own operation instead of consuming Store jobs',
    () async {
      final pending = Completer<bool>();
      final calls = <String>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) {
            calls.add(call.method);
            expect(call.arguments['desktop'], 'app.desktop');
            return pending.future;
          });
      var completed = false;
      final result =
          LinuxAppList.uninstall(LinuxAppEntry({'id': 'app.desktop'}, 'c123'))
              .then((value) {
                completed = true;
                return value;
              });
      await Future<void>.delayed(Duration.zero);
      expect(completed, isFalse);
      pending.complete(true);
      expect(await result, isTrue);
      expect(calls, ['uninstallApp']);
    },
  );
  test(
    'package manager error reaches UI instead of becoming generic failure',
    () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (_) async {
            throw PlatformException(
              code: 'uninstall',
              message: 'package is locked',
            );
          });
      await expectLater(
        LinuxAppList.uninstall(LinuxAppEntry({'id': 'app.desktop'}, 'c123')),
        throwsA(
          isA<PlatformException>().having(
            (e) => e.message,
            'reason',
            'package is locked',
          ),
        ),
      );
    },
  );
}
