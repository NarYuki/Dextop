import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/linux_page.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:material_3_expressive/material_3_expressive.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('app.freedextop/linux');

  testWidgets(
    'unavailable access guides users to built-in setup and refreshes afterwards',
    (tester) async {
      SharedPreferences.setMockInitialValues({'linux_enabled': false});
      var available = false;
      var openedSetup = false;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'status') {
              return {
                'supportedDevice': true,
                'plasmaV2': true,
                'privilege': available,
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
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: LinuxPage(
            onSwitchToPlasmaV2: () async {},
            onSetupPrivilege: () async {
              openedSetup = true;
              available = true;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('built-in access service'), findsOneWidget);
      expect(
        tester.widget<M3ESwitch>(find.byType(M3ESwitch)).onChanged,
        isNull,
      );
      await tester.tap(find.text('Set up Dextop access'));
      await tester.pumpAndSettle();
      expect(openedSetup, isTrue);
      expect(find.textContaining('built-in access service'), findsNothing);
      expect(
        tester.widget<M3ESwitch>(find.byType(M3ESwitch)).onChanged,
        isNotNull,
      );
    },
  );
  testWidgets(
    'runtime failure survives refresh and recovery targets only its container',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        'linux_enabled': true,
        'linux_tab': 1,
      });
      final calls = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            calls.add(call);
            return switch (call.method) {
              'status' => {
                'supportedDevice': true,
                'plasmaV2': true,
                'privilege': true,
              },
              'catalog' => [
                {'id': 'debian', 'name': 'Debian'},
              ],
              'list' => [
                {
                  'id': 'ctest',
                  'name': 'My Linux',
                  'distro': 'debian',
                  'state': 'installed',
                  'runtimeState': 'failed',
                  'runtimeError': 'Xvfb exited (exit 1)',
                },
              ],
              'runtimeLogs' => 'SingletonSocket: Permission denied (13)',
              'resetSession' => true,
              _ => null,
            };
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      await tester.pumpWidget(
        MaterialApp(
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: LinuxPage(onSwitchToPlasmaV2: () async {}),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('Xvfb exited (exit 1)', findRichText: true),
        findsOneWidget,
      );
      await tester.tap(
        find.widgetWithIcon(M3EIconButton, M3EIcons.more_vert_rounded),
      );
      await tester.pumpAndSettle();
      await tester.drag(find.text('My Linux').last, const Offset(0, -450));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Show log'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Show log'));
      await tester.pumpAndSettle();
      expect(
        find.text(
          'SingletonSocket: Permission denied (13)',
          findRichText: true,
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.widgetWithIcon(M3EIconButton, M3EIcons.more_vert_rounded),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Force stop PRoot'));
      await tester.pumpAndSettle();
      expect(calls.where((call) => call.method == 'resetSession'), isEmpty);
      await tester.tap(find.widgetWithText(M3EButton, 'Force stop PRoot'));
      await tester.pumpAndSettle();
      expect(
        calls.lastWhere((call) => call.method == 'resetSession').arguments,
        {'id': 'ctest', 'force': true},
      );
      await tester.pumpWidget(const SizedBox());
    },
  );
}
