import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/linux/linux_macos.dart';

void main() {
  const channel = MethodChannel('app.freedextop/linux');
  TestWidgetsFlutterBinding.ensureInitialized();
  final calls = <MethodCall>[];
  var installed = false;
  var registered = false;
  var bundleAvailable = false;
  setUp(() {
    calls.clear();
    installed = false;
    registered = false;
    bundleAvailable = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          if (call.method == 'macosRegister') {
            registered = true;
            return {};
          }
          if (call.method == 'macosUnregister') {
            registered = false;
            return {};
          }
          if (call.method == 'macosStatus') {
            return {
              'componentsPresent': installed,
              'guiPresent': installed,
              'bundleAvailable': bundleAvailable,
              'applications': [
                if (registered)
                  {
                    'key': '0' * 24,
                    'name': 'Example',
                    'bundle': '/root/Example.app',
                    'present': true,
                  },
              ],
            };
          }
          if (call.method == 'macosInstall') {
            installed = true;
            return {};
          }
          throw StateError('Unexpected ${call.method}');
        });
  });
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null),
  );
  Future<void> open(WidgetTester tester) async {
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        locale: const Locale('en'),
        home: const Scaffold(
          body: SingleChildScrollView(
            child: LinuxMacosPanel(
              containers: [
                {'id': 'ctest', 'name': 'Test Linux'},
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('only an available approved bundle enables installation', (
    tester,
  ) async {
    bundleAvailable = true;
    await open(tester);
    await tester.tap(find.text('Install compatibility runtime'));
    await tester.pumpAndSettle();
    expect(calls.singleWhere((c) => c.method == 'macosInstall').arguments, {
      'id': 'ctest',
      'value': '',
    });
    expect(
      find.text('Components installed; app compatibility is unverified'),
      findsOneWidget,
    );
  });
  testWidgets('missing runtime never enables bundle registration', (
    tester,
  ) async {
    await open(tester);
    expect(
      find.text('Not installed or required files are missing'),
      findsOneWidget,
    );
    await tester.tap(find.text('Register an application'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNothing);
    expect(calls.map((c) => c.method), ['macosStatus']);
  });
  testWidgets(
    'registers a bundle for the selected container and confirms removal',
    (tester) async {
      installed = true;
      await open(tester);
      await tester.tap(find.text('Register an application'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), '/root/日本語 App.app');
      await tester.tap(find.text('Register'));
      await tester.pumpAndSettle();
      expect(calls.singleWhere((c) => c.method == 'macosRegister').arguments, {
        'id': 'ctest',
        'value': '/root/日本語 App.app',
      });
      expect(find.text('Example'), findsOneWidget);
      await tester.ensureVisible(find.text('Example'));
      await tester.tap(find.text('Example'));
      await tester.pumpAndSettle();
      expect(
        find.text('The application bundle and saved data will be kept.'),
        findsOneWidget,
      );
      await tester.tap(find.text('Remove'));
      await tester.pumpAndSettle();
      expect(
        calls.singleWhere((c) => c.method == 'macosUnregister').arguments,
        {'id': 'ctest', 'value': '0' * 24},
      );
      expect(find.text('Example'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );
}
