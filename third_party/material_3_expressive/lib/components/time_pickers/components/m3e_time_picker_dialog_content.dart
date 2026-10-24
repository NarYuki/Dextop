import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';

/// Wraps time picker dialog body content with mode-specific padding.
class M3ETimePickerDialogContent extends StatelessWidget {
  /// M3ETimePickerDialogContent.
  const M3ETimePickerDialogContent({
    required this.isInputMode,
    required this.child,
    super.key,
  });

  /// isInputMode.

  final bool isInputMode;

  /// child.
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final EdgeInsets padding = M3ETheme.of(context).dialogTheme.padding;
    if (isInputMode) {
      return Padding(padding: padding, child: child);
    }
    return Padding(
      padding: EdgeInsets.fromLTRB(0, padding.top, 0, padding.bottom),
      child: child,
    );
  }
}
