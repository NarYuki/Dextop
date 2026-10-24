import 'package:flutter/foundation.dart';

import '../res/m3e_side_sheet_strings.dart';

/// Accessibility strings for a side sheet.
///
/// The sheet itself reads as a dialog named by its headline.
@immutable
class M3ESideSheetLabels {
  /// M3ESideSheetLabels.
  const M3ESideSheetLabels({
    this.close = M3ESideSheetStrings.close,
    this.back = M3ESideSheetStrings.back,
    this.scrim = M3ESideSheetStrings.dismiss,
  });

  /// Close icon button tooltip and label.
  final String close;

  /// Back icon button tooltip and label.
  final String back;

  /// Scrim label (modal only).
  final String scrim;
}
