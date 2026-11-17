import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Types a printable key into [controller]. Only keys that the input method left
/// to the app get here, so text the keyboard already entered is not repeated.
KeyEventResult typePrintableKey(
  TextEditingController controller,
  KeyEvent event, {
  VoidCallback? changed,
}) {
  final text = event.character;
  final keyboard = HardwareKeyboard.instance;
  if (event is KeyUpEvent ||
      text == null ||
      text.isEmpty ||
      text.runes.any((c) => c < 0x20 || c == 0x7f) ||
      keyboard.isControlPressed ||
      keyboard.isAltPressed ||
      keyboard.isMetaPressed) {
    return KeyEventResult.ignored;
  }
  final value = controller.value;
  final selection = value.selection.isValid
      ? value.selection
      : TextSelection.collapsed(offset: value.text.length);
  controller.value = TextEditingValue(
    text: value.text.replaceRange(selection.start, selection.end, text),
    selection: TextSelection.collapsed(offset: selection.start + text.length),
  );
  changed?.call();
  return KeyEventResult.handled;
}
