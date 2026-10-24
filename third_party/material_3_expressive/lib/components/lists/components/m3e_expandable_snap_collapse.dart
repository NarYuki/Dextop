import 'package:flutter/widgets.dart';

/// When true, expandable items jump expand progress instead of springing.
///
/// Used to collapse a row before reorder drag measures extent.
class M3EExpandableSnapCollapse extends InheritedWidget {
  /// Creates a snap-collapse scope.
  const M3EExpandableSnapCollapse({
    required this.snap,
    required super.child,
    super.key,
  });

  /// Whether expand/collapse updates should snap.
  final bool snap;

  /// Nearest snap flag, or false when absent.
  static bool of(BuildContext context) {
    return context
            .dependOnInheritedWidgetOfExactType<M3EExpandableSnapCollapse>()
            ?.snap ??
        false;
  }

  @override
  bool updateShouldNotify(M3EExpandableSnapCollapse oldWidget) =>
      snap != oldWidget.snap;
}
