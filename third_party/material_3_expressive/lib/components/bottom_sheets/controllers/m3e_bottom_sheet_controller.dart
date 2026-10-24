import 'package:flutter/foundation.dart';

import '../enums/m3e_bottom_sheet_enums.dart';

/// Host that an [M3EBottomSheetController] drives.
abstract class M3EBottomSheetControllerClient {
  /// Current resting value.
  M3EBottomSheetValue get value;

  /// Preset heights the sheet can rest at, lowest first.
  List<M3EBottomSheetValue> get availableValues;

  /// Visible height in dp.
  double get extent;

  /// Springs to [value]. Hidden closes a modal sheet.
  Future<void> animateTo(M3EBottomSheetValue value);

  /// Moves to the next taller height, wrapping to the lowest.
  Future<void> cycle();

  /// Closes the sheet; a modal sheet pops with [result].
  void close([Object? result]);
}

/// Drives a bottom sheet without dragging.
///
/// It is the single-pointer alternative to the drag handle. Pass it to
/// `M3EBottomSheet.show` or `M3EBottomSheet.standard`.
class M3EBottomSheetController extends ChangeNotifier {
  /// M3EBottomSheetController.
  M3EBottomSheetController();

  M3EBottomSheetControllerClient? _client;
  M3EBottomSheetValue _value = M3EBottomSheetValue.hidden;
  bool _disposed = false;

  /// Whether a sheet is attached.
  bool get isAttached => _client != null;

  /// Current resting value; hidden when detached.
  M3EBottomSheetValue get value => _value;

  /// Preset heights of the attached sheet, lowest first.
  List<M3EBottomSheetValue> get availableValues =>
      _client?.availableValues ?? const <M3EBottomSheetValue>[];

  /// Visible height in dp; 0 when detached.
  double get extent => _client?.extent ?? 0;

  /// Springs to [value] when it is available.
  Future<void> animateTo(M3EBottomSheetValue value) async =>
      _client?.animateTo(value);

  /// Springs to the tallest height.
  Future<void> expand() async {
    final List<M3EBottomSheetValue> values = availableValues;
    if (values.isNotEmpty) {
      await animateTo(values.last);
    }
  }

  /// Springs back to the initial (collapsed) height.
  Future<void> collapse() async {
    final List<M3EBottomSheetValue> values = availableValues;
    if (values.isEmpty) {
      return;
    }
    await animateTo(
      values.contains(M3EBottomSheetValue.collapsed)
          ? M3EBottomSheetValue.collapsed
          : values.first,
    );
  }

  /// Moves to the next taller height, wrapping to the lowest.
  Future<void> cycle() async => _client?.cycle();

  /// Shows a hidden standard sheet at its collapsed height.
  Future<void> show() => collapse();

  /// Hides a standard sheet, or closes a modal one.
  Future<void> hide() => animateTo(M3EBottomSheetValue.hidden);

  /// Closes the sheet; a modal sheet pops with [result].
  void close([Object? result]) => _client?.close(result);

  /// Binds [client]. Called by the sheet.
  void attach(M3EBottomSheetControllerClient client) {
    _client = client;
    _value = client.value;
    _notify();
  }

  /// Unbinds [client]. Called by the sheet.
  void detach(M3EBottomSheetControllerClient client) {
    if (_client != client) {
      return;
    }
    _client = null;
    _value = M3EBottomSheetValue.hidden;
    _notify();
  }

  /// Reports a new resting value. Called by the sheet.
  void updateValue(M3EBottomSheetValue value) {
    if (_value == value) {
      return;
    }
    _value = value;
    _notify();
  }

  // The sheet can unmount after the owner disposed this controller.
  void _notify() {
    if (!_disposed) {
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _client = null;
    super.dispose();
  }
}
