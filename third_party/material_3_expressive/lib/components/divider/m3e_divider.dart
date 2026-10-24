import 'package:flutter/widgets.dart';

import '../../foundations/foundations.dart';
import 'enums/m3e_divider_axis.dart';
import 'enums/m3e_divider_inset.dart';

export 'enums/m3e_divider_axis.dart';
export 'enums/m3e_divider_inset.dart';
export 'styles/m3e_divider_theme.dart';

/// A Material 3 Expressive divider.
///
/// A thin decorative line. [M3EDividerInset.full] spans the cross axis.
/// [M3EDividerInset.inset] indents the leading edge. [M3EDividerInset.middle]
/// indents both edges. [outerMargin] adds the theme's end and bottom margins.
class M3EDivider extends StatelessWidget {
  /// Creates a divider.
  const M3EDivider({
    this.axis = M3EDividerAxis.horizontal,
    this.inset = M3EDividerInset.full,
    this.thickness,
    this.indent,
    this.endIndent,
    this.outerMargin = false,
    this.color,
    super.key,
  });

  /// Horizontal or vertical line.
  final M3EDividerAxis axis;

  /// Which measured inset to use when [indent] or [endIndent] is null.
  final M3EDividerInset inset;

  /// Line thickness. Null uses the theme thickness.
  final double? thickness;

  /// Leading inset along the line. Null uses [inset].
  final double? indent;

  /// Trailing inset along the line. Null uses [inset].
  final double? endIndent;

  /// Whether to add the theme end and bottom margins.
  final bool outerMargin;

  /// Line color. Null uses outline variant.
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(builder: _buildDivider);
  }

  Widget _buildDivider(BuildContext context) {
    final theme = M3ETheme.of(context);
    final dividerTheme = theme.dividerTheme;
    final Color line = color ?? dividerTheme.color(theme.colorScheme);
    final double lineThickness = thickness ?? dividerTheme.thickness;
    final double start = indent ?? dividerTheme.startFor(inset);
    final double end = endIndent ?? dividerTheme.endFor(inset);
    final EdgeInsetsGeometry padding = axis == M3EDividerAxis.vertical
        ? EdgeInsetsDirectional.only(
            top: start,
            bottom: end + (outerMargin ? dividerTheme.bottomMargin : 0),
            end: outerMargin ? dividerTheme.endMargin : 0,
          )
        : EdgeInsetsDirectional.only(
            start: start,
            end: end + (outerMargin ? dividerTheme.endMargin : 0),
            bottom: outerMargin ? dividerTheme.bottomMargin : 0,
          );

    final Widget rule = axis == M3EDividerAxis.vertical
        ? Padding(
            padding: padding,
            child: LayoutBuilder(
              builder: (BuildContext context, BoxConstraints constraints) {
                final double? height = constraints.hasBoundedHeight
                    ? constraints.maxHeight
                    : null;
                return SizedBox(
                  width: lineThickness,
                  height: height,
                  child: ColoredBox(color: line),
                );
              },
            ),
          )
        : Padding(
            padding: padding,
            child: SizedBox(
              height: lineThickness,
              width: double.infinity,
              child: ColoredBox(color: line),
            ),
          );

    return ExcludeSemantics(child: rule);
  }
}
