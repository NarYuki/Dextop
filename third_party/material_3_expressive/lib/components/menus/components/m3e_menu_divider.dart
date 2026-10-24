import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import 'm3e_menu_style_scope.dart';

/// Horizontal divider between menu items inside one elevated surface.
class M3EMenuDividerWidget extends StatelessWidget {
  /// M3EMenuDividerWidget.
  const M3EMenuDividerWidget({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final menuTheme = theme.menuTheme;
    final style = M3EMenuStyleScope.styleOf(context);
    final Color color = menuTheme.dividerColor(theme.colorScheme, style);
    return Padding(
      padding: EdgeInsets.symmetric(
        vertical: menuTheme.dividerVerticalPadding,
        horizontal: menuTheme.stateLayerInset,
      ),
      child: SizedBox(
        height: menuTheme.dividerThickness,
        child: ColoredBox(color: color),
      ),
    );
  }
}
