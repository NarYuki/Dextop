import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_list_theme.dart';

/// 40dp circular leading avatar.
class M3EListAvatar extends StatelessWidget {
  /// Creates a list avatar.
  const M3EListAvatar({this.label, this.child, super.key});

  /// Initials drawn when [child] is null.
  final String? label;

  /// Optional avatar content, such as an image.
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3EListItemTheme item = theme.listTheme.item;
    final M3EColorScheme scheme = theme.colorScheme;
    final double size = item.avatarSize;
    return SizedBox.square(
      dimension: size,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: item.resolveAvatar(scheme),
          shape: BoxShape.circle,
        ),
        child: Center(
          child:
              child ??
              Text(
                label ?? '',
                style: item.avatarLabelStyle(theme.typeScale, scheme),
              ),
        ),
      ),
    );
  }
}
