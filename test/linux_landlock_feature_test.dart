import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/linux/landlock_feature.dart';
import 'package:free_dextop/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('app.freedextop/linux');
  const display = MethodChannel('app.freedextop/display');
  final applied = <Map<String, Object?>>[];
  var offline = false;
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    applied.clear();
    offline = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (offline) throw MissingPluginException();
          if (call.method != 'syncExperimentalFastMode') {
            throw StateError('Unexpected runtime call: ${call.method}');
          }
          final prefs = await SharedPreferences.getInstance();
          applied.add({
            'enabled': prefs.getBool(fastModePreferenceKey),
            'mode': prefs.getString(fastModeKindPreferenceKey),
          });
          return prefs.getBool(fastModePreferenceKey);
        });
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          display,
          (call) async => {
            'supportsInternal120Hz': true,
            'forceInternal120Hz': false,
          },
        );
  });
  tearDown(() {
    for (final c in [channel, display]) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(c, null);
    }
  });
  Future<void> open(
    WidgetTester tester, {
    Locale locale = const Locale('en'),
    bool withReference = false,
    double textScale = 1,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        locale: locale,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: Column(
            children: [
              if (withReference)
                DisplayEnvironmentSettingsCard(
                  bridge: NativeBridge(),
                  showConvenience: false,
                ),
              const LinuxFastModeExperimentalTile(),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  LinuxFastMode? mode(WidgetTester tester) => tester
      .widget<DropdownButton<LinuxFastMode>>(
        find.byType(DropdownButton<LinuxFastMode>),
      )
      .value;
  Future<void> choose(WidgetTester tester, String label) async {
    await tester.tap(find.byType(DropdownButton<LinuxFastMode>));
    await tester.pumpAndSettle();
    await tester.tap(find.text(label).last);
    await tester.pumpAndSettle();
  }

  testWidgets(
    'defaults to Disabled in one dropdown, without a separate switch or Linux startup',
    (tester) async {
      await open(tester);
      expect(mode(tester), LinuxFastMode.disabled);
      expect(find.byType(Switch), findsNothing);
      expect(applied, isEmpty);
      expect(
        tester
            .widget<DropdownButton<LinuxFastMode>>(
              find.byType(DropdownButton<LinuxFastMode>),
            )
            .items!
            .length,
        3,
      );
    },
  );
  testWidgets('all three choices apply immediately and survive reopening', (
    tester,
  ) async {
    await open(tester);
    for (final entry in [
      ('Protected + fast', LinuxFastMode.protected),
      ('Unprotected', LinuxFastMode.unprotected),
      ('Disabled', LinuxFastMode.disabled),
    ]) {
      await choose(tester, entry.$1);
      expect(mode(tester), entry.$2);
      expect(applied.last, {
        'enabled': entry.$2 != LinuxFastMode.disabled,
        'mode': entry.$2.name,
      });
      await tester.pumpWidget(const SizedBox());
      await open(tester);
      expect(mode(tester), entry.$2);
    }
    expect(applied.length, 3);
  });
  testWidgets('old ON preference migrates to protected', (tester) async {
    SharedPreferences.setMockInitialValues({fastModePreferenceKey: true});
    await open(tester);
    expect(mode(tester), LinuxFastMode.protected);
    expect(applied, isEmpty);
  });
  testWidgets('old OFF with remembered unprotected choice stays Disabled', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      fastModePreferenceKey: false,
      fastModeKindPreferenceKey: 'unprotected',
    });
    await open(tester);
    expect(mode(tester), LinuxFastMode.disabled);
  });
  testWidgets('unavailable runtime keeps the new selection saved', (
    tester,
  ) async {
    offline = true;
    await open(tester);
    await choose(tester, 'Unprotected');
    expect(mode(tester), LinuxFastMode.unprotected);
    expect(
      (await SharedPreferences.getInstance()).getBool(fastModePreferenceKey),
      true,
    );
    expect(tester.takeException(), isNull);
  });
  for (final locale in [const Locale('en'), const Locale('ja')]) {
    testWidgets(
      'three-choice dropdown fits narrow ${locale.languageCode} screen',
      (tester) async {
        await tester.binding.setSurfaceSize(const Size(320, 800));
        addTearDown(() => tester.binding.setSurfaceSize(null));
        await open(tester, locale: locale);
        expect(tester.takeException(), isNull);
        expect(
          tester.getCenter(find.byType(DropdownButton<LinuxFastMode>)).dx,
          greaterThan(tester.getCenter(find.byIcon(Icons.speed_rounded)).dx),
        );
      },
    );
    testWidgets('rows grow naturally with text in ${locale.languageCode}', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(411, 1200));
      tester.binding.platformDispatcher.localeTestValue = locale;
      addTearDown(() {
        tester.binding.setSurfaceSize(null);
        tester.binding.platformDispatcher.clearLocaleTestValue();
      });
      await open(tester, locale: locale, withReference: true);
      final initialFast = tester
          .getSize(find.byType(LinuxFastModeExperimentalTile))
          .height;
      final initialReference = tester
          .getSize(find.byType(DisplayEnvironmentSettingsCard))
          .height;
      await open(tester, locale: locale, withReference: true, textScale: 1.6);
      expect(
        tester.getSize(find.byType(LinuxFastModeExperimentalTile)).height,
        greaterThan(initialFast),
      );
      expect(
        tester.getSize(find.byType(DisplayEnvironmentSettingsCard)).height,
        greaterThan(initialReference),
      );
      expect(tester.takeException(), isNull);
    });
  }
}
