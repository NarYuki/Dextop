import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/linux_setup_page.dart';

const catalog = <Map<Object?, Object?>>[
  {'id': 'debian', 'name': 'Debian 13'},
  {'id': 'ubuntu', 'name': 'Ubuntu 24.04'},
  {'id': 'archlinux', 'name': 'Arch Linux ARM'},
];

Future<void> next(WidgetTester tester) async {
  await tester.tap(find.text('Continue'));
  await tester.pumpAndSettle();
}

void main() {
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
      await tester.tap(find.text('Minimal IceWM'));
      await next(tester);
      await next(tester);
      await tester.tap(find.text('Japanese — Mozc'));
      await next(tester);
      await next(tester);
      await tester.tap(find.text('Start setup'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(selected?['profile'], 'minimal');
      expect(selected?['graphics'], false);
      expect(selected?['themes'], false);
      expect(selected?['ime'], 'mozc');
    expect(selected?['apps'], isEmpty);
      expect(find.text('Setting up language and fonts'), findsOneWidget);
      progress.value = {'new': const LinuxSetupProgress(state: 'installed')};
      await tester.pumpAndSettle();
      expect(find.text('Your Linux workspace is ready'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('Ubuntu disables Snap browser choices and allows Chrome', (
    tester,
  ) async {
    final progress = ValueNotifier<Map<String, LinuxSetupProgress>>({});
    addTearDown(progress.dispose);
    await tester.pumpWidget(
      MaterialApp(
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
    await tester.ensureVisible(find.text('Ubuntu 24.04'));
    await tester.drag(find.byType(ListView).first, const Offset(0, -220));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ubuntu 24.04'));
    for (var i = 0; i < 4; i++) {
      await next(tester);
    }
    final firefox = tester.widget<CheckboxListTile>(
      find.widgetWithText(CheckboxListTile, 'Firefox'),
    );
    final chromium = tester.widget<CheckboxListTile>(
      find.widgetWithText(CheckboxListTile, 'Chromium'),
    );
    final chrome = tester.widget<CheckboxListTile>(
      find.widgetWithText(CheckboxListTile, 'Google Chrome'),
    );
    expect(firefox.onChanged, isNull);
    expect(firefox.value, false);
    expect(chromium.onChanged, isNull);
    expect(chrome.onChanged, isNotNull);
    await tester.pumpWidget(const SizedBox());
  });

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
      for (var i = 0; i < 5; i++) {
        await next(tester);
      }
      await tester.tap(find.text('Start setup'));
      await tester.pumpAndSettle();
      expect(find.text('Download interrupted'), findsOneWidget);
      await tester.tap(find.text('Review and retry'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Start setup'));
      await tester.pumpAndSettle();
      expect(targets, [null, 'existing']);
      await tester.pumpWidget(const SizedBox());
    },
  );
}
