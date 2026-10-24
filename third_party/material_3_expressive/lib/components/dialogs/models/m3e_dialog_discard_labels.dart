import 'package:flutter/foundation.dart';

import '../res/m3e_dialog_strings.dart';

/// Copy for the basic dialog that confirms discarding unsaved changes.
@immutable
class M3EDialogDiscardLabels {
  /// M3EDialogDiscardLabels.
  const M3EDialogDiscardLabels({
    this.title = M3EDialogStrings.discardTitle,
    this.keepLabel = M3EDialogStrings.keepEditing,
    this.discardLabel = M3EDialogStrings.discard,
    this.supportingText,
  });

  /// Headline ("Discard unsaved changes?").
  final String title;

  /// Dismissive action ("Keep editing").
  final String keepLabel;

  /// Confirming action ("Discard").
  final String discardLabel;

  /// Optional supporting text.
  final String? supportingText;
}
