import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart' as mui;

import '../lib/linux/linux_apps.dart';
import '../lib/linux/linux_expressive.dart';
import '../lib/linux/linux_installation_icon.dart';
import '../lib/linux/linux_installations.dart';
import '../lib/linux/linux_store_catalog.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('app.freedextop/linux');
  final registry = LinuxInstallations.instance;
  const app = StoreApp(
    id: 'org.example.Editor',
    package: 'flathub:org.example.Editor',
    name: 'Example Editor',
    summary: '',
    description: '',
    categories: [],
    icon: '',
    desktopIds: ['org.example.Editor.desktop'],
  );
  var installed = false;
  var jobs = <Map<Object?, Object?>>[];
  final calls = <String>[];

  setUp(() {
    installed = false;
    jobs = [];
    calls.clear();
    registry.snapshot('test', []);
    registry.remember('test', StoreSource.forDistro('debian'), [app]);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call.method);
          switch (call.method) {
            case 'apps':
              return installed
                  ? [
                      {
                        'id': 'org.example.Editor.desktop',
                        'name': 'Example Editor',
                      },
                    ]
                  : [];
            case 'shortcuts':
            case 'sessions':
              return [];
            case 'storeJobs':
              expect((call.arguments as Map)['peek'], isTrue);
              return jobs;
            default:
              return null;
          }
        });
  });
  tearDown(() {
    registry.snapshot('test', []);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  Map<Object?, Object?> job(String state, {int percent = -1}) => {
    'action': 'install',
    'packages': [app.package],
    'state': state,
    'percent': percent,
  };

  test('queue appears before desktop entry and completion preserves tile identity without duplicates', () async {
    jobs = [job('queued')];
    final list = LinuxAppList();
    addTearDown(list.dispose);
    await list.load('test');
    final placeholder = list.displayedApps.single;
    expect(placeholder.name, app.name);
    expect(list.installationOf(placeholder)!.queued, isTrue);
    final key = list.tileKey(placeholder);
    installed = true;
    jobs = [job('running', percent: 45)];
    await list.load('test');
    expect(list.displayedApps, hasLength(1));
    expect(list.installationOf(list.displayedApps.single)!.percent, 45);
    jobs = [job('done', percent: 100)];
    await list.refreshInstallations();
    final ready = list.displayedApps.single;
    expect(ready.desktopId, 'org.example.Editor.desktop');
    expect(list.tileKey(ready), key);
    expect(list.installationOf(ready), isNull);
  });

  test('cancelled and failed placeholders disappear while another container stays queued', () async {
    registry.enqueue('other', 'deb-get:editor');
    addTearDown(() => registry.snapshot('other', []));
    final list = LinuxAppList();
    addTearDown(list.dispose);
    for (final state in ['cancelled', 'failed']) {
      jobs = [job('queued')];
      await list.load('test');
      expect(list.displayedApps, hasLength(1));
      jobs = [job(state)];
      await list.refreshInstallations();
      expect(list.displayedApps, isEmpty);
      expect(registry.forContainer('other'), hasLength(1));
    }
  });

  Widget host(Widget child) => MaterialApp(
    localizationsDelegates: [linuxMaterialUiLocalizations],
    builder: (context, child) => LinuxExpressive(child: child!),
    home: child,
  );

  testWidgets('right click opens application menu without launching it', (
    tester,
  ) async {
    installed = true;
    final list = LinuxAppList();
    await list.load('test');
    await tester.pumpWidget(
      host(
        mui.Scaffold(
          body: CustomScrollView(
            slivers: [LinuxAppsGrid(list: list, onReload: () async {})],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final gesture = await tester.startGesture(
      tester.getCenter(find.text(app.name)),
      kind: PointerDeviceKind.mouse,
      buttons: kSecondaryMouseButton,
    );
    await gesture.up();
    await tester.pumpAndSettle();
    expect(find.text('Uninstall'), findsOneWidget);
    expect(calls, isNot(contains('launchApp')));
    await tester.pumpWidget(const SizedBox());
    list.dispose();
  });

  testWidgets('completion animates grey shrunken icon back to normal', (
    tester,
  ) async {
    final state = ValueNotifier<AppInstallation?>(
      const AppInstallation(
        container: 'test',
        package: 'editor',
        name: 'Editor',
        queued: false,
        percent: 40,
      ),
    );
    await tester.pumpWidget(
      host(
        ValueListenableBuilder<AppInstallation?>(
          valueListenable: state,
          builder: (context, value, _) => Center(
            child: LinuxInstallationIcon(
              installation: value,
              child: const SizedBox.square(dimension: 56),
            ),
          ),
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 600));
    final finder = find.descendant(
      of: find.byType(LinuxInstallationIcon),
      matching: find.byType(Transform),
    );
    expect(
      tester.widget<Transform>(finder).transform.storage[0],
      closeTo(.78, .01),
    );
    state.value = null;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    expect(
      tester.widget<Transform>(finder).transform.storage[0],
      closeTo(1, .001),
    );
    await tester.pumpWidget(const SizedBox());
    state.dispose();
    expect(tester.takeException(), isNull);
  });
}
