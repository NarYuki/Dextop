import 'package:flutter/semantics.dart';
import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_side_sheet_theme.dart';

/// Top row of a side sheet: optional back icon, headline, close icon.
///
/// Padding is 24 at each end, 16 before a back icon, and 12 between the
/// elements. Screen readers hear the headline first, then back and close.
class M3ESideSheetHeader extends StatelessWidget {
  /// M3ESideSheetHeader.
  const M3ESideSheetHeader({
    required this.theme,
    required this.title,
    this.back,
    this.close,
    super.key,
  });

  /// Resolved sheet theme.
  final M3ESideSheetTheme theme;

  /// Headline text.
  final String title;

  /// Back icon button.
  final Widget? back;

  /// Close icon button.
  final Widget? close;

  @override
  Widget build(BuildContext context) {
    final M3EThemeData m3e = M3ETheme.of(context);
    final Widget? back = this.back;
    final Widget? close = this.close;
    return Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        back != null ? theme.startPaddingWithIcon : theme.horizontalPadding,
        theme.headerVerticalPadding,
        theme.horizontalPadding,
        theme.headerVerticalPadding,
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 48),
        child: Row(
          children: <Widget>[
            if (back != null) ...<Widget>[
              _ordered(1, back),
              SizedBox(width: theme.topElementsGap),
            ],
            Expanded(
              child: _ordered(
                0,
                Semantics(
                  header: true,
                  child: Text(
                    title,
                    maxLines: theme.headlineMaxLines,
                    overflow: TextOverflow.ellipsis,
                    style: theme.headlineStyle(m3e.typeScale, m3e.colorScheme),
                  ),
                ),
              ),
            ),
            if (close != null) ...<Widget>[
              SizedBox(width: theme.topElementsGap),
              _ordered(2, close),
            ],
          ],
        ),
      ),
    );
  }

  static Widget _ordered(double order, Widget child) =>
      Semantics(container: true, sortKey: OrdinalSortKey(order), child: child);
}
