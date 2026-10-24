import 'package:flutter/widgets.dart';

import '../styles/m3e_expandable_style.dart';

/// How expanded content under an expandable row is presented.
enum M3EExpandableExpandedType {
  /// A nested list widget rendered as sibling cards below the header.
  ///
  /// Typically a card-backed list so selection/reorder use the list's own
  /// implementation. A configurable top gap separates the header card from the
  /// nested list.
  list,

  /// A single freeform widget inside the expanded header body.
  content,

  /// A full-screen container transform. The row stays collapsed in place.
  transform,
}

/// Expanded content for an expandable list item.
///
/// Use [M3EExpandableExpanded.list] for a nested list widget (e.g. a
/// card-backed list), or [M3EExpandableExpanded.content] for freeform body
/// content.
@immutable
class M3EExpandableExpanded {
  /// Nested list expansion (sibling cards below the header).
  ///
  /// Pass a list widget such as a card-backed list so selection and reorder
  /// come from that list. [topGap] defaults to the expandable style gap when
  /// null.
  const M3EExpandableExpanded.list(this.child, {this.topGap})
    : type = M3EExpandableExpandedType.list;

  /// Single freeform expanded child inside the header card.
  const M3EExpandableExpanded.content(this.child)
    : type = M3EExpandableExpandedType.content,
      topGap = null;

  /// Full-screen morph. [child] is the destination surface.
  const M3EExpandableExpanded.transform(this.child)
    : type = M3EExpandableExpandedType.transform,
      topGap = null;

  /// Presentation kind.
  final M3EExpandableExpandedType type;

  /// Expanded child — a list widget for [M3EExpandableExpandedType.list], or
  /// freeform for [M3EExpandableExpandedType.content].
  final Widget child;

  /// Gap between the parent header card and the nested list.
  ///
  /// Only used when [type] is [M3EExpandableExpandedType.list]. When null,
  /// the expandable style's [M3EExpandableStyle.gap] is used.
  final double? topGap;

  /// Whether this is a nested-list expansion.
  bool get isList => type == M3EExpandableExpandedType.list;

  /// Whether this is a freeform content expansion.
  bool get isContent => type == M3EExpandableExpandedType.content;

  /// Whether a tap opens a full-screen container transform.
  bool get isTransform => type == M3EExpandableExpandedType.transform;
}
