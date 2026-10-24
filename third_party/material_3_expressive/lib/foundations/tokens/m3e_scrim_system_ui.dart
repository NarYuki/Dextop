import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

/// System UI overlay for modal surfaces shown over a dark scrim.
///
/// Use [wrap] for full-scrim modals (dialogs, FAB menu, nav-rail modal)
/// to apply light status and navigation bar icons.
///
/// Use [wrapBottomSheet] for bottom sheets to apply light status-bar icons only;
/// the navigation bar keeps the app’s existing overlay style.
class M3EScrimSystemUi {
  M3EScrimSystemUi._();

  /// Overlay style with light status and navigation bar icons.
  static const SystemUiOverlayStyle overlayStyle = SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    systemNavigationBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    systemNavigationBarIconBrightness: Brightness.light,
    statusBarBrightness: Brightness.dark,
    systemStatusBarContrastEnforced: false,
    systemNavigationBarContrastEnforced: false,
  );

  /// Overlay style with light status-bar icons only (for bottom sheets).
  static const SystemUiOverlayStyle bottomSheetOverlayStyle =
      SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: Brightness.light,
        statusBarBrightness: Brightness.dark,
        systemStatusBarContrastEnforced: false,
      );

  /// Wraps [child] so status and navigation bar icons use [overlayStyle].
  static Widget wrap(Widget child) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: overlayStyle,
      child: child,
    );
  }

  /// Wraps [child] so only status-bar icons use [bottomSheetOverlayStyle].
  static Widget wrapBottomSheet(Widget child) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: bottomSheetOverlayStyle,
      child: child,
    );
  }
}
