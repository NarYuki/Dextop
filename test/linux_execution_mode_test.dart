import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/linux/linux_apps.dart';
import 'package:free_dextop/linux_page.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('app.freedextop/linux');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test(
    'late privileged app reply cannot repopulate the Android app list',
    () async {
      final old = Completer<Object?>();
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'apps') {
          if ((call.arguments as Map)['id'] == 'old') return old.future;
          return [
            {'id': 'new.desktop', 'name': 'Android app'},
          ];
        }
        return [];
      });
      final apps = LinuxAppList(trackInstallations: false);
      addTearDown(apps.dispose);
      final pending = apps.load('old');
      await Future<void>.delayed(Duration.zero);
      apps.resetForExecutionMode();
      await apps.load('new');
      old.complete([
        {'id': 'old.desktop', 'name': 'Privileged app'},
      ]);
      await pending;
      expect(apps.apps.single.name, 'Android app');
      expect(apps.loading, isFalse);
    },
  );

  test(
    'late shortcut reply cannot overwrite new app and shortcut snapshots',
    () async {
      final oldShortcuts = Completer<Object?>();
      var oldMode = true;
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'apps') {
          return [
            {
              'id': oldMode ? 'old.desktop' : 'new.desktop',
              'name': oldMode ? 'Old' : 'New',
            },
          ];
        }
        if (call.method == 'shortcuts') {
          return oldMode ? oldShortcuts.future : ['new.desktop'];
        }
        return [];
      });
      final apps = LinuxAppList(trackInstallations: false);
      addTearDown(apps.dispose);
      final pending = apps.load('old');
      await Future<void>.delayed(Duration.zero);
      apps.resetForExecutionMode();
      oldMode = false;
      await apps.load('new');
      oldShortcuts.complete(['old.desktop']);
      await pending;
      expect(apps.apps.single.name, 'New');
      expect(apps.shortcuts, {'new.desktop'});
    },
  );

  test('mode reset discards late window snapshots', () async {
    final windows = Completer<Object?>();
    messenger.setMockMethodCallHandler(channel, (call) async => windows.future);
    final apps = LinuxAppList(trackInstallations: false);
    addTearDown(apps.dispose);
    final pending = apps.refreshSessions();
    await Future<void>.delayed(Duration.zero);
    apps.resetForExecutionMode();
    windows.complete([
      {'container': 'old', 'desktop': 'old.desktop', 'xid': 42},
    ]);
    await pending;
    expect(apps.sessions, isEmpty);
  });

  testWidgets('Linux page retires old containers when execution mode changes', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({'linux_tab': 1});
    var mode = 'privileged';
    final oldList = Completer<Object?>();
    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'status':
          return {
            'supportedDevice': true,
            'privilege': true,
            'connected': true,
            'executionMode': mode,
          };
        case 'list':
        case 'listAll':
          if (mode == 'privileged') return oldList.future;
          return [
            {
              'id': 'cAndroidTest',
              'name': 'Android environment',
              'state': 'installed',
              'distro': 'debian',
            },
          ];
        case 'settings':
          return <String, Object?>{};
        case 'catalog':
        case 'apps':
        case 'shortcuts':
        case 'sessions':
        case 'storeJobs':
          return [];
        default:
          return null;
      }
    });
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: LinuxPage(onSwitchToPlasmaV2: () async {}),
      ),
    );
    await tester.pump();
    mode = 'android';
    await messenger.handlePlatformMessage(
      channel.name,
      const StandardMethodCodec().encodeMethodCall(
        const MethodCall('connection', false),
      ),
      (_) {},
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 350));
    oldList.complete([
      {
        'id': 'cOldTest',
        'name': 'Old privileged environment',
        'state': 'installed',
        'distro': 'debian',
      },
    ]);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 350));
    expect(find.text('Android environment'), findsWidgets);
    expect(find.text('Old privileged environment'), findsNothing);
    expect(find.text('Loading containers…'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('unresponsive container list ends loading with an error', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({'linux_tab': 1});
    final blocked = Completer<Object?>();
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'status') {
        return {
          'supportedDevice': true,
          'privilege': true,
          'executionMode': 'android',
        };
      }
      if (call.method == 'list' || call.method == 'listAll') return blocked.future;
      return [];
    });
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: LinuxPage(onSwitchToPlasmaV2: () async {}),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(seconds: 21));
    await tester.pump();
    expect(find.text('Loading containers…'), findsNothing);
    expect(find.textContaining('TimeoutException'), findsWidgets);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
