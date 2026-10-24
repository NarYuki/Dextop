import 'dart:async';

import 'package:flutter/services.dart';

/// Shares native display callbacks between setup and the home screen.
/// Pages cancel their subscriptions without replacing the channel handler.
abstract final class NativeDisplayEvents {
  static const _channel = MethodChannel('app.freedextop/display');
  static final _events = StreamController<MethodCall>.broadcast();

  static Stream<MethodCall> get stream {
    _channel.setMethodCallHandler((call) async => _events.add(call));
    return _events.stream;
  }
}
