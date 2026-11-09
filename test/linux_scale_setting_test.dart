import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:free_dextop/linux/linux_scale_setting.dart';

import 'package:free_dextop/l10n/inline_text.dart';

void main() {
  testWidgets(
    'scale validates bounds before saving and accepts fractional percentages',
    (tester) async {
      int? saved;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: LinuxScaleSetting(
              value: 100,
              t: (key) => inlineText(const Locale('en'), key),
              onChanged: (value) => saved = value,
            ),
          ),
        ),
      );
      await tester.tap(find.text('Display scale'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextFormField), '301');
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      expect(saved, isNull);
      expect(find.text('Enter a value from 50 to 300'), findsOneWidget);
      await tester.enterText(find.byType(TextFormField), '137');
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      expect(saved, 137);
    },
  );
  testWidgets('app can clear its override and follow global scale', (
    tester,
  ) async {
    int? saved;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: LinuxScaleSetting(
            value: 125,
            allowDefault: true,
            t: (key) => inlineText(const Locale('en'), key),
            onChanged: (value) => saved = value,
          ),
        ),
      ),
    );
    await tester.tap(find.text('Display scale'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Use global setting'));
    await tester.pumpAndSettle();
    expect(saved, 0);
  });
}
