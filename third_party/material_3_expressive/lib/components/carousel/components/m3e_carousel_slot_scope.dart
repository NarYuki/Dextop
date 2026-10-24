import 'package:flutter/widgets.dart';

/// The visible main-axis size of one carousel slot.
class M3ECarouselSlotScope extends InheritedWidget {
  /// M3ECarouselSlotScope.
  const M3ECarouselSlotScope({
    required this.slotMain,
    required super.child,
    super.key,
  });

  /// Current slot length along the scroll axis.
  final double slotMain;

  /// Nearest slot scope, or null.
  static M3ECarouselSlotScope? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<M3ECarouselSlotScope>();
  }

  @override
  bool updateShouldNotify(M3ECarouselSlotScope oldWidget) =>
      slotMain != oldWidget.slotMain;
}
