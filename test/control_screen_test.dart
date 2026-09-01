import 'package:free_dextop/main.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('app.freedextop/display');

  setUp(() {
    SharedPreferences.setMockInitialValues({'setup_completed': true});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'status') {
            return {
              'active': false,
              'privileged': true,
              'shizukuInstalled': true,
              'shizukuRunning': true,
              'shizukuGranted': true,
            };
          }
          return null;
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  testWidgets('shows status and action', (tester) async {
    await tester.pumpWidget(const DextopApp());
    for (var frame = 0; frame < 12; frame++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    expect(find.text('Dextop'), findsWidgets);
    expect(find.text('Dextop is ready'), findsOneWidget);
    expect(find.text('Start'), findsOneWidget);
    expect(find.textContaining('Shizuku'), findsNothing);
  });

  test('passes system decorations to the native start request', () async {
    MethodCall? received;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          received = call;
          return {'displayId': 7};
        });
    final bridge = NativeBridge();
    await bridge.start(
      DisplayProfile(
        'Test',
        '240 dpi',
        1920,
        1080,
        240,
        Icons.desktop_windows_rounded,
        id: 'test',
      ),
      false,
      true,
      decorations: false,
    );
    expect(received?.method, 'start');
    expect((received?.arguments as Map)['decorations'], isFalse);
    expect((received?.arguments as Map)['secure'], isTrue);
  });

  test('applies workspace magnification after resolving a profile', () async {
    MethodCall? received;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          received = call;
          return {'displayId': 7};
        });
    final bridge = NativeBridge();
    await bridge.start(
      DisplayProfile(
        'Test',
        '240 dpi',
        1920,
        1080,
        240,
        Icons.desktop_windows_rounded,
        id: 'test',
      ),
      false,
      false,
      decorations: false,
      workspaceMagnificationPercent: 200,
    );
    final arguments = received?.arguments as Map;
    expect(arguments['width'], 960);
    expect(arguments['height'], 540);
    expect(arguments['density'], 120);
  });
}
