import 'dart:ui' show SemanticsRole;

import 'package:flutter/widgets.dart';

/// Supporting text, error text and character counter below `M3ETextField`.
///
/// Error text replaces supporting text so the layout does not shift. The error
/// message has the alert role; supporting text and the counter have their own
/// labels and no role. The alert role is announced when the error appears.
class M3ETextFieldSupportingRow extends StatelessWidget {
  /// Creates the supporting row.
  const M3ETextFieldSupportingRow({
    required this.style,
    required this.color,
    required this.padding,
    required this.counterGap,
    this.supportingText,
    this.errorText,
    this.counterText,
    this.counterSemanticsLabel,
    super.key,
  });

  /// Supporting text style.
  final TextStyle style;

  /// Supporting text and counter color.
  final Color color;

  /// Row insets (top and start/end).
  final EdgeInsetsGeometry padding;

  /// Gap between supporting text and the counter.
  final double counterGap;

  /// Supporting text, shown when there is no error.
  final String? supportingText;

  /// Error text; replaces [supportingText].
  final String? errorText;

  /// Counter text, such as `5/20`.
  final String? counterText;

  /// Counter label for assistive tech.
  final String? counterSemanticsLabel;

  @override
  Widget build(BuildContext context) {
    final String? message = errorText ?? supportingText;
    if (message == null && counterText == null) {
      return const SizedBox.shrink();
    }
    final TextStyle resolved = style.copyWith(color: color);
    return Padding(
      padding: padding,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Expanded(
            child: message == null
                ? const SizedBox.shrink()
                : _buildMessage(message, resolved),
          ),
          if (counterText != null) ...<Widget>[
            SizedBox(width: counterGap),
            Semantics(
              label: counterSemanticsLabel,
              child: ExcludeSemantics(
                child: Text(counterText!, style: resolved),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildMessage(String message, TextStyle resolved) {
    final isError = errorText != null;
    return Semantics(
      container: true,
      role: isError ? SemanticsRole.alert : null,
      label: message,
      child: ExcludeSemantics(child: Text(message, style: resolved)),
    );
  }
}
