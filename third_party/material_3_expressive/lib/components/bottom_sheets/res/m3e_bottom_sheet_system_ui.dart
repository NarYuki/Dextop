import 'package:flutter/services.dart';

/// System bar styles for modal bottom sheets in light theme.
abstract final class M3EBottomSheetSystemUi {
  const M3EBottomSheetSystemUi._();

  /// Dark status and navigation bar icons where the light sheet sits
  /// under a system bar.
  static const SystemUiOverlayStyle lightSurface = SystemUiOverlayStyle(
    statusBarColor: Color(0x00000000),
    systemNavigationBarColor: Color(0x00000000),
    statusBarIconBrightness: Brightness.dark,
    systemNavigationBarIconBrightness: Brightness.dark,
    statusBarBrightness: Brightness.light,
    systemStatusBarContrastEnforced: false,
    systemNavigationBarContrastEnforced: false,
  );
}
