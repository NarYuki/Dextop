import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Autofill client for `M3ETextField` that keeps web input alive.
///
/// The web engine keeps a dormant autofill form per autofill id. After a
/// `TextInput.updateConfig` (obscure, read-only or keyboard type changed while
/// focused), closing the connection drops the input from that form, and the
/// next connection with the same id can no longer insert its input. The
/// caret shows but typing is ignored. On web this client gives each input
/// configuration its own autofill id, so a reconnect always gets a working
/// form. Other platforms keep the field's own id.
class M3ETextFieldAutofillClient implements AutofillClient {
  /// Creates a client that delegates to the field's [EditableTextState].
  M3ETextFieldAutofillClient(this._editableKey);

  final GlobalKey<EditableTextState> _editableKey;

  String? _signature;
  int _version = 0;

  EditableTextState get _state => _editableKey.currentState!;

  @override
  String get autofillId {
    final String base = _state.autofillId;
    return _version == 0 ? base : '$base-$_version';
  }

  @override
  TextInputConfiguration get textInputConfiguration {
    final TextInputConfiguration config = _state.textInputConfiguration;
    if (!kIsWeb) {
      return config;
    }
    final signature =
        '${config.obscureText}|${config.readOnly}|'
        '${config.inputType.toJson()}';
    if (_signature != null && _signature != signature) {
      _version++;
    }
    _signature = signature;
    final AutofillConfiguration autofill = config.autofillConfiguration;
    if (!autofill.enabled || _version == 0) {
      return config;
    }
    return config.copyWith(
      autofillConfiguration: AutofillConfiguration(
        uniqueIdentifier: autofillId,
        autofillHints: autofill.autofillHints,
        currentEditingValue: autofill.currentEditingValue,
        hintText: autofill.hintText,
      ),
    );
  }

  @override
  void autofill(TextEditingValue newEditingValue) =>
      _state.autofill(newEditingValue);
}
