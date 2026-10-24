import 'package:flutter/widgets.dart';

import '../res/m3e_dialog_strings.dart';

/// Dialog headline that wraps freely or truncates at [maxLines].
///
/// A truncated headline expands to its full text on a single tap.
class M3EDialogHeadline extends StatefulWidget {
  /// M3EDialogHeadline.
  const M3EDialogHeadline({
    required this.text,
    required this.style,
    this.textAlign = TextAlign.start,
    this.maxLines,
    super.key,
  });

  /// Headline copy.
  final String text;

  /// Headline style.
  final TextStyle style;

  /// Alignment (center with an icon, start without).
  final TextAlign textAlign;

  /// Lines before truncation. Null wraps without limit.
  final int? maxLines;

  @override
  State<M3EDialogHeadline> createState() => _M3EDialogHeadlineState();
}

class _M3EDialogHeadlineState extends State<M3EDialogHeadline> {
  bool _expanded = false;

  void _expand() => setState(() => _expanded = true);

  @override
  Widget build(BuildContext context) {
    final int? maxLines = widget.maxLines;
    if (maxLines == null || _expanded) {
      return Text(
        widget.text,
        style: widget.style,
        textAlign: widget.textAlign,
      );
    }
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final text = Text(
          widget.text,
          style: widget.style,
          textAlign: widget.textAlign,
          maxLines: maxLines,
          overflow: TextOverflow.ellipsis,
        );
        if (!_overflows(context, constraints.maxWidth, maxLines)) {
          return text;
        }
        return Semantics(
          label: widget.text,
          hint: M3EDialogStrings.expandHeadline,
          onTap: _expand,
          excludeSemantics: true,
          child: MouseRegion(
            cursor: SystemMouseCursors.click,
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: _expand,
              child: text,
            ),
          ),
        );
      },
    );
  }

  bool _overflows(BuildContext context, double maxWidth, int maxLines) {
    final painter = TextPainter(
      text: TextSpan(
        text: widget.text,
        style: DefaultTextStyle.of(context).style.merge(widget.style),
      ),
      maxLines: maxLines,
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
    )..layout(maxWidth: maxWidth);
    final bool exceeded = painter.didExceedMaxLines;
    painter.dispose();
    return exceeded;
  }
}
