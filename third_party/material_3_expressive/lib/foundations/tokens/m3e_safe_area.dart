import 'package:flutter/widgets.dart';

/// Keyboard-aware system safe insets from raw view metrics.
///
/// Unlike [MediaQuery.viewPaddingOf], bottom updates when the IME appears:
/// home-indicator inset while the keyboard is hidden, `0` while it is shown
/// (the keyboard owns the bottom edge). Use [overlayBottomOf] for floating
/// overlays that must sit above the home indicator **or** the keyboard.
abstract final class M3ESafeArea {
  const M3ESafeArea._();

  /// Gesture / status / notch insets with keyboard-aware [EdgeInsets.bottom].
  static EdgeInsets paddingOf(BuildContext context) {
    // Register for MediaQuery updates so keyboard open/close rebuilds callers.
    MediaQuery.viewInsetsOf(context);
    final raw = MediaQueryData.fromView(View.of(context));
    final keyboardVisible = raw.viewInsets.bottom > 0;
    return EdgeInsets.only(
      left: raw.viewPadding.left,
      top: raw.viewPadding.top,
      right: raw.viewPadding.right,
      bottom: keyboardVisible ? 0.0 : raw.viewPadding.bottom,
    );
  }

  /// Status-bar / top cutout inset.
  static double topOf(BuildContext context) => paddingOf(context).top;

  /// Home-indicator inset when the keyboard is hidden; `0` when it is shown.
  static double bottomOf(BuildContext context) => paddingOf(context).bottom;

  /// Leading notch / display-cutout inset.
  static double leftOf(BuildContext context) => paddingOf(context).left;

  /// Trailing notch / display-cutout inset.
  static double rightOf(BuildContext context) => paddingOf(context).right;

  /// Bottom inset for overlays: home indicator, or keyboard height when shown.
  static double overlayBottomOf(BuildContext context) {
    return bottomOf(context) + MediaQuery.viewInsetsOf(context).bottom;
  }
}
