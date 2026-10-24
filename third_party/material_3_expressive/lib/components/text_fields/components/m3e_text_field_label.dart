import 'package:flutter/widgets.dart';

/// Label text for `M3ETextField`.
///
/// [progress] runs from 0 (resting in the field) to 1 (floating). The label
/// stays on one line and is never truncated. A required field ends with an
/// asterisk in the label color.
class M3ETextFieldLabel extends StatelessWidget {
  /// Creates the label.
  const M3ETextFieldLabel({
    required this.text,
    required this.progress,
    required this.restingStyle,
    required this.floatingStyle,
    required this.color,
    this.isRequired = false,
    super.key,
  });

  /// Label text.
  final String text;

  /// 0 = resting, 1 = floating.
  final double progress;

  /// Style in an empty field.
  final TextStyle restingStyle;

  /// Style in a populated or focused field.
  final TextStyle floatingStyle;

  /// Label (and asterisk) color.
  final Color color;

  /// Appends the required asterisk.
  final bool isRequired;

  /// Label plus the required asterisk.
  static String compose(String text, {required bool isRequired}) =>
      isRequired ? '$text*' : text;

  @override
  Widget build(BuildContext context) {
    final TextStyle style = TextStyle.lerp(
      restingStyle,
      floatingStyle,
      progress.clamp(0, 1),
    )!.copyWith(color: color);
    // The field carries the label for assistive tech.
    return ExcludeSemantics(
      child: Text(
        compose(text, isRequired: isRequired),
        style: style,
        maxLines: 1,
        softWrap: false,
        overflow: TextOverflow.visible,
      ),
    );
  }
}
