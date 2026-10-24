import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../cards/m3e_cards.dart';
import '../../icon_buttons/m3e_icon_buttons.dart';
import '../../lists/m3e_lists.dart';
import 'm3e_carousel_item.dart';

/// Vertical page of every carousel item.
///
/// Shown by Show all and the header arrow. Repeats [header] when one was set.
class M3ECarouselShowAll extends StatelessWidget {
  /// M3ECarouselShowAll.
  const M3ECarouselShowAll({required this.children, this.header, super.key});

  /// The same items the carousel shows.
  final List<M3ECarouselItem> children;

  /// Header repeated from the carousel, when it has one.
  final Widget? header;

  @override
  Widget build(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3EColorScheme scheme = theme.colorScheme;
    return ColoredBox(
      color: scheme.surface,
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            SizedBox(
              height: 48,
              child: Padding(
                padding: const EdgeInsets.only(left: 16),
                child: Row(
                  children: <Widget>[
                    Expanded(child: header ?? const SizedBox.shrink()),
                    M3EIconButton(
                      variant: M3EIconButtonVariant.standard,
                      icon: const Icon(M3EIcons.arrow_back),
                      tooltip: 'Back',
                      onPressed: () =>
                          M3ECardContainerTransformScope.closeOf(context),
                    ),
                  ],
                ),
              ),
            ),
            Expanded(
              child: M3EList.scrollable(
                itemCount: children.length,
                listPadding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                itemBuilder: (BuildContext context, int index) {
                  final M3ECarouselItem item = children[index];
                  return M3EListItem(
                    headline: item.semanticLabel ?? 'Item ${index + 1}',
                    leading: SizedBox(width: 56, height: 56, child: item.image),
                    transform: item.transform,
                    onTap: item.onTap,
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
