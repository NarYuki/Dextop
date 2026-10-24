import 'package:flutter/widgets.dart';

/// A single option within an `M3ESegmentedButton`.
@immutable
class M3ESegment<T> {
  /// Creates a segment.
  const M3ESegment({
    required this.value,
    this.label,
    this.icon,
    this.enabled = true,
    this.semanticLabel,
  }) : assert(
         label != null || icon != null,
         'A segment needs at least a label or an icon.',
       ),
       assert(
         label != null || semanticLabel != null,
         'Icon-only segments need a semanticLabel for accessibility.',
       );

  /// The value reported when this segment is selected.
  final T value;

  /// The optional text label.
  final String? label;

  /// The optional leading icon.
  final Widget? icon;

  /// Whether this segment accepts interaction.
  final bool enabled;

  /// Accessibility label; required when [label] is null (icon-only).
  ///
  /// Defaults to [label] when unset.
  final String? semanticLabel;

  /// Resolved a11y label for this segment.
  String? get resolvedSemanticLabel => semanticLabel ?? label;
}
