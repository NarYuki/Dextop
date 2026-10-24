import 'package:flutter/widgets.dart';

/// Replaces a list item's trailing slot with one widget.
///
/// Expandable rows use this so the expand icon is the only trailing icon.
class M3EListTrailingOverride extends InheritedWidget {
  /// M3EListTrailingOverride.
  const M3EListTrailingOverride({
    required super.child,
    this.trailing,
    super.key,
  });

  /// The only trailing widget. Null clears the slot.
  final Widget? trailing;

  /// Nearest override, or null when the item owns its trailing.
  static M3EListTrailingOverride? maybeOf(BuildContext context) {
    return context
        .dependOnInheritedWidgetOfExactType<M3EListTrailingOverride>();
  }

  @override
  bool updateShouldNotify(M3EListTrailingOverride oldWidget) =>
      trailing != oldWidget.trailing;
}
