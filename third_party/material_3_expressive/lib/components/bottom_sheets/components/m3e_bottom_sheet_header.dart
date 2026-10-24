import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../icon_buttons/m3e_icon_buttons.dart';

/// Header of a full-screen bottom sheet.
///
/// Holds a collapse (standard) or close (modal) button and an optional title.
class M3EBottomSheetHeader extends StatelessWidget {
  /// M3EBottomSheetHeader.
  const M3EBottomSheetHeader({
    required this.height,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.title,
    super.key,
  });

  /// Header height (64).
  final double height;

  /// Collapse or close glyph.
  final IconData icon;

  /// Button tooltip and label.
  final String tooltip;

  /// Collapses or closes the sheet.
  final VoidCallback onPressed;

  /// Optional title.
  final String? title;

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final String? text = title;
    return SizedBox(
      height: height,
      child: Row(
        children: <Widget>[
          const SizedBox(width: 4),
          M3EIconButton(
            icon: Icon(icon),
            onPressed: onPressed,
            tooltip: tooltip,
            semanticLabel: tooltip,
          ),
          const SizedBox(width: 4),
          if (text != null)
            Expanded(
              child: Text(
                text,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.typeScale.titleLarge.copyWith(
                  color: theme.colorScheme.onSurface,
                ),
              ),
            ),
        ],
      ),
    );
  }
}
