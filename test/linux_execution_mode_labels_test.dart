import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/linux/linux_advanced_settings.dart';
import 'package:free_dextop/linux/linux_widgets.dart';
import 'package:free_dextop/linux_setup_page.dart';

void main() {
  const linux = MethodChannel('app.freedextop/linux');
  const display = MethodChannel('app.freedextop/display');
  String? saved;
  var channel = 'github';
  setUp(() {
    saved = null;
    channel = 'github';
    SharedPreferences.setMockInitialValues({'setup_completed': true});
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      linux,
      (call) async => call.method == 'executionMode' ? saved : null,
    );
    messenger.setMockMethodCallHandler(
      display,
      (_) async => {
        'shizukuRunning': true,
        'shizukuGranted': true,
        'privileged': true,
        'distributionChannel': channel,
      },
    );
  });
  tearDown(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(linux, null);
    messenger.setMockMethodCallHandler(display, null);
  });
  Future<void> open(WidgetTester tester, Widget page) async {
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('ja'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: page,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('特権シェル（高度）'), findsOneWidget);
    expect(find.text('一般権限'), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('一般権限')).dy,
      lessThan(tester.getTopLeft(find.text('特権シェル（高度）')).dy),
    );
    expect(find.text('コンテナを高度な権限で実行します'), findsOneWidget);
    expect(find.text('既定の方式、アプリ権限で動かします'), findsOneWidget);
    final choices = tester.widgetList<LinuxChoice<String>>(
      find.byType(LinuxChoice<String>),
    );
    expect(choices.map((choice) => choice.groupValue).toSet(), {
      saved ?? 'android',
    });
  }

  testWidgets(
    'settings defaults to Android and uses the requested descriptions',
    (tester) async {
      await open(tester, const LinuxAdvancedSettingsPage());
    },
  );
  testWidgets('the Google Play build does not offer the privileged shell', (
    tester,
  ) async {
    channel = 'play';
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('ja'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const LinuxAdvancedSettingsPage(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('一般権限'), findsOneWidget);
    expect(find.text('特権シェル（高度）'), findsNothing);
  });
  testWidgets('the Google Play first setup skips the execution mode page', (
    tester,
  ) async {
    channel = 'play';
    final progress = ValueNotifier<Map<String, LinuxSetupProgress>>({});
    addTearDown(progress.dispose);
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('ja'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: LinuxSetupPage(
          firstRun: true,
          catalog: const [
            {'id': 'debian', 'name': 'Debian'},
          ],
          progress: progress,
          refresh: () async {},
          install: (_, _, _, _) async => 'ctest',
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('特権シェル（高度）'), findsNothing);
    expect(find.text('一般権限'), findsNothing);
    expect(find.byType(LinuxToggle), findsWidgets);
  });
  testWidgets('settings preserves an explicitly selected privileged shell', (
    tester,
  ) async {
    saved = 'privileged';
    await open(tester, const LinuxAdvancedSettingsPage());
  });
  testWidgets(
    'first setup defaults to Android even when privileged access is ready',
    (tester) async {
      final progress = ValueNotifier<Map<String, LinuxSetupProgress>>({});
      addTearDown(progress.dispose);
      await open(
        tester,
        LinuxSetupPage(
          firstRun: true,
          catalog: const [
            {'id': 'debian', 'name': 'Debian'},
          ],
          progress: progress,
          refresh: () async {},
          install: (_, _, _, _) async => 'ctest',
        ),
      );
    },
  );
}
