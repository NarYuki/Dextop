import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:free_dextop/linux/linux_backups.dart';

void main() {
  testWidgets(
    'deleting popup stays until service completes and blocks dismissal',
    (tester) async {
      final pending = Completer<bool>();
      bool? result;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async {
                  result = await LinuxBackupsUi.duringOperation(
                    context,
                    'Deleting the container',
                    'Test container',
                    () => pending.future,
                  );
                },
                child: const Text('Delete'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Delete'));
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.text('Deleting the container'), findsOneWidget);
      await tester.tapAt(const Offset(1, 1));
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.text('Deleting the container'), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.text('Deleting the container'), findsOneWidget);
      pending.complete(true);
      await tester.pump();
      await tester.pumpAndSettle();
      expect(find.text('Deleting the container'), findsNothing);
      expect(result, isTrue);
    },
  );
  testWidgets('failed deletion closes popup and lets caller report error', (
    tester,
  ) async {
    final pending = Completer<bool>();
    Object? error;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () async {
                try {
                  await LinuxBackupsUi.duringOperation(
                    context,
                    'Deleting the container',
                    'Test container',
                    () => pending.future,
                  );
                } catch (e) {
                  error = e;
                }
              },
              child: const Text('Delete'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Delete'));
    await tester.pump(const Duration(milliseconds: 400));
    pending.completeError(StateError('failure'));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Deleting the container'), findsNothing);
    expect(error, isA<StateError>());
  });
}
