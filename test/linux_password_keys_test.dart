import 'package:free_dextop/linux_password_keys.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  KeyEventResult press(TextEditingController c, String? character) => typePrintableKey(
    c,
    KeyDownEvent(
      physicalKey: PhysicalKeyboardKey.keyA,
      logicalKey: LogicalKeyboardKey.keyA,
      character: character,
      timeStamp: Duration.zero,
    ),
  );

  test('adds a typed character at the cursor', () {
    final c = TextEditingController(text: 'ac')..selection = const TextSelection.collapsed(offset: 1);
    expect(press(c, 'b'), KeyEventResult.handled);
    expect(c.text, 'abc');
    expect(c.selection.baseOffset, 2);
  });

  test('uses the end of the text when there is no cursor yet', () {
    final c = TextEditingController();
    press(c, 'x');
    press(c, 'y');
    expect(c.text, 'xy');
  });

  test('replaces selected text', () {
    final c = TextEditingController(text: 'abcd')
      ..selection = const TextSelection(baseOffset: 1, extentOffset: 3);
    press(c, 'z');
    expect(c.text, 'azd');
  });

  test('leaves control keys alone', () {
    final c = TextEditingController();
    expect(press(c, null), KeyEventResult.ignored);
    expect(press(c, '\b'), KeyEventResult.ignored);
    expect(c.text, '');
  });

  testWidgets('reaches a focused field through a wrapper that takes no focus', (tester) async {
    final c = TextEditingController();
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: Focus(
      canRequestFocus: false,
      skipTraversal: true,
      onKeyEvent: (_, e) => typePrintableKey(c, e),
      child: TextField(controller: c, obscureText: true, autofocus: true),
    ))));
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.keyQ, character: 'q');
    expect(c.text, 'q');
  });
}
