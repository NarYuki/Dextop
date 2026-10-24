import 'package:flutter/widgets.dart';

/// Leading / content / trailing layout for floating or docked toolbars.
class M3EToolbarBody extends StatelessWidget {
  /// M3EToolbarBody.
  const M3EToolbarBody({
    required this.axis,
    required this.gap,
    this.leading,
    this.trailing,
    this.content,
    this.mainAxisSize = MainAxisSize.min,
    this.mainAxisAlignment = MainAxisAlignment.start,
    this.expandContent = false,
    super.key,
  });

  /// axis.

  final Axis axis;

  /// gap.
  final double gap;

  /// leading.
  final Widget? leading;

  /// trailing.
  final Widget? trailing;

  /// content.
  final Widget? content;

  /// mainAxisSize.
  final MainAxisSize mainAxisSize;

  /// mainAxisAlignment.
  final MainAxisAlignment mainAxisAlignment;

  /// When true, [content] is wrapped in [Expanded] so title rows with flex
  /// children receive bounded main-axis constraints.
  final bool expandContent;

  @override
  Widget build(BuildContext context) {
    Widget? resolvedContent = content;
    if (resolvedContent != null && expandContent) {
      resolvedContent = Expanded(child: resolvedContent);
    }

    final children = <Widget>[
      if (leading != null) ...<Widget>[
        leading!,
        SizedBox(
          width: axis == Axis.horizontal ? gap : 0,
          height: axis == Axis.vertical ? gap : 0,
        ),
      ],
      ?resolvedContent,
      if (trailing != null) ...<Widget>[
        SizedBox(
          width: axis == Axis.horizontal ? gap : 0,
          height: axis == Axis.vertical ? gap : 0,
        ),
        trailing!,
      ],
    ];

    if (axis == Axis.vertical) {
      return Column(
        mainAxisSize: mainAxisSize,
        mainAxisAlignment: mainAxisAlignment,
        children: children,
      );
    }
    return Row(
      mainAxisSize: mainAxisSize,
      mainAxisAlignment: mainAxisAlignment,
      children: children,
    );
  }
}
