import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/main.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('Book uses the native store and restores the launcher choice', (tester) async {
    SharedPreferences.setMockInitialValues({});
    const channel = MethodChannel('app.freedextop/display');
    final flags = <String, dynamic>{'always_show_taskbar': true, 'desktop_icons': true,
      'live_activity': true, 'notification_icons': true, 'launcher_expanded': true, 'display_scale': 0};
    final writes = <Map<dynamic, dynamic>>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'bookSettings') return flags;
      if (call.method == 'setBookSetting') {
        final value = call.arguments as Map;
        writes.add(value);
        flags[value['key'] as String] = value['value'];
        return flags;
      }
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, null));
    Future<void> open() async {
      await tester.pumpWidget(MaterialApp(locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: BookSettingsPage(bridge: NativeBridge())));
      await tester.pumpAndSettle();
    }
    await open();
    expect(find.text('Breeze Dark'), findsNothing);
    final label = find.text('Expand the Start menu');
    await tester.ensureVisible(label);
    await tester.pumpAndSettle();
    final row = find.ancestor(of: label, matching: find.byType(SwitchListTile));
    expect(tester.widget<SwitchListTile>(row).value, isTrue);
    await tester.tap(label);
    await tester.pumpAndSettle();
    expect(writes.single, {'key': 'launcher_expanded', 'value': false});
    await tester.pumpWidget(const SizedBox.shrink());
    await open();
    await tester.ensureVisible(label);
    expect(tester.widget<SwitchListTile>(row).value, isFalse);
  });
}
