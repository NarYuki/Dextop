part of '../m3e_side_sheets.dart';

/// Runtime state a host (route or layout) hands to its [M3ESideSheet].
class _M3ESideSheetScope extends InheritedWidget {
  const _M3ESideSheetScope({
    required this.modal,
    required this.back,
    required this.fromAnchoredEdge,
    required this.onClose,
    required this.autofocus,
    required this.routeScope,
    required super.child,
  });

  /// 0 for the standard look, 1 for modal.
  final double modal;

  /// Predictive-back progress (0–1).
  final double back;

  /// Whether the back swipe started at the anchored edge.
  final bool fromAnchoredEdge;

  /// Close button handler.
  final VoidCallback onClose;

  /// Whether the first icon button takes focus.
  final bool autofocus;

  /// Whether the sheet scopes and names its route.
  final bool routeScope;

  static _M3ESideSheetScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_M3ESideSheetScope>();

  @override
  bool updateShouldNotify(_M3ESideSheetScope old) =>
      modal != old.modal ||
      back != old.back ||
      fromAnchoredEdge != old.fromAnchoredEdge ||
      onClose != old.onClose ||
      autofocus != old.autofocus ||
      routeScope != old.routeScope;
}
