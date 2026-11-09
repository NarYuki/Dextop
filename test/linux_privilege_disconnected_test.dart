import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/linux_page.dart';

void main() {
  const channel = MethodChannel('app.freedextop/linux');
  final calls = <String>[];
  var connected = false;
  var connectionRequests = 0;
  var includeSecond = false;
  var preferred = 'c123';
  final requestedApps = <String>[];
  const container = {
    'id': 'c123',
    'name': 'Saved Debian',
    'distro': 'debian',
    'state': 'installed',
    'runtimeState': 'stopped',
  };
  const app = {'id': 'steam.desktop', 'name': 'Saved Steam'};
  const otherContainer = {
    'id': 'c456',
    'name': 'Other Debian',
    'distro': 'debian',
    'state': 'installed',
  };
  const otherApp = {'id': 'other.desktop', 'name': 'Other App'};

  setUp(() {
    SharedPreferences.setMockInitialValues({'linux_first_run_done': true});
    calls.clear();
    connected = false;
    connectionRequests = 0;
    includeSecond = false;
    preferred = 'c123';
    requestedApps.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call.method);
          if (call.method == 'apps') {
            requestedApps.add(call.arguments['id'] as String);
          }
          return switch (call.method) {
            'status' => {
              'supportedDevice': true,
              'privilege': connected,
              'executionMode': 'privileged',
            },
            'offlineOverview' => {
              'containers': [container, if (includeSecond) otherContainer],
              'defaultContainer': preferred,
              'apps': [
                {...app, 'container': 'c123'},
                if (includeSecond) {...otherApp, 'container': 'c456'},
              ],
            },
            'listAll' => [container, if (includeSecond) otherContainer],
            'settings' => {'defaultContainer': preferred},
            'catalog' => [
              {'id': 'debian', 'name': 'Debian'},
            ],
            'apps' => [call.arguments['id'] == 'c456' ? otherApp : app],
            'sessions' || 'shortcuts' || 'storeJobs' => [],
            _ => null,
          };
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  Future<void> open(WidgetTester tester, String label) async {
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        locale: const Locale('en'),
        home: LinuxPage(
          onSwitchToPlasmaV2: () async {},
          privilegeConnectionLabel: label,
          onSetupPrivilege: () async {
            connectionRequests++;
            connected = true;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets(
    'disconnected apps and containers remain visible and cannot operate',
    (tester) async {
      await open(tester, 'Allow Stellar permissions');
      expect(find.text('Allow Stellar permissions'), findsOneWidget);
      expect(find.text('Saved Steam'), findsOneWidget);
      expect(calls, isNot(contains('connect')));
      expect(calls, isNot(contains('listAll')));
      expect(calls, isNot(contains('apps')));
      final disabled = tester.widget<SliverIgnorePointer>(
        find.byType(SliverIgnorePointer).last,
      );
      expect(disabled.ignoring, isTrue);
      await tester.tap(find.text('Saved Steam'), warnIfMissed: false);
      await tester.longPress(find.text('Saved Steam'), warnIfMissed: false);
      expect(calls, isNot(contains('launchApp')));
      await tester.tap(find.text('Containers'));
      await tester.pumpAndSettle();
      expect(find.text('Saved Debian'), findsOneWidget);
      await tester.tap(find.text('Saved Debian'), warnIfMissed: false);
      await tester.pump();
      expect(find.text('Saved Debian'), findsOneWidget);
      expect(calls, isNot(contains('connect')));
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  for (final provider in ['Stellar', 'Shizuku', 'Built-in binder']) {
    testWidgets('$provider connection action restores the Linux contents', (
      tester,
    ) async {
      final label = 'Connect $provider';
      await open(tester, label);
      await tester.tap(find.text(label));
      await tester.pumpAndSettle();
      expect(connectionRequests, 1);
      expect(calls, containsAll(['connect', 'listAll', 'apps']));
      expect(find.text(label), findsNothing);
      expect(find.text('Saved Steam'), findsOneWidget);
      expect(
        tester
            .widget<SliverIgnorePointer>(find.byType(SliverIgnorePointer).last)
            .ignoring,
        isFalse,
      );
      connected = false;
      calls.clear();
      tester.binding.channelBuffers.push(
        channel.name,
        const StandardMethodCodec().encodeMethodCall(
          const MethodCall('connection', false),
        ),
        (_) {},
      );
      await tester.pumpAndSettle();
      await tester.pump(const Duration(seconds: 3));
      expect(find.text(label), findsOneWidget);
      expect(find.text('Saved Steam'), findsOneWidget);
      expect(
        tester
            .widget<SliverIgnorePointer>(find.byType(SliverIgnorePointer).last)
            .ignoring,
        isTrue,
      );
      expect(calls, isNot(contains('connect')));
      expect(calls, isNot(contains('apps')));
      expect(calls, isNot(contains('storeJobs')));
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }

  for (final selected in ['c456', '', 'removed']) {
    testWidgets(
      'only one preferred container supplies apps for preference "$selected"',
      (tester) async {
        includeSecond = true;
        preferred = selected;
        final expected = selected == 'c456' ? 'Other App' : 'Saved Steam';
        final hidden = selected == 'c456' ? 'Saved Steam' : 'Other App';
        await open(tester, 'Connect Stellar');
        expect(find.text(expected), findsOneWidget);
        expect(find.text(hidden), findsNothing);
        await tester.tap(find.text('Connect Stellar'));
        await tester.pumpAndSettle();
        expect(find.text(expected), findsOneWidget);
        expect(find.text(hidden), findsNothing);
        expect(requestedApps.toSet(), {selected == 'c456' ? 'c456' : 'c123'});
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }
}
