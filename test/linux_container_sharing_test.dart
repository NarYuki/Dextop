import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/linux/linux_compat.dart';

void main() {
  const channel = MethodChannel('app.freedextop/linux');
  final grants = <String, Map<String, bool>>{};
  final calls = <MethodCall>[];

  setUp(() {
    grants.clear();
    grants.addAll({
      'c1': {'camera': true, 'microphone': true, 'wholeStorage': true},
      'c2': {'camera': false, 'microphone': false, 'wholeStorage': false},
    });
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          final args = (call.arguments as Map?) ?? const {};
          switch (call.method) {
            case 'list':
              return [
                {'id': 'c1', 'name': 'First Debian', 'state': 'installed'},
                {'id': 'c2', 'name': 'Second Debian', 'state': 'installed'},
              ];
            case 'compatStatus':
              return {
                'androidMode': true,
                'foldersActive': true,
                'wholeStorageAvailable': true,
                ...grants[args['id']]!,
                'folders': [
                  {
                    'name': '${args['id']} folder',
                    'path': '/media/android/test',
                    'granted': true,
                  },
                ],
              };
            case 'setContainerGrant':
              grants[args['id']]![args['kind'] as String] =
                  args['enabled'] as bool;
              return true;
            default:
              return null;
          }
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  Future<void> open(WidgetTester tester, {String? id}) async {
    tester.view.physicalSize = const Size(1200, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        locale: const Locale('en'),
        home: Scaffold(
          body: SingleChildScrollView(child: LinuxCompatPanel(containerId: id)),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets(
    'whole storage hides folders; revocation affects selected container only',
    (tester) async {
      await open(tester);
      expect(find.text('c1 folder'), findsNothing);
      expect(find.text('Add a folder'), findsNothing);
      await tester.tap(find.text('Whole storage'));
      await tester.pumpAndSettle();
      expect(grants['c1']!['wholeStorage'], false);
      expect(grants['c2']!['wholeStorage'], false);
      expect(find.text('c1 folder'), findsOneWidget);
      await tester.tap(find.text('Second Debian'));
      await tester.pumpAndSettle();
      expect(find.text('c2 folder'), findsOneWidget);
      expect(find.text('c1 folder'), findsNothing);
      await tester.tap(find.text('Whole storage'));
      await tester.pumpAndSettle();
      expect(grants['c2']!['wholeStorage'], true);
      expect(grants['c1']!['wholeStorage'], false);
      expect(find.text('c2 folder'), findsNothing);
      expect(find.text('Add a folder'), findsNothing);
      expect(calls.where((c) => c.method == 'compatStatus').last.arguments, {
        'id': 'c2',
      });
    },
  );

  testWidgets(
    'granted camera and microphone can be revoked without Android permission requests',
    (tester) async {
      await open(tester, id: 'c1');
      expect(find.text('Second Debian'), findsNothing);
      for (final label in ['Camera', 'Microphone']) {
        await tester.tap(find.text(label));
        await tester.pumpAndSettle();
      }
      expect(grants['c1']!['camera'], false);
      expect(grants['c1']!['microphone'], false);
      expect(calls.where((c) => c.method == 'requestPermission'), isEmpty);
      expect(
        calls
            .where((c) => c.method == 'setContainerGrant')
            .every((c) => c.arguments['id'] == 'c1'),
        true,
      );
    },
  );

  testWidgets('folder picker receives the selected container identity', (
    tester,
  ) async {
    await open(tester, id: 'c2');
    await tester.tap(find.text('Add a folder'));
    await tester.pumpAndSettle();
    expect(calls.singleWhere((c) => c.method == 'addFolder').arguments, {
      'id': 'c2',
    });
  });
}
