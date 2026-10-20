import 'dart:async';
import 'package:free_dextop/main.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'cold resumed shortcut waits for initial status before consuming',
    (tester) async {
      SharedPreferences.setMockInitialValues({'setup_completed': true});
      const channel = MethodChannel('app.freedextop/display');
      final initialStatus = Completer<Map<String, Object>>();
      final calls = <String>[];
      var pending = true;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            calls.add(call.method);
            if (call.method == 'status') return initialStatus.future;
            if (call.method == 'consumeShortcutAction') {
              final request = pending;
              pending = false;
              return request ? {'closeAfterStart': false} : null;
            }
            return null;
          });
      await tester.pumpWidget(const DextopApp());
      for (var i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 10));
      }
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();
      initialStatus.complete({
        'active': false,
        'privileged': true,
        'shizukuInstalled': true,
        'shizukuRunning': true,
        'shizukuGranted': true,
      });
      for (var i = 0; i < 30; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      expect(
        calls.where((method) => method == 'start').length,
        1,
        reason: 'the shortcut must survive initial status loading',
      );
    },
  );
  testWidgets('a shortcut arriving during startup is applied after startup', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({'setup_completed': true});
    const channel = MethodChannel('app.freedextop/display');
    final startup = Completer<void>();
    var active = false;
    var pending = false;
    var starts = 0;
    var stops = 0;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          switch (call.method) {
            case 'status':
              return {
                'active': active,
                'privileged': true,
                'shizukuInstalled': true,
                'shizukuRunning': true,
                'shizukuGranted': true,
              };
            case 'consumeShortcutAction':
              final request = pending;
              pending = false;
              return request ? {'closeAfterStart': false} : null;
            case 'start':
              starts++;
              await startup.future;
              active = true;
              return null;
            case 'stop':
              stops++;
              active = false;
              return null;
          }
          return null;
        });
    await tester.pumpWidget(const DextopApp());
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    await tester.tap(find.text('Start'));
    await tester.pump();
    expect(starts, 1);
    pending = true;
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
      channel.name,
      const StandardMethodCodec().encodeMethodCall(
        const MethodCall('shortcutAction'),
      ),
      (_) {},
    );
    await tester.pump();
    expect(
      pending,
      isTrue,
      reason: 'busy startup must not consume the native action',
    );
    startup.complete();
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    expect(starts, 1);
    expect(
      stops,
      1,
      reason: 'the deferred toggle stops the now-started display',
    );
    expect(pending, isFalse);
  });
}
