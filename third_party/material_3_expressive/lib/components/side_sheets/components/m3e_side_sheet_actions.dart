import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../divider/m3e_divider.dart';
import '../styles/m3e_side_sheet_theme.dart';

/// Bottom actions of a side sheet.
///
/// An optional divider over a bar at least 72 tall, with 16 above and 24
/// below the buttons, which sit at the start edge 8 apart.
class M3ESideSheetActions extends StatelessWidget {
  /// M3ESideSheetActions.
  const M3ESideSheetActions({
    required this.theme,
    required this.actions,
    this.showDivider = true,
    super.key,
  });

  /// Resolved sheet theme.
  final M3ESideSheetTheme theme;

  /// Action buttons, in focus order.
  final List<Widget> actions;

  /// Whether to separate the actions from the content.
  final bool showDivider;

  @override
  Widget build(BuildContext context) {
    final M3EColorScheme scheme = M3ETheme.of(context).colorScheme;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        if (showDivider)
          M3EDivider(
            color: theme.dividerColor(scheme),
            thickness: theme.dividerThickness,
          ),
        ConstrainedBox(
          constraints: BoxConstraints(minHeight: theme.actionsHeight),
          child: Padding(
            padding: EdgeInsetsDirectional.fromSTEB(
              theme.horizontalPadding,
              theme.actionsTopPadding,
              theme.horizontalPadding,
              theme.actionsBottomPadding,
            ),
            child: Row(
              mainAxisAlignment: theme.actionsAlignment,
              spacing: theme.actionsGap,
              children: actions,
            ),
          ),
        ),
      ],
    );
  }
}
