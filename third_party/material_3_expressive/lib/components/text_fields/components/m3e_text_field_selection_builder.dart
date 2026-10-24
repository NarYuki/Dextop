import 'package:flutter/widgets.dart';

/// Text selection gestures for `M3ETextField`.
///
/// Tap places the caret, double tap selects a word, and long press or drag
/// selects text. [onTap] runs on every user tap.
class M3ETextFieldSelectionBuilder extends TextSelectionGestureDetectorBuilder {
  /// Creates the gesture builder.
  M3ETextFieldSelectionBuilder({required super.delegate, this.onTap});

  /// Called after a user tap.
  final VoidCallback? onTap;

  @override
  void onUserTap() => onTap?.call();
}
