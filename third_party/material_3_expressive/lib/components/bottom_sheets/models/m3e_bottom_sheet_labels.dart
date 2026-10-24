import 'package:flutter/foundation.dart';

import '../enums/m3e_bottom_sheet_enums.dart';
import '../res/m3e_bottom_sheet_strings.dart';

/// Accessibility strings for a bottom sheet.
///
/// Only the drag handle is labelled; it reads as a button.
@immutable
class M3EBottomSheetLabels {
  /// M3EBottomSheetLabels.
  const M3EBottomSheetLabels({
    this.dragHandle = M3EBottomSheetStrings.dragHandle,
    this.expand = M3EBottomSheetStrings.expand,
    this.collapse = M3EBottomSheetStrings.collapse,
    this.dismiss = M3EBottomSheetStrings.dismiss,
    this.close = M3EBottomSheetStrings.close,
    this.scrim = M3EBottomSheetStrings.dismiss,
    this.preview = M3EBottomSheetStrings.preview,
    this.collapsed = M3EBottomSheetStrings.collapsed,
    this.expanded = M3EBottomSheetStrings.expanded,
    this.fullScreen = M3EBottomSheetStrings.fullScreen,
  });

  /// Drag handle label.
  final String dragHandle;

  /// Custom action that moves to a taller height.
  final String expand;

  /// Custom action that moves to a lower height; also the collapse tooltip.
  final String collapse;

  /// Custom action that closes the sheet.
  final String dismiss;

  /// Close button tooltip in the full-screen header.
  final String close;

  /// Scrim label (modal only).
  final String scrim;

  /// Value read for [M3EBottomSheetValue.preview].
  final String preview;

  /// Value read for [M3EBottomSheetValue.collapsed].
  final String collapsed;

  /// Value read for [M3EBottomSheetValue.expanded].
  final String expanded;

  /// Value read for [M3EBottomSheetValue.fullScreen].
  final String fullScreen;

  /// Spoken value for [value].
  String valueOf(M3EBottomSheetValue value) {
    return switch (value) {
      M3EBottomSheetValue.hidden => '',
      M3EBottomSheetValue.preview => preview,
      M3EBottomSheetValue.collapsed => collapsed,
      M3EBottomSheetValue.expanded => expanded,
      M3EBottomSheetValue.fullScreen => fullScreen,
    };
  }
}
