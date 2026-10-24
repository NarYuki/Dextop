import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/main.dart';
import 'package:free_dextop/setup_page.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets(
    'setup keeps shortcut callbacks and releases only its own listener',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      const channel = MethodChannel('app.freedextop/display');
      var statusRequests = 0;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
        call,
      ) async {
        if (call.method == 'status') {
          statusRequests++;
          return <String, Object>{};
        }
        return null;
      });
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          channel,
          null,
        ),
      );
      var shortcuts = 0;
      final pins = <bool?>[];
      final shortcutSubscription = NativeBridge.shortcutActions.listen(
        (_) => shortcuts++,
      );
      final pinSubscription = NativeBridge.pinResults.listen(pins.add);
      addTearDown(shortcutSubscription.cancel);
      addTearDown(pinSubscription.cancel);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: DextopSetupPage(onCompleted: () {}),
        ),
      );
      await tester.pumpAndSettle();
      void emit(String method, [Object? arguments]) {
        tester.binding.channelBuffers.push(
          channel.name,
          const StandardMethodCodec().encodeMethodCall(
            MethodCall(method, arguments),
          ),
          (_) {},
        );
      }

      final before = statusRequests;
      emit('shizukuStatusChanged');
      emit('shortcutAction');
      emit('shortcutPinResult', {'pinned': true});
      await tester.pumpAndSettle();
      expect(statusRequests, greaterThan(before));
      expect(shortcuts, 1);
      expect(pins, [true]);
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
      final after = statusRequests;
      emit('shizukuStatusChanged');
      emit('shortcutAction');
      emit('shortcutPinResult', {'pinned': false});
      await tester.pump();
      expect(statusRequests, after);
      expect(shortcuts, 2);
      expect(pins, [true, false]);
    },
  );
}
