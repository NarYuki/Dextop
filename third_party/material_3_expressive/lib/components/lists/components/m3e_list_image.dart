import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../styles/m3e_list_theme.dart';

/// 56dp leading image.
class M3EListImage extends StatelessWidget {
  /// Creates a list image slot.
  const M3EListImage({required this.child, super.key});

  /// Image content.
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final M3EListItemTheme item = M3ETheme.of(context).listTheme.item;
    final double radius = item.isBaseline ? 0 : item.leadingImageRadius;
    return SizedBox.square(
      dimension: item.leadingImageSize,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: child,
      ),
    );
  }
}
