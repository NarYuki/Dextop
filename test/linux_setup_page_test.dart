import 'package:flutter/material.dart';
import 'package:material_ui/material_ui.dart' as mui;
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/linux_setup_page.dart';
import 'package:material_3_expressive/material_3_expressive.dart';

const catalog = <Map<Object?, Object?>>[
  {'id': 'debian', 'name': 'Debian 13'},
  {'id': 'ubuntu', 'name': 'Ubuntu 24.04'},
  {'id': 'archlinux', 'name': 'Arch Linux ARM'},
];

Future<void> next(WidgetTester tester) async {
  await tester.tap(find.text('Continue'));
  await tester.pumpAndSettle();
}

Future<void> enterAccount(WidgetTester tester) async {
  for (final label in ['Password', 'Confirm password']) {
    final field = find.byWidgetPredicate(
      (widget) => widget is M3ETextField && widget.label == label,
    );
    await tester.ensureVisible(field);
    await tester.enterText(
      find.descendant(of: field, matching: find.byType(EditableText)),
      'test-account-password',
    );
    await tester.pump();
  }
}

void main() {
  const channel = MethodChannel('app.freedextop/linux');
  final keyringCalls = <MethodCall>[];
  setUp(() {
    keyringCalls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'setKeyring') {
            keyringCalls.add(call);
            return true;
          }
          return null;
        });
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });
  testWidgets(
    'new containers offer only Debian and restore below the distro list',
    (tester) async {
      tester.view.physicalSize = const Size(1000, 1400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final progress = ValueNotifier<Map<String, LinuxSetupProgress>>({});
      addTearDown(progress.dispose);
      await tester.pumpWidget(
        mui.MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          locale: const Locale('en'),
          home: LinuxSetupPage(
            catalog: catalog,
            progress: progress,
            refresh: () async {},
            install: (_, _, _, _) async => 'new',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Debian 13'), findsOneWidget);
      expect(find.text('Ubuntu 24.04'), findsNothing);
      expect(find.text('Arch Linux ARM'), findsNothing);
      final restore = find.text('Restore from a snapshot or backup');
      await tester.ensureVisible(restore);
      expect(
        tester.getTopLeft(restore).dy,
        greaterThan(tester.getTopLeft(find.text('Debian 13')).dy),
      );
      await tester.tap(restore);
      await tester.pumpAndSettle();
      expect(find.text('Restore from a backup file'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'walks through choices and sends the selected options to installation',
    (tester) async {
      final progress = ValueNotifier<Map<String, LinuxSetupProgress>>({});
      addTearDown(progress.dispose);
      Map<String, Object>? selected;
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          locale: const Locale('en'),
          home: LinuxSetupPage(
            catalog: catalog,
            progress: progress,
            refresh: () async {},
            install: (name, distro, options, targetId) async {
              expect(distro, 'debian');
              expect(targetId, isNull);
              selected = options;
              progress.value = {
                'new': const LinuxSetupProgress(
                  state: 'installing',
                  phase: 'language',
                ),
              };
              return 'new';
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      await next(tester);
      await next(tester);
      await tester.tap(find.text('Japanese — Mozc (experimental)'));
      await next(tester);
      await next(tester);
      await enterAccount(tester);
      await next(tester);
      await tester.tap(find.text('Start setup'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(selected?['displayBackend'], 'wayland');
      expect(selected?.containsKey('profile'), false);
      expect(selected?.containsKey('graphics'), false);
      expect(selected?.containsKey('themes'), false);
      expect(selected?['ime'], 'mozc');
      expect(selected?['apps'], containsAll(['files', 'editor']));
      expect(selected?['username'], 'dextop');
      expect(selected?['accountPassword'], 'test-account-password');
      expect(keyringCalls, isEmpty);
      expect(find.text('Setting up language and fonts'), findsOneWidget);
      progress.value = {'new': const LinuxSetupProgress(state: 'installed')};
      await tester.pumpAndSettle();
      expect(find.text('Your Linux workspace is ready'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets(
    'existing Ubuntu containers keep their supported browser choices',
    (tester) async {
      final progress = ValueNotifier<Map<String, LinuxSetupProgress>>({});
      addTearDown(progress.dispose);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          locale: const Locale('en'),
          home: LinuxSetupPage(
            catalog: catalog,
            containerId: 'existing-ubuntu',
            distroId: 'ubuntu',
            progress: progress,
            refresh: () async {},
            install: (_, _, _, _) async => 'new',
          ),
        ),
      );
      await tester.pumpAndSettle();
      for (var i = 0; i < 3; i++) {
        await next(tester);
      }
      M3ECheckbox browser(String name) => tester.widget<M3ECheckbox>(
        find.descendant(
          of: find.widgetWithText(M3EListItem, name),
          matching: find.byType(M3ECheckbox),
        ),
      );
      final firefox = browser('Firefox');
      final chromium = browser('Chromium');
      final chrome = browser('Google Chrome');
      expect(firefox.onChanged, isNull);
      expect(firefox.value, false);
      expect(chromium.onChanged, isNull);
      expect(chrome.onChanged, isNotNull);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets(
    'retry updates the same container instead of creating another one',
    (tester) async {
      final progress = ValueNotifier<Map<String, LinuxSetupProgress>>({});
      addTearDown(progress.dispose);
      final targets = <String?>[];
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          locale: const Locale('en'),
          home: LinuxSetupPage(
            catalog: catalog,
            progress: progress,
            refresh: () async {},
            install: (_, _, _, id) async {
              targets.add(id);
              progress.value = {
                'existing': const LinuxSetupProgress(
                  state: 'failed',
                  error: 'Download interrupted',
                ),
              };
              return 'existing';
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      for (var i = 0; i < 4; i++) {
        await next(tester);
      }
      await enterAccount(tester);
      await next(tester);
      await tester.tap(find.text('Start setup'));
      await tester.pumpAndSettle();
      expect(find.text('Download interrupted'), findsOneWidget);
      await tester.tap(find.text('Review and retry'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Start setup'));
      await tester.pumpAndSettle();
      expect(targets, [null, 'existing']);
      expect(keyringCalls, isEmpty);
      await tester.pumpWidget(const SizedBox());
    },
  );
}
